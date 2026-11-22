// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.internal.screens.ScreenGraph.Presence;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.graph.DirectedWeightedPseudograph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.graph.MaskSubgraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The map's graph algorithms, on JGraphT (spec {@code 2026-11-22-jgrapht-map-graph-design.md}); {@link ScreenGraph}
 * asks them and no other class names a JGraphT type.
 *
 * <p>A way back returns where the game came from, so whether one may be taken depends on the step before it. The
 * search graph's nodes are therefore walk states, a screen and the screen its last way in left from, and the rule is
 * in the graph's edges: a way back after a way in from another screen than the one it leads to has no edge. Each
 * screen also has one arrival node, reached from each of its states at no cost, so a search to a screen has one
 * target.
 */
final class MapGraph {

    /** What one step weighs beyond its cost: of paths that cost the same, the one of fewer steps is lighter. */
    private static final long PER_STEP = 1024;

    private sealed interface Node permits State, Arrival {}

    /** Being on {@code screen}, its last way in from {@code cameFrom}; null after a way back, one on its own, or none. */
    private record State(String screen, String cameFrom) implements Node {}

    /** Being on {@code screen}, however the game came. */
    private record Arrival(String screen) implements Node {}

    private sealed interface Link permits Move, Arrive {}

    /** Taking the map's edge number {@code index} from {@code from}. */
    private record Move(State from, int index) implements Link {}

    /** Arriving: from a state to its screen's arrival node. */
    private record Arrive(State from) implements Link {}

    /** The search graph of {@link ScreenGraph#edges}, each its number in {@code edges}. */
    private record Search(List<ScreenGraph.Edge> edges, Graph<Node, Link> graph) {}

    private final ScreenGraph map;
    /** The map's screens: a path begins and ends on one, though an edge may lead to a screen of no map. */
    private final Set<String> screens;
    /** The map's ways in, {@link ScreenGraph#ways}, no way back: the screen each is on to the one it opens. */
    private final List<ScreenGraph.Edge> ways;
    /** The screens and their ways in, each edge its number in {@link #ways}. */
    private final Graph<String, Integer> plain;
    /** {@link #search}, once built: only a path or a cost needs it, so a race only builds it twice. */
    private Search search;

    private MapGraph(ScreenGraph map, Set<String> screens, List<ScreenGraph.Edge> ways, Graph<String, Integer> plain) {
        this.map = map;
        this.screens = screens;
        this.ways = ways;
        this.plain = plain;
    }

    /**
     * The graphs of {@code map}: the screens and their ways in, built now, and the search graph of its
     * {@link ScreenGraph#edges}, each weighted by {@link ScreenGraph#cost}, built when a path or a cost is first asked.
     */
    static MapGraph of(ScreenGraph map) {
        List<ScreenGraph.Edge> ways = List.copyOf(map.ways());
        Graph<String, Integer> plain = new DirectedPseudograph<>(Integer.class);
        map.screens().keySet().forEach(plain::addVertex);
        for (int i = 0; i < ways.size(); i++) {
            plain.addVertex(ways.get(i).from());
            plain.addVertex(ways.get(i).to());
            plain.addEdge(ways.get(i).from(), ways.get(i).to(), i);
        }
        return new MapGraph(map, Set.copyOf(map.screens().keySet()), ways, plain);
    }

    private Search search() {
        Search built = search;
        if (built == null) search = built = search(map);
        return built;
    }

    private static Search search(ScreenGraph map) {
        List<ScreenGraph.Edge> edges = List.copyOf(map.edges());
        Graph<Node, Link> graph = new DirectedWeightedPseudograph<>(Link.class);
        Map<String, List<State>> states = new LinkedHashMap<>();
        map.screens().keySet().forEach(screen -> state(graph, states, new State(screen, null)));
        for (ScreenGraph.Edge edge : edges) {
            state(graph, states, new State(edge.from(), null));
            state(graph, states, after(edge));
        }
        for (List<State> at : List.copyOf(states.values())) {
            for (State from : at) {
                for (int i = 0; i < edges.size(); i++) {
                    ScreenGraph.Edge edge = edges.get(i);
                    if (!edge.from().equals(from.screen()) || refused(from, edge)) continue;
                    Move move = new Move(from, i);
                    graph.addEdge(from, after(edge), move);
                    graph.setEdgeWeight(move, map.cost(edge) * PER_STEP + 1);
                }
            }
        }
        return new Search(edges, graph);
    }

    private static void state(Graph<Node, Link> graph, Map<String, List<State>> states, State state) {
        List<State> at = states.computeIfAbsent(state.screen(), screen -> {
            graph.addVertex(new Arrival(screen));
            return new ArrayList<>();
        });
        if (!graph.addVertex(state)) return;
        at.add(state);
        Arrive arrive = new Arrive(state);
        graph.addEdge(state, new Arrival(state.screen()), arrive);
        graph.setEdgeWeight(arrive, 0);
    }

    /** The state {@code edge} leaves the game in. */
    private static State after(ScreenGraph.Edge edge) {
        return new State(edge.to(), edge.back() || edge.onItsOwn() ? null : edge.from());
    }

    /** A way back returns where the game came from: after a way in from elsewhere it leads there again, not here. */
    private static boolean refused(State from, ScreenGraph.Edge edge) {
        return edge.back() && from.cameFrom() != null && !from.cameFrom().equals(edge.to());
    }

