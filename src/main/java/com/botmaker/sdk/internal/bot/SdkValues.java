package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.managed.ManagedValues;
import com.botmaker.plugin.api.params.Param;
import com.botmaker.plugin.api.source.ManagedClasses;
import com.botmaker.plugin.api.source.ManagedMethod;
import com.botmaker.plugin.api.source.ManagedOpenSet;
import com.botmaker.plugin.api.source.ManagedParam;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.ParamScope;
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
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.PictureTag;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.InstalledRun;
import com.botmaker.sdk.internal.vision.BotScreen;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.shared.platform.Os;

import java.util.List;
import java.util.Set;

/**
 * The SDK's managed values, each declared once, and what a run does with each one.
 *
 * <p>One declaration per {@link SdkValue.Id} constant, which a bot writes as {@code @SdkValue(…)}: the
 * plugin names this class ({@code .values(SdkValues.class)}), so each {@code public static final} field is one of
 * its {@code managedValues()}, in field order; its windows open each one through a toolkit
 * {@code ManagedHandle}, and {@link #claim()} hands each to the runtime typed. {@code FLOWS} are the activity
 * flows, {@code RUN} the game's home and setup, {@code FLOW_LAYOUT} where the
 * flow editor draws each card (a run ignores it), {@code SETTINGS} how it clicks and looks, {@code SCREEN} the
 * size its pictures were cut at, and {@code PICTURES}, {@code TAGS}, {@code ACTIVITIES},
 * {@code ZONES}, {@code DIGITS}, {@code TEXTS} and {@code SCREEN_MAP} the classes of constants the bot grows. {@code FLOW}, {@code DO_SETUP} and {@code SKIP} are the
 * run's {@code @Param} fields in {@code Sdk.java}, which {@code Bot.runFlow} reads by their marks and nothing
 * claims; {@code LAUNCH_TARGETS}, {@code CAPTURE} (where pixels are read from) and {@code VM} the game's,
 * which {@code Bot.runFlow} reads by their marks.
 *
 * <p><b>It is {@code internal} because a bot never names it</b>, and the plugin half may. It names contract,
 * {@code api} and shared types only — no toolkit — so it is safe in a bot. {@link com.botmaker.sdk.api.bot.Bot#runFlow} calls
 * {@link #claim()} before installing, which is the only ordering anyone has to get right.
 */
public final class SdkValues {

    /** The class the method-shaped values live in: {@code plugins/sdk/Sdk.java}, named by the bot's {@code main}. */
    public static final String HOLDER = "Sdk";

    /**
     * The flows — {@code static final Flow MAIN = Flow.of(…)}, one per way of playing. 🔀 Activity Flow draws one,
     * keeping its activities, wires and layout in step.
     */
    public static final ManagedOpenSet.OfConstants<Flow> FLOWS = ManagedValue.openSet(SdkValue.Id.FLOWS)
            .of(Flow.class)
            .referredToAs(BotConstants::flow)
            .in("Flows")
            .because("These are the bot's activity flows. Draw them in 🔀 Activity Flow, which keeps the"
                    + " activities, the wires and the layout in step.");

    /** The game's home and setup — {@code Run.of().home(…)}. */
    public static final ManagedMethod<Run> RUN = ManagedValue.method(SdkValue.Id.RUN)
            .in(HOLDER)
            .holds(Run.class, Run.NONE)
            .because("This says how the bot gets the game home and sets it up. Change them in 🔀 Activity Flow.");

    public static final ManagedMethod<FlowLayout> FLOW_LAYOUT = ManagedValue.method(SdkValue.Id.FLOW_LAYOUT)
            .in(HOLDER)
            .holds(FlowLayout.class, FlowLayout.NONE)
            .because("These are where the Activity Flow's cards sit. Drag them in 🔀 Activity Flow, which keeps"
                    + " them in step with the activities' names.");

    public static final ManagedMethod<BotSettings> SETTINGS = ManagedValue.method(SdkValue.Id.SETTINGS)
            .in(HOLDER)
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
            .in(TemplateNames.CLASS_NAME)
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
            .in("Tags")
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
            .in("Activities")
            .because("Activities and their outcomes are managed in 🔀 Activity Flow, which renames the card or the"
                    + " exit and every use of it together.");

    /**
     * The named areas — {@code static final Zone BAG = new Zone(10, 20, 200, 120)}, in the bot's pixels, which
     * {@code source.region(Zone)} places on the game as it shows. The assistant's {@code save_region} adds one, or
     * moves one it names again.
     */
    public static final ManagedOpenSet.OfConstants<Zone> ZONES = ManagedValue.openSet(SdkValue.Id.ZONES)
            .of(Zone.class)
            .in("Zones")
            .because("Zones.java holds the bot's named areas. Ask Claude to save_region an area again, under its"
                    + " name, to move it.");

