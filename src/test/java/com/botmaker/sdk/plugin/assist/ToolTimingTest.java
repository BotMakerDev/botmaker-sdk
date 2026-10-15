package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolTimingTest {

    enum Side { LEFT, RIGHT }

    record Sample(String name, @Describe(value = "n", optional = true) Integer count, boolean show,
                  @Describe(value = "s", optional = true) Side side, List<String> boxes,
                  @Describe(value = "z", optional = true) Double zoom) {
    }

    @Test
    void theTimedToolGetsTheParametersItWasSentAndEndsWithItsTime() {
        AtomicReference<Sample> got = new AtomicReference<>();
        AssistantTool<Sample> tool = AssistantTool.named("sample").describedAs("a sample").takes(Sample.class)
                .handledBy((params, context) -> {
                    got.set(params);
                    return AgentReply.text("done");
                });
        AssistantTool<Sample> timed = ToolTiming.timed(tool);
        AgentReply reply = timed.invoke(Map.of("name", "a", "show", true, "side", "right",
                "boxes", List.of("x 1 2 3 4")), null);

        assertEquals(new Sample("a", null, true, Side.RIGHT, List.of("x 1 2 3 4"), null), got.get());
        assertEquals(tool.params(), timed.params());
        List<AgentReply.Part> parts = reply.parts();
        assertEquals("done", parts.getFirst().text());
        assertTrue(parts.getLast().text().matches("\nTook \\d+\\.\\d s\\."), parts.getLast().text());
    }

    @Test
    void aRefusalKeepsItsRefusalAndItsTime() {
        AssistantTool<AssistantTool.None> tool = AssistantTool.named("no").describedAs("refuses").takesNothing()
                .handledBy((params, context) -> AgentReply.refused("not now"));
        AgentReply reply = ToolTiming.timed(tool).invoke(Map.of(), null);
        assertTrue(reply.isRefused());
        assertTrue(reply.parts().getLast().text().startsWith("\nTook "));
    }

    @Test
    void aPartInsideAnotherCountsTowardTheOuterOnly() {
        AgentReply reply = ToolTiming.time(() -> ToolTiming.timed(ToolTiming.Phase.MATCH, () -> {
            pause();
            return ToolTiming.timed(ToolTiming.Phase.TEXT, () -> {
                pause();
                return AgentReply.text("x");
            });
        }));
        String line = reply.parts().getLast().text();
        assertTrue(line.contains("matching 0."), line);
        assertFalse(line.contains("reading text"), line);
    }

    /** Each call is a row of the bot's tool-times.tsv, under a header naming each part (2026-10-14). */
    @Test
    void eachCallIsARowOfTheProjectsTimes(@TempDir Path project) throws IOException {
        ToolTiming.time("try_input", project, () -> ToolTiming.timed(ToolTiming.Phase.SETTLE, () -> {
            pause();
            return AgentReply.text("x");
        }));
        ToolTiming.time("screenshot", project, () -> AgentReply.text("y"));

        List<String> lines = Files.readAllLines(project.resolve(ToolTiming.FILE));
        assertEquals(List.of("started", "tool", "total_ms", "capture_ms", "motion_ms", "settle_ms", "match_ms",
                "text_ms", "wait_ms", "studio_ms"), List.of(lines.getFirst().split("\t")));
        assertEquals(3, lines.size(), "one header, then a row a call");
        String[] row = lines.get(1).split("\t");
        assertEquals("try_input", row[1]);
        assertTrue(Long.parseLong(row[5]) >= 50 && Long.parseLong(row[2]) >= Long.parseLong(row[5]),
                "settling counted, within the total: " + lines.get(1));
        assertEquals("screenshot", lines.get(2).split("\t")[1]);
    }

    @Test
    void outsideACallAPartIsNotTimed() {
        assertEquals("y", ToolTiming.timed(ToolTiming.Phase.CAPTURE, () -> "y"));
    }

    private static void pause() {
        try {
            Thread.sleep(60);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
