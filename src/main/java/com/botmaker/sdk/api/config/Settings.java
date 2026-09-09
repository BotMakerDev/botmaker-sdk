package com.botmaker.sdk.api.config;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

import java.util.List;

/**
 * What this bot's own settings say — the values set in the editor, read back at run time by the name they
 * were given.
 *
 * <pre>{@code
 * if (Settings.load("minHealth", int.class) < 20) GoHome.INSTANCE.execute();
 * ImageFinder.find(Settings.load("healthBar", ImageTemplate.class));
 * for (Rect zone : Settings.loadAll("zones", Rect.class)) { ... }
 * if (Settings.enabled("Mining")) { ... }
 * }</pre>
 *
 * <h2>Two methods, because the type is an argument</h2>
 *
 * <p>This replaces {@link Wire}, which had eighteen readers — {@code whole}, {@code decimal}, {@code date},
 * {@code area} — one per type the SDK registers. That list could only ever be the SDK's: a value type
 * belonging to some other plugin had no reader and no way to get one, which {@code Wire.one(String)}'s own
 * javadoc admitted as <i>"the escape hatch for a type this class has no reader for"</i>. Passing the type in
 * makes the set open: whichever plugin introduced {@code Rect} — or {@code Channel}, or anything else —
 * ships the grammar that reads it, and the call site is the same shape either way.
 *
 * <p>Both {@code int.class} and {@code Integer.class} resolve, so a bot writes whichever its field is.
 *
 * <h2>Nothing here throws over a file, and that is still the whole contract</h2>
 *
 * <p>A missing file, a missing name, a name declared as some other type and text that will not parse all
 * answer that type's own fallback — {@code 0}, {@code ""}, {@code false}, a zero {@code Duration}. <b>A bot
 * never fails to start because of its own configuration file.</b> A misspelled name is still not a compile
 * error: {@code Settings.load("minHelath", int.class)} compiles and answers {@code 0}. What is new is that
 * the call now <em>states</em> the type it expected, so the editor can compare that against what the
 * variable was declared as and say so — which {@code Wire.whole("minHelath")} gave it no way to do.
 *
 * <p><b>One thing does throw</b>, and it is not about the file: a type no grammar on the classpath claims.
 * That is a bot compiled against a plugin it does not run with — a packaging mistake, with no value to fall
 * back to and nothing a default could honestly stand in for.
 *
 * <h2>Names</h2>
 *
 * <p>A <em>variable</em> is looked up by the name it has in the editor. An <em>activity</em> is looked up by
 * its own name through {@link #enabled(String)}, which is a different question and a different list: whether
 * an activity runs is a property of the activity, not a variable somebody declared.
 *
 * <h2>Where the work actually happens</h2>
 *
 * <p>{@code com.botmaker.plugin.toolkit.config.Settings}, and this class is a facade over it holding no
 * logic. Of the five stages in a value's life — declaring, editing, writing, reading it in an editor,
 * reading it in a running bot — the first four are already plugin-general, because they are contract types.
 * The fifth could not join them, because <b>a bot's classpath does not have the contract on it</b>: a plugin
 * declares {@code botmaker-studio-api} {@code provided} on purpose. The toolkit is what a plugin compiles
 * against at {@code compile} scope, so it is the one module that travels with a plugin all the way onto the
 * classpath of the bots that use it.
 *
 * <p>The facade exists so a bot spells an <em>SDK</em> name for an SDK concept, and so this class can carry
 * a {@code @Palette} mark — the toolkit is not a plugin and has no catalog. A bot may call the toolkit's
 * {@code Settings} directly; it will get the same answers.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "🎛", order = 37)
public final class Settings {

    private Settings() {
    }

    // ---- the two readers --------------------------------------------------------------------------------

    /**
     * The named variable as a {@code type}, or that type's fallback.
     *
     * <p>A list variable answers its first element, which is what {@link Wire}'s typed readers did and what a
     * bot asking for one value out of a list means.
     *
     * @throws IllegalArgumentException when no grammar on the classpath reads {@code type} — see the class
     *                                  javadoc for why that is the one failure that is not a fallback
     */
    public static <T> T load(String name, Class<T> type) {
        return com.botmaker.plugin.toolkit.config.Settings.load(name, type);
    }

    /**
     * Every stored value of the named variable, as {@code type}s, in the order the file holds them.
     *
     * <p>An undeclared name and a declared-but-empty list both answer an empty list; {@link #declares} is
     * what tells the two apart. An element that will not parse is the type's fallback rather than a gap, so
     * the result is always the same length as what the editor stored.
     */
    public static <T> List<T> loadAll(String name, Class<T> type) {
        return com.botmaker.plugin.toolkit.config.Settings.loadAll(name, type);
    }

    // ---- the questions that need no grammar -------------------------------------------------------------

    /**
     * Whether the named activity is switched on in the editor — what a generated activity's
     * {@code isEnabled()} answers with.
     *
     * <p>An activity nothing knows about reads {@code false}: a bot that silently ran an activity its own
     * configuration had never heard of would be worse than one that quietly skips it.
     */
    public static boolean enabled(String activity) {
        return com.botmaker.plugin.toolkit.config.Settings.enabled(activity);
    }

    /**
     * Whether the file declares this name at all.
     *
     * <p>The question a fallback cannot answer: an unset text variable and a name nobody ever declared both
     * read as {@code ""}, and only one of the two is a mistake.
     */
    public static boolean declares(String name) {
        return com.botmaker.plugin.toolkit.config.Settings.declares(name);
    }

    // ---- the stored text --------------------------------------------------------------------------------

    /**
     * The named variable's stored text, exactly as the file holds it, or {@code ""}.
     *
     * <p>Kept as the honest bottom of the stack — a value written by a plugin whose grammar this bot does not
     * carry is still text, and reading it is better than a throw. It is not the escape hatch it was on
     * {@link Wire}: {@link #load} answers any type some plugin on the classpath claims, so needing this means
     * the plugin is genuinely absent.
     */
    public static String one(String name) {
        return com.botmaker.plugin.toolkit.config.ProjectValues.current().one(name);
    }

    /** Every stored value of the named variable as text — one element for a plain value, several for a list. */
    public static List<String> many(String name) {
        return com.botmaker.plugin.toolkit.config.ProjectValues.current().many(name);
    }

    /**
     * The names of every variable this bot's configuration declares.
     *
     * <p>Not offered in the menus: a bot enumerating its own settings is a debugging move, and the palette
     * proposing it would suggest that reading them by name is the exception rather than the point.
     */
    @Hidden("a bot reads its settings by name; enumerating them is a debugging move")
    public static List<String> names() {
        return com.botmaker.plugin.toolkit.config.ProjectValues.current().variables();
    }
}
