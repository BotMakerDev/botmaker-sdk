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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Versions deduced from frames alone: one button's, the screen's, and a shade proposed once two buttons show it; what
 * a version means, by pressing it, by its going, and by its lasting into a later session.
 */
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

    private Path store() {
        return folder.resolve("versions.bin");
    }

    private List<Versions.Sighting> kept() {
        return Versions.kept(folder, store());
    }

    private List<Versions.Proposal> proposals() {
        return Versions.proposals(kept(), Versions.presses(folder, store()), List.of());
    }

    private void record(int frame, Versions.Sight sight) {
        Versions.record(folder, store(), frame(frame), sight);
    }

    /** {@code play} dimmed on frame 1 and {@code shop} on frame 2, each beside plain buttons: a shared fit. */
    private void dimmedOnTwo() {
        record(1, Versions.read(List.of(seen("play", DIM), seen("shop", SAME), seen("bag", SAME))));
        record(2, Versions.read(List.of(seen("shop", fit(0.46, 0)), seen("bag", SAME))));
    }

    private void press(String picture, Versions.Answer answer, String said) {
        Versions.keep(folder, store(), new Versions.Press(picture, DIM, answer, said));
    }

    @Test
    void oneButtonShadedWhileItsNeighboursStayIsThatButtonsVersion() {
        Versions.Sight sight = Versions.read(List.of(seen("play", DIM), seen("shop", SAME), seen("bag", SAME)));
        assertEquals(List.of("play"), sight.versions().stream().map(Versions.Seen::picture).toList());
        assertTrue(sight.screenWide().isEmpty());
        assertEquals(List.of("shop", "bag"), sight.plain().stream().map(Versions.Seen::picture).toList());
    }

    @Test
    void theSameFitOnMostButtonsIsAShadeOverTheScreenNeverAVersion() {
        Versions.Sight sight = Versions.read(List.of(seen("play", DIM), seen("shop", DIM), seen("bag", fit(0.47, 0)),
                seen("help", SAME)));
        assertTrue(sight.versions().isEmpty());
        assertEquals(3, sight.screenWide().size());
        record(1, sight);
        assertTrue(proposals().isEmpty(), "a screen-wide shade is never proposed");
    }

    @Test
    void aFitSeenOnTwoButtonsIsProposedAndOnOneIsOnlySaid() {
        record(1, Versions.read(List.of(seen("play", DIM), seen("shop", SAME), seen("bag", SAME))));
        Versions.Proposal lone = proposals().getFirst();
        assertFalse(lone.shared());
        record(2, Versions.read(List.of(seen("shop", fit(0.46, 0)), seen("bag", SAME))));
        List<Versions.Proposal> proposals = proposals();
        assertEquals(1, proposals.size());
        Versions.Proposal shared = proposals.getFirst();
        assertTrue(shared.shared());
        assertEquals(List.of("play", "shop"), shared.pictures());
        assertEquals(Shade.Kind.DIM, shared.shade().kind());
        assertEquals(State.DISABLED, shared.shade().state(), "a dimmed button means Disabled");
        assertNull(shared.why(), "nothing but its kind told it");
        assertEquals(lone.id(), shared.id(), "the id holds while more sightings come");
        assertTrue(shared.ids().contains(Versions.id(fit(0.46, 0).learned(Shade.Cause.GAME, State.DISABLED))),
                "each sighting's id names the group, once the first is dropped too");
        assertTrue(Versions.said(Versions.read(List.of(seen("shop", fit(0.46, 0)), seen("bag", SAME),
                seen("help", SAME))), 3, proposals, List.of(), List.of()).contains("learn_shade fit " + shared.id()));
    }

    @Test
    void aFitALearnedShadeNamesIsNotProposedAgain() {
        record(1, Versions.read(List.of(seen("play", DIM), seen("shop", SAME), seen("bag", SAME))));
        record(2, Versions.read(List.of(seen("shop", DIM), seen("bag", SAME))));
        Shade learned = DIM.learned(Shade.Cause.GAME, State.DISABLED);
        assertTrue(Versions.proposals(kept(), List.of(), List.of(learned)).isEmpty());
    }

    @Test
    void theSameButtonOnTheSameFrameIsKeptOnceAndEverySightingOutlivesARestart() {
        Versions.Sight sight = Versions.read(List.of(seen("play", DIM), seen("shop", SAME), seen("bag", SAME)));
        record(1, sight);
        record(1, sight);
        assertEquals(1, kept().size(), "a button shown as cut is kept only once it showed a version");
        record(1, Versions.read(List.of(seen("play", fit(0.6, 0)), seen("shop", SAME), seen("bag", SAME))));
        assertEquals(0.6, kept().getFirst().fit().gain().red(), 1e-9, "a button fitted otherwise on the frame replaces it");
        record(1, sight);
        press("play", Versions.Answer.TOAST, "Not enough gold");
        Versions.forget();
        List<Versions.Sighting> back = kept();
        assertEquals(1, back.size());
        assertEquals("play", back.getFirst().picture());
        assertEquals(0.45, back.getFirst().fit().gain().green(), 1e-9);
        assertEquals(List.of(new Versions.Press("play", DIM, Versions.Answer.TOAST, "Not enough gold")),
                Versions.presses(folder, store()), "presses outlive a restart too");
    }

    @Test
    void pressingAVersionThatDoesNothingMakesItDisabled() {
        record(1, Versions.read(List.of(seen("play", fit(1.6, 0)), seen("shop", SAME), seen("bag", SAME))));
        record(2, Versions.read(List.of(seen("shop", fit(1.62, 0)), seen("bag", SAME))));
        assertEquals(State.SELECTED, proposals().getFirst().shade().state(), "a brighter button means Selected");
        Versions.keep(folder, store(), new Versions.Press("shop", fit(1.6, 0), Versions.Answer.NOTHING, ""));
        Versions.Proposal proposal = proposals().getFirst();
        assertEquals(State.DISABLED, proposal.shade().state());
        assertEquals("pressing shop did nothing", proposal.why());
        assertNull(proposal.asks());
    }

    @Test
    void aToastAsksTheAuthorForABlocksStateNamedFromItsWords() {
        dimmedOnTwo();
        press("play", Versions.Answer.TOAST, "Not enough gold!");
        Versions.Proposal proposal = proposals().getFirst();
        assertEquals("Blocks.NOT_ENOUGH_GOLD", proposal.asks().id());
        assertTrue(proposal.asks().blocks());
        assertEquals("pressing play said \"Not enough gold!\"", proposal.why());
        String said = Versions.said(Versions.read(List.of(seen("play", DIM), seen("bag", SAME), seen("help", SAME))),
                3, List.of(proposal), List.of(), List.of());
        assertTrue(said.contains("learn_shade fit " + proposal.id() + " state Blocks.NOT_ENOUGH_GOLD"), said);
        assertTrue(said.contains("Ask the author"), said);
    }

    @Test
    void aVersionThatStillAnswersAsksForAPressesState() {
        dimmedOnTwo();
        press("shop", Versions.Answer.ACTED, "");
        assertEquals("Presses.DIMMED", proposals().getFirst().asks().id());
        press("play", Versions.Answer.NOTHING, "");
        assertEquals(State.DISABLED, proposals().getFirst().shade().state(), "a press that did nothing tells most");
        assertNull(proposals().getFirst().asks());
    }

    @Test
    void aVersionGoneWhenItsButtonIsSeenAgainIsDisabled() {
        record(1, Versions.read(List.of(seen("play", fit(1.6, 0)), seen("shop", SAME), seen("bag", SAME))));
        record(2, Versions.read(List.of(seen("shop", fit(1.62, 0)), seen("bag", SAME))));
        record(3, Versions.read(List.of(seen("play", SAME), seen("shop", fit(1.62, 0)), seen("bag", SAME))));
        Versions.Proposal proposal = proposals().getFirst();
        assertEquals(State.DISABLED, proposal.shade().state());
        assertEquals("play showed it no more when seen again", proposal.why());
    }

    @Test
    void aBadFitIsNotTheCutShownSoTheVersionIsNotGone() {
        dimmedOnTwo();
        Fit covered = new Fit(false, Shade.Channels.all(1), Shade.Channels.all(0), 0.5, null);
        record(3, Versions.read(List.of(seen("play", covered), seen("bag", SAME), seen("help", SAME))));
        assertEquals(State.DISABLED, proposals().getFirst().shade().state());
        assertNull(proposals().getFirst().why(), "a covered button told nothing");
    }

    @Test
    void aButtonShownAsCutAgainAndAgainIsKeptOncePerVersion() {
        dimmedOnTwo();
        for (int frame = 10; frame < 20; frame++) {
            record(frame, Versions.read(List.of(seen("play", SAME), seen("bag", SAME), seen("help", SAME))));
        }
        assertEquals(1, kept().stream().filter(s -> s.picture().equals("play") && s.plain()).count());
    }

    @Test
    void aVersionStillShownInALaterSessionLastsAndAsksForTheBotsState() {
        dimmedOnTwo();
        assertNull(proposals().getFirst().asks(), "one session tells nothing yet");
        Versions.forget();
        record(4, Versions.read(List.of(seen("play", DIM), seen("bag", SAME), seen("help", SAME))));
        Versions.Proposal proposal = proposals().getFirst();
        assertEquals("Blocks.DIMMED", proposal.asks().id());
        assertTrue(proposal.why().contains("later session"), proposal.why());
    }

    @Test
    void whatAPressDidIsReadFromWhatChanged() {
        assertEquals(Versions.Answer.ACTED, Versions.answer(true, false, "", ""), "a screen opened");
        assertEquals(Versions.Answer.NOTHING, Versions.answer(false, false, "", ""), "nothing beyond the button");
        assertEquals(Versions.Answer.TOAST, Versions.answer(false, true, "Locked", ""), "words showed");
        assertEquals(Versions.Answer.ACTED, Versions.answer(false, true, " ", ""), "a change with no words");
        assertEquals(Versions.Answer.ACTED, Versions.answer(false, true, "Loadout 2", "Loadout 1"),
                "words there before changed in place: a label, not a toast");
        assertEquals(Versions.Answer.TOAST, Versions.answer(false, true, "Locked", "~i Lv"),
                "noise read from the art before is no label: still a toast");
        Rect button = new Rect(10, 10, 20, 10);
        assertTrue(Versions.inside(new Rect(8, 9, 24, 12), button), "the button's own pressed look");
        assertFalse(Versions.inside(new Rect(10, 40, 20, 10), button), "a toast below it");
    }

    @Test
    void aStateIsNamedFromTheFirstWordsOrTheKind() {
        assertEquals("NOT_ENOUGH_GOLD", Versions.constant("Not enough gold!", Shade.Kind.DIM));
        assertEquals("UPGRADE_NEEDS_LEVEL_10", Versions.constant("Upgrade needs level 10 first", Shade.Kind.DIM));
        assertEquals("DIMMED", Versions.constant("10 seconds", Shade.Kind.DIM), "a name starts with a letter");
        assertEquals("DIMMED", Versions.constant("", Shade.Kind.DIM));
    }
}
