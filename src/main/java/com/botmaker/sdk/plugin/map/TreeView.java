package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A tree's nodes drawn where their offsets place them: the first node at the origin, each other one at the sum of the
 * {@link Beside} offsets along a chain from it, each offset in its own node's size, and a line for each unlock
 * ({@link com.botmaker.sdk.api.screens.Node#after}). The nodes no chain reaches sit in a row below the rest.
 */
final class TreeView {

    /** A node's size when its picture is not cut yet, in the game's pixels. */
    static final Size UNKNOWN = new Size(48, 48);

    /** What an offset dragged by hand is rounded to: try_input's step for one it measures. */
    static final double STEP = 0.05;

    /** How far, in the drawing's pixels, a press moves before it is a drag. */
    private static final double DRAG_SLOP = 5;

    /** The room left around the drawing, and between the nodes no offset places. */
    private static final double GAP = 16;

    /**
     * How much bigger than the game the drawing is shown at {@link #BASE_WIDTH}, and in proportion at other widths: a
     * tree of two nodes is not drawn huge at first, and the picture window's wheel still zooms it.
     */
    private static final double MOST_SCALE = 2;

    /** The width the picture window first asks for. */
    private static final double BASE_WIDTH = 880;

    private TreeView() {
    }

    /**
     * One node laid out: its picture id, its centre and size in the game's pixels, the first node's centre at the
     * origin, and whether a chain of offsets placed it.
     */
    record Place(String id, double x, double y, double width, double height, boolean placed) {

        double left() {
            return x - width / 2;
        }

        double right() {
            return x + width / 2;
        }

        double top() {
            return y - height / 2;
        }

        double bottom() {
            return y + height / 2;
        }
    }

    /**
     * {@code tree}'s nodes laid out, in its order: each offset is followed either way from the first node, so a node is
     * placed from its parent and a parent from its node; {@code sizes} gives a picture's size by id, null or empty for
     * one not cut.
     */
    static List<Place> layout(Tree tree, Function<String, Size> sizes) {
        List<ImageTemplate> members = tree.members().stream().filter(p -> p != null).toList();
        if (members.isEmpty()) return List.of();
        Map<String, Size> size = new LinkedHashMap<>();
        for (ImageTemplate p : members) {
            Size s = sizes.apply(p.id());
            size.put(p.id(), s == null || s.empty() ? UNKNOWN : s);
        }
        // Each offset both ways: the node's centre from its other's, in the other's size.
        Map<String, List<String>> backward = new LinkedHashMap<>();
        Map<String, Map<String, Beside>> written = new LinkedHashMap<>();
        for (com.botmaker.sdk.api.screens.Node node : tree.nodes()) {
            if (node.picture() == null) continue;
            for (Beside b : node.beside()) {
                if (b.other() == null || !size.containsKey(b.other().id())) continue;
                written.computeIfAbsent(b.other().id(), k -> new LinkedHashMap<>()).put(node.picture().id(), b);
                backward.computeIfAbsent(node.picture().id(), k -> new ArrayList<>()).add(b.other().id());
            }
        }
        Map<String, double[]> at = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        at.put(members.getFirst().id(), new double[]{0, 0});
        queue.add(members.getFirst().id());
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            double[] c = at.get(id);
            Size s = size.get(id);
            written.getOrDefault(id, Map.of()).forEach((child, b) -> {
                if (at.containsKey(child)) return;
                at.put(child, new double[]{c[0] + b.across() * s.width(), c[1] + b.down() * s.height()});
                queue.addLast(child);
            });
            for (String parent : backward.getOrDefault(id, List.of())) {
                if (at.containsKey(parent)) continue;
                Beside b = written.get(parent).get(id);
                Size ps = size.get(parent);
                at.put(parent, new double[]{c[0] - b.across() * ps.width(), c[1] - b.down() * ps.height()});
                queue.addLast(parent);
            }
        }
        double bottom = 0;
        double left = 0;
        for (Map.Entry<String, double[]> e : at.entrySet()) {
            Size s = size.get(e.getKey());
            bottom = Math.max(bottom, e.getValue()[1] + s.height() / 2.0);
            left = Math.min(left, e.getValue()[0] - s.width() / 2.0);
        }
        List<Place> places = new ArrayList<>();
        double rowX = left;
        double rowY = bottom + GAP;
        for (ImageTemplate p : members) {
            Size s = size.get(p.id());
            double[] c = at.get(p.id());
            if (c != null) {
                places.add(new Place(p.id(), c[0], c[1], s.width(), s.height(), true));
            } else {
                places.add(new Place(p.id(), rowX + s.width() / 2.0, rowY + s.height() / 2.0, s.width(), s.height(),
                        false));
                rowX += s.width() + GAP;
            }
        }
        return places;
    }

    /**
     * Where a node dragged in the drawing snaps: {@code across} of {@code other}'s widths and {@code down} of its
     * heights from it, {@code other} the nearest node placed already that a line or an offset links it to.
     */
    record Drop(String other, double across, double down) {}

    /** A node dragged in the drawing, and where it snapped; empty when no linked node is placed. True: written. */
    interface Dropped {
        boolean at(String id, Optional<Drop> drop);
    }

    /**
     * Where {@code id}, dropped with its centre at ({@code x}, {@code y}) in {@code places}' pixels, sits from the
     * nearest placed node of {@code tree} linked to it, rounded as a measured offset is; empty when none is. A node it
     * is placed from already wins over a nearer one, so the offset it has is moved rather than a second one added.
     */
    static Optional<Drop> snapped(Tree tree, List<Place> places, String id, double x, double y) {
        Set<String> linked = linked(tree, id);
        Set<String> from = new HashSet<>();
        tree.nodes().stream().filter(n -> n.picture() != null && n.picture().id().equals(id))
                .flatMap(n -> n.beside().stream()).filter(b -> b.other() != null)
                .forEach(b -> from.add(b.other().id()));
        return places.stream().filter(p -> p.placed() && !p.id().equals(id) && linked.contains(p.id()))
                .min(Comparator.<Place>comparingInt(p -> from.contains(p.id()) ? 0 : 1)
                        .thenComparingDouble(p -> Math.hypot(p.x() - x, p.y() - y)))
                .map(p -> new Drop(p.id(), rounded((x - p.x()) / p.width()), rounded((y - p.y()) / p.height())));
    }

    /** The ids of the nodes of {@code tree} a line or an offset joins to {@code id}, either way. */
    private static Set<String> linked(Tree tree, String id) {
        Set<String> linked = new HashSet<>();
        for (com.botmaker.sdk.api.screens.Node node : tree.nodes()) {
            if (node.picture() == null) continue;
            boolean mine = node.picture().id().equals(id);
            Stream.concat(node.after().stream(), node.beside().stream().map(Beside::other))
                    .filter(p -> p != null).map(ImageTemplate::id).forEach(other -> {
                        if (mine) linked.add(other);
                        else if (other.equals(id)) linked.add(node.picture().id());
                    });
        }
        return linked;
    }

    private static double rounded(double v) {
        // As try_input rounds a measured offset: a node dragged by hand is a few pixels off too.
        double r = Math.round(v / STEP) * STEP;
        return Math.round(r * 100) / 100.0;
    }

    /** As {@link #drawing(Tree, Function, double, BiFunction, Consumer, Dropped)}, its nodes not dragged. */
    static Node drawing(Tree tree, Function<String, Size> sizes, double width,
                        BiFunction<String, Double, Node> thumb, Consumer<String> picked) {
        return drawing(tree, sizes, width, thumb, picked, null);
    }

    /**
     * {@code tree} drawn {@code width} wide (at most {@link #MOST_SCALE} times the game's size per
     * {@link #BASE_WIDTH}): each node a {@code thumb} (picture
     * id and side, in the drawing's pixels) at its place, a line from each parent to its node; a click on a node hands
     * its id to {@code picked}, and a node dragged is handed to {@code dropped} (null: nodes are not dragged) with
     * where it snapped, going back to its place unless that wrote it.
     */
    static Node drawing(Tree tree, Function<String, Size> sizes, double width,
                        BiFunction<String, Double, Node> thumb, Consumer<String> picked, Dropped dropped) {
        List<Place> places = layout(tree, sizes);
        Pane pane = new Pane();
        pane.getStyleClass().add("screen-tree");
        if (places.isEmpty()) return pane;
        double minX = places.stream().mapToDouble(Place::left).min().orElse(0);
        double maxX = places.stream().mapToDouble(Place::right).max().orElse(0);
        double minY = places.stream().mapToDouble(Place::top).min().orElse(0);
        double maxY = places.stream().mapToDouble(Place::bottom).max().orElse(0);
        double scale = Math.min(MOST_SCALE * width / BASE_WIDTH, Math.max(1, width - 2 * GAP) / Math.max(1, maxX - minX));
        Function<Double, Double> sx = x -> GAP + (x - minX) * scale;
        Function<Double, Double> sy = y -> GAP + (y - minY) * scale;
        Map<String, Place> byId = new LinkedHashMap<>();
        places.forEach(p -> byId.put(p.id(), p));
        for (com.botmaker.sdk.api.screens.Node node : tree.nodes()) {
            if (node.picture() == null) continue;
            Place to = byId.get(node.picture().id());
            for (ImageTemplate parent : node.after()) {
                Place from = parent == null ? null : byId.get(parent.id());
                if (to == null || from == null) continue;
                Line line = new Line(sx.apply(from.x()), sy.apply(from.y()), sx.apply(to.x()), sy.apply(to.y()));
                line.getStyleClass().add("screen-tree-line");
                pane.getChildren().add(line);
            }
        }
        for (Place p : places) {
            double side = Math.max(p.width(), p.height()) * scale;
            Node box = thumb.apply(p.id(), side);
            box.relocate(sx.apply(p.x()) - side / 2, sy.apply(p.y()) - side / 2);
            Tooltip.install(box, new Tooltip(p.id() + (p.placed() ? "" : ": no offsets place it yet")));
            // A press that moved past the slop is a drag, not a click: a click's jitter writes nothing.
            boolean[] moved = {false};
            box.setOnMouseClicked(e -> {
                if (!moved[0]) picked.accept(p.id());
            });
            if (dropped != null) {
                double[] from = new double[2];
                box.setOnMousePressed(e -> {
                    from[0] = e.getSceneX();
                    from[1] = e.getSceneY();
                    moved[0] = false;
                });
                box.setOnMouseDragged(e -> {
                    double dx = e.getSceneX() - from[0];
                    double dy = e.getSceneY() - from[1];
                    if (Math.hypot(dx, dy) >= DRAG_SLOP) moved[0] = true;
                    if (!moved[0]) return;
                    box.setTranslateX(dx);
                    box.setTranslateY(dy);
                });
                box.setOnMouseReleased(e -> {
                    double dx = box.getTranslateX();
                    double dy = box.getTranslateY();
                    if (!moved[0]) return;
                    double x = p.x() + dx / scale;
                    double y = p.y() + dy / scale;
                    if (!dropped.at(p.id(), snapped(tree, places, p.id(), x, y))) {
                        box.setTranslateX(0);
                        box.setTranslateY(0);
                    }
                });
            }
            pane.getChildren().add(box);
        }
        pane.setPrefSize(2 * GAP + (maxX - minX) * scale, 2 * GAP + (maxY - minY) * scale);
        return pane;
    }
}