    /**
     * The game fonts' digits — {@code static final DigitSet GOLD = new DigitSet("src/main/resources/digits/gold")},
     * a folder of {@code 0.png} to {@code 9.png} that {@code Text.readDigits} reads a number with. The assistant's
     * {@code save_digits} cuts the digits of a number on screen into it and adds the constant.
     */
    public static final ManagedOpenSet.OfConstants<DigitSet> DIGITS = ManagedValue.openSet(SdkValue.Id.DIGITS)
            .of(DigitSet.class)
            .in("Digits")
            .because("Digits.java holds the game fonts' digit pictures. Ask Claude to save_digits on a number the"
                    + " game shows to add or fix one.");

    /**
     * The bot's texts — {@code static final Words GUILD = Words.of("Dragons")}, which a text field of the map types
     * ({@code Button.types(Texts.GUILD)}). A field's text set in 🗺 Screens, or by the assistant's set_input, adds
     * one (2026-10-22).
     */
    public static final ManagedOpenSet.OfConstants<Words> TEXTS = ManagedValue.openSet(SdkValue.Id.TEXTS)
            .of(Words.class)
            .in("Texts")
            .because("Texts.java holds the texts the bot types. Set a text field's text in 🗺 Screens to add one.");

    /**
     * The game's screens — {@code static final class Campaign extends Screen} with its constant {@code CAMPAIGN}: its
     * inputs are its fields ({@code public final Button claim = Button.of(…)}), the rest its {@link Screen#setup}. A
     * screen and an input are named by their constant and field, read by name like a sub-flow ({@link
     * BotConstants#screen}, {@link BotConstants#input}), so one equal to another, or not filled in yet, is still its
     * own.
     */
    public static final ManagedClasses<Screen> SCREEN_MAP = ManagedValue.openSet(SdkValue.Id.SCREEN_MAP)
            .ofClasses(Screen.class)
            .fields(Input.class, BotConstants::input)
            .setup(Screen::setup)
            .referredToAs(BotConstants::screen)
            .in("Screens")
            .because("Screens.java is the game's map, as Discovery found it. Fill it in with ▶ Try in 🗺 Screens, or"
                    + " ask Claude to try_input on the game.");

    /**
     * The bot's screen size — {@code new Size(1280, 720)}: the frame its pictures were cut from, which a picture
     * names apart only when it was cut from another ({@code .in(Size)}). Written by the first picture cut while it
     * is {@code 0×0}, which is no size: pictures are matched as they are, and a private display is made at the
     * session default.
     */
    public static final ManagedMethod<Size> SCREEN = ManagedValue.method(SdkValue.Id.SCREEN)
            .in(HOLDER)
            .holds(Size.class, new Size(0, 0))
            .because("This is the size the bot's pictures were cut at. The first picture cut sets it; a private"
                    + " display is made at it and a game window is resized to it when the bot starts.");

    /** The run parameters' heading in the Runner and the Parameters window. */
    private static final String RUN_CATEGORY = "Run";

    private static final String RUN_PARAM = "The SDK reads this parameter in the bot's main. Change its value in the"
            + " Runner or in Project ▸ Parameters.";

    /** Which flow a run walks — {@code public static Flow flow}, read by {@code Bot.runFlow} by its mark. */
    public static final ManagedParam<Flow> FLOW = ManagedValue.param(SdkValue.Id.FLOW)
            .in(HOLDER)
            .startsAs(SdkValues::noFlow)
            .shown(RUN_CATEGORY, "Which flow the bot runs", Param.Visibility.PUBLIC)
            .because(RUN_PARAM);

    /** Whether a run sets the game up first — {@code public static boolean doSetup}. */
    public static final ManagedParam<Boolean> DO_SETUP = ManagedValue.param(SdkValue.Id.DO_SETUP)
            .in(HOLDER)
            .startsAs(SdkValues::setUpFirst)
            .shown(RUN_CATEGORY, "Set the game up first: tick it on a new computer or after changing a game"
                    + " setting", Param.Visibility.PUBLIC)
            .because(RUN_PARAM);

    /** The activities a run switches off — {@code public static Set<Class<? extends Outcome>> skip}. */
    public static final ManagedParam<Set<Class<? extends Outcome>>> SKIP = ManagedValue.param(SdkValue.Id.SKIP)
            .in(HOLDER)
            .startsAs(SdkValues::skipNothing)
            .shown(RUN_CATEGORY, "Activities this run skips", Param.Visibility.PUBLIC)
            .because(RUN_PARAM);

    /** The game parameters' heading in the Runner and the Parameters window. */
    private static final String GAME_CATEGORY = "Game";

    private static final String GAME_PARAM = "The SDK reads this parameter when the bot starts its game. Pick the"
            + " game in 🎮 Game, in the Runner or in Project ▸ Parameters.";

