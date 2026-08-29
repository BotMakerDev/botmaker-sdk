package com.botmaker.sdk.api.config;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.authoring.WireText;
import com.botmaker.sdk.internal.config.ProjectData;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * What this bot's own settings say — the values set in the editor, read back at run time by the name they
 * were given.
 *
 * <pre>{@code
 * if (Wire.whole("minHealth") < 20) GoHome.INSTANCE.execute();
 * ImageFinder.find(Wire.template("healthBar"));
 * }</pre>
 *
 * <h2>Why this exists rather than a generated class of constants</h2>
 *
 * <p>Because a generated file is a file its user cannot edit. A bot's parameters used to be emitted as a
 * {@code Parameters} class of {@code public static final} fields, rewritten wholesale on every change — so a
 * hand edit inside it was lost, and the file existed only to give these values a name. Reading them here
 * gives the same values the same names and costs one thing, stated plainly: <b>a misspelled name is not a
 * compile error.</b> {@code Wire.whole("minHelath")} compiles and answers {@code 0}.
 *
 * <h2>Nothing here throws, and that is the whole contract</h2>
 *
 * <p>A missing file, a missing name and a value that will not parse all fall back — {@code 0}, {@code ""},
 * {@code false}, a zero {@link Duration}, and for the richer types whatever {@link WireText} documents as its
 * own fallback. <b>A bot never fails to start because of its own configuration file.</b> Which reader is
 * right for a name is the caller's to know: nothing here consults the type the editor stored, so
 * {@code Wire.whole} over a colour answers {@code 0} rather than complaining. That rule is why the old
 * generated class baked parsed literals rather than parse calls, and it
 * survives the move because every reader below goes through {@link WireText}, which is total by construction
 * and is the same parser the editor interprets a stored value with. The editor and the running bot therefore
 * cannot disagree about what a stored string means.
 *
 * <h2>Names</h2>
 *
 * <p>A <em>variable</em> is looked up by the name it has in the editor. An <em>activity</em> is looked up by
 * its own name through {@link #enabled(String)}, which is a different question and a different list: whether
 * an activity runs is a property of the activity, not a variable somebody declared.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "🎛", order = 37)
public final class Wire {

    private Wire() {
    }

    // ---- activities -------------------------------------------------------------------------------------

    /**
     * Whether the named activity is switched on in the editor — what a generated activity's
     * {@code isEnabled()} answers with.
     *
     * <p>An activity nothing knows about reads {@code false}: a bot that silently ran an activity its own
     * configuration had never heard of would be worse than one that quietly skips it.
     */
    public static boolean enabled(String activity) {
        return ProjectData.current().enabled(activity);
    }

    // ---- the stored text --------------------------------------------------------------------------------

    /**
     * The named variable's stored text, exactly as the file holds it, or {@code ""}.
     *
     * <p>The escape hatch for a type this class has no reader for — a value some other plugin owns, say. Every
     * typed reader below is this plus one {@link WireText} call, so there is nothing here they can do that a
     * caller cannot.
     */
    public static String one(String name) {
        return ProjectData.current().value(name);
    }

    /** Every stored value of the named variable — one element for a plain value, several for a list. */
    public static List<String> many(String name) {
        return ProjectData.current().values(name);
    }

    /**
     * Whether the file declares this name at all.
     *
     * <p>The question {@code ""} cannot answer: an unset text variable and a name nobody ever declared read
     * the same through {@link #one}, and only one of the two is a mistake.
     */
    public static boolean declares(String name) {
        return ProjectData.current().declares(name);
    }

    // ---- the typed readers ------------------------------------------------------------------------------
    //
    // One per type the SDK registers in the project's value vocabulary, named after the WireText parser it
    // delegates to so the two lists can be read side by side. A type added there wants a reader here.

    /** Text, or {@code ""}. */
    public static String text(String name) {
        return WireText.text(one(name));
    }

    /** A yes/no value, or {@code false}. Not {@link #enabled}, which asks about an activity. */
    public static boolean flag(String name) {
        return WireText.flag(one(name));
    }

    /** A whole number, or {@code 0}. */
    public static int whole(String name) {
        return WireText.whole(one(name));
    }

    /** A decimal number, or {@code 0}. */
    public static double decimal(String name) {
        return WireText.decimal(one(name));
    }

    /** The first character, or {@code 'a'}. */
    public static char letter(String name) {
        return WireText.letter(one(name));
    }

    /** An ISO date, or 2000-01-01. */
    public static LocalDate date(String name) {
        return WireText.date(one(name));
    }

    /** A time of day, or midnight. */
    public static LocalTime time(String name) {
        return WireText.time(one(name));
    }

    /** A length of time, or zero. */
    public static Duration duration(String name) {
        return WireText.duration(one(name));
    }

    /** A colour, or white. */
    public static Color color(String name) {
        return WireText.color(one(name));
    }

    /** A named picture from this project's images folder. */
    public static ImageTemplate template(String name) {
        return WireText.template(one(name));
    }

    /**
     * A picture from this project's {@code images/} folder, <b>by its file name</b> —
     * {@code Wire.image("ore")} is {@code images/ore.png}.
     *
     * <p><b>Not the same question as {@link #template(String)}</b>, which reads a <em>variable</em> whose
     * value happens to be a picture. This one names a file directly, and it is what replaces the generated
     * {@code Templates} class: {@code Templates.ORE} was one {@code public static final String} per file,
     * regenerated on every capture, rename and delete.
     *
     * <p>What is given up is the same thing given up everywhere else here — {@code Templates.ORE} did not
     * compile once the file was renamed, and {@code Wire.image("ore")} does. What is bought is that a
     * project's pictures stop being a compiled artefact of the project at all, so adding one is no longer a
     * source edit.
     */
    public static ImageTemplate image(String baseName) {
        return WireText.template(baseName == null ? "" : baseName);
    }

    /** A match precision, or the default one. */
    public static Precision precision(String name) {
        return WireText.precision(one(name));
    }

    /** A point, or the origin. */
    public static Point point(String name) {
        return WireText.point(one(name));
    }

    /** A size, or zero by zero. */
    public static Size size(String name) {
        return WireText.size(one(name));
    }

    /** A rectangle, or an empty one at the origin. */
    public static Rect area(String name) {
        return WireText.area(one(name));
    }

    /** A direction, or the first one. */
    public static Direction direction(String name) {
        return WireText.direction(one(name));
    }

    /** A keyboard key, or the first one. */
    public static Key key(String name) {
        return WireText.key(one(name));
    }

    /** A mouse button, or the first one. */
    public static MouseButton mouseButton(String name) {
        return WireText.mouseButton(one(name));
    }

    /**
     * The names of every variable this bot's configuration declares.
     *
     * <p>Not offered in the menus: a bot enumerating its own settings is a debugging move, and the palette
     * proposing it would suggest that reading them by name is the exception rather than the point.
     */
    @Hidden("a bot reads its settings by name; enumerating them is a debugging move")
    public static List<String> names() {
        return ProjectData.current().variables();
    }
}
