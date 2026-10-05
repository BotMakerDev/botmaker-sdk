package com.botmaker.sdk.plugin.flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * That is not refused either: dragging from a port that has a wire <em>moves</em> the wire ({@link #rewired}).
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
     * The wire {@code from}'s {@code outcome} port already has, if any. A blank outcome and {@code NEXT} are the
     * same port.
     */
    public static Optional<Arrow> held(List<Arrow> edges, String from, String outcome) {
        String label = outcome == null || outcome.isBlank() ? Arrow.NEXT : outcome;
        for (Arrow e : edges) {
            if (e.from().equals(from) && e.outcomeOrNext().equals(label)) return Optional.of(e);
        }
        return Optional.empty();
    }

    /**
     * {@code edges} with {@code from —outcome→ to} wired: the port's existing wire, if it has one, is moved to
     * {@code to} in its place in the list, so one result still leads to exactly one place. Otherwise the wire is
     * added at the end.
     */
    public static List<Arrow> rewired(List<Arrow> edges, String from, String outcome, String to) {
        Optional<Arrow> old = held(edges, from, outcome);
        Arrow wire = new Arrow(from, to, old.map(Arrow::outcome).orElse(outcome));
        List<Arrow> out = new ArrayList<>(edges);
        if (old.isPresent()) out.set(out.indexOf(old.get()), wire);
        else out.add(wire);
        return out;
    }

    /**
     * The activities that are placed but unreachable from {@code start} — they won't run. With no wires at
     * all nothing is wired yet, so nothing is an orphan — a flow with no wires runs its activities in the
     * order they are listed.
     */
    public static List<String> orphans(List<String> placed, List<Arrow> edges, String start) {
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
     */
    public static List<String> reachable(List<String> placed, List<Arrow> edges, String start) {
        String from = placed.contains(start) ? start : (placed.isEmpty() ? "" : placed.getFirst());
        Set<String> known = new HashSet<>(placed);
        if (!known.contains(from)) return List.of();

        Map<String, List<String>> successors = new LinkedHashMap<>();
        for (Arrow edge : edges) {
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