    /**
     * The launchers the bot supports, preferred first — {@code public static List<Launcher> launchTargets}, each a
     * launch spec ({@code Launcher.of("steam:1145350")}). The bot's author lists them, so the Runner does not show
     * it; a run takes {@link #LAUNCH_TARGET}'s pick, else the first its computer's system can launch
     * ({@code LaunchKind.runsOn}), so nothing about a platform is written. Empty: the bot starts no game, and is run
     * against one already open.
     */
    public static final ManagedParam<List<Launcher>> LAUNCH_TARGETS = ManagedValue.param(SdkValue.Id.LAUNCH_TARGETS)
            .in(HOLDER)
            .startsAs(SdkValues::noLaunchTargets)
            .shown(GAME_CATEGORY, "The games this bot supports, preferred first", Param.Visibility.EDITOR)
            .because("The SDK reads this parameter when the bot starts its game. Add or remove a game in 🎮 Game or"
                    + " in Project ▸ Parameters.");

    /**
     * Which of {@link #LAUNCH_TARGETS} a run starts — {@code public static LaunchChoice launchTarget}, picked in the
     * Runner by the person running the bot (2026-10-19: apart from the author's list, which a pick used to reorder
     * and grow). None: the first this computer can launch.
     */
    public static final ManagedParam<LaunchChoice> LAUNCH_TARGET = ManagedValue.param(SdkValue.Id.LAUNCH_TARGET)
            .in(HOLDER)
            .startsAs(SdkValues::firstAvailable)
            .shown(GAME_CATEGORY, "Which of the bot's games this computer launches", Param.Visibility.PUBLIC)
            .because(GAME_PARAM);

    /**
     * Where the bot reads pixels from — {@code public static CaptureSource captureSource}, the game's window as the
     * person running the bot picked it (2026-10-13: a parameter, so the Runner shows it). {@code Bot.runFlow} reads
     * it by its mark ({@code InstalledGame}).
     */
    public static final ManagedParam<CaptureSource> CAPTURE = ManagedValue.param(SdkValue.Id.CAPTURE)
            .in(HOLDER)
            .startsAs(SdkValues::wholeDesktop)
            .shown(GAME_CATEGORY, "The game's window: where the bot reads pixels from", Param.Visibility.PUBLIC)
            .because("This is where the bot reads pixels from. Choose it in 🎯 Capture Source, in the Runner or in"
                    + " Project ▸ Parameters.");

    /**
     * The game VM the game runs in when ⚙ Bot Settings runs it in one — {@code public static String vm}, by name;
     * blank for the one VM there is. Declared only while it means something ({@link #vmApplies}): the settings run
     * the game in a VM, on a system that has game VMs. So the bot's {@code main} never names it; the session reads
     * it by its mark.
     */
    public static final ManagedParam<String> VM = ManagedValue.param(SdkValue.Id.VM)
            .in(HOLDER)
            .startsAs(SdkValues::theOneVm)
            .shown(GAME_CATEGORY, "The game VM the game runs in; blank for the one VM there is",
                    Param.Visibility.EDITOR)
            .when(SdkValues::vmApplies)
            .because(GAME_PARAM);

    private static boolean claimed;

    private SdkValues() {}

    /** {@link #FLOW}'s type and first value: no flow until the runner picks one of {@code Flows}. */
    private static Flow noFlow() {
        return Flow.NONE;
    }

    /** {@link #DO_SETUP}'s: a new bot sets its game up on its first run. */
    private static boolean setUpFirst() {
        return true;
    }

    /** {@link #SKIP}'s: every activity runs. */
    private static Set<Class<? extends Outcome>> skipNothing() {
        return Set.of();
    }

    /** {@link #LAUNCH_TARGETS}'s: no launcher until the game is picked. */
    private static List<Launcher> noLaunchTargets() {
        return List.of();
    }

    /** {@link #LAUNCH_TARGET}'s: no pick, so the first launcher this computer can launch. */
    private static LaunchChoice firstAvailable() {
        return LaunchChoice.of("");
    }

    /** {@link #CAPTURE}'s: the whole desktop until the game's window is picked. */
    private static CaptureSource wholeDesktop() {
        return CaptureSource.desktop();
    }

    /** {@link #VM}'s: the one game VM there is. */
    private static String theOneVm() {
        return "";
    }

    /**
     * Whether {@link #VM} applies: the bot's settings run the game in a VM, and this computer's system has game VMs —
     * Windows and a Mac; Linux runs a game on a private display instead.
     */
    static boolean vmApplies(ParamScope scope) {
        Os os = Os.current();
        if (os != Os.WINDOWS && os != Os.MAC) return false;
        return scope.value(SETTINGS).map(settings -> settings.where() == BotSettings.Where.VM).orElse(false);
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
        // Where the flow editor's cards sit: the editor's, and nothing at runtime reads it. Claimed so a run
        // does not report it as a value nobody takes.
        ManagedValues.claim(FLOW_LAYOUT, layout -> { });
    }
}
