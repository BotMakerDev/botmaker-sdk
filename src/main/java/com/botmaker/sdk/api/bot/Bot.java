package com.botmaker.sdk.api.bot;
import com.botmaker.plugin.api.managed.ManagedValues;
import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.plugin.api.palette.Untraced;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.bot.Watchdog;
import com.botmaker.sdk.internal.flow.FlowWalker;
import com.botmaker.sdk.internal.flow.Flows;
import com.botmaker.sdk.internal.launch.Target;
import com.botmaker.sdk.internal.observe.IpcObserver;

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
 * <p>{@link #run} also runs a start-up sequence <em>once</em> before the first loop pass — launch the
 * project's configured target, then {@code goHome()} — so a fresh launch actually opens the game and reaches a
 * known screen instead of assuming it is already running. A first launch leaves an already-open game alone; a
 * recovery shuts a frozen one down first.
 *
 * <p>The bot ends when {@link #stop()} is called — from an activity that is done, or automatically by the
 * generated loop once every activity is disabled. {@code stop()} unwinds the supervise loop cleanly and
 * {@code supervise} returns, rather than treating it as a crash to recover from.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): {@link #stop()} is offered — it is exactly the
 * statement an activity that has finished its work writes, from anywhere in the call stack. {@link #run} is not:
 * it <em>is</em> the bot, written once in its {@code main}, and it does not return. The two {@code start}
 * overloads and their {@code StartMode} went on 2026-10-01: {@code run} replaced them, and a bot never called
 * either.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "🤖")
public class Bot {

    /**
     * For {@code extends Bot} alone — see {@link #run}. There is nothing to construct here and nothing to
     * override; the class is extendable so a bot's entry point can call {@code run(…)} unqualified, and so
     * that <em>this class is a bot's entry point</em> is a fact javac knows rather than a convention.
     */
    protected Bot() {}

    /**
     * Installs this bot's managed values (the SDK's {@code @SdkValue} ones and any other plugin's) and runs its
     * flow — the whole of a bot's {@code main}.
     *
     * <pre>{@code
     * public final class Gamebot extends Bot {
     *
     *     public static void main(String[] args) {
     *         run(Gamebot::goHome, Sdk.class);
     *     }
     * }
     * }</pre>
     *
     * <p>The bot names each plugin's values class, which is a fact only it has and one javac checks; what it
     * does not write is what to do with them.
     *
     * @param goHome what gets the game back to a known screen, between activities and after anything
     *               unexpected
     * @param values each plugin's values class in this bot — {@code Sdk.class}, the file that plugin shipped
     *               and this project now owns. None is legal: the bot runs with whatever the SDK defaults to.
     */
    @Hidden("the entry point a bot's own main calls; a second run() inside an activity body would nest one "
            + "supervised run inside another")
    @Untraced("holds the whole run")
    public static void run(Runnable goHome, Class<?>... values) {
        observe();
        SdkValues.claim();
        ManagedValues.install(values);
        lifecycle(() -> FlowWalker.run(Flows.installed(), goHome), goHome);
    }

    /**
     * Installs this bot's managed values as {@link #run} does, then runs {@code body} once and returns —
     * what Studio's ▶ Try calls with one statement as the body, from a caller it writes outside the project's
     * sources.
     *
     * <p>No launch, no {@code goHome}, no recovery: a try is the statement against the screen as it is now. An
     * exception from {@code body} is the try's failure and propagates; {@link #stop()} ends it quietly.
     *
     * @param body   the statement, and the locals it reads
     * @param values each plugin's values class, as {@link #run} takes them
     */
    @Hidden("the entry Studio's Try calls; a bot runs through run()")
    @Untraced("holds the whole try")
    public static void trial(Runnable body, Class<?>... values) {
        observe();
        SdkValues.claim();
        ManagedValues.install(values);
        try {
            body.run();
        } catch (BotStoppedException e) {
            Debug.log("Stopped by request.");
        }
    }

    /** Why the supervisor invokes the start-up step: the first launch, or a recovery after a crash. */
    enum StartMode {
        /** First launch, before the loop: bring the game up only if it isn't already running. */
        COLD,
        /** Recovery restart: shut the (possibly frozen) game down first, then bring it back up. */
        RESTART
    }

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
    public static void stop() {
        throw new BotStoppedException();
    }

    /**
     * Runs {@code body} forever with the standard "get home, then (re)start the configured launch target"
     * lifecycle around it — a one-time cold start before the first pass, and a {@code goHome} → restart recovery
     * on every crash or stuck state. The launch target is this machine's {@code botmaker.launch.target}
     * property; a project with none configured launches nothing.
     *
     * @param body   the bot's main work (e.g. one pass of the macro loop; it is re-run continuously)
     * @param goHome navigate from wherever the bot is back to a safe/home screen
     */
    static void lifecycle(Runnable body, Runnable goHome) {
        supervise(body, goHome, Bot::launchConfiguredTarget);
    }

    /**
     * The start-up step: bring the project's configured launch target up, choosing skip-if-already-running on a
     * first {@code COLD} launch over force-stop-then-relaunch on a {@code RESTART} recovery. Always waits for the
     * game window to appear after launching.
     */
    private static void launchConfiguredTarget(StartMode mode) {
        switch (mode) {
            case COLD -> {
                Target.startIfNotRunning();
                // Always wait for the game window to appear before starting activities
                Target.waitForLaunch(BotSettings.DEFAULT_LAUNCH_WAIT_TIMEOUT);
            }
            case RESTART -> {
                Target.restart();
                // Always wait for the game window to appear after restart
                Target.waitForLaunch(BotSettings.DEFAULT_LAUNCH_WAIT_TIMEOUT);
            }
        }
    }

    /**
     * Connects the run to the host's trace before anything runs, and logs a thread that dies of an exception
     * nobody caught (2026-09-30). The trace's sink used to be installed by whichever vision call came first, so
     * a bot that failed before matching anything showed nothing; and a helper thread's death reached stderr
     * only, never the trace. A handler the bot set itself is kept.
     */
    private static void observe() {
        IpcObserver.installIfEnabled();
        if (Thread.getDefaultUncaughtExceptionHandler() == null) {
            Thread.setDefaultUncaughtExceptionHandler((thread, e) ->
                    Debug.error("thread " + thread.getName() + " died: " + e, e));
        }
    }

    /**
     * Run {@code body} forever, recovering with {@code recovery} whenever it throws. Enables the
     * {@link Watchdog} so stuck states surface as {@link BotStuckException}. Does not return under normal
     * operation.
     *
     * <p>Package-private: bots call {@link #run} — {@code supervise} is the internal loop.
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
                Debug.log("Stopped by request.");
                return;
            } catch (BotStuckException e) {
                Debug.error("Stuck: " + e.getMessage() + " — recovering.");
                Watchdog.reset();
                recovery.run();
            } catch (RuntimeException e) {
                Debug.error("Crashed: " + e + " — recovering.");
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
        observe();
        Runnable recovery = () -> {
            // Both halves are announced because a recovery is where a bot spends its most confusing time:
            // without these, "goHome" navigating a game that is already gone and "restart" waiting on a
            // launch are one indistinguishable silence.
            Debug.log("goHome");
            goHome.run();
            Debug.log("restarting the game");
            startGame.accept(StartMode.RESTART);
        };
        Watchdog.enable();
        // Cold start: open the game and reach a known screen once, before the loop. A failure here recovers
        // exactly as a mid-run failure would, so a bad first launch still self-heals instead of exiting.
        try {
            Debug.log("cold start");
            startGame.accept(StartMode.COLD);
            Debug.log("goHome");
            goHome.run();
        } catch (BotStoppedException e) {
            Debug.log("Stopped by request during start-up.");
            return;
        } catch (BotStuckException e) {
            Debug.error("Stuck during start-up: " + e.getMessage() + " — recovering.");
            Watchdog.reset();
            recovery.run();
        } catch (RuntimeException e) {
            Debug.error("Crashed during start-up: " + e + " — recovering.");
            Watchdog.reset();
            recovery.run();
        }
        supervise(body, recovery);
    }
}
