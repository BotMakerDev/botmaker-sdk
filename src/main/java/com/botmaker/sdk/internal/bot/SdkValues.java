package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.ValueHolder;
import com.botmaker.plugin.api.managed.ManagedValues;
import com.botmaker.plugin.api.params.Param;
import com.botmaker.plugin.api.source.ManagedClasses;
import com.botmaker.plugin.api.source.ManagedMethod;
import com.botmaker.plugin.api.source.ManagedOpenSet;
import com.botmaker.plugin.api.source.ManagedParam;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.GameVm;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.Run;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.ScreenPart;
import com.botmaker.sdk.api.screens.Shades;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.PictureTag;
import com.botmaker.sdk.internal.screens.ShadeFit;
import com.botmaker.sdk.internal.vision.BotScreen;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * The SDK's managed values, each declared once, and what a run does with each one.
 *
 * <p>One declaration per {@link SdkValue.Id} constant, which a bot writes as {@code @SdkValue(…)}: the
 * plugin names this class ({@code .values(SdkValues.class)}), so each value {@link #values()} lists is one of
 * its {@code managedValues()}, in list order; its windows open each one through a toolkit
 * {@code ManagedHandle}, and {@link #claim()} hands each to the runtime typed. {@code PLANS} and {@code GOALS} are
 * what the bot wants, {@code RUN} the game's home and setup, {@code SCREEN} the size its pictures were cut at, and {@code PICTURES}, {@code TAGS},
 * {@code DIGITS}, {@code TEXTS}, {@code ELEMENTS}, {@code STATES} and {@code SCREEN_MAP} the classes of
 * constants the bot grows. {@code PLAN} and {@code DO_SETUP} are the
 * run's parameters, methods of the SDK's group {@code params/com_github_botmakerdev/botmaker_sdk/Running.java}, and
 * {@code LAUNCH_TARGET} and {@code CAPTURE} (where pixels are read from) the game's, in {@code Game.java} beside it
 * (2026-11-05: a group per holder, which is its category); {@code Bot.run} reads them by their marks and nothing
 * claims them. {@code LAUNCH_TARGETS} and {@code VM} are the author's game data in
 * {@code Sdk.java}. How the bot clicks, looks and recovers, and where its game runs, are one parameter each in
 * {@code Settings.java} ({@code FOUND_DELAY} to {@code INPUT_BACKEND}, 2026-11-09: they were one {@code BotSettings}
 * method), which {@link SettingParams} reads into {@code BotSettings.current()}.
 *
 * <p><b>It is {@code internal} because a bot never names it</b>, and the plugin half may. It names contract,
 * {@code api} and shared types only — no toolkit — so it is safe in a bot. {@link com.botmaker.sdk.api.bot.Bot#run} calls
 * {@link #claim()} before installing, which is the only ordering anyone has to get right.
 */
public final class SdkValues implements ValueHolder {

    /**
     * The plans — {@code static final Plan DAILY = Plan.of(…)}, each tiers of the goals of {@code Goals.java}. A
     * reference to one, as the {@code plan} parameter holds, reads as its name.
     */
    public static final ManagedOpenSet.OfConstants<Plan> PLANS = ManagedValue.openSet(SdkValue.Id.PLANS)
            .of(Plan.class)
            .referredToAs(BotConstants::plan)
            .in(SdkValue.Holder.PLANS)
            .because("These are the bot's plans: what matters most, as tiers of the goals in Goals.java. Pick one"
                    + " in the Runner to run it.");

    /**
     * The goals — {@code static final Goal ENOUGH_GOLD = Goal.of(…)}, each what is wanted, named by the actions that
     * serve it and the plans that order it. A reference to one, inside an action's {@code .serves(…)} or a tier, reads
     * as its name.
     */
    public static final ManagedOpenSet.OfConstants<Goal> GOALS = ManagedValue.openSet(SdkValue.Id.GOALS)
            .of(Goal.class)
            .referredToAs(BotConstants::goal)
            .in(SdkValue.Holder.GOALS)
            .because("These are the bot's goals: what is wanted, served by the actions of Screens.java and ordered"
                    + " by the plans of Plans.java. See them in 🗺 Screens ▸ 🎯 Goals.");

    /** The game's home and setup — {@code Run.of().home(…)}. */
    public static final ManagedMethod<Run> RUN = ManagedValue.method(SdkValue.Id.RUN)
            .in(SdkValue.Holder.SDK)
            .holds(Run.class, Run.NONE)
            .because("This says how the bot gets the game home and sets it up. Change it in Sdk.java's run().");

    /**
     * The picture constants — {@code static final ImageTemplate COLLECT = new ImageTemplate(…)}. 🖼 Manage
     * Pictures renames the file, the constant and every use of it together; the canvas can only rename the one
     * it is looking at, which would leave the bot calling a name that is gone.
     */
    public static final ManagedOpenSet.OfConstants<ImageTemplate> PICTURES = ManagedValue.openSet(SdkValue.Id.PICTURES)
            .of(ImageTemplate.class)
            .in(SdkValue.Holder.PICTURES)
            .because("Picture constants are managed in 🗺 Screens ▸ 🖼 Pictures, which renames the picture and every"
                    + " use of it together.");

    /**
     * The pictures' own tags — {@code static final PictureTag UI = PictureTag.named("UI")}, which a picture names
     * in {@code .tags(List.of(Tags.UI))} (2026-10-14). A tag inside a picture is a reference to the constant, read by
     * name ({@link BotConstants#tag}), so two tags of one name in two spellings stay one constant.
     */
    public static final ManagedOpenSet.OfConstants<PictureTag> TAGS = ManagedValue.openSet(SdkValue.Id.TAGS)
            .of(PictureTag.class)
            .referredToAs(BotConstants::tag)
            .in(SdkValue.Holder.TAGS)
            .because("Tags.java holds the groups 🗺 Screens ▸ 🖼 Pictures files pictures under. Add, rename or delete one"
                    + " there, which changes every picture it holds together.");

    /**
     * The game fonts' digits — {@code static final DigitSet GOLD = new DigitSet("digits/gold")},
     * a folder of {@code 0.png} to {@code 9.png} that {@code Text.readDigits} reads a number with. The assistant's
     * {@code save_digits} cuts the digits of a number on screen into it and adds the constant.
     */
    public static final ManagedOpenSet.OfConstants<DigitSet> DIGITS = ManagedValue.openSet(SdkValue.Id.DIGITS)
            .of(DigitSet.class)
            .in(SdkValue.Holder.DIGITS)
            .because("Digits.java holds the game fonts' digit pictures. Ask Claude to save_digits on a number the"
                    + " game shows to add or fix one.");

    /**
     * The bot's texts — {@code static final Words GUILD = Words.of("Dragons")}, which a text field of the map types
     * ({@code Button.types(Texts.GUILD)}). A field's text set in 🗺 Screens, or by the assistant's set_input, adds
     * one (2026-10-22).
     */
    public static final ManagedOpenSet.OfConstants<Words> TEXTS = ManagedValue.openSet(SdkValue.Id.TEXTS)
            .of(Words.class)
            .in(SdkValue.Holder.TEXTS)
            .because("Texts.java holds the texts the bot types. Set a text field's text in 🗺 Screens to add one.");

    /**
     * An app's inputs found in the screen's tree — {@code static final Element BATTERY =
     * Element.byId("title").text(Texts.BATTERY)}, which a button of the map presses on an emulator's app. Picking one
     * on a frozen frame in 🗺 Screens adds one (2026-10-31).
     */
    public static final ManagedOpenSet.OfConstants<Element> ELEMENTS = ManagedValue.openSet(SdkValue.Id.ELEMENTS)
            .of(Element.class)
            .in(SdkValue.Holder.ELEMENTS)
            .because("Elements.java holds the app inputs the bot finds in the screen's tree. Pick one on a frozen"
                    + " frame in 🗺 Screens to add one.");

    /**
     * What the bot's buttons' looks mean beyond the SDK's normal, selected and disabled —
     * {@code final class States { enum Blocks implements ButtonState { MAXED } enum Presses implements ButtonState
     * { BADGE } }} (2026-11-02, the user's call). The enum a constant is in says whether a button showing it is
     * pressed. Naming a new state on a look in 🗺 Screens, or to the assistant, adds it.
     */
    public static final ManagedOpenSet.EnumGroups<ButtonState> STATES = ManagedValue.openSet(SdkValue.Id.STATES)
            .ofEnumGroups(ButtonState.class, BotConstants::state)
            .groupedAs(BotConstants.StateGroup.class, BotConstants::stateGroup)
            .in(SdkValue.Holder.STATES)
            .because("States.java holds what the bot's buttons' looks mean: Blocks is never pressed, Presses is."
                    + " Name a new state on a look in 🗺 Screens to add one.");

    /**
     * How the game shows a button changed by colour, a glow, a size or a place — {@code Shades.of(new
     * Shade(Shade.Kind.DIM, …, State.DISABLED, Shade.Geometry.NONE))}: learned on one button seen both ways and
     * applied to all, never chosen (2026-11-02). None written: no button is seen in a shade.
     */
    public static final ManagedMethod<Shades> SHADES = ManagedValue.method(SdkValue.Id.SHADES)
            .in(SdkValue.Holder.SDK)
            .holds(Shades.class, Shades.none())
            .because("These are how the game shows a button dimmed, greyed or tinted, each learned from one button"
                    + " seen both ways. Discovery and the assistant write them; a run applies them to every button.");

    /**
     * The game's screens — {@code static final class Campaign extends Screen} with its constant {@code CAMPAIGN}: its
     * inputs and readouts are its fields ({@code public final Button claim = Button.of(…)}), the rest its {@link
     * Screen#setup}. A
     * screen and an input are named by their constant and field, read by name like a plan ({@link
     * BotConstants#screen}, {@link BotConstants#input}), so one equal to another, or not filled in yet, is still its
     * own.
     */
    public static final ManagedClasses<Screen> SCREEN_MAP = ManagedValue.openSet(SdkValue.Id.SCREEN_MAP)
            .ofClasses(Screen.class)
            .fields(ScreenPart.class, BotConstants::input)
            .setup(Screen::setup)
            .referredToAs(BotConstants::screen)
            .in(SdkValue.Holder.SCREENS)
            .because("Screens.java is the game's map, as Discovery found it. Fill it in with ▶ Try in 🗺 Screens, or"
                    + " ask Claude to try_input on the game.");

    /**
     * The bot's screen size — {@code new Size(1280, 720)}: the frame its pictures were cut from, which a picture
     * names apart only when it was cut from another ({@code .in(Size)}). Written by the first picture cut while it
     * is {@code 0×0}, which is no size: pictures are matched as they are, and a private display is made at the
     * session default.
     */
    public static final ManagedMethod<Size> SCREEN = ManagedValue.method(SdkValue.Id.SCREEN)
            .in(SdkValue.Holder.SDK)
            .holds(Size.class, new Size(0, 0))
            .because("This is the size the bot's pictures were cut at. The first picture cut sets it; a private"
                    + " display is made at it and a game window is resized to it when the bot starts.");

    private static final String RUN_PARAM = "The SDK reads this parameter in the bot's main. Change its value in the"
            + " Runner or in Project ▸ Parameters.";

    /**
     * What a run runs — {@code public Plan plan()} in the SDK's parameter group, one of the {@code Plans}, read by
     * {@code Bot.run} by its mark. Like each parameter below, the host writes it there and in {@code starting-params/}
     * on bind.
     */
    public static final ManagedParam<Plan> PLAN = ManagedValue.param(SdkValue.Id.PLAN)
            .in(SdkValue.Holder.RUNNING)
            .startsAs(SdkValues::noPlan)
            .shown("What the bot runs: one of its plans", Param.Visibility.PUBLIC)
            .because(RUN_PARAM);

    /** Whether a run sets the game up first — {@code public boolean doSetup()}. */
    public static final ManagedParam<Boolean> DO_SETUP = ManagedValue.param(SdkValue.Id.DO_SETUP)
            .in(SdkValue.Holder.RUNNING)
            .startsAs(SdkValues::setUpFirst)
            .shown("Set the game up first: tick it on a new computer or after changing a game"
                    + " setting", Param.Visibility.PUBLIC)
            .because(RUN_PARAM);

    private static final String GAME_PARAM = "The SDK reads this parameter when the bot starts its game. Pick the"
            + " game in 🎮 Game, in the Runner or in Project ▸ Parameters.";

    /**
     * The launchers the bot supports, preferred first — {@code public static List<Launcher> launchTargets()} in
     * {@code Sdk.java}, each a launch spec ({@code Launcher.of("steam:1145350")}). The bot's author lists them, so
     * it is the bot's data, not a parameter (2026-11-04, the user's rule); a run takes {@link #LAUNCH_TARGET}'s pick,
     * else the first its computer's system can launch ({@code LaunchKind.runsOn}), so nothing about a platform is
     * written. Empty: the bot starts no game, and is run against one already open.
     */
    @SuppressWarnings("unchecked")
    public static final ManagedMethod<List<Launcher>> LAUNCH_TARGETS = ManagedValue.method(SdkValue.Id.LAUNCH_TARGETS)
            .in(SdkValue.Holder.SDK)
            .holds((Class<List<Launcher>>) (Class<?>) List.class, List.of())
            .because("This is the list of games the bot supports. Add or remove a game in 🎮 Game.");

    /**
     * Which of {@link #LAUNCH_TARGETS} a run starts — {@code public LaunchChoice launchTarget()}, picked in the
     * Runner by the person running the bot (2026-10-19: apart from the author's list, which a pick used to reorder
     * and grow). None: the first this computer can launch.
     */
    public static final ManagedParam<LaunchChoice> LAUNCH_TARGET = ManagedValue.param(SdkValue.Id.LAUNCH_TARGET)
            .in(SdkValue.Holder.GAME)
            .startsAs(SdkValues::firstAvailable)
            .shown("Which of the bot's games this computer launches", Param.Visibility.PUBLIC)
            .because(GAME_PARAM);

    /**
     * Where the bot reads pixels from — {@code public CaptureSource captureSource()}, the game's window as the
     * person running the bot picked it (2026-10-13: a parameter, so the Runner shows it). {@code Bot.run} reads
     * it by its mark ({@code InstalledGame}).
     */
    public static final ManagedParam<CaptureSource> CAPTURE = ManagedValue.param(SdkValue.Id.CAPTURE)
            .in(SdkValue.Holder.GAME)
            .startsAs(SdkValues::wholeDesktop)
            .shown("The game's window: where the bot reads pixels from", Param.Visibility.PUBLIC)
            .because("This is where the bot reads pixels from. Choose it in the Runner or in Project ▸ Parameters.");

    /**
     * The game VM the game runs in when {@code gameRunsIn()} says a VM — {@code public static GameVm vm()} in
     * {@code Sdk.java}, by name; {@code GameVm.of("")} for the one VM there is. The author's, so bot data (2026-11-04,
     * the user's rule); a {@code GameVm} since 2026-11-09, drawn as this computer's VMs with their setup and screen.
     */
    public static final ManagedMethod<GameVm> VM = ManagedValue.method(SdkValue.Id.VM)
            .in(SdkValue.Holder.SDK)
            .holds(GameVm.class, GameVm.of(""))
            .because("This is the game VM the game runs in. Pick it, or set one up, in Sdk.java's vm().");

    /**
     * The game server's time zone — {@code public ZoneId gameZone()}, {@code ZoneId.of("Europe/Paris")}, which a
     * {@code Reset} with no zone of its own is read in. A parameter, since one game's servers sit in several zones and
     * the runner plays on one. A {@code ZoneId} since 2026-11-09, the user's call: it starts as the zone of the
     * computer that binds it, since a value cannot say "this computer's" (an {@code Optional} would, but the grammar
     * writes none). The {@code String} it was is another type: an upgrade removes it and bind writes this one.
     */
    public static final ManagedParam<ZoneId> GAME_ZONE = ManagedValue.param(SdkValue.Id.GAME_ZONE)
            .in(SdkValue.Holder.GAME)
            .startsAs(SdkValues::thisComputersZone)
            .shown("The game server's time zone, that its daily reset is read in", Param.Visibility.PUBLIC)
            .because("The SDK reads this parameter whenever a daily asks for the game's last reset. Set the"
                    + " server's zone, such as Europe/Paris, in the Runner or in Project ▸ Parameters.");

    /**
     * Words a run exploring on its own refuses to press beside — {@code public static List<String> guardWords()} in
     * {@code Sdk.java}, the author's, matched case-blind as whole words (2026-11-09). Seeded empty: the guards are
     * general, currency signs are built in, and the author adds the app's own words and language.
     */
    @SuppressWarnings("unchecked")
    public static final ManagedMethod<List<String>> GUARD_WORDS = ManagedValue.method(SdkValue.Id.GUARD_WORDS)
            .in(SdkValue.Holder.SDK)
            .holds((Class<List<String>>) (Class<?>) List.class, List.of())
            .because("These are words a run exploring on its own never presses beside unless the input spends, such"
                    + " as Buy or Delete in the app's language. Currency signs are refused without being listed.");

    private static final String SETTING = "The SDK reads this parameter when the bot starts. Change its value in the"
            + " Runner or in Project ▸ Parameters ▸ Settings.";

    /** The pause after a match is found — {@code public int foundDelay()}, in ms. */
    public static final ManagedParam<Integer> FOUND_DELAY = ManagedValue.param(SdkValue.Id.FOUND_DELAY)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::foundDelay)
            .shown("Pause after a picture is found, in ms: long enough for the game's animation to settle",
                    Param.Visibility.PUBLIC)
            .because(SETTING);

    /** The pause after a match is not found — {@code public int notFoundDelay()}, in ms. */
    public static final ManagedParam<Integer> NOT_FOUND_DELAY = ManagedValue.param(SdkValue.Id.NOT_FOUND_DELAY)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::notFoundDelay)
            .shown("Pause after a picture is not found, in ms: how fast the bot looks again", Param.Visibility.PUBLIC)
            .because(SETTING);

    /** Whether a click lands on a random point of the match — {@code public boolean randomClicks()}. */
    public static final ManagedParam<Boolean> RANDOM_CLICKS = ManagedValue.param(SdkValue.Id.RANDOM_CLICKS)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::randomClicks)
            .shown("Click a random point of what was found rather than its centre", Param.Visibility.PUBLIC)
            .because(SETTING);

    /** How sure a match has to be — {@code public double confidence()}, 0..1. */
    public static final ManagedParam<Double> CONFIDENCE = ManagedValue.param(SdkValue.Id.CONFIDENCE)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::confidence)
            .shown("How sure a match has to be, from 0 to 1: lower finds more, and finds wrong things more",
                    Param.Visibility.PUBLIC)
            .because(SETTING);

    /** How far a good picture must beat a look-alike — {@code public double compareMargin()}, 0..1. */
    public static final ManagedParam<Double> COMPARE_MARGIN = ManagedValue.param(SdkValue.Id.COMPARE_MARGIN)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::compareMargin)
            .shown("How far a picture must beat its look-alike, from 0 to 1", Param.Visibility.PUBLIC)
            .because(SETTING);

    /** The rounds a vision loop runs before the bot is stuck — {@code public int maxRounds()}. */
    public static final ManagedParam<Integer> MAX_ROUNDS = ManagedValue.param(SdkValue.Id.MAX_ROUNDS)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::maxRounds)
            .shown("Rounds a repeated look runs before the bot counts as stuck", Param.Visibility.PUBLIC)
            .because(SETTING);

    /** How long a loop's round gives its action to show — {@code public int patience()}, in ms. */
    public static final ManagedParam<Integer> PATIENCE = ManagedValue.param(SdkValue.Id.PATIENCE)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::patience)
            .shown("How long, in ms, the bot waits for a press to show before it acts again", Param.Visibility.PUBLIC)
            .because(SETTING);

    /** How long the screen may stay still while the bot acts — {@code public int frozenAfter()}, in ms. */
    public static final ManagedParam<Integer> FROZEN_AFTER = ManagedValue.param(SdkValue.Id.FROZEN_AFTER)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::frozenAfter)
            .shown("How long, in ms, the screen may stay still while the bot acts before the game counts as frozen",
                    Param.Visibility.PUBLIC)
            .because(SETTING);

    /** How long a plan run may act with no progress — {@code public Duration stuckAfter()}. */
    public static final ManagedParam<Duration> STUCK_AFTER = ManagedValue.param(SdkValue.Id.STUCK_AFTER)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::stuckAfter)
            .shown("How long a run may act with no progress before it goes home or restarts the game",
                    Param.Visibility.PUBLIC)
            .because(SETTING);

    /** The game restarts a run allows within {@link #RESTARTS_WITHIN} — {@code public int restartCap()}. */
    public static final ManagedParam<Integer> RESTART_CAP = ManagedValue.param(SdkValue.Id.RESTART_CAP)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::restartCap)
            .shown("Game restarts allowed in a while before the bot stops instead", Param.Visibility.PUBLIC)
            .because(SETTING);

    /** The time {@link #RESTART_CAP} counts over — {@code public Duration restartsWithin()}. */
    public static final ManagedParam<Duration> RESTARTS_WITHIN = ManagedValue.param(SdkValue.Id.RESTARTS_WITHIN)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::restartsWithin)
            .shown("The while the game restarts are counted over", Param.Visibility.PUBLIC)
            .because(SETTING);

    /** Where the game runs — {@code public BotSettings.Where gameRunsIn()}. */
    public static final ManagedParam<BotSettings.Where> GAME_RUNS_IN = ManagedValue.param(SdkValue.Id.GAME_RUNS_IN)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::gameRunsIn)
            .shown("Where the game runs: a private display, a game VM, or your desktop", Param.Visibility.PUBLIC)
            .because(SETTING);

    /** On the desktop, the real mouse and keyboard — {@code public boolean takeOver()}. */
    public static final ManagedParam<Boolean> TAKE_OVER = ManagedValue.param(SdkValue.Id.TAKE_OVER)
            .in(SdkValue.Holder.SETTINGS)
            .startsAs(SdkValues::takeOver)
            .shown("On your desktop, drive the real mouse and keyboard: for a game that ignores sent clicks",
                    Param.Visibility.PUBLIC)
            .because(SETTING);

    /** Which private display hosts the game — {@code public BotSettings.DisplayBackend displayBackend()}. */
    public static final ManagedParam<BotSettings.DisplayBackend> DISPLAY_BACKEND =
            ManagedValue.param(SdkValue.Id.DISPLAY_BACKEND)
                    .in(SdkValue.Holder.SETTINGS)
                    .startsAs(SdkValues::displayBackend)
                    .shown("Which private display hosts the game", Param.Visibility.PUBLIC)
                    .because(SETTING);

    /** Which Linux backend delivers a take-over — {@code public BotSettings.InputBackend inputBackend()}. */
    public static final ManagedParam<BotSettings.InputBackend> INPUT_BACKEND =
            ManagedValue.param(SdkValue.Id.INPUT_BACKEND)
                    .in(SdkValue.Holder.SETTINGS)
                    .startsAs(SdkValues::inputBackend)
                    .shown("Which Linux backend drives the real mouse and keyboard", Param.Visibility.PUBLIC)
                    .because(SETTING);

    private static boolean claimed;

    private SdkValues() {}

    @Override
    public List<ManagedValue<?>> values() {
        return List.of(PLANS, GOALS, RUN, PICTURES, TAGS, DIGITS, TEXTS, ELEMENTS, STATES, SCREEN_MAP,
                SCREEN, SHADES, PLAN, DO_SETUP, LAUNCH_TARGETS, LAUNCH_TARGET, CAPTURE, VM, GAME_ZONE, GUARD_WORDS,
                FOUND_DELAY, NOT_FOUND_DELAY, RANDOM_CLICKS, CONFIDENCE, COMPARE_MARGIN, MAX_ROUNDS, PATIENCE,
                FROZEN_AFTER, STUCK_AFTER, RESTART_CAP, RESTARTS_WITHIN, GAME_RUNS_IN, TAKE_OVER, DISPLAY_BACKEND,
                INPUT_BACKEND);
    }

    // The settings' first values: BotSettings.DEFAULTS, field by field.

    private static int foundDelay() {
        return BotSettings.DEFAULT_FOUND_DELAY;
    }

    private static int notFoundDelay() {
        return BotSettings.DEFAULT_NOT_FOUND_DELAY;
    }

    private static boolean randomClicks() {
        return BotSettings.DEFAULT_RANDOMIZE_CLICKS;
    }

    private static double confidence() {
        return BotSettings.DEFAULT_CONFIDENCE;
    }

    private static double compareMargin() {
        return BotSettings.DEFAULT_COMPARE_MARGIN;
    }

    private static int maxRounds() {
        return BotSettings.DEFAULT_MAX_ROUNDS;
    }

    private static int patience() {
        return BotSettings.DEFAULT_PATIENCE;
    }

    private static int frozenAfter() {
        return BotSettings.DEFAULT_FROZEN_AFTER;
    }

    private static Duration stuckAfter() {
        return BotSettings.DEFAULT_STUCK_AFTER;
    }

    private static int restartCap() {
        return BotSettings.DEFAULT_RESTART_CAP;
    }

    private static Duration restartsWithin() {
        return BotSettings.DEFAULT_RESTARTS_WITHIN;
    }

    private static BotSettings.Where gameRunsIn() {
        return BotSettings.DEFAULTS.where();
    }

    private static boolean takeOver() {
        return BotSettings.DEFAULTS.takeOver();
    }

    private static BotSettings.DisplayBackend displayBackend() {
        return BotSettings.DEFAULTS.runIn().displayBackend();
    }

    private static BotSettings.InputBackend inputBackend() {
        return BotSettings.DEFAULTS.runIn().inputBackend();
    }

    /** {@link #PLAN}'s type and first value: nothing until the runner picks a plan. */
    private static Plan noPlan() {
        return Plan.NONE;
    }

    /** {@link #DO_SETUP}'s: a new bot sets its game up on its first run. */
    private static boolean setUpFirst() {
        return true;
    }

    /** {@link #LAUNCH_TARGET}'s: no pick, so the first launcher this computer can launch. */
    private static LaunchChoice firstAvailable() {
        return LaunchChoice.of("");
    }

    /** {@link #GAME_ZONE}'s: this computer's zone, until the runner names the server's. */
    private static ZoneId thisComputersZone() {
        return ZoneId.systemDefault();
    }

    /** {@link #CAPTURE}'s: the whole desktop until the game's window is picked. */
    private static CaptureSource wholeDesktop() {
        return CaptureSource.desktop();
    }

    /**
     * Registers this plugin's values with {@link ManagedValues}. Idempotent, and cheap enough to be called on
     * every {@code Bot.run} rather than guarded by its caller.
     */
    public static synchronized void claim() {
        if (claimed) {
            return;
        }
        claimed = true;
        ManagedValues.claim(RUN, InstalledRun::use);
        ManagedValues.claim(SCREEN, BotScreen::use);
        ManagedValues.claim(SHADES, ShadeFit::use);
        // The launchers and the VM: InstalledGame reads them by their marks, with the parameters beside them.
        ManagedValues.claim(LAUNCH_TARGETS, launchers -> { });
        ManagedValues.claim(VM, vm -> { });
    }
}
