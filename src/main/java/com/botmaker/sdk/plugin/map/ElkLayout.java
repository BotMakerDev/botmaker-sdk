package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.toolkit.graph.GraphLayout;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes;
import org.eclipse.elk.alg.disco.DisCoLayoutProvider;
import org.eclipse.elk.alg.disco.options.DisCoMetaDataProvider;
import org.eclipse.elk.alg.disco.options.DisCoOptions;
import org.eclipse.elk.alg.force.options.ForceMetaDataProvider;
import org.eclipse.elk.alg.force.options.StressMetaDataProvider;
import org.eclipse.elk.alg.force.options.StressOptions;
import org.eclipse.elk.alg.force.stress.StressLayoutProvider;
import org.eclipse.elk.alg.layered.LayeredLayoutProvider;
import org.eclipse.elk.alg.layered.options.CycleBreakingStrategy;
import org.eclipse.elk.alg.layered.options.LayerConstraint;
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.mrtree.TreeLayoutProvider;
import org.eclipse.elk.alg.mrtree.options.MrTreeMetaDataProvider;
import org.eclipse.elk.alg.mrtree.options.MrTreeOptions;
import org.eclipse.elk.alg.radial.RadialLayoutProvider;
import org.eclipse.elk.alg.radial.options.RadialMetaDataProvider;
import org.eclipse.elk.alg.radial.options.RadialOptions;
import org.eclipse.elk.core.AbstractLayoutProvider;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.data.LayoutMetaDataService;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.HierarchyHandling;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkBendPoint;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 🗺 Screens laid out by ELK (the user's call, 2026-10-22, over the toolkit's own layout), one of six ways
 * ({@link LayoutMode}, 2026-11-09). The layered modes rank every way in from the first screen, route links square and
 * clear of the cards and keep crossings few: the entrances weigh most, so they set the ranks, and a way back goes in
 * turned round, so it runs back against them rather than pulling its screen forward. Tree, Hub and Organic place the
 * cards only, and the canvas routes their links. Every mode but Groups lays out inside {@code disco}, which packs the
 * parts of the map no way joins (popups, screens no way reaches) beside each other.
 *
 * <p>The cards' places and the links' paths go to the canvas as one {@link Placement}; a link with no path here is
 * routed by the canvas.
 */
final class ElkLayout {

    /** One way between two screens, as the map has it. */
    record Way(String from, String to, boolean entrance, boolean back) {}

    /** How much more an entrance weighs than another way, in which way its link runs. */
    private static final int ENTRANCE = 10;
    /** The space between two links side by side. */
    private static final double LANE = 10;
    /** The room a group's box keeps round its cards, and above them for its title (the canvas's frame fits inside). */
    private static final double GROUP_PAD = 20;
    private static final double GROUP_TITLE = 40;

    static {
        // Outside OSGi ELK knows its options only once they are registered: the core's at the service's start, each
        // algorithm's here, named, as a plugin's class loader is not the one a service lookup searches. Disco finds
        // the algorithm it runs inside by its registered id.
        LayoutMetaDataService.getInstance().registerLayoutMetaDataProviders(new LayeredMetaDataProvider(),
                new MrTreeMetaDataProvider(), new RadialMetaDataProvider(), new ForceMetaDataProvider(),
                new StressMetaDataProvider(), new DisCoMetaDataProvider());
    }

    private ElkLayout() {}

    /**
     * Lays out two cards in every mode and throws the results away: ELK's classes loaded and warmed before a map needs
     * them. A failure is left for the map's own layout, which falls back to the toolkit's.
     */
    static void warm() {
        for (LayoutMode mode : LayoutMode.values()) {
            try {
                arrange(mode, List.of(Card.of("a", 10, 10), Card.of("b", 10, 10), Card.of("c", 10, 10)),
                        List.of(new Way("a", "b", true, false), new Way("b", "a", false, true)), Set.of(), "a",
                        Direction.RIGHT, Gaps.DEFAULT);
            } catch (RuntimeException | LinkageError ignored) {
                // The map's layout meets the same failure, and falls back.
            }
        }
    }

