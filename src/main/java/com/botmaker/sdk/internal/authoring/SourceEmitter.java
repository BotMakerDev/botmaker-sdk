package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.ParameterGroup;
import com.botmaker.sdk.authoring.ActivityModel;
import com.botmaker.sdk.authoring.ProjectModel;
import com.botmaker.sdk.authoring.ProjectSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every {@code .java} file a bot project is made of, as text.
 *
 * <h2>What used to be here</h2>
 *
 * <p>Nine files, five of which were <em>regenerated</em>: {@code Activities} (one {@code boolean} per
 * activity), {@code Parameters} (one baked literal per variable), {@code Templates} (one path constant per
 * image), {@code ActivityRegistry} (one typed singleton per activity) and {@code FlowDriver} (the drawn flow
 * as a {@code FlowGraph.of(…)} table). All five are gone, and none of them was replaced by a different
 * generator — <b>they became reads</b>: {@code Wire.enabled}, {@code Wire.number} and friends,
 * {@code Wire.image}, and {@code FlowGraph.load}. A file whose contents follow from project data is data.
 *
 * <p>That is what makes deleting this class possible at all. The four files left are <b>seeds</b> — an entry
 * point, {@code GoHome}, {@code Popups} and one stub per activity — and a seed is a real class the plugin
 * ships and compiles in its own build, not a string assembled here. Building them as text is the last thing
 * this file does, and it does it only until the seeds themselves land.
 *
 * <h2>What was traded</h2>
 *
 * <p>Typed names. {@code Parameters.minHealth} was an {@code int} field and a misspelling was a compile
 * error; {@code Wire.whole("minHealth")} is a lookup that answers {@code 0}. That cost is real and was
 * accepted deliberately: the compile check it bought applied only to files nobody hand-writes, and it was
 * paid for with a generator no second plugin could ever have used.
 *
 * <p>The one place a user does write a name by hand — {@code return Outcome.BAG_FULL;} — keeps its enum, and
 * therefore keeps its compile check. That is not an oversight in the trade; it is the point of it.
 */
public final class SourceEmitter {

    private static final String SRC = "src/main/java/";

    /**
     * The one parameter group the SDK owns.
     *
     * <p>Its id is {@link ParameterGroup#DEFAULT_ID} — blank — which is what makes every project written
     * before groups existed read back as this plugin's without a migration. It survives the deletion of the
     * generated {@code Parameters} class because a group was never really about that file: it is how the
     * editor's Parameters dialog decides which plugin a variable belongs to.
     */
    public static final ParameterGroup SDK_PARAMETERS =
            new ParameterGroup(ParameterGroup.DEFAULT_ID, "Parameters", "Parameters");

    private SourceEmitter() {}

    // ---- the file set -----------------------------------------------------------------------------------

    /**
     * Every file the project is created with, keyed by its path relative to the project root.
     *
     * <p>All of them are seeds now, so this is also the complete list of files BotMaker ever writes: an
     * {@link ProjectSpec.Kind#EMPTY} project gets an entry point that prints a greeting, and a game bot gets
     * an entry point, {@code GoHome}, {@code Popups} and one stub per activity it already has.
     *
     * <p>{@code Templates} used to be here for an empty project too, on the argument that a hand-written
     * import of it must keep compiling and the first vision block dropped must name a constant that resolves.
     * Neither survives its deletion: {@code Wire.image("ore")} names the file rather than a constant, so
     * there is nothing to resolve against and nothing to keep in step.
     */
    public static Map<String, String> sources(ProjectSpec spec, ProjectModel model) {
        Map<String, String> out = new LinkedHashMap<>();
        if (spec.kind() != ProjectSpec.Kind.GAME_BOT) {
            out.put(path(spec, spec.entryClassName()), emptyEntryPoint(spec));
            return out;
        }

        out.put(path(spec, spec.entryClassName()), entryPoint(spec));
        out.put(path(spec, "GoHome"), goHome(spec));
        out.put(path(spec, "Popups"), popups(spec));
        for (ActivityModel activity : model.activities()) out.putAll(activityStub(spec, activity));
        return out;
    }

    /** One activity's editable stub, as a single-entry map so a caller commits it like any other file. */
    public static Map<String, String> activityStub(ProjectSpec spec, ActivityModel activity) {
        return Map.of(SRC + packagePath(spec) + "/activities/" + activity.name() + ".java",
                stub(spec, activity));
    }

    // ---- paths ------------------------------------------------------------------------------------------

    private static String path(ProjectSpec spec, String className) {
        return SRC + packagePath(spec) + "/" + className + ".java";
    }

    private static String packagePath(ProjectSpec spec) {
        return spec.packageName().replace('.', '/');
    }

