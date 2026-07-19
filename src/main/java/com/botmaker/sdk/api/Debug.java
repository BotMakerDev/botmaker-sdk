package com.botmaker.sdk.api;

import com.botmaker.sdk.internal.config.ProjectDefaults;

/**
 * The single, global debug-output switch for a running bot. One flag governs <em>all</em> of the SDK's
 * diagnostic printing:
 * <ul>
 *   <li>the lifecycle/launch traces — {@code [Bot]}, {@code [Game]}, {@code [Target]}, {@code [Activity]}
 *       — that used to print unconditionally, and</li>
 *   <li>the vision traces (find/click/wait/pixel/text) that used to be gated by the separate
 *       {@code ClickConfig.DEBUG_MODE}; {@code ClickConfig.enableDebugMode(...)} now delegates here.</li>
 * </ul>
 *
 * <p><b>Default: on.</b> A bot prints its trace out of the box so a first run is legible; turn it off for a
 * quiet production run with {@link #disable()} (or the Studio "Debug output" toggle). The initial state is
 * seeded from the project's {@code debug} key in {@code botmaker-project.properties} (see
 * {@link ProjectDefaults#debug()}) — absent/unparseable leaves it on — and can be overridden at runtime.
 *
 * <p>Emit your own trace through {@link #log(String)} / {@link #error(String)}: they print only when debugging
 * is enabled, so bot code never has to wrap prints in an {@code if}.
 */
public final class Debug {

    private static volatile boolean enabled = initialState();

    private Debug() {}

    /** Default on; a project {@code debug=false} baked in by Studio starts a bot quiet. */
    private static boolean initialState() {
        Boolean configured = ProjectDefaults.debug();
        return configured == null || configured;
    }

    /** Whether debug output is currently on. All SDK diagnostic prints consult this. */
    public static boolean isEnabled() {
        return enabled;
    }

    /** Turns debug output on for the rest of the run. */
    public static void enable() {
        enabled = true;
    }

    /** Turns debug output off for the rest of the run (a quiet production run). */
    public static void disable() {
        enabled = false;
    }

    /** Sets debug output on or off. */
    public static void set(boolean on) {
        enabled = on;
    }

    /** Prints {@code message} to stdout when debugging is on; a no-op when off. */
    public static void log(String message) {
        if (enabled) {
            System.out.println(message);
        }
    }

    /** Prints {@code message} to stderr when debugging is on; a no-op when off. */
    public static void error(String message) {
        if (enabled) {
            System.err.println(message);
        }
    }
}
