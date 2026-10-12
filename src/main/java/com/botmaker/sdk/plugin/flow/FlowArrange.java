package com.botmaker.sdk.plugin.flow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where Auto-arrange puts the cards, and the path each wire takes between them — the canvas's geometry without
 * JavaFX, so {@code FlowArrangeTest} can hold it to "every card and every wire readable".
 *
 * <p><b>Arrange</b> is layered (Sugiyama). A card's column is its longest path from the start, with the loops taken
 * out. A wire that skips columns holds a lane slot in each column it crosses, so the cards in that column make room
 * for it. The order down each column comes from barycenter sweeps, down then up, keyed by the port a wire leaves
 * from, and the order with the fewest crossings is kept. Each column is centred on the tallest, columns are as far
 * apart as their widest card needs, and the block is centred on a canvas that grows to hold it.
 *
 * <p><b>Routes</b> are computed from where the cards are now, not from the last arrange, so a wire stays clear of the
 * cards after a drag too. A forward wire crosses each column of cards between its ends through a free gap. A wire
 * back, or onto its own card, leaves to the right, runs below or above the cards between its ends, and comes back
 * into the input port. Until 2026-10-12 every wire was one S-curve, drawn through whatever card lay between its
 * ends, Recenter stayed at 1:1 and the canvas was a fixed 3000×2000.
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
    static final double CLEAR = 18;
    /** The barycenter sweeps Auto-arrange runs: down, up, down, up. */
    static final int SWEEPS = 4;

    /** The height a lane slot takes in a column a wire crosses. */
    private static final double LANE = 16;
    /** The spacing between wires that go around the block on the same side. */
    private static final double BACK_STEP = 10;
    /** The spacing between wires arriving at one input port. */
    private static final double FAN = 4;
    /** The radius of a routed wire's corners. */
    private static final double RADIUS = 12;
    /** A lane slot's name: no activity can be named with a NUL. */
    private static final String LANE_PREFIX = "\0lane";

    /** A point on the canvas, unscaled. */
    record Pt(double x, double y) {}

    /** An axis-aligned box on the canvas. */
    record Rect(double minX, double minY, double maxX, double maxY) {

        double width() { return maxX - minX; }

        double height() { return maxY - minY; }

        Pt centre() { return new Pt((minX + maxX) / 2, (minY + maxY) / 2); }

        Rect union(Rect other) {
            return new Rect(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.max(maxX, other.maxX), Math.max(maxY, other.maxY));
        }

        /** Whether {@code p} lies strictly inside, more than {@code inset} from every edge. */
        boolean holds(Pt p, double inset) {
            return p.x() > minX + inset && p.x() < maxX - inset && p.y() > minY + inset && p.y() < maxY - inset;
        }
    }

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

        Rect rect() { return new Rect(x, y, right(), bottom()); }

        Card at(Pt p) { return new Card(name, p.x(), p.y(), width, height, ports); }

        Pt in() { return new Pt(x + PORT_INSET, y + height / 2); }

        Pt out(String outcome) {
            Double portY = ports.get(outcome);
            return new Pt(right() - PORT_INSET, y + (portY == null ? height / 2 : portY));
        }

        /** Where down the card {@code outcome}'s port sits, from 0 (top) to under 1 (bottom). */
        double portFraction(String outcome) {
            Double portY = ports.get(outcome);
            return portY == null || height <= 0 ? 0.5 : Math.clamp(portY / height, 0, 0.999);
        }
    }

    /** One piece of a wire's path. */
    sealed interface Segment permits Line, Cubic {

        Pt from();

        Pt to();

        Pt at(double t);

        /** Every point the piece is drawn through or towards: its hull holds it. */
        List<Pt> hull();
    }

    record Line(Pt from, Pt to) implements Segment {

        public Pt at(double t) {
            return new Pt(from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t);
        }

        public List<Pt> hull() { return List.of(from, to); }
    }

    record Cubic(Pt from, Pt c1, Pt c2, Pt to) implements Segment {

        public Pt at(double t) {
            double u = 1 - t;
            double a = u * u * u;
            double b = 3 * u * u * t;
            double c = 3 * u * t * t;
            double d = t * t * t;
            return new Pt(a * from.x() + b * c1.x() + c * c2.x() + d * to.x(),
                    a * from.y() + b * c1.y() + c * c2.y() + d * to.y());
        }

        public List<Pt> hull() { return List.of(from, c1, c2, to); }
    }

    /** One wire's path, from its outcome port to its target's input port. */
    record Route(Arrow arrow, List<Segment> segments) {

        Route {
            segments = List.copyOf(segments);
        }

        Pt start() { return segments.getFirst().from(); }

        Pt end() { return segments.getLast().to(); }

        /** The point the arrow head is aimed from: the path's last direction. */
        Pt headFrom() {
            Segment last = segments.getLast();
            Pt from = last instanceof Cubic cubic && !cubic.c2().equals(cubic.to()) ? cubic.c2() : last.from();
            return from.equals(last.to()) ? new Pt(last.to().x() - 1, last.to().y()) : from;
        }

        /** Points along the path, about {@code step} apart. */
        List<Pt> sample(double step) {
            List<Pt> points = new ArrayList<>();
            for (Segment segment : segments) {
                double length = 0;
                List<Pt> hull = segment.hull();
                for (int i = 1; i < hull.size(); i++) length += distance(hull.get(i - 1), hull.get(i));
                int n = Math.max(2, (int) Math.ceil(length / step));
                for (int i = 0; i <= n; i++) points.add(segment.at((double) i / n));
            }
            return points;
        }

        Rect bounds() {
            Rect box = null;
            for (Segment segment : segments) {
                for (Pt p : segment.hull()) {
                    Rect dot = new Rect(p.x(), p.y(), p.x(), p.y());
                    box = box == null ? dot : box.union(dot);
                }
            }
            return box;
        }
    }

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
        Map<String, Card> byName = new LinkedHashMap<>();
        for (Card card : cards) byName.put(card.name(), card);
        List<String> names = List.copyOf(byName.keySet());
        // No wire yet: there are no layers, and every card goes on the grid.
        List<String> reachable = arrows.isEmpty() ? List.of() : FlowRules.reachable(names, arrows, start);

        Map<String, Pt> at = new LinkedHashMap<>();
        int crossings = 0;
        double top = 0;
        if (!reachable.isEmpty()) {
            Layered layered = Layered.of(reachable, arrows, byName);
            crossings = layered.order(sweeps);
            top = layered.place(at) + ROW_GAP * 2;
        }
        List<String> leftovers = new ArrayList<>();
        for (String name : names) {
            if (!at.containsKey(name)) leftovers.add(name);
        }
        grid(leftovers, byName, top, at);
        return centred(at, byName, crossings);
    }

    /** Lays {@code names} out as a squarish grid whose top edge sits at {@code top}, in placement order. */
    private static void grid(List<String> names, Map<String, Card> cards, double top, Map<String, Pt> at) {
        if (names.isEmpty()) return;
        int columns = (int) Math.ceil(Math.sqrt(names.size()));
        double pitch = 0;
        for (String name : names) pitch = Math.max(pitch, cards.get(name).width());
        pitch += COLUMN_GAP;
        double y = top;
        double rowHeight = 0;
        for (int i = 0; i < names.size(); i++) {
            int column = i % columns;
            if (column == 0 && i > 0) {
                y += rowHeight + ROW_GAP;
                rowHeight = 0;
            }
            at.put(names.get(i), new Pt(column * pitch, y));
            rowHeight = Math.max(rowHeight, cards.get(names.get(i)).height());
        }
    }

    /** Moves the block into the middle of a canvas that holds it with {@link #MARGIN} to spare. */
    private static Layout centred(Map<String, Pt> at, Map<String, Card> cards, int crossings) {
        Rect box = null;
        for (Map.Entry<String, Pt> entry : at.entrySet()) {
            Rect card = cards.get(entry.getKey()).at(entry.getValue()).rect();
            box = box == null ? card : box.union(card);
        }
        if (box == null) return new Layout(Map.of(), MIN_W, MIN_H, crossings);
        double width = Math.max(MIN_W, box.width() + 2 * MARGIN);
        double height = Math.max(MIN_H, box.height() + 2 * MARGIN);
        double dx = (width - box.width()) / 2 - box.minX();
        double dy = (height - box.height()) / 2 - box.minY();
        Map<String, Pt> moved = new LinkedHashMap<>();
        at.forEach((name, p) -> moved.put(name, new Pt(p.x() + dx, p.y() + dy)));
        return new Layout(moved, width, height, crossings);
    }

    /** A wire's step from one column to the next: from a card's port or a lane slot, into a card or a slot. */
    private record Hop(String from, double fromFraction, String to) {}

    /** The reachable cards in columns, with a lane slot wherever a wire crosses a column. */
    private static final class Layered {

        private final Map<String, Card> cards;
        private final List<List<String>> layers = new ArrayList<>();
        private final Map<String, Integer> layerOf = new HashMap<>();
        private final List<Hop> hops = new ArrayList<>();

        private Layered(Map<String, Card> cards) {
            this.cards = cards;
        }

        static Layered of(List<String> reachable, List<Arrow> arrows, Map<String, Card> cards) {
            Layered layered = new Layered(cards);
            Map<String, Integer> layer = longestPathLayers(reachable, arrows);
            int max = 0;
            for (int l : layer.values()) max = Math.max(max, l);
            for (int l = 0; l <= max; l++) layered.layers.add(new ArrayList<>());
            for (String name : reachable) layered.add(name, layer.get(name));

            int lanes = 0;
            for (Arrow arrow : arrows) {
                Integer from = layer.get(arrow.from());
                Integer to = layer.get(arrow.to());
                if (from == null || to == null || to <= from) continue;   // a loop back goes around the block
                String upper = arrow.from();
                double fraction = cards.get(upper).portFraction(arrow.outcome());
                for (int l = from + 1; l < to; l++) {
                    String lane = LANE_PREFIX + lanes++;
                    layered.add(lane, l);
                    layered.hops.add(new Hop(upper, fraction, lane));
                    upper = lane;
                    fraction = 0.5;
                }
                layered.hops.add(new Hop(upper, fraction, arrow.to()));
            }
            return layered;
        }

        private void add(String node, int layer) {
            layers.get(layer).add(node);
            layerOf.put(node, layer);
        }

        /** Runs {@code sweeps} barycenter sweeps and keeps the order with the fewest crossings; returns them. */
        int order(int sweeps) {
            List<List<String>> best = copy(layers);
            int fewest = crossings();
            for (int pass = 0; pass < sweeps; pass++) {
                if (pass % 2 == 0) {
                    for (int l = 1; l < layers.size(); l++) sort(l, true);
                } else {
                    for (int l = layers.size() - 2; l >= 0; l--) sort(l, false);
                }
                int now = crossings();
                if (now < fewest) {
                    fewest = now;
                    best = copy(layers);
                }
            }
            for (int l = 0; l < layers.size(); l++) layers.set(l, best.get(l));
            return fewest;
        }

        /**
         * Orders column {@code l} by where its wires meet the column before it ({@code byUpper}) or after it: the
         * mean of the neighbours' rows, each plus how far down its card the wire's port sits. A node with no wire
         * on that side keeps its row.
         */
        private void sort(int l, boolean byUpper) {
            List<String> column = layers.get(l);
            Map<String, Integer> row = rows(column);
            Map<String, Integer> neighbour = rows(layers.get(byUpper ? l - 1 : l + 1));
            Map<String, double[]> sums = new HashMap<>();
            for (Hop hop : hops) {
                String self = byUpper ? hop.to() : hop.from();
                String other = byUpper ? hop.from() : hop.to();
                if (!row.containsKey(self) || !neighbour.containsKey(other)) continue;
                double key = neighbour.get(other) + (byUpper ? hop.fromFraction() : 0.5);
                double[] sum = sums.computeIfAbsent(self, k -> new double[2]);
                sum[0] += key;
                sum[1]++;
            }
            Map<String, Double> key = new HashMap<>();
            for (String node : column) {
                double[] sum = sums.get(node);
                key.put(node, sum == null ? row.get(node) + 0.5 : sum[0] / sum[1]);
            }
            column.sort(Comparator.comparingDouble(key::get));
        }

        /** Pairs of hops between the same two columns whose ends are in opposite orders. */
        int crossings() {
            Map<String, Integer> row = new HashMap<>();
            for (List<String> column : layers) row.putAll(rows(column));
            Map<Integer, List<Hop>> byLayer = new HashMap<>();
            for (Hop hop : hops) byLayer.computeIfAbsent(layerOf.get(hop.from()), k -> new ArrayList<>()).add(hop);
            int count = 0;
            for (List<Hop> between : byLayer.values()) {
                for (int i = 0; i < between.size(); i++) {
                    for (int j = i + 1; j < between.size(); j++) {
                        Hop a = between.get(i);
                        Hop b = between.get(j);
                        double upper = row.get(a.from()) + a.fromFraction() - row.get(b.from()) - b.fromFraction();
                        double lower = row.get(a.to()) - row.get(b.to());
                        if (upper * lower < 0) count++;
                    }
                }
            }
            return count;
        }

        /** Puts each card in its column, the columns centred on the tallest; returns the tallest's height. */
        double place(Map<String, Pt> at) {
            double tallest = 0;
            for (List<String> column : layers) tallest = Math.max(tallest, height(column));
            double x = 0;
            for (List<String> column : layers) {
                double y = (tallest - height(column)) / 2;
                double widest = 0;
                for (String node : column) {
                    Card card = cards.get(node);
                    if (card == null) {
                        y += LANE + ROW_GAP;
                        continue;
                    }
                    at.put(node, new Pt(x, y));
                    y += card.height() + ROW_GAP;
                    widest = Math.max(widest, card.width());
                }
                x += widest + COLUMN_GAP;
            }
            return tallest;
        }

        private double height(List<String> column) {
            double height = -ROW_GAP;
            for (String node : column) {
                Card card = cards.get(node);
                height += (card == null ? LANE : card.height()) + ROW_GAP;
            }
            return Math.max(0, height);
        }

        private static Map<String, Integer> rows(List<String> column) {
            Map<String, Integer> rows = new HashMap<>();
            for (int i = 0; i < column.size(); i++) rows.put(column.get(i), i);
            return rows;
        }

        private static List<List<String>> copy(List<List<String>> layers) {
            List<List<String>> copy = new ArrayList<>();
            for (List<String> column : layers) copy.add(new ArrayList<>(column));
            return copy;
        }
    }

    /**
     * Each reachable activity's column: the longest path to it from the start, over the flow with its back-edges
     * removed. Removing them is what keeps a cycle from making "longest path" unbounded, and what makes a loop
     * back the only right-to-left wire; with shortest-path columns an ordinary forward wire could point left.
     */
    private static Map<String, Integer> longestPathLayers(List<String> reachable, List<Arrow> arrows) {
        Map<String, List<String>> forward = forwardEdges(new LinkedHashSet<>(reachable), arrows);
        Map<String, Integer> layer = new LinkedHashMap<>();
        for (String name : reachable) layer.put(name, 0);
        // Relax |V| times: with the back-edges gone this is a DAG, so that is enough for the longest path.
        for (int pass = 0; pass < reachable.size(); pass++) {
            boolean moved = false;
            for (String from : reachable) {
                for (String to : forward.getOrDefault(from, List.of())) {
                    if (layer.get(to) < layer.get(from) + 1) {
                        layer.put(to, layer.get(from) + 1);
                        moved = true;
                    }
                }
            }
            if (!moved) break;
        }
        return layer;
    }

    /** The wiring with cycles broken: every edge except the ones a depth-first walk finds leading backwards. */
    private static Map<String, List<String>> forwardEdges(Set<String> known, List<Arrow> arrows) {
        Map<String, List<String>> all = new LinkedHashMap<>();
        for (Arrow e : arrows) {
            if (known.contains(e.from()) && known.contains(e.to()) && !e.from().equals(e.to())) {
                all.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            }
        }
        Set<String> onStack = new LinkedHashSet<>();
        Set<String> done = new HashSet<>();
        Map<String, List<String>> forward = new LinkedHashMap<>();
        for (String root : known) {
            if (!done.contains(root)) dropBackEdges(root, all, forward, onStack, done);
        }
        return forward;
    }

    /** Depth-first from {@code node}, copying every edge except those pointing at the current stack. */
    private static void dropBackEdges(String node, Map<String, List<String>> all,
                                      Map<String, List<String>> forward, Set<String> onStack, Set<String> done) {
        onStack.add(node);
        for (String next : all.getOrDefault(node, List.of())) {
            if (onStack.contains(next)) continue;   // a back-edge: this is the loop, not a layout constraint
            forward.computeIfAbsent(node, k -> new ArrayList<>()).add(next);
            if (!done.contains(next)) dropBackEdges(next, all, forward, onStack, done);
        }
        onStack.remove(node);
        done.add(node);
    }

    // --- routes ---

    /** The path of every arrow whose two cards are both in {@code cards}, where those cards are now. */
    static List<Route> routes(List<Card> cards, List<Arrow> arrows) {
        Map<String, Card> byName = new LinkedHashMap<>();
        for (Card card : cards) byName.put(card.name(), card);
        Map<Arrow, Double> arrival = arrivals(byName, arrows);
        int[] lanes = {0, 0};
        List<Route> routes = new ArrayList<>();
        for (Arrow arrow : arrows) {
            Card from = byName.get(arrow.from());
            Card to = byName.get(arrow.to());
            if (from == null || to == null) continue;
            Pt start = from.out(arrow.outcome());
            Pt end = new Pt(to.in().x(), arrival.get(arrow));
            if (from != to && end.x() - start.x() >= 2 * CLEAR) {
                List<Card> others = new ArrayList<>(cards);
                others.remove(from);
                others.remove(to);
                routes.add(new Route(arrow, smooth(forward(start, end, others))));
            } else {
                routes.add(new Route(arrow, rounded(back(start, end, cards, lanes))));
            }
        }
        return routes;
    }

    /** Where each wire meets its input port: wires sharing one port fan in a few pixels apart, by where they come from. */
    private static Map<Arrow, Double> arrivals(Map<String, Card> cards, List<Arrow> arrows) {
        Map<String, List<Arrow>> byTarget = new LinkedHashMap<>();
        for (Arrow arrow : arrows) {
            if (cards.containsKey(arrow.from()) && cards.containsKey(arrow.to())) {
                byTarget.computeIfAbsent(arrow.to(), k -> new ArrayList<>()).add(arrow);
            }
        }
        Map<Arrow, Double> arrival = new HashMap<>();
        byTarget.forEach((target, incoming) -> {
            Card to = cards.get(target);
            incoming.sort(Comparator.comparingDouble(a -> cards.get(a.from()).out(a.outcome()).y()));
            double room = Math.max(0, to.height() / 2 - 2 * FAN);
            for (int i = 0; i < incoming.size(); i++) {
                double shift = Math.clamp((i - (incoming.size() - 1) / 2.0) * FAN, -room, room);
                arrival.put(incoming.get(i), to.in().y() + shift);
            }
        });
        return arrival;
    }

    /**
     * A wire to a card further right: straight across where nothing is in the way, else through a free gap in each
     * column of cards between its ends. The turns happen between columns, where there is no card.
     */
    private static List<Pt> forward(Pt start, Pt end, List<Card> others) {
        List<Card> between = new ArrayList<>();
        for (Card card : others) {
            if (card.right() + CLEAR > start.x() && card.x() - CLEAR < end.x()) between.add(card);
        }
        List<Pt> points = new ArrayList<>();
        points.add(start);
        double floor = start.x() + CLEAR;
        double ceiling = end.x() - CLEAR;
        double y = start.y();
        for (List<Card> column : columns(between)) {
            double minX = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            for (Card card : column) {
                minX = Math.min(minX, card.x() - CLEAR);
                maxX = Math.max(maxX, card.right() + CLEAR);
            }
            // A column the wire is already level with, or still level with at its end, has no room before or
            // after it to turn: the wire keeps to the gap it is in.
            boolean noTurnBefore = minX <= Math.max(floor, points.getLast().x());
            boolean noTurnAfter = maxX >= ceiling;
            double entry = Math.max(minX, Math.max(floor, points.getLast().x()));
            double exit = Math.max(entry, Math.min(maxX, ceiling));
            double t = ((entry + exit) / 2 - start.x()) / (end.x() - start.x());
            double target = start.y() + (end.y() - start.y()) * t;
            y = freeY(column, target, noTurnBefore ? y : Double.NaN, noTurnAfter ? end.y() : Double.NaN);
            points.add(new Pt(entry, y));
            points.add(new Pt(exit, y));
        }
        points.add(end);
        return points;
    }

    /** {@code cards} in groups that overlap left to right, kept {@link #CLEAR} apart: the columns a wire crosses. */
    private static List<List<Card>> columns(List<Card> cards) {
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparingDouble(Card::x));
        List<List<Card>> columns = new ArrayList<>();
        double reach = -Double.MAX_VALUE;
        for (Card card : sorted) {
            if (columns.isEmpty() || card.x() - CLEAR >= reach + CLEAR) columns.add(new ArrayList<>());
            columns.getLast().add(card);
            reach = Math.max(reach, card.right());
        }
        return columns;
    }

    /**
     * The height nearest {@code target} at which a wire passes {@code column} at least {@link #CLEAR} from each
     * card, within the gap holding {@code keep} and the one holding {@code reach} when those are numbers.
     */
    private static double freeY(List<Card> column, double target, double keep, double reach) {
        List<double[]> blocked = new ArrayList<>();
        for (Card card : column) blocked.add(new double[]{card.y() - CLEAR, card.bottom() + CLEAR});
        blocked.sort(Comparator.comparingDouble(b -> b[0]));
        List<double[]> gaps = new ArrayList<>();
        double low = -Double.MAX_VALUE;
        for (double[] b : blocked) {
            if (b[0] > low) gaps.add(new double[]{low, b[0]});
            low = Math.max(low, b[1]);
        }
        gaps.add(new double[]{low, Double.MAX_VALUE});

        List<double[]> allowed = gaps.stream().filter(g -> holds(g, keep) && holds(g, reach)).toList();
        if (allowed.isEmpty()) allowed = gaps.stream().filter(g -> holds(g, keep)).toList();
        if (allowed.isEmpty()) allowed = gaps;
        double best = target;
        double nearest = Double.MAX_VALUE;
        for (double[] gap : allowed) {
            double y = Math.clamp(target, gap[0], gap[1]);
            if (Math.abs(y - target) < nearest) {
                nearest = Math.abs(y - target);
                best = y;
            }
        }
        return best;
    }

    private static boolean holds(double[] gap, double y) {
        return Double.isNaN(y) || (y >= gap[0] && y <= gap[1]);
    }

    /**
     * A wire back to a card on the left, or onto its own card: right out of the port, down (or up, when that is
     * shorter) past every card between its ends, across, and back into the input port from the left. Each wire on
     * one side runs {@link #BACK_STEP} further out than the one before.
     */
    private static List<Pt> back(Pt start, Pt end, List<Card> cards, int[] lanes) {
        double right = start.x() + CLEAR;
        double left = end.x() - CLEAR;
        double y = start.y();
        boolean below = true;
        for (int pass = 0; pass < 8; pass++) {
            double lo = Math.min(left, right);
            double hi = Math.max(left, right);
            double top = Double.MAX_VALUE;
            double bottom = -Double.MAX_VALUE;
            for (Card card : cards) {
                if (card.right() + CLEAR > lo && card.x() - CLEAR < hi) {
                    top = Math.min(top, card.y());
                    bottom = Math.max(bottom, card.bottom());
                }
            }
            double under = bottom + CLEAR;
            double over = top - CLEAR;
            // Above only when it stays on the canvas, whose top edge is 0: the canvas grows right and down only.
            below = over - lanes[1] * BACK_STEP < 0
                    || Math.abs(start.y() - under) + Math.abs(end.y() - under)
                    <= Math.abs(start.y() - over) + Math.abs(end.y() - over);
            y = below ? under : over;
            double nextRight = clearOf(cards, right, start.y(), y, true);
            double nextLeft = clearOf(cards, left, end.y(), y, false);
            if (nextRight == right && nextLeft == left) break;
            right = nextRight;
            left = nextLeft;
        }
        int lane = below ? lanes[0]++ : lanes[1]++;
        y += (below ? 1 : -1) * lane * BACK_STEP;
        return List.of(start, new Pt(right, start.y()), new Pt(right, y), new Pt(left, y), new Pt(left, end.y()), end);
    }

    /** {@code x}, moved past every card a vertical from {@code y0} to {@code y1} at {@code x} would cut through. */
    private static double clearOf(List<Card> cards, double x, double y0, double y1, boolean rightwards) {
        double lo = Math.min(y0, y1);
        double hi = Math.max(y0, y1);
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Card card : cards) {
                boolean across = card.x() - CLEAR < x && x < card.right() + CLEAR;
                boolean along = card.y() - CLEAR < hi && lo < card.bottom() + CLEAR;
                if (across && along) {
                    x = rightwards ? card.right() + CLEAR : card.x() - CLEAR;
                    moved = true;
                }
            }
        }
        return x;
    }

    /** Joins {@code points} with flat S-curves: each leaves and arrives level, which reads as "left to right". */
    private static List<Segment> smooth(List<Pt> points) {
        List<Pt> distinct = distinct(points);
        List<Segment> segments = new ArrayList<>();
        for (int i = 1; i < distinct.size(); i++) {
            Pt a = distinct.get(i - 1);
            Pt b = distinct.get(i);
            double dx = b.x() - a.x();
            if (Math.abs(a.y() - b.y()) < 0.5 || Math.abs(dx) < 0.5) {
                segments.add(new Line(a, b));
            } else {
                segments.add(new Cubic(a, new Pt(a.x() + dx / 2, a.y()), new Pt(b.x() - dx / 2, b.y()), b));
            }
        }
        if (segments.isEmpty()) segments.add(new Line(points.getFirst(), points.getLast()));
        return segments;
    }

    /** Joins {@code points}, a path of level and upright legs, with its corners rounded. */
    private static List<Segment> rounded(List<Pt> points) {
        List<Pt> path = new ArrayList<>();
        for (Pt p : distinct(points)) {
            // A point in line with the two around it is no corner.
            if (path.size() >= 2 && inLine(path.get(path.size() - 2), path.getLast(), p)) path.removeLast();
            path.add(p);
        }
        List<Segment> segments = new ArrayList<>();
        Pt at = path.getFirst();
        for (int i = 1; i < path.size() - 1; i++) {
            Pt corner = path.get(i);
            Pt before = path.get(i - 1);
            Pt after = path.get(i + 1);
            double r = Math.min(RADIUS, Math.min(distance(before, corner), distance(corner, after)) / 2);
            Pt in = toward(corner, before, r);
            Pt out = toward(corner, after, r);
            if (!at.equals(in)) segments.add(new Line(at, in));
            segments.add(new Cubic(in, toward(in, corner, r * 2 / 3), toward(out, corner, r * 2 / 3), out));
            at = out;
        }
        if (!at.equals(path.getLast()) || segments.isEmpty()) segments.add(new Line(at, path.getLast()));
        return segments;
    }

    private static List<Pt> distinct(List<Pt> points) {
        List<Pt> distinct = new ArrayList<>();
        for (Pt p : points) {
            if (distinct.isEmpty() || distance(distinct.getLast(), p) >= 0.5) distinct.add(p);
        }
        return distinct;
    }

    private static boolean inLine(Pt a, Pt b, Pt c) {
        return (Math.abs(a.x() - b.x()) < 0.5 && Math.abs(b.x() - c.x()) < 0.5)
                || (Math.abs(a.y() - b.y()) < 0.5 && Math.abs(b.y() - c.y()) < 0.5);
    }

    /** The point {@code by} from {@code from} toward {@code to}. */
    private static Pt toward(Pt from, Pt to, double by) {
        double length = distance(from, to);
        if (length == 0) return from;
        return new Pt(from.x() + (to.x() - from.x()) / length * by, from.y() + (to.y() - from.y()) / length * by);
    }

    private static double distance(Pt a, Pt b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    // --- fit ---

    /** The box every card and every wire's path takes; null when there are no cards. */
    static Rect extent(List<Card> cards, List<Route> routes) {
        Rect box = null;
        for (Card card : cards) box = box == null ? card.rect() : box.union(card.rect());
        for (Route route : routes) box = box == null ? route.bounds() : box.union(route.bounds());
        return box;
    }

    /** The space Recenter leaves around what it shows, on each side. */
    static final double FIT_MARGIN = 40;

    /** The zoom, at most 1:1, at which {@code box} and {@link #FIT_MARGIN} around it fit a {@code view}. */
    static double fitZoom(Rect box, double viewWidth, double viewHeight) {
        double width = box.width() + 2 * FIT_MARGIN;
        double height = box.height() + 2 * FIT_MARGIN;
        return Math.min(1, Math.min(viewWidth / width, viewHeight / height));
    }
}
