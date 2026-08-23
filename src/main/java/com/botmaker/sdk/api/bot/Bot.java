package com.botmaker.sdk.api.bot;
import com.botmaker.sdk.api.BotSettings;
import com.botmaker.sdk.api.Debug;
import com.botmaker.sdk.api.Scaffolding;
import com.botmaker.sdk.api.launch.Target;

import java.util.function.Consumer;

/**
 * Bot lifecycle supervisor: the outermost loop that keeps a bot running through crashes and stuck states.
 *
 * <p>{@link #supervise} runs your bot body forever, and whenever it throws — a
 * {@link BotStuckException} from the {@link Watchdog}, or any other {@link RuntimeException} — catches it,
 * resets the watchdog and runs your recovery (typically {@code goHome()} then {@code startGame()}) to get
 * back to a known-good state before restarting. This is the "restart the bot on failure" machinery a game
 * bot needs; the body, the recovery hooks and the per-activity logic stay in editable user code.
 *
 * <p>Both {@link #start} forms also run a start-up sequence <em>once</em> before the first loop pass —
 * {@code startGame(COLD)} then {@code goHome()} — so a fresh launch actually opens the game and reaches a known
 * screen instead of assuming it is already running. The start-up step is handed a {@link StartMode} so it can
 * tell a first {@code COLD} launch (don't relaunch an already-open game) from a {@code RESTART} recovery (shut
 * a frozen game down first). {@link #start(Runnable, Runnable)} supplies that step itself, from the project's
 * configured {@link com.botmaker.sdk.api.launch.Target}; pass your own to
 * {@link #start(Runnable, Runnable, Consumer)} when the game needs more than launching.
 *
 * <p>The bot ends when {@link #stop()} is called — from an activity that is done, or automatically by the
 * generated loop once every activity is disabled. {@code stop()} unwinds the supervise loop cleanly and
 * {@code supervise} returns, rather than treating it as a crash to recover from.
 */
public final class Bot {

    private Bot() {}

    /**
     * Signals a clean end of the bot. Thrown by {@link #stop()} and caught by {@link #supervise} to break the
     * loop. Private so the only public way to end the bot is {@code Bot.stop()} — users never see or throw it.
     */
    private static final class BotStoppedException extends RuntimeException {}

    /**
     * Ends the bot: unwinds the supervise loop cleanly from wherever it is called — e.g. an activity that has
     * finished its work, or a helper deep in the call stack. {@link #supervise} catches this and returns
     * instead of recovering. This is the deliberate "we're done" exit, as opposed to a crash.
     */
    @Scaffolding   // the generated FlowDriver ends every run with it
    public static void stop() {
        throw new BotStoppedException();
    }

    /**
     * Starts the bot: the single public entry point, and the one a generated game bot uses. Runs {@code body}
     * forever, with the standard "get home, then (re)start the configured launch target" lifecycle around it —
     * a one-time cold start before the first pass, and a {@code goHome} → restart recovery on every crash or
     * stuck state.
     *
     * <p>The start-up step is the SDK's own: {@link Target#startIfNotRunning()} on the cold start,
     * {@link Target#restart()} on a recovery, driven by the {@link StartMode} the supervisor supplies. That is
     * the whole of what generated projects used to carry as a read-only {@code Startup.java} — the launch
     * target itself was never in that file, it is read from {@code botmaker-project.properties} at runtime, so
     * the file said nothing a bot's own project didn't already say. A project with no target configured simply
     * launches nothing.
     *
     * <p>Use {@link #start(Runnable, Runnable, Consumer)} to supply a start-up step of your own instead.
     *
     * @param body   the bot's main work (e.g. one pass of the macro loop; it is re-run continuously)
     * @param goHome navigate from wherever the bot is back to a safe/home screen
     */
    @Scaffolding   // the game-bot scaffold's entry point: Bot.start(FlowDriver::run, GoHome.INSTANCE::execute)
    public static void start(Runnable body, Runnable goHome) {
        supervise(body, goHome, Bot::launchConfiguredTarget);
    }

    /**
     * The default start-up step: bring the project's configured launch target up, choosing skip-if-already-
     * running on a first {@code COLD} launch over force-stop-then-relaunch on a {@code RESTART} recovery.
     * Always waits for the game window to appear after launching (as requested by user).
     *
     * <p>Private because a bot that wants something else passes its own {@code Consumer<StartMode>} to the
     * 3-arg {@link #start}; the two {@link Target} calls it would delegate to are public.
     */
    private static void launchConfiguredTarget(StartMode mode) {
        switch (mode) {
            case COLD -> {
                Target.startIfNotRunning();
                // Always wait for the game window to appear before starting activities
                Target.waitForLaunch(BotSettings.defaultLaunchWaitTimeout());
            }
            case RESTART -> {
                Target.restart();
                // Always wait for the game window to appear after restart
                Target.waitForLaunch(BotSettings.defaultLaunchWaitTimeout());
            }
        }
    }