    /**
     * The groups {@link LayoutMode#GROUPS} boxes: {@link ScreenClusters} over the cards not in {@code apart}, by the
     * ways between them.
     */
    static List<ScreenClusters.Group> groups(List<Card> cards, List<Way> ways, Set<String> apart) {
        List<String> screens = cards.stream().map(Card::id).filter(id -> !apart.contains(id)).toList();
        List<Way> between = ways.stream().filter(w -> !apart.contains(w.from()) && !apart.contains(w.to())).toList();
        return ScreenClusters.of(screens, between);
    }

    /**
     * Lays {@code cards} out by {@code ways} the way {@code mode} says. {@code ways} are every way of the map in the
     * canvas's order (their paths go in that order); {@code root} is the first rank of the layered modes and the top
     * of the tree, the first card when null. A way to or from a card not in {@code cards} or in {@code apart} places
     * nothing and has no path. Hub and Organic have no direction: both give the same places.
     */
    static Placement arrange(LayoutMode mode, List<Card> cards, List<Way> ways, Set<String> apart, String root,
                             Direction direction, Gaps gaps) {
        return arrange(mode, cards, ways, apart, root, direction, gaps,
                mode == LayoutMode.GROUPS ? groups(cards, ways, apart) : List.of());
    }

    /** {@link #arrange}, {@link LayoutMode#GROUPS} boxing {@code groups}, as {@link #groups} gave them. */
    static Placement arrange(LayoutMode mode, List<Card> cards, List<Way> ways, Set<String> apart, String root,
                             Direction direction, Gaps gaps, List<ScreenClusters.Group> groups) {
        return switch (mode) {
            case LAYERED -> layered(cards, ways, apart, root, direction, gaps, List.of());
            case COMPACT -> layered(cards, ways, apart, root, direction,
                    new Gaps(gaps.between() / 2, gaps.across() / 2), List.of());
            case GROUPS -> layered(cards, ways, apart, root, direction, gaps, groups);
            case TREE, HUB, ORGANIC -> placed(mode, cards, ways, apart, root, direction, gaps);
        };
    }

