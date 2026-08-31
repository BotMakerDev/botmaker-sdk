package com.botmaker.sdk.authoring;

import com.botmaker.shared.config.CaptureSourceKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an editor is entitled to rely on now that {@code capture.json} has one owner: it round-trips, a
 * missing file is a state rather than an error, and every index that could name nothing has been normalised
 * before a caller sees it.
 */
class CaptureModelTest {

    private static final SdkVersion V = SdkVersion.latest();

    @Test
    void anAbsentFileIsAnEmptyModelRatherThanAnError(@TempDir Path dir) throws IOException {
        CaptureModel read = Authoring.readCapture(V, dir);
        assertEquals(CaptureModel.empty(), read);
        assertTrue(read.isEmpty());
        assertNull(read.defaultTarget());
    }

    @Test
    void aCorruptFileThrowsRatherThanReadingAsEmpty(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(CaptureModel.FILE_NAME), "{ not json");
        assertThrows(IOException.class, () -> Authoring.readCapture(V, dir));
    }

    @Test
    void aModelRoundTrips(@TempDir Path dir) throws IOException {
        CaptureModel written = new CaptureModel(List.of(
                CaptureTargetModel.of("desktop"),
                new CaptureTargetModel("window:Diablo IV", "The game"),
                CaptureTargetModel.of("emulator:BlueStacks-1")), 1,
                new CaptureModel.Resolution(1920, 1080));

        Authoring.writeCapture(V, dir, written);
        CaptureModel read = Authoring.readCapture(V, dir);

        assertEquals(written, read);
        assertEquals("The game", read.defaultTarget().describe());
        assertEquals(CaptureSourceKind.WINDOW, read.defaultTarget().kind());
        assertEquals("Diablo IV", read.defaultTarget().argument());
    }

    @Test
    void anIndexNamingNothingIsNormalisedAwayAndTheFirstTargetStandsIn() {
        CaptureModel model = new CaptureModel(List.of(CaptureTargetModel.of("desktop")), 7, null);

        assertNull(model.defaultIndex());
        assertEquals("desktop", model.defaultTarget().spec());
        assertEquals(model, model.withDefaultIndex(-1));
        assertEquals(0, model.withDefaultIndex(0).defaultIndex());
    }

    /**
     * The capture resolution is a third component of this file since 2026-08-31, and the two states that
     * cannot come from the editor are the ones worth pinning: absent, which every project written before
     * that date is, and unusable, which only a hand-edited file can be. Both read as "no resolution", so a
     * project opens either way and the overlay simply does not snap.
     */
    @Test
    void aCaptureSizeIsCarriedAndAnUnusableOneReadsAsNone() {
        CaptureModel none = CaptureModel.of(List.of(CaptureTargetModel.desktop()));
        assertNull(none.reference());

        CaptureModel sized = none.withReference(new CaptureModel.Resolution(1280, 720));
        assertEquals(new CaptureModel.Resolution(1280, 720), sized.reference());
        // It survives the two withers that rebuild the model around it.
        assertEquals(sized.reference(), sized.withTarget(CaptureTargetModel.monitor(0)).reference());
        assertEquals(sized.reference(), sized.withDefaultIndex(0).reference());
        assertNull(sized.withReference(null).reference());

        assertNull(none.withReference(new CaptureModel.Resolution(0, 1080)).reference());
        assertNull(none.withReference(new CaptureModel.Resolution(1920, -1)).reference());
    }

    @Test
    void theFirstTargetAddedBecomesTheDefaultAndLaterOnesDoNot() {
        CaptureModel model = CaptureModel.empty()
                .withTarget(CaptureTargetModel.of("monitor:0"))
                .withTarget(CaptureTargetModel.of("desktop"));

        assertEquals(0, model.defaultIndex());
        assertEquals("monitor:0", model.defaultTarget().spec());
        assertEquals(2, model.targets().size());
    }

    @Test
    void aTargetDescribesItselfBySpecWhenItHasNoLabel() {
        CaptureTargetModel target = CaptureTargetModel.of("  window:Diablo IV  ");

        assertEquals("window:Diablo IV", target.spec());
        assertEquals("window:Diablo IV", target.describe());
        assertNull(CaptureTargetModel.of(null).kind());
    }

    @Test
    void theFactoriesAndTheAccessorsAreTheSameFourForms() {
        assertEquals("desktop", CaptureTargetModel.desktop().spec());
        assertEquals("monitor:2", CaptureTargetModel.monitor(2).spec());
        assertEquals("window:Diablo IV", CaptureTargetModel.window("Diablo IV").spec());
        assertEquals("emulator:MuMu", CaptureTargetModel.emulator("MuMu").spec());

        assertEquals(2, CaptureTargetModel.monitor(2).monitorIndex());
        assertEquals("Diablo IV", CaptureTargetModel.window("Diablo IV").windowTitle());
        assertEquals("MuMu", CaptureTargetModel.emulator("MuMu").emulatorName());
        assertTrue(CaptureTargetModel.desktop().isDesktop());
    }

    /**
     * The accessors answer for the form they belong to and nothing else. Studio dispatched on four record
     * shapes before 2026-08-30, where a shape could only be one thing; the replacement has to be as narrow,
     * or a window title read off a monitor target silently captures the wrong surface.
     */
    @Test
    void anAccessorAnswersOnlyForItsOwnForm() {
        CaptureTargetModel monitor = CaptureTargetModel.monitor(1);

        assertNull(monitor.windowTitle());
        assertNull(monitor.emulatorName());
        assertFalse(monitor.isDesktop());
        assertEquals(0, CaptureTargetModel.window("Diablo IV").monitorIndex());
    }

    /**
     * A spec nothing recognises reads as the whole desktop and never throws. It is the ordinary state of a
     * hand-edited project file, and of one written by a newer Studio that knows a form this one does not.
     */
    @Test
    void anUnreadableSpecIsTheWholeDesktop() {
        CaptureTargetModel nonsense = CaptureTargetModel.of("something:else");

        assertNull(nonsense.kind());
        assertTrue(nonsense.isDesktop());
        assertEquals(0, nonsense.monitorIndex());
        assertNull(nonsense.windowTitle());
        assertEquals("Whole desktop", nonsense.shortLabel());
        // A monitor index that is not a number is the same kind of state and takes the same answer.
        assertEquals(0, CaptureTargetModel.of("monitor:left").monitorIndex());
    }

    @Test
    void theTwoLabelsSayTheSameThingAtTwoLengths() {
        assertEquals("Screen 3", CaptureTargetModel.monitor(2).longLabel());
        assertEquals("Screen 3", CaptureTargetModel.monitor(2).shortLabel());

        assertEquals("Window: Diablo IV", CaptureTargetModel.window("Diablo IV").longLabel());
        assertEquals("Diablo IV", CaptureTargetModel.window("Diablo IV").shortLabel());

        assertEquals("Whole desktop (all monitors)", CaptureTargetModel.desktop().longLabel());
        assertEquals("Whole desktop", CaptureTargetModel.desktop().shortLabel());

        // An unset default is the whole desktop, which is what every caller has always meant by one.
        assertEquals("Whole desktop (all monitors)", CaptureTargetModel.longLabelOf(null));
        assertEquals("Whole desktop", CaptureTargetModel.shortLabelOf(null));
    }

    @Test
    void theUsersOwnLabelWinsOverTheDerivedOne() {
        CaptureTargetModel named = new CaptureTargetModel("window:Diablo IV", "The game");

        assertEquals("The game", named.longLabel());
        assertEquals("The game", named.shortLabel());
        assertEquals("Diablo IV", named.windowTitle(), "and naming it changes nothing about what it matches");
    }
}
