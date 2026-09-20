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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The eight types the SDK contributes to a project's vocabulary — registered through the same
 * {@link ValueCatalog} builder any other plugin uses, with no privilege a second plugin is denied.
 *
 * <h2>This was an enum, and the loss of it is the point</h2>
 *
 * <p>{@code ValueType} was seventeen constants in {@code api.authoring} and two exhaustive {@code switch}es
 * over them. That is exactly right for as long as there is one plugin and wrong the moment there are two: a
 * Discord plugin wanting a {@code Channel} variable would have needed a constant granted to it in the SDK's
 * enum.
 *
 * <h2>Nine of the seventeen left on 2026-09-09, and the eight that stayed are the test</h2>
 *
 * <p>{@code TEXT}, {@code YES_NO}, {@code WHOLE_NUMBER}, {@code DECIMAL_NUMBER}, {@code CHARACTER},
 * {@code COLOR}, {@code DATE}, {@code TIME_OF_DAY} and {@code DURATION} are
 * {@code com.botmaker.plugin.basics.values.BasicsValueTypes}' now — plugin #2's, with the same ids, so no
 * stored project changed meaning. Nothing about a whole number is about automating a game; they were here
 * only because the SDK was written first, which made having a duration variable plugin #1's privilege.
 * What is left is what a bot's own API actually names: a picture, how exactly to match it, three geometry
 * shapes and three enums.
 *
 * <p><b>A host merges the two.</b> {@code SdkPlugin.valueTypes()} answers this catalog and nothing else, as
 * every plugin does; the editor's vocabulary is {@link ValueCatalog#merge} over every loaded plugin's. Where
 * the SDK itself has to read a whole project file — {@code Authoring}'s Jackson mapper — it merges the two
 * itself, because it resolves plugin-basics as an ordinary dependency and can.
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
 * <p>Every type here is the SDK's own, so every one is written by simple name and declares an
 * {@link ValueType.Builder#importing import}, named by a real {@link Class} literal so a rename in
 * {@code api.*} breaks this file rather than a bot's build. (The fully-qualified spellings the JDK types
 * used went with them to plugin-basics, where the rule is unchanged: a type that needs no import cannot be
 * left out of a generated file's fixed import block.)
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

    /**
     * The headings a picker files these under. Free strings by contract — a second plugin groups its own
     * types without a constant being granted to it — so they are named once here rather than spelled at
     * eight call sites, where a typo would silently split a group in two.
     */
    private static final String VISION = "Vision";
    private static final String GEOMETRY = "Geometry";
    private static final String INPUT = "Input";

    public static final ValueType IMAGE_TEMPLATE = sdk("IMAGE_TEMPLATE", "Image template", VISION,
            com.botmaker.sdk.api.vision.ImageTemplate.class, false);

    public static final ValueType PRECISION = sdk("PRECISION", "Precision", VISION, Precision.class, false);

    public static final ValueType POINT = sdk("POINT", "Point", GEOMETRY, Point.class, false);
    public static final ValueType RECT = sdk("RECT", "Rectangle", GEOMETRY, Rect.class, false);
    public static final ValueType SIZE = sdk("SIZE", "Size", GEOMETRY, Size.class, false);
    public static final ValueType DIRECTION = sdk("DIRECTION", "Direction", GEOMETRY, Direction.class, true);

    public static final ValueType KEY = sdk("KEY", "Key", INPUT, Key.class, true);
    public static final ValueType MOUSE_BUTTON = sdk("MOUSE_BUTTON", "Mouse button", INPUT,
            MouseButton.class, true);

    /**
     * The SDK's vocabulary, in the order a menu should offer it: the vision types, then the geometry ones,
     * then the two input enums. A host offers plugin-basics' nine before them, which is what puts the
     * literals a bot mostly counts and labels with at the top of the list, exactly as before the split.
     */
    public static final ValueCatalog CATALOG = ValueCatalog.builder()
            // The one codec whose default is a choice rather than a fallback: a fresh image variable points
            // at the placeholder every project ships, for the same reason a fresh `new ImageTemplate(...)`
            // block does — an empty chip is a value the bot cannot run on.
            .add(IMAGE_TEMPLATE, seeded(codec(SdkValueTypes::trim, s -> s, SdkValueTypes::templateLiteral,
                            SdkValueTypes::templateOfLiteral),
                    TemplateNames.DEFAULT_TEMPLATE_NAME))
            .add(PRECISION, codec(WireText::precision, WireText::spellPrecision,
                    SdkValueTypes::precisionLiteral, SdkValueTypes::precisionOfLiteral))
            .add(POINT, codec(WireText::point, WireText::spellPoint,
                    p -> "new Point(%d, %d)".formatted(p.x(), p.y()),
                    java -> newInstance(java, "Point", 2).map(n -> new Point(n[0], n[1]))))
            .add(RECT, codec(WireText::area, WireText::spellArea,
                    r -> "new Rect(%d, %d, %d, %d)".formatted(r.x(), r.y(), r.width(), r.height()),
                    java -> newInstance(java, "Rect", 4).map(n -> new Rect(n[0], n[1], n[2], n[3]))))
            .add(SIZE, codec(WireText::size, WireText::spellSize,
                    s -> "new Size(%d, %d)".formatted(s.width(), s.height()),
                    java -> newInstance(java, "Size", 2).map(n -> new Size(n[0], n[1]))))
            .add(DIRECTION, enumCodec(WireText::direction, "Direction"))
            .add(KEY, enumCodec(WireText::key, "Key"))
            .add(MOUSE_BUTTON, enumCodec(WireText::mouseButton, "MouseButton"))
            // The two leaves whose value is a name — a method reference, a capture source's own factory —
            // and the five fixed shapes a flow is written as. Registered here rather than in a second
            // catalog for the reason there is one catalog at all: a host merges per plugin, not per file.
            .add(SdkFlowValues.ACTIVITY_BODY, SdkFlowValues.BODY_CODEC)
            .add(SdkFlowValues.CAPTURE_SOURCE, SdkFlowValues.SOURCE_CODEC)
            .add(SdkFlowValues.FLOW_SHAPE)
            .add(SdkFlowValues.ACTIVITY_SHAPE)
            .add(SdkFlowValues.EDGE_SHAPE)
            .add(SdkFlowValues.PRESET_SHAPE)
            .add(SdkFlowValues.LIMITS_SHAPE)
            .build();

    // ---- literals -------------------------------------------------------------------------------------
    //
    // Every one of these writes the *parsed* value, never the text — `new java.awt.Color(255, 0, 0)` rather
    // than `Color.decode("#FF0000")`, `LocalDate.of(2026, 8, 26)` rather than `LocalDate.parse(…)`. A
    // generated file therefore holds no expression that can throw at class initialisation, which is what it
    // means for a bot never to fail to start because of its own configuration file.

    private static String templateLiteral(String name) {
        return "new ImageTemplate(" + LiteralWriter.quote(WireText.IMAGE_PREFIX + name + ".png") + ")";
    }

    /** Through {@link WireText#precision}, which is where the clamping to what the record accepts lives. */
    private static String precisionLiteral(Precision p) {
        return "new Precision(%s, %d, %d)".formatted(Double.toString(p.deltaE()), p.minArea(), p.minCount());
    }

    private static String trim(String wire) {
        return wire == null ? "" : wire.trim();
    }

    // ---- the inverses ---------------------------------------------------------------------------------
    //
    // Each of these undoes the literal written a few lines above it, and nothing else. A spelling that means
    // the same thing — `Point.of(3, 4)`, a constant from elsewhere, an expression over another field — is
    // deliberately not recognised: it was written by a person, and the host shows it as it stands rather
    // than rewriting it into the canonical form the moment somebody opens the window. Recognising more would
    // be recognising *other people's* Java, which is a parser's job and not a codec's.
    //
    // Until 2026-09-20 there were no inverses here at all. `wireOfLiteral` was a `default` answering empty,
    // so all eight of these types were written into a user's Java by an editor that then refused to edit
    // them, and nothing anywhere said so. That is why the method is abstract now.

    /**
     * The arguments of {@code new Name(…)}, exactly {@code count} whole numbers, or empty.
     *
     * <p>A leading package is accepted — {@code new com.botmaker.sdk.api.geometry.Point(3, 4)} is what a
     * file that did not import the type has to write — because only the simple name and the arity are this
     * codec's own.
     */
    private static Optional<int[]> newInstance(String java, String name, int count) {
        String source = java.strip();
        if (!source.startsWith("new ") || !source.endsWith(")")) return Optional.empty();
        int open = source.indexOf('(');
        if (open < 0) return Optional.empty();
        String type = source.substring(4, open).strip();
        if (!type.equals(name) && !type.endsWith("." + name)) return Optional.empty();
        return integers(source.substring(open + 1, source.length() - 1), count);
    }

    /** The comma-separated whole-number arguments of a call, exactly {@code count} of them, or empty. */
    private static Optional<int[]> integers(String arguments, int count) {
        String[] parts = arguments.split(",", -1);
        if (parts.length != count || (count == 0 && !arguments.isBlank())) return Optional.empty();
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].strip());
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
        }
        return Optional.of(out);
    }

    /**
     * {@code new ImageTemplate("images/ore.png")} back to the base name {@code ore}.
     *
     * <p>Deliberately narrower than the picture editor's own reader, which accepts any folder: what is
     * stored here is a <em>base name</em>, so a path outside the project's picture folder has no stored form
     * and must read as "not a value this codec wrote" rather than as a name that would be written back
     * somewhere else.
     */
    private static Optional<String> templateOfLiteral(String java) {
        String source = java.strip();
        int open = source.indexOf('(');
        if (!source.startsWith("new ") || !source.endsWith(")") || open < 0) return Optional.empty();
        String type = source.substring(4, open).strip();
        if (!type.equals("ImageTemplate") && !type.endsWith(".ImageTemplate")) return Optional.empty();
        return LiteralWriter.unquote(source.substring(open + 1, source.length() - 1))
                .filter(path -> path.startsWith(WireText.IMAGE_PREFIX) && path.endsWith(".png"))
                .map(path -> path.substring(WireText.IMAGE_PREFIX.length(), path.length() - ".png".length()))
                .filter(name -> !name.isEmpty());
    }

    /** Through {@link WireText#precision} again, so a hand-edited number is clamped exactly as a stored one. */
    private static Optional<Precision> precisionOfLiteral(String java) {
        String source = java.strip();
        int open = source.indexOf('(');
        if (!source.startsWith("new ") || !source.endsWith(")") || open < 0) return Optional.empty();
        String type = source.substring(4, open).strip();
        if (!type.equals("Precision") && !type.endsWith(".Precision")) return Optional.empty();
        String[] parts = source.substring(open + 1, source.length() - 1).split(",", -1);
        if (parts.length != 3) return Optional.empty();
        try {
            return Optional.of(WireText.precision("%s,%s,%s".formatted(
                    Double.toString(Double.parseDouble(parts[0].strip())),
                    Integer.toString(Integer.parseInt(parts[1].strip())),
                    Integer.toString(Integer.parseInt(parts[2].strip())))));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
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
    static ValueType sdk(String id, String label, String group, Class<?> type, boolean closedSet) {
        ValueType.Builder b = ValueType.of(id).label(label).group(group)
                .source(type.getSimpleName()).importing(type.getName());
        return (closedSet ? b.closedSet().options(constantNames(type)) : b).build();
    }

    /** The constant names of an enum in declaration order; empty for anything that is not one. */
    private static List<String> constantNames(Class<?> type) {
        Object[] constants = type.getEnumConstants();
        if (constants == null) return List.of();
        return Arrays.stream(constants).map(c -> ((Enum<?>) c).name()).toList();
    }

    /**
     * A codec from its four answers — the last being {@code literal} read backwards, which has no default
     * and cannot have one: a type that cannot read its own literal is one the editor writes once and
     * thereafter refuses to edit, which is what all eight of these were until 2026-09-20.
     */
    static <T> ValueCodec<T> codec(Function<String, T> parse, Function<T, String> store,
                                   Function<T, String> literal,
                                   Function<String, Optional<T>> valueOfLiteral) {
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

            @Override
            public Optional<T> valueOfLiteral(String javaSource) {
                return javaSource == null ? Optional.empty() : valueOfLiteral.apply(javaSource.strip());
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
            public Optional<T> valueOfLiteral(String javaSource) {
                return codec.valueOfLiteral(javaSource);
            }

            @Override
            public String defaultWire() {
                return defaultWire;
            }
        };
    }

    /**
     * An enum constant, written and read as {@code SimpleName.NAME}.
     *
     * <p>A leading package is accepted for the same reason {@link #newInstance} accepts one. The constant
     * name is checked against what {@code parse} answers rather than trusted: these parsers are total and
     * fall back, so a name this build does not have would otherwise read back as some other constant
     * entirely — silently retyping a user's value instead of declining to read it.
     */
    private static <E extends Enum<E>> ValueCodec<E> enumCodec(Function<String, E> parse, String simpleName) {
        return codec(parse, Enum::name, e -> simpleName + "." + e.name(), java -> {
            if (!java.startsWith(simpleName + ".") && !java.contains("." + simpleName + ".")) {
                return Optional.empty();
            }
            String name = java.substring(java.lastIndexOf('.') + 1);
            E constant = parse.apply(name);
            return constant != null && constant.name().equals(name) ? Optional.of(constant) : Optional.empty();
        });
    }
}
