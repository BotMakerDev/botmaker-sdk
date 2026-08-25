package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.meta.Since;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.Precision;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

/**
 * What a stored value's text means. One reader per {@link ValueType}, and the grammar every one of them is
 * written in.
 *
 * <h2>Why this exists at all, when the value is now baked into the source</h2>
 *
 * <p>{@link VariableModel#value()} is text — one shape on disk, one reader, one writer. Something still has
 * to turn {@code "1h30m"} into a {@link Duration}; what changed in this release is <em>when</em>. It used to
 * happen inside the running bot, through a generated field that called a parser at startup; it happens here
 * now, at generation time, and the emitter writes the answer as {@code java.time.Duration.ofMillis(5400000L)}.
 * The maintainer's objection to the old arrangement is the whole of the reasoning: if a reader knows how to
 * turn the text into a value, the generator can do that once instead of every bot doing it on every launch.
 *
 * <p>So this class is the parsers' home, not their grave. It is public because the <em>editor</em> needs the
 * same answers — a Parameters dialog showing a duration field has to read {@code "1h30m"} too — and one
 * grammar per type means one implementation per type, called from both sides. That is the settlement the old
 * {@code Wire} reached after the parsers had been Java-source-inside-Java-strings, and it survives the class.
 *
 * <h2>Every reader is total</h2>
 *
 * <p>Nothing here throws and nothing returns {@code null}. A number that will not parse, a choice that is no
 * longer offered, a duration in a unit nobody knows: each answers the type's default. That mattered when a
 * bot read its own configuration at startup ("a bot never fails to start because of its own file") and it
 * matters for a different reason now: <b>a project must still open, and still generate, when its file says
 * something impossible.</b> A refusal here would be a project nobody can repair through the editor.
 *
 * <p>It is also why {@link #precision} clamps — {@link Precision}'s constructor rejects a negative tolerance,
 * and a hand-edited file must never be able to reach it.
 */
@Since("1.2.0")
public final class WireText {

    /**
     * Where a bot's image templates sit, relative to the project root. The editor's own template manager puts
     * the files there; this is the half of that agreement the generator needs, so that an
     * {@link ValueType#IMAGE_TEMPLATE} value spelled {@code "ore"} in the file becomes
     * {@code new ImageTemplate("src/main/resources/images/ore.png")} in source.
     */
    public static final String IMAGE_PREFIX = "src/main/resources/images/";

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;

    private WireText() {}

    /** Text, exactly as stored — not trimmed, because a trailing space may be the point. */
    public static String text(String stored) {
        return stored == null ? "" : stored;
    }

    /** A tick box. Anything that is not {@code "true"} is false. */
    public static boolean flag(String stored) {
        return Boolean.parseBoolean(trim(stored));
    }

    /** A whole number, rounded from what was stored so a hand-edited {@code "3.0"} still reads as 3. */
    public static int whole(String stored) {
        return (int) Math.rint(number(stored, 0));
    }

    /** A decimal number; 0.0 when unreadable. */
    public static double decimal(String stored) {
        return number(stored, 0);
    }

    /** The first character, or {@code 'a'} when nothing was stored. */
    public static char letter(String stored) {
        return stored == null || stored.isEmpty() ? 'a' : stored.charAt(0);
    }

    /** An ISO date ({@code 2026-08-24}); 2000-01-01 when unreadable. */
    public static LocalDate date(String stored) {
        try {
            return LocalDate.parse(trim(stored));
        } catch (RuntimeException e) {
            return LocalDate.of(2000, 1, 1);
        }
    }

    /** An ISO time of day ({@code 07:30}); midnight when unreadable. */
    public static LocalTime time(String stored) {
        try {
            return LocalTime.parse(trim(stored));
        } catch (RuntimeException e) {
            return LocalTime.MIDNIGHT;
        }
    }

