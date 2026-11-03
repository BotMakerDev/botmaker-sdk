package com.botmaker.sdk.internal.trace;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The SDK's lines above debug, at the level the run's report counts them by (spec {@code
 * docs/superpowers/specs/2026-11-03-run-reports-design.md} §1): {@link #info} the bot's code handled it, {@link #warn}
 * the SDK's fallback rescued the run, an error ({@code Debug.error}) nobody did. Each is written under the calling
 * class, as {@code Debug}'s lines are.
 *
 * <p><b>Said once.</b> A failure the SDK warns of where it happens ({@code Navigate.to} stuck) and then throws is
 * {@linkplain #told told}, so whoever recovers from the throw says the recovery as an info, not a second problem.
 */
public final class Say {

    private static final Set<Throwable> TOLD = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private Say() {}

    /** A fallback rescued the run: printed and kept whether debugging is on or off. */
    public static void warn(String message) {
        SdkRun.warn(TraceSources.origin(), message);
    }

    /** What the bot's code handled, said even with debugging off. */
    public static void info(String message) {
        SdkRun.info(TraceSources.origin(), message);
    }

    /** {@link #warn(String)} carrying {@code values} of the SDK's own ({@link SdkRun#warn(SdkRun.Origin, String, Map)}). */
    public static void warn(String message, Map<String, String> values) {
        SdkRun.warn(TraceSources.origin(), message, values);
    }

    /** {@link #info(String)} carrying {@code values}, as {@link #warn(String, Map)}. */
    public static void info(String message, Map<String, String> values) {
        SdkRun.info(TraceSources.origin(), message, values);
    }

    /** Nobody handled it, said whether debugging is on or off, carrying {@code values} as {@link #warn(String, Map)}. */
    public static void error(String message, Map<String, String> values) {
        SdkRun.error(TraceSources.origin(), message, null, values);
    }

    /** {@code thrown}, after its problem was said where it happened; returns it, to throw. */
    public static <T extends Throwable> T told(T thrown) {
        TOLD.add(thrown);
        return thrown;
    }

    /** Whether {@code thrown}'s problem was said already: it, or one it wraps, was {@linkplain #told told}. */
    public static boolean wasTold(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (TOLD.contains(t)) return true;
        }
        return false;
    }
}
