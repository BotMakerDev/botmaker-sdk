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
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.plan.Routine;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.ScreenPart;
import com.botmaker.sdk.api.screens.Shades;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.PictureTag;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.InstalledRun;
import com.botmaker.sdk.internal.screens.ShadeFit;
import com.botmaker.sdk.internal.vision.BotScreen;

import java.util.List;

/**
 * The SDK's managed values, each declared once, and what a run does with each one.
 *
 * <p>One declaration per {@link SdkValue.Id} constant, which a bot writes as {@code @SdkValue(…)}: the
 * plugin names this class ({@code .values(SdkValues.class)}), so each value {@link #values()} lists is one of
 * its {@code managedValues()}, in list order; its windows open each one through a toolkit
 * {@code ManagedHandle}, and {@link #claim()} hands each to the runtime typed. {@code FLOWS} are the activity
 * flows, {@code RUN} the game's home and setup, {@code FLOW_LAYOUT} where the
 * flow editor draws each card (a run ignores it), {@code SETTINGS} how it clicks and looks, {@code SCREEN} the
 * size its pictures were cut at, and {@code PICTURES}, {@code TAGS}, {@code ACTIVITIES},
 * {@code ZONES}, {@code DIGITS}, {@code TEXTS}, {@code ELEMENTS}, {@code STATES} and {@code SCREEN_MAP} the classes of constants the bot grows. {@code ROUTINE} and {@code DO_SETUP} are the
 * run's parameters, methods of the SDK's group {@code params/com_github_botmakerdev/botmaker_sdk/Running.java}, and
 * {@code LAUNCH_TARGET} and {@code CAPTURE} (where pixels are read from) the game's, in {@code Game.java} beside it
 * (2026-11-05: a group per holder, which is its category); {@code Bot.runFlow} reads them by their marks and nothing
 * claims them. {@code LAUNCH_TARGETS} and {@code VM} are the author's game data in
 * {@code Sdk.java}.
 *
 * <p><b>It is {@code internal} because a bot never names it</b>, and the plugin half may. It names contract,
 * {@code api} and shared types only — no toolkit — so it is safe in a bot. {@link com.botmaker.sdk.api.bot.Bot#runFlow} calls
 * {@link #claim()} before installing, which is the only ordering anyone has to get right.
 */
public final class SdkValues implements ValueHolder {

    /**
     * The flows — {@code static final Flow MAIN = Flow.of(…)}, one per way of playing. 🔀 Activity Flow draws one,
     * keeping its activities, wires and layout in step.
     */
    public static final ManagedOpenSet.OfConstants<Flow> FLOWS = ManagedValue.openSet(SdkValue.Id.FLOWS)
            .of(Flow.class)
            .referredToAs(BotConstants::flow)
            .in(SdkValue.Holder.FLOWS)
            .because("These are the bot's activity flows. Draw them in 🔀 Activity Flow, which keeps the"
                    + " activities, the wires and the layout in step.");

    /**
     * The plans — {@code static final Plan DAILY = Plan.of(…)}, each tiers of the goals of {@code Goals.java}. A
     * reference to one, as the {@code routine} parameter holds, reads as its name.
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
                    + " by the plans of Plans.java. See them in 🎯 Goals.");

    /** The game's home and setup — {@code Run.of().home(…)}. */
    public static final ManagedMethod<Run> RUN = ManagedValue.method(SdkValue.Id.RUN)
            .in(SdkValue.Holder.SDK)
            .holds(Run.class, Run.NONE)
            .because("This says how the bot gets the game home and sets it up. Change them in 🔀 Activity Flow.");

    public static final ManagedMethod<FlowLayout> FLOW_LAYOUT = ManagedValue.method(SdkValue.Id.FLOW_LAYOUT)
            .in(SdkValue.Holder.SDK)
            .holds(FlowLayout.class, FlowLayout.NONE)
            .because("These are where the Activity Flow's cards sit. Drag them in 🔀 Activity Flow, which keeps"
                    + " them in step with the activities' names.");

