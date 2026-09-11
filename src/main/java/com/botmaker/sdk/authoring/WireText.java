package com.botmaker.sdk.authoring;

import com.botmaker.plugin.basics.values.JdkText;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.Precision;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

/**
 * What a stored value's text means. One reader per {@code ValueType}, and the grammar every one of them is
 * written in.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>A variable's stored value is text — one shape on disk, one reader, one writer. Something has to turn
 * {@code "1h30m"} into a {@link Duration}, and this is where every such answer lives, once per type.
 *
 * <p><b>Two readers ask, and that is the reason it is public.</b> The <em>editor</em> needs it — a Parameters
 * dialog showing a duration field has to read {@code "1h30m"} — and so does a <em>running bot</em>, through
 * {@code com.botmaker.plugin.basics.store.Settings}, which reaches these readers as a
 * {@code com.botmaker.plugin.basics.store.ValueGrammar} registered by {@code internal.config.SdkGrammar}. One grammar per type means one implementation per type,
 * called from both sides. That is the settlement the old {@code Wire} reached after the parsers had been
 * Java-source-inside-Java-strings, and it survives the class.
 *
 * <p><b>A paragraph here claimed the parsing "happens here now, at generation time, and the emitter writes
 * {@code java.time.Duration.ofMillis(5400000L)}" — deleted 2026-09-07, because there is no emitter.</b>
 * {@code SourceEmitter} was deleted with the inversion (2026-08-29 to 2026-09-02); Studio composes a bot's
 * one starting file itself and generates no field per variable. So a bot reads its own text at run time
 * again, which is what {@code Wire} does, and {@code LiteralWriter}'s initializer path — the half that wrote
 * a parsed value into source — has no live caller left. Nothing about the grammar changed; only the claim
 * about who runs it and when.
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
 *
 * <h2>Nine of these readers are plugin #2's now (2026-09-09)</h2>
 *
 * <p>{@link #text}, {@link #flag}, {@link #whole}, {@link #decimal}, {@link #letter}, {@link #color},
 * {@link #date}, {@link #time}, {@link #duration} and the two spellers beside them are
 * {@link JdkText}'s, in {@code botmaker-plugin-basics}, and every one here <b>delegates</b>. Nothing about a
 * whole number or a time of day is about automating a game: they were the SDK's only because the SDK was
 * written first, which made a project having a duration variable plugin #1's privilege.
 *
 * <p><b>They are kept here rather than deleted</b>, and not merely out of politeness to callers. The reason
 * given here used to be that {@code api.config.Wire} called straight through them; that class was deleted on
 * 2026-09-11 and the better reason is the one that outlives it — this class is the SDK's own grammar, reached
 * by {@code internal.config.SdkGrammar} and by the editor's codecs, and there is still exactly one
 * implementation of it. Two readers of one file is the standing risk, and a delegation cannot drift where a
 * copy would. Never-delete keeps them public regardless: they are {@code authoring}, not {@code api.*}, but
 * a plugin author's code may already name them.
 */
public final class WireText {

    /**
     * Where a bot's image templates sit, relative to the project root. The editor's own template manager puts
     * the files there; this is the half of that agreement every reader needs, so that an
     * {@code IMAGE_TEMPLATE} value spelled {@code "ore"} in the file resolves to
     * {@code src/main/resources/images/ore.png}.
     */
    public static final String IMAGE_PREFIX = "src/main/resources/images/";

    private WireText() {}

    /** Text, exactly as stored — not trimmed, because a trailing space may be the point. */
    public static String text(String stored) {
        return JdkText.text(stored);
    }

    /** A tick box. Anything that is not {@code "true"} is false. */
    public static boolean flag(String stored) {
        return JdkText.flag(stored);
    }

    /** A whole number, rounded from what was stored so a hand-edited {@code "3.0"} still reads as 3. */
    public static int whole(String stored) {
        return JdkText.whole(stored);
    }

    /** A decimal number; 0.0 when unreadable. */
    public static double decimal(String stored) {
        return JdkText.decimal(stored);
    }

    /** The first character, or {@code 'a'} when nothing was stored. */
    public static char letter(String stored) {
        return JdkText.letter(stored);
    }

    /** An ISO date ({@code 2026-08-24}); 2000-01-01 when unreadable. */
    public static LocalDate date(String stored) {
        return JdkText.date(stored);
    }

    /** An ISO time of day ({@code 07:30}); midnight when unreadable. */
    public static LocalTime time(String stored) {
        return JdkText.time(stored);
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
        return JdkText.duration(stored);
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
        return JdkText.spellDuration(millis);
    }

    /**
     * A colour as {@code #RRGGBB}; white when unreadable.
     *
     * <p>The hash is optional, because a person typing a colour into a field routinely leaves it off and
     * {@link Color#decode} treats a bare {@code 1a2b3c} as a decimal number rather than as hex — which is
     * not a rejection the user can see the reason for. Six hex digits with nothing in front of them can
     * only have been meant one way.
     */
    public static Color color(String stored) {
        return JdkText.color(stored);
    }

    /** The image template of that name, from the project's own {@code images/} directory. */
    public static ImageTemplate template(String stored) {
        return new ImageTemplate(templatePath(stored));
    }

    /** The project-relative path an {@code IMAGE_TEMPLATE} value names. */
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

    // ---- the writers ------------------------------------------------------------------------------------
    //
    // The other half of five of the readers above, and here for the reason spellDuration is here: one
    // grammar, so a value written by the editor and a value read by a bot cannot mean two things. They are
    // deliberately *not* the Java literals — those are `SdkValueTypes`' business and describe a source file,
    // while these describe the stored text. Every one is exact: `parse(spell(v))` equals `v`.

    /** A colour as {@code #RRGGBB}, the spelling {@link #color} reads back. */
    public static String spellColor(Color value) {
        return JdkText.spellColor(value);
    }

    /** A precision as {@code deltaE,minArea,minCount}. */
    public static String spellPrecision(Precision value) {
        return value.deltaE() + "," + value.minArea() + "," + value.minCount();
    }

    /** A point as {@code x,y}. */
    public static String spellPoint(Point value) {
        return value.x() + "," + value.y();
    }

    /** A size as {@code width,height}. */
    public static String spellSize(Size value) {
        return value.width() + "," + value.height();
    }

    /** A rectangle as {@code x,y,width,height}. */
    public static String spellArea(Rect value) {
        return value.x() + "," + value.y() + "," + value.width() + "," + value.height();
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