    /**
     * The layered algorithm, every way ranked from {@code root}; each of {@code groups} a box of its own, laid out with
     * the rest as one hierarchy. Without groups it runs inside disco.
     */
    private static Placement layered(List<Card> cards, List<Way> ways, Set<String> apart, String root,
                                     Direction direction, Gaps gaps, List<ScreenClusters.Group> groups) {
        ElkNode graph = ElkGraphUtil.createGraph();
        org.eclipse.elk.core.options.Direction elkDirection = direction == Direction.DOWN
                ? org.eclipse.elk.core.options.Direction.DOWN : org.eclipse.elk.core.options.Direction.RIGHT;
        List<ElkNode> levels = new ArrayList<>(List.of(graph));
        Map<String, ElkNode> parentOf = new HashMap<>();
        for (ScreenClusters.Group group : groups) {
            ElkNode box = ElkGraphUtil.createNode(graph);
            box.setProperty(LayeredOptions.PADDING, new ElkPadding(GROUP_TITLE, GROUP_PAD, GROUP_PAD, GROUP_PAD));
            levels.add(box);
            for (String screen : group.screens()) parentOf.put(screen, box);
        }
        for (ElkNode level : levels) {
            level.setProperty(CoreOptions.ALGORITHM, LayeredOptions.ALGORITHM_ID);
            level.setProperty(LayeredOptions.DIRECTION, elkDirection);
            level.setProperty(LayeredOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
            level.setProperty(LayeredOptions.CYCLE_BREAKING_STRATEGY, CycleBreakingStrategy.GREEDY);
            level.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, gaps.between());
            level.setProperty(LayeredOptions.SPACING_NODE_NODE, gaps.across());
            level.setProperty(LayeredOptions.SPACING_COMPONENT_COMPONENT, gaps.between());
            level.setProperty(LayeredOptions.SPACING_EDGE_NODE, Routes.CLEAR);
            level.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, Routes.CLEAR);
            level.setProperty(LayeredOptions.SPACING_EDGE_EDGE, LANE);
            level.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, LANE);
        }
        graph.setProperty(LayeredOptions.PADDING, new ElkPadding(0));
        if (!groups.isEmpty()) graph.setProperty(LayeredOptions.HIERARCHY_HANDLING, HierarchyHandling.INCLUDE_CHILDREN);

        Map<String, ElkNode> nodes = nodes(cards, id -> parentOf.getOrDefault(id, graph));
        String first = root != null ? root : cards.isEmpty() ? null : cards.getFirst().id();
        ElkNode top = nodes.get(first);
        if (top != null) top.setProperty(LayeredOptions.LAYERING_LAYER_CONSTRAINT, LayerConstraint.FIRST);
        // Entrances first, then the other ways on, then the ways back: ELK breaks a tie by the order it is given.
        Map<Integer, ElkEdge> edgeOf = new HashMap<>();
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < ways.size(); i++) {
                Way way = ways.get(i);
                ElkNode from = nodes.get(way.from());
                ElkNode to = nodes.get(way.to());
                int order = way.entrance() ? 0 : way.back() ? 2 : 1;
                if (order != pass || from == null || to == null || apart.contains(way.from())
                        || apart.contains(way.to())) {
                    continue;
                }
                ElkEdge edge = way.back() ? ElkGraphUtil.createSimpleEdge(to, from)
                        : ElkGraphUtil.createSimpleEdge(from, to);
                if (way.entrance()) edge.setProperty(LayeredOptions.PRIORITY_DIRECTION, ENTRANCE);
                edgeOf.put(i, edge);
            }
        }

        if (groups.isEmpty()) {
            run(graph, new LayeredLayoutProvider(), LayeredOptions.ALGORITHM_ID, gaps);
        } else {
            // The boxes and what is in them as one hierarchy: the engine hands the whole of it to the layered
            // algorithm, which lays the boxes' insides out with the rest.
            new RecursiveGraphLayoutEngine().layout(graph, new BasicProgressMonitor());
        }

        Map<GraphLayout.Link, List<List<Pt>>> paths = new LinkedHashMap<>();
        for (int i = 0; i < ways.size(); i++) {
            Way way = ways.get(i);
            ElkEdge edge = edgeOf.get(i);
            paths.computeIfAbsent(GraphLayout.Link.of(way.from(), way.to()), link -> new ArrayList<>())
                    .add(edge == null ? List.of() : path(edge, way.back()));
        }
        return new Placement(corners(nodes, graph), Set.of(), 0, paths);
    }

    /**
     * Tree, Hub or Organic: the cards placed, every link left to the canvas. Tree and Hub take a tree of the ways
     * (from {@code root} down the ways on, or out from the busiest screen by any way), so a ring places nothing twice.
     */
    private static Placement placed(LayoutMode mode, List<Card> cards, List<Way> ways, Set<String> apart, String root,
                                    Direction direction, Gaps gaps) {
        ElkNode graph = ElkGraphUtil.createGraph();
        Map<String, ElkNode> nodes = nodes(cards, id -> graph);
        List<String> order = cards.stream().map(Card::id).toList();
        List<Way> kept = ways.stream().filter(w -> nodes.containsKey(w.from()) && nodes.containsKey(w.to())
                && !apart.contains(w.from()) && !apart.contains(w.to()) && !w.from().equals(w.to())).toList();
        // How far apart two linked cards' centres go, in a ring and in the stress layout, which measure from centres:
        // the biggest card's diagonal and a gap, so no card's corner reaches the next one.
        double biggest = 0;
        for (Card card : cards) biggest = Math.max(biggest, Math.hypot(card.width(), card.height()));
        double reach = biggest + gaps.between();
        AbstractLayoutProvider provider;
        String algorithm;
        switch (mode) {
            case TREE -> {
                String first = root != null && nodes.containsKey(root) ? root : order.isEmpty() ? null : order.getFirst();
                // A screen hangs under its entrance; another way on places only what no entrance reaches.
                List<Way> entrances = kept.stream().filter(Way::entrance).toList();
                List<Way> on = kept.stream().filter(w -> !w.back() && !w.entrance()).toList();
                tree(first, order, List.of(entrances, on), false).forEach((child, parent) ->
                        ElkGraphUtil.createSimpleEdge(nodes.get(parent), nodes.get(child)));
                graph.setProperty(MrTreeOptions.DIRECTION, direction == Direction.DOWN
                        ? org.eclipse.elk.core.options.Direction.DOWN : org.eclipse.elk.core.options.Direction.RIGHT);
                graph.setProperty(MrTreeOptions.SPACING_NODE_NODE, gaps.across());
                graph.setProperty(MrTreeOptions.SEPARATE_CONNECTED_COMPONENTS, false);
                provider = new TreeLayoutProvider();
                algorithm = MrTreeOptions.ALGORITHM_ID;
            }
            case HUB -> {
                String centre = busiest(order, kept);
                Map<String, String> parents = tree(centre, order, List.of(kept), true);
                parents.forEach((child, parent) ->
                        ElkGraphUtil.createSimpleEdge(nodes.get(parent), nodes.get(child)));
                graph.setProperty(RadialOptions.SPACING_NODE_NODE, gaps.across());
                graph.setProperty(RadialOptions.RADIUS, ringGap(centre, parents, reach));
                provider = new RadialLayoutProvider();
                algorithm = RadialOptions.ALGORITHM_ID;
            }
            default -> {
                Set<List<String>> joined = new HashSet<>();
                for (Way way : kept) {
                    if (joined.add(List.of(way.from(), way.to())) && !joined.contains(List.of(way.to(), way.from()))) {
                        ElkGraphUtil.createSimpleEdge(nodes.get(way.from()), nodes.get(way.to()));
                    }
                }
                graph.setProperty(StressOptions.DESIRED_EDGE_LENGTH, reach);
                provider = new StressLayoutProvider();
                algorithm = StressOptions.ALGORITHM_ID;
            }
        }
        graph.setProperty(CoreOptions.ALGORITHM, algorithm);
        run(graph, provider, algorithm, gaps);
        Map<GraphLayout.Link, List<List<Pt>>> paths = new LinkedHashMap<>();
        for (Way way : ways) {
            paths.computeIfAbsent(GraphLayout.Link.of(way.from(), way.to()), link -> new ArrayList<>()).add(List.of());
        }
        return new Placement(corners(nodes, graph), Set.of(), 0, paths);
    }

    /**
     * Lays {@code graph} out by {@code provider}, inside disco when no way joins some of its cards to the rest: each
     * part laid out alone, the parts packed. A graph in one part is laid out by {@code provider} itself.
     */
    private static void run(ElkNode graph, AbstractLayoutProvider provider, String algorithm, Gaps gaps) {
        if (parts(graph) <= 1) {
            provider.layout(graph, new BasicProgressMonitor());
            return;
        }
        graph.setProperty(DisCoOptions.COMPONENT_COMPACTION_COMPONENT_LAYOUT_ALGORITHM, algorithm);
        graph.setProperty(DisCoOptions.SPACING_COMPONENT_COMPONENT, gaps.across());
        new DisCoLayoutProvider().layout(graph, new BasicProgressMonitor());
    }

    /** How many parts {@code graph}'s cards fall into, joined by its edges either way. */
    private static int parts(ElkNode graph) {
        Map<ElkNode, ElkNode> head = new HashMap<>();
        for (ElkNode node : graph.getChildren()) head.put(node, node);
        int parts = head.size();
        for (ElkEdge edge : graph.getContainedEdges()) {
            ElkNode a = find(head, ElkGraphUtil.connectableShapeToNode(edge.getSources().getFirst()));
            ElkNode b = find(head, ElkGraphUtil.connectableShapeToNode(edge.getTargets().getFirst()));
            if (a != b) {
                head.put(a, b);
                parts--;
            }
        }
        return parts;
    }

    private static ElkNode find(Map<ElkNode, ElkNode> head, ElkNode node) {
        while (head.get(node) != node) node = head.get(node);
        return node;
    }

    /** One node per card, sized as it, in {@code cards}' order, each made in the parent {@code in} names. */
    private static Map<String, ElkNode> nodes(List<Card> cards, Function<String, ElkNode> in) {
        Map<String, ElkNode> nodes = new LinkedHashMap<>();
        for (Card card : cards) {
            ElkNode node = ElkGraphUtil.createNode(in.apply(card.id()));
            node.setDimensions(card.width(), card.height());
            nodes.put(card.id(), node);
        }
        return nodes;
    }

    /** Each node's corner on the whole graph: inside a group's box, the box's corner added. */
    private static Map<String, Pt> corners(Map<String, ElkNode> nodes, ElkNode graph) {
        Map<String, Pt> at = new LinkedHashMap<>();
        nodes.forEach((id, node) -> {
            double x = node.getX();
            double y = node.getY();
            for (ElkNode up = node.getParent(); up != null && up != graph; up = up.getParent()) {
                x += up.getX();
                y += up.getY();
            }
            at.put(id, new Pt(x, y));
        });
        return at;
    }

    /**
     * How far apart the rings round {@code centre} go: {@code reach} at least, and wide enough that each ring holds
     * its screens side by side, ring {@code k} of radius {@code k} times this with {@code reach} a screen.
     */
    static double ringGap(String centre, Map<String, String> parents, double reach) {
        Map<Integer, Integer> perRing = new HashMap<>();
        for (String child : parents.keySet()) {
            int ring = 0;
            String at = child;
            while (at != null && !at.equals(centre)) {
                at = parents.get(at);
                ring++;
            }
            // A screen no way joins to the centre is laid out in a part of its own: no ring of the centre's.
            if (at != null) perRing.merge(ring, 1, Integer::sum);
        }
        double gap = reach;
        for (Map.Entry<Integer, Integer> ring : perRing.entrySet()) {
            gap = Math.max(gap, ring.getValue() * reach / (2 * Math.PI * ring.getKey()));
        }
        return gap;
    }

    /**
     * A tree over {@code order} by {@code tiers} of ways, as each screen's parent: breadth first from {@code first} by
     * the first tier's ways, then on from every screen reached by the next tier's, and so on; then the same from each
     * screen not yet reached, in order. {@code eitherWay} walks a way from either end.
     */
    static Map<String, String> tree(String first, List<String> order, List<List<Way>> tiers, boolean eitherWay) {
        List<Map<String, List<String>>> nexts = new ArrayList<>();
        for (List<Way> ways : tiers) {
            Map<String, List<String>> next = new LinkedHashMap<>();
            for (Way way : ways) {
                next.computeIfAbsent(way.from(), k -> new ArrayList<>()).add(way.to());
                if (eitherWay) next.computeIfAbsent(way.to(), k -> new ArrayList<>()).add(way.from());
            }
            nexts.add(next);
        }
        Map<String, String> parent = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        List<String> starts = new ArrayList<>();
        if (first != null) starts.add(first);
        starts.addAll(order);
        for (String start : starts) {
            if (!seen.add(start)) continue;
            List<String> part = new ArrayList<>(List.of(start));
            // A screen a later tier reached walks the earlier tiers again: its own entrances come before other ways.
            for (int t = 0; t < nexts.size(); t++) {
                int before = part.size();
                Deque<String> open = new ArrayDeque<>(part);
                while (!open.isEmpty()) {
                    String at = open.poll();
                    for (String to : nexts.get(t).getOrDefault(at, List.of())) {
                        if (seen.add(to)) {
                            parent.put(to, at);
                            part.add(to);
                            open.add(to);
                        }
                    }
                }
                if (t > 0 && part.size() > before) t = -1;
            }
        }
        return parent;
    }

    /** The screen of {@code order} with the most ways; ties, the first. Null for none. */
    static String busiest(List<String> order, List<Way> ways) {
        Map<String, Integer> count = new HashMap<>();
        for (Way way : ways) {
            count.merge(way.from(), 1, Integer::sum);
            count.merge(way.to(), 1, Integer::sum);
        }
        String busiest = null;
        for (String id : order) {
            if (busiest == null || count.getOrDefault(id, 0) > count.getOrDefault(busiest, 0)) busiest = id;
        }
        return busiest;
    }

    /**
     * {@code edge}'s path from its start through its bends to its end, on the whole graph; from the end back when it
     * went in turned.
     */
    private static List<Pt> path(ElkEdge edge, boolean turned) {
        if (edge.getSections().isEmpty()) return List.of();
        double dx = 0;
        double dy = 0;
        for (ElkNode in = edge.getContainingNode(); in != null && in.getParent() != null; in = in.getParent()) {
            dx += in.getX();
            dy += in.getY();
        }
        ElkEdgeSection section = edge.getSections().getFirst();
        List<Pt> path = new ArrayList<>();
        path.add(new Pt(section.getStartX() + dx, section.getStartY() + dy));
        for (ElkBendPoint bend : section.getBendPoints()) path.add(new Pt(bend.getX() + dx, bend.getY() + dy));
        path.add(new Pt(section.getEndX() + dx, section.getEndY() + dy));
        return turned ? path.reversed() : path;
    }
}
