package com.botmaker.sdk.api.vision;

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

    /**
     * Whether to print debug information during matching.
     */
    public static boolean DEBUG_MODE = false;

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

    public static void enableDebugMode(boolean enable) {
        DEBUG_MODE = enable;
    }

    /**
     * Resets all settings to their defaults.
     */
    public static void resetToDefaults() {
        DEFAULT_FOUND_DELAY = 500;
        DEFAULT_NOT_FOUND_DELAY = 200;
        RANDOMIZE_CLICKS = true;
        DEFAULT_CONFIDENCE = 0.8;
        DEFAULT_COMPARE_MARGIN = 0.05;
        MAX_RETRY_ATTEMPTS = 20;
        DEBUG_MODE = false;
    }
}