    /**
     * The quickest edges {@code usable} allows from {@code from} to {@code to}: least total cost; of equal cost, fewest
     * edges; then, step by step from {@code from}, the edge first in {@link ScreenGraph#edges} order. Empty when none
     * leads there or either screen is not on the map; no edges from a screen to itself.
     */
    Optional<List<ScreenGraph.Edge>> pathTo(String from, String to, Predicate<ScreenGraph.Edge> usable) {
        if (!screens.contains(from) || !screens.contains(to)) return Optional.empty();
        State start = new State(from, null);
        Arrival end = new Arrival(to);
        if (from.equals(to)) return Optional.of(List.of());
        Search search = search();
        Graph<Node, Link> open = usableOnly(search, usable);
        // Searched backward from the screen, so each state knows what is left to it, and the path is chosen forward
        // from the start, the first edge in map order at each step that keeps to the least.
        DijkstraShortestPath.SingleSourcePaths<Node, Link> left =
                new DijkstraShortestPath<>(new EdgeReversedGraph<>(open)).getPaths(end);
        if (left.getWeight(start) == Double.POSITIVE_INFINITY) return Optional.empty();
        List<ScreenGraph.Edge> path = new ArrayList<>();
        State at = start;
        while (!at.screen().equals(to)) {
            Move best = null;
            for (Link link : open.outgoingEdgesOf(at)) {
                if (!(link instanceof Move move) || best != null && move.index() >= best.index()) continue;
                if (open.getEdgeWeight(move) + left.getWeight(open.getEdgeTarget(move)) == left.getWeight(at)) {
                    best = move;
                }
            }
            if (best == null) return Optional.empty();
            path.add(search.edges().get(best.index()));
            at = (State) open.getEdgeTarget(best);
        }
        return Optional.of(List.copyOf(path));
    }

    /**
     * What the quickest path {@code usable} allows from {@code from} costs to each screen it reaches, by
     * {@link ScreenGraph#cost}: {@code from} itself at 0. Empty for a screen not on the map.
     */
    Map<String, Long> costsFrom(String from, Predicate<ScreenGraph.Edge> usable) {
        if (!screens.contains(from)) return Map.of();
        Search search = search();
        DijkstraShortestPath.SingleSourcePaths<Node, Link> paths =
                new DijkstraShortestPath<>(usableOnly(search, usable)).getPaths(new State(from, null));
        Map<String, Long> costs = new LinkedHashMap<>();
        for (Node node : search.graph().vertexSet()) {
            if (!(node instanceof Arrival(String screen)) || !screens.contains(screen)) continue;
            double weight = paths.getWeight(node);
            if (weight != Double.POSITIVE_INFINITY) costs.put(screen, (long) weight / PER_STEP);
        }
        return costs;
    }

    /**
     * Every screen's presence: {@link Presence#ALWAYS} for one reached from {@code sources} through ways in that
     * {@code through} says are always there, else {@link Presence#SOMETIMES} for one reached through ways that are at
     * least at times, else {@link Presence#LOCKED}. A loop of ways reaches nothing new, so it adds nothing.
     */
    Map<String, Presence> presences(Set<String> sources, Function<ScreenGraph.Edge, Presence> through) {
        Presence[] each = ways.stream().map(through).toArray(Presence[]::new);
        Map<String, Presence> known = new HashMap<>();
        screens.forEach(screen -> known.put(screen, Presence.LOCKED));
        // The worse level first, so the better overwrites what it also reaches.
        for (Presence level : List.of(Presence.SOMETIMES, Presence.ALWAYS)) {
            Graph<String, Integer> open = new MaskSubgraph<>(plain, screen -> false,
                    way -> each[way].ordinal() > level.ordinal());
            reached(open, sources).stream().filter(screens::contains).forEach(screen -> known.put(screen, level));
        }
        return known;
    }

    /**
     * The screens that follow {@code screen} on its own, and those that follow them in turn, nearest first and, of
     * equal distance, in declared order; at most {@code most}.
     */
    List<String> followers(String screen, int most) {
        if (!plain.containsVertex(screen)) return List.of();
        Graph<String, Integer> onItsOwn = new MaskSubgraph<>(plain, s -> false, way -> !ways.get(way).onItsOwn());
        return reached(onItsOwn, List.of(screen)).stream().filter(s -> !s.equals(screen)).limit(most).toList();
    }

    /** The screens {@code screen}'s ways in are on, and theirs in turn, at most {@code most}. */
    Set<String> dependsOn(String screen, int most) {
        if (!plain.containsVertex(screen)) return Set.of();
        Set<String> found = new LinkedHashSet<>();
        for (String s : reached(new EdgeReversedGraph<>(plain), Graphs.predecessorListOf(plain, screen))) {
            if (found.size() >= most) break;
            found.add(s);
        }
        return found;
    }

    /**
     * What {@code graph} reaches from {@code starts}, the starts included, breadth first from all of them at once:
     * nearest first, so a cap keeps the nearest of every start's. JGraphT's iterator of several starts goes through
     * all one start reaches before the next.
     */
    private static Set<String> reached(Graph<String, Integer> graph, Collection<String> starts) {
        Set<String> found = new LinkedHashSet<>(starts);
        Deque<String> next = new ArrayDeque<>(found);
        while (!next.isEmpty()) {
            for (String to : Graphs.successorListOf(graph, next.poll())) if (found.add(to)) next.add(to);
        }
        return found;
    }

    /**
     * The graph of the edges {@code usable} allows. It is asked once per edge, before the search: a walk's test may
     * match a picture or read a condition that changes, and the search reads each edge many times.
     */
    private static Graph<Node, Link> usableOnly(Search search, Predicate<ScreenGraph.Edge> usable) {
        boolean[] open = new boolean[search.edges().size()];
        for (int i = 0; i < open.length; i++) open[i] = usable.test(search.edges().get(i));
        return new MaskSubgraph<>(search.graph(), node -> false,
                link -> link instanceof Move move && !open[move.index()]);
    }
}
