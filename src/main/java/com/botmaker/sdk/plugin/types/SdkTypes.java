package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.plugin.toolkit.Types;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.KeySequence;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.api.vision.TextMatch;
import com.botmaker.sdk.api.vision.Vision;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.editors.GeometryEditors;
import com.botmaker.sdk.plugin.editors.InputEditors;
import com.botmaker.sdk.plugin.editors.PrecisionEditors;
import com.botmaker.sdk.plugin.editors.ResultEditors;
import com.botmaker.sdk.plugin.editors.TemplateEditors;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.botmaker.plugin.toolkit.Types.method;

/**
 * The sixteen types the SDK declares ({@link #ALL}): a picture and a group of them, how exactly to match
 * one, three geometry shapes, three enums, a key combination and a key sequence, the capture source, and four
 * vision results a bot holds but nobody edits.
 *
 * <p>Each type is declared once, as one {@link Types} expression: what it is, what a fresh one is, how a
 * person edits it, and — when its Java is a call — the call, as the same object. The host writes and reads
 * the Java. A record's call is its canonical constructor ({@link Types#record}), so the geometry types and
 * {@link Precision} state nothing about their parts at all.
 *
 * <p>The JDK's own values — text, a flag, numbers, a colour, a date, a duration — are plugin-basics'
 * ({@code com.botmaker.plugin.basics.values.BasicsTypes}). What is here is what a bot's own API names.
 *
 * <h2>The identity is the class, so a rename in {@code api.*} breaks this file</h2>
 *
 * <p>Every one of these names a real {@link Class}, which is the whole of what the host indexes on: a
 * project's file says {@code com.botmaker.sdk.api.geometry.Point} because that is what the field is declared
 * as. Every factory is looked up once, in a {@code static final} field, so a renamed method breaks this
 * plugin's class initialisation and its own tests rather than a bot's file.
 *
 * @see FlowTypes the five shapes a {@code Flow} is written as, which are composites and never picked
 * @see CaptureTypes the six calls a {@code CaptureSource} is written as
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
    public static final Types.DeclaredCall<ImageTemplate> IMAGE_TEMPLATE =
            Types.editable(ImageTemplate.class, SdkTypes::placeholder, () -> TemplateEditors::template)
                    .preview(() -> TemplateEditors::preview)
                    .writtenAs(Types.call(ImageTemplate.class, Types.constructor(ImageTemplate.class, String.class),
                            t -> List.of(t.filePath()), parts -> new ImageTemplate(Types.text(parts, 0))));

    /**
     * How exact a pixel match has to be.
     *
     * <p>{@code Precision.DEFAULT} rather than {@code new Precision(0, 0, 0)}: a zero tolerance matches
     * nothing and a zero minimum area matches everything, so neither end of the range is a sensible start.
     * Built through the record's own canonical constructor, which is where the clamping to what a match can
     * actually use lives — so a hand-edited number is clamped exactly as a picked one is.
     */
    public static final Types.DeclaredCall<Precision> PRECISION =
            Types.editable(Precision.class, () -> Precision.DEFAULT, () -> PrecisionEditors::precision)
                    .writtenAs(Types.record(Precision.class));

    /**
     * The three geometry calls. Each is handed to its {@code GeometryEditors} editor, which passes it to
     * {@code Editors.tuplePill} — where the arity, the components and the way back from a row of numbers to a
     * value all come from, so "a Rect has four numbers" is stated once, by the record.
     */
    private static final ComponentType<Point> POINT = Types.record(Point.class);
    private static final ComponentType<Rect> RECT = Types.record(Rect.class);
    private static final ComponentType<Size> SIZE = Types.record(Size.class);

    /** A point on the screen. */
    public static final Types.DeclaredCall<Point> POINT_TYPE =
            Types.editable(Point.class, () -> new Point(0, 0), () -> ctx -> GeometryEditors.point(ctx, POINT))
                    .writtenAs(POINT);

    /** A rectangle on the screen: where it is, then how big it is. */
    public static final Types.DeclaredCall<Rect> RECT_TYPE =
            Types.editable(Rect.class, () -> new Rect(0, 0, 0, 0), () -> ctx -> GeometryEditors.rect(ctx, RECT))
                    .writtenAs(RECT);

    /** A width and a height, with no position. */
    public static final Types.DeclaredCall<Size> SIZE_TYPE =
            Types.editable(Size.class, () -> new Size(0, 0), () -> ctx -> GeometryEditors.size(ctx, SIZE))
                    .writtenAs(SIZE);

    /** Which way something moves or faces. An enum's Java is its constant, so it needs no call. */
    public static final PluginType<Direction> DIRECTION = Types.enumType(Direction.class, () -> InputEditors::direction);

    /** A key on the keyboard. */
    public static final PluginType<Key> KEY = Types.enumType(Key.class, () -> InputEditors::key);

    /** A mouse button. */
    public static final PluginType<MouseButton> MOUSE_BUTTON =
            Types.enumType(MouseButton.class, () -> InputEditors::mouseButton);

    /**
     * Keys pressed together, written {@code Combo.of(Key.CTRL, Key.S)}: one declared part, repeated as varargs,
     * as {@link #IMAGE_TEMPLATE_GROUP} is. A fresh one is Ctrl+S, the combination a recorded bot most often
     * starts with.
     */
    public static final Types.DeclaredCall<Combo> COMBO =
            Types.editable(Combo.class, () -> Combo.of(Key.CTRL, Key.S), () -> InputEditors::combo)
                    .writtenAs(Types.call(Combo.class, method(Combo.class, "of", Key[].class),
                            c -> List.copyOf(c.keys()), parts -> {
                                List<Key> keys = Types.each(parts, Key.class);
                                return keys == null ? null : new Combo(keys);
                            }));

    /**
     * {@code combo.held(d)}: a held combo, written on the combo without its hold. {@link #COMBO}'s factory
     * has no hold, so a held combo does not survive it, and that is when the host writes this chain instead.
     */
    public static final ComponentType<Combo> COMBO_HELD = Types.call(Combo.class,
            method(Combo.class, "held", Duration.class),
            c -> List.of(c.held(Duration.ZERO), c.hold()),
            parts -> parts.size() == 2 && parts.get(0) instanceof Combo on && parts.get(1) instanceof Duration hold
                    && !hold.isNegative() ? on.held(hold) : null);

    /** {@code KeySequence.step(combo, after)}: one step of a sequence, never declared or picked on its own. */
    public static final ComponentType<KeySequence.Step> STEP = Types.call(KeySequence.Step.class,
            method(KeySequence.class, "step", Combo.class, Duration.class),
            s -> List.of(s.combo(), s.after()),
            parts -> parts.size() == 2 && parts.get(0) instanceof Combo combo && parts.get(1) instanceof Duration after
                    && !after.isNegative() ? KeySequence.step(combo, after) : null);

    /**
     * Combos one after another, written {@code KeySequence.of(step(…), step(…))}: one declared part, the
     * {@link #STEP}, repeated as varargs. A fresh one is select all, wait 100 ms, copy — two steps, so the
     * editor opens on what a sequence is for.
     */
    public static final Types.DeclaredCall<KeySequence> KEY_SEQUENCE =
            Types.editable(KeySequence.class,
                            () -> KeySequence.of(KeySequence.step(Combo.of(Key.CTRL, Key.A), Duration.ofMillis(100)),
                                    KeySequence.step(Combo.of(Key.CTRL, Key.C), Duration.ZERO)),
                            () -> InputEditors::sequence)
                    .writtenAs(Types.call(KeySequence.class, method(KeySequence.class, "of", KeySequence.Step[].class),
                            s -> List.copyOf(s.steps()), parts -> {
                                List<KeySequence.Step> steps = Types.each(parts, KeySequence.Step.class);
                                return steps == null ? null : new KeySequence(steps);
                            }));

    /**
     * Several pictures, written {@code ImageTemplateGroup.of(a, b)}. A fresh one holds the placeholder
     * picture, for the reason a fresh {@code ImageTemplate} is it: an empty group is a value the bot cannot
     * match anything with. Drawn by the same picture row as a run of picture arguments.
     */
    public static final Types.DeclaredCall<ImageTemplateGroup> IMAGE_TEMPLATE_GROUP =
            Types.editable(ImageTemplateGroup.class, () -> ImageTemplateGroup.of(placeholder()), () -> TemplateEditors::group)
                    .writtenAs(Types.call(ImageTemplateGroup.class,
                            method(ImageTemplateGroup.class, "of", ImageTemplate[].class),
                            g -> List.copyOf(g.templates()), parts -> {
                                // An empty group is still a group the bot can hold, unlike an empty combo.
                                if (parts.isEmpty()) return ImageTemplateGroup.of(List.of());
                                List<ImageTemplate> templates = Types.each(parts, ImageTemplate.class);
                                return templates == null ? null : ImageTemplateGroup.of(templates);
                            }));

    /**
     * A type a bot author may <b>hold</b> but cannot edit, whose fresh form is a call the bot re-evaluates.
     *
     * <p>{@code fresh()} answers {@code null} and {@code freshCall()} names the method the host writes a
     * call to, which is the distinction {@code PluginType} grew for these. The difference is not a spelling
     * one: {@code Vision.lastMatch()} means <em>the match the bot found a moment ago</em>, and freezing it into
     * a {@code MatchResult} value would change the declaration into a fabricated miss. Calling it to obtain
     * one is worse still — it would run the vision stack inside {@code botmaker plugin validate}.
     *
     * <p>The editor is a pill saying in plain words what the bot fills in, with the Java in its tooltip
     * ({@link ResultEditors}). That is the honest control: there is nothing here anyone configures.
     */
    private static <T> PluginType<T> seeded(Class<T> type, Method freshCall) {
        return Types.editable(type, () -> null, () -> ctx -> ResultEditors.pill(ctx, type))
                .preview(() -> ctx -> ResultEditors.pill(ctx, type))
                .freshCall(freshCall);
    }

    /**
     * The sixteen, in the order a menu should offer them: the vision types, the geometry ones, the two
     * input enums, a key combination and a key sequence, the capture source and the picture group, then the four
     * a bot holds but nobody edits.
     *
     * <p>A host offers plugin-basics' eleven before them, which is what puts the literals a bot mostly counts
     * and labels with at the top of the list.
     */
    public static final List<PluginType<?>> ALL = List.of(
            IMAGE_TEMPLATE, PRECISION,
            POINT_TYPE, RECT_TYPE, SIZE_TYPE, DIRECTION,
            KEY, MOUSE_BUTTON, COMBO, KEY_SEQUENCE,
            CaptureTypes.CAPTURE_SOURCE, IMAGE_TEMPLATE_GROUP,
            seeded(MatchResult.class, method(Vision.class, "lastMatch")),
            seeded(Matches.class, method(Matches.class, "none")),
            seeded(ColorMatch.class, method(Vision.class, "lastColorMatch")),
            seeded(TextMatch.class, method(Vision.class, "lastTextMatch")));

    /**
     * {@code precision.tolerance(d)}, {@code .minArea(n)} and {@code .minCount(n)}: the chains a person
     * writes on a named precision ({@code Precision.TIGHT.minArea(400)}), read as the value they build.
     * Instance factories, so the host never writes them: an edited precision is written as
     * {@link #PRECISION} writes it.
     */
    public static final List<ComponentType<Precision>> PRECISION_WITHERS = List.of(
            wither("tolerance", double.class, Precision::deltaE, (p, v) -> p.tolerance(v.doubleValue())),
            wither("minArea", int.class, Precision::minArea, (p, v) -> p.minArea(v.intValue())),
            wither("minCount", int.class, Precision::minCount, (p, v) -> p.minCount(v.intValue())));

    /** One of {@link #PRECISION_WITHERS}: the method, and how to read its one part back off a value. */
    private static ComponentType<Precision> wither(String name, Class<?> argument, Function<Precision, Object> part,
                                                   BiFunction<Precision, Number, Precision> apply) {
        return Types.call(Precision.class, method(Precision.class, name, argument),
                p -> List.of(p, part.apply(p)),
                parts -> parts.size() == 2 && parts.get(0) instanceof Precision on && parts.get(1) instanceof Number n
                        ? apply.apply(on, n) : null);
    }
}
