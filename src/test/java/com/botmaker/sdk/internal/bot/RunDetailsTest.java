package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.TraceLine;
import com.botmaker.plugin.api.run.RunChannel;
import com.botmaker.sdk.internal.trace.Say;
import com.botmaker.sdk.internal.trace.SdkRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The run's details and lines reach the host through the run channel, never through a file of the SDK's own. */
class RunDetailsTest {

    private final Map<String, String> details = new LinkedHashMap<>();
    private final List<TraceLine> lines = new ArrayList<>();

    private final RunChannel channel = new RunChannel() {
        @Override
        public void details(Map<String, String> given) {
            details.putAll(given);
        }

        @Override
        public void line(TraceLine line, Throwable cause) {
            lines.add(line);
        }
    };

    @AfterEach
    void console() {
        SdkRun.ended();
    }

    @Test
    void theDetailsAreHandedToTheHost() {
        SdkRun.started(channel);
        RunDetails.start();
        assertEquals("none", details.get("launchTarget"));
        assertEquals("unknown", details.get("gameVersion"));
        assertTrue(details.containsKey("platform"));
    }

    /** A warning carries the bot line that wrote it, as a bot's line is named: not the SDK's plumbing. */
    @Test
    void aWarningReachesTheHostNamedByItsWriter() {
        SdkRun.started(channel);
        Say.warn("Stuck going to Market: no way there.");
        assertEquals(1, lines.size());
        TraceLine warning = lines.getFirst();
        assertEquals(TraceLine.Level.WARN, warning.level());
        assertEquals("Stuck going to Market: no way there.", warning.text());
        assertEquals(RunDetailsTest.class.getName(), warning.writerClass());
    }
}
