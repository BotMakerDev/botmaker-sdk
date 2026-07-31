package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.Debug;

/**
 * Utility class for delays and waiting.
 */
public class Wait {

    /**
     * Waits for the specified number of milliseconds.
     *
     * @param milliseconds Time to wait
     */
    public static void milliseconds(int milliseconds) {
        if (milliseconds <= 0) return;
        Debug.log("[Wait] " + milliseconds + "ms");

        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        milliseconds((int)(seconds * 1000));
    }
}
