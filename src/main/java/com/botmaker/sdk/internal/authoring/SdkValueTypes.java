package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueCodec;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.authoring.TemplateNames;
import com.botmaker.sdk.authoring.WireText;

import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * The seventeen types the SDK contributes to a project's vocabulary — registered through the same
 * {@link ValueCatalog} builder any other plugin uses, with no privilege a second plugin is denied.
 *
 * <h2>This was an enum, and the loss of it is the point</h2>
 *
 * <p>{@code ValueType} was seventeen constants in {@code api.authoring} and two exhaustive {@code switch}es
 * over them. That is exactly right for as long as there is one plugin and wrong the moment there are two: a
 * Discord plugin wanting a {@code Channel} variable would have needed a constant granted to it in the SDK's
 * enum. The seventeen are still declared in one place, and that place is still the SDK — but as
 * registrations, which anyone can make.
 *
 * <p>What is absent is absent for one reason: it has no value anyone writes down. {@code void}; a group of
 * templates (a {@code List of Image template} says it better); and the vision <em>results</em> — a match is
 * something the bot found a moment ago, not something anyone configures.
 *
 * <h2>The ids are persisted and the ids are stable</h2>
 *
 * <p>Each id is the name its old enum constant had, so every {@code activities.json} ever written keeps its
 * meaning. Renaming one rewrites every stored project silently — don't. There is no longer a total
 * {@code fromWire} falling back to {@code TEXT}: an unrecognised id is now an
 * {@linkplain ValueType#unknown unknown type}, which keeps its text instead of quietly becoming a string.
 *
 * <h2>Qualified or imported</h2>
 *
 * <p>The {@code java.time} and {@code java.awt} types are written <b>fully qualified</b>, for the reason that
 * has not changed: a generated file carries a fixed import block, and a type that needs no import cannot be
 * left out of one. The SDK's own types are written by simple name and declare an
 * {@link ValueType.Builder#importing import}, named by a real {@link Class} literal so a rename in
 * {@code api.*} breaks this file rather than a bot's build.
 *
 * <h2>What each codec's {@code T} is</h2>
 *
 * <p>Whatever is most useful to parse into, and nothing else depends on the choice — the host only ever calls
 * {@code literal(parse(wire))} behind a wildcard. Mostly that is the obvious type. {@code IMAGE_TEMPLATE} is
 * the exception and reads as a {@code String}: what is stored is a template's <em>base name</em>, and
 * constructing an {@link com.botmaker.sdk.api.vision.ImageTemplate} to describe it would open a file at
 * generation time to write one line of source.
 */
public final class SdkValueTypes {

    private SdkValueTypes() {
    }

    // ---- the types ------------------------------------------------------------------------------------

    public static final ValueType TEXT = ValueType.of(ValueCatalog.TEXT_ID)
            .label("Text").source("String").build();

    public static final ValueType YES_NO = ValueType.of("YES_NO")
            .label("Yes / no").source("boolean").boxed("Boolean").primitive().closedSet().build();

    public static final ValueType WHOLE_NUMBER = ValueType.of("WHOLE_NUMBER")
            .label("Whole number").source("int").boxed("Integer").primitive().bounded().build();

    public static final ValueType DECIMAL_NUMBER = ValueType.of("DECIMAL_NUMBER")
            .label("Decimal number").source("double").boxed("Double").primitive().bounded().build();

    public static final ValueType CHARACTER = ValueType.of("CHARACTER")
            .label("Character").source("char").boxed("Character").primitive().build();

    public static final ValueType COLOR = ValueType.of("COLOR")
            .label("Colour").source("java.awt.Color").build();

    public static final ValueType DATE = ValueType.of("DATE")
            .label("Date").source("java.time.LocalDate").build();

    public static final ValueType TIME_OF_DAY = ValueType.of("TIME_OF_DAY")
            .label("Time of day").source("java.time.LocalTime").build();

    public static final ValueType DURATION = ValueType.of("DURATION")
            .label("Duration").source("java.time.Duration").build();

    public static final ValueType IMAGE_TEMPLATE = sdk("IMAGE_TEMPLATE", "Image template",
            com.botmaker.sdk.api.vision.ImageTemplate.class, false);

    public static final ValueType PRECISION = sdk("PRECISION", "Precision", Precision.class, false);

    public static final ValueType POINT = sdk("POINT", "Point", Point.class, false);
    public static final ValueType RECT = sdk("RECT", "Rectangle", Rect.class, false);
    public static final ValueType SIZE = sdk("SIZE", "Size", Size.class, false);
    public static final ValueType DIRECTION = sdk("DIRECTION", "Direction", Direction.class, true);

    public static final ValueType KEY = sdk("KEY", "Key", Key.class, true);
    public static final ValueType MOUSE_BUTTON = sdk("MOUSE_BUTTON", "Mouse button", MouseButton.class, true);

    /**
     * The SDK's vocabulary, in the order a menu should offer it: the literals a bot mostly counts, flags and
     * labels with, then the time types, then the vision and geometry ones, then the two input enums.
     */
    public static final ValueCatalog CATALOG = ValueCatalog.builder()
            .add(TEXT, codec(WireText::text, s -> s, LiteralWriter::quote))
            .add(YES_NO, codec(WireText::flag, b -> Boolean.toString(b), b -> Boolean.toString(b)))
            .add(WHOLE_NUMBER, codec(WireText::whole, i -> Integer.toString(i), i -> Integer.toString(i)))
            .add(DECIMAL_NUMBER, codec(WireText::decimal, d -> Double.toString(d), d -> Double.toString(d)))
            .add(CHARACTER, codec(WireText::letter, String::valueOf, LiteralWriter::quoteChar))
            .add(COLOR, codec(WireText::color, SdkValueTypes::hex, SdkValueTypes::colorLiteral))
            .add(DATE, codec(WireText::date, LocalDate::toString, SdkValueTypes::dateLiteral))
            .add(TIME_OF_DAY, codec(WireText::time, LocalTime::toString, SdkValueTypes::timeLiteral))
            .add(DURATION, codec(WireText::duration,
                    d -> WireText.spellDuration(d.toMillis()),
                    d -> "java.time.Duration.ofMillis(" + d.toMillis() + "L)"))
            // The one codec whose default is a choice rather than a fallback: a fresh image variable points
            // at the placeholder every project ships, for the same reason a fresh `new ImageTemplate(...)`
            // block does — an empty chip is a value the bot cannot run on.
            .add(IMAGE_TEMPLATE, seeded(codec(SdkValueTypes::trim, s -> s, SdkValueTypes::templateLiteral),
                    TemplateNames.DEFAULT_TEMPLATE_NAME))
            .add(PRECISION, codec(WireText::precision, SdkValueTypes::spellPrecision,
                    SdkValueTypes::precisionLiteral))
            .add(POINT, codec(WireText::point, p -> p.x() + "," + p.y(),
                    p -> "new Point(%d, %d)".formatted(p.x(), p.y())))
            .add(RECT, codec(WireText::area, r -> r.x() + "," + r.y() + "," + r.width() + "," + r.height(),
                    r -> "new Rect(%d, %d, %d, %d)".formatted(r.x(), r.y(), r.width(), r.height())))
            .add(SIZE, codec(WireText::size, s -> s.width() + "," + s.height(),
                    s -> "new Size(%d, %d)".formatted(s.width(), s.height())))
            .add(DIRECTION, enumCodec(WireText::direction, "Direction"))
            .add(KEY, enumCodec(WireText::key, "Key"))
            .add(MOUSE_BUTTON, enumCodec(WireText::mouseButton, "MouseButton"))
            .build();

    // ---- literals -------------------------------------------------------------------------------------
    //
    // Every one of these writes the *parsed* value, never the text — `new java.awt.Color(255, 0, 0)` rather
    // than `Color.decode("#FF0000")`, `LocalDate.of(2026, 8, 26)` rather than `LocalDate.parse(…)`. A
    // generated file therefore holds no expression that can throw at class initialisation, which is what it
    // means for a bot never to fail to start because of its own configuration file.

    /** The components, not {@code Color.decode(…)}: {@code decode} parses at class-init and can throw. */
    private static String colorLiteral(Color c) {
        return "new java.awt.Color(%d, %d, %d)".formatted(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String dateLiteral(LocalDate d) {
        return "java.time.LocalDate.of(%d, %d, %d)".formatted(d.getYear(), d.getMonthValue(),
                d.getDayOfMonth());
    }

    /** Seconds included always, so a stored {@code 07:30:15} is not silently truncated to the minute. */
    private static String timeLiteral(LocalTime t) {
        return "java.time.LocalTime.of(%d, %d, %d)".formatted(t.getHour(), t.getMinute(), t.getSecond());
    }

    private static String templateLiteral(String name) {
        return "new ImageTemplate(" + LiteralWriter.quote(WireText.IMAGE_PREFIX + name + ".png") + ")";
    }

    /** Through {@link WireText#precision}, which is where the clamping to what the record accepts lives. */
    private static String precisionLiteral(Precision p) {
        return "new Precision(%s, %d, %d)".formatted(Double.toString(p.deltaE()), p.minArea(), p.minCount());
    }

    private static String spellPrecision(Precision p) {
        return p.deltaE() + "," + p.minArea() + "," + p.minCount();
    }

    private static String hex(Color c) {
        return "#%02X%02X%02X".formatted(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String trim(String wire) {
        return wire == null ? "" : wire.trim();
    }

    // ---- plumbing -------------------------------------------------------------------------------------

    /** An SDK type: written by simple name, imported, and named by the class so a rename breaks this build. */
    /**
     * An SDK type, written by its simple name with an import arranged.
     *
     * <p>A closed set also declares its own values, read off the enum rather than written down — an enum's
     * constants are never curated (they are the type's whole value set), so there is nothing here for a
     * hand-kept list to add beyond a second place to forget one.
     */
    private static ValueType sdk(String id, String label, Class<?> type, boolean closedSet) {
        ValueType.Builder b = ValueType.of(id).label(label)
                .source(type.getSimpleName()).importing(type.getName());
        return (closedSet ? b.closedSet().options(constantNames(type)) : b).build();
    }

    /** The constant names of an enum in declaration order; empty for anything that is not one. */
    private static List<String> constantNames(Class<?> type) {
        Object[] constants = type.getEnumConstants();
        if (constants == null) return List.of();
        return Arrays.stream(constants).map(c -> ((Enum<?>) c).name()).toList();
    }

    private static <T> ValueCodec<T> codec(Function<String, T> parse, Function<T, String> store,
                                           Function<T, String> literal) {
        return new ValueCodec<>() {
            @Override
            public T parse(String wire) {
                return parse.apply(wire);
            }

            @Override
            public String store(T value) {
                return store.apply(value);
            }

            @Override
            public String literal(T value) {
                return literal.apply(value);
            }
        };
    }

    /** An enum constant, stored and written by its own name. Total because {@code parse} already is. */
    /** {@code codec} with a different seed for a freshly created value; everything else is unchanged. */
    private static <T> ValueCodec<T> seeded(ValueCodec<T> codec, String defaultWire) {
        return new ValueCodec<>() {
            @Override
            public T parse(String wire) {
                return codec.parse(wire);
            }

            @Override
            public String store(T value) {
                return codec.store(value);
            }

            @Override
            public String literal(T value) {
                return codec.literal(value);
            }

            @Override
            public String defaultWire() {
                return defaultWire;
            }
        };
    }

    private static <E extends Enum<E>> ValueCodec<E> enumCodec(Function<String, E> parse, String simpleName) {
        return codec(parse, Enum::name, e -> simpleName + "." + e.name());
    }
}
