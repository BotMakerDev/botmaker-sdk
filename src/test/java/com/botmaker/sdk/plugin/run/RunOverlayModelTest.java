package com.botmaker.sdk.plugin.run;

import com.botmaker.shared.ipc.TelemetryEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the run bar says and which marks the layer draws, from the run's telemetry. */
class RunOverlayModelTest {

    private static final TelemetryEvent.Target SCREEN = TelemetryEvent.NO_SURFACE;

    @Test
    void theBarNamesTheActivityAndTheLastThingDoneThere() {
        RunStatus status = RunStatus.START;
        assertEquals("", status.text());

        status = status.after(new TelemetryEvent.Step("Collect", "", -1));
        assertEquals("Collect", status.text());

        status = status.after(new TelemetryEvent.Match(SCREEN, null, new TelemetryEvent.Rect(1, 2, 3, 4), 0.934,
                true, 40));
        assertEquals("Collect · found (93%) · line 40", status.text());

        status = status.after(new TelemetryEvent.Click(SCREEN, 5, 6, 1, 42));
        assertEquals("Collect · clicked · line 42", status.text());

        status = status.after(new TelemetryEvent.Log("info", "", "hi", 1, 0, null, "", "", "", 3));
        assertEquals("Collect · clicked · line 42", status.text(), "a log line moves nothing");

        status = status.after(new TelemetryEvent.Step("Home", "", -1));
        assertEquals("Home", status.text(), "a new activity starts with nothing done");
    }

    @Test
    void aBotWithNoFlowStillSaysWhatItDid() {
        RunStatus status = RunStatus.START.after(new TelemetryEvent.Match(SCREEN, null, null, 0.4, false, 7));
        assertEquals("looked, not found · line 7", status.text());
    }

    @Test
    void aFoundBoxAndAClickDotFadeOutAndAMissDrawsNothing() {
        RunMarks marks = new RunMarks();
        marks.add(new TelemetryEvent.Match(SCREEN, null, new TelemetryEvent.Rect(10, 20, 30, 40), 0.9, true, -1), 0);
        marks.add(new TelemetryEvent.Match(SCREEN, null, null, 0.2, false, -1), 0);
        marks.add(new TelemetryEvent.Click(SCREEN, 100, 200, 1, -1), 500);
        marks.add(new TelemetryEvent.Step("A", "", -1), 500);

        List<RunMarks.Mark> start = marks.at(500);
        assertEquals(2, start.size());
        assertEquals(new RunMarks.Mark(0, RunMarks.Kind.FOUND, 10, 20, 30, 40, 1 - 500.0 / RunMarks.FADE_MILLIS,
                "90%"), start.getFirst());
        assertEquals(new RunMarks.Mark(1, RunMarks.Kind.CLICK, 100, 200, 0, 0, 1, ""), start.getLast());
        assertEquals(start.getLast().id(), marks.at(600).getLast().id(), "a mark keeps its id while it shows");

        assertEquals(List.of(RunMarks.Kind.CLICK), marks.at(RunMarks.FADE_MILLIS).stream().map(RunMarks.Mark::kind)
                .toList(), "the box has faded, the later dot has not");
        assertTrue(marks.at(500 + RunMarks.FADE_MILLIS).isEmpty());
        assertTrue(marks.isEmpty());
    }

    @Test
    void aClickingLoopKeepsABoundedNumberOfMarks() {
        RunMarks marks = new RunMarks();
        for (int i = 0; i < RunMarks.MAX_MARKS * 3; i++) marks.add(new TelemetryEvent.Click(SCREEN, i, 0, 1, -1), 0);
        List<RunMarks.Mark> live = marks.at(0);
        assertEquals(RunMarks.MAX_MARKS, live.size());
        assertEquals(RunMarks.MAX_MARKS * 3 - 1, live.getLast().x(), "the newest are kept");
    }
}
