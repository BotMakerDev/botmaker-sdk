package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.meta.ReplacedBy;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.shared.Diag;
import com.botmaker.shared.capture.NativeControllerFactory;

import java.util.Arrays;
import java.util.Optional;

/**
 * The bot's runtime tuning — how long it pauses around a match, how sure it has to be, and where the game runs:
 * on a private display of its own, or on the user's desktop, where it may take over the mouse and keyboard. One
 * value, written in the bot's own Java:
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.SETTINGS)
 * public static BotSettings settings() {
 *     return BotSettings.defaults()
 *             .foundDelay(400)
 *             .confidence(0.85)
 *             .where(BotSettings.Where.MY_DESKTOP)
 *             .takesOver();
 * }
 * }</pre>
 *
 * <p>Each setting is a named link after {@link #defaults()}, written only when it is not the default
 * (2026-10-09), so the method says what this bot changed and nothing else. A setting that is on by default is
 * turned off by a link with a negative name ({@link #centredClicks()}, {@link #debugOff()}).
 *
 * <p>{@code Bot.run(…, Sdk.class)} hands it to {@link #use} before anything runs, so a click never happens on
 * the defaults and then on the bot's own values. Studio's ⚙ Bot Settings window edits the expression; a bot
 * that wants another value for a while calls {@code BotSettings.use(BotSettings.current().confidence(0.9))}.
 *
 * <h2>What this replaced (2026-09-27)</h2>
 *
 * <p>Until then this class was a set of static setters seeded from eight keys in a
 * {@code botmaker-project.properties} Studio wrote beside the bot's sources — a second file, in a second
 * format, that nothing but those two readers knew about, and that the compiler could not check. The settings
 * are an {@code @SdkValue} value now, like the flow and the capture source: Java the bot compiles, which a
 * developer with no BotMaker installed can read and change. A project without the method runs on
 * {@link #DEFAULTS}. What the bot <em>launches</em> is not here: that is a fact about this machine, and it
 * arrives as the {@code botmaker.launch.target} system property.
 *
 * @param clicks           the pauses around a match and where a click lands
 * <h2>Where the game runs (2026-10-07)</h2>
 *
 * <p>Four settings used to answer one question — a real-input tick, its Linux backend, a private-display tick
 * and its backend — and two of them silently did nothing in combination with the others: real input escalated
 * the user's desktop even when the bot ran on a private display, and the Linux backend never applied there.
 * {@link RunIn} asks the question once ({@link Where}), and the take-over tick only means something on the
 * desktop. The two backends are kept, for a machine that needs one pinned.
 *
 * @param vision           how sure a match has to be
 * @param runIn            where the game runs, and whether the bot takes over the mouse and keyboard there
 * @param maxRetryAttempts how many no-progress checks {@link Watchdog} tolerates before the bot is stuck; at
 *                         least 1
 * @param debug            whether the SDK's debug output starts on, for a run that does not say: a
 *                         {@code -Dbotmaker.debug=true|false} on the run (Studio's Debug output toggle) wins
 */
public record BotSettings(Clicks clicks, Vision vision, RunIn runIn, int maxRetryAttempts, boolean debug) {

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
     * How many consecutive no-progress checks {@link Watchdog} tolerates before it throws
     * {@link BotStuckException} at the next {@code checkpoint()} — how long a frozen screen or a repeated no-op
     * click may run before the bot counts as stuck and is restarted.
     */
    public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 20;

    /** How long to wait for a launched game's window to appear, in milliseconds. */
    public static final long DEFAULT_LAUNCH_WAIT_TIMEOUT = 60000;

    /** The pauses around a match, and whether a click lands on a random point of it rather than its centre. */
    public record Clicks(int foundDelay, int notFoundDelay, boolean randomize) {
        /** A negative pause is none. */
        public Clicks {
            foundDelay = Math.max(0, foundDelay);
            notFoundDelay = Math.max(0, notFoundDelay);
        }
    }

    /** The template-match confidence every call without its own uses, and the compare margin. Both 0..1. */
    public record Vision(double confidence, double compareMargin) {
        public Vision {
            confidence = unit(confidence, DEFAULT_CONFIDENCE);
            compareMargin = unit(compareMargin, DEFAULT_COMPARE_MARGIN);
        }
    }

