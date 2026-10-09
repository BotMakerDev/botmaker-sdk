package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.DeclaredCallType;
import com.botmaker.plugin.api.value.DeclaredType;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.input.KeySequence;
import com.botmaker.sdk.api.input.MouseButton;
import com.botmaker.sdk.api.text.OcrOptions;
import com.botmaker.sdk.api.text.Text;
import com.botmaker.sdk.api.text.TextMatch;
import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.api.vision.Vision;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.editors.ActivityEditors;
import com.botmaker.sdk.plugin.editors.CaptureSourceEditors;
import com.botmaker.sdk.plugin.editors.GeometryEditors;
import com.botmaker.sdk.plugin.editors.InputEditors;
import com.botmaker.sdk.plugin.editors.OcrEditors;
import com.botmaker.sdk.plugin.editors.PrecisionEditors;
import com.botmaker.sdk.plugin.editors.ResultEditors;
import com.botmaker.sdk.plugin.editors.TemplateEditors;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * The types the SDK declares ({@link #ALL}) and the calls inside its values that are not types of their own
 * ({@link #PARTS}): a picture and a group of them, how exactly to match one, how to read text, three geometry shapes, three
 * enums, a key combination and a key sequence, the capture source, and the vision results a bot holds but
 * nobody edits.
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
public final class SdkTypes {

    private SdkTypes() {
    }

    private static ImageTemplate placeholder() {
        return new ImageTemplate(TemplateNames.pathFor(TemplateNames.DEFAULT_TEMPLATE_NAME));
    }

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
            .writtenAs(ImageTemplate::new, ImageTemplate::filePath);

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
     * {@link #OCR_CHAINS}.
     */
    public static final DeclaredCallType<OcrOptions> OCR_OPTIONS = PluginType.value(OcrOptions.class)
            .fresh(() -> Text.DEFAULT_OPTIONS.withCharWhitelist(""))
            .editor(() -> OcrEditors::options)
            .writtenAsRecord();

    /**
     * A point on the screen. Its editor is handed this declaration, which is where the arity, the parts and
     * the way back from a row of numbers to a value come from — so "a Point has two numbers" is stated once,
     * by the record. The same holds for {@link #RECT} and {@link #SIZE}.
     */
    public static final DeclaredCallType<Point> POINT = PluginType.value(Point.class)
            .fresh(() -> new Point(0, 0))
            .editor(() -> ctx -> GeometryEditors.point(ctx, SdkTypes.POINT))
            .writtenAsRecord();

    /** A rectangle on the screen: where it is, then how big it is. */
    public static final DeclaredCallType<Rect> RECT = PluginType.value(Rect.class)
            .fresh(() -> new Rect(0, 0, 0, 0))
            .editor(() -> ctx -> GeometryEditors.rect(ctx, SdkTypes.RECT))
            .writtenAsRecord();

    /** A width and a height, with no position. */
    public static final DeclaredCallType<Size> SIZE = PluginType.value(Size.class)
            .fresh(() -> new Size(0, 0))
            .editor(() -> ctx -> GeometryEditors.size(ctx, SdkTypes.SIZE))
            .writtenAsRecord();

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
    public static final DeclaredCall<KeySequence.Step> STEP = ComponentType.part(KeySequence.Step.class)
            .writtenAs(KeySequence::step, KeySequence.Step::combo, KeySequence.Step::after);

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
     * One activity of the flow: the bot's {@code Activities.COLLECT}, which an editor holds by name
     * ({@link BotConstants#activity}) and the host writes as that constant. A fresh one is no activity; the
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
     * What an activity reports: the bot's {@code Outcomes.WON}, held and written as {@link #ACTIVITY} is, and
     * {@code Outcome.NEXT}/{@code Outcome.DISABLED} for the two every activity has. A fresh one is
     * {@code NEXT}, "nothing special to report".
     */
    public static final DeclaredCallType<Outcome> OUTCOME = PluginType.value(Outcome.class)
            .fresh(() -> Outcome.NEXT)
            .editor(() -> ActivityEditors::outcome)
            .writtenAs(BotConstants::outcome, Outcome::name)
            .constants(Outcome.NEXT, Outcome.DISABLED);

    /**
     * Types a bot author may <b>hold</b> but nobody edits, whose fresh form is a call the bot re-evaluates.
     *
     * <p>{@code filledBy} names the method the host writes a call to: {@code Vision.lastMatch()} means <em>the
     * match the bot found a moment ago</em>, and freezing it into a {@code MatchResult} value would change the
     * declaration into a fabricated miss. Calling it to obtain one is worse still — it would run the vision
     * stack inside {@code botmaker plugin validate}.
     *
     * <p>The editor is a pill saying in plain words what the bot fills in, with the Java in its tooltip
     * ({@link ResultEditors}). That is the honest control: there is nothing here anyone configures.
     */
    public static final PluginType<MatchResult> MATCH_RESULT = PluginType.value(MatchResult.class)
            .filledBy(Vision::lastMatch)
            .shownAs(() -> ctx -> ResultEditors.pill(ctx, MatchResult.class));

    /** The matches of a picture search, starting as none. See {@link #MATCH_RESULT}. */
    public static final PluginType<Matches> MATCHES = PluginType.value(Matches.class)
            .filledBy(Matches::none)
            .shownAs(() -> ctx -> ResultEditors.pill(ctx, Matches.class));

    /** The last colour match. See {@link #MATCH_RESULT}. */
    public static final PluginType<ColorMatch> COLOR_MATCH = PluginType.value(ColorMatch.class)
            .filledBy(Vision::lastColorMatch)
            .shownAs(() -> ctx -> ResultEditors.pill(ctx, ColorMatch.class));

    /** The last text match. See {@link #MATCH_RESULT}. */
    public static final PluginType<TextMatch> TEXT_MATCH = PluginType.value(TextMatch.class)
            .filledBy(Text::lastMatch)
            .shownAs(() -> ctx -> ResultEditors.pill(ctx, TextMatch.class));

    /**
     * Every type, in the order a menu should offer them: the vision types, the geometry ones, the input
     * types, the capture source and the picture group, then the ones a bot holds but nobody edits. A host
     * offers plugin-basics' types before them, which puts the literals a bot mostly counts and labels with at
     * the top of the list.
     */
    public static final List<PluginType<?>> ALL = List.of(
            IMAGE_TEMPLATE, PRECISION, OCR_OPTIONS,
            POINT, RECT, SIZE,
            KEY, MOUSE_BUTTON, COMBO, KEY_SEQUENCE,
            CAPTURE_SOURCE, IMAGE_TEMPLATE_GROUP,
            ACTIVITY, OUTCOME,
            MATCH_RESULT, MATCHES, COLOR_MATCH, TEXT_MATCH);

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
     * the value it builds. Read and never written, as {@link #PRECISION_WITHERS} are: {@code defaults()} loses
     * every part it is not handed, and the withers answer the value itself as their receiver, so an edited
     * value is written as {@link #OCR_OPTIONS} writes it. {@code withLanguages(OcrLanguage...)} is not among
     * them; its {@code String} twin is.
     */
    public static final List<DeclaredCall<OcrOptions>> OCR_CHAINS = List.of(
            ComponentType.part(OcrOptions.class).writtenAs(OcrOptions::defaults),
            ComponentType.part(OcrOptions.class).writtenAs(OcrOptions::withLanguages, o -> o, OcrOptions::languages),
            ComponentType.part(OcrOptions.class).writtenAs(OcrOptions::withPageSegMode, o -> o, OcrOptions::pageSegMode),
            ComponentType.part(OcrOptions.class)
                    .writtenAs(OcrOptions::withOcrEngineMode, o -> o, OcrOptions::ocrEngineMode),
            ComponentType.part(OcrOptions.class).writtenAs(OcrOptions::withGrayscale, o -> o, OcrOptions::grayscale),
            ComponentType.part(OcrOptions.class).writtenAs(OcrOptions::withUpscale, o -> o, OcrOptions::upscale),
            ComponentType.part(OcrOptions.class).writtenAs(OcrOptions::withBinarize, o -> o, OcrOptions::binarize),
            ComponentType.part(OcrOptions.class).writtenAs(OcrOptions::withInvert, o -> o, OcrOptions::invert),
            ComponentType.part(OcrOptions.class)
                    .writtenAs(OcrOptions::withCharWhitelist, o -> o, OcrOptions::charWhitelist),
            ComponentType.part(OcrOptions.class).writtenAs(OcrOptions::withLevel, o -> o, OcrOptions::level));

    /**
     * The calls inside this plugin's values that are not types of their own: a flow's and a layout's shapes,
     * the calls a capture source and the bot settings are written as, the precision and OCR withers, a held
     * combo and a key sequence's step. None is picked on its own; the host reads each back so an editor is handed a
     * value rather than a string.
     */
    public static final List<ComponentType<?>> PARTS = Stream.of(
                    FlowTypes.ALL, CaptureTypes.ALL, SettingsTypes.ALL, PRECISION_WITHERS, OCR_CHAINS,
                    List.of(COMBO_HELD, STEP))
            .<ComponentType<?>>flatMap(List::stream).toList();
}