    public static final ManagedMethod<BotSettings> SETTINGS = ManagedValue.method(SdkValue.Id.SETTINGS)
            .in(SdkValue.Holder.SDK)
            .holds(BotSettings.class, BotSettings.DEFAULTS)
            .because("These are the bot's settings — delays, confidence, input and its private display. Change"
                    + " them in ⚙ Bot Settings.");

    /**
     * The picture constants — {@code static final ImageTemplate COLLECT = new ImageTemplate(…)}. 🖼 Manage
     * Pictures renames the file, the constant and every use of it together; the canvas can only rename the one
     * it is looking at, which would leave the bot calling a name that is gone.
     */
    public static final ManagedOpenSet.OfConstants<ImageTemplate> PICTURES = ManagedValue.openSet(SdkValue.Id.PICTURES)
            .of(ImageTemplate.class)
            .in(SdkValue.Holder.PICTURES)
            .because("Picture constants are managed in 🖼 Manage Pictures, which renames the picture and every"
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
            .because("Tags.java holds the groups 🖼 Manage Pictures files pictures under. Add, rename or delete one"
                    + " there, which changes every picture it holds together.");

    /**
     * The activities, each an enum of its outcomes nested in one class —
     * {@code final class Activities { enum Collect implements Outcome { NEXT, NOTHING_LEFT } }} (2026-10-10).
     * 🔀 Activity Flow adds, renames and removes an enum with its card and a constant with its exit, together with
     * every step, wire, body and {@code ActivitySwitch} call naming them.
     */
    public static final ManagedOpenSet.EnumGroups<Outcome> ACTIVITIES = ManagedValue.openSet(SdkValue.Id.ACTIVITIES)
            .ofEnumGroups(Outcome.class, BotConstants::outcome)
            .groupedAs(Activity.class, BotConstants::activity)
            .in(SdkValue.Holder.ACTIVITIES)
            .because("Activities and their outcomes are managed in 🔀 Activity Flow, which renames the card or the"
                    + " exit and every use of it together.");

    /**
     * The named areas — {@code static final Zone BAG = new Zone(10, 20, 200, 120)}, in the bot's pixels, which
     * {@code source.region(Zone)} places on the game as it shows. The assistant's {@code save_region} adds one, or
     * moves one it names again.
     */
    public static final ManagedOpenSet.OfConstants<Zone> ZONES = ManagedValue.openSet(SdkValue.Id.ZONES)
            .of(Zone.class)
            .in(SdkValue.Holder.ZONES)
            .because("Zones.java holds the bot's named areas. Ask Claude to save_region an area again, under its"
                    + " name, to move it.");

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
     * screen and an input are named by their constant and field, read by name like a sub-flow ({@link
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
     * What a run runs — {@code public Routine routine()} in the SDK's parameter group, one of the {@code Flows} or of
     * the {@code Plans}, read by {@code Bot.runFlow} by its mark. Like each parameter below, the host writes it there
     * and in {@code starting-params/} on bind.
     */
    public static final ManagedParam<Routine> ROUTINE = ManagedValue.param(SdkValue.Id.ROUTINE)
            .in(SdkValue.Holder.RUNNING)
            .startsAs(SdkValues::noRoutine)
            .shown("What the bot runs: a flow or a plan", Param.Visibility.PUBLIC)
            .because(RUN_PARAM);

    // The flow parameter ROUTINE replaces is no value of the SDK's any more, so bind writes it in no bot: a bot that
    // has one keeps it marked, and a run reads it by that mark while routine is Flow.NONE, until it goes next release.

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
     * person running the bot picked it (2026-10-13: a parameter, so the Runner shows it). {@code Bot.runFlow} reads
     * it by its mark ({@code InstalledGame}).
     */
    public static final ManagedParam<CaptureSource> CAPTURE = ManagedValue.param(SdkValue.Id.CAPTURE)
            .in(SdkValue.Holder.GAME)
            .startsAs(SdkValues::wholeDesktop)
            .shown("The game's window: where the bot reads pixels from", Param.Visibility.PUBLIC)
            .because("This is where the bot reads pixels from. Choose it in 🎯 Capture Source, in the Runner or in"
                    + " Project ▸ Parameters.");

    /**
     * The game VM the game runs in when ⚙ Bot Settings runs it in one — {@code public static String vm()} in
     * {@code Sdk.java}, by name; blank for the one VM there is. The author's, so bot data (2026-11-04, the user's
     * rule); written when ⚙ Bot Settings first names one, and read by its mark.
     */
    public static final ManagedMethod<String> VM = ManagedValue.method(SdkValue.Id.VM)
            .in(SdkValue.Holder.SDK)
            .holds(String.class, "")
            .because("This is the game VM ⚙ Bot Settings runs the game in. Change it there.");

    /**
     * The game server's time zone — {@code public String gameZone()}, a zone id such as {@code Europe/Paris}, which a
     * {@code Reset} with no zone of its own is read in (2026-11-09). A parameter, since one game's servers sit in
     * several zones and the runner plays on one; blank for this computer's zone.
     */
    public static final ManagedParam<String> GAME_ZONE = ManagedValue.param(SdkValue.Id.GAME_ZONE)
            .in(SdkValue.Holder.GAME)
            .startsAs(SdkValues::thisComputersZone)
            .shown("The game server's time zone, such as Europe/Paris, that its daily reset is read in; blank for"
                    + " this computer's", Param.Visibility.PUBLIC)
            .because("The SDK reads this parameter whenever a daily asks for the game's last reset. Type the"
                    + " server's zone id, such as Europe/Paris, in the Runner or in Project ▸ Parameters.");

    private static boolean claimed;

    private SdkValues() {}

    @Override
    public List<ManagedValue<?>> values() {
        return List.of(FLOWS, PLANS, GOALS, RUN, FLOW_LAYOUT, SETTINGS, PICTURES, TAGS, ACTIVITIES, ZONES, DIGITS, TEXTS, ELEMENTS,
                STATES, SCREEN_MAP, SCREEN, SHADES, ROUTINE, DO_SETUP, LAUNCH_TARGETS, LAUNCH_TARGET, CAPTURE, VM,
                GAME_ZONE);
    }

    /** {@link #ROUTINE}'s type and first value: nothing until the runner picks a flow or a plan. */
    private static Routine noRoutine() {
        return Flow.NONE;
    }

    /** {@link #DO_SETUP}'s: a new bot sets its game up on its first run. */
    private static boolean setUpFirst() {
        return true;
    }

    /** {@link #LAUNCH_TARGET}'s: no pick, so the first launcher this computer can launch. */
    private static LaunchChoice firstAvailable() {
        return LaunchChoice.of("");
    }

    /** {@link #GAME_ZONE}'s: blank, this computer's zone, until the runner names the server's. */
    private static String thisComputersZone() {
        return "";
    }

    /** {@link #CAPTURE}'s: the whole desktop until the game's window is picked. */
    private static CaptureSource wholeDesktop() {
        return CaptureSource.desktop();
    }

    /**
     * Registers this plugin's values with {@link ManagedValues}. Idempotent, and cheap enough to be called on
     * every {@code Bot.runFlow} rather than guarded by its caller.
     */
    public static synchronized void claim() {
        if (claimed) {
            return;
        }
        claimed = true;
        ManagedValues.claim(RUN, InstalledRun::use);
        ManagedValues.claim(SETTINGS, BotSettings::use);
        ManagedValues.claim(SCREEN, BotScreen::use);
        ManagedValues.claim(SHADES, ShadeFit::use);
        // Where the flow editor's cards sit: the editor's, and nothing at runtime reads it. Claimed so a run
        // does not report it as a value nobody takes.
        ManagedValues.claim(FLOW_LAYOUT, layout -> { });
        // The launchers and the VM: InstalledGame reads them by their marks, with the parameters beside them.
        ManagedValues.claim(LAUNCH_TARGETS, launchers -> { });
        ManagedValues.claim(VM, vm -> { });
    }
}
