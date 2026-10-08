package com.botmaker.sdk.internal.launch;
import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.PaletteDefault;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.capture.Source;

import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.sdk.internal.session.SessionBootstrap;

/**
 * The SDK's global, ambient <em>launch target</em> — the "what" the bot automates, the launch-side counterpart
 * to {@link com.botmaker.sdk.api.capture.Source} (the "where" it looks). A game bot's start-up step
 * is just {@link #startIfNotRunning()} / {@link #restart()} — {@link com.botmaker.sdk.api.bot.Bot#start(Runnable,
 * Runnable)} calls them for you — so the supervisor (re)launches whatever the project is configured to run
 * without the user hand-editing any launch code.
 *
 * <p>On first use the current target initialises from <strong>this machine's</strong> — the
 * {@code botmaker.launch.target} system property Studio starts the bot with (see {@link ProjectDefaults}).
 * When none is configured the target is {@code null} and {@link #start()} is a no-op: an empty game-bot scaffold
 * that hasn't picked a game yet simply doesn't launch anything. Override at runtime with
 * {@link #set(LaunchTarget)}.
 *
 * <p><b>Not in the palette</b>: {@code Bot.start} launches the target itself, so a block doing it
 * again is a second way to say what the run already does. The class stays public for a hand-written bot, and
 * its {@code @Hidden} members say what reaches nothing if a later palette class ever returns a {@code Target}.
 */
public final class Target {

    private static volatile LaunchTarget current;
    private static volatile boolean initialised;

    private Target() {}

    /**
     * The current launch target, initialised lazily from the project default. May be {@code null} when no target
     * is configured.
     */
    @Hidden("hands back a LaunchTarget, which a bot cannot declare, store or pass on in Studio")
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
    @Hidden("both overloads take what an editor cannot build: a LaunchTarget, or the launch.target spec grammar")
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
            Debug.log("start: no launch target configured — nothing to launch");
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
            Debug.log("startIfNotRunning: no launch target configured — nothing to launch");
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
        if (t == null) return false;
        // In a game VM the game's processes are the guest's: this computer's process table never has them.
        if (SessionBootstrap.inVm()) return SessionBootstrap.runningInVm(t.launchSpec());
        return t.isRunning();
    }

    /**
     * Restarts the current target from a clean state (see {@link LaunchTarget#restart()}). No-op when none. In a
     * game VM it ends the game's processes in the guest, then starts it there again.
     */
    public static void restart() {
        LaunchTarget t = current();
        if (t == null) return;
        if (SessionBootstrap.inVm()) {
            SessionBootstrap.relaunchInVm(t.launchSpec());
            return;
        }
        t.restart();
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
            Debug.log("launchAndWait: no launch target configured — nothing to launch");
            return false;
        }

        if (SessionBootstrap.launchIsolated(t.launchSpec())) {
            // For isolated sessions, we need to wait for the session window
            return Game.waitForDefaultSource(BotSettings.DEFAULT_LAUNCH_WAIT_TIMEOUT);
        }

        t.startIfNotRunning();
        return Game.waitForLaunch(Source.current(), BotSettings.DEFAULT_LAUNCH_WAIT_TIMEOUT);
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
            Debug.log("waitForLaunch: no launch target configured — nothing to wait for");
            return false;
        }

        if (SessionBootstrap.launchIsolated(t.launchSpec())) {
            return Game.waitForDefaultSource(timeoutMillis);
        }

        return Game.waitForLaunch(Source.current(), timeoutMillis);
    }

    /**
     * Waits up to {@code timeout} for the current target's window to appear. The palette's lead: a dropped
     * block starts at the editor's {@code Duration}, where a {@code long} would start at 0 ms and give up at once.
     */
    @PaletteDefault
    public static boolean waitForLaunch(java.time.Duration timeout) {
        return waitForLaunch(timeout.toMillis());
    }
}