    private static String header(ProjectSpec spec) {
        return "package " + spec.packageName() + ";\n\n";
    }

    // ---- the files that are written once ----------------------------------------------------------------

    /**
     * An empty project's entry point: a {@code main} that prints a greeting and nothing else. SEED.
     *
     * <p>It is here rather than in the editor for one reason — it names {@code BotMaker.print}, an SDK
     * element, so the version that knows whether that spelling exists is the version generating it. That it
     * is only four lines does not change which repository owns them.
     */
    static String emptyEntryPoint(ProjectSpec spec) {
        return header(spec) + """
                import com.botmaker.sdk.api.util.BotMaker;

                public class %s {
                    public static void main(String[] args) {
                        BotMaker.print("Hello from %s!");
                    }
                }
                """.formatted(spec.entryClassName(), spec.entryClassName());
    }

    /**
     * The bot's entry point. SEED — written when the project is created, and never again.
     *
     * <p>It is the one file that names the project's own package without being able to spell it, which is why
     * {@code FlowGraph.run} takes {@code %s.class}: an activity's class is
     * {@code <this package>.activities.<Name>}, and this class is what tells the loader where "this package"
     * is. Renaming it, or the package, changes nothing.
     */
    static String entryPoint(ProjectSpec spec) {
        return header(spec) + """
                import com.botmaker.sdk.api.bot.Bot;
                import com.botmaker.sdk.api.bot.PopupGuard;
                import com.botmaker.sdk.api.flow.FlowGraph;

                /**
                 * The bot's entry point.
                 *
                 * <p>SEED — BotMaker writes it once, when the project is created, and never again. Everything
                 * below is yours from that moment on.
                 */
                public class %1$s {

                    public static void main(String[] args) {
                        // Click delays, match confidence, and whether to drive the real mouse and keyboard (which
                        // is what a game needs — it ignores the quiet background clicks BotMaker sends by
                        // default) are project settings, applied by the SDK before the first click. Edit them in
                        // the Input & Clicks dialog.

                        // Runs Popups.run() before every vision step, so a daily reward or a mail popup is
                        // dismissed instead of hiding whatever the next find was looking for. Popups.java is
                        // yours: it decides which templates mean "a popup is up", and how to close each one.
                        PopupGuard.install(Popups.INSTANCE::execute);

                        // Walks the Activity Flow you drew, read from activities.json — which activities run,
                        // where each outcome leads, and the two limits that stop a loop running away. Nothing
                        // about it is generated into this project, so redrawing the canvas changes no Java.
                        // %1$s.class is only how the SDK finds this project's package.
                        Bot.start(() -> FlowGraph.run(%1$s.class, GoHome.INSTANCE::execute),
                                GoHome.INSTANCE::execute);
                    }
                }
                """.formatted(spec.entryClassName());
    }

    /** Navigate back to a known-good "home" screen. SEED. */
    static String goHome(ProjectSpec spec) {
        return header(spec) + """
                import com.botmaker.sdk.api.bot.Activity;

                /**
                 * Navigate back to a known-good "home" screen. Called by the supervisor before it relaunches the
                 * game during recovery, and before any activity whose "go home first" tick is on. Fill in
                 * {@link #run()} for your game, e.g.:
                 * <pre>
                 *   while (!ImageFinder.find(home)) {
                 *       ImageClicker.click(back);
                 *       Wait.seconds(1);
                 *   }
                 * </pre>
                 *
                 * <p>SEED — BotMaker writes it once, when the project is created, and never again.
                 */
                public class GoHome extends Activity<GoHome.Outcome> {

                    /** The one instance; referenced by the entry point. */
                    public static final GoHome INSTANCE = new GoHome();

                    /** GoHome reports nothing to route on — it is called directly, not wired into the flow. */
                    public enum Outcome { NEXT }

                    @Override
                    public boolean isEnabled() {
                        return true;   // recovery hook — always available
                    }

                    @Override
                    public Outcome run() {
                        // TODO: navigate back to your game's home screen.
                        return Outcome.NEXT;
                    }
                }
                """;
    }

