package com.botmaker.sdk.internal.trace;

import com.botmaker.plugin.api.TraceLine;
import com.botmaker.sdk.api.geometry.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The collapsing rule, on its own: a burst reports once, at the end, and never sooner. */
class TraceTest {

    @Test
    void aDurationReadsAsOneAPersonWouldSayIt() {
        assertEquals("0ms", Trace.elapsed(0));
        assertEquals("340ms", Trace.elapsed(340));
        assertEquals("999ms", Trace.elapsed(999));
        assertEquals("1.0s", Trace.elapsed(1_000));
        assertEquals("3.4s", Trace.elapsed(3_420));
    }

    @Test
    void aBurstIsSilentUntilItEndsAndThenReportsItsWholeCount() {
        Trace.Runs runs = new Trace.Runs();
        for (int i = 0; i < 47; i++) {
            assertNull(runs.tick("Foo"), "a run under the report interval must stay quiet, tick " + i);
        }

        Trace.Runs.Run run = runs.flush("Foo");

        assertNotNull(run, "the run has to be reported when it ends");
        assertEquals(47, run.count(), "every occurrence is counted, not just the ones since the last line");
    }

    /** Two keys are two runs: one template being found says nothing about another still missing. */
    @Test
    void runsDoNotBleedBetweenKeys() {
        Trace.Runs runs = new Trace.Runs();
        runs.tick("Foo");
        runs.tick("Bar");
        runs.tick("Bar");

        assertEquals(1, runs.flush("Foo").count());
        assertEquals(2, runs.flush("Bar").count());
    }

    /** A collapsed run crosses as one trace line with its count, and a located one with its rectangle. */
    @Test
    void aTracedLineCarriesItsCountAndWhere() {
        List<TraceLine> lines;
        try {
            lines = RecordingChannel.start(true).lines;
            Trace.log("[Vision] ore not found", new Trace.Runs.Run(47, 3_400));
            Trace.log("[Vision] find ore → (5,6)", new Rect(1, 2, 8, 8));
        } finally {
            RecordingChannel.stop();
        }

        assertEquals(2, lines.size());
        assertEquals("Vision", lines.getFirst().source());
        assertEquals("ore not found over 3.4s", lines.getFirst().text(), "the count is the host's to show, once");
        assertEquals(47, lines.getFirst().count());
        assertEquals(Optional.of(new TraceLine.Region(1, 2, 8, 8)), lines.get(1).where());
    }

    @Test
    void closingARunThatWasNeverOpenedSaysNothing() {
        Trace.Runs runs = new Trace.Runs();
        assertNull(runs.flush("Foo"), "an event that never repeated has no run to report");
        runs.tick("Foo");
        assertNotNull(runs.flush("Foo"));
        assertNull(runs.flush("Foo"), "a closed run is closed once");
    }
}
