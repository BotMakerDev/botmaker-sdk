package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.Reach;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * try_input's scroll over a node of a tree: the {@link Node#beside} offsets of its linked nodes measured off where
 * they show. Every pair linked by {@link Node#after} and in view is measured on one frame; then the view is moved once,
 * and a pair split by the move is measured as if seen together, the nodes left behind carried by how far the view
 * moved. Each offset is written on the node a line leads to, from its parent, in the parent's size.
 */
final class TreeMeasure {

    /** What an offset is rounded to: a match box is a few pixels off, and a node's size is tens of them. */
    static final double STEP = 0.05;

    /** How much of the frame one drag moves the view, along its longer side. */
    static final double DRAG_SHARE = 0.6;

    /** How long that drag takes. */
    static final Duration DRAG_TIME = Duration.ofMillis(300);

    private TreeMeasure() {
    }

    /** One offset measured: {@code beside}, written on the node whose picture is {@code node}. */
    record Offset(ImageTemplate node, Beside beside) {

        String label() {
            return node.id() + " from " + beside.other().id() + " (" + beside.across() + ", " + beside.down() + ")";
        }
    }

    /**
     * Each node of {@code tree} in {@code seen} (match boxes by picture id) placed from each of its parents in
     * {@code seen}: its centre's distance from the parent's, in the parent's width and height.
     */
    static List<Offset> between(Map<String, Rect> seen, Tree tree) {
        List<Offset> offsets = new ArrayList<>();
        for (Node node : tree.nodes()) {
            if (node.picture() == null) continue;
            Rect at = seen.get(node.picture().id());
            if (at == null) continue;
            for (ImageTemplate parent : node.after()) {
                Rect from = seen.get(parent.id());
                if (from == null || from.width() == 0 || from.height() == 0) continue;
                double across = (centreX(at) - centreX(from)) / from.width();
                double down = (centreY(at) - centreY(from)) / from.height();
                offsets.add(new Offset(node.picture(), Beside.of(parent, rounded(across), rounded(down))));
            }
        }
        return offsets;
    }

    /**
     * The pairs a move split: one node in {@code before}, moved by {@code shift} (where it sits now, out of view), and
     * the other in {@code after}. A pair both of whose nodes show on one side is measured there instead.
     */
    static List<Offset> across(Map<String, Rect> before, Point shift, Map<String, Rect> after, Tree tree) {
        Map<String, Rect> both = new LinkedHashMap<>();
        before.forEach((id, at) -> {
            if (!after.containsKey(id)) both.put(id, at.translate(shift.x(), shift.y()));
        });
        both.putAll(after);
        return between(both, tree).stream()
                .filter(o -> after.containsKey(o.node().id()) != after.containsKey(o.beside().other().id())).toList();
    }

    /**
     * {@code tree} with each of {@code offsets} written on its node, in order: one from the same parent is replaced,
     * and one written the other way round, on the parent, is taken off (one per pair).
     */
    static Tree with(Tree tree, List<Offset> offsets) {
        List<Node> nodes = new ArrayList<>(tree.nodes());
        for (Offset offset : offsets) {
            String id = offset.node().id();
            String other = offset.beside().other().id();
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                if (node.picture() == null) continue;
                String at = node.picture().id();
                if (at.equals(id)) {
                    List<Beside> kept = new ArrayList<>(node.beside().stream()
                            .filter(b -> b.other() == null || !b.other().id().equals(other)).toList());
                    kept.add(offset.beside());
                    nodes.set(i, node.beside(kept));
                } else if (at.equals(other)) {
                    nodes.set(i, node.beside(node.beside().stream()
                            .filter(b -> b.other() == null || !b.other().id().equals(id)).toList()));
                }
            }
        }
        return tree.nodes(nodes);
    }

    /**
     * try_input's scroll over a node of {@code field}, {@code screen}'s tree: measures what shows, moves the view once
     * the way {@code notches} says (negative: what lies right of or below the view, on the frame's longer side for a
     * drag) and measures again, then writes every offset found. {@code buttons} are the screen's node buttons by
     * picture id, whose looks find each node.
     */
    static AgentReply tried(AgentContext context, String screen, String name, String field, Tree tree,
                            Map<String, Button> buttons, int notches, Boolean desktop) {
        Optional<ToolReach> found = ToolReach.of(context);
        if (found.isEmpty()) return AgentReply.refused(Frames.noProject());
        ToolReach reach = found.get();
        Optional<AgentReply> refused = ActTools.desktopRefusal(context, reach.hands(), desktop);
        if (refused.isPresent()) return refused.get();
        Optional<BufferedImage> first = reach.look();
        if (first.isEmpty()) return AgentReply.refused(Frames.noScreen());
        Map<String, Rect> before = shown(reach, tree, buttons, first.get());
        if (before.isEmpty()) {
            return AgentReply.refused("No node of " + field + " shows once on " + name + ": scroll its view to some, "
                    + "or crop_pictures them with screen " + name + ". Nothing was done.");
        }
        List<Offset> offsets = new ArrayList<>(between(before, tree));
        Map<String, Rect> last = before;
        String moved = "";
        if (tree.scrolls()) {
            BufferedImage frame = first.get();
            Point c = new Rect(0, 0, frame.getWidth(), frame.getHeight()).center();
            if (tree.isDragged()) {
                // Dragging toward the start carries what lies past the view's end into it.
                int sign = notches < 0 ? 1 : -1;
                boolean wide = frame.getWidth() >= frame.getHeight();
                int m = (int) Math.round(DRAG_SHARE * (wide ? frame.getWidth() : frame.getHeight())) / 2 * 2;
                int mx = wide ? sign * m : 0;
                int my = wide ? 0 : sign * m;
                reach.drag(new Point(c.x() + mx / 2, c.y() + my / 2), new Point(c.x() - mx / 2, c.y() - my / 2),
                        DRAG_TIME);
            } else {
                int turn = Math.abs(tree.notches());
                reach.scroll(c, notches < 0 ? -turn : turn);
            }
            Optional<BufferedImage> next = settled(reach, tree, buttons, before);
            if (next.isEmpty()) return AgentReply.refused(Frames.noScreen());
            Map<String, Rect> after = shown(reach, tree, buttons, next.get());
            // Read off the nodes or the pixels only: the drag asked for is not how far a view that glides or meets its
            // end moved, and an offset guessed from it would be written as measured.
            Optional<Point> shift = common(before, after).or(() -> reach.shift(frame, next.get()));
            if (shift.isPresent() && shift.get().x() == 0 && shift.get().y() == 0) {
                moved = " The view did not move: that end is reached.";
            } else if (shift.isEmpty()) {
                moved = " How far the view moved was not read, so no pair split by the move was measured.";
            } else {
                offsets.addAll(across(before, shift.get(), after, tree));
                moved = " Moved the view by (" + shift.get().x() + ", " + shift.get().y() + ").";
            }
            offsets.addAll(between(after, tree));
            last = after;
        }
        // Written on the tree as it is now: an edit made while the view moved is kept.
        Optional<String> failed = FxCall.call(() -> ScreenBook.update(context.services(), screen, s -> s.input(field)
                .filter(Tree.class::isInstance).map(now -> s.withInput(field, with((Tree) now, offsets).tried()))
                .orElse(s)));
        if (failed.isPresent()) return AgentReply.refused(ScreenEdits.NOT_WRITTEN + failed.get());
        List<String> unplaced = with(tree, offsets).unplaced();
        List<String> labels = offsets.stream().map(Offset::label).distinct().toList();
        return AgentReply.text((labels.isEmpty() ? "No linked pair showed, so nothing was measured: edit_map link the "
                + "nodes first." : "Measured " + labels.size() + " offset" + (labels.size() == 1 ? "" : "s") + ": "
                + String.join("; ", labels) + ".") + moved + " In view now: " + String.join(", ", last.keySet())
                + "; anything else of the tree in view is uncut: screenshot, crop_pictures it with screen " + name
                + ", link it, and scroll again."
                + (unplaced.isEmpty() ? "" : " No offsets place yet: " + String.join(", ", unplaced) + "."));
    }

    /** The nodes of {@code tree} that show once on {@code frame}, found by any of their looks, by picture id. */
    private static Map<String, Rect> shown(Reach reach, Tree tree, Map<String, Button> buttons, BufferedImage frame) {
        Map<String, Rect> seen = new LinkedHashMap<>();
        for (ImageTemplate picture : tree.members()) {
            if (picture == null) continue;
            Button button = buttons.getOrDefault(picture.id(), Button.of(picture));
            List<Reach.Seen> copies = reach.copies(button, null, Order.NORTH, frame);
            if (copies.size() == 1) seen.put(picture.id(), copies.getFirst().place());
        }
        return seen;
    }

    /**
     * The view once it holds still after the move: looked at every {@link Reach#SCROLL_SETTLE_MS} until two looks in a
     * row show its nodes at the same places, away from where they were {@code before}, for up to
     * {@link Scroll#SETTLE}. Nodes still where they were are looked at to the end of that time, and at least twice: a
     * game may start gliding late, and a look over sixteen nodes took 11 s, past the time on the first look (Firestone's
     * Tree I read as at its end, 2026-10-17).
     */
    static Optional<BufferedImage> settled(Reach reach, Tree tree, Map<String, Button> buttons,
                                           Map<String, Rect> before) {
        long until = reach.nowMs() + Scroll.SETTLE.toMillis();
        Map<String, Rect> last = null;
        while (true) {
            reach.pause(Reach.SCROLL_SETTLE_MS);
            Optional<BufferedImage> frame = reach.look();
            if (frame.isEmpty() || Thread.currentThread().isInterrupted()) return Optional.empty();
            Map<String, Rect> now = shown(reach, tree, buttons, frame.get());
            // Two looks showing no node tell nothing of a glide: only nodes held still settle it.
            boolean held = last != null && !now.isEmpty() && Reach.still(last, now) && Reach.moved(before, now);
            if (held || last != null && reach.nowMs() >= until) return frame;
            last = now;
        }
    }

    /** How far the nodes that show both before and after moved, the middle of their moves each way; empty for none. */
    static Optional<Point> common(Map<String, Rect> before, Map<String, Rect> after) {
        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        before.forEach((id, at) -> {
            Rect now = after.get(id);
            if (now == null) return;
            xs.add(now.center().x() - at.center().x());
            ys.add(now.center().y() - at.center().y());
        });
        if (xs.isEmpty()) return Optional.empty();
        xs.sort(null);
        ys.sort(null);
        return Optional.of(new Point(xs.get(xs.size() / 2), ys.get(ys.size() / 2)));
    }

    private static double centreX(Rect r) {
        return r.x() + r.width() / 2.0;
    }

    private static double centreY(Rect r) {
        return r.y() + r.height() / 2.0;
    }

    private static double rounded(double v) {
        double r = Math.round(v / STEP) * STEP;
        // Two decimals, so 1.2 reads as 1.2 and not 1.2000000000000002.
        return Math.round(r * 100) / 100.0;
    }
}
