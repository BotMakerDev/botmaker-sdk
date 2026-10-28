package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.TypeHolder;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.DeclaredCallType;
import com.botmaker.plugin.api.value.DeclaredType;
import com.botmaker.plugin.api.value.Held;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.input.KeySequence;
import com.botmaker.sdk.api.input.MouseButton;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.text.OcrOptions;
import com.botmaker.sdk.api.text.Text;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.api.vision.PictureTag;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.editors.ActivityEditors;
import com.botmaker.sdk.plugin.editors.CaptureSourceEditors;
import com.botmaker.sdk.plugin.editors.GeometryEditors;
import com.botmaker.sdk.plugin.editors.InputEditors;
import com.botmaker.sdk.plugin.editors.LauncherEditors;
import com.botmaker.sdk.plugin.editors.OcrEditors;
import com.botmaker.sdk.plugin.editors.PrecisionEditors;
import com.botmaker.sdk.plugin.editors.TemplateEditors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The types the SDK declares and the calls inside its values that are not types of their own: a picture and a
 * group of them, how exactly to match one, how to read text, three geometry shapes, three enums, a key
 * combination and a key sequence, and the capture source. The vision results a bot holds but nobody edits are
 * not here: each is its {@code api} method marked {@code @Fills} ({@code Vision.lastMatch()}), and the host
 * declares them.
 *
 * <p>{@code SdkPlugin} names this class and the other holders ({@code .types(SdkTypes.class, …)}); the host
 * takes what {@link #types()} and {@link #components()} list, in their order. So the types are listed in the
 * order a menu offers them: the vision types, the geometry ones, the input types, the capture source and the
 * picture group. A host offers plugin-basics' types before them. Two shapes of one class are tried in list
 * order too.
 *
 * <p>Each type is one {@link PluginType#value} declaration, whose steps ask in order for what a fresh one is,
 * how a person edits it and how its Java is written. A factory is a method reference and each part an
 * accessor, so there is no method name to misspell and no build to write: the host invokes the factory on
 * the parts. A record's call is its canonical constructor, so the geometry types and {@link Precision} state
 * nothing about their parts at all.
 *
 * <p>The JDK's own values — text, a flag, numbers, a colour, a date, a duration — are plugin-basics'
 * ({@code com.botmaker.plugin.basics.values.BasicsTypes}). What is here is what a bot's own API names.
 *
 * <h2>The identity is the class, so a rename in {@code api.*} breaks this file</h2>
 *
 * <p>Every one of these names a real {@link Class}, which is the whole of what the host indexes on: a
 * project's file says {@code com.botmaker.sdk.api.geometry.Point} because that is what the field is declared
 * as. A renamed factory or accessor is a compile error here rather than a call written into a bot's file.
 *
 * @see FlowTypes the shapes a {@code Flow} and its layout are written as, which are parts and never picked
 * @see CaptureTypes the calls a {@code CaptureSource} is written as
 * @see SettingsTypes the calls a {@code BotSettings} is written as
 */
public final class SdkTypes implements TypeHolder {

    private SdkTypes() {
    }

    @Override
    public List<PluginType<?>> types() {
        return List.of(IMAGE_TEMPLATE, PRECISION, OCR_OPTIONS, DIGIT_SET, WORDS, POINT, RECT, SIZE, ZONE, KEY,
                MOUSE_BUTTON, COMBO, KEY_SEQUENCE, CAPTURE_SOURCE, IMAGE_TEMPLATE_GROUP, LAUNCHER, LAUNCH_CHOICE,
                ACTIVITY, OUTCOME);
    }

    @Override
    public List<ComponentType<?>> components() {
        List<ComponentType<?>> all = new ArrayList<>(List.of(PICTURE_TAG, COMBO_HELD, STEP));
        all.addAll(PRECISION_WITHERS);
        all.add(OCR_CHAIN);
        return all;
    }

    private static ImageTemplate placeholder() {
        return new ImageTemplate(TemplateNames.pathFor(TemplateNames.DEFAULT_TEMPLATE_NAME));
    }

    /**
     * A picture's activities as the host holds them: each enum's class literal crosses as its {@link Activity}
     * ({@code ManagedValue.groupedAs}), which {@link ImageTemplate#inActivities} takes.
     */
    private static final Held<ImageTemplate, List<Activity>> PICTURE_ACTIVITIES =
            Held.list(Activity.class, ImageTemplate::activities);

    /**
     * A named picture, {@code new ImageTemplate("images/ore.png")}.
     *
     * <p><b>The one type whose fresh value is a choice rather than a fallback.</b> A new picture value
     * points at the placeholder every project ships, because an empty chip is a value the bot cannot run
     * on and no amount of reasoning about an empty {@code ImageTemplate} discovers that.
     */
    public static final DeclaredCallType<ImageTemplate> IMAGE_TEMPLATE = PluginType.value(ImageTemplate.class)
            .fresh(SdkTypes::placeholder)
            .editor(() -> TemplateEditors::template)
            .preview(() -> TemplateEditors::preview)
            .writtenAs(ImageTemplate::new, ImageTemplate::filePath)
            .with(ImageTemplate::at, ImageTemplate::place)
            .with(ImageTemplate::in, ImageTemplate::frame)
            .with(ImageTemplate::tags, ImageTemplate::tags)
            // The part's type spelled out: a held link names it only in the wither, an overloaded method
            // reference javac cannot infer from (the tags link above infers it from its accessor).
            .<List<Class<? extends Outcome>>, List<Activity>>with(ImageTemplate::activities, PICTURE_ACTIVITIES,
                    ImageTemplate::inActivities);

    /**
     * A tag spelled out, {@code PictureTag.named("UI")}: what a {@code Tags} constant holds. A tag inside a picture is
     * a reference to that constant ({@link BotConstants#tag}) and is written only as the constant.
     */
    public static final DeclaredCall<PictureTag> PICTURE_TAG = ComponentType.part(PictureTag.class)
            .<String, PictureTag>writtenAs(PictureTag::named, PictureTag::name)
            .when(tag -> BotConstants.tagName(tag) == null);

    /**
     * How exact a pixel match has to be.
     *
     * <p>{@code Precision.DEFAULT} rather than {@code new Precision(0, 0, 0)}: a zero tolerance matches
     * nothing and a zero minimum area matches everything, so neither end of the range is a sensible start.
     * Built through the record's own canonical constructor, which is where the clamping to what a match can
     * actually use lives — so a hand-edited number is clamped exactly as a picked one is.
     */
    public static final DeclaredCallType<Precision> PRECISION = PluginType.value(Precision.class)
            .fresh(() -> Precision.DEFAULT)
            .editor(() -> PrecisionEditors::precision)
            .writtenAsRecord();

    /**
     * How {@code Text} reads the screen: languages, lines or words, a character whitelist, the upscale and the
     * clean-up. A fresh one is what {@code Text} reads with when handed none, except that "any character" is
     * {@code ""}: the value is written as the record's constructor, and the host writes no {@code null} part.
     * A chain a person writes — {@code OcrOptions.defaults().withUpscale(3.0)} — is read through
     * {@link #OCR_CHAIN}.
     */
    public static final DeclaredCallType<OcrOptions> OCR_OPTIONS = PluginType.value(OcrOptions.class)
            .fresh(() -> Text.DEFAULT_OPTIONS.withCharWhitelist(""))
            .editor(() -> OcrEditors::options)
            .writtenAsRecord();

    /**
     * A game font's digits as pictures: {@code new DigitSet("src/main/resources/digits/gold")}, and {@code .in(…)}
     * only when they were cut from a frame of another size than the bot's (2026-10-11).
     */
    public static final DeclaredCallType<DigitSet> DIGIT_SET = PluginType.value(DigitSet.class)
            .fresh(() -> new DigitSet("src/main/resources/digits/digits"))
            .editor(() -> OcrEditors::digits)
            .writtenAs(DigitSet::new, DigitSet::folder)
            .with(DigitSet::in, DigitSet::frame);

    /** A text of the bot's, {@code Words.of("Dragons")}: what a text field of the map types (2026-10-22). */
    public static final DeclaredCallType<Words> WORDS = PluginType.value(Words.class)
            .fresh(() -> Words.of(""))
            .editor(() -> OcrEditors::words)
            .writtenAs(Words::of, Words::text);

    /**
     * A point on the screen. Its editor asks the host for this declaration ({@code ValueContext.declared}),
     * which is where the arity, the parts and the way back from a row of numbers to a value come from — so "a
     * Point has two numbers" is stated once, by the record. The same holds for {@link #RECT} and {@link #SIZE}.
     */
    public static final DeclaredCallType<Point> POINT = PluginType.value(Point.class)
            .fresh(() -> new Point(0, 0))
            .editor(() -> GeometryEditors::point)
            .writtenAsRecord();

    /** A rectangle on the screen: where it is, then how big it is. */
    public static final DeclaredCallType<Rect> RECT = PluginType.value(Rect.class)
            .fresh(() -> new Rect(0, 0, 0, 0))
            .editor(() -> GeometryEditors::rect)
            .writtenAsRecord();

    /** A width and a height, with no position. */
    public static final DeclaredCallType<Size> SIZE = PluginType.value(Size.class)
            .fresh(() -> new Size(0, 0))
            .editor(() -> GeometryEditors::size)
            .writtenAsRecord();

    /**
     * A named area of the game, in the bot's pixels: {@code new Zone(10, 20, 200, 120)}, and
     * {@code .anchored(Anchor.…)} only when it stays with another part of the screen than its centre (2026-10-11).
     */
    public static final DeclaredCallType<Zone> ZONE = PluginType.value(Zone.class)
            .fresh(() -> new Zone(0, 0, 0, 0))
            .editor(() -> GeometryEditors::zone)
            .writtenAs(Zone::new, Zone::x, Zone::y, Zone::width, Zone::height)
            .with(Zone::anchored, Zone::anchor);

    /** A key on the keyboard. An enum's Java is its constant, so it needs no call. */
    public static final PluginType<Key> KEY = PluginType.value(Key.class)
            .firstConstant()
            .editor(() -> InputEditors::key)
            .writtenAsConstant();

    /** A mouse button. */
    public static final PluginType<MouseButton> MOUSE_BUTTON = PluginType.value(MouseButton.class)
            .firstConstant()
            .editor(() -> InputEditors::mouseButton)
            .writtenAsConstant();

    /**
     * Keys pressed together, written {@code Combo.of(Key.CTRL, Key.S)}: a run of keys, as
     * {@link #IMAGE_TEMPLATE_GROUP} is a run of pictures. A fresh one is Ctrl+S, the combination a recorded
     * bot most often starts with. A held combo is written through {@link #COMBO_HELD}.
     */
    public static final DeclaredCallType<Combo> COMBO = PluginType.value(Combo.class)
            .fresh(() -> Combo.of(Key.CTRL, Key.S))
            .editor(() -> InputEditors::combo)
            .writtenAsEach(Combo::of, Combo::keys);

    /**
     * {@code combo.held(d)}: a held combo, written on the combo without its hold. {@link #COMBO}'s factory
     * has no hold, so a held combo does not survive it, and that is when the host writes this chain instead.
     */
    public static final DeclaredCall<Combo> COMBO_HELD = ComponentType.part(Combo.class)
            .writtenAs(Combo::held, combo -> combo.held(Duration.ZERO), Combo::hold);

    /** {@code KeySequence.step(combo, after)}: one step of a sequence, never declared or picked on its own. */
    public static final DeclaredCall<KeySequence.Press> STEP = ComponentType.part(KeySequence.Press.class)
            .writtenAs(KeySequence::step, KeySequence.Press::combo, KeySequence.Press::after);

    /**
     * Combos one after another, written {@code KeySequence.of(step(…), step(…))}: a run of {@link #STEP}s. A
     * fresh one is select all, wait 100 ms, copy — two steps, so the editor opens on what a sequence is for.
     */
    public static final DeclaredCallType<KeySequence> KEY_SEQUENCE = PluginType.value(KeySequence.class)
            .fresh(() -> KeySequence.of(KeySequence.step(Combo.of(Key.CTRL, Key.A), Duration.ofMillis(100)),
                    KeySequence.step(Combo.of(Key.CTRL, Key.C), Duration.ZERO)))
            .editor(() -> InputEditors::sequence)
            .writtenAsEach(KeySequence::of, KeySequence::steps);

    /**
     * Where a bot reads pixels from. An interface, so it is written as whichever of {@link CaptureTypes}' calls
     * builds it. A fresh one is the ambient source, which keeps following the project's source when that
     * changes later; a concrete source would freeze the declaration into what was true when it was made.
     *
     * <p>Drawn as a pill opening the source tiles ({@link CaptureSourceEditors}), the same picker as the
     * toolbar's Capture Source.
     */
    public static final DeclaredType<CaptureSource> CAPTURE_SOURCE = PluginType.value(CaptureSource.class)
            .fresh(CurrentSource::new)
            .editor(() -> CaptureSourceEditors::source)
            .preview(() -> CaptureSourceEditors::preview)
            .writtenAsParts();

    /**
     * Several pictures, written {@code ImageTemplateGroup.of(a, b)}. A fresh one holds the placeholder
     * picture, for the reason a fresh {@code ImageTemplate} is it: an empty group is a value the bot cannot
     * match anything with. Drawn by the same picture row as a run of picture arguments.
     */
    public static final DeclaredCallType<ImageTemplateGroup> IMAGE_TEMPLATE_GROUP =
            PluginType.value(ImageTemplateGroup.class)
                    .fresh(() -> ImageTemplateGroup.of(placeholder()))
                    .editor(() -> TemplateEditors::group)
                    .writtenAsEach(ImageTemplateGroup::of, ImageTemplateGroup::templates);

    /**
     * One launcher the bot starts its game from, {@code Launcher.of("heroic:43d4…")}: drawn as a pill naming the
     * game that opens 🎮 Game ({@link LauncherEditors}). A fresh one names no game.
     */
    public static final DeclaredCallType<Launcher> LAUNCHER = PluginType.value(Launcher.class)
            .fresh(() -> Launcher.of(""))
            .editor(() -> LauncherEditors::launcher)
            .preview(() -> LauncherEditors::preview)
            .writtenAs(Launcher::of, Launcher::spec);

    /**
     * Which of the bot's launchers a run starts, {@code LaunchChoice.of("steam:570")}: drawn as a pill offering only
     * the author's games this computer can start ({@link LauncherEditors#choice}). A fresh one picks none, so a
     * run starts the first available.
     */
    public static final DeclaredCallType<LaunchChoice> LAUNCH_CHOICE = PluginType.value(LaunchChoice.class)
            .fresh(() -> LaunchChoice.of(""))
            .editor(() -> LauncherEditors::choice)
            .preview(() -> LauncherEditors::choicePreview)
            .writtenAs(LaunchChoice::of, LaunchChoice::spec);

    /**
     * One activity of the flow: the bot's {@code Activities.Collect} enum, which an editor holds by name
     * ({@link BotConstants#activity}) and the host writes as its class literal. A fresh one is no activity; the
     * picker offers the flow's.
     *
     * <p>The call is what the host compares a value with a constant through, never what it writes: every value
     * the editors hand back is a constant's, declared first ({@code FlowConstants}).
     */
    public static final DeclaredCallType<Activity> ACTIVITY = PluginType.value(Activity.class)
            .fresh(() -> Activity.NONE)
            .editor(() -> ActivityEditors::activity)
            .writtenAs(BotConstants::activity, Activity::name)
            .constants(Activity.NONE);

    /**
     * What an activity reports: a constant of its enum, {@code Activities.Collect.NOTHING_LEFT}, held by its two
     * names ({@link BotConstants#outcome}) and written as that constant. A fresh one is
     * {@link Outcome#NONE}, written so; the picker offers the activity's.
     */
    public static final DeclaredCallType<Outcome> OUTCOME = PluginType.value(Outcome.class)
            .fresh(() -> Outcome.NONE)
            .editor(() -> ActivityEditors::outcome)
            .writtenAs(BotConstants::outcome, outcome -> outcome.activity().name(), Outcome::name)
            .constants(Outcome.NONE);

    /**
     * {@code precision.tolerance(d)}, {@code .minArea(n)} and {@code .minCount(n)}: the chains a person
     * writes on a named precision ({@code Precision.TIGHT.minArea(400)}), read as the value they build.
     * Instance factories, so the host never writes them: an edited precision is written as
     * {@link #PRECISION} writes it.
     */
    public static final List<DeclaredCall<Precision>> PRECISION_WITHERS = List.of(
            ComponentType.part(Precision.class).writtenAs(Precision::tolerance, p -> p, Precision::deltaE),
            ComponentType.part(Precision.class).writtenAs(Precision::minArea, p -> p, Precision::minArea),
            ComponentType.part(Precision.class).writtenAs(Precision::minCount, p -> p, Precision::minCount));

    /**
     * {@code OcrOptions.defaults()} and its withers, the chain a person writes
     * ({@code OcrOptions.defaults().withLevel(TextResult.Level.LINE).withCharWhitelist("0123456789")}), read as
     * the value it builds, its links in any order. Never what an edited value is written as: {@link #OCR_OPTIONS}
     * is declared first and its record constructor writes every value. {@code withLanguages(OcrLanguage...)} is
     * not among the links; its {@code String} twin is.
     */
    public static final DeclaredCall<OcrOptions> OCR_CHAIN = ComponentType.part(OcrOptions.class)
            .writtenAs(OcrOptions::defaults)
            .with(OcrOptions::withLanguages, OcrOptions::languages)
            .with(OcrOptions::withPageSegMode, OcrOptions::pageSegMode)
            .with(OcrOptions::withOcrEngineMode, OcrOptions::ocrEngineMode)
            .with(OcrOptions::withGrayscale, OcrOptions::grayscale)
            .with(OcrOptions::withUpscale, OcrOptions::upscale)
            .with(OcrOptions::withBinarize, OcrOptions::binarize)
            .with(OcrOptions::withInvert, OcrOptions::invert)
            .with(OcrOptions::withCharWhitelist, OcrOptions::charWhitelist)
            .with(OcrOptions::withLevel, OcrOptions::level);
}
