package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.plugin.toolkit.AbstractPluginType;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.api.vision.TextMatch;
import com.botmaker.sdk.api.vision.Vision;
import com.botmaker.sdk.authoring.TemplateNames;
import com.botmaker.sdk.internal.plugin.editors.GeometryEditors;
import com.botmaker.sdk.internal.plugin.editors.InputEditors;
import com.botmaker.sdk.internal.plugin.editors.PrecisionEditors;
import com.botmaker.sdk.internal.plugin.editors.TemplateEditors;
import javafx.scene.Node;

import java.util.List;

/**
 * The eight types the SDK declares — a picture, how exactly to match it, three geometry shapes and three
 * enums.
 *
 * <h2>This was an enum, then four declarations, and now it is one</h2>
 *
 * <p>{@code ValueType} was seventeen constants in {@code api.authoring} and two exhaustive {@code switch}es
 * over them — right for as long as there is one plugin, wrong the moment there are two: a Discord plugin
 * wanting a {@code Channel} value would have needed a constant granted to it in the SDK's enum.
 *
 * <p>What replaced it in 2026-08-27 was an open registry, and it asked for a type to be described four
 * times: a {@code ValueType} carrying a persisted id, a {@code ValueCodec} with
 * {@code parse}/{@code store}/{@code literal}/{@code valueOfLiteral}, a {@code SourceSeed} carrying the
 * fresh value as Java <em>text</em>, and a {@code SlotEditor} predicate naming the type a third time. For
 * {@code Point} those lived in three files and agreed only because somebody kept them agreeing. Here javac
 * asks for all of it at once, and the type is named once — in {@code type()}.
 *
 * <h2>Nine of the seventeen left on 2026-09-09, and the eight that stayed are the test</h2>
 *
 * <p>Text, a flag, two numbers, a character, a colour, a date, a time of day and a duration are
 * {@code com.botmaker.plugin.basics.values.BasicsTypes}' now — plugin #2's. Nothing about a whole number is
 * about automating a game; they were here only because the SDK was written first, which made having a
 * duration value plugin #1's privilege. What is left is what a bot's own API actually names.
 *
 * <p>What is absent is absent for one reason: it has no value anyone writes down. {@code void}; a group of
 * templates (a {@code List<ImageTemplate>} says it better); and the vision <em>results</em> — a match is
 * something the bot found a moment ago, not something anyone configures.
 *
 * <h2>The identity is the class, so a rename in {@code api.*} breaks this file</h2>
 *
 * <p>Every one of these names a real {@link Class}, which is the whole of what the host indexes on. The
 * persisted ids ({@code POINT}, {@code IMAGE_TEMPLATE}, …) are deleted with {@code ValueType}: a project's
 * file says {@code com.botmaker.sdk.api.geometry.Point} because that is what the field is declared as, and
 * has done since a parameter became a {@code @Param} field.
 *
 * @see SdkFlowValues the five shapes a {@code Flow} is written as, which are composites and never picked
 */
public final class SdkTypes {

    private SdkTypes() {
    }

    /**
     * A named picture.
     *
     * <p><b>The one type whose fresh value is a choice rather than a fallback.</b> A new picture value
     * points at the placeholder every project ships, because an empty chip is a value the bot cannot run
     * on and no amount of reasoning about an empty {@code ImageTemplate} discovers that.
     */
    public static final class ImageTemplateType extends AbstractPluginType<ImageTemplate>
            implements ComponentType<ImageTemplate> {
        public ImageTemplateType() { super(ImageTemplate.class); }
        @Override public ImageTemplate fresh() {
            return new ImageTemplate(TemplateNames.pathFor(TemplateNames.DEFAULT_TEMPLATE_NAME));
        }
        @Override public Node editor(ValueContext ctx) { return TemplateEditors.template(ctx); }
        @Override public Node preview(ValueContext ctx) { return TemplateEditors.preview(ctx); }

        @Override public List<Class<?>> componentTypes() { return List.of(String.class); }
        @Override public List<Object> components(ImageTemplate t) { return List.of(t.filePath()); }
        @Override public ImageTemplate build(List<Object> parts) {
            return new ImageTemplate(text(parts, 0));
        }
    }

