package com.botmaker.sdk.internal.trace;

import com.botmaker.plugin.api.TraceLine;
import com.botmaker.plugin.api.run.RunChannel;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.observe.IpcObserver;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * The SDK's end of the run channel (spec {@code docs/superpowers/specs/2026-11-03-run-reports-design.md} §7): the
 * channel the host handed the SDK for this run, and the one way the bot half writes a line to it. Every SDK line
 * goes through here; none through {@code botmaker-shared}.
 *
 * <p>A run no host started keeps {@link RunChannel#console()}: lines on the console, no log, no telemetry.
 */
public final class SdkRun {

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    /** A leading {@code [Name]} longer than this is text, not a source. */
    private static final int MAX_SOURCE = 32;

    /** The value key of the screen the game was last seen on, which every line said while one is known carries. */
    public static final String SCREEN = "screen";

    private static volatile RunChannel channel = RunChannel.console();
    /** The screen the game was last seen on, as the map shows it; null while none is known. */
    private static volatile String screen;
    /** Said once, as the run's JVM ends: the SDK's lines that sum the run up (the time on each screen). */
    private static volatile Runnable atEnd;
    private static volatile boolean hooked;

    private SdkRun() {}

    /** The host's channel for this run, before the bot's {@code main}: {@code SdkPlugin}'s {@code onBotRun}. */
    public static void started(RunChannel given) {
        if (given == null) return;
        channel = given;
        IpcObserver.install();
        hook();
    }

    /** The screen the game is on now, as the map shows it ({@code null}: none known); later lines carry it. */
    public static void screen(String shown) {
        screen = shown == null || shown.isBlank() ? null : shown;
    }

    /**
     * What sums the run up, said once as its JVM ends, through the channel then: a host's run log stays open to the
     * end, its lines flushed one by one, so the hook that ends the host's run and this one may run in either order.
     */
    public static void atEnd(Runnable summary) {
        atEnd = summary;
    }

    private static synchronized void hook() {
        if (hooked) return;
        hooked = true;
        Runtime.getRuntime().addShutdownHook(new Thread(SdkRun::end, "botmaker-sdk-run-end"));
    }

    /** Says {@link #atEnd}'s lines once; a summary that throws costs only itself. */
    static void end() {
        Runnable summary = atEnd;
        atEnd = null;
        if (summary == null) return;
        try {
            summary.run();
        } catch (RuntimeException ignored) {
            // the run's own lines are written already
        }
    }

    /** Back to {@link RunChannel#console()}, with no observer encoding frames nobody reads. */
    public static void ended() {
        channel = RunChannel.console();
        screen = null;
        IpcObserver.uninstall();
    }

    /** This run's channel. */
    public static RunChannel channel() {
        return channel;
    }

    /** Where a line was written: the source it is shown under, and the class and method that wrote it. */
    public record Origin(String source, String className, String method) {
        public Origin {
            source = source == null ? "" : source.strip();
            className = className == null ? "" : className;
            method = method == null ? "" : method;
        }
    }

    /** A debug line, built only when the channel keeps one. */
    public static void debug(Origin origin, String message, int count, Rect where) {
        RunChannel to = channel;
        if (!to.recorded()) return;
        write(to, TraceLine.Level.DEBUG, origin, message, count, where, null, Map.of());
    }

    /** A debug line under {@code source}, written by the first class calling in. */
    public static void debug(String source, String message) {
        RunChannel to = channel;
        if (!to.recorded()) return;
        write(to, TraceLine.Level.DEBUG, named(source), message, 1, null, null, Map.of());
    }

    /** What the bot's code handled, said even with debugging off. */
    public static void info(Origin origin, String message) {
        info(origin, message, Map.of());
    }

    /**
     * {@link #info(Origin, String)} carrying {@code values} besides the screen: keys of the SDK's own, which its
     * report views count ({@code plugin/run/SdkReportViews}); one named {@value #SCREEN} wins over the one known.
     */
    public static void info(Origin origin, String message, Map<String, String> values) {
        write(channel, TraceLine.Level.INFO, origin, message, 1, null, null, values);
    }

    /** {@link #warn(Origin, String)} carrying {@code values}, as {@link #info(Origin, String, Map)}. */
    public static void warn(Origin origin, String message, Map<String, String> values) {
        write(channel, TraceLine.Level.WARN, origin, message, 1, null, null, values);
    }

    /** {@link #error(Origin, String, Throwable)} carrying {@code values}, as {@link #info(Origin, String, Map)}. */
    public static void error(Origin origin, String message, Throwable cause, Map<String, String> values) {
        write(channel, TraceLine.Level.ERROR, origin, message, 1, null, cause, values);
    }

    /** {@link #info(Origin, String)} under {@code source}. */
    public static void info(String source, String message) {
        info(named(source), message);
    }

    /** A fallback rescued the run: said whether debugging is on or off. */
    public static void warn(Origin origin, String message) {
        warn(origin, message, Map.of());
    }

    /** {@link #warn(Origin, String)} under {@code source}. */
    public static void warn(String source, String message) {
        warn(named(source), message);
    }

    /** Nobody handled it; {@code cause} may be null. */
    public static void error(Origin origin, String message, Throwable cause) {
        error(origin, message, cause, Map.of());
    }

    /** The first frame beyond {@code skip}, and beyond this class's own. */
    public static Optional<StackWalker.StackFrame> first(Predicate<StackWalker.StackFrame> skip) {
        return WALKER.walk(frames -> frames
                .filter(f -> f.getDeclaringClass() != SdkRun.class && !skip.test(f))
                .findFirst());
    }

    /** {@code name}, or the method a lambda named {@code lambda$<method>$<n>} was written in. */
    public static String method(String name) {
        if (name == null || !name.startsWith("lambda$")) return name;
        int end = name.indexOf('$', "lambda$".length());
        return end < 0 ? name : name.substring("lambda$".length(), end);
    }

    /** {@code source}, written by the first class calling into this one. */
    private static Origin named(String source) {
        return first(f -> false)
                .map(f -> new Origin(source, f.getClassName(), method(f.getMethodName())))
                .orElse(new Origin(source, "", ""));
    }

    private static void write(RunChannel to, TraceLine.Level level, Origin origin, String message, int count,
                              Rect where, Throwable cause, Map<String, String> given) {
        Map<String, String> values = new LinkedHashMap<>();
        String known = screen;
        if (known != null) values.put(SCREEN, known);
        if (given != null) values.putAll(given);
        send(to, level, origin, message, count, where, cause, values);
    }

    private static void send(RunChannel to, TraceLine.Level level, Origin origin, String message, int count,
                             Rect where, Throwable cause, Map<String, String> values) {
        Origin given = origin == null ? new Origin("", "", "") : origin;
        String text = message == null ? "" : message;
        String source = sourceOf(text);
        if (source.isEmpty()) {
            source = given.source();
        } else {
            text = text.substring(source.length() + 2).stripLeading();
        }
        Optional<TraceLine.Region> region = where == null ? Optional.empty()
                : Optional.of(new TraceLine.Region(where.x(), where.y(), where.width(), where.height()));
        to.line(new TraceLine(Instant.now(), level, source, text, count, given.className(), given.method(), "",
                OptionalInt.empty(), region, values), cause);
    }

    /** The name in a leading {@code [Name]}, or empty when {@code message} does not start with one. */
    static String sourceOf(String message) {
        if (message == null || !message.startsWith("[")) return "";
        int close = message.indexOf(']');
        if (close < 2 || close > MAX_SOURCE + 1) return "";
        String name = message.substring(1, close);
        return name.isBlank() || !name.strip().equals(name) ? "" : name;
    }
}
