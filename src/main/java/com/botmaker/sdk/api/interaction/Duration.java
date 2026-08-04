package com.botmaker.sdk.api.interaction;

import java.util.concurrent.ThreadLocalRandom;

/**
 * How long to wait — as one value that knows its own unit, and that may be a <b>range</b> rather than a
 * single number.
 *
 * <pre>{@code
 * Wait.time(Duration.seconds(2));                                     // exactly 2s
 * Wait.time(Duration.between(Duration.ms(800), Duration.ms(1500)));   // 0.8s–1.5s, re-rolled each time
 * }</pre>
 *
 * <h2>Why a type and not a bare number</h2>
 *
 * <p>Two reasons, and the second is the one that matters for bots.
 *
 * <p><b>The unit.</b> {@code Wait.milliseconds(2)} and {@code Wait.seconds(2)} differ by a factor of a
 * thousand and read the same at a glance, and a slot typed {@code int} can only be edited as a number — the
 * Studio has nothing to dispatch a real editor on. A slot typed {@code Duration} is dispatched
 * <em>by type</em>, the same reasoning already recorded on {@link com.botmaker.sdk.api.vision.Precision}: an
 * {@code (method, argIndex)} table would silently stop firing the day {@code Wait} gains an overload.
 *
 * <p><b>The range.</b> A bot that waits exactly 1000ms between every action is a bot that is trivially
 * identifiable as one. Humanized delays are the normal case, not an advanced one — so the range is part of
 * the type every waiting call already takes, rather than a separate API a bot author has to go find. A fixed
 * duration is simply the range where both ends are equal, which is why {@link #millis()} is the only accessor
 * callers need: it re-rolls per call for a range and returns the constant otherwise.
 *
 * <p>Milliseconds are the storage unit throughout ({@link #minMillis()} / {@link #maxMillis()}); the
 * {@code seconds}/{@code minutes} factories are conversions, so {@code Duration.seconds(1.5)} and
 * {@code Duration.ms(1500)} are the same value. That also means the unit a bot author typed is not recoverable
 * from the value — the Studio's picker shows the largest unit that divides evenly, which reproduces what was
 * typed in every practical case.
 *
 * <p>Named {@code Duration} rather than {@code Delay} because it is the length of a span, not only a pause
 * before an action; a bot that also uses {@code java.time.Duration} in the same file will need to qualify one
 * of the two.
 *
 * @param minMillis the shortest this duration can be, in milliseconds; never negative
 * @param maxMillis the longest; equal to {@code minMillis} for a fixed duration, never less
 */
public record Duration(long minMillis, long maxMillis) {

    public Duration {
        if (minMillis < 0) {
            throw new IllegalArgumentException("a duration cannot be negative, got: " + minMillis + "ms");
        }
        if (maxMillis < minMillis) {
            throw new IllegalArgumentException(
                    "a duration's range must not end before it starts, got: " + minMillis + "–" + maxMillis + "ms");
        }
    }

    /** A fixed duration of {@code millis} milliseconds. */
    public static Duration ms(long millis) {
        return new Duration(millis, millis);
    }

    /** A fixed duration of {@code seconds} seconds; fractions are allowed ({@code 0.5} → 500ms). */
    public static Duration seconds(double seconds) {
        return ms(Math.round(seconds * 1000.0));
    }

    /** A fixed duration of {@code minutes} minutes; fractions are allowed ({@code 1.5} → 90s). */
    public static Duration minutes(double minutes) {
        return ms(Math.round(minutes * 60_000.0));
    }

    /**
     * A duration picked at random between {@code min} and {@code max} (both inclusive) <b>each time it is
     * used</b> — the humanized wait. The two bounds may be given in different units; only their millisecond
     * values matter. Order is not significant: the shorter of the two becomes the floor.
     *
     * <p>If either bound is itself a range, its own ends are used — {@code between} spans from the shortest
     * possible to the longest possible.
     */
    public static Duration between(Duration min, Duration max) {
        long lo = Math.min(min.minMillis, max.minMillis);
        long hi = Math.max(min.maxMillis, max.maxMillis);
        return new Duration(lo, hi);
    }

    /** True when this duration is a range, so {@link #millis()} varies from call to call. */
    public boolean isRange() {
        return maxMillis > minMillis;
    }

    /**
     * This duration in milliseconds — the value to actually sleep for. <b>Re-rolled on every call</b> when
     * this is a range, so two waits built from the same {@code Duration} are not the same length. Callers that
     * need to log or compare a wait must read this once and reuse it.
     */
    public long millis() {
        return isRange() ? ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1) : minMillis;
    }

    /** {@code "1.5s"} / {@code "800–1500ms"} — for {@link com.botmaker.sdk.api.Debug} lines, not for parsing. */
    @Override
    public String toString() {
        return isRange() ? minMillis + "–" + maxMillis + "ms" : minMillis + "ms";
    }
}
