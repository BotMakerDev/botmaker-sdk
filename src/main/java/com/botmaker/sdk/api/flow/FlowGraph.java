package com.botmaker.sdk.api.flow;

import com.botmaker.plugin.api.meta.ReplacedBy;
import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.plugin.api.authoring.FlowEdgeModel;
import com.botmaker.sdk.internal.bot.ActivityRegistry;
import com.botmaker.sdk.internal.bot.LegacyActivity;
import com.botmaker.sdk.internal.config.ProjectData;
import com.botmaker.sdk.internal.flow.ActivityLoader;
import com.botmaker.sdk.internal.flow.FlowWalker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Activity Flow drawn in BotMaker Studio, as data: which activity each node runs, and where each outcome
 * it can report leads.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Studio used to generate the walk itself — a {@code switch} over node names, each case running an
 * activity and switching again over its outcome, the loop and its step budget written out as text. Every line
 * of that was the same in every project; the only thing that differed was the table. A generated file should
 * hold what is true about <em>this</em> project and nothing else, so the table stayed generated and the walk
 * moved here, where it compiles, has a type, and can be tested. {@link com.botmaker.sdk.internal.flow.FlowWalker}
 * is that walk; {@link #walk} is how a generated {@code FlowDriver} reaches it.
 *
 * <h2>Nothing generates it any more</h2>
 *
 * <p>The table went the same way the walk did, and for the same reason: it followed entirely from the model,
 * so it was data spelled as Java. {@link #load(Class)} reads it from the project's own {@code activities.json}
 * and {@link #run(Class, Runnable)} walks it, which together are the whole of what the generated
 * {@code FlowDriver} and {@code ActivityRegistry} were. Everything below about the hand-built form still
 * holds — it is deprecated, not removed, and a bot that has one keeps working.
 *
 * <h2>The shape of the calls</h2>
 *
 * <p>Everything is a {@code static} call on this type — {@code FlowGraph.of}, {@code FlowGraph.node},
 * {@code FlowGraph.route} — and never a builder chain. That is not a matter of taste. Studio can mechanically
 * repair a generated file against a newer SDK in exactly two shapes: a type that moved, and a static call
 * whose member moved. Every call in a fluent chain after the first has an <em>instance</em> receiver, whose
 * type the repair's parser cannot know, so a builder would put this table beyond repair and turn any rename
 * here into "update Studio before you can edit your flow".
 *
 * <p>What a generated table looks like:
 *
 * <pre>{@code
 * private static final FlowGraph FLOW = FlowGraph.of("Mining",
 *         FlowGraph.node("Mining", ActivityRegistry.MINING, PopupCheck.ON, Recovery.NONE, "Smelting",
 *                 FlowGraph.route(Mining.Outcome.BAG_FULL, "Smelting"),
 *                 FlowGraph.route(Mining.Outcome.DEFAULT, "Mining")),
 *         FlowGraph.node("Smelting", ActivityRegistry.SMELTING, PopupCheck.OFF, Recovery.GO_HOME, null,
 *                 FlowGraph.route(Smelting.Outcome.DEFAULT, "Mining")));
 * }</pre>
 *
 * <p>{@link #node} is generic in the activity's outcome type, so a {@link Route} may only be built from a
 * constant of <em>that activity's own</em> enum: routing {@code Smelting.Outcome.DEFAULT} out of the mining
 * node does not compile. That is the same guarantee the old generated {@code switch} had, kept rather than
 * traded away for the table.
 *
 * <h2>Ending a run</h2>
 *
 * <p>There is no terminal node. A run ends when the outcome an activity reported has no route leaving it —
 * an unwired outcome <em>is</em> the stop — or when the node it led to is not in the graph. Cycles are legal,
 * that being how a bot repeats, which is why {@link #walk} takes a step budget.
 */
@Palette(category = "flow", categoryLabel = "Flow", order = 104)
@Hidden("a value type: the generated flow driver builds one, a bot body does not")
public final class FlowGraph {

    private final String start;
    private final Map<String, Node> nodes;

    private FlowGraph(String start, Map<String, Node> nodes) {
        this.start = start;
        this.nodes = nodes;
    }

    /**
     * The flow this project drew, read from its own {@code activities.json}.
     *
     * <p>This is what replaced the generated {@code FlowDriver} and {@code ActivityRegistry}. Between them
     * those two files held a {@code FlowGraph.of(…)} table and one typed singleton per activity, both
     * rewritten from the model on every change to the flow — which makes them data that had been spelled as
     * Java, and the reason a plugin could not contribute a file without becoming a code generator.
     *
     * <p><b>What is given up, and where it is bought back.</b> The table used to be typed: {@link #node} is
     * generic in the activity's own outcome enum, so a route built from another activity's constant did not
     * compile. Read from a file, the routes are outcome <em>names</em> and nothing checks them at build time.
     * The check that mattered is kept where a human actually writes one — {@code return Outcome.BAG_FULL;} in
     * an activity's own body, against an enum the editor keeps in step with the canvas. A wire in a file the
     * editor wrote is not where the mistakes were.
     *
     * <p><b>The anchor.</b> Any class in the project's base package; in practice the bot's entry point,
     * {@code FlowGraph.run(Main.class, …)}. An activity's class is {@code <that package>.activities.<Name>},
     * which is where the editor has always written it, so the entry point naming itself is all the loader
     * needs — and it keeps working when the class, the package, or both are renamed.
     *
     * <p>Best-effort throughout, like every other read of a bot's own configuration: no file, an unreadable
     * one, a name with no class behind it and a wire pointing nowhere are all states with an answer. An empty
     * graph runs nothing and stops, which is what a project with no flow drawn should do.
     *
     * @param anchor a class in the project's base package
     */
    public static FlowGraph load(Class<?> anchor) {
        return assemble(anchor, ProjectData.current());
    }

    /**
     * Loads this project's flow and walks it — the whole of what a generated {@code FlowDriver.run()} did.
     *
     * <p>Does not return; see {@link #walk}. The step budget and the pause between activities come from the
     * file, so changing either in Project &rarr; Activity Flow no longer rewrites any Java.
     *
     * @param anchor a class in the project's base package — the bot's entry point
     * @param goHome the bot's own "get back to a known screen" step, run for a {@link Recovery#GO_HOME} node.
     *               Passed rather than discovered: {@code GoHome} is a file the user owns outright, and a
     *               loader guessing at it would be the one piece of this that was magic
     */
    public static void run(Class<?> anchor, Runnable goHome) {
        ProjectData data = ProjectData.current();
        walk(assemble(anchor, data), data.maxSteps(), data.stepDelayMs(), goHome);
    }

    /**
     * The graph one model describes.
     *
     * <p>It is here rather than in {@code internal} for one reason: {@link Node}'s constructor is private, and
     * widening it so a loader elsewhere could call it would put the only unchecked way to build a node on the
     * public surface. Reading the model is {@link ProjectData}'s and constructing the activities is
     * {@link ActivityLoader}'s; what is left here is the assembly, which is the part that must not be
     * reachable from anywhere else.
     */
    static FlowGraph assemble(Class<?> anchor, ProjectData data) {
        Map<String, ActivityRegistry.Runner> activities = ActivityLoader.load(anchor, data.activities());
        Map<String, Node> byName = new LinkedHashMap<>();
        for (String name : data.placed()) {
            // A placed activity with no body is still a node, and that is a deliberate reversal: it used to
            // be dropped, so a wire into it ended the run. An activity nobody has written yet is an activity
            // that does nothing, which the flow already has a word for — it takes its DISABLED wire, exactly
            // as one switched off in the editor does, and a flow drawn ahead of its code walks through.
            ActivityRegistry.Runner activity = activities.get(name);
            Map<String, String> routes = new LinkedHashMap<>(data.routes(name));
            // DISABLED is not an outcome an activity can report — it did not run — so it is one slot on the
            // node rather than a route, exactly as the generated table spelled it.
            //
            // FlowEdgeModel is a com.botmaker.plugin.api type named from inside api.*, which the standing
            // invariant forbids — and this is not an exception to it, for the same reason the four palette
            // annotations are not. The invariant is about a bot's *runtime*: no api.* signature or field
            // type may name a contract type, because the contract dependency is optional and never
            // transitive. DISABLED_OUTCOME is a compile-time String constant, so javac writes its value
            // into this class's constant pool and a running bot never resolves the class it came from.
            // Spelling the literal here instead would be the actual violation: two copies of one wire word,
            // and the flow silently taking the wrong branch when one of them changes.
            String whenDisabled = routes.remove(FlowEdgeModel.DISABLED_OUTCOME);
            byName.put(name, new Node(name, activity,
                    data.popupCheck(name) ? PopupCheck.ON : PopupCheck.OFF,
                    data.goHome(name) ? Recovery.GO_HOME : Recovery.NONE,
                    whenDisabled, Map.copyOf(routes)));
        }
        String start = data.start();
        return new FlowGraph(byName.containsKey(start) ? start : null, byName);
    }

    /**
     * The graph, built by hand.
     *
     * @param start the node a run begins at; {@code null} for a flow with nothing in it, which ends at once
     * @param nodes every node, in the order Studio lists them
     * @throws IllegalArgumentException if two nodes share a name — the name is the identity routes are
     *                                  written against, so a duplicate would silently make one unreachable
     * @deprecated a project's flow is read from its own {@code activities.json} now — {@link #load(Class)}.
     */
    @Deprecated(since = "1.3.0", forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.flow.FlowGraph#load", behaviourChanged = true,
            note = "The flow is no longer written into your bot as a table. FlowGraph.load(Main.class) reads "
                    + "the same flow from activities.json, so this call and the FlowDriver around it can go. "
                    + "A hand-built graph still works exactly as before.")
    public static FlowGraph of(String start, Node... nodes) {
        Map<String, Node> byName = new LinkedHashMap<>();
        for (Node node : nodes) {
            if (byName.putIfAbsent(node.name(), node) != null) {
                throw new IllegalArgumentException("two flow nodes are called \"" + node.name() + "\"");
            }
        }
        return new FlowGraph(start, byName);
    }

    /**
     * One node: an activity, how to run it, and where each of its outcomes leads.
     *
     * @param name         the node's name, which is what routes and {@code start} refer to
     * @param activity     the activity to run — a singleton from the generated {@code ActivityRegistry}
     * @param popupCheck   whether the popup guard runs while it does
     * @param recovery     what to do first, once the activity is known to be active
     * @param whenDisabled where to go when the activity is switched off; {@code null} ends the run. A
     *                     disabled activity is not skipped <em>out of</em> the flow — the flow still passes
     *                     through it, it just does nothing and follows the wire it would have taken with
     *                     nothing to report
     * @param routes       one per outcome that has somewhere to go; an outcome with no route ends the run
     * @param <O>          the activity's own outcome enum, which is what makes the routes checked
     * @throws IllegalArgumentException if two routes name the same outcome
     * @deprecated a project's flow is read from its own {@code activities.json} now — {@link #load(Class)}.
     */
    @Deprecated(since = "1.3.0", forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.flow.FlowGraph#load", behaviourChanged = true,
            note = "One row of a table that is no longer written into your bot. FlowGraph.load(Main.class) "
                    + "builds the same node from activities.json.")
    @SafeVarargs
    public static <O extends Enum<O>> Node node(String name, Activity<O> activity, PopupCheck popupCheck,
                                                Recovery recovery, String whenDisabled, Route<O>... routes) {
        Map<String, String> targets = new LinkedHashMap<>();
        for (Route<O> route : routes) {
            if (targets.put(route.outcome().name(), route.to()) != null) {
                throw new IllegalArgumentException(
                        "node \"" + name + "\" routes " + route.outcome().name() + " twice");
            }
        }
        return new Node(name, LegacyActivity.of(activity), popupCheck, recovery, whenDisabled, targets);
    }

    /**
     * Where one outcome leads.
     *
     * @param outcome a constant of the node's activity's own outcome enum
     * @param to      the node to go to next, or {@code null} to end the run there
     * @deprecated a project's flow is read from its own {@code activities.json} now — {@link #load(Class)}.
     */
    @Deprecated(since = "1.3.0", forRemoval = false)
    @ReplacedBy(value = "com.botmaker.sdk.api.flow.FlowGraph#load", behaviourChanged = true,
            note = "One wire of a table that is no longer written into your bot. The same wire is in "
                    + "activities.json, which FlowGraph.load(Main.class) reads.")
    public static <O extends Enum<O>> Route<O> route(O outcome, String to) {
        return new Route<>(outcome, to);
    }

    /**
     * Runs the flow: the body a generated {@code FlowDriver} hands to
     * {@link com.botmaker.sdk.api.bot.Bot#start(Runnable, Runnable)}. Does not return — it ends by stopping
     * the bot, either because the flow ran out of wires or because it spent its step budget.
     *
     * @param graph        the table
     * @param maxSteps     how many hand-offs between activities one run may make before giving up. A flow is
     *                     allowed to loop, so this is what separates "farming all night" from a cycle with no
     *                     way out
     * @param stepDelayMs  how long to pause between two activities; 0 disables it
     * @param goHome       the bot's own "get back to a known screen" step, run for a
     *                     {@link Recovery#GO_HOME} node
     */
    public static void walk(FlowGraph graph, int maxSteps, int stepDelayMs, Runnable goHome) {
        FlowWalker.walk(graph, maxSteps, stepDelayMs, goHome);
    }

    /** The node a run begins at, or {@code null} for an empty flow. */
    public String start() {
        return start;
    }

    /** The node by that name, or {@code null} — which ends the run, the same as an unwired outcome. */
    public Node nodeNamed(String name) {
        return name == null ? null : nodes.get(name);
    }

    /**
     * Where one outcome leads.
     *
     * <p>A record rather than a pair of arguments on {@link #node} because a node has as many of these as its
     * activity has outcomes, and they have to be readable one per line in a generated file.
     */
    public record Route<O extends Enum<O>>(O outcome, String to) {}

    /**
     * One node of the graph. Built by {@link FlowGraph#node}; read by the walker.
     *
     * <p>The activity's outcome type is deliberately forgotten here. It did its work at the call site, where
     * it made the routes checkable against the activity they belong to; the walk that follows them only ever
     * asks an outcome for its {@linkplain Enum#name() name}, and a node table that stayed generic could not
     * be a plain array.
     *
     * <p><b>The activity may be absent</b>, and that is the node an unwritten activity gets: it is on the
     * canvas, so the flow passes through it, and with nothing to run it takes {@link #whenDisabled()}.
     */
    public static final class Node {

        private final String name;
        private final ActivityRegistry.Runner activity;
        private final PopupCheck popupCheck;
        private final Recovery recovery;
        private final String whenDisabled;
        private final Map<String, String> targets;

        private Node(String name, ActivityRegistry.Runner activity, PopupCheck popupCheck, Recovery recovery,
                     String whenDisabled, Map<String, String> targets) {
            this.name = name;
            this.activity = activity;
            this.popupCheck = popupCheck;
            this.recovery = recovery;
            this.whenDisabled = whenDisabled;
            this.targets = targets;
        }

        /** The node's name — what {@code start} and every route's {@code to} refer to. */
        public String name() {
            return name;
        }

        /**
         * The activity this node runs, or {@code null} when nothing is registered for it.
         *
         * <p>Used by the walk to decide whether to run at all, and how: a node with no runner is treated as
         * a disabled one.
         */
        public ActivityRegistry.Runner runner() {
            return activity;
        }

        /**
         * The {@link Activity} instance this node runs, or {@code null}.
         *
         * <p>{@code null} for the two cases that are ordinary now and were impossible when this was written:
         * an activity defined as a lambda through {@link com.botmaker.sdk.api.bot.Activities#define}, and one
         * on the canvas with no body written for it at all. Use {@link #runner()}, which answers for every
         * kind of activity there is.
         *
         * @deprecated an activity is not necessarily an {@link Activity} subclass — see {@link #runner()}.
         */
        @Deprecated(since = "1.3.0", forRemoval = false)
        @ReplacedBy(value = "com.botmaker.sdk.api.flow.FlowGraph$Node#runner", behaviourChanged = true,
                note = "A node's activity may be a lambda handed to Activities.define, or may not have been "
                        + "written yet, and neither is an Activity instance. runner() answers for all three; "
                        + "this one answers null for the two it cannot describe.")
        public Activity<?> activity() {
            return activity instanceof LegacyActivity legacy ? legacy.activity() : null;
        }

        /** Whether the popup guard runs while it does. */
        public PopupCheck popupCheck() {
            return popupCheck;
        }

        /** What to do before it runs. */
        public Recovery recovery() {
            return recovery;
        }

        /** Where a run goes when this node's activity is switched off; {@code null} ends the run. */
        public String whenDisabled() {
            return whenDisabled;
        }

        /**
         * The node {@code outcome} leads to, or {@code null} when nothing was wired to it — which is how a
         * run ends.
         */
        public String target(Outcome outcome) {
            return outcome == null ? null : targets.get(outcome.name());
        }

        /**
         * The node {@code outcome} leads to, for an outcome reported as an enum constant.
         *
         * @deprecated an activity reports an {@link Outcome} now — {@link #target(Outcome)}. This overload
         *             stays for a hand-built graph whose activities are {@link Activity} subclasses.
         */
        @Deprecated(since = "1.3.0", forRemoval = false)
        @ReplacedBy("com.botmaker.sdk.api.flow.FlowGraph$Node#target")
        public String target(Enum<?> outcome) {
            return outcome == null ? null : targets.get(outcome.name());
        }
    }
}
