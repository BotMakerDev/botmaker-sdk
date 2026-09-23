package com.botmaker.sdk.internal.plugin.flow;

import com.botmaker.sdk.api.flow.Flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules the Activity Flow canvas enforces while wiring. Kept pure and free of JavaFX so the canvas can
 * ask "may I connect these?" and so the rules are unit-testable.
 *
 * <p><b>Almost everything this class used to forbid is now the feature.</b> It was written to keep the flow a
 * single linear chain, so it rejected a fork, a join, a self-wire and anything that would loop. With
 * outcome-routed edges: a fork <em>is</em> branching (one wire per outcome), a join is two branches meeting
 * again, a self-wire is a retry, and a cycle is how a bot repeats — the generated driver's step budget is
 * what bounds it now, not the editor. What is left is the one thing that genuinely cannot be drawn: <b>a
 * second wire on the same {@code (from, outcome)} pair</b>, because one result can't lead to two places.
 *
 * <p>Note there is nothing here about ending the run. An outcome with no wire ends it, so "stop" is the
 * absence of a rule rather than a node with rules of its own.
 *
 * <p>Nodes the run can't reach are reported by {@link #orphans} instead, which is a warning ("won't run"),
 * not a blocked action: while you are still wiring, most cards are legitimately unconnected.
 */
public final class FlowRules {

    private FlowRules() {}

    /**
     * Why {@code from —outcome→ to} may not be wired, or {@code null} when it is allowed. The message is
     * written for the user and shown inline on the canvas.
     */
    public static String rejectionFor(List<Flow.Edge> edges, String from, String outcome, String to) {
        String label = outcome == null || outcome.isBlank() ? Flow.Edge.NEXT : outcome;
        for (Flow.Edge e : edges) {
            if (e.from().equals(from) && e.outcomeOrNext().equals(label)) {
                return from + " already goes somewhere when it reports " + label
                        + " — remove that wire first, or use a different outcome.";
            }
        }
        return null;
    }

    /**
     * The activities that are placed but unreachable from {@code start} — they won't run. With no wires at
     * all nothing is wired yet, so nothing is an orphan — a flow with no wires runs its activities in the
     * order they are listed.
     */
    public static List<String> orphans(List<String> placed, List<Flow.Edge> edges, String start) {
        if (edges.isEmpty()) return List.of();
        Set<String> live = new HashSet<>(reachable(placed, edges, start));
        List<String> out = new ArrayList<>();
        for (String a : placed) {
            if (!live.contains(a)) out.add(a);
        }
        return out;
    }

    /**
     * The activities a run can reach, breadth-first from {@code start}, falling back to the first placed
     * card when {@code start} names nothing placed — which is the rule {@code FlowWalker} resolves a start
     * with, so what the canvas marks as reachable is what a run actually reaches.
     *
     * <p><b>It walks here rather than delegating, since 2026-09-21.</b> The walk was
     * {@code FlowModel.reachableFrom}, shared so that the canvas and the code generator could not disagree
     * about it. There is no generator, and there is no {@code FlowModel}: the flow is a value in the bot's
     * own Java. What is left is one canvas asking one question about its own wires.
     */
    public static List<String> reachable(List<String> placed, List<Flow.Edge> edges, String start) {
        String from = placed.contains(start) ? start : (placed.isEmpty() ? "" : placed.getFirst());
        Set<String> known = new HashSet<>(placed);
        if (!known.contains(from)) return List.of();

        Map<String, List<String>> successors = new LinkedHashMap<>();
        for (Flow.Edge edge : edges) {
            // A wire naming something that is not placed is stale — a card that has been deleted — and is
            // dropped rather than making a node of a name nothing draws.
            if (!known.contains(edge.from()) || !known.contains(edge.to())) continue;
            successors.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
        }

        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            String node = queue.removeFirst();
            // Already reached; this is also the cycle guard, which is what makes a looping flow terminate
            // here rather than spin.
            if (!visited.add(node)) continue;
            queue.addAll(successors.getOrDefault(node, List.of()));
        }
        return List.copyOf(visited);
    }
}
