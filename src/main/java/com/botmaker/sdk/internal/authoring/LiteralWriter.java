package com.botmaker.sdk.internal.authoring;

import com.botmaker.sdk.api.authoring.ValueChoice;
import com.botmaker.sdk.api.authoring.ValueType;
import com.botmaker.sdk.api.authoring.WireText;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Java source of a stored value: one writer per {@link ValueType}, and the inverse of
 * {@link WireText}'s readers.
 *
 * <h2>The value is written, not read</h2>
 *
 * <p>A generated field used to be a blank final assigned from a parser call — {@code Wire.duration(one("REST"))}
 * — so the bot re-read its own configuration file on every launch. It does not any more. Every value is
 * emitted as the literal the parser would have produced, which is the maintainer's own argument for this
 * design: if a reader already knows how to turn {@code "1h30m"} into a {@link Duration}, the generator can do
 * that conversion once, at generation time, rather than shipping the text and the parser into every bot.
 *
 * <p>The objection that used to block this — "a re-run would then need a re-build" — turned out not to hold:
 * every Run recompiles the project before launching it, so the re-build was always happening.
 *
 * <h2>Every literal is total, and none of them can throw</h2>
 *
 * <p>Each writer goes through {@link WireText}, so an unreadable value becomes the type's default rather than
 * failing generation. It then writes the <em>parsed</em> value, never the text: {@code new java.awt.Color(255,
 * 0, 0)} rather than {@code Color.decode("#FF0000")}, {@code java.time.LocalDate.of(2026, 8, 25)} rather than
 * {@code LocalDate.parse("…")}. A generated file therefore contains no expression that can throw at class
 * initialisation — which is what it means for a bot never to fail to start because of its own configuration.
 *
 * <h2>Qualified or imported</h2>
 *
 * <p>JDK types are written fully qualified, so the generated file needs no import for them and none can be
 * forgotten. The SDK's own types are written by simple name and reported through {@link #imports}, because
 * {@link ValueType#sourceName()} names them by simple name — which is what makes a rename in {@code api.*}
 * break this build rather than a bot's.
 */
public final class LiteralWriter {

    private LiteralWriter() {}

    /**
     * The initialiser for a field of this type holding this value: a single literal, or
     * {@code java.util.List.of(…)} over one literal per item.
     */
    public static String initializer(ValueChoice type, List<String> value) {
        if (!type.isList()) {
            return literal(type.type(), value == null || value.isEmpty() ? "" : value.getFirst());
        }
        if (value == null || value.isEmpty()) return "java.util.List.of()";
        StringBuilder out = new StringBuilder("java.util.List.of(");
        for (int i = 0; i < value.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(literal(type.type(), value.get(i)));
        }
        return out.append(')').toString();
    }

    /** The SDK classes a field of this type has to import — empty for a JDK or primitive type. */
    public static Set<String> imports(ValueChoice type) {
        Class<?> sdk = type.type().sdkType();
        return sdk == null ? Set.of() : Set.of(sdk.getName());
    }

    /** Every SDK class the given types need imported, in first-use order. */
    public static Set<String> imports(List<ValueChoice> types) {
        Set<String> out = new LinkedHashSet<>();
        for (ValueChoice type : types) out.addAll(imports(type));
        return out;
    }

    /** One value of one type, as Java source. */
    static String literal(ValueType type, String wire) {
        return switch (type) {
            case TEXT -> quote(WireText.text(wire));
            case YES_NO -> Boolean.toString(WireText.flag(wire));
            case WHOLE_NUMBER -> Integer.toString(WireText.whole(wire));
            case DECIMAL_NUMBER -> Double.toString(WireText.decimal(wire));
            case CHARACTER -> "'" + escapeChar(WireText.letter(wire)) + "'";
            case COLOR -> color(WireText.color(wire));
            case DATE -> date(WireText.date(wire));
            case TIME_OF_DAY -> time(WireText.time(wire));
            case DURATION -> "java.time.Duration.ofMillis(" + WireText.duration(wire).toMillis() + "L)";
            case IMAGE_TEMPLATE -> "new ImageTemplate(" + quote(WireText.templatePath(wire)) + ")";
            case PRECISION -> precision(wire);
            case POINT -> point(WireText.point(wire));
            case RECT -> rect(WireText.area(wire));
            case SIZE -> size(WireText.size(wire));
            case DIRECTION -> "Direction." + WireText.direction(wire).name();
            case KEY -> "Key." + WireText.key(wire).name();
            case MOUSE_BUTTON -> "MouseButton." + WireText.mouseButton(wire).name();
        };
    }

    /**
     * The components, not {@code Color.decode("#RRGGBB")}. {@code decode} parses at class-initialisation
     * time and can throw; three ints cannot.
     */
    private static String color(Color c) {
        return "new java.awt.Color(%d, %d, %d)".formatted(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String date(LocalDate d) {
        return "java.time.LocalDate.of(%d, %d, %d)".formatted(d.getYear(), d.getMonthValue(),
                d.getDayOfMonth());
    }

    /** Seconds included always, so a stored {@code 07:30:15} is not silently truncated to the minute. */
    private static String time(LocalTime t) {
        return "java.time.LocalTime.of(%d, %d, %d)".formatted(t.getHour(), t.getMinute(), t.getSecond());
    }

    /** Through {@link WireText#precision}, which is where the clamping to what the record accepts lives. */
    private static String precision(String wire) {
        var p = WireText.precision(wire);
        return "new Precision(%s, %d, %d)".formatted(Double.toString(p.deltaE()), p.minArea(), p.minCount());
    }

    private static String point(Point p) {
        return "new Point(%d, %d)".formatted(p.x(), p.y());
    }

    private static String size(Size s) {
        return "new Size(%d, %d)".formatted(s.width(), s.height());
    }

    private static String rect(Rect r) {
        return "new Rect(%d, %d, %d, %d)".formatted(r.x(), r.y(), r.width(), r.height());
    }

    /** A Java string literal. Kept here rather than shared, because what is legal differs per position. */
    static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }

    private static String escapeChar(char c) {
        return switch (c) {
            case '\'' -> "\\'";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> String.valueOf(c);
        };
    }
}
