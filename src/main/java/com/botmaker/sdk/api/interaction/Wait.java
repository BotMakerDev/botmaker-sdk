package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.Debug;

/**
 * Pauses the bot.
 *
 * <p>{@link #time(Duration)} is the one to reach for: a {@link Duration} carries its own unit, so the slot
 * reads as a length of time rather than a bare number, and it can be a <em>range</em> —
 * {@code Wait.time(Duration.between(Duration.ms(800), Duration.ms(1500)))} waits a different amount each time,
 * which is what a bot that shouldn't look like one wants. It is also the form the Studio gives a real editor
 * to (dispatched on the {@code Duration} type), so it is what the Wait block inserts.
 *
 * <p>{@link #milliseconds(int)} / {@link #seconds(double)} remain as literal shorthands — they are what a
 * one-off fixed pause reads best as, and the SDK itself uses them for its own fixed poll intervals.
 */
public class Wait {

    /**
     * Waits for {@code duration}. A range duration is rolled here, so each call waits a different amount
     * within it.
     *
     * @param duration how long to wait; null or zero-length returns immediately
     */
    public static void time(Duration duration) {
        if (duration == null) return;
        long ms = duration.millis();   // one roll: the logged value must be the slept value
        if (ms <= 0) return;
        Debug.log("[Wait] " + ms + "ms" + (duration.isRange() ? " (of " + duration + ")" : ""));
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
        time(Duration.minutes(minutes));
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
