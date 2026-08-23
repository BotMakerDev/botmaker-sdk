package com.botmaker.sdk.api.util;

import com.botmaker.sdk.api.meta.Scaffolding;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.shared.Diag;

/**
 * The single, global debug-output switch for a running bot. One flag governs <em>all</em> of the SDK's
 * diagnostic printing:
 * <ul>
 *   <li>the lifecycle/launch traces — {@code [Bot]}, {@code [Game]}, {@code [Target]}, {@code [Activity]}
 *       — that used to print unconditionally, and</li>
 *   <li>the vision traces (find/click/wait/pixel/text) that used to be gated by the separate
 *       {@code ClickConfig.DEBUG_MODE}; {@code BotSettings.enableDebugMode(...)} now delegates here.</li>
 * </ul>
 *
 * <p><b>Default: on.</b> A bot prints its trace out of the box so a first run is legible; turn it off for a
 * quiet production run with {@link #disable()} (or the Studio "Debug output" toggle). The initial state is
 * seeded from the project's {@code debug} key in {@code botmaker-project.properties} (see
 * {@link ProjectDefaults#debug()}) — absent/unparseable leaves it on — and can be overridden at runtime.
 *
 * <p>Emit your own trace through {@link #log(String)} / {@link #error(String)}: they print only when debugging
 * is enabled, so bot code never has to wrap prints in an {@code if}.
 *
 * <p>The flag itself lives in {@code botmaker-shared}'s {@link Diag}, which this class only delegates to.
 * {@code shared} can't depend on the SDK, yet its window/capture/input code prints diagnostics of its own —
 * keeping the state in the lower module is what makes this <em>one</em> switch rather than two that drift.
 */
public final class Debug {

    private Debug() {}

    static {
        // Seed the shared flag from the project's `debug` key; absent/unparseable leaves it on.
        Boolean configured = ProjectDefaults.debug();
        Diag.set(configured == null || configured);
    }

    /** Whether debug output is currently on. All SDK diagnostic prints consult this. */
    public static boolean isEnabled() {
        return Diag.isEnabled();
    }

    /** Turns debug output on for the rest of the run. */
    public static void enable() {
        Diag.set(true);
    }

    /** Turns debug output off for the rest of the run (a quiet production run). */
    public static void disable() {
        Diag.set(false);
    }

    /** Sets debug output on or off. */
    public static void set(boolean on) {
        Diag.set(on);
    }

    /** Prints {@code message} to stdout when debugging is on; a no-op when off. */
    public static void log(String message) {
        Diag.log(message);
    }

    /** Prints {@code message} to stderr when debugging is on; a no-op when off. */
    @Scaffolding   // the generated FlowDriver's "gave up after N steps" line
    public static void error(String message) {
        Diag.error(message);
    }

    /**
     * Prints {@code message} then {@code t}'s stack trace to stderr, when debugging is on. Use this instead of
     * {@code t.printStackTrace()}, which would print on a quiet run.
     */
    public static void error(String message, Throwable t) {
        Diag.error(message, t);
    }
}
