package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.util.Time;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.config.ProjectProperties;

import java.time.ZoneId;

/**
 * The bot's runtime tuning — how long it pauses around a match, how sure it has to be, and whether it drives
 * the real mouse and keyboard. The counterpart to {@link Source} for behaviour rather than "where to look":
 * one ambient value, seeded from the project on first use and overridable at runtime.
 *
 * <p>It reads its defaults from the project's {@code botmaker-project.properties} (the keys shared owns as
 * {@link ProjectProperties#KEY_CLICKS_FOUND_DELAY} and friends), so what Studio's <b>Input &amp; Clicks</b>
 * dialog saves is what a bot runs with — from the Studio, from the command line, or on someone else's
 * machine. A bot that wants to override one at runtime just calls the setter.
 *
 * <p><b>This replaces the generated {@code BotSettings.java}</b> Studio used to write into each project, whose
 * {@code apply()} was called at the top of {@code main}. The generated class stored the same values as Java
 * source that Studio read back with a per-statement regex; the properties file it now uses is the format
 * Studio and the SDK already share for everything else. Nothing calls {@code apply()} any more — see
 * {@link #useRealInput} for the one ordering constraint that made an explicit call look necessary, and why it
 * isn't.
 */
public final class BotSettings {

    /** Pause after a successful match, in ms — long enough for a game's animation to settle. */
    public static final int DEFAULT_FOUND_DELAY = 500;

    /** Pause after a failed match, in ms — how fast the bot retries when it doesn't see what it wants. */
    public static final int DEFAULT_NOT_FOUND_DELAY = 200;

    /** Whether clicks land on a random point inside the match rather than dead centre. */
    public static final boolean DEFAULT_RANDOMIZE_CLICKS = true;

    /** Template-match confidence (0..1). Lower finds more, and finds wrong things more. */
    public static final double DEFAULT_CONFIDENCE = 0.8;

    /**
     * How far the "good" template must beat every "bad" (distractor) template at the same location for
     * {@code ImageFinder.findCompare}/{@code ImageClicker.clickCompare} to accept the match. Scores are
     * TM_CCOEFF_NORMED (0..1), so two visually-similar templates (active vs. greyed-out) only resolve when
     * {@code goodScore - badScore >= margin}.
     */
    public static final double DEFAULT_COMPARE_MARGIN = 0.05;

    /**
     * How many consecutive no-progress checks {@link com.botmaker.sdk.api.bot.Watchdog} tolerates before it
     * throws {@link com.botmaker.sdk.api.bot.BotStuckException} at the next {@code checkpoint()} — how long a
     * frozen screen or a repeated no-op click may run before the bot counts as stuck and is restarted.
     */
    public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 20;

    /** Default timeout for waiting for game launch/window to appear, in milliseconds. */
    public static final long DEFAULT_LAUNCH_WAIT_TIMEOUT = 60000;

    private static volatile int foundDelay = DEFAULT_FOUND_DELAY;
    private static volatile int notFoundDelay = DEFAULT_NOT_FOUND_DELAY;
    private static volatile boolean randomizeClicks = DEFAULT_RANDOMIZE_CLICKS;
    private static volatile double confidence = DEFAULT_CONFIDENCE;
    private static volatile double compareMargin = DEFAULT_COMPARE_MARGIN;
    private static volatile int maxRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;
    private static volatile boolean realInput;
    private static volatile long defaultLaunchWaitTimeout = DEFAULT_LAUNCH_WAIT_TIMEOUT;
    private static volatile ZoneId defaultTimeZone = ZoneId.systemDefault();

    /**
     * Whether the project defaults have been folded in yet. Every read goes through {@link #ensureLoaded()},
     * which is what makes the values <em>accessors</em> rather than the public mutable fields the old
     * {@code ClickConfig} had: a bare field read cannot trigger a lazy load, so the project's values would
     * apply only if something happened to call a method first.
     */
    private static volatile boolean loaded;

    private BotSettings() {}

    // --- reads ---

    /** Pause after a successful match, in ms. */
    public static int foundDelay() {
        ensureLoaded();
        return foundDelay;
    }

    /** Pause after a failed match, in ms. */
    public static int notFoundDelay() {
        ensureLoaded();
        return notFoundDelay;
    }

    /** Whether clicks land on a random point inside the match rather than its centre. */
    public static boolean randomizeClicks() {
        ensureLoaded();
        return randomizeClicks;
    }

    /** The default template-match confidence (0..1) every no-confidence vision call uses. */
    public static double confidence() {
        ensureLoaded();
        return confidence;
    }