    /**
     * Where the game runs, and what the bot does to the mouse and keyboard there.
     *
     * @param where          a private display of the bot's own (Linux), a game VM (Windows), or the user's
     *                       desktop, which is where the game runs when the place asked for isn't this
     *                       computer's
     * @param takeOver       on the desktop, drive the real mouse and keyboard rather than send events to the
     *                       game's window — some games ignore those. Nothing on a private display, which the bot
     *                       has to itself
     * @param displayBackend which private display, when {@link #where} is one
     * @param inputBackend   which Linux backend delivers a take-over
     */
    public record RunIn(Where where, boolean takeOver, DisplayBackend displayBackend, InputBackend inputBackend) {
        public RunIn {
            where = where == null ? Where.PRIVATE_DISPLAY : where;
            displayBackend = displayBackend == null ? DisplayBackend.AUTO : displayBackend;
            inputBackend = inputBackend == null ? InputBackend.AUTO : inputBackend;
        }
    }

    /** Where the game runs. */
    public enum Where {
        /** A display of the bot's own: the game never appears on the desktop and the user keeps the machine. */
        PRIVATE_DISPLAY("private-display", "A private display — you keep using your computer"),
        /** The user's desktop: the user watches the bot work, and shares the screen with it. */
        MY_DESKTOP("my-desktop", "My desktop — watch it work"),
        /**
         * A game VM that Studio set up, on Windows: the game runs in the VM's Windows and the bot drives its
         * screen. Which VM is the {@code botmaker.session.vm} run property, or the only one set up.
         */
        VM("vm", "A virtual machine — you keep using your computer");

        private final String id;
        private final String displayName;

        Where(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        /** The {@code botmaker.session.where} run property's value. */
        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        /** The value whose {@link #id()} is {@code id}, ignoring case; empty for anything else, blank included. */
        public static Optional<Where> fromId(String id) {
            return id == null ? Optional.empty() : Arrays.stream(values())
                    .filter(w -> w.id.equalsIgnoreCase(id.trim())).findFirst();
        }
    }

    /**
     * Which Linux backend delivers a take-over. {@link #AUTO} lets the controller choose; the others pin one,
     * for a machine that only works with a particular one. Ignored on Windows and on a private display.
     */
    public enum InputBackend {
        AUTO("auto", "Automatic"),
        XSENDEVENT("xsendevent", "xsendevent — sent to the window, leaves your cursor alone"),
        XTEST("xtest", "XTest — X11's own synthetic input; moves the shared cursor"),
        XDOTOOL("xdotool", "xdotool — XTest via the xdotool command; moves the shared cursor"),
        UINPUT("uinput", "uinput — a kernel virtual device the system reports as real");

        private final String id;
        private final String displayName;

