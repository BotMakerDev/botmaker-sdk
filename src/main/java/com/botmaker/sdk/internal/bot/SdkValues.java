package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.managed.ManagedValues;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.FlowLayout;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Spot;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.flow.InstalledRun;
import com.botmaker.sdk.internal.vision.BotScreen;
import com.botmaker.sdk.internal.vision.TemplateNames;

import java.util.List;

/**
 * The SDK's managed values, each declared once, and what a run does with each one.
 *
 * <p>Thirteen declarations, one per {@link SdkValue.Id} constant, which a bot writes as {@code @SdkValue(…)}: the
 * plugin lists {@link #ALL} as its {@code managedValues()}, its windows open each one through a toolkit
 * {@code ManagedHandle}, and {@link #claim()} hands each to the runtime typed. {@code FLOWS} are the activity
 * flows, {@code RUN} the one that runs by default with the game's home and setup, {@code FLOW_LAYOUT} where the
 * flow editor draws each card (a run ignores it), {@code CAPTURE} where
 * pixels are read from, {@code SETTINGS} how it clicks and looks, {@code SCREEN} the size its pictures were cut
 * at, and {@code PICTURES}, {@code ACTIVITIES}, {@code SPOTS}, {@code ZONES} and {@code DIGITS} the classes of
 * constants the bot grows. {@code POINTS} and {@code REGIONS} are retired: read only for {@link SdkConversions}.
 *
 * <p><b>It is {@code internal} because a bot never names it</b>, and the plugin half may. It names contract
 * and {@code api} types only, so it is safe in a bot. {@link com.botmaker.sdk.api.bot.Bot#runFlow} calls
 * {@link #claim()} before installing, which is the only ordering anyone has to get right.
 */
public final class SdkValues {

    /** The class the method-shaped values live in: {@code plugins/sdk/Sdk.java}, named by the bot's {@code main}. */
    public static final String HOLDER = "Sdk";

    /**
     * The flows — {@code static final Flow MAIN = Flow.of(…)}, one per way of playing. 🔀 Activity Flow draws one,
     * keeping its activities, wires and layout in step.
     */
    public static final ManagedValue<Flow> FLOWS = ManagedValue.openSet(SdkValue.Id.FLOWS)
            .of(Flow.class)
            .in("Flows")
            .because("These are the bot's activity flows. Draw them in 🔀 Activity Flow, which keeps the"
                    + " activities, the wires and the layout in step.");

    /** Which flow runs when nobody picks, and the game's home and setup — {@code Run.of(Flows.MAIN).home(…)}. */
    public static final ManagedValue<Run> RUN = ManagedValue.method(SdkValue.Id.RUN)
            .in(HOLDER)
            .holds(Run.class, Run.NONE)
            .because("This says which flow runs when nobody picks one, and the game's home and setup. Change"
                    + " them in 🔀 Activity Flow.");

    public static final ManagedValue<FlowLayout> FLOW_LAYOUT = ManagedValue.method(SdkValue.Id.FLOW_LAYOUT)
            .in(HOLDER)
            .holds(FlowLayout.class, FlowLayout.NONE)
            .because("These are where the Activity Flow's cards sit. Drag them in 🔀 Activity Flow, which keeps"
                    + " them in step with the activities' names.");

    public static final ManagedValue<CaptureSource> CAPTURE = ManagedValue.method(SdkValue.Id.CAPTURE)
            .in(HOLDER)
            .holds(CaptureSource.class, CaptureSource.desktop())
            .because("This is where the bot reads pixels from. Choose it in 🎯 Capture Source.");

    public static final ManagedValue<BotSettings> SETTINGS = ManagedValue.method(SdkValue.Id.SETTINGS)
            .in(HOLDER)
            .holds(BotSettings.class, BotSettings.DEFAULTS)
            .because("These are the bot's settings — delays, confidence, input and its private display. Change"
                    + " them in ⚙ Bot Settings.");

    /**
     * The picture constants — {@code static final ImageTemplate COLLECT = new ImageTemplate(…)}. 🖼 Manage
     * Pictures renames the file, the constant and every use of it together; the canvas can only rename the one
     * it is looking at, which would leave the bot calling a name that is gone.
     */
    public static final ManagedValue<ImageTemplate> PICTURES = ManagedValue.openSet(SdkValue.Id.PICTURES)
            .of(ImageTemplate.class)
            .in(TemplateNames.CLASS_NAME)
            .because("Picture constants are managed in 🖼 Manage Pictures, which renames the picture and every"
                    + " use of it together.");

