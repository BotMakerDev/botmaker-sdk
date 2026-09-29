package com.botmaker.sdk.internal.trace;

import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.util.TraceSource;

import java.util.Set;

/**
 * The source a debug line is traced under, deduced from the class that wrote it, so no line spells its own
 * {@code [Name]} ({@code docs/refactor/40-run-trace.md}). The class is the first caller on the stack that is
 * not one of the tracing classes themselves; its name is its top-level class's {@link TraceSource}, or else
 * that class's simple name.
 *
 * <p>The stack is walked only for a line that is printed, which is only while debugging is on.
 */
public final class TraceSources {

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** The classes a line passes through on its way to {@code Diag}; the caller is the first class beyond them. */
    private static final Set<Class<?>> PASS_THROUGH = Set.of(Debug.class, Trace.class, TraceSources.class);

    private TraceSources() {}

    /** The source of the class that called into the tracing classes, or empty when there is none. */
    public static String caller() {
        return WALKER.walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(c -> !PASS_THROUGH.contains(c))
                        .findFirst())
                .map(TraceSources::of)
                .orElse("");
    }

    /** {@code type}'s source: its top-level class's {@link TraceSource}, or that class's simple name. */
    public static String of(Class<?> type) {
        Class<?> host = type.getNestHost();
        TraceSource named = host.getAnnotation(TraceSource.class);
        if (named != null && !named.value().isBlank()) return named.value().strip();
        return host.getSimpleName();
    }
}
