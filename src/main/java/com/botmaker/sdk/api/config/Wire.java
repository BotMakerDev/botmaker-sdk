package com.botmaker.sdk.api.config;

import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.meta.Since;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.internal.config.ConfigStore;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * What a stored parameter's text means. One reader per type, and the file it reads from.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Every value the Parameters screen stores is <b>text</b> — see
 * {@link com.botmaker.sdk.internal.config.ConfigStore} for why — so something has to turn {@code "1h30m"}
 * back into a {@link Duration} at startup. Studio used to write that something into the generated
 * {@code Activities} class: seventeen parser bodies, held as Java source inside Java strings, emitted only
 * for the types a project happened to use. They were untestable by construction, and the duration one was a
 * hand-kept copy of the editor's own parser with a comment asking the next reader to diff the two by eye.
 *
 * <p>They are compiled methods now, and the editor calls the same ones. There is one grammar per type
 * because there is one implementation per type.
 *
 * <h2>Every reader is total</h2>
 *
 * <p>Nothing here throws and nothing returns {@code null}. A number that will not parse, a choice that is no
 * longer offered, a duration in a unit nobody knows: each answers the type's default. <b>A bot never fails to
 * start because of its own configuration file.</b> That is also why {@link #precision} clamps — its record's
 * constructor rejects a negative tolerance, and a stored value must never be able to reach it.
 *
 * <h2>The shape of the calls</h2>
 *
 * <p>Everything is a {@code static} call on this type, for the reason {@link com.botmaker.sdk.api.flow.FlowGraph}
 * gives at length: Studio can mechanically repair a generated file across an SDK rename only for a moved type
 * and a moved static member. A generated field reads
 *
 * <pre>{@code
 * public static final Duration REST = Wire.duration(Wire.one("REST"));
 * public static final List<Key> HOTKEYS = Wire.many("HOTKEYS", Wire::key);
 * }</pre>
 *
 * <p>{@link #many} takes the very method reference the single-valued form would have called, so an item of a
 * list is read exactly the way a lone value is.
 */
@Since("1.1.0")
public final class Wire {

    /**
     * Where a bot's image templates sit, relative to the project root. Studio's {@code TemplateConstants}
     * names the same directory — it is the one that puts the files there.
     */
    @Since("1.1.0")
    public static final String IMAGE_PREFIX = "src/main/resources/images/";

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;

    private Wire() {}

    // ---- the file -------------------------------------------------------------------------------------

    /** The text stored for {@code name}, or {@code ""} when the file has nothing to say about it. */
    @Since("1.1.0")
    public static String one(String name) {
        return ConfigStore.one(name);
    }

    /**
     * Every item stored for {@code name}, each read by {@code of} — the same reader a single value of that
     * type goes through.
     */
    @Since("1.1.0")
    public static <T> List<T> many(String name, Function<String, T> of) {
        List<T> out = new ArrayList<>();
        for (String item : ConfigStore.all(name)) out.add(of.apply(item));
        return List.copyOf(out);
    }

    // ---- one reader per type --------------------------------------------------------------------------

    /** Text, exactly as stored — not trimmed, because a trailing space may be the point. */
    @Since("1.1.0")
    public static String text(String stored) {
        return stored == null ? "" : stored;
    }

    /** A tick box. Anything that is not {@code "true"} is false. */
    @Since("1.1.0")
    public static boolean flag(String stored) {
        return Boolean.parseBoolean(trim(stored));
    }

    /** A whole number, rounded from what was stored so a hand-edited {@code "3.0"} still reads as 3. */
    @Since("1.1.0")
    public static int whole(String stored) {
        return (int) Math.rint(number(stored, 0));
    }

    /** A decimal number; 0.0 when unreadable. */
    @Since("1.1.0")
    public static double decimal(String stored) {
        return number(stored, 0);
    }

    /** The first character, or {@code 'a'} when nothing was stored. */
    @Since("1.1.0")
    public static char letter(String stored) {
        return stored == null || stored.isEmpty() ? 'a' : stored.charAt(0);
    }

    /** An ISO date ({@code 2026-08-24}); 2000-01-01 when unreadable. */
    @Since("1.1.0")
    public static LocalDate date(String stored) {
        try {
            return LocalDate.parse(trim(stored));
        } catch (RuntimeException e) {
            return LocalDate.of(2000, 1, 1);
        }
    }

    /** An ISO time of day ({@code 07:30}); midnight when unreadable. */
    @Since("1.1.0")
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
     * <p>Studio spells the same value back out with its {@code DurationWire.format}, which emits one
     * canonical form — so a typed {@code "90 s"} is stored as {@code "1m30s"} and a diff never churns on
     * spacing. Reading is here; only the spelling is Studio's.
     */
    @Since("1.1.0")
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

    /** A colour as {@code #RRGGBB}; white when unreadable. */
    @Since("1.1.0")
    public static Color color(String stored) {
        try {
            return Color.decode(trim(stored));
        } catch (RuntimeException e) {
            return Color.WHITE;
        }
    }

    /** The image template of that name, from the project's own {@code images/} directory. */
    @Since("1.1.0")
    public static ImageTemplate template(String stored) {
        return new ImageTemplate(IMAGE_PREFIX + trim(stored) + ".png");
    }

    /** A key; the enum's first constant when the name is not one it has. */
    @Since("1.1.0")
    public static Key key(String stored) {
        return constant(Key.class, stored, Key.values()[0]);
    }

    /** A mouse button; the enum's first constant when the name is not one it has. */
    @Since("1.1.0")
    public static MouseButton mouseButton(String stored) {
        return constant(MouseButton.class, stored, MouseButton.values()[0]);
    }

    /** A direction; the enum's first constant when the name is not one it has. */
    @Since("1.1.0")
    public static Direction direction(String stored) {
        return constant(Direction.class, stored, Direction.values()[0]);
    }

    /**
     * A colour tolerance, stored as {@code deltaE,minArea,minCount}.
     *
     * <p>Not {@link #constant} despite looking like the enums above: {@link Precision} is a record. Each
     * component is clamped to what its constructor accepts, which is what keeps a hand-edited file from
     * throwing before the bot's first line runs.
     */
    @Since("1.1.0")
    public static Precision precision(String stored) {
        String[] parts = trim(stored).split(",");
        double deltaE = parts.length > 0 ? number(parts[0], 12.0) : 12.0;
        int[] n = ints(stored, 3);
        return new Precision(Math.max(0.0, deltaE), Math.max(1, n[1]), Math.max(0, n[2]));
    }

    /** A point, stored as {@code x,y}. A missing or unreadable component is 0. */
    @Since("1.1.0")
    public static Point point(String stored) {
        int[] n = ints(stored, 2);
        return new Point(n[0], n[1]);
    }

    /** A size, stored as {@code width,height}. A missing or unreadable component is 0. */
    @Since("1.1.0")
    public static Size size(String stored) {
        int[] n = ints(stored, 2);
        return new Size(n[0], n[1]);
    }

    /** A rectangle, stored as {@code x,y,width,height}. A missing or unreadable component is 0. */
    @Since("1.1.0")
    public static Rect area(String stored) {
        int[] n = ints(stored, 4);
        return new Rect(n[0], n[1], n[2], n[3]);
    }

    // ---- shared parsing -------------------------------------------------------------------------------

    private static <E extends Enum<E>> E constant(Class<E> type, String stored, E fallback) {
        try {
            return Enum.valueOf(type, trim(stored).toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** {@code count} comma-separated whole numbers; anything missing or unreadable is 0. */
    private static int[] ints(String stored, int count) {
        int[] out = new int[count];
        String[] parts = trim(stored).split(",");
        for (int i = 0; i < count && i < parts.length; i++) {
            out[i] = (int) Math.rint(number(parts[i], 0));
        }
        return out;
    }

    private static double number(String stored, double fallback) {
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