    /** The default margin a good template must beat a distractor by. See {@link #DEFAULT_COMPARE_MARGIN}. */
    public static double compareMargin() {
        ensureLoaded();
        return compareMargin;
    }

    /** How many no-progress checks the watchdog tolerates before declaring the bot stuck. */
    public static int maxRetryAttempts() {
        ensureLoaded();
        return maxRetryAttempts;
    }

    /** The default timeout for waiting for game launch/window to appear, in milliseconds. */
    public static long defaultLaunchWaitTimeout() {
        ensureLoaded();
        return defaultLaunchWaitTimeout;
    }

    /**
     * Returns the project's default capture source configuration.
     * This allows bots to use the same capture source that Studio configured for the project.
     *
     * @return the project's default capture source, or the current source if not configured
     */
    public static CaptureSource defaultCaptureSource() {
        return CaptureSource.fromProjectDefault();
    }

    /**
     * Returns the default timezone for time-related operations.
     *
     * @return the default timezone
     */
    public static ZoneId defaultTimeZone() {
        ensureLoaded();
        return defaultTimeZone;
    }

    /**
     * Sets the default timezone for time-related operations.
     *
     * @param zoneId the timezone ID to use as default
     */
    public static void setDefaultTimeZone(String zoneId) {
        ensureLoaded();
        if (zoneId == null) {
            defaultTimeZone = ZoneId.systemDefault();
        } else {
            defaultTimeZone = ZoneId.of(zoneId);
        }
        // Also set the Time class default
        Time.setDefaultTimeZone(defaultTimeZone);
    }

    /**
     * Whether this bot drives the <b>real</b> mouse and keyboard instead of posting quiet synthetic events to
     * the target window. Read-only mirror of the project setting and of the last {@link #useRealInput} call.
     */
    public static boolean realInput() {
        ensureLoaded();
        return realInput;
    }

    // --- writes ---

    public static void setFoundDelay(int milliseconds) {
        ensureLoaded();
        if (milliseconds < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        foundDelay = milliseconds;
    }

    public static void setNotFoundDelay(int milliseconds) {
        ensureLoaded();
        if (milliseconds < 0) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        notFoundDelay = milliseconds;
    }

    public static void enableRandomClicks(boolean enable) {
        ensureLoaded();
        randomizeClicks = enable;
    }

    public static void setDefaultConfidence(double value) {
        ensureLoaded();
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        confidence = value;
    }

    public static void setCompareMargin(double value) {
        ensureLoaded();
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Compare margin must be between 0.0 and 1.0");
        }
        compareMargin = value;
    }

    public static void setMaxRetryAttempts(int attempts) {
        ensureLoaded();
        if (attempts < 1) {
            throw new IllegalArgumentException("Max attempts must be at least 1");
        }
        maxRetryAttempts = attempts;
    }

