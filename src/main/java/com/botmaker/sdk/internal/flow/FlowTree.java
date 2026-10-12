package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A flow with its sub-flows, read as one tree: the walker, the runner's switches, the canvas and Claude's tools
 * share these answers, so a sub-flow card's exits are the same everywhere.
 */
public final class FlowTree {

    private FlowTree() {}

    /** The first step running {@code activity} in {@code flow} or, depth first, in one of its sub-flows; else null. */
    public static Flow.Step find(Flow flow, Activity activity) {
        return find(flow, activity, flows());
    }

    private static Flow.Step find(Flow flow, Activity activity, Set<Flow> seen) {
        if (flow == null || !seen.add(flow)) return null;
        Flow.Step own = flow.step(activity);
        if (own != null) return own;
        for (Flow.Step step : flow.steps()) {
            if (step.node() instanceof Flow.Sub sub) {
                Flow.Step inner = find(sub.inner(), activity, seen);
                if (inner != null) return inner;
            }
        }
        return null;
    }

    /** Every step of {@code flow} and, depth first, of its sub-flows, each flow once. */
    public static List<Flow.Step> steps(Flow flow) {
        List<Flow.Step> out = new ArrayList<>();
        collect(flow, out, flows());
        return out;
    }

    private static void collect(Flow flow, List<Flow.Step> out, Set<Flow> seen) {
        if (flow == null || !seen.add(flow)) return;
        for (Flow.Step step : flow.steps()) {
            out.add(step);
            if (step.node() instanceof Flow.Sub sub) collect(sub.inner(), out, seen);
        }
    }

    /**
     * The sub-flow card in {@code flow}, or in one of its sub-flows, whose flow is null — read before its
     * declaration — as the flow holding it; else null.
     */
    public static Flow holderOfUnread(Flow flow) {
        return holderOfUnread(flow, flows());
    }

    private static Flow holderOfUnread(Flow flow, Set<Flow> seen) {
        if (flow == null || !seen.add(flow)) return null;
        for (Flow.Step step : flow.steps()) {
            if (!(step.node() instanceof Flow.Sub sub)) continue;
            if (sub.inner() == null) return flow;
            Flow deeper = holderOfUnread(sub.inner(), seen);
            if (deeper != null) return deeper;
        }
        return null;
    }

    /**
     * The outcomes {@code flow} can end on — a sub-flow card's exits: every outcome of a card the run can reach that
     * nothing in {@code flow} wires, in the order the cards and their outcomes come. A sub-flow card inside it adds
     * its own exits that nothing wires on it.
     *
     * @param outcomesOf every outcome an activity declares, its enum's constants
     */
    public static List<Outcome> endOutcomes(Flow flow, Function<Activity, List<? extends Outcome>> outcomesOf) {
        return endOutcomes(flow, outcomesOf, UnaryOperator.identity());
    }

    /**
     * The same, each sub-flow card's flow read through {@code inner} first: an editor holds a card's flow as a
     * reference by name ({@link BotConstants#flow}), and {@code inner} answers the flow that name holds — the same
     * object for the same name, which is how a cycle stops.
     */
    public static List<Outcome> endOutcomes(Flow flow, Function<Activity, List<? extends Outcome>> outcomesOf,
                                            UnaryOperator<Flow> inner) {
        return endOutcomes(flow, outcomesOf, inner, flows());
    }

    /** A set of flows by identity, as sub-flow cards compare them: hashing a whole flow tree costs a walk of it. */
    private static Set<Flow> flows() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static List<Outcome> endOutcomes(Flow flow, Function<Activity, List<? extends Outcome>> outcomesOf,
                                             UnaryOperator<Flow> inner, Set<Flow> open) {
        if (flow == null || !open.add(flow)) return List.of();
        List<Outcome> ends = new ArrayList<>();
        for (Flow.Node node : reachable(flow)) {
            List<? extends Outcome> exits = switch (node) {
                case Activity activity -> outcomesOf.apply(activity);
                case Flow.Sub sub -> endOutcomes(inner.apply(sub.inner()), outcomesOf, inner, open);
            };
            for (Outcome exit : exits) {
                if (wired(flow, node, exit) == null && !ends.contains(exit)) ends.add(exit);
            }
        }
        open.remove(flow);
        return ends;
    }

    /** Where {@code flow} goes when the card {@code from} reports {@code on}, or null when nothing is wired. */
    public static Flow.Node wired(Flow flow, Flow.Node from, Outcome on) {
        for (Flow.Edge edge : flow.edges()) {
            if (edge.from().equals(from) && on.equals(edge.on())) return edge.to();
        }
        return null;
    }

    /** The cards a run of {@code flow} can reach from its start, over its wires and its off-wires. */
    public static List<Flow.Node> reachable(Flow flow) {
        Set<Flow.Node> seen = new LinkedHashSet<>();
        Deque<Flow.Node> queue = new ArrayDeque<>();
        Flow.Node start = FlowWalker.start(flow);
        if (start != null) queue.add(start);
        while (!queue.isEmpty()) {
            Flow.Node node = queue.removeFirst();
            Flow.Step step = flow.step(node);
            if (step == null || !seen.add(node)) continue;
            for (Flow.Edge edge : flow.edges()) {
                if (edge.from().equals(node)) queue.add(edge.to());
            }
            if (!step.whenOff().isNone()) queue.add(step.whenOff());
        }
        return List.copyOf(seen);
    }
}