        InputBackend(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        /** The {@code botmaker.linux.input} value the controller reads. */
        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * Which private display hosts the game. {@link #AUTO} is right almost always: gamescope, which puts a real
     * GPU in the display, for everything but an emulator app, which gets Xephyr.
     */
    public enum DisplayBackend {
        AUTO("auto", "Automatic (gamescope; Xephyr for emulator apps)"),
        GAMESCOPE("gamescope", "gamescope — a real GPU in the private display (3D games)"),
        XEPHYR("xephyr", "Xephyr — software-rendered 2D (crashes 3D games)");

        private final String id;
        private final String displayName;

        DisplayBackend(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        /** The session module's backend id; {@code auto} names none and lets the launch kind pick. */
        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** What a bot runs with when it declares no settings. */
    public static final BotSettings DEFAULTS = new BotSettings(
            new Clicks(DEFAULT_FOUND_DELAY, DEFAULT_NOT_FOUND_DELAY, DEFAULT_RANDOMIZE_CLICKS),
            new Vision(DEFAULT_CONFIDENCE, DEFAULT_COMPARE_MARGIN),
            new RunIn(Where.PRIVATE_DISPLAY, false, DisplayBackend.AUTO, InputBackend.AUTO),
            DEFAULT_MAX_RETRY_ATTEMPTS, true);

    /** A missing part is its default, and fewer than one retry is one. */
    public BotSettings {
        clicks = clicks == null ? new Clicks(DEFAULT_FOUND_DELAY, DEFAULT_NOT_FOUND_DELAY, DEFAULT_RANDOMIZE_CLICKS)
                : clicks;
        vision = vision == null ? new Vision(DEFAULT_CONFIDENCE, DEFAULT_COMPARE_MARGIN) : vision;
        runIn = runIn == null ? new RunIn(Where.PRIVATE_DISPLAY, false, DisplayBackend.AUTO, InputBackend.AUTO)
                : runIn;
        maxRetryAttempts = Math.max(1, maxRetryAttempts);
    }

    /**
     * Whether the SDK's debug output starts on, for a run no host started.
     *
     * @deprecated since 2026-09-29 debug output is the host's to decide, for every plugin and not only the SDK:
     * Studio's 🐞 Debug button sets {@code -Dbotmaker.debug} on the run, and a run it started ignores this value
     * whenever the button says on or off. The value stays in the bot's settings (this API only grows) and still
     * applies to a bot run from a terminal; Bot Settings no longer shows it.
     */
    @Deprecated
    @ReplacedBy(note = "Debug output is chosen with Studio's Debug button (the botmaker.debug run property); "
            + "this value only applies to a run no host started.")
    @Override
    public boolean debug() {
        return debug;
    }

    // --- how the bot's Java writes it ---

    /** {@link #DEFAULTS}, as the call each changed setting is a link after. */
    public static BotSettings defaults() {
        return DEFAULTS;
    }

    // --- the settings in force ---

    private static volatile BotSettings current = DEFAULTS;

    /** The settings the bot is running with: its own once {@code Bot.run} has installed them. */
    public static BotSettings current() {
        return current;
    }

    /**
     * Makes {@code settings} the ones in force, from the next click on.
     *
     * <p><b>A take-over is one-way.</b> On Linux it swaps the process-wide input backend, which cannot be swapped
     * back, so a later value with {@code takeOver = false} only stops a future escalation. That is why
     * {@code Bot.run} installs the bot's settings before anything else runs: the swap has to precede the first
     * click, or the click is dropped silently. For the same reason the Linux backend is pinned only when nothing
     * has pinned it yet — an explicit {@code -Dbotmaker.linux.input} on the command line wins.
     *
     * <p><b>Nothing is taken over for a private display.</b> The swap is the user's own desktop's, and a bot on a
     * private display drives that display alone; escalating the desktop anyway was the old real-input tick's
     * bug. Where the bot runs is the resolved answer ({@code -Dbotmaker.session.where} included), not only
     * these settings'.
     */
    public static void use(BotSettings settings) {
        BotSettings next = settings == null ? DEFAULTS : settings;
        synchronized (BotSettings.class) {
            BotSettings was = current;
            current = next;
            if (next.runIn.inputBackend != InputBackend.AUTO && System.getProperty(LINUX_INPUT) == null) {
                System.setProperty(LINUX_INPUT, next.runIn.inputBackend.id());
            }
            boolean escalate = next.runIn.takeOver && onTheDesktop();
            if (escalate && !takenOver) {
                takenOver = true;
                boolean ok = NativeControllerFactory.get().useReliableInput();
                Debug.log("[Input] taking over the mouse and keyboard: "
                        + (ok ? "active" : "UNAVAILABLE — clicks may not register"));
            } else if (takenOver && !next.runIn.takeOver) {
                // Kept on: the backend it swapped in cannot be swapped back, and saying otherwise would lie.
                current = next.takeOver(true);
            }
        }
        // The run's -Dbotmaker.debug (a host's Debug output toggle) wins over the bot's own default.
        Debug.set(Diag.runOverride().orElse(next.debug));
    }

    /** The system property {@code LinuxController} reads its backend from. */
    private static final String LINUX_INPUT = "botmaker.linux.input";

    /** Whether this process has swapped in the take-over backend — once, for good. Guarded by the class lock. */
    private static boolean takenOver;

    /**
     * Whether the game runs on the user's desktop: unless the bot's resolved choice is a private display on
     * Linux or a game VM on Windows. Asked again by {@code Session.set}, which can
     * move the bot to the desktop after these settings were installed.
     */
    private static boolean onTheDesktop() {
        return !SessionBootstrap.isolationRequested();
    }

    /**
     * Test seam: back to {@link #DEFAULTS} without touching the input backend or the debug switch, and forgetting
     * a take-over, which a test made on a stand-in controller.
     */
    static synchronized void resetForTesting() {
        current = DEFAULTS;
        takenOver = false;
    }

    // --- reading one setting ---

    /** Pause after a successful match, in ms. */
    public int foundDelay() {
        return clicks.foundDelay;
    }

    /** Pause after a failed match, in ms. */
    public int notFoundDelay() {
        return clicks.notFoundDelay;
    }

    /** Whether clicks land on a random point inside the match rather than its centre. */
    public boolean randomizeClicks() {
        return clicks.randomize;
    }

    /** The default template-match confidence (0..1) every no-confidence vision call uses. */
    public double confidence() {
        return vision.confidence;
    }

    /** The default margin a good template must beat a distractor by. See {@link #DEFAULT_COMPARE_MARGIN}. */
    public double compareMargin() {
        return vision.compareMargin;
    }

    /** Where the game runs. */
    public Where where() {
        return runIn.where;
    }

    /** Whether, on the desktop, the bot drives the real mouse and keyboard. */
    public boolean takeOver() {
        return runIn.takeOver;
    }

    // --- one setting changed: a copy ---

    public BotSettings foundDelay(int milliseconds) {
        return withClicks(new Clicks(milliseconds, clicks.notFoundDelay, clicks.randomize));
    }

    public BotSettings notFoundDelay(int milliseconds) {
        return withClicks(new Clicks(clicks.foundDelay, milliseconds, clicks.randomize));
    }

    public BotSettings randomizeClicks(boolean randomize) {
        return withClicks(new Clicks(clicks.foundDelay, clicks.notFoundDelay, randomize));
    }

    public BotSettings confidence(double confidence) {
        return new BotSettings(clicks, new Vision(confidence, vision.compareMargin), runIn, maxRetryAttempts, debug);
    }

    public BotSettings compareMargin(double margin) {
        return new BotSettings(clicks, new Vision(vision.confidence, margin), runIn, maxRetryAttempts, debug);
    }

    public BotSettings maxRetryAttempts(int attempts) {
        return new BotSettings(clicks, vision, runIn, attempts, debug);
    }

    public BotSettings takeOver(boolean takeOver) {
        return withRunIn(new RunIn(runIn.where, takeOver, runIn.displayBackend, runIn.inputBackend));
    }

    public BotSettings debug(boolean on) {
        return new BotSettings(clicks, vision, runIn, maxRetryAttempts, on);
    }

    /** Clicks land on the match's centre rather than a random point of it. */
    public BotSettings centredClicks() {
        return randomizeClicks(false);
    }

    /** The game runs {@code where}. */
    public BotSettings where(Where where) {
        return withRunIn(new RunIn(where, runIn.takeOver, runIn.displayBackend, runIn.inputBackend));
    }

    /** On the desktop, the bot drives the real mouse and keyboard. */
    public BotSettings takesOver() {
        return takeOver(true);
    }

    /** Which private display hosts the game, when it runs on one. */
    public BotSettings displayBackend(DisplayBackend backend) {
        return withRunIn(new RunIn(runIn.where, runIn.takeOver, backend, runIn.inputBackend));
    }

    /** Which Linux backend delivers a take-over. */
    public BotSettings inputBackend(InputBackend backend) {
        return withRunIn(new RunIn(runIn.where, runIn.takeOver, runIn.displayBackend, backend));
    }

    /**
     * The SDK's debug output starts off, for a run no host started.
     *
     * @deprecated as {@link #debug()} is: debug output is the host's to decide. Kept so a value read is written
     * back whole.
     */
    @Deprecated
    @ReplacedBy(note = "Debug output is chosen with Studio's Debug button (the botmaker.debug run property); "
            + "this value only applies to a run no host started.")
    public BotSettings debugOff() {
        return debug(false);
    }

    private BotSettings withClicks(Clicks next) {
        return new BotSettings(next, vision, runIn, maxRetryAttempts, debug);
    }

    private BotSettings withRunIn(RunIn next) {
        return new BotSettings(clicks, vision, next, maxRetryAttempts, debug);
    }

    private static double unit(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : fallback;
    }
}