    /**
     * How exact a pixel match has to be.
     *
     * <p>{@code Precision.DEFAULT} rather than {@code new Precision(0, 0, 0)}: a zero tolerance matches
     * nothing and a zero minimum area matches everything, so neither end of the range is a sensible start.
     * That is what the deleted {@code SourceSeed} said in text, and what this says in Java.
     */
    public static final class PrecisionType extends AbstractPluginType<Precision>
            implements ComponentType<Precision> {
        public PrecisionType() { super(Precision.class); }
        @Override public Precision fresh() { return Precision.DEFAULT; }
        @Override public Node editor(ValueContext ctx) { return PrecisionEditors.precision(ctx); }

        @Override public List<Class<?>> componentTypes() {
            return List.of(double.class, int.class, int.class);
        }
        @Override public List<Object> components(Precision p) {
            return List.of(p.deltaE(), p.minArea(), p.minCount());
        }
        @Override public Precision build(List<Object> parts) {
            // Through the record's own canonical constructor, which is where the clamping to what a match
            // can actually use lives — so a hand-edited number is clamped exactly as a picked one is.
            return new Precision(number(parts, 0), whole(parts, 1), whole(parts, 2));
        }
    }

    /** A point on the screen. */
    public static final class PointType extends AbstractPluginType<Point> implements ComponentType<Point> {
        public PointType() { super(Point.class); }
        @Override public Point fresh() { return new Point(0, 0); }
        @Override public Node editor(ValueContext ctx) { return GeometryEditors.point(ctx); }

        @Override public List<Class<?>> componentTypes() { return List.of(int.class, int.class); }
        @Override public List<Object> components(Point p) { return List.of(p.x(), p.y()); }
        @Override public Point build(List<Object> parts) {
            return new Point(whole(parts, 0), whole(parts, 1));
        }
    }

    /** A rectangle on the screen: where it is, then how big it is. */
    public static final class RectType extends AbstractPluginType<Rect> implements ComponentType<Rect> {
        public RectType() { super(Rect.class); }
        @Override public Rect fresh() { return new Rect(0, 0, 0, 0); }
        @Override public Node editor(ValueContext ctx) { return GeometryEditors.rect(ctx); }

        @Override public List<Class<?>> componentTypes() {
            return List.of(int.class, int.class, int.class, int.class);
        }
        @Override public List<Object> components(Rect r) {
            return List.of(r.x(), r.y(), r.width(), r.height());
        }
        @Override public Rect build(List<Object> parts) {
            return new Rect(whole(parts, 0), whole(parts, 1), whole(parts, 2), whole(parts, 3));
        }
    }

    /** A width and a height, with no position. */
    public static final class SizeType extends AbstractPluginType<Size> implements ComponentType<Size> {
        public SizeType() { super(Size.class); }
        @Override public Size fresh() { return new Size(0, 0); }
        @Override public Node editor(ValueContext ctx) { return GeometryEditors.size(ctx); }

        @Override public List<Class<?>> componentTypes() { return List.of(int.class, int.class); }
        @Override public List<Object> components(Size s) { return List.of(s.width(), s.height()); }
        @Override public Size build(List<Object> parts) {
            return new Size(whole(parts, 0), whole(parts, 1));
        }
    }

    /**
     * An enum constant, which needs no {@link ComponentType}.
     *
     * <p>Its Java is the constant's own name, which the host writes and reads without help — the case that
     * proves the two interfaces had to stay independent. The fresh value is the first constant, which is
     * what an enum with no obvious default means by "unset".
     *
     * @param <E> the enum
     */
    private abstract static class EnumType<E extends Enum<E>> extends AbstractPluginType<E> {
        EnumType(Class<E> type) { super(type); }
        @Override public E fresh() { return type().getEnumConstants()[0]; }
    }

    /** Which way something moves or faces. */
    public static final class DirectionType extends EnumType<Direction> {
        public DirectionType() { super(Direction.class); }
        @Override public Node editor(ValueContext ctx) { return InputEditors.direction(ctx); }
    }

    /** A key on the keyboard. */
    public static final class KeyType extends EnumType<Key> {
        public KeyType() { super(Key.class); }
        @Override public Node editor(ValueContext ctx) { return InputEditors.key(ctx); }
    }

    /** A mouse button. */
    public static final class MouseButtonType extends EnumType<MouseButton> {
        public MouseButtonType() { super(MouseButton.class); }
        @Override public Node editor(ValueContext ctx) { return InputEditors.mouseButton(ctx); }
    }