    /**
     * Sets the default timeout for waiting for game launch/window to appear.
     *
     * @param timeoutMillis timeout in milliseconds, must be positive
     */
    public static void setDefaultLaunchWaitTimeout(long timeoutMillis) {
        ensureLoaded();
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Launch wait timeout must be positive");
        }
        defaultLaunchWaitTimeout = timeoutMillis;
    }

    /**
     * Toggles the SDK's global debug output. Kept here for discoverability alongside the other tuning knobs,
     * but it is a thin delegate to the single global switch {@link Debug} — vision, lifecycle and launch traces
     * all share one flag.
     */
    public static void enableDebugMode(boolean enable) {
        Debug.set(enable);
    }

    /**
     * Switch to real device input — turn this on when the target is a <b>game</b>.
     *
     * <p>By default BotMaker delivers synthetic events straight to the target window, which clicks a background
     * window without ever moving the cursor. Games (and anything else reading raw input) ignore those events by
     * design: on X11 they carry a {@code send_event} flag the client rejects, and on Windows they land in a
     * message queue a raw-input game never reads. The click is dropped silently — neither OS reports
     * delivery — which is why nothing can auto-detect this and why it is a setting.
     *
     * <p>Turning it on trades background operation for the click landing: the pointer moves to each target and
     * returns to where it was, and the target window is raised, because real input goes to whatever is topmost.
     *
     * <p><b>One-way, and it must happen before the first click.</b> On Linux this swaps the process-wide input
     * backend, which cannot be swapped back, so {@code useRealInput(false)} only prevents a future escalation
     * rather than undoing one. That ordering is why the project's own {@code input.real} is applied inside
     * {@link #ensureLoaded()} rather than left to a generated call: every click path reads a setting from this
     * class first, so the load — and the swap — is always ahead of the click that needs it.
     */
    public static void useRealInput(boolean enable) {
        ensureLoaded();
        applyRealInput(enable);
    }

    /**
     * Resets the click/vision tuning to the SDK's own defaults, discarding both the project's values and any
     * runtime override. Does not touch the global {@link Debug} switch — that has its own lifecycle (project
     * default plus runtime toggle) — and does not un-swap real input, which is one-way.
     */
    public static void resetToDefaults() {
        synchronized (BotSettings.class) {
            // Marked loaded first: the point of a reset is to end up on the SDK defaults, so a later read must
            // not re-seed the project's values over the top of them.
            loaded = true;
            foundDelay = DEFAULT_FOUND_DELAY;
            notFoundDelay = DEFAULT_NOT_FOUND_DELAY;
            randomizeClicks = DEFAULT_RANDOMIZE_CLICKS;
            confidence = DEFAULT_CONFIDENCE;
            compareMargin = DEFAULT_COMPARE_MARGIN;
            maxRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;
            // realInput is deliberately not reset: the backend swap it caused is one-way, so clearing the flag
            // would only make it lie about how input is actually being delivered.
        }
    }

    /**
     * Test seam: forget that the project defaults were ever read, so the next accessor loads them again.
     * {@link #resetToDefaults()} deliberately does the opposite (it pins the SDK defaults), which is why a test
     * that wants to observe the <em>load</em> cannot use it.
     */
    static void resetForTesting() {
        synchronized (BotSettings.class) {
            foundDelay = DEFAULT_FOUND_DELAY;
            notFoundDelay = DEFAULT_NOT_FOUND_DELAY;
            randomizeClicks = DEFAULT_RANDOMIZE_CLICKS;
            confidence = DEFAULT_CONFIDENCE;
            compareMargin = DEFAULT_COMPARE_MARGIN;
            maxRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;
            realInput = false;
            loaded = false;
        }
    }

    // --- project defaults ---

    /**
     * Folds the project's configured values in, once. Every accessor calls this, so the project's tuning is in
     * force by the time anything can observe a setting — including the real-input swap, which has to precede
     * the first click.
     */
    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (BotSettings.class) {
            if (loaded) {
                return;
            }
            Integer configuredFoundDelay = ProjectProperties.clicksFoundDelay();
            if (configuredFoundDelay != null) {
                foundDelay = configuredFoundDelay;
            }
            Integer configuredNotFoundDelay = ProjectProperties.clicksNotFoundDelay();
            if (configuredNotFoundDelay != null) {
                notFoundDelay = configuredNotFoundDelay;
            }
            Boolean configuredRandomize = ProjectProperties.clicksRandomize();
            if (configuredRandomize != null) {
                randomizeClicks = configuredRandomize;
            }
            Double configuredConfidence = ProjectProperties.visionConfidence();
            if (configuredConfidence != null) {
                confidence = configuredConfidence;
            }
            Double configuredMargin = ProjectProperties.visionCompareMargin();
            if (configuredMargin != null) {
                compareMargin = configuredMargin;
            }
            Integer configuredRetries = ProjectProperties.botMaxRetryAttempts();
            if (configuredRetries != null) {
                maxRetryAttempts = configuredRetries;
            }
            // Launch wait timeout: uses SDK default (60000 ms). Can be extended to read from project properties later.
            // Set before the real-input swap below, not after: that swap reaches into the native controller,
            // and anything it touches which reads a setting back must find the load already done rather than
            // re-entering this block.
            loaded = true;
            applyLinuxBackend();
            if (Boolean.TRUE.equals(ProjectProperties.inputReal())) {
                applyRealInput(true);
            }
        }
    }

    /**
     * Pins the Linux input backend the project chose, via the {@code botmaker.linux.input} property
     * {@code LinuxController} reads. An explicit {@code -D} on the command line wins — someone debugging one
     * run should not have to edit the project to do it.
     */
    private static void applyLinuxBackend() {
        String backend = ProjectProperties.inputLinuxBackend();
        if (backend != null && System.getProperty("botmaker.linux.input") == null) {
            System.setProperty("botmaker.linux.input", backend);
        }
    }

    private static void applyRealInput(boolean enable) {
        realInput = enable;
        if (enable) {
            boolean ok = NativeControllerFactory.get().useReliableInput();
            Debug.log("[Input] real device input " + (ok ? "active" : "UNAVAILABLE — clicks may not register"));
        }
    }
}
