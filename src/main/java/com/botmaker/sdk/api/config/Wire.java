package com.botmaker.sdk.api.config;

import com.botmaker.plugin.api.meta.ReplacedBy;
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
 * What this bot's own settings say. <b>Replaced by {@link Settings} on 2026-09-09, and kept.</b>
 *
 * <p>Every member below still answers exactly what it always did — this class is not going anywhere, and no
 * existing bot stops compiling. What changed is that the eighteen typed readers here could only ever read the
 * SDK's own value types: a value belonging to some other plugin had no reader, and {@link #one(String)}'s own
 * javadoc admitted it as <i>"the escape hatch for a type this class has no reader for"</i>. Passing the type
 * in as an argument makes the set open, so {@link Settings} has two readers where this has eighteen and
 * serves every plugin rather than one.
 *
 * <pre>{@code
 * Wire.whole("minHealth")          ->  Settings.load("minHealth", int.class)
 * Wire.duration("rest")            ->  Settings.load("rest", Duration.class)
 * Wire.many("zones")               ->  Settings.loadAll("zones", Rect.class)   // typed, now
 * Wire.image("ore")                ->  Images.named("ore")                     // a file, not a variable
 * }</pre>
 *
 * <p><b>Catalogued but not offered.</b> The {@code @Palette} mark stays so Studio still recognises the name —
 * an existing bot's {@code Wire} import must resolve, and the migrator needs the pointers below to redirect
 * it. {@code @Hidden} keeps it out of the menus, because nothing new should be written against it.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "🎛", order = 37)
@Hidden("replaced by Settings, which reads every plugin's value types rather than only the SDK's")
@Deprecated(forRemoval = false)
@ReplacedBy(value = "com.botmaker.sdk.api.config.Settings",
        note = "Settings.load(name, T.class) replaces the typed readers; Images.named(file) replaces image()")
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
    @Deprecated(forRemoval = false)
    @ReplacedBy("com.botmaker.sdk.api.config.Settings#enabled")
    public static boolean enabled(String activity) {
        return ProjectData.current().enabled(activity);
    }

    // ---- the stored text --------------------------------------------------------------------------------

    /** The named variable's stored text, exactly as the file holds it, or {@code ""}. */
    @Deprecated(forRemoval = false)
    @ReplacedBy("com.botmaker.sdk.api.config.Settings#one")
    public static String one(String name) {
        return ProjectData.current().value(name);
    }

    /** Every stored value of the named variable — one element for a plain value, several for a list. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#many",
            note = "Settings.loadAll(name, T.class) reads the same list already typed")
    public static List<String> many(String name) {
        return ProjectData.current().values(name);
    }

    /** Whether the file declares this name at all. */
    @Deprecated(forRemoval = false)
    @ReplacedBy("com.botmaker.sdk.api.config.Settings#declares")
    public static boolean declares(String name) {
        return ProjectData.current().declares(name);
    }

    // ---- the typed readers ------------------------------------------------------------------------------
    //
    // One per type the SDK registers in the project's value vocabulary. They are the list that stopped being
    // extensible: `Settings.load(name, T.class)` asks whichever plugin introduced T, through the
    // ValueGrammar that plugin ships. The SDK's own is `internal.config.SdkGrammar`, and it wraps exactly the
    // WireText calls below, so a redirected call reads the same text the same way.

    /** Text, or {@code ""}. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, String.class)")
    public static String text(String name) {
        return WireText.text(one(name));
    }

    /** A yes/no value, or {@code false}. Not {@link #enabled}, which asks about an activity. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, boolean.class)")
    public static boolean flag(String name) {
        return WireText.flag(one(name));
    }

    /** A whole number, or {@code 0}. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, int.class)")
    public static int whole(String name) {
        return WireText.whole(one(name));
    }

    /** A decimal number, or {@code 0}. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, double.class)")
    public static double decimal(String name) {
        return WireText.decimal(one(name));
    }

    /** The first character, or {@code 'a'}. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, char.class)")
    public static char letter(String name) {
        return WireText.letter(one(name));
    }

    /** An ISO date, or 2000-01-01. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, LocalDate.class)")
    public static LocalDate date(String name) {
        return WireText.date(one(name));
    }

    /** A time of day, or midnight. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, LocalTime.class)")
    public static LocalTime time(String name) {
        return WireText.time(one(name));
    }

    /** A length of time, or zero. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, Duration.class)")
    public static Duration duration(String name) {
        return WireText.duration(one(name));
    }

    /** A colour, or white. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, Color.class)")
    public static Color color(String name) {
        return WireText.color(one(name));
    }

    /** A named picture from this project's images folder. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load",
            note = "Settings.load(name, ImageTemplate.class) — still a variable, not a file name")
    public static ImageTemplate template(String name) {
        return WireText.template(one(name));
    }

    /**
     * A picture from this project's {@code images/} folder, <b>by its file name</b> —
     * {@code Wire.image("ore")} is {@code images/ore.png}.
     *
     * <p>Moved to {@code com.botmaker.sdk.api.vision.Images} on 2026-09-09, which is where a picture belongs:
     * everything else on this class reads a <em>variable</em>, and this reads a file.
     */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.vision.Images#named",
            note = "a file name, not a variable — the one member here that is not a settings read")
    public static ImageTemplate image(String baseName) {
        return WireText.template(baseName == null ? "" : baseName);
    }

    /** A match precision, or the default one. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, Precision.class)")
    public static Precision precision(String name) {
        return WireText.precision(one(name));
    }

    /** A point, or the origin. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, Point.class)")
    public static Point point(String name) {
        return WireText.point(one(name));
    }

    /** A size, or zero by zero. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, Size.class)")
    public static Size size(String name) {
        return WireText.size(one(name));
    }

    /** A rectangle, or an empty one at the origin. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, Rect.class)")
    public static Rect area(String name) {
        return WireText.area(one(name));
    }

    /** A direction, or the first one. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, Direction.class)")
    public static Direction direction(String name) {
        return WireText.direction(one(name));
    }

    /** A keyboard key, or the first one. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load", note = "Settings.load(name, Key.class)")
    public static Key key(String name) {
        return WireText.key(one(name));
    }

    /** A mouse button, or the first one. */
    @Deprecated(forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.config.Settings#load",
            note = "Settings.load(name, MouseButton.class)")
    public static MouseButton mouseButton(String name) {
        return WireText.mouseButton(one(name));
    }

    /** The names of every variable this bot's configuration declares. */
    @Hidden("a bot reads its settings by name; enumerating them is a debugging move")
    @Deprecated(forRemoval = false)
    @ReplacedBy("com.botmaker.sdk.api.config.Settings#names")
    public static List<String> names() {
        return ProjectData.current().variables();
    }
}
