package com.botmaker.sdk.api.flow;

import com.botmaker.plugin.api.palette.Facade;
import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.plugin.api.meta.Since;
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
@Since("1.1.0")
@Facade(category = "flow", categoryLabel = "Flow", role = "VALUE", order = 104)
public final class FlowGraph {

    private final String start;
    private final Map<String, Node> nodes;

    private FlowGraph(String start, Map<String, Node> nodes) {
        this.start = start;
        this.nodes = nodes;
    }

    /**
     * The graph.
     *
     * @param start the node a run begins at; {@code null} for a flow with nothing in it, which ends at once
     * @param nodes every node, in the order Studio lists them
     * @throws IllegalArgumentException if two nodes share a name — the name is the identity routes are
     *                                  written against, so a duplicate would silently make one unreachable
     */
    @Since("1.1.0")
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
     */
    @SafeVarargs
    @Since("1.1.0")
    public static <O extends Enum<O>> Node node(String name, Activity<O> activity, PopupCheck popupCheck,
                                                Recovery recovery, String whenDisabled, Route<O>... routes) {
        Map<String, String> targets = new LinkedHashMap<>();
        for (Route<O> route : routes) {
            if (targets.put(route.outcome().name(), route.to()) != null) {
                throw new IllegalArgumentException(
                        "node \"" + name + "\" routes " + route.outcome().name() + " twice");
            }
        }
        return new Node(name, activity, popupCheck, recovery, whenDisabled, targets);
    }

    /**
     * Where one outcome leads.
     *
     * @param outcome a constant of the node's activity's own outcome enum
     * @param to      the node to go to next, or {@code null} to end the run there
     */
    @Since("1.1.0")
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
    @Since("1.1.0")
    public static void walk(FlowGraph graph, int maxSteps, int stepDelayMs, Runnable goHome) {
        FlowWalker.walk(graph, maxSteps, stepDelayMs, goHome);
    }

    /** The node a run begins at, or {@code null} for an empty flow. */
    @Since("1.1.0")
    public String start() {
        return start;
    }

    /** The node by that name, or {@code null} — which ends the run, the same as an unwired outcome. */
    @Since("1.1.0")
    public Node nodeNamed(String name) {
        return name == null ? null : nodes.get(name);
    }

    /**
     * Where one outcome leads.
     *
     * <p>A record rather than a pair of arguments on {@link #node} because a node has as many of these as its
     * activity has outcomes, and they have to be readable one per line in a generated file.
     */
    @Since("1.1.0")
    public record Route<O extends Enum<O>>(O outcome, String to) {}

    /**
     * One node of the graph. Built by {@link FlowGraph#node}; read by the walker.
     *
     * <p>The activity's outcome type is deliberately forgotten here. It did its work at the call site, where
     * it made the routes checkable against the activity they belong to; the walk that follows them only ever
     * asks an outcome for its {@linkplain Enum#name() name}, and a node table that stayed generic could not
     * be a plain array.
     */
    @Since("1.1.0")
    public static final class Node {

        private final String name;
        private final Activity<?> activity;
        private final PopupCheck popupCheck;
        private final Recovery recovery;
        private final String whenDisabled;
        private final Map<String, String> targets;

        private Node(String name, Activity<?> activity, PopupCheck popupCheck, Recovery recovery,
                     String whenDisabled, Map<String, String> targets) {
            this.name = name;
            this.activity = activity;
            this.popupCheck = popupCheck;
            this.recovery = recovery;
            this.whenDisabled = whenDisabled;
            this.targets = targets;
        }

        /** The node's name — what {@code start} and every route's {@code to} refer to. */
        @Since("1.1.0")
        public String name() {
            return name;
        }

        /** The activity this node runs. */
        @Since("1.1.0")
        public Activity<?> activity() {
            return activity;
        }

        /** Whether the popup guard runs while it does. */
        @Since("1.1.0")
        public PopupCheck popupCheck() {
            return popupCheck;
        }

        /** What to do before it runs. */
        @Since("1.1.0")
        public Recovery recovery() {
            return recovery;
        }

        /** Where a run goes when this node's activity is switched off; {@code null} ends the run. */
        @Since("1.1.0")
        public String whenDisabled() {
            return whenDisabled;
        }

        /**
         * The node {@code outcome} leads to, or {@code null} when nothing was wired to it — which is how a
         * run ends.
         */
        @Since("1.1.0")
        public String target(Enum<?> outcome) {
            return outcome == null ? null : targets.get(outcome.name());
        }
    }
}