    /**
     * The activities, each an enum of its outcomes nested in one class —
     * {@code final class Activities { enum Collect implements Outcome { NEXT, NOTHING_LEFT } }} (2026-10-10).
     * 🔀 Activity Flow adds, renames and removes an enum with its card and a constant with its exit, together with
     * every step, wire, body and {@code ActivitySwitch} call naming them.
     */
    public static final ManagedValue<Outcome> ACTIVITIES = ManagedValue.openSet(SdkValue.Id.ACTIVITIES)
            .ofEnums(Outcome.class, BotConstants::outcome)
            .groupedAs(Activity.class, BotConstants::activity)
            .in("Activities")
            .because("Activities and their outcomes are managed in 🔀 Activity Flow, which renames the card or the"
                    + " exit and every use of it together.");

    /**
     * Retired 2026-10-11: the named spots as desktop pixels, {@code static final Point CLAIM = new Point(412, 230)}.
     * Read only so {@link SdkConversions#PLACES} carries them into {@link #SPOTS}.
     */
    public static final ManagedValue<Point> POINTS = ManagedValue.openSet(SdkValue.Id.POINTS)
            .of(Point.class)
            .in("Points")
            .because("Points.java holds the bot's named spots. Ask Claude to save_point a spot again, under its"
                    + " name, to move it.")
            .retired();

    /**
     * Retired 2026-10-11: the named areas as source pixels, {@code static final Rect BAG = new Rect(10, 20, 200,
     * 120)}. Read only so {@link SdkConversions#PLACES} carries them into {@link #ZONES}.
     */
    public static final ManagedValue<Rect> REGIONS = ManagedValue.openSet(SdkValue.Id.REGIONS)
            .of(Rect.class)
            .in("Regions")
            .because("Regions.java holds the bot's named areas. Ask Claude to save_region an area again, under"
                    + " its name, to move it.")
            .retired();

    /**
     * The named spots — {@code static final Spot CLAIM = new Spot(412, 230)}, in the bot's pixels, which
     * {@code Mouse.click(Spot)} places on the game as it shows. The assistant's {@code save_point} adds one, or
     * moves one it names again.
     */
    public static final ManagedValue<Spot> SPOTS = ManagedValue.openSet(SdkValue.Id.SPOTS)
            .of(Spot.class)
            .in("Spots")
            .because("Spots.java holds the bot's named spots. Ask Claude to save_point a spot again, under its"
                    + " name, to move it.");

    /**
     * The named areas — {@code static final Zone BAG = new Zone(10, 20, 200, 120)}, in the bot's pixels, which
     * {@code source.region(Zone)} places on the game as it shows. The assistant's {@code save_region} adds one, or
     * moves one it names again.
     */
    public static final ManagedValue<Zone> ZONES = ManagedValue.openSet(SdkValue.Id.ZONES)
            .of(Zone.class)
            .in("Zones")
            .because("Zones.java holds the bot's named areas. Ask Claude to save_region an area again, under its"
                    + " name, to move it.");

    /**
     * The game fonts' digits — {@code static final DigitSet GOLD = new DigitSet("src/main/resources/digits/gold")},
     * a folder of {@code 0.png} to {@code 9.png} that {@code Text.readDigits} reads a number with. The assistant's
     * {@code save_digits} cuts the digits of a number on screen into it and adds the constant.
     */
    public static final ManagedValue<DigitSet> DIGITS = ManagedValue.openSet(SdkValue.Id.DIGITS)
            .of(DigitSet.class)
            .in("Digits")
            .because("Digits.java holds the game fonts' digit pictures. Ask Claude to save_digits on a number the"
                    + " game shows to add or fix one.");

    /**
     * The bot's screen size — {@code new Size(1280, 720)}: the frame its pictures were cut from, which a picture
     * names apart only when it was cut from another ({@code .in(Size)}). Written by the first picture cut while it
     * is {@code 0×0}, which is no size: pictures are matched as they are, and a private display is made at the
     * session default.
     */
    public static final ManagedValue<Size> SCREEN = ManagedValue.method(SdkValue.Id.SCREEN)
            .in(HOLDER)
            .holds(Size.class, new Size(0, 0))
            .because("This is the size the bot's pictures were cut at. The first picture cut sets it; a private"
                    + " display is made at it and a game window is resized to it when the bot starts.");

    /** All thirteen, in the order the plugin declares them, the two retired ones included. */
    public static final List<ManagedValue<?>> ALL = List.of(FLOWS, RUN, FLOW_LAYOUT, CAPTURE, SETTINGS, PICTURES,
            ACTIVITIES, POINTS, REGIONS, SPOTS, ZONES, DIGITS, SCREEN);

    private static boolean claimed;

    private SdkValues() {}

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
        ManagedValues.claim(CAPTURE, Source::set);
        ManagedValues.claim(SETTINGS, BotSettings::use);
        ManagedValues.claim(SCREEN, BotScreen::use);
        // Where the flow editor's cards sit: the editor's, and nothing at runtime reads it. Claimed so a run
        // does not report it as a value nobody takes.
        ManagedValues.claim(FLOW_LAYOUT, layout -> { });
    }
}