    /**
     * Starts the bot with the "get home, then (re)start the game" recovery and a one-time cold start
     * before the loop — the shape a generated game bot uses. The single public entry point; delegates to
     * the internal supervise loop.
     *
     * @param body      the bot's main work
     * @param goHome    navigate from wherever the bot is back to a safe/home screen
     * @param startGame (re)launch the game; receives {@link StartMode#COLD} on the one-time cold start and
     *                  {@link StartMode#RESTART} on every recovery restart
     */
    public static void start(Runnable body, Runnable goHome, Consumer<StartMode> startGame) {
        supervise(body, goHome, startGame);
    }

    /**
     * Run {@code body} forever, recovering with {@code recovery} whenever it throws. Enables the
     * {@link Watchdog} so stuck states surface as {@link BotStuckException}. Does not return under normal
     * operation.
     *
     * <p>Package-private: bots call {@link #start} — {@code supervise} is the internal loop, not part of
     * the public palette (Studio only surfaces {@code public} facade methods as blocks).
     *
     * @param body     the bot's main work (e.g. one pass of the macro loop; it is re-run continuously)
     * @param recovery run after a crash/stuck to restore a known-good state before the next attempt
     */
    static void supervise(Runnable body, Runnable recovery) {
        Watchdog.enable();
        while (true) {
            try {
                body.run();
            } catch (BotStoppedException e) {
                Debug.log("[Bot] Stopped by request.");
                return;
            } catch (BotStuckException e) {
                Debug.error("[Bot] Stuck: " + e.getMessage() + " — recovering.");
                Watchdog.reset();
                recovery.run();
            } catch (RuntimeException e) {
                Debug.error("[Bot] Crashed: " + e + " — recovering.");
                Watchdog.reset();
                recovery.run();
            }
        }
    }

    /**
     * Convenience supervisor whose recovery is "get back home, then (re)start the game", and which also runs a
     * one-time start-up before the loop.
     *
     * <p><b>Cold start (once, before the first pass):</b> {@code startGame(COLD)} then {@code goHome} — launch
     * the game (only if it isn't already open), then navigate to a known-good screen. Without this the loop
     * began against whatever was on screen, so "launch the game in Startup" never fired on a normal run (Startup
     * only ran during recovery).
     *
     * <p><b>Recovery (on every crash/stuck):</b> {@code goHome} then {@code startGame(RESTART)} — get back
     * home, then restart the game (shutting a frozen one down first). A failure <em>during</em> cold start
     * routes through this same recovery rather than aborting the bot.
     *
     * @param body      the bot's main work
     * @param goHome    navigate from wherever the bot is back to a safe/home screen
     * @param startGame (re)launch the game; gets {@link StartMode#COLD} at cold start, {@link StartMode#RESTART}
     *                  on recovery
     */
    static void supervise(Runnable body, Runnable goHome, Consumer<StartMode> startGame) {
        Runnable recovery = () -> {
            // Both halves are announced because a recovery is where a bot spends its most confusing time:
            // without these, "goHome" navigating a game that is already gone and "restart" waiting on a
            // launch are one indistinguishable silence.
            Debug.log("[Bot] goHome");
            goHome.run();
            Debug.log("[Bot] restarting the game");
            startGame.accept(StartMode.RESTART);
        };
        Watchdog.enable();
        // Cold start: open the game and reach a known screen once, before the loop. A failure here recovers
        // exactly as a mid-run failure would, so a bad first launch still self-heals instead of exiting.
        try {
            Debug.log("[Bot] cold start");
            startGame.accept(StartMode.COLD);
            Debug.log("[Bot] goHome");
            goHome.run();
        } catch (BotStoppedException e) {
            Debug.log("[Bot] Stopped by request during start-up.");
            return;
        } catch (BotStuckException e) {
            Debug.error("[Bot] Stuck during start-up: " + e.getMessage() + " — recovering.");
            Watchdog.reset();
            recovery.run();
        } catch (RuntimeException e) {
            Debug.error("[Bot] Crashed during start-up: " + e + " — recovering.");
            Watchdog.reset();
            recovery.run();
        }
        supervise(body, recovery);
    }
}
