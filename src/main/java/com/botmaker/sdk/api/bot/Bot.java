package com.botmaker.sdk.api.bot;
import com.botmaker.plugin.api.assist.TrialEntry;
import com.botmaker.plugin.api.managed.ManagedValues;
import com.botmaker.plugin.api.palette.EntryPoint;
import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.plugin.api.palette.PaletteLabel;
import com.botmaker.plugin.api.palette.Untraced;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.bot.RunDetails;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.bot.Watchdog;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.flow.FlowTree;
import com.botmaker.sdk.internal.flow.FlowWalker;
import com.botmaker.sdk.internal.flow.InstalledRun;
import com.botmaker.sdk.internal.flow.SkippedActivities;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.internal.launch.LaunchTarget;
import com.botmaker.sdk.internal.launch.Target;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.screens.KeptLooks;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.sdk.internal.state.KeptFacts;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.sdk.internal.trace.Say;
import com.botmaker.sdk.internal.vision.BotScreen;

import java.time.Duration;
import java.util.HashSet;
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
     * Installs this bot's managed values (the SDK's {@code @SdkValue} ones and any other plugin's) and runs the
     * flow its {@code flow} parameter names — the whole of a bot's {@code main}.
     *
     * <pre>{@code
     * public final class MyBot extends Bot {
     *
     *     public static void main(String[] args) {
     *         runFlow(Sdk.class);
     *     }
     * }
     * }</pre>
     *
     * <p>What the person running the bot chooses is the bot's own {@code @Param}s, read off {@code values} by their
     * marks ({@code @SdkValue(SdkValue.Id.FLOW)}, {@code DO_SETUP}, {@code SKIP}): which of the {@code Flows} runs,
     * whether the game is set up first, and which activities the flow lets them switch off are skipped. A bot with
     * no {@code flow} runs nothing; with no {@code doSetup} it skips the setup. An activity in {@code skip} that the
     * flow does not draw {@code .switchable()} runs anyway, and the debug trace says so. What gets the game back to
     * a known screen is {@code Sdk.run()}'s {@link com.botmaker.sdk.api.flow.Run#home}, run at start-up, after
     * anything unexpected and before each step that goes home; a run with none goes nowhere.
     *
     * <p>Offered in the insert menu inside {@code main} only ({@link EntryPoint}): anywhere else it would start
     * a second supervised run inside the first.
     *
     * @param values  each plugin's values class in this bot — {@code Sdk.class}, the file that plugin shipped and
     *                this project now owns
     */
    @EntryPoint
    @PaletteLabel("Run activity flow")
    @Untraced("holds the whole run")
    public static void runFlow(Class<?>... values) {
        observe();
        MissFrames.start();
        SdkValues.claim();
        ManagedValues.install(values);
        InstalledGame.install(values);
        InstalledRun.nameFlows(values);
        ScreenMaps.install(values);
        KeptFacts.install(values);
        if (InstalledGame.marked(SdkValue.Id.FLOW, values) instanceof Flow flow) InstalledRun.walk(flow);
        else {
            InstalledRun.walk(null);
            Debug.log("No @SdkValue(SdkValue.Id.FLOW) field in the classes runFlow was given: no flow runs.");
        }
        // Once the flow is known: the log's facts name it.
        RunDetails.start(values);
        InstalledRun.skip(SkippedActivities.of(InstalledRun.flow(), skipped(values)));
        boolean doSetup = InstalledGame.marked(SdkValue.Id.DO_SETUP, values) instanceof Boolean on && on;
        Runnable goHome = () -> {
            Runnable home = InstalledRun.home();
            if (home != null) home.run();
        };
        PopupGuard.enabled(true);
        lifecycle(() -> FlowWalker.run(InstalledRun.flow(), InstalledRun.home()), goHome,
                startUp(doSetup ? InstalledRun.installed().setup() : null));
    }

    /**
     * What the game's first start owes: the settings the map keeps ({@code Button.keeps}), each pressed until it shows
     * its look, then {@code setup}; a setting that never shows stops the run saying which. Null when nothing is owed.
     */
    private static Runnable startUp(Runnable setup) {
        if (KeptLooks.of(ScreenMaps.installed()).isEmpty()) return setup;
        return () -> {
            Debug.log("settings the map keeps");
            KeptLooks.hold(ScreenMaps.installed(), Navigate::press).ifPresent(Bot::stop);
            if (setup != null) setup.run();
        };
    }

    /**
     * Installs this bot's managed values as {@link #runFlow} does, then runs {@code body} once and returns —
     * what Studio's ▶ Try calls with one statement as the body, from a caller it writes outside the project's
     * sources.
     *
     * <p>No launch, no recovery: a try is the statement against the screen as it is now — in the private display
     * Studio offers when the game runs in one. Home runs only before an activity's body run whole whose card goes
     * home, as the run does. An exception from {@code body} is the try's
     * failure and propagates; {@link #stop()} ends it quietly.
     *
     * @param body   the statement, and the locals it reads
     * @param values each plugin's values class, as {@link #runFlow} takes them
     */
    @Hidden("the entry Studio's Try calls; a bot runs through runFlow()")
    @Untraced("holds the whole try")
    public static void trial(Runnable body, Class<?>... values) {
        observe();
        MissFrames.start();
        SdkValues.claim();
        ManagedValues.install(values);
        InstalledGame.install(values);
        ScreenMaps.install(values);
        KeptFacts.install(values);
        RunDetails.start(values);
        SessionBootstrap.joinOffered();
        // Switched back on each run, so a warm JVM keeps no switch a body of the run before left off.
        PopupGuard.enabled(true);
        try {
            homeFirst(body, values);
            body.run();
        } catch (BotStoppedException e) {
            Debug.log("Stopped by request.");
        }
    }

    /**
     * Runs the bot's home before a try that runs an activity's body whole ({@link TrialEntry#TARGET}) when that
     * activity's card goes home, as the run would. The card is the one of the activity the method returns.
     */
    private static void homeFirst(Runnable body, Class<?>... values) {
        // Read once: a later trial in this JVM that names no method must not find it.
        String target = System.getProperty(TrialEntry.TARGET, "");
        System.clearProperty(TrialEntry.TARGET);
        int hash = target.indexOf('#');
        Runnable home = InstalledRun.home();
        if (hash < 0 || home == null || !(InstalledGame.marked(SdkValue.Id.FLOW, values) instanceof Flow flow)) return;
        Class<?> returned;
        try {
            returned = Class.forName(target.substring(0, hash), false, body.getClass().getClassLoader())
                    .getDeclaredMethod(target.substring(hash + 1)).getReturnType();
        } catch (ReflectiveOperationException | LinkageError e) {
            return;
        }
        if (!Outcome.class.isAssignableFrom(returned)) return;
        Flow.Step card = FlowTree.find(flow, Activity.of(returned.asSubclass(Outcome.class)));
        if (card == null || !card.goHome()) return;
        Debug.log("Going home first, as the " + card.label() + " card does.");
        home.run();
    }

    /** The activity classes the {@code skip} parameter names; empty when the bot has none. */
    private static Set<Class<? extends Outcome>> skipped(Class<?>... values) {
        if (!(InstalledGame.marked(SdkValue.Id.SKIP, values) instanceof Set<?> set)) return Set.of();
        Set<Class<? extends Outcome>> out = new HashSet<>();
        for (Object each : set) {
            if (each instanceof Class<?> type && Outcome.class.isAssignableFrom(type)) {
                out.add(type.asSubclass(Outcome.class));
            } else {
                Debug.log("skip holds " + each + ", which is no activity: it skips nothing.");
            }
        }
        return out;
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
     * lifecycle around it — a one-time cold start before the first pass, and on every crash or stuck state a
     * {@code goHome} recovery that restarts the game only when {@code goHome} fails. The launch target is this machine's {@code botmaker.launch.target}
     * property; a project with none configured launches nothing.
     *
     * <p>{@code setup} makes the game ready once, at the first start of the game: at the cold start before the
     * {@code goHome} that follows it. A setup that throws is still owed, so it runs at the next recovery: after the
     * restart, or after the {@code goHome} that got home without one. A restart after the setup returned does not
     * run it again: the game keeps its settings. Every restart is followed by a {@code goHome} of its own.
     *
     * @param body   the bot's main work (e.g. one pass of the macro loop; it is re-run continuously)
     * @param goHome navigate from wherever the bot is back to a safe/home screen
     * @param setup  the setup this run owes, or null for none
     */
    static void lifecycle(Runnable body, Runnable goHome, Runnable setup) {
        AtomicReference<Runnable> owed = new AtomicReference<>(setup);
        Runnable runOwed = () -> {
            Runnable due = owed.get();
            if (due != null) {
                Debug.log("setup");
                due.run();
                owed.set(null);
            }
        };
        // A recovery that gets home without a restart still owes a setup that threw. A setup that throws again here
        // stays owed and is said: the game got home, so it is no reason to restart it.
        Runnable homeThenOwed = () -> {
            goHome.run();
            try {
                runOwed.run();
            } catch (BotStoppedException e) {
                throw e;
            } catch (RuntimeException e) {
                Debug.error("setup failed again, still owed: " + e);
            }
        };
        supervise(body, homeThenOwed, mode -> {
            launchConfiguredTarget(mode);
            runOwed.run();
            // A restarted game is taken home: the recovery's goHome, if it ran, ran before the game came back.
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
    static void launchConfiguredTarget(StartMode mode) {
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
        // An emulator's other apps are kept off it: no notification covers the game, and it asks no permission.
        if (Source.current() instanceof EmulatorSource emulator
                && Target.current() instanceof LaunchTarget.EmulatorApp app) {
            emulator.quiet(app.packageName());
        }
    }

    /**
     * Logs a thread that dies of an exception nobody caught (2026-09-30): a helper thread's death reached stderr
     * only, never the trace. A handler the bot set itself is kept. The trace itself is the run channel's, handed
     * before {@code main} ({@code SdkRun.started}).
     */
    private static void observe() {
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
        int failed = 0;
        while (true) {
            // Recoveries that fail in a row wait longer each time (2026-11-03): a game that cannot be captured was
            // restarted 456 times in 150 s, each restart a new launch and the run log 1.7 MB.
            if (failed > 0) Wait.time(backOff(failed));
            try {
                body.run();
                failed = 0;
            } catch (BotStoppedException e) {
                Debug.log("Stopped by request.");
                return;
            } catch (BotStuckException e) {
                // The recovery rescues the run: a warning, unless whoever threw said the problem already.
                String recovering = "Stuck: " + e.getMessage() + " — recovering.";
                if (Say.wasTold(e)) Say.info(recovering);
                else Say.warn(recovering);
                Watchdog.reset();
                if (!recovered(recovery)) return;
                failed++;
            } catch (RuntimeException e) {
                Debug.error("Crashed: " + e + " — recovering.");
                Watchdog.reset();
                if (!recovered(recovery)) return;
                failed++;
            }
        }
    }

    /** The pause before the next pass after {@code failed} failures in a row: none after one, then 2 s doubling to 60 s. */
    static Duration backOff(int failed) {
        if (failed <= 1) return Duration.ZERO;
        return Duration.ofSeconds(Math.min(60, 1L << Math.min(failed - 1, 6)));
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
     * Convenience supervisor whose recovery is "get back home, or restart the game when that fails", and which
     * also runs a one-time start-up before the loop.
     *
     * <p><b>Cold start (once, before the first pass):</b> {@code startGame(COLD)} then {@code goHome} — launch
     * the game (only if it isn't already open), then navigate to a known-good screen. Without this the loop
     * began against whatever was on screen, so "launch the game in Startup" never fired on a normal run (Startup
     * only ran during recovery). A cold start that fails restarts the game: there is no home to go to yet.
     *
     * <p><b>Recovery (on every crash/stuck):</b> {@code goHome}; only when it throws (its loops ran out, the game
     * is frozen or gone) {@code startGame(RESTART)}, shutting the game down first. Until 2026-10-14 every
     * recovery restarted the game, even one {@code goHome} had already brought back.
     *
     * @param body      the bot's main work
     * @param goHome    navigate from wherever the bot is back to a safe/home screen
     * @param startGame (re)launch the game; gets {@link StartMode#COLD} at cold start, {@link StartMode#RESTART}
     *                  on recovery
     */
    static void supervise(Runnable body, Runnable goHome, Consumer<StartMode> startGame) {
        observe();
        Runnable restart = () -> {
            // Whichever path restarts it (a cold start that failed, a recovery whose goHome threw), the restart a
            // system dialog owed is paid: left set, the next ordinary recovery would restart instead of going home.
            Watchdog.takeRestartNext();
            Debug.log("restarting the game");
            startGame.accept(StartMode.RESTART);
        };
        Runnable recovery = () -> {
            // Each step is announced because a recovery is where a bot spends its most confusing time: without
            // these, "goHome" navigating a game that is gone and "restart" waiting on a launch are one silence.
            if (Watchdog.takeRestartNext()) {
                // Android said the game crashed or stopped answering: no screen of it to walk home through.
                restart.run();
                return;
            }
            Debug.log("goHome");
            try {
                goHome.run();
                Debug.log("home reached");
                return;
            } catch (BotStoppedException e) {
                throw e;
            } catch (RuntimeException e) {
                Debug.error("goHome failed: " + e);
                Watchdog.reset();
            }
            restart.run();
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
            if (!recovered(restart)) return;
        } catch (RuntimeException e) {
            Debug.error("Crashed during start-up: " + e + " — recovering.");
            Watchdog.reset();
            if (!recovered(restart)) return;
        }
        supervise(body, recovery);
    }
}
