package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Debug;
import com.botmaker.shared.capture.NativeControllerFactory;

/**
 * Global configuration for click behavior and delays.
 * Mimics your templateFound delay logic from C++.
 */
public class ClickConfig {

    /**
     * Delay after successful template match (in milliseconds).
     * Corresponds to your "waitfound" setting.
     */
    public static int DEFAULT_FOUND_DELAY = 500;

    /**
     * Delay after failed template match (in milliseconds).
     * Corresponds to your "waitnotfound" setting.
     */
    public static int DEFAULT_NOT_FOUND_DELAY = 200;

    /**
     * Whether to use randomized click points within templates.
     * When true, clicks use getRandomClickPoint() instead of getCenter().
     * This creates more human-like behavior.
     */
    public static boolean RANDOMIZE_CLICKS = true;

    /**
     * Default confidence threshold for template matching (0.0 to 1.0).
     * Lower values are more permissive but may cause false positives.
     */
    public static double DEFAULT_CONFIDENCE = 0.8;

    /**
     * Default margin the "good" template must beat every "bad" (distractor) template by, at the
     * same location, for {@link ImageFinder#findCompare}/{@link ImageClicker#clickCompare} to accept
     * the match. Scores are TM_CCOEFF_NORMED (0.0 to 1.0); a good match of two visually-similar
     * templates (e.g. active vs. greyed-out) only counts when {@code goodScore - badScore >= margin}.
     */
    public static double DEFAULT_COMPARE_MARGIN = 0.05;

    /**
     * How many consecutive no-progress checks the {@link com.botmaker.sdk.api.bot.Watchdog} tolerates
     * before it throws {@link com.botmaker.sdk.api.bot.BotStuckException} at the next
     * {@code Watchdog.checkpoint()} — i.e. how long a frozen screen or a repeated no-op click is allowed
     * to run before the bot is considered stuck and restarted.
     */
    public static int MAX_RETRY_ATTEMPTS = 20;

    // Configuration methods

    public static void setFoundDelay(int milliseconds) {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        DEFAULT_FOUND_DELAY = milliseconds;
    }

    public static void setNotFoundDelay(int milliseconds) {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        DEFAULT_NOT_FOUND_DELAY = milliseconds;
    }

    public static void enableRandomClicks(boolean enable) {
        RANDOMIZE_CLICKS = enable;
    }

    public static void setDefaultConfidence(double confidence) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        DEFAULT_CONFIDENCE = confidence;
    }

    public static void setMaxRetryAttempts(int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("Max attempts must be at least 1");
        }
        MAX_RETRY_ATTEMPTS = attempts;
    }

    /**
     * Toggles the SDK's global debug output. Kept here for discoverability alongside the other tuning knobs,
     * but it is a thin delegate to the single global switch {@link Debug} — vision, lifecycle, and launch
     * traces all share one flag now.
     */
    public static void enableDebugMode(boolean enable) {
        Debug.set(enable);
    }

    /**
     * Whether this bot drives the <b>real</b> mouse and keyboard instead of sending quiet synthetic events to
     * the target window. Read-only mirror of the last {@link #useRealInput} call.
     */
    public static boolean REAL_INPUT = false;

    /**
     * Switch to real device input — call this when the target is a <b>game</b>.
     *
     * <p>By default BotMaker delivers synthetic events straight to the target window, which clicks a
     * background window without ever moving the cursor. Games (and anything else reading raw input) ignore
     * those events by design: on X11 they carry a {@code send_event} flag the client rejects, and on Windows
     * they land in a message queue a raw-input game never reads. The click is dropped silently — neither OS
     * reports delivery — which is why nothing can auto-detect this and why it is a setting.
     *
     * <p>Turning it on trades background operation for the click landing: the pointer moves to each target and
     * returns to where it was, and the target window is raised, because real input goes to whatever is
     * topmost.
     *
     * <p><b>One-way.</b> On Linux this swaps the process-wide input backend, which cannot be swapped back, so
     * {@code useRealInput(false)} only prevents a future escalation rather than undoing one. Call it once,
     * before the first click — generated projects put it at the top of {@code main}.
     */
    public static void useRealInput(boolean enable) {
        REAL_INPUT = enable;
        if (enable) {
            boolean ok = NativeControllerFactory.get().useReliableInput();
            Debug.log("[Input] real device input " + (ok ? "active" : "UNAVAILABLE — clicks may not register"));
        }
    }

    /**
     * Resets the click/vision tuning to its defaults. Does not touch the global {@link Debug} switch — that has
     * its own lifecycle (project default + runtime toggle).
     */
    public static void resetToDefaults() {
        DEFAULT_FOUND_DELAY = 500;
        DEFAULT_NOT_FOUND_DELAY = 200;
        RANDOMIZE_CLICKS = true;
        DEFAULT_CONFIDENCE = 0.8;
        DEFAULT_COMPARE_MARGIN = 0.05;
        MAX_RETRY_ATTEMPTS = 20;
        // REAL_INPUT is deliberately not reset: the backend swap it caused is one-way, so clearing the flag
        // would only make it lie about how input is actually being delivered.
    }
}
