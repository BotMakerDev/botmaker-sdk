package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.internal.screens.ShadeFit.Fit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Versions deduced from frames alone: one button's, the screen's, and a shade proposed once two buttons show it. */
class VersionsTest {

    private static final Fit SAME = fit(1, 0);
    private static final Fit DIM = fit(0.45, 0);
    private static final Rect AT = new Rect(0, 0, 10, 10);

    @TempDir
    Path folder;

    @AfterEach
    void forget() {
        Versions.forget();
    }

    private static Fit fit(double gain, double offset) {
        return new Fit(false, Shade.Channels.all(gain), Shade.Channels.all(offset), 0.01, null);
    }

    private static Versions.Seen seen(String picture, Fit fit) {
        return new Versions.Seen(picture, AT, fit);
    }

    /** A frame told apart from the others by {@code shade}. */
    private static BufferedImage frame(int shade) {
        BufferedImage frame = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) frame.setRGB(x, y, shade * 0x010101);
        return frame;
    }

    private List<Versions.Sighting> kept() {
        return Versions.kept(folder, folder.resolve("versions.bin"));
    }

    private void record(int frame, Versions.Sight sight) {
        Versions.record(folder, folder.resolve("versions.bin"), frame(frame), sight);
    }

    @Test
    void oneButtonShadedWhileItsNeighboursStayIsThatButtonsVersion() {
        Versions.Sight sight = Versions.read(List.of(seen("play", DIM), seen("shop", SAME), seen("bag", SAME)));
        assertEquals(List.of("play"), sight.versions().stream().map(Versions.Seen::picture).toList());
        assertTrue(sight.screenWide().isEmpty());
    }

    @Test
    void theSameFitOnMostButtonsIsAShadeOverTheScreenNeverAVersion() {
        Versions.Sight sight = Versions.read(List.of(seen("play", DIM), seen("shop", DIM), seen("bag", fit(0.47, 0)),
                seen("help", SAME)));
        assertTrue(sight.versions().isEmpty());
        assertEquals(3, sight.screenWide().size());
        record(1, sight);
        assertTrue(Versions.proposals(kept(), List.of()).isEmpty(), "a screen-wide shade is never proposed");
    }

    @Test
    void aFitSeenOnTwoButtonsIsProposedAndOnOneIsOnlySaid() {
        record(1, Versions.read(List.of(seen("play", DIM), seen("shop", SAME), seen("bag", SAME))));
        Versions.Proposal lone = Versions.proposals(kept(), List.of()).getFirst();
        assertFalse(lone.shared());
        record(2, Versions.read(List.of(seen("play", SAME), seen("shop", fit(0.46, 0)), seen("bag", SAME))));
        List<Versions.Proposal> proposals = Versions.proposals(kept(), List.of());
        assertEquals(1, proposals.size());
        Versions.Proposal shared = proposals.getFirst();
        assertTrue(shared.shared());
        assertEquals(List.of("play", "shop"), shared.pictures());
        assertEquals(Shade.Kind.DIM, shared.shade().kind());
        assertEquals(State.DISABLED, shared.shade().state(), "a dimmed button means Disabled");
        assertEquals(lone.id(), shared.id(), "the id holds while more sightings come");
        assertTrue(shared.ids().contains(Versions.id(fit(0.46, 0).learned(Shade.Cause.GAME, State.DISABLED))),
                "each sighting's id names the group, once the first is dropped too");
        assertTrue(Versions.said(Versions.read(List.of(seen("shop", fit(0.46, 0)), seen("bag", SAME),
                seen("help", SAME))), 3, proposals, List.of(), List.of()).contains("learn_shade fit " + shared.id()));
    }

    @Test
    void aFitALearnedShadeNamesIsNotProposedAgain() {
        record(1, Versions.read(List.of(seen("play", DIM), seen("shop", SAME), seen("bag", SAME))));
        record(2, Versions.read(List.of(seen("shop", DIM), seen("play", SAME), seen("bag", SAME))));
        Shade learned = DIM.learned(Shade.Cause.GAME, State.DISABLED);
        assertTrue(Versions.proposals(kept(), List.of(learned)).isEmpty());
    }

    @Test
    void theSameButtonOnTheSameFrameIsKeptOnceAndEverySightingOutlivesARestart() {
        Versions.Sight sight = Versions.read(List.of(seen("play", DIM), seen("shop", SAME), seen("bag", SAME)));
        record(1, sight);
        record(1, sight);
        assertEquals(1, kept().size());
        record(1, Versions.read(List.of(seen("play", fit(0.6, 0)), seen("shop", SAME), seen("bag", SAME))));
        assertEquals(0.6, kept().getFirst().fit().gain().red(), 1e-9, "a button fitted otherwise on the frame replaces it");
        record(1, sight);
        Versions.forget();
        List<Versions.Sighting> back = kept();
        assertEquals(1, back.size());
        assertEquals("play", back.getFirst().picture());
        assertEquals(0.45, back.getFirst().fit().gain().green(), 1e-9);
    }
}
