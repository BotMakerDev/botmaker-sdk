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
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A tree's nodes drawn where their offsets place them: the first node at the origin, each other one at the sum of the
 * {@link Beside} offsets along a chain from it, each offset in its own node's size, and a line for each unlock
 * ({@link com.botmaker.sdk.api.screens.Node#after}). The nodes no chain reaches sit in a row below the rest.
 */
final class TreeView {

    /** A node's size when its picture is not cut yet, in the game's pixels. */
    static final Size UNKNOWN = new Size(48, 48);

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
     * {@code tree} drawn {@code width} wide (at most {@link #MOST_SCALE} times the game's size per
     * {@link #BASE_WIDTH}): each node a {@code thumb} (picture
     * id and side, in the drawing's pixels) at its place, a line from each parent to its node; a click on a node hands
     * its id to {@code picked}.
     */
    static Node drawing(Tree tree, Function<String, Size> sizes, double width,
                        BiFunction<String, Double, Node> thumb, Consumer<String> picked) {
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
            box.setOnMouseClicked(e -> picked.accept(p.id()));
            pane.getChildren().add(box);
        }
        pane.setPrefSize(2 * GAP + (maxX - minX) * scale, 2 * GAP + (maxY - minY) * scale);
        return pane;
    }
}
