package com.botmaker.sdk.internal.trace;

import org.junit.jupiter.api.Test;

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

    @Test
    void closingARunThatWasNeverOpenedSaysNothing() {
        Trace.Runs runs = new Trace.Runs();
        assertNull(runs.flush("Foo"), "an event that never repeated has no run to report");
        runs.tick("Foo");
        assertNotNull(runs.flush("Foo"));
        assertNull(runs.flush("Foo"), "a closed run is closed once");
    }
}
