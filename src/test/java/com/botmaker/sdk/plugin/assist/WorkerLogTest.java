// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.plugin.assist.WorkerLog.Kind;
import com.botmaker.sdk.plugin.assist.WorkerLog.Line;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A worker's stream log read while it is written: each event a line a person reads, a cut last line left for later. */
class WorkerLogTest {

    /** A session as {@code claude -p --output-format stream-json --verbose} writes it, one event per line. */
    private static final List<String> SESSION = List.of(
            "{\"type\":\"system\",\"subtype\":\"init\",\"model\":\"claude-sonnet-5-5\",\"tools\":[]}",
            "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"thinking\",\"thinking\":\"The frame "
                    + "shows Settings.\"}]}}",
            "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Reading the map.\"}]}}",
            "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":"
                    + "\"mcp__botmaker__botmaker_sdk_read_map\",\"input\":{\"screen\":\"Home\"}}]}}",
            "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"t1\","
                    + "\"content\":[{\"type\":\"text\",\"text\":\"Home: marker PRIVATE_DNS\"}]}]}}",
            "{\"type\":\"rate_limit_event\"}",
            "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"num_turns\":3,\"duration_ms\":"
                    + "12400,\"result\":\"\"}");

    @TempDir
    Path folder;

    private static String text(List<String> lines) {
        return String.join("\n", lines) + "\n";
    }

    @Test
    void aRecordedSessionReadsIntoTheLinesAPersonReads() throws IOException {
        Path log = folder.resolve("s1.log");
        Files.writeString(log, text(SESSION));

        WorkerLog.Read read = WorkerLog.read(log, 0);

        assertEquals(List.of(
                new Line(Kind.START, "claude-sonnet-5-5"),
                new Line(Kind.THINKING, "The frame shows Settings."),
                new Line(Kind.SAID, "Reading the map."),
                new Line(Kind.TOOL_CALL, "botmaker_sdk_read_map {\"screen\":\"Home\"}"),
                new Line(Kind.TOOL_ANSWER, "Home: marker PRIVATE_DNS"),
                new Line(Kind.END, "done, 3 turns, 12 s")), read.lines());
        assertEquals(Files.size(log), read.next());
        assertEquals(List.of(), WorkerLog.read(log, read.next()).lines(), "nothing new, nothing read");
    }

    @Test
    void aHalfWrittenLastLineIsLeftForTheNextRead() throws IOException {
        Path log = folder.resolve("s1.log");
        String last = SESSION.get(2);
        Files.writeString(log, text(SESSION.subList(0, 2)) + last.substring(0, 30));

        WorkerLog.Read first = WorkerLog.read(log, 0);
        assertEquals(List.of(Kind.START, Kind.THINKING), first.lines().stream().map(Line::kind).toList());

        Files.writeString(log, last.substring(30) + "\n", StandardOpenOption.APPEND);
        WorkerLog.Read second = WorkerLog.read(log, first.next());

        assertEquals(List.of(new Line(Kind.SAID, "Reading the map.")), second.lines());
        assertEquals(Files.size(log), second.next());
    }

    @Test
    void aRefusedCallAPictureAndTheClientsOwnPrintingAreKept() throws IOException {
        Path log = folder.resolve("s1.log");
        Files.write(log, List.of(
                "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"is_error\":true,"
                        + "\"content\":\"'anchor' is required\"}]}}",
                "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"content\":["
                        + "{\"type\":\"image\",\"source\":{}},{\"type\":\"text\",\"text\":\"Settings\"}]}]}}",
                "Error: not signed in — é"), StandardCharsets.UTF_8);

        assertEquals(List.of(
                new Line(Kind.TOOL_ANSWER, "refused: 'anchor' is required"),
                new Line(Kind.TOOL_ANSWER, "[picture]\nSettings"),
                new Line(Kind.OUTPUT, "Error: not signed in — é")), WorkerLog.read(log, 0).lines());
    }

    @Test
    void aPicturesDataStaysOutOfTheLogAndOtherLinesAreKeptAsTheyAre() {
        String answer = "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"content\":["
                + "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/png\","
                + "\"data\":\"iVBORw0KGgo\"}}]}]}}";

        String slim = WorkerLog.slim(answer);

        assertFalse(slim.contains("iVBORw0KGgo"), slim);
        assertTrue(slim.contains("image/png"), slim);
        assertEquals(List.of(new Line(Kind.TOOL_ANSWER, "[picture]")), WorkerLog.lines(slim));
        assertEquals("not json \"image\"", WorkerLog.slim("not json \"image\""));
        assertEquals(SESSION.get(1), WorkerLog.slim(SESSION.get(1)));
    }

    @Test
    void aLongLogIsReadOverSeveralCallsAndALineLongerThanTheWindowIsSkipped() throws IOException {
        Path log = folder.resolve("s1.log");
        Files.writeString(log, SESSION.get(2) + "\n" + "x".repeat(300) + "\n" + SESSION.get(2) + "\n");

        WorkerLog.Read first = WorkerLog.read(log, 0, 100);
        WorkerLog.Read second = WorkerLog.read(log, first.next(), 100);
        WorkerLog.Read third = WorkerLog.read(log, second.next(), 100);

        assertEquals(List.of(new Line(Kind.SAID, "Reading the map.")), first.lines());
        assertEquals(List.of(new Line(Kind.OUTPUT, WorkerLog.TOO_LONG)), second.lines());
        assertEquals(List.of(new Line(Kind.SAID, "Reading the map.")), third.lines());
        assertEquals(Files.size(log), third.next());
    }

    @Test
    void aClippedLineNeverEndsInHalfACharacter() {
        String text = "a".repeat(WorkerLog.LONGEST - 1) + "😀" + "b";

        String said = WorkerLog.lines("{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\","
                + "\"text\":\"" + text + "\"}]}}").getFirst().text();

        assertEquals("a".repeat(WorkerLog.LONGEST - 1) + " …", said);
    }

    @Test
    void aLogNotWrittenYetReadsAsNothingAndAFailedSessionSaysSo() {
        assertEquals(new WorkerLog.Read(List.of(), 7), WorkerLog.read(folder.resolve("none.log"), 7));
        Line end = WorkerLog.lines("{\"type\":\"result\",\"subtype\":\"error_max_turns\",\"is_error\":true,"
                + "\"num_turns\":40,\"duration_ms\":900}").getFirst();
        assertEquals(new Line(Kind.END, "failed (error_max_turns), 40 turns, 1 s"), end);
        Line why = WorkerLog.lines("{\"type\":\"result\",\"subtype\":\"error_during_execution\",\"is_error\":true,"
                + "\"num_turns\":2,\"duration_ms\":0,\"errors\":[\"tool crashed\"]}").getFirst();
        assertEquals(new Line(Kind.END, "failed (error_during_execution), 2 turns, 0 s: tool crashed"), why);
        assertTrue(Kind.fromId("nope") == Kind.UNKNOWN && Kind.fromId("tool_call") == Kind.TOOL_CALL);
    }
}
