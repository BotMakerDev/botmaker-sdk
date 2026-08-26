package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueChoice;
import com.botmaker.sdk.authoring.ActivityModel;
import com.botmaker.sdk.authoring.FlowEdgeModel;
import com.botmaker.sdk.authoring.FlowModel;
import com.botmaker.sdk.authoring.ProjectModel;
import com.botmaker.sdk.authoring.ProjectSpec;
import com.botmaker.sdk.authoring.TemplateNames;
import com.botmaker.sdk.authoring.VariableModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every {@code .java} file a bot project is made of, as text.
 *
 * <h2>Text, not templates</h2>
 *
 * <p>Until this release these files were <em>templates</em>: nine compilable Java files shipped inside the
 * SDK jar, each carrying {@code /*<STUDIO:NAME>*}{@code /} fences that the editor found and filled in. That
 * arrangement existed to let two repositories co-author one file — the SDK owning the frame, the editor
 * owning the holes — and the machinery it needed (a manifest format, a generation number per hole, a
 * committed ledger of which holes existed, and two release gates comparing the two repositories) was all in
 * service of keeping those two authors in agreement.
 *
 * <p>There is one author now. A file with one author does not need a protocol, so the templates and every
 * part of that protocol are gone, and what is left is the thing they were a way of avoiding: source built as
 * strings, here, where the file being written and the API it calls are the same build and one test can
 * compile the result.
 *
 * <h2>SEED and REGENERATED</h2>
 *
 * <p>The distinction survives the templates, because it is about the user rather than about the generator.
 * {@link #sources} emits everything, which is what creating a project needs. {@link #regenerated} emits only
 * the files that are rewritten wholesale afterwards — {@code Activities}, {@code Parameters},
 * {@code Templates}, {@code ActivityRegistry}, {@code FlowDriver}. The rest ({@code GoHome}, {@code Popups},
 * the entry point, and each activity's stub) is written once and is the user's from that moment.
 *
 * <p><b>A regenerated file loses hand edits inside it</b>, and now that a value is a literal rather than a
 * parser call, that includes editing a value in the Java instead of in the dialog. Each generated file says
 * so in its own javadoc, which is the only place a user reading the file will look.
 */
public final class SourceEmitter {

    private static final String SRC = "src/main/java/";

    /**
     * The vocabulary this emitter can write literals for — the SDK's own, and deliberately not a merged one.
     *
     * <p>The SDK writes the files the SDK owns, so the types it can put in them are the types it registered.
     * A variable of a type some other plugin owns is that plugin's to emit, into its own file; here it is
     * simply left out, with a comment saying why. Threading a host-merged catalog in would be the SDK
     * generating another plugin's fields, which is the back door the platform exists to close.
     */
    private static final ValueCatalog CATALOG = SdkValueTypes.CATALOG;

    /** The indent a node sits at inside {@code FlowGraph.of(…)}, and its routes one level further in. */
    private static final String NODE_INDENT = " ".repeat(12);
    private static final String ROUTE_INDENT = " ".repeat(20);

    private SourceEmitter() {}

    // ---- the file sets ----------------------------------------------------------------------------------

    /**
     * Every file the project is created with, keyed by its path relative to the project root.
     *
     * <p>An {@link ProjectSpec.Kind#EMPTY} project gets two files and no more: a {@code main} that prints a
     * greeting, and {@code Templates} — which is not about activities at all, only about the images folder,
     * and has to exist from the start so the first vision block a user drops names a constant that resolves.
     * Everything after that is the user's from the first character: no activities, no parameters, no flow.
     */
    public static Map<String, String> sources(ProjectSpec spec, ProjectModel model,
                                              List<String> imageBaseNames) {
        Map<String, String> out = new LinkedHashMap<>();
        if (spec.kind() != ProjectSpec.Kind.GAME_BOT) {
            out.put(path(spec, spec.entryClassName()), emptyEntryPoint(spec));
            out.put(path(spec, TemplateNames.CLASS_NAME), templates(spec, imageBaseNames));
            return out;
        }

        out.put(path(spec, spec.entryClassName()), entryPoint(spec));
        out.put(path(spec, "GoHome"), goHome(spec));
        out.put(path(spec, "Popups"), popups(spec));
        out.putAll(regenerated(spec, model, imageBaseNames));
        for (ActivityModel activity : model.activities()) out.putAll(activityStub(spec, activity));
        return out;
    }

    /** The files rewritten on every change to the project's model, keyed the same way. */
    public static Map<String, String> regenerated(ProjectSpec spec, ProjectModel model,
                                                  List<String> imageBaseNames) {
        Map<String, String> out = new LinkedHashMap<>();
        if (spec.kind() != ProjectSpec.Kind.GAME_BOT) return out;

        out.put(path(spec, "Activities"), activities(spec, model));
        out.put(path(spec, "Parameters"), parameters(spec, model));
        out.put(path(spec, TemplateNames.CLASS_NAME), templates(spec, imageBaseNames));
        out.put(path(spec, "ActivityRegistry"), registry(spec, model));
        out.put(path(spec, "FlowDriver"), flowDriver(spec, model));
        return out;
    }

    /**
     * The {@code Templates} class alone, as a single-entry map — for <b>either</b> kind of project.
     *
     * <p>It is separate from {@link #regenerated} because it is the one generated file that is not derived
     * from the model at all: it is a function of the images folder, it exists in an {@link
     * ProjectSpec.Kind#EMPTY} project too, and it is rewritten on every capture, rename and delete — moments
     * at which nothing about the activities has changed and rewriting the flow would be noise.
     */
    public static Map<String, String> templatesFile(ProjectSpec spec, List<String> imageBaseNames) {
        return Map.of(path(spec, TemplateNames.CLASS_NAME), templates(spec, imageBaseNames));
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

    /**
     * The {@code import <pkg>.activities.*;} line for a generated file, or nothing when that package has no
     * source in it — an import-on-demand of an empty package does not compile.
     */
    private static String activitiesImport(ProjectSpec spec, ProjectModel model) {
        return model.activities().isEmpty() ? "" : "import " + spec.packageName() + ".activities.*;\n";
    }

    // ---- the value holders ------------------------------------------------------------------------------

    /**
     * The generated {@code Activities} class: one {@code boolean} per activity the project defines, and
     * nothing else.
     *
     * <p><b>Not {@code final} — deliberately, and this is the one thing in the emitter that must not be
     * "tidied".</b> {@code public static final boolean MINING = false;} is a JLS §4.12.4 <em>constant
     * variable</em>, which javac folds into every use site: a user's own {@code while (Activities.MINING)
     * { … }} then becomes an {@code unreachable statement} <b>compile error</b>, so unticking a box in the
     * editor would stop the bot compiling. Dropping {@code final} is what makes the field an ordinary
     * variable that cannot fold. {@link #parameters} keeps {@code final}, because a folded {@code int} is
     * harmless — a value can never make a statement unreachable.
     */
    static String activities(ProjectSpec spec, ProjectModel model) {
        StringBuilder out = new StringBuilder(header(spec));
        out.append("""
                /**
                 * Which of this bot's activities are switched on. GENERATED by BotMaker — do not edit by hand;
                 * manage via Project &rarr; Activity Flow. One field per activity the project defines, which is
                 * what each activity's {@code isEnabled()} reads.
                 *
                 * <p><b>Why these are not {@code final}.</b> A {@code static final boolean} with a constant
                 * initialiser is folded into every use site by the compiler, and a constant-{@code false} one
                 * makes {@code while (Activities.X) { … }} an <em>unreachable statement</em> — a compile error
                 * in your own code, caused by unticking a box. Leaving off {@code final} keeps that from
                 * happening. Assign to one at run time if you want to; nothing here objects.
                 *
                 * <p>REGENERATED — rewritten whenever the project's activities change, so edits here are lost.
                 */
                public final class Activities {
                """);
        for (VariableModel flag : model.activityFlags()) {
            out.append('\n');
            appendDoc(out, flag.description());
            out.append("    public static boolean ").append(flag.name()).append(" = ")
                    .append(LiteralWriter.initializer(CATALOG, flag.type(), flag.value())).append(";\n");
        }
        return out.append("\n    private Activities() {}\n}\n").toString();
    }

    /**
     * The generated {@code Parameters} class: one {@code public static final} field per project variable, of
     * the variable's own type, holding the value as a plain Java literal.
     *
     * <p><b>Its own file, and not {@link #activities}.</b> The two were once one class holding one flat
     * namespace, in which an activity's on/off tick and the delay it waits for were spelled the same way and
     * neither name said which was which — while they are governed differently at every level above the field:
     * a flag is written by the Activity Flow and read by an activity, a value is the user's and is what the
     * Runner offers.
     */
    static String parameters(ProjectSpec spec, ProjectModel model) {
        StringBuilder out = new StringBuilder(header(spec));
        Set<String> imports = LiteralWriter.imports(CATALOG,
                model.variables().stream().map(VariableModel::type).toList());
        for (String fqn : imports) out.append("import ").append(fqn).append(";\n");
        if (!imports.isEmpty()) out.append('\n');
        out.append("""
                /**
                 * Every value this bot was configured with. GENERATED by BotMaker — do not edit by hand; manage
                 * via Project &rarr; Parameters.
                 *
                 * <p>The values are <b>baked in</b>: what you see is the literal the editor wrote at generation
                 * time, not a string parsed at startup. Changing one here is therefore lost the next time the
                 * project is saved — change it in the dialog, which is also what keeps the file beside it in
                 * step.
                 *
                 * <p>REGENERATED — rewritten whenever the project's parameters change.
                 */
                public final class Parameters {
                """);
        for (VariableModel v : model.variables()) {
            out.append('\n');
            // A type nothing registered has no source name and no literal, so there is no field to write.
            // Saying so in the file is the whole of the handling: the value itself is untouched in
            // activities.json and comes back the moment the plugin that owns the type does.
            if (!LiteralWriter.canEmit(CATALOG, v.type())) {
                out.append("    // ").append(v.name()).append(": no plugin provides the type '")
                        .append(v.type().type().id())
                        .append("', so no field is generated. Its value is kept in activities.json.\n");
                continue;
            }
            appendDoc(out, v.description());
            out.append("    public static final ").append(v.type().sourceName()).append(' ').append(v.name())
                    .append(" = ").append(LiteralWriter.initializer(CATALOG, v.type(), v.value()))
                    .append(";\n");
        }
        return out.append("\n    private Parameters() {}\n}\n").toString();
    }

    /**
     * The generated {@code Templates} class — one {@code String} constant per image template.
     *
     * <p>Written even with no templates at all: a hand-written {@code import} of it must keep compiling after
     * the last template is deleted. Names with no constant are listed in a comment rather than dropped
     * silently, so the one question the class raises ("why isn't mine here?") is answered in the file itself.
     */
    static String templates(ProjectSpec spec, List<String> imageBaseNames) {
        List<String> named = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String baseName : imageBaseNames == null ? List.<String>of() : imageBaseNames) {
            if (TemplateNames.constantFor(baseName) != null) named.add(baseName);
            else if (baseName != null && !baseName.isBlank()) skipped.add(baseName);
        }
        named.sort(String::compareTo);

        StringBuilder out = new StringBuilder(header(spec));
        out.append("/**\n");
        out.append(" * Every image template in this project, by name.\n");
        out.append(" *\n");
        out.append(" * <p>Use one wherever a template is wanted: {@code new ImageTemplate(")
                .append(TemplateNames.CLASS_NAME).append('.')
                .append(named.isEmpty() ? "MY_TEMPLATE" : TemplateNames.constantFor(named.getFirst()))
                .append(")}.\n");
        out.append(" * Naming the file here rather than repeating its path at every use site means a rename is\n");
        out.append(" * one edit, and a use site that has to change fails to compile instead of failing to find.\n");
        out.append(" *\n");
        out.append(" * <p>GENERATED from the images folder — edits are overwritten whenever a template is added,\n");
        out.append(" * renamed or deleted. Add a template through BotMaker, not by editing here.\n");
        out.append(" */\n");
        out.append("public final class ").append(TemplateNames.CLASS_NAME).append(" {\n\n");
        out.append("    private ").append(TemplateNames.CLASS_NAME).append("() {}\n");
        for (String baseName : named) {
            out.append("\n    public static final String ").append(TemplateNames.constantFor(baseName))
                    .append(" = ").append(LiteralWriter.quote(
                            TemplateNames.pathForConstant(TemplateNames.constantFor(baseName)))).append(";\n");
        }
        if (!skipped.isEmpty()) {
            out.append("\n    // No constant for: ").append(String.join(", ", skipped)).append('\n');
            out.append("    // A template is named here only when its file name is a lowercase identifier;\n");
            out.append("    // rename it in the resource manager to give it one.\n");
        }
        return out.append("}\n").toString();
    }

    // ---- the flow ---------------------------------------------------------------------------------------

    /**
     * The generated {@code ActivityRegistry}: one typed singleton per activity the flow can reach, plus
     * {@code ALL} over them.
     *
     * <p>The singletons are typed ({@code public static final Mining MINING}) rather than only living in
     * {@code ALL}, because {@link #flowDriver} builds a route from that activity's <em>own</em> outcome enum
     * — which {@code List<Activity<?>>} erases. {@code ALL} is still emitted because constructing an activity
     * is what registers it by name for {@code Activity.disable("Mining")}.
     *
     * <p>Orphans (placed but unreachable) are left out: they do not run. They keep their stub and their
     * {@code Activities} flag, so the project keeps compiling and wiring one up is one drag away.
     */
    static String registry(ProjectSpec spec, ProjectModel model) {
        List<ActivityModel> reachable = model.orderedActivities();
        StringBuilder out = new StringBuilder(header(spec));
        out.append("import com.botmaker.sdk.api.bot.Activity;\n");
        out.append(activitiesImport(spec, model));
        out.append("\nimport java.util.List;\n\n");
        out.append("""
                /**
                 * The activities this bot can run. GENERATED by BotMaker — do not edit by hand; manage via
                 * Project &rarr; Activity Flow. Each is built once here, which is also what registers it by name
                 * for {@code Activity.disable("Name")}.
                 *
                 * <p>REGENERATED — rewritten on every change to the flow.
                 */
                public final class ActivityRegistry {
                """);
        for (ActivityModel a : reachable) {
            out.append("\n    public static final ").append(a.name()).append(' ').append(constantName(a.name()))
                    .append(" = new ").append(a.name()).append("();\n");
        }
        out.append("\n    public static final List<Activity<?>> ALL = List.of(");
        for (int i = 0; i < reachable.size(); i++) {
            out.append(i == 0 ? "\n" : ",\n").append("            ")
                    .append(constantName(reachable.get(i).name()));
        }
        out.append(");\n");
        return out.append("\n    private ActivityRegistry() {}\n}\n").toString();
    }

    /**
     * The generated {@code FlowDriver} — the drawn flow as a {@code FlowGraph} table, and nothing else.
     *
     * <p><b>The walk is not generated.</b> It used to be: a {@code switch} over node names, each case
     * checking the enable flag, setting the popup guard, calling {@code GoHome}, then switching again over
     * the activity's outcome — around a loop with its own step budget and delay. Every line of that was
     * identical in every project and only the table differed, so the walk moved into the SDK where it
     * compiles once, has a type, and is tested against branches, joins, loops and unwired outcomes.
     *
     * <p>The typing that made the old {@code switch} safe is kept rather than traded away: {@code node} is
     * generic in the activity's own outcome enum, so a route built from another activity's constant does not
     * compile.
     */
    static String flowDriver(ProjectSpec spec, ProjectModel model) {
        List<ActivityModel> reachable = model.orderedActivities();
        FlowModel flow = model.flow();
        String start = flow.resolvedStart(reachable.stream().map(ActivityModel::name).toList());

        StringBuilder out = new StringBuilder(header(spec));
        out.append("import com.botmaker.sdk.api.flow.FlowGraph;\n");
        out.append("import com.botmaker.sdk.api.flow.PopupCheck;\n");
        out.append("import com.botmaker.sdk.api.flow.Recovery;\n");
        out.append(activitiesImport(spec, model));
        out.append("""

                /**
                 * Walks the Activity Flow drawn in BotMaker. GENERATED — do not edit by hand; manage via
                 * Project &rarr; Activity Flow.
                 *
                 * <p>Runs the current activity, then picks the next one from the outcome it reported. The run
                 * ends when the reported outcome has no wire leaving it; a cycle is legal, because that is how a
                 * bot repeats, and the step budget below is what stops one that loops with no way out.
                 *
                 * <p>REGENERATED — rewritten on every change to the flow.
                 */
                public final class FlowDriver {

                    /**
                     * How many activities one run may hand off to before giving up. Change it in
                     * Project &rarr; Activity Flow.
                     */
                """);
        out.append("    private static final int MAX_STEPS = ").append(flow.maxSteps()).append(";\n\n");
        out.append("""
                    /**
                     * How long to pause between two activities, in milliseconds. A flow may loop, so an activity
                     * that finishes in milliseconds can hand straight back to itself and never let go of the
                     * mouse — leaving no gap in which to stop the bot. This is that gap; 0 disables it.
                     */
                """);
        out.append("    private static final int STEP_DELAY_MS = ").append(flow.stepDelayMs()).append(";\n\n");
        out.append("    /** The flow, as drawn: which activity each node runs, and where each outcome leads. */\n");
        out.append("    private static final FlowGraph FLOW = FlowGraph.of(\n").append("            ")
                .append(start.isEmpty() ? "null" : LiteralWriter.quote(start));
        for (ActivityModel a : reachable) {
            out.append(",\n").append(driverNode(a, flow));
        }
        out.append(");\n\n");
        out.append("""
                    public static void run() {
                        FlowGraph.walk(FLOW, MAX_STEPS, STEP_DELAY_MS, GoHome.INSTANCE::execute);
                    }

                    private FlowDriver() {}
                }
                """);
        return out.toString();
    }

    /**
     * One activity's row of the table: which activity the node runs, how to run it, and where each outcome it
     * can report leads.
     */
    private static String driverNode(ActivityModel a, FlowModel flow) {
        // A disabled activity isn't skipped out of the flow — the flow still passes through it, it just
        // doesn't do anything, so it follows its own DISABLED wire. That wire used to be inferred from NEXT,
        // which meant the destination was invisible in the editor and could not be chosen; a project drawn
        // before DISABLED existed has none, so switching an activity off now ends the run where it used to
        // carry on. Deliberate, and not migrated: guessing NEXT was the wrong answer often enough that
        // preserving it would preserve the bug.
        FlowEdgeModel whenDisabled = edgeFor(flow, a.name(), FlowEdgeModel.DISABLED_OUTCOME);
        StringBuilder out = new StringBuilder(NODE_INDENT)
                .append("FlowGraph.node(").append(LiteralWriter.quote(a.name())).append(", ")
                .append("ActivityRegistry.").append(constantName(a.name()))
                .append(", PopupCheck.").append(a.popupCheck() ? "ON" : "OFF")
                .append(", Recovery.").append(a.goHome() ? "GO_HOME" : "NONE")
                .append(", ").append(whenDisabled == null ? "null" : LiteralWriter.quote(whenDisabled.to()));
        // allOutcomes(), not flowPorts(): DISABLED is the argument above, never a route. Emitting it here too
        // would be a second mechanism for one thing, and Outcome has no such constant to name it with.
        for (String outcome : a.allOutcomes()) {
            FlowEdgeModel wire = edgeFor(flow, a.name(), outcome);
            if (wire == null) continue;   // nothing drawn for it: an unrouted outcome ends the run
            out.append(",\n").append(ROUTE_INDENT).append("FlowGraph.route(").append(a.name())
                    .append(".Outcome.").append(outcome).append(", ")
                    .append(LiteralWriter.quote(wire.to())).append(')');
        }
        return out.append(')').toString();
    }

    /** The wire drawn for one {@code (activity, outcome)} pair, or null when that outcome goes nowhere. */
    private static FlowEdgeModel edgeFor(FlowModel flow, String from, String outcome) {
        for (FlowEdgeModel e : flow.edges()) {
            if (e.from().equals(from) && e.outcomeOrNext().equals(outcome)) return e;
        }
        return null;
    }

    /**
     * The registry field name for an activity — its name upper-cased, the usual shape for a constant.
     *
     * <p>Two activities whose names differ only in case would collide here; {@link ProjectModel#nameClash} is
     * case-insensitive so that cannot be saved. It would be a broken project anyway — their stub files differ
     * only in case, which does not survive a case-insensitive filesystem.
     */
    static String constantName(String activityName) {
        return activityName.toUpperCase(Locale.ROOT);
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

    /** The bot's entry point. SEED — written when the project is created, and never again. */
    static String entryPoint(ProjectSpec spec) {
        return header(spec) + """
                import com.botmaker.sdk.api.bot.Bot;
                import com.botmaker.sdk.api.bot.PopupGuard;

                /**
                 * The bot's entry point.
                 *
                 * <p>SEED — BotMaker writes it once, when the project is created, and never again. Everything
                 * below is yours from that moment on.
                 */
                public class %s {

                    public static void main(String[] args) {
                        // Click delays, match confidence, and whether to drive the real mouse and keyboard (which
                        // is what a game needs — it ignores the quiet background clicks BotMaker sends by
                        // default) are project settings, applied by the SDK before the first click. Edit them in
                        // the Input & Clicks dialog.

                        // Runs Popups.run() before every vision step, so a daily reward or a mail popup is
                        // dismissed instead of hiding whatever the next find was looking for. Popups.java is
                        // yours: it decides which templates mean "a popup is up", and how to close each one.
                        PopupGuard.install(Popups.INSTANCE::execute);

                        // Walks the Activity Flow forever; on a crash or a stuck screen it runs GoHome and
                        // restarts the game you picked in the editor.
                        Bot.start(FlowDriver::run, GoHome.INSTANCE::execute);
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

                    /** The one instance; referenced by the entry point and FlowDriver. */
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
                 *   private static final ImageTemplateGroup POPUPS = ImageTemplateGroup.of(mail, claimAll, close);
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
     */
    static String stub(ProjectSpec spec, ActivityModel a) {
        String doc = a.description().isBlank() ? "" : "\n * <p>" + escapeDoc(a.description()) + "\n *";
        return "package " + spec.packageName() + ".activities;\n\n"
                + "import com.botmaker.sdk.api.bot.Activity;\n"
                + "import " + spec.packageName() + ".Activities;\n\n"
                + ("""
                /**
                 * Activity: %1$s. Fill in {@link #run()} with how to do it — that method is the whole point of
                 * this file, and this file is yours to edit (BotMaker creates it once and never overwrites it).
                 * {@link #isEnabled()} is wired to this activity's flag on {@code Activities} and is managed for
                 * you.
                 *%4$s
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
                        return Activities.%3$s;
                    }

                    @Override
                    public Outcome run() {
                        // TODO: how to do %1$s
                        return Outcome.NEXT;
                    }
                }
                """).formatted(a.name(), String.join(", ", a.allOutcomes()), a.name(), doc);
    }

    // ---- shared -----------------------------------------------------------------------------------------

    /** A one-line javadoc for a field, when the model has a description to put in it. */
    private static void appendDoc(StringBuilder out, String description) {
        if (description == null || description.isBlank()) return;
        out.append("    /** ").append(escapeDoc(description)).append(" */\n");
    }

    /** Anything a user typed has to be safe inside a comment; only one sequence can end one. */
    private static String escapeDoc(String text) {
        return text.strip().replace("*/", "*&#47;");
    }
}
