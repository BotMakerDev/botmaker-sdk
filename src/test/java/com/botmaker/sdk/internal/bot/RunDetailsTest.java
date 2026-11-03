package com.botmaker.sdk.internal.bot;

import com.botmaker.runlog.RunLog;
import com.botmaker.runlog.RunLogs;
import com.botmaker.runlog.api.Level;
import com.botmaker.runlog.api.LogLine;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.trace.Say;
import com.botmaker.shared.Diag;
import com.botmaker.shared.runlog.RunFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunDetailsTest {

    private final boolean wasOn = Diag.isEnabled();

    @AfterEach
    void restore() {
        RunFiles.stop();
        Diag.set(wasOn);
    }

    /** A quiet run's stuck walk: its warning in the file, after the last 50 calls that led to it. */
    @Test
    void aStuckWalksWarningLandsInTheFileWithTheLinesBeforeIt(@TempDir Path dir) {
        Diag.set(false);
        RunFiles.start(dir, Map.of(), RunDetails::attributed);
        for (int i = 0; i < 60; i++) Debug.log("step " + i);
        // An info line (a closed way the bot handles) is kept like a debug one: it does not empty the ring.
        Say.info("Market is not open now.");
        Say.warn("Stuck going to Market: no way there.");
        RunFiles.stop();

        RunLogs.Readable log = assertInstanceOf(RunLogs.Readable.class, RunLogs.read(dir.resolve(RunLog.FILE)));
        List<LogLine> lines = log.lines();
        assertEquals(51, lines.size());
        assertEquals("step 11", lines.getFirst().text());
        assertTrue(lines.subList(0, 49).stream().allMatch(l -> l.level() == Level.DEBUG));
        assertEquals(Level.INFO, lines.get(49).level());
        assertEquals(Level.WARN, lines.getLast().level());
        // Written by this test's own line, as a bot's line is named: not by the SDK's plumbing.
        assertEquals(RunDetailsTest.class.getName(), lines.getLast().writerClass());
    }
}
