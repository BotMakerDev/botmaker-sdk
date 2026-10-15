package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.sdk.internal.trace.MissFrames;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * How long one assistant tool call took, and where the time went. Every SDK tool's reply ends with a line saying it
 * ("Took 18.6 s: matching 9.2, reading text 5.0, settling 4.1."), so a slow tool shows itself on every call and
 * the next session knows which part to make faster.
 *
 * <p>A call's parts are timed on the thread that runs it, at the few places all of them go through:
 * {@link Frames#look} (capturing), {@link ActTools#ambient} (watching motion: the screen read before acting, to
 * learn what moves on its own), {@link ActTools#answered} (settling: the game given time to answer),
 * {@link LookTools#best} (matching a picture), {@link LookTools#recognise} (reading text), wait_for_picture's
 * pauses between looks (waiting) and {@link FxCall#call} (reading and writing the bot through Studio). A part run inside another counts toward the outer one only, so the parts never
 * add up past the total: settling's own captures are settling. Work handed to another thread is not counted.
 */
final class ToolTiming {

    /** Where a call spends its time. */
    enum Phase {
        CAPTURE("capturing"),
        MOTION("watching motion"),
        SETTLE("settling"),
        MATCH("matching"),
        TEXT("reading text"),
        WAIT("waiting"),
        STUDIO("reading and writing the bot");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** One timed part; {@code E} lets a part that waits throw its {@link InterruptedException}. */
    @FunctionalInterface
    interface Part<T, E extends Exception> {
        T get() throws E;
    }

    private static final ThreadLocal<ToolTiming> CURRENT = new ThreadLocal<>();
    /** A part shorter than this is not named: noise beside the call's total. */
    private static final long SHOWN_NANOS = 50_000_000L;

    private final long[] spent = new long[Phase.values().length];
    private boolean inside;

    private ToolTiming() {}

    /** {@code tool}, its reply ending with how long it took, and the call a row of the project's {@link #FILE}. */
    static <P extends Record> AssistantTool<P> timed(AssistantTool<P> tool) {
        return AssistantTool.named(tool.name()).describedAs(tool.description()).takes(tool.paramsType())
                .handledBy((params, context) -> time(tool.name(), context == null || context.services() == null
                        ? null : context.services().projectDir(), () -> tool.invoke(arguments(params), context)));
    }

    /** Runs {@code call} and adds the line saying how long it took and on what. */
    static AgentReply time(Supplier<AgentReply> call) {
        return time(null, null, call);
    }

    /** As {@link #time(Supplier)}, and a row of {@code project}'s {@link #FILE} for {@code tool}, when both are given. */
    static AgentReply time(String tool, Path project, Supplier<AgentReply> call) {
        if (CURRENT.get() != null) return call.get();
        ToolTiming timing = new ToolTiming();
        CURRENT.set(timing);
        long started = System.currentTimeMillis();
        long start = System.nanoTime();
        try {
            AgentReply reply = call.get();
            long total = System.nanoTime() - start;
            if (tool != null && project != null) timing.write(project.resolve(FILE), tool, started, total);
            // On a line of its own: a client may run the reply's text parts together.
            return reply.and(AgentReply.text("\n" + timing.line(total)));
        } finally {
            CURRENT.remove();
        }
    }

    /**
     * Where each call's times go, in the bot's folder beside the runs' frames, one row a call: which waits to cut
     * is read off it rather than guessed (2026-10-14).
     */
    static final Path FILE = MissFrames.FOLDER.resolve("tool-times.tsv");

    /** The file's first line. */
    static String header() {
        return "started\ttool\ttotal_ms\t" + Arrays.stream(Phase.values())
                .map(phase -> phase.name().toLowerCase(Locale.ROOT) + "_ms").collect(Collectors.joining("\t"));
    }

    /** One call's row: when it began, the tool, its total and each part, in milliseconds. */
    String row(String tool, long startedMs, long totalNanos) {
        return Instant.ofEpochMilli(startedMs) + "\t" + tool + "\t" + totalNanos / 1_000_000 + "\t"
                + Arrays.stream(spent).mapToObj(nanos -> String.valueOf(nanos / 1_000_000))
                .collect(Collectors.joining("\t"));
    }

    /** Appends the row, one call at a time; a file that cannot be written loses the row, never the call. */
    private void write(Path file, String tool, long startedMs, long totalNanos) {
        synchronized (ToolTiming.class) {
            append(file, tool, startedMs, totalNanos);
        }
    }

    private void append(Path file, String tool, long startedMs, long totalNanos) {
        try {
            Files.createDirectories(file.getParent());
            String lines = (Files.exists(file) ? "" : header() + "\n") + row(tool, startedMs, totalNanos) + "\n";
            Files.writeString(file, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException unwritten) {
            // Times are for the next session to read; a call never fails for them.
        }
    }

    /** Runs {@code part}, counting its time toward {@code phase} when a tool call on this thread is being timed. */
    static <T, E extends Exception> T timed(Phase phase, Part<T, E> part) throws E {
        ToolTiming timing = CURRENT.get();
        if (timing == null || timing.inside) return part.get();
        timing.inside = true;
        long start = System.nanoTime();
        try {
            return part.get();
        } finally {
            timing.spent[phase.ordinal()] += System.nanoTime() - start;
            timing.inside = false;
        }
    }

    /** "Took 18.6 s: matching 9.2, settling 4.1." — the parts that count, longest first. */
    String line(long totalNanos) {
        String parts = Arrays.stream(Phase.values())
                .filter(phase -> spent[phase.ordinal()] >= SHOWN_NANOS)
                .sorted(Comparator.comparingLong((Phase phase) -> spent[phase.ordinal()]).reversed())
                .map(phase -> phase.label() + " " + seconds(spent[phase.ordinal()]))
                .collect(Collectors.joining(", "));
        return "Took " + seconds(totalNanos) + " s" + (parts.isEmpty() ? "." : ": " + parts + ".");
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e9);
    }

    /**
     * {@code params} as the arguments that build it again: what the timed tool hands the tool it wraps, whose
     * handler takes only what {@link AssistantTool#invoke} builds. A choice goes back as its constant's name.
     */
    static Map<String, Object> arguments(Record params) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (RecordComponent component : params.getClass().getRecordComponents()) {
            Object value;
            try {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                value = accessor.invoke(params);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read " + component.getName(), e);
            }
            if (value == null) continue;
            arguments.put(component.getName(), value instanceof Enum<?> choice ? choice.name() : value);
        }
        return arguments;
    }
}
