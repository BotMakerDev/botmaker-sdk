// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A worker's log read while it is written: {@code claude -p --output-format stream-json --verbose} writes one JSON
 * event per line as the session goes, and each becomes the lines a person reads (what it thought, said, called, was
 * answered, and how it ended). A log holds every session of its stop, one after the other, each opened by a
 * {@link Kind#START} line.
 */
public final class WorkerLog {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** What every bot tool's name starts with in a worker's session: dropped from the lines. */
    private static final String TOOL_PREFIX = "mcp__" + ClaudeCli.SERVER + "__";
    /** The longest a line's text runs: a tool's answer can be a whole map. */
    static final int LONGEST = 4000;
    /** The most one read takes of a log: a long one is read over several calls. */
    static final int WINDOW = 4 << 20;
    /** What a line too long to read reads as. */
    static final String TOO_LONG = "(a line too long to show)";

    private WorkerLog() {}

    /** What a line of the log is. */
    public enum Kind {
        START("start", "Started"),
        THINKING("thinking", "Thinking"),
        SAID("said", "Said"),
        TOOL_CALL("tool_call", "Called"),
        TOOL_ANSWER("tool_answer", "Answered"),
        END("end", "Ended"),
        /** A line that is not one of the session's events: a notice of the client, an error it printed. */
        OUTPUT("output", "Printed"),
        UNKNOWN("unknown", "Unknown");

        private final String id;
        private final String displayName;

        Kind(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static Kind fromId(String id) {
            return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst().orElse(UNKNOWN);
        }
    }

    /** One line a person reads: its kind and its text. */
    public record Line(Kind kind, String text) {}

    /** The lines read from an offset on, and the offset the next read starts at. */
    public record Read(List<Line> lines, long next) {}

    /**
     * The whole lines of {@code log} from the byte {@code from} on, and where the next read starts: a last line not yet
     * ended by its newline is left for that read. A log not written yet reads as nothing.
     */
    public static Read read(Path log, long from) {
        return read(log, from, WINDOW);
    }

    /**
     * As {@link #read(Path, long)}, reading at most {@code window} bytes: a long log is read over several calls, and a
     * line longer than the window is skipped whole once its end is written.
     */
    static Read read(Path log, long from, int window) {
        Read none = new Read(List.of(), from);
        if (!Files.isRegularFile(log)) return none;
        try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "r")) {
            long size = file.length();
            if (size <= from) return none;
            byte[] bytes = new byte[(int) Math.min(size - from, window)];
            file.seek(from);
            file.readFully(bytes);
            int end = lastNewline(bytes);
            if (end >= 0) {
                List<Line> lines = new ArrayList<>();
                // A newline byte is never inside a UTF-8 character, so the whole lines decode alone.
                for (String raw : new String(bytes, 0, end, StandardCharsets.UTF_8).split("\n")) {
                    lines.addAll(lines(raw));
                }
                return new Read(List.copyOf(lines), from + end + 1);
            }
            if (bytes.length < window) return none;
            for (long at = from + bytes.length; at < size; ) {
                int n = file.read(bytes, 0, (int) Math.min(size - at, window));
                if (n <= 0) break;
                for (int i = 0; i < n; i++) {
                    if (bytes[i] == '\n') return new Read(List.of(new Line(Kind.OUTPUT, TOO_LONG)), at + i + 1);
                }
                at += n;
            }
            return none;
        } catch (IOException unread) {
            return none;
        }
    }

    /**
     * Until when, epoch milliseconds, the user's usage limit refused the session written to {@code log} from the byte
     * {@code from} on: the {@code resetsAt} of its last {@code rate_limit_event} whose status is {@code rejected}; 0 when
     * none refused it (or nothing is written).
     */
    static long limitedUntil(Path log, long from) {
        if (!Files.isRegularFile(log)) return 0;
        try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "r")) {
            if (file.length() <= from) return 0;
            byte[] bytes = new byte[(int) Math.min(file.length() - from, WINDOW)];
            file.seek(from);
            file.readFully(bytes);
            long until = 0;
            for (String raw : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
                if (!raw.contains("\"rate_limit_event\"")) continue;
                try {
                    JsonNode info = JSON.readTree(raw).path("rate_limit_info");
                    if ("rejected".equals(info.path("status").asText())) {
                        until = Math.max(until, info.path("resetsAt").asLong() * 1000);
                    }
                } catch (IOException notJson) {
                    // a line cut by a kill: not the limit's
                }
            }
            return until;
        } catch (IOException unread) {
            return 0;
        }
    }

    private static int lastNewline(byte[] bytes) {
        for (int i = bytes.length - 1; i >= 0; i--) if (bytes[i] == '\n') return i;
        return -1;
    }

    /**
     * {@code line} as a worker's log keeps it: a picture in it without its data, which only makes the log heavy
     * (a picture reads {@code [picture]} anyway); any other line as it is.
     */
    static String slim(String line) {
        if (!line.contains("\"image\"")) return line;
        try {
            JsonNode event = JSON.readTree(line);
            if (event == null || !event.isContainerNode()) return line;
            dropPictures(event);
            return event.toString();
        } catch (IOException notJson) {
            return line;
        }
    }

    private static void dropPictures(JsonNode node) {
        if (node instanceof ObjectNode object && "image".equals(object.path("type").asText())
                && object.get("source") instanceof ObjectNode source) {
            source.remove("data");
        }
        node.forEach(WorkerLog::dropPictures);
    }

    /** The lines one line of the log reads as: none for an event nobody needs to read. */
    static List<Line> lines(String raw) {
        String line = raw.strip();
        if (line.isEmpty()) return List.of();
        JsonNode event;
        try {
            event = JSON.readTree(line);
        } catch (IOException notJson) {
            return List.of(new Line(Kind.OUTPUT, clip(line)));
        }
        if (event == null || !event.isObject()) return List.of(new Line(Kind.OUTPUT, clip(line)));
        return switch (event.path("type").asText()) {
            case "system" -> "init".equals(event.path("subtype").asText())
                    ? List.of(new Line(Kind.START, event.path("model").asText("")))
                    : List.of();
            case "assistant" -> said(event.path("message").path("content"));
            case "user" -> answered(event.path("message").path("content"));
            case "result" -> List.of(new Line(Kind.END, ended(event)));
            default -> List.of();
        };
    }

    private static List<Line> said(JsonNode content) {
        List<Line> lines = new ArrayList<>();
        for (JsonNode block : content) {
            switch (block.path("type").asText()) {
                case "thinking" -> text(block.path("thinking").asText()).ifPresent(t -> lines.add(
                        new Line(Kind.THINKING, t)));
                case "text" -> text(block.path("text").asText()).ifPresent(t -> lines.add(new Line(Kind.SAID, t)));
                case "tool_use" -> lines.add(new Line(Kind.TOOL_CALL, clip(toolName(block.path("name").asText())
                        + " " + block.path("input").toString())));
                default -> { }
            }
        }
        return lines;
    }

    private static List<Line> answered(JsonNode content) {
        List<Line> lines = new ArrayList<>();
        for (JsonNode block : content) {
            if (!"tool_result".equals(block.path("type").asText())) continue;
            String text = answerText(block.path("content"));
            lines.add(new Line(Kind.TOOL_ANSWER, clip(block.path("is_error").asBoolean(false) ? "refused: " + text
                    : text)));
        }
        return lines;
    }

    /** A tool's answer as text: its text parts, a picture named as one. */
    private static String answerText(JsonNode content) {
        if (content.isTextual()) return content.asText().strip();
        List<String> parts = new ArrayList<>();
        for (JsonNode part : content) {
            switch (part.path("type").asText()) {
                case "text" -> parts.add(part.path("text").asText().strip());
                case "image" -> parts.add("[picture]");
                default -> { }
            }
        }
        return String.join("\n", parts);
    }

    /** How a session ended: its turns and time, and its last words or its error. */
    private static String ended(JsonNode result) {
        String how = result.path("is_error").asBoolean(false) ? "failed (" + result.path("subtype").asText() + ")"
                : "done";
        String took = result.path("num_turns").asInt(0) + " turns, "
                + Math.round(result.path("duration_ms").asLong(0) / 1000.0) + " s";
        String words = result.path("result").asText("").strip();
        if (words.isEmpty() && result.path("errors").isArray()) {
            List<String> errors = new ArrayList<>();
            result.path("errors").forEach(error -> errors.add(error.isTextual() ? error.asText()
                    : error.path("message").asText(error.toString())));
            words = String.join("; ", errors).strip();
        }
        return clip(how + ", " + took + (words.isEmpty() ? "" : ": " + words));
    }

    private static String toolName(String name) {
        return name.startsWith(TOOL_PREFIX) ? name.substring(TOOL_PREFIX.length()) : name;
    }

    private static Optional<String> text(String text) {
        String kept = text.strip();
        return kept.isEmpty() ? Optional.empty() : Optional.of(clip(kept));
    }

    private static String clip(String text) {
        if (text.length() <= LONGEST) return text;
        // Never half a character: a cut between a surrogate pair would show a broken glyph.
        int cut = Character.isHighSurrogate(text.charAt(LONGEST - 1)) ? LONGEST - 1 : LONGEST;
        return text.substring(0, cut) + " …";
    }
}
