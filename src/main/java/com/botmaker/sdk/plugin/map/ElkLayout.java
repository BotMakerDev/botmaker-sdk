package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.toolkit.graph.GraphLayout;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes;
import org.eclipse.elk.alg.layered.LayeredLayoutProvider;
import org.eclipse.elk.alg.layered.options.CycleBreakingStrategy;
import org.eclipse.elk.alg.layered.options.LayerConstraint;
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.core.data.LayoutMetaDataService;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkBendPoint;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🗺 Screens laid out by ELK's layered algorithm (the user's call, 2026-10-22, over the toolkit's own layout): every
 * way in ranks from the first screen, square links routed clear of the cards, fewer crossings. The entrances weigh
 * most, so they set the ranks; a way back goes in turned round, so it runs back against them rather than pulling its
 * screen forward. Popups and screens no way reaches are ELK's components of their own, packed beside the rest.
 *
 * <p>The cards' places and the links' paths go to the canvas as one {@link Placement}; a link with no path here (one
 * to or from a popup) is routed by the canvas as before.
 */
final class ElkLayout {

    /** One way between two screens, as the map has it. */
    record Way(String from, String to, boolean entrance, boolean back) {}

    /** How much more an entrance weighs than another way, in which way its link runs. */
    private static final int ENTRANCE = 10;
    /** The space between two links side by side. */
    private static final double LANE = 10;

    static {
        // Outside OSGi ELK knows its options only once they are registered: the core's at the service's start, the
        // layered algorithm's here, named, as a plugin's class loader is not the one a service lookup searches.
        LayoutMetaDataService.getInstance().registerLayoutMetaDataProviders(new LayeredMetaDataProvider());
    }

    private ElkLayout() {}

    /**
     * Lays out two cards and throws the result away: ELK's classes loaded and warmed before a map needs them. A
     * failure is left for the map's own layout, which falls back to the toolkit's.
     */
    static void warm() {
        try {
            arrange(List.of(Card.of("a", 10, 10), Card.of("b", 10, 10)), List.of(new Way("a", "b", true, false),
                    new Way("b", "a", false, true)), Set.of(), "a", Direction.RIGHT, Gaps.DEFAULT);
        } catch (RuntimeException | LinkageError ignored) {
            // The map's layout meets the same failure, and falls back.
        }
    }

    /**
     * Lays {@code cards} out by {@code ways}, which are every way of the map in the canvas's order (their paths go in
     * that order), with {@code root} in the first rank. A way to or from a card not in {@code cards} or in
     * {@code apart} places nothing and has no path.
     */
    static Placement arrange(List<Card> cards, List<Way> ways, Set<String> apart, String root, Direction direction,
                             Gaps gaps) {
        ElkNode graph = ElkGraphUtil.createGraph();
        graph.setProperty(LayeredOptions.DIRECTION, direction == Direction.DOWN
                ? org.eclipse.elk.core.options.Direction.DOWN : org.eclipse.elk.core.options.Direction.RIGHT);
        graph.setProperty(LayeredOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
        graph.setProperty(LayeredOptions.CYCLE_BREAKING_STRATEGY, CycleBreakingStrategy.GREEDY);
        graph.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, gaps.between());
        graph.setProperty(LayeredOptions.SPACING_NODE_NODE, gaps.across());
        graph.setProperty(LayeredOptions.SPACING_COMPONENT_COMPONENT, gaps.between());
        graph.setProperty(LayeredOptions.SPACING_EDGE_NODE, Routes.CLEAR);
        graph.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, Routes.CLEAR);
        graph.setProperty(LayeredOptions.SPACING_EDGE_EDGE, LANE);
        graph.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, LANE);
        graph.setProperty(LayeredOptions.PADDING, new ElkPadding(0));

        Map<String, ElkNode> nodes = new LinkedHashMap<>();
        for (Card card : cards) {
            ElkNode node = ElkGraphUtil.createNode(graph);
            node.setDimensions(card.width(), card.height());
            if (card.id().equals(root)) node.setProperty(LayeredOptions.LAYERING_LAYER_CONSTRAINT, LayerConstraint.FIRST);
            nodes.put(card.id(), node);
        }
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

        new LayeredLayoutProvider().layout(graph, new BasicProgressMonitor());

        Map<String, Pt> at = new LinkedHashMap<>();
        nodes.forEach((id, node) -> at.put(id, new Pt(node.getX(), node.getY())));
        Map<GraphLayout.Link, List<List<Pt>>> paths = new LinkedHashMap<>();
        for (int i = 0; i < ways.size(); i++) {
            Way way = ways.get(i);
            ElkEdge edge = edgeOf.get(i);
            paths.computeIfAbsent(GraphLayout.Link.of(way.from(), way.to()), link -> new ArrayList<>())
                    .add(edge == null ? List.of() : path(edge, way.back()));
        }
        return new Placement(at, Set.of(), 0, paths);
    }

    /** {@code edge}'s path from its start through its bends to its end; from the end back when it went in turned. */
    private static List<Pt> path(ElkEdge edge, boolean turned) {
        if (edge.getSections().isEmpty()) return List.of();
        ElkEdgeSection section = edge.getSections().getFirst();
        List<Pt> path = new ArrayList<>();
        path.add(new Pt(section.getStartX(), section.getStartY()));
        for (ElkBendPoint bend : section.getBendPoints()) path.add(new Pt(bend.getX(), bend.getY()));
        path.add(new Pt(section.getEndX(), section.getEndY()));
        return turned ? path.reversed() : path;
    }
}
