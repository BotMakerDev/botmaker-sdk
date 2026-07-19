package com.botmaker.sdk.api.launch;
import com.botmaker.sdk.api.Debug;

import com.botmaker.sdk.internal.config.ProjectDefaults;

/**
 * The SDK's global, ambient <em>launch target</em> — the "what" the bot automates, the launch-side counterpart
 * to {@link com.botmaker.sdk.api.capture.Source} (the "where" it looks). A game-bot's generated
 * {@code Startup.run()} is just {@code Target.start();}, so the supervisor (re)launches whatever the project is
 * configured to run without the user hand-editing any launch code.
 *
 * <p>On first use the current target initialises from the <strong>project default</strong> — the
 * {@code launch.target} key Studio bakes into {@code botmaker-project.properties} (see {@link ProjectDefaults}).
 * When none is configured the target is {@code null} and {@link #start()} is a no-op: an empty game-bot scaffold
 * that hasn't picked a game yet simply doesn't launch anything. Override at runtime with {@link #set(String)}.
 */
public final class Target {

    private static volatile LaunchTarget current;
    private static volatile boolean initialised;

    private Target() {}

    /**
     * The current launch target, initialised lazily from the project default. May be {@code null} when no target
     * is configured.
     */
    public static LaunchTarget current() {
        if (!initialised) {
            synchronized (Target.class) {
                if (!initialised) {
                    current = LaunchTarget.parse(ProjectDefaults.launchTarget());
                    initialised = true;
                }
            }
        }
        return current;
    }

    /**
     * Overrides the current target. Accepts a {@code launch.target} spec string (see {@link LaunchTarget});
     * {@code null}/blank or an unparseable spec clears it back to "no target".
     */
    public static void set(String spec) {
        current = LaunchTarget.parse(spec);
        initialised = true;
    }

    /** Overrides the current target with an already-parsed one. */
    public static void set(LaunchTarget target) {
        current = target;
        initialised = true;
    }

    /**
     * Launches the current target. No-op when none is configured — a bot that hasn't chosen a game yet won't
     * fail to start; it just has nothing to launch.
     */
    public static void start() {
        LaunchTarget t = current();
        if (t == null) {
            Debug.log("[Target] start: no launch target configured — nothing to launch");
            return;
        }
        t.start();
    }

    /**
     * Brings the current target up only if it isn't already running — the cold-start path. Avoids relaunching a
     * game the user already opened by hand on a first run (see {@link LaunchTarget#startIfNotRunning()}). No-op
     * when no target is configured.
     */
    public static void startIfNotRunning() {
        LaunchTarget t = current();
        if (t == null) {
            Debug.log("[Target] startIfNotRunning: no launch target configured — nothing to launch");
            return;
        }
        t.startIfNotRunning();
    }

    /** Restarts the current target from a clean state (see {@link LaunchTarget#restart()}). No-op when none. */
    public static void restart() {
        LaunchTarget t = current();
        if (t != null) {
            t.restart();
        }
    }
}
