package com.botmaker.sdk.api.launch;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.capture.CaptureSource;

import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.sdk.internal.session.SessionBootstrap;

/**
 * The SDK's global, ambient <em>launch target</em> — the "what" the bot automates, the launch-side counterpart
 * to {@link com.botmaker.sdk.api.capture.Source} (the "where" it looks). A game bot's start-up step
 * is just {@link #startIfNotRunning()} / {@link #restart()} — {@link com.botmaker.sdk.api.bot.Bot#start(Runnable,
 * Runnable)} calls them for you — so the supervisor (re)launches whatever the project is configured to run
 * without the user hand-editing any launch code.
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
        // Isolated bots launch into a private nested :N display (and route input/vision through it); a plain
        // bot takes the normal :0 launch below. See SessionBootstrap for the gate.
        if (SessionBootstrap.launchIsolated(t.launchSpec())) {
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
        // Isolated: bring up (once) the private :N session and launch into it — its "already running" is the
        // session already existing, so this is idempotent. Non-isolated bots keep the :0 cold-start probe.
        if (SessionBootstrap.launchIsolated(t.launchSpec())) {
            return;
        }
        t.startIfNotRunning();
    }

    /**
     * Whether the current target is up right now (see {@link LaunchTarget#isRunning()} for the layers it asks).
     * {@code false} when no target is configured — there is nothing that could be running.
     */
    public static boolean isRunning() {
        LaunchTarget t = current();
        return t != null && t.isRunning();
    }

    /** Restarts the current target from a clean state (see {@link LaunchTarget#restart()}). No-op when none. */
    public static void restart() {
        LaunchTarget t = current();
        if (t != null) {
            t.restart();
        }
    }

    /**
     * Launches the current target and waits for its window to appear.
     * Uses the default launch wait timeout from BotSettings.
     *
     * @return true if the target's window appeared within the timeout, false if it timed out
     */
    public static boolean launchAndWait() {
        LaunchTarget t = current();
        if (t == null) {
            Debug.log("[Target] launchAndWait: no launch target configured — nothing to launch");
            return false;
        }

        if (SessionBootstrap.launchIsolated(t.launchSpec())) {
            // For isolated sessions, we need to wait for the session window
            return Game.waitForDefaultSource(BotSettings.defaultLaunchWaitTimeout());
        }

        t.startIfNotRunning();
        return Game.waitForLaunch(CaptureSource.fromProjectDefault(), BotSettings.defaultLaunchWaitTimeout());
    }

    /**
     * Waits for the current target's window to appear.
     *
     * @param timeoutMillis the maximum time to wait, in milliseconds
     * @return true if the target's window appeared within the timeout, false if it timed out
     */
    public static boolean waitForLaunch(long timeoutMillis) {
        LaunchTarget t = current();
        if (t == null) {
            Debug.log("[Target] waitForLaunch: no launch target configured — nothing to wait for");
            return false;
        }

        if (SessionBootstrap.launchIsolated(t.launchSpec())) {
            return Game.waitForDefaultSource(timeoutMillis);
        }

        return Game.waitForLaunch(CaptureSource.fromProjectDefault(), timeoutMillis);
    }
}
