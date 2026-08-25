package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.util.Debug;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pauses the bot.
 *
 * <p>{@link #time(Duration)} is the one to reach for: a {@link Duration} carries its own unit, so the slot
 * reads as a length of time rather than a bare number, and it is the form the Studio gives a real editor to
 * (dispatched on the {@code Duration} type) — which is why it is what the Wait block inserts.
 *
 * <p>{@link #between(Duration, Duration)} is the humanized wait: a bot that pauses exactly 1000ms between
 * every action is trivially identifiable as one, so the randomization is part of the waiting API rather than
 * something a bot author has to go build. It is a <em>method</em> and not a range-valued {@code Duration}
 * deliberately — the SDK used to ship its own {@code Duration} record whose accessor re-rolled on every read,
 * and a value that answers differently each time you look at it is a trap regardless of how convenient the
 * slot is. Rolling at the call site means the number that is logged is the number that is slept.
 *
 * <p>The type is {@code java.time.Duration}, not a BotMaker one. It already models a length of time, every
 * Java author knows it, and the duplicate simple name was itself a hazard — the Studio's import table mapped
 * the bare name {@code Duration} to {@code java.time} while the picker inserted the SDK's, so anything
 * resolving that name imported the wrong class.
 *
 * <p>{@link #milliseconds(int)} / {@link #seconds(double)} remain as literal shorthands — they are what a
 * one-off fixed pause reads best as, and the SDK itself uses them for its own fixed poll intervals.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): five of the six are offered, the paragraph above
 * having already decided four of them. The one hidden is {@link #seconds(int)}, which is not a second
 * operation but a second spelling — {@code seconds(5)} and {@code seconds(5.0)} sleep the same 5000ms, and
 * the class javadoc's own list of the shorthands names {@code milliseconds(int)} and {@code seconds(double)}
 * and passes over the {@code int} form in silence. Offering both puts a choice in the menu that has no
 * consequence. It stays public: every existing {@code Wait.seconds(5)} still binds to it and still compiles.
 *
 * <p>{@link #time(Duration)} and {@link #between(Duration, Duration)} <b>are</b> offered, which is worth
 * saying because {@code Duration} is a JDK type and this sweep hid {@code ZoneId} and {@code OcrOptions}
 * parameters for being JDK-typed. That was never the rule. The rule is <em>an argument the editor cannot
 * produce</em>, and {@code Duration} is the opposite case: Studio ships a real editor for it
 * ({@code DurationPicker}, {@code DurationFields}), it is a declarable variable type, and the paragraph above
 * records that this is precisely why the Wait block inserts the {@code Duration} form. A JDK type with a
 * picker is fillable; an SDK type without one would not be.
 */
public class Wait {

    /**
     * Waits for {@code duration}.
     *
     * @param duration how long to wait; null, zero or negative returns immediately
     */
    public static void time(Duration duration) {
        if (duration == null) return;
        long ms = duration.toMillis();
        if (ms <= 0) return;
        Debug.log("[Wait] " + ms + "ms");
        sleep(ms);
    }

    /**
     * Waits a random length between {@code min} and {@code max} (both inclusive), re-rolled on every call —
     * the humanized pause. Order is not significant: the shorter of the two is the floor. The two bounds may
     * be written in different units; only their millisecond values matter.
     *
     * @param min one end of the range; null falls back to waiting the other end
     * @param max the other end
     */
    public static void between(Duration min, Duration max) {
        if (min == null || max == null) {
            time(min == null ? max : min);
            return;
        }
        long lo = Math.min(min.toMillis(), max.toMillis());
        long hi = Math.max(min.toMillis(), max.toMillis());
        if (hi <= 0) return;
        // One roll, then log it: the value reported has to be the value slept, which is the whole reason the
        // randomization lives here and not behind an accessor.
        long ms = lo == hi ? lo : ThreadLocalRandom.current().nextLong(Math.max(lo, 0), hi + 1);
        if (ms <= 0) return;
        Debug.log("[Wait] " + ms + "ms (of " + lo + "–" + hi + "ms)");
        sleep(ms);
    }

    /**
     * Waits for the specified number of milliseconds.
     *
     * @param milliseconds Time to wait
     */
    public static void milliseconds(int milliseconds) {
        if (milliseconds <= 0) return;
        Debug.log("[Wait] " + milliseconds + "ms");
        sleep(milliseconds);
    }

    /**
     * Waits for the specified number of seconds.
     *
     * @param seconds Time to wait
     */
    public static void seconds(int seconds) {
        milliseconds(seconds * 1000);
    }

    /**
     * Waits for the specified number of seconds (supports fractions).
     *
     * @param seconds Time to wait (can be fractional, e.g., 0.5)
     */
    public static void seconds(double seconds) {
        milliseconds((int) (seconds * 1000));
    }

    /**
     * Waits for the specified number of minutes (supports fractions).
     *
     * @param minutes Time to wait (can be fractional, e.g., 1.5)
     */
    public static void minutes(double minutes) {
        time(Duration.ofMillis(Math.round(minutes * 60_000.0)));
    }

    /** Sleeps {@code ms}, restoring the interrupt flag so a stop request isn't swallowed by the pause. */
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
