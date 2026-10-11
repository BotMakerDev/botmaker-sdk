package com.botmaker.sdk.api.bot;
import com.botmaker.plugin.api.managed.ManagedValues;
import com.botmaker.plugin.api.palette.EntryPoint;
import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.plugin.api.palette.PaletteLabel;
import com.botmaker.plugin.api.palette.Untraced;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.bot.Watchdog;
import com.botmaker.sdk.internal.flow.FlowWalker;
import com.botmaker.sdk.internal.flow.InstalledRun;
import com.botmaker.sdk.internal.flow.SkippedActivities;
import com.botmaker.sdk.internal.launch.Target;
import com.botmaker.sdk.internal.observe.IpcObserver;
import com.botmaker.sdk.internal.vision.BotScreen;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
 * <p>{@link #runFlow} also runs a start-up sequence <em>once</em> before the first loop pass — launch the
 * project's configured target, then the run's {@code setup} when the bot's {@code doSetup} parameter is ticked,
 * then its {@code home} — so a fresh launch actually opens the game, sets it as the bot needs and reaches a known
 * screen instead of assuming it is already running. A first launch leaves an already-open game alone; a recovery
 * shuts a frozen one down first. A recovery runs the setup only when it has not yet returned.
 *
 * <p>The bot ends when {@link #stop()} is called — from an activity that is done, or automatically by the
 * generated loop once every activity is disabled. {@code stop()} unwinds the supervise loop cleanly and
 * {@code supervise} returns, rather than treating it as a crash to recover from.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): {@link #stop()} is offered — it is exactly the
 * statement an activity that has finished its work writes, from anywhere in the call stack. {@link #runFlow}
 * is offered inside {@code main} only: it <em>is</em> the bot, and it does not return. It replaced
 * {@code run(goHome, values…)} on 2026-10-10, when what gets home moved onto the flow.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "🤖")
public class Bot {

    /**
     * For {@code extends Bot} alone — see {@link #runFlow}. There is nothing to construct here and nothing to
     * override; the class is extendable so a bot's entry point can call {@code runFlow(…)} unqualified, and so
     * that <em>this class is a bot's entry point</em> is a fact javac knows rather than a convention.
     */
    protected Bot() {}

    /**
     * Installs this bot's managed values (the SDK's {@code @SdkValue} ones and any other plugin's) and runs
     * {@code flow} — the whole of a bot's {@code main}.
     *
     * <pre>{@code
     * public final class Gamebot extends Bot {
     *
     *     public static void main(String[] args) {
     *         runFlow(Sdk.flow, Sdk.doSetup, Sdk.skip, Sdk.class);
     *     }
     * }
     * }</pre>
     *
     * <p>What the person running the bot chooses is the bot's own {@code @Param}s, handed in here: which of the
     * {@code Flows} runs, whether the game is set up first, and which activities the flow lets them switch off
     * are skipped. What gets the game back to a known screen is {@code Sdk.run()}'s
     * {@link com.botmaker.sdk.api.flow.Run#home}, run at start-up, after anything unexpected and before each step
     * that goes home; a run with none goes nowhere.
     *
     * <p>Offered in the insert menu inside {@code main} only ({@link EntryPoint}): anywhere else it would start
     * a second supervised run inside the first.
     *
     * @param flow    the flow to walk — a constant of the bot's {@code Flows}
     * @param doSetup whether {@code Sdk.run()}'s {@link com.botmaker.sdk.api.flow.Run#setup} runs first, once the
     *                game has started: tick it on a new computer, or after changing a game setting
     * @param skip    the activities to switch off, each one the flow draws {@code .switchable()}; another is run
     *                anyway, and the debug trace says so
     * @param values  each plugin's values class in this bot — {@code Sdk.class}, the file that plugin shipped and
     *                this project now owns
     */
    @EntryPoint
    @PaletteLabel("Run activity flow")
    @Untraced("holds the whole run")
    public static void runFlow(Flow flow, boolean doSetup, Set<Class<? extends Outcome>> skip,
                               Class<?>... values) {
        observe();
        SdkValues.claim();
        ManagedValues.install(values);
        InstalledRun.walk(SkippedActivities.applied(flow == null ? Flow.NONE : flow, skip));
        Runnable goHome = () -> {
            Runnable home = InstalledRun.installed().home();
            if (home != null) home.run();
        };
        lifecycle(() -> FlowWalker.run(InstalledRun.flow(), InstalledRun.installed().home()), goHome,
                doSetup ? InstalledRun.installed().setup() : null);
    }

    /**
     * Installs this bot's managed values as {@link #runFlow} does, then runs {@code body} once and returns —
     * what Studio's ▶ Try calls with one statement as the body, from a caller it writes outside the project's
     * sources.
     *
     * <p>No launch, no {@code goHome}, no recovery: a try is the statement against the screen as it is now. An
     * exception from {@code body} is the try's failure and propagates; {@link #stop()} ends it quietly.
     *
     * @param body   the statement, and the locals it reads
     * @param values each plugin's values class, as {@link #runFlow} takes them
     */
    @Hidden("the entry Studio's Try calls; a bot runs through runFlow()")
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
     * Ends the bot, telling whoever runs it why: {@code whatToChange} is shown as the run's error. What a setup
     * calls when the game is not as the bot needs it and the bot cannot change it itself —
     * {@code Bot.stop("Set the game to windowed mode in Settings ▸ Display.")}.
     */
    @PaletteLabel("Stop, saying why")
    public static void stop(String whatToChange) {
        Debug.error(whatToChange);
        stop();
    }

    /**
     * Runs {@code body} forever with the standard "get home, then (re)start the configured launch target"
     * lifecycle around it — a one-time cold start before the first pass, and a {@code goHome} → restart recovery
     * on every crash or stuck state. The launch target is this machine's {@code botmaker.launch.target}
     * property; a project with none configured launches nothing.
     *
     * <p>{@code setup} makes the game ready once, at the first start of the game: at the cold start before the
     * {@code goHome} that follows it. A setup that throws is still owed, so it runs after the restart that recovers.
     * A restart after the setup returned does not run it again: the game keeps its settings. Every restart is
     * followed by a {@code goHome} of its own, since the recovery's ran before the game came back.
     *
     * @param body   the bot's main work (e.g. one pass of the macro loop; it is re-run continuously)
     * @param goHome navigate from wherever the bot is back to a safe/home screen
     * @param setup  the setup this run owes, or null for none
     */
    static void lifecycle(Runnable body, Runnable goHome, Runnable setup) {
        AtomicReference<Runnable> owed = new AtomicReference<>(setup);
        supervise(body, goHome, mode -> {
            launchConfiguredTarget(mode);
            Runnable due = owed.get();
            if (due != null) {
                Debug.log("setup");
                due.run();
                owed.set(null);
            }
            // The recovery's goHome ran before the game came back, so a restarted game is taken home again.
            if (mode == StartMode.RESTART) {
                Debug.log("goHome");
                goHome.run();
            }
        });
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
        // The game is up: a window of another size than the pictures were cut at is resized, else the trace says.
        BotScreen.fit(Source.current());
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
     * <p>Package-private: bots call {@link #runFlow} —{@code supervise} is the internal loop.
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
                if (!recovered(recovery)) return;
            } catch (RuntimeException e) {
                Debug.error("Crashed: " + e + " — recovering.");
                Watchdog.reset();
                if (!recovered(recovery)) return;
            }
        }
    }

    /**
     * Runs {@code recovery}; false when it stopped the bot, as a setup's {@code Bot.stop("…")} does after a restart. A
     * recovery that crashes is said and the loop goes on: the next pass recovers again.
     */
    private static boolean recovered(Runnable recovery) {
        try {
            recovery.run();
            return true;
        } catch (BotStoppedException e) {
            Debug.log("Stopped by request during recovery.");
            return false;
        } catch (RuntimeException e) {
            Debug.error("Recovery failed: " + e);
            Watchdog.reset();
            return true;
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
            if (!recovered(recovery)) return;
        } catch (RuntimeException e) {
            Debug.error("Crashed during start-up: " + e + " — recovering.");
            Watchdog.reset();
            if (!recovered(recovery)) return;
        }
        supervise(body, recovery);
    }
}