    /** Dismiss whatever the game has interrupted us with. SEED. */
    static String popups(ProjectSpec spec) {
        return header(spec) + """
                import com.botmaker.sdk.api.bot.Activity;
                import com.botmaker.sdk.api.vision.ImageFinder;
                import com.botmaker.sdk.api.vision.ImageTemplateGroup;

                /**
                 * Dismiss whatever the game has interrupted us with. BotMaker runs this before every vision step
                 * (see the {@code PopupGuard.install} line in the entry point), so no activity has to open with
                 * its own defensive dismissal code.
                 *
                 * <p>{@link #run()} already has the loop; fill in {@link #POPUPS} and the body for your game. The
                 * shape that works is "which combination is on screen", not "click anything that looks like a
                 * cross": the same close button often belongs to the screen the bot is actually working on, and a
                 * popup's body usually isn't clickable at all.
                 * <pre>
                 *   private static final ImageTemplateGroup POPUPS =
                 *           ImageTemplateGroup.of(Wire.image("mail"), Wire.image("claim_all"));
                 *
                 *   ImageFinder.whileFindAny(POPUPS, found -&gt; {
                 *       if (found.has(mail) &amp;&amp; found.has(claimAll)) ImageClicker.click(found.get(claimAll));
                 *       else if (found.has(close))                   ImageClicker.click(found.get(close));
                 *   });
                 * </pre>
                 * The loop keeps going while any popup is still up, so a reward stacked behind a mail is cleared
                 * too — and the finds inside it are not themselves guarded, so this cannot recurse.
                 *
                 * <p>Each activity has a "check for popups" tick in Project &rarr; Activity Flow; turn it off for
                 * one that works through a popup-shaped screen itself.
                 *
                 * <p>SEED — BotMaker writes it once, when the project is created, and never again.
                 */
                public class Popups extends Activity<Popups.Outcome> {

                    /** The one instance; the entry point installs it as the popup guard. */
                    public static final Popups INSTANCE = new Popups();

                    /** The popups this bot knows how to dismiss. Add your templates here; empty means "none". */
                    private static final ImageTemplateGroup POPUPS = ImageTemplateGroup.of();

                    /** Popups reports nothing to route on — it is called by the guard, not wired into the flow. */
                    public enum Outcome { NEXT }

                    @Override
                    public boolean isEnabled() {
                        return true;   // guard hook — always available
                    }

                    @Override
                    public Outcome run() {
                        ImageFinder.whileFindAny(POPUPS, found -> {
                            // TODO: click the popup this frame found — ImageClicker.click(found.get(close));
                        });
                        return Outcome.NEXT;
                    }
                }
                """;
    }

    /**
     * The initial editable stub for one activity.
     *
     * <p>SEED — with one exception: {@code Outcome}, which the flow editor keeps in step with what the canvas
     * can route on. That is a surgical edit of a file the user owns, and it belongs to the editor; what is
     * here is only the shape it starts in.
     *
     * <p>{@code isEnabled()} reads {@code Wire.enabled(name)} rather than a field on a generated
     * {@code Activities} class. The tick in the editor is the same tick and lands in the same
     * {@code activities.json}; what is gone is the file that used to restate it in Java, and with it the
     * hazard that class carried — a {@code static final boolean} folded by javac, so unticking a box made a
     * user's own {@code while} loop an unreachable statement.
     */
    static String stub(ProjectSpec spec, ActivityModel a) {
        String doc = a.description().isBlank() ? "" : "\n * <p>" + escapeDoc(a.description()) + "\n *";
        return "package " + spec.packageName() + ".activities;\n\n"
                + "import com.botmaker.sdk.api.bot.Activity;\n"
                + "import com.botmaker.sdk.api.config.Wire;\n\n"
                + ("""
                /**
                 * Activity: %1$s. Fill in {@link #run()} with how to do it — that method is the whole point of
                 * this file, and this file is yours to edit (BotMaker creates it once and never overwrites it).
                 * {@link #isEnabled()} is wired to this activity's tick in the editor and is managed for you.
                 *%3$s
                 * <p>SEED — written once, when the activity is created, and never again. The one exception is
                 * {@link Outcome}, which Project &rarr; Activity Flow keeps in step with what the canvas can
                 * route on.
                 */
                public class %1$s extends Activity<%1$s.Outcome> {

                    /**
                     * What this activity can report having happened. Return one from {@link #run()} and the flow
                     * drawn in the editor decides where each one goes — so this says what happened here, never
                     * where to go next. GENERATED from Project &rarr; Activity Flow; edit it there, not here.
                     */
                    public enum Outcome { %2$s }

                    @Override
                    public boolean isEnabled() {
                        return Wire.enabled("%1$s");
                    }

                    @Override
                    public Outcome run() {
                        // TODO: how to do %1$s
                        return Outcome.NEXT;
                    }
                }
                """).formatted(a.name(), String.join(", ", a.allOutcomes()), doc);
    }

    // ---- shared -----------------------------------------------------------------------------------------

    /** Anything a user typed has to be safe inside a comment; only one sequence can end one. */
    private static String escapeDoc(String text) {
        return text.strip().replace("*/", "*&#47;");
    }
}