    /**
     * The three geometry declarations, named so {@code GeometryEditors} can hand one to
     * {@code Editors.tuplePill} — which is where the arity, the components and the way back from a row of
     * numbers to a value all come from. Three independent statements of "a Rect has four numbers" became
     * one on 2026-09-22.
     */
    public static final PointType POINT_TYPE = new PointType();
    public static final RectType RECT_TYPE = new RectType();
    public static final SizeType SIZE_TYPE = new SizeType();

    /**
     * A type a bot author may <b>hold</b> but cannot edit, whose fresh form is a call the bot re-evaluates.
     *
     * <p>{@link #fresh()} answers {@code null} and {@link #freshSource()} carries the expression, which is
     * the distinction {@code PluginType} grew for these six. The difference is not a spelling one:
     * {@code Vision.lastMatch()} means <em>the match the bot found a moment ago</em>, and freezing it into a
     * {@code MatchResult} value would change the declaration into a fabricated miss. Calling it to obtain
     * one is worse still — it would run the vision stack inside {@code botmaker plugin validate}.
     *
     * <p>{@link #editor(ValueContext)} answers {@code null}, so the host shows the expression as written and
     * read-only. That is the honest control: there is nothing here anyone configures.
     *
     * <p>These were {@code SourceSeed}s until 2026-09-22 and are the reason the seed's one irreplaceable
     * fact survived the deletion. Four of the SDK's fourteen declarable types are in this list; the capture
     * source and the picture group left it on 2026-09-23, when each became a value the host reads.
     */
    private static final class SeededType<T> implements PluginType<T> {
        private final Class<T> type;
        private final String freshSource;

        SeededType(Class<T> type, String freshSource) {
            this.type = type;
            this.freshSource = freshSource;
        }

        @Override public Class<T> type() { return type; }
        @Override public T fresh() { return null; }
        @Override public String freshSource() { return freshSource; }
        @Override public Node editor(ValueContext ctx) { return null; }
    }

    /** {@code Owner.member()}, fully qualified — the host writes a declaration with no import rewriter. */
    private static String call(Class<?> owner, String member) {
        return owner.getName() + "." + member + "()";
    }

    /**
     * Several pictures, written {@code ImageTemplateGroup.of(a, b)}. A fresh one holds the placeholder
     * picture, for the reason a fresh {@code ImageTemplate} is it: an empty group is a value the bot cannot
     * match anything with. Nobody edits one on its own: the picture-row editor claims a run of pictures, not
     * this slot.
     */
    public static final class ImageTemplateGroupType extends AbstractPluginType<ImageTemplateGroup>
            implements ComponentType<ImageTemplateGroup> {
        public ImageTemplateGroupType() { super(ImageTemplateGroup.class); }
        @Override public ImageTemplateGroup fresh() {
            return ImageTemplateGroup.of(new ImageTemplate(TemplateNames.pathFor(TemplateNames.DEFAULT_TEMPLATE_NAME)));
        }
        @Override public Node editor(ValueContext ctx) { return null; }

        @Override public String factory() { return "of"; }
        /** One declared part: the host repeats the last part type for every further argument, as varargs. */
        @Override public List<Class<?>> componentTypes() { return List.of(ImageTemplate.class); }
        @Override public List<Object> components(ImageTemplateGroup g) { return List.copyOf(g.templates()); }
        @Override public ImageTemplateGroup build(List<Object> parts) {
            List<ImageTemplate> templates = new java.util.ArrayList<>();
            for (Object part : parts) {
                if (!(part instanceof ImageTemplate template)) return null;
                templates.add(template);
            }
            return ImageTemplateGroup.of(templates);
        }
    }

    /**
     * The fourteen, in the order a menu should offer them: the vision types, the geometry ones, the two
     * input enums, the capture source and the picture group, then the four a bot holds but nobody edits.
     *
     * <p>A host offers plugin-basics' nine before them, which is what puts the literals a bot mostly counts
     * and labels with at the top of the list.
     */
    public static final List<PluginType<?>> ALL = List.of(
            new ImageTemplateType(), new PrecisionType(),
            POINT_TYPE, RECT_TYPE, SIZE_TYPE, new DirectionType(),
            new KeyType(), new MouseButtonType(),
            new CaptureTypes.CaptureSourceType(), new ImageTemplateGroupType(),
            new SeededType<>(MatchResult.class, call(Vision.class, "lastMatch")),
            new SeededType<>(Matches.class, call(Matches.class, "none")),
            new SeededType<>(ColorMatch.class, call(Vision.class, "lastColorMatch")),
            new SeededType<>(TextMatch.class, call(Vision.class, "lastTextMatch")));
}
