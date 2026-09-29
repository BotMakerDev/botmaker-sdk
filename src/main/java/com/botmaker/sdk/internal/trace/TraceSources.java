package com.botmaker.sdk.internal.trace;

import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.util.TraceSource;
import com.botmaker.shared.Diag;

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

    /** The classes a line passes through on its way to {@code Diag}; the caller is the first class beyond them. */
    private static final Set<Class<?>> PASS_THROUGH = Set.of(Debug.class, Trace.class, TraceSources.class);

    private TraceSources() {}

    /** The source of the class that called into the tracing classes, or empty when there is none. */
    public static String caller() {
        return origin().source();
    }

    /**
     * Where the line is being written: the caller's source, and its class and method, which a host filters the
     * trace by. A lambda's body is named after the method it sits in ({@code lambda$body$0} is {@code body}).
     */
    public static Diag.Origin origin() {
        return Diag.Callers.first(f -> PASS_THROUGH.contains(f.getDeclaringClass()))
                .map(f -> new Diag.Origin(of(f.getDeclaringClass()), f.getClassName(),
                        Diag.Callers.method(f.getMethodName())))
                .orElse(Diag.Origin.named(""));
    }

    /** {@code type}'s source: its top-level class's {@link TraceSource}, or that class's simple name. */
    public static String of(Class<?> type) {
        Class<?> host = type.getNestHost();
        TraceSource named = host.getAnnotation(TraceSource.class);
        if (named != null && !named.value().isBlank()) return named.value().strip();
        return host.getSimpleName();
    }
}
