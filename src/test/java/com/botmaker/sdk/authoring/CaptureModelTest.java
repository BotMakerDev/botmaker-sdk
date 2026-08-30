package com.botmaker.sdk.authoring;

import com.botmaker.shared.config.CaptureSourceKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                CaptureTargetModel.of("emulator:BlueStacks-1")), 1);

        Authoring.writeCapture(V, dir, written);
        CaptureModel read = Authoring.readCapture(V, dir);

        assertEquals(written, read);
        assertEquals("The game", read.defaultTarget().describe());
        assertEquals(CaptureSourceKind.WINDOW, read.defaultTarget().kind());
        assertEquals("Diablo IV", read.defaultTarget().argument());
    }

    @Test
    void anIndexNamingNothingIsNormalisedAwayAndTheFirstTargetStandsIn() {
        CaptureModel model = new CaptureModel(List.of(CaptureTargetModel.of("desktop")), 7);

        assertNull(model.defaultIndex());
        assertEquals("desktop", model.defaultTarget().spec());
        assertEquals(model, model.withDefaultIndex(-1));
        assertEquals(0, model.withDefaultIndex(0).defaultIndex());
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
}