    /**
     * A duration written the way a person says one: {@code 250ms}, {@code 90s}, {@code 5m}, {@code 1h30m}.
     *
     * <p>Deliberately generous — any ordering, any subset of units, spaces and case ignored, and a bare
     * number read as milliseconds so {@code "500"} still means something. Anything it cannot read at all is
     * {@link Duration#ZERO}: an unknown unit, a unit with no number in front of it, or a count so large it is
     * certainly a typo (a bot delay is not measured in weeks).
     *
     * <p>{@link #spellDuration} writes the same value back out in one canonical form, so a typed {@code "90 s"}
     * is stored as {@code "1m30s"} and a diff never churns on spacing.
     */
    public static Duration duration(String stored) {
        String s = trim(stored).toLowerCase(Locale.ROOT).replace(" ", "");
        long total = 0;
        long digits = 0;
        boolean sawDigit = false;
        boolean sawAny = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                digits = digits * 10 + (c - '0');
                if (digits > Integer.MAX_VALUE) return Duration.ZERO;
                sawDigit = true;
                continue;
            }
            if (!sawDigit) return Duration.ZERO;
            // "ms" is the only two-letter unit, and it must be checked before the bare "m" it starts with.
            if (c == 'm' && i + 1 < s.length() && s.charAt(i + 1) == 's') {
                total += digits;
                i++;
            } else if (c == 'h') {
                total += digits * HOUR;
            } else if (c == 'm') {
                total += digits * MINUTE;
            } else if (c == 's') {
                total += digits * SECOND;
            } else {
                return Duration.ZERO;
            }
            digits = 0;
            sawDigit = false;
            sawAny = true;
        }
        if (sawDigit) {
            total += digits;
            sawAny = true;
        }
        return Duration.ofMillis(sawAny ? total : 0L);
    }

    /**
     * {@code millis} in the one canonical spelling {@link #duration} reads back: the non-zero components in
     * descending order, and {@code 0s} for nothing and for anything negative.
     *
     * <p>The only writer in this class, and it is here because reading and writing one grammar in two
     * repositories is how they drift. It lived in the editor (as {@code DurationWire.format}) while the
     * editor was the only thing that ever wrote a stored value; the generator reads them now, and a stored
     * duration has to mean the same thing to both.
     *
     * <p>Durations are stored as text rather than as a number because the unit is the part a reader needs:
     * {@code 90000} in a file says nothing, and whoever wrote it had "a minute and a half" in mind.
     */
    public static String spellDuration(long millis) {
        if (millis <= 0) return "0s";
        StringBuilder out = new StringBuilder();
        long left = millis;
        left = spellUnit(out, left, HOUR, "h");
        left = spellUnit(out, left, MINUTE, "m");
        left = spellUnit(out, left, SECOND, "s");
        if (left > 0) out.append(left).append("ms");
        return out.toString();
    }

    private static long spellUnit(StringBuilder out, long left, long unit, String suffix) {
        long count = left / unit;
        if (count > 0) out.append(count).append(suffix);
        return left % unit;
    }

    /** A colour as {@code #RRGGBB}; white when unreadable. */
    public static Color color(String stored) {
        try {
            return Color.decode(trim(stored));
        } catch (RuntimeException e) {
            return Color.WHITE;
        }
    }

    /** The image template of that name, from the project's own {@code images/} directory. */
    public static ImageTemplate template(String stored) {
        return new ImageTemplate(templatePath(stored));
    }

    /** The project-relative path an {@link ValueType#IMAGE_TEMPLATE} value names. */
    public static String templatePath(String stored) {
        return IMAGE_PREFIX + trim(stored) + ".png";
    }

    /** A key; the enum's first constant when the name is not one it has. */
    public static Key key(String stored) {
        return constant(Key.class, stored, Key.values()[0]);
    }

    /** A mouse button; the enum's first constant when the name is not one it has. */
    public static MouseButton mouseButton(String stored) {
        return constant(MouseButton.class, stored, MouseButton.values()[0]);
    }

    /** A direction; the enum's first constant when the name is not one it has. */
    public static Direction direction(String stored) {
        return constant(Direction.class, stored, Direction.values()[0]);
    }

    /**
     * A colour tolerance, stored as {@code deltaE,minArea,minCount}.
     *
     * <p>Not {@link #constant} despite looking like the enums above: {@link Precision} is a record. Each
     * component is clamped to what its constructor accepts, which is what keeps a hand-edited file from
     * throwing before anything can report where the bad value is.
     */
    public static Precision precision(String stored) {
        String[] parts = trim(stored).split(",");
        double deltaE = parts.length > 0 ? number(parts[0], 12.0) : 12.0;
        int[] n = ints(stored, 3);
        return new Precision(Math.max(0.0, deltaE), Math.max(1, n[1]), Math.max(0, n[2]));
    }

    /** A point, stored as {@code x,y}. A missing or unreadable component is 0. */
    public static Point point(String stored) {
        int[] n = ints(stored, 2);
        return new Point(n[0], n[1]);
    }

    /** A size, stored as {@code width,height}. A missing or unreadable component is 0. */
    public static Size size(String stored) {
        int[] n = ints(stored, 2);
        return new Size(n[0], n[1]);
    }

    /** A rectangle, stored as {@code x,y,width,height}. A missing or unreadable component is 0. */
    public static Rect area(String stored) {
        int[] n = ints(stored, 4);
        return new Rect(n[0], n[1], n[2], n[3]);
    }

    // ---- shared parsing ---------------------------------------------------------------------------------

    private static <E extends Enum<E>> E constant(Class<E> type, String stored, E fallback) {
        try {
            return Enum.valueOf(type, trim(stored).toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** {@code count} comma-separated whole numbers; anything missing or unreadable is 0. */
    static int[] ints(String stored, int count) {
        int[] out = new int[count];
        String[] parts = trim(stored).split(",");
        for (int i = 0; i < count && i < parts.length; i++) {
            out[i] = (int) Math.rint(number(parts[i], 0));
        }
        return out;
    }

    static double number(String stored, double fallback) {
        try {
            double parsed = Double.parseDouble(trim(stored));
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String trim(String stored) {
        return stored == null ? "" : stored.trim();
    }
}
