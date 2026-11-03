package com.botmaker.sdk.internal.trace;

import com.botmaker.plugin.api.TraceLine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every SDK line said while a screen is known carries it; a line's own values win; the run's last lines once. */
class SdkRunValuesTest {

    @Test
    void aLineCarriesTheScreenKnownAndItsOwnValuesOverIt() {
        List<TraceLine> lines;
        try {
            lines = RecordingChannel.start(true).lines;
            SdkRun.debug("Test", "before");
            SdkRun.screen("Shop");
            SdkRun.debug("Test", "on it");
            Say.warn("lost", Map.of(SdkRun.SCREEN, "none", "target", "Shop"));
            SdkRun.screen(null);
            Say.info("after");
        } finally {
            RecordingChannel.stop();
        }

        assertEquals(Map.of(), lines.get(0).values());
        assertEquals(Map.of("screen", "Shop"), lines.get(1).values());
        assertEquals(Map.of("screen", "none", "target", "Shop"), lines.get(2).values());
        assertEquals(TraceLine.Level.WARN, lines.get(2).level());
        assertEquals(Map.of(), lines.get(3).values());
    }

    @Test
    void theRunsLastLinesAreSaidOnce() {
        List<TraceLine> lines;
        try {
            lines = RecordingChannel.start(false).lines;
            SdkRun.atEnd(() -> Say.info("spent", Map.of("millis", "5")));
            SdkRun.end();
            SdkRun.end();
        } finally {
            RecordingChannel.stop();
        }

        assertEquals(1, lines.size());
        assertEquals(Map.of("millis", "5"), lines.getFirst().values());
    }
}
