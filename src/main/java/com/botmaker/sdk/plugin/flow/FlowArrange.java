package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.toolkit.graph.GraphLayout;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.LayeredLayout;
import com.botmaker.plugin.toolkit.graph.Routes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where Auto-arrange puts the cards, and the path each wire takes between them — the canvas's geometry without
 * JavaFX, so {@code FlowArrangeTest} can hold it to "every card and every wire readable".
 *
 * <p>The layout and the routes are the toolkit's ({@link LayeredLayout}, {@link Routes}), the ones the screen map
 * uses too (2026-10-14): they were lifted from this class, which now only turns a flow into the toolkit's cards
 * and links — a card per activity, a port per outcome, a link per wire — and places the block on the canvas.
 *
 * <p><b>Arrange</b> is layered: a card's column is its longest path from the start with the loops taken out, the
 * order down each column the one with the fewest crossings, and the block is centred on a canvas that grows to
 * hold it. <b>Routes</b> are computed from where the cards are now, so a wire stays clear of the cards after a
 * drag too.
 */
final class FlowArrange {

    private FlowArrange() {}

    /** Where a port's dot sits inside its card's edge: the dot's radius. */
    static final double PORT_INSET = 6;
    /** The smallest canvas, as before the canvas grew. */
    static final double MIN_W = 3000;
    static final double MIN_H = 2000;
    /** The space between two cards of a column, and the least space between any two arranged cards. */
    static final double ROW_GAP = 40;
    /** The space between two columns: room for wires to turn. */
    static final double COLUMN_GAP = 110;
    /** The space around the arranged block, which holds the wires that go around it. */
    static final double MARGIN = 160;
    /** How far a wire keeps from a card it passes. */
    static final double CLEAR = Routes.CLEAR;
    /** The barycenter sweeps Auto-arrange runs: down, up, down, up. */
    static final int SWEEPS = LayeredLayout.SWEEPS;

    private static final Gaps GAPS = new Gaps(COLUMN_GAP, ROW_GAP);
    /** A wire back runs above the cards only while it stays on the canvas, whose top edge is 0. */
    private static final Routes.Rules RULES = new Routes.Rules(PORT_INSET, 0);

    /**
     * One card as the layout sees it.
     *
     * @param ports each outcome port's centre, as a distance from the card's top; an outcome missing here leaves
     *              from the middle of the card's right edge
     */
    record Card(String name, double x, double y, double width, double height, Map<String, Double> ports) {

        Card {
            ports = Map.copyOf(ports);
        }

        double right() { return x + width; }

        double bottom() { return y + height; }

        Box rect() { return new Box(x, y, right(), bottom()); }

        Card at(Pt p) { return new Card(name, p.x(), p.y(), width, height, ports); }

        GraphLayout.Card shape() { return new GraphLayout.Card(name, width, height, ports); }
    }

    /** One wire's path, from its outcome port to its target's input port. */
    record Route(Arrow arrow, Routes.Route path) {}

    /**
     * What Auto-arrange decided.
     *
     * @param at        each card's top-left corner on the canvas
     * @param width     the canvas's width: the block plus {@link #MARGIN} on each side, at least {@link #MIN_W}
     * @param crossings the wire crossings between neighbouring columns, counted on the chosen order
     */
    record Layout(Map<String, Pt> at, double width, double height, int crossings) {}

    // --- arrange ---

    /** Arranges {@code cards} (their sizes and ports; their places are ignored) around {@code start}. */
    static Layout arrange(List<Card> cards, List<Arrow> arrows, String start) {
        return arrange(cards, arrows, start, SWEEPS);
    }

    /** {@link #arrange(List, List, String)} with {@code sweeps} barycenter sweeps, for the test's baseline. */
    static Layout arrange(List<Card> cards, List<Arrow> arrows, String start, int sweeps) {
        List<String> names = cards.stream().map(Card::name).toList();
        // A blank or stale start falls back on the first card, as a run does.
        String root = names.contains(start) ? start : (names.isEmpty() ? "" : names.getFirst());
        Placement placement = LayeredLayout.arrange(shapes(cards), links(arrows), List.of(root),
                Direction.RIGHT, GAPS, sweeps);
        return centred(placement, cards);
    }

    /** Moves the block into the middle of a canvas that holds it with {@link #MARGIN} to spare. */
    private static Layout centred(Placement placement, List<Card> cards) {
        Box box = placement.extent(shapes(cards));
        if (box == null) return new Layout(Map.of(), MIN_W, MIN_H, placement.crossings());
        double width = Math.max(MIN_W, box.width() + 2 * MARGIN);
        double height = Math.max(MIN_H, box.height() + 2 * MARGIN);
        double dx = (width - box.width()) / 2 - box.minX();
        double dy = (height - box.height()) / 2 - box.minY();
        Map<String, Pt> moved = new LinkedHashMap<>();
        placement.at().forEach((name, p) -> moved.put(name, new Pt(p.x() + dx, p.y() + dy)));
        return new Layout(moved, width, height, placement.crossings());
    }

    // --- routes ---

    /** The path of every arrow whose two cards are both in {@code cards}, where those cards are now. */
    static List<Route> routes(List<Card> cards, List<Arrow> arrows) {
        Map<String, Pt> at = new LinkedHashMap<>();
        for (Card card : cards) at.put(card.name(), new Pt(card.x(), card.y()));
        List<Link> links = links(arrows);
        Map<Link, Arrow> arrowOf = new LinkedHashMap<>();
        for (int i = 0; i < arrows.size(); i++) arrowOf.putIfAbsent(links.get(i), arrows.get(i));
        List<Route> routes = new ArrayList<>();
        for (Routes.Route path : Routes.of(shapes(cards), at, links, Direction.RIGHT, RULES)) {
            routes.add(new Route(arrowOf.get(path.link()), path));
        }
        return routes;
    }

    private static List<GraphLayout.Card> shapes(List<Card> cards) {
        return cards.stream().map(Card::shape).toList();
    }

    /** Each wire as a link from its outcome's port. */
    private static List<Link> links(List<Arrow> arrows) {
        return arrows.stream().map(a -> new Link(a.from(), a.outcome(), a.to())).toList();
    }

    // --- fit ---

    /** The box every card and every wire's path takes; null when there are no cards. */
    static Box extent(List<Card> cards, List<Route> routes) {
        Box box = null;
        for (Card card : cards) box = card.rect().union(box);
        for (Route route : routes) {
            Box bounds = route.path().bounds();
            box = box == null ? bounds : box.union(bounds);
        }
        return box;
    }

    /** The space Recenter leaves around what it shows, on each side. */
    static final double FIT_MARGIN = 40;

    /** The zoom, at most 1:1, at which {@code box} and {@link #FIT_MARGIN} around it fit a {@code view}. */
    static double fitZoom(Box box, double viewWidth, double viewHeight) {
        double width = box.width() + 2 * FIT_MARGIN;
        double height = box.height() + 2 * FIT_MARGIN;
        return Math.min(1, Math.min(viewWidth / width, viewHeight / height));
    }
}
