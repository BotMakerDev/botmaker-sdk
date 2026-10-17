package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A node of a {@link Tree} brought into view: the nodes in view found by their pictures, a chain of
 * {@link Node#beside} offsets followed from one of them to the node, and the view moved toward where that puts it
 * until it shows. No place on the screen is stored: each offset is turned into pixels by the size the node it is
 * measured from shows at.
 */
final class TreeWalk {

    /** The most moves of the view one walk makes: a node further than that is not brought into view. */
    static final int MOST_MOVES = 12;

    /** How much of the frame one move covers at most, each way: a drag starts and ends inside the frame. */
    static final double MOST_MOVE = 0.8;

    /** How long one drag of the view takes. */
    static final Duration DRAG_TIME = Duration.ofMillis(300);

    private TreeWalk() {
    }

    /** Why a node was not brought into view. */
    enum Missed {
        /** The game could not be captured, or the walk was stopped. */
        NO_FRAME,
        /** No node of the tree shows: nothing to measure from. */
        NO_NODE_IN_VIEW,
        /** No chain of offsets joins the node to one in view. */
        NO_PATH,
        /** Its offsets put it in view, and it does not show there. */
        NOT_AT_ITS_PLACE,
        /** The view stopped moving short of it: an end of the tree, or offsets that put it past one. */
        END_MET,
        /** Still out of view after {@link #MOST_MOVES} moves. */
        TOO_FAR
    }

    /**
     * A walk's end: the node as the last frame shows it, or why it is not there, with the moves made either way.
     *
     * @param seen   the node in view; null when missed
     * @param missed why it is not in view; null when seen
     * @param moves  how many times the view was moved
     * @param why    what the walk met, a sentence's end after the node's name ("has no offset to …"); null when seen
     */
    record Walked(Reach.Seen seen, Missed missed, int moves, String why) {

        Optional<Reach.Seen> found() {
            return Optional.ofNullable(seen);
        }
    }

    /**
     * {@code node} on {@code frame}, or once the view of {@code tree} is moved to it. A node is found by any of its
     * looks ({@code buttons}, the screen's buttons by their picture's id; a node with none there by its picture);
     * a picture that matches twice is the copy nearest the place its offsets give. Each move brings that place toward
     * the middle, at most {@link #MOST_MOVE} of the frame each way, and the next look plans again from the nodes it
     * shows: an overshoot or a lag is corrected there. When none shows, the nodes seen are carried by how far the
     * view moved ({@link Reach#shift}, else the drag made). After a move the view is looked at until the nodes seen
     * hold still, as a glide may run on past the move.
     */
    static Walked reach(Reach reach, Button node, Tree tree, Map<String, Button> buttons, BufferedImage frame) {
        String id = node.picture().id();
        BufferedImage on = frame;
        BufferedImage last = null;
        Point moved = null;
        Map<String, Rect> before = Map.of();
        boolean wasShown = false;
        int moves = 0;
        while (true) {
            Map<String, Rect> anchors = anchors(reach, tree, id, buttons, on);
            boolean shown = !anchors.isEmpty();
            if (!shown && !before.isEmpty()) {
                // Every node seen left the view on the move: each is carried by how far the view moved, read off the
                // pixels, else by the drag made, and the next look that shows one corrects it.
                Point made = moved;
                Optional<Point> shift = reach.shift(last, on).or(() -> Optional.ofNullable(made));
                if (shift.isPresent()) anchors = carried(before, shift.get());
            }
            List<Tree.Step> path = tree.path(anchors.keySet(), id);
            Optional<Rect> goal = path.isEmpty() ? Optional.empty()
                    : Optional.of(along(path, anchors.get(path.getFirst().from().id())));
            List<Reach.Seen> copies = reach.copies(node, null, Order.NORTH, on);
            if (!copies.isEmpty()) {
                Reach.Seen seen = goal.isEmpty() ? copies.getFirst() : copies.stream()
                        .min(Comparator.comparingLong(copy -> distance(copy.place().center(), goal.get().center())))
                        .orElseThrow();
                return new Walked(seen, null, moves, null);
            }
            if (anchors.isEmpty()) {
                return new Walked(null, Missed.NO_NODE_IN_VIEW, moves, "is not in view, nor any node of its tree.");
            }
            if (path.isEmpty()) {
                return new Walked(null, Missed.NO_PATH, moves, "has no chain of offsets to a node in view ("
                        + String.join(", ", anchors.keySet()) + ").");
            }
            // Seen before the move and after it at the same places: the view did not move.
            if (shown && wasShown && unmoved(before, anchors)) {
                return new Walked(null, Missed.END_MET, moves, "is not in view where the view stopped moving.");
            }
            Rect area = new Rect(0, 0, on.getWidth(), on.getHeight());
            Point at = goal.get().center();
            // Placed by nodes in view, it should show; placed by nodes carried out of view, the next move corrects it.
            if (shown && area.contains(at)) {
                return new Walked(null, Missed.NOT_AT_ITS_PLACE, moves, "does not show at (" + at.x() + ", "
                        + at.y() + "), where its offsets from " + path.getFirst().from().id() + " put it.");
            }
            if (!tree.scrolls()) {
                return new Walked(null, Missed.END_MET, moves, "is out of view, and its tree does not scroll.");
            }
            if (moves >= MOST_MOVES) {
                return new Walked(null, Missed.TOO_FAR, moves, "is not in view after " + moves + " moves.");
            }
            Point c = area.center();
            double dx = at.x() - c.x();
            double dy = at.y() - c.y();
            // At most MOST_MOVE of the frame each way, in proportion.
            double most = Math.min(1, Math.min(dx == 0 ? 1 : MOST_MOVE * area.width() / Math.abs(dx),
                    dy == 0 ? 1 : MOST_MOVE * area.height() / Math.abs(dy)));
            int mx = (int) Math.round(dx * most);
            int my = (int) Math.round(dy * most);
            if (!tree.isDragged()) {
                if (my == 0) {
                    return new Walked(null, Missed.END_MET, moves, "is out of view sideways, and its tree's wheel "
                            + "scrolls it up and down.");
                }
                // Negative notches scroll down, as a strip's do; the tree's own say only how far.
                int notches = Math.abs(tree.notches());
                reach.scroll(c, my > 0 ? -notches : notches);
                // How far a notch moves the view is not known: only the pixels tell.
                moved = null;
            } else {
                // Dragging from c + m/2 to c − m/2 carries what sat at c + m to the middle.
                reach.drag(new Point(c.x() + mx / 2, c.y() + my / 2), new Point(c.x() - mx / 2, c.y() - my / 2),
                        DRAG_TIME);
                moved = new Point(-(mx / 2 * 2), -(my / 2 * 2));
            }
            moves++;
            Optional<BufferedImage> next = settled(reach, tree, shown ? anchors : Map.of(), buttons);
            if (next.isEmpty()) return new Walked(null, Missed.NO_FRAME, moves, "was not seen: no frame.");
            before = anchors;
            wasShown = shown;
            last = on;
            on = next.get();
        }
    }

    /** {@code anchors}, each moved by {@code shift}: where they sit once the view moved, though none shows. */
    private static Map<String, Rect> carried(Map<String, Rect> anchors, Point shift) {
        Map<String, Rect> now = new LinkedHashMap<>();
        anchors.forEach((id, at) -> now.put(id, at.translate(shift.x(), shift.y())));
        return now;
    }

    /**
     * Where {@code step.to()} sits now, {@code step.from()} showing at {@code at}. An offset is in the sizes of the node
     * it is measured from; a node not in view is taken to show at the scale {@code from} shows at, its cut size times
     * that scale.
     */
    static Rect predicted(Tree.Step step, Rect at) {
        double scale = at.width() / (double) width(step.from());
        int w = (int) Math.round(width(step.to()) * scale);
        int h = (int) Math.round(height(step.to()) * scale);
        Beside b = step.beside();
        // Forward: to = from + offset × from's size. Backward (the offset is written on from): to = from − offset × to's.
        double dx = step.reversed() ? -b.across() * w : b.across() * at.width();
        double dy = step.reversed() ? -b.down() * h : b.down() * at.height();
        Point c = new Point((int) Math.round(at.center().x() + dx), (int) Math.round(at.center().y() + dy));
        return new Rect(c.x() - w / 2, c.y() - h / 2, w, h);
    }

    /** Where the last step of {@code path} lands, its first node showing at {@code at}. */
    static Rect along(List<Tree.Step> path, Rect at) {
        Rect now = at;
        for (Tree.Step step : path) now = predicted(step, now);
        return now;
    }

    /**
     * The nodes of {@code tree} but {@code target} that show once on {@code frame}, by id, in the tree's order: a
     * picture that matches twice tells no place.
     */
    private static Map<String, Rect> anchors(Reach reach, Tree tree, String target, Map<String, Button> buttons,
                                             BufferedImage frame) {
        Map<String, Rect> found = new LinkedHashMap<>();
        for (ImageTemplate picture : tree.members()) {
            if (picture == null || picture.id().equals(target)) continue;
            List<Reach.Seen> copies = reach.copies(buttonOf(picture, buttons), null, Order.NORTH, frame);
            if (copies.size() == 1) found.put(picture.id(), copies.getFirst().place());
        }
        return found;
    }

    private static Button buttonOf(ImageTemplate picture, Map<String, Button> buttons) {
        Button button = buttons.get(picture.id());
        return button != null ? button : Button.of(picture);
    }

    /**
     * The view once it holds still after a move: looked at every {@link Reach#SCROLL_SETTLE_MS} until two looks in a
     * row show the nodes seen before the move at the same places, away from where they were, for up to
     * {@link Scroll#SETTLE}. Nodes still where they were are looked at to the end of that time, and at least twice: a
     * game may start gliding late, and a slow look can pass the time on the first one. Empty when the game could not
     * be captured or the walk was stopped.
     */
    private static Optional<BufferedImage> settled(Reach reach, Tree tree, Map<String, Rect> seen,
                                                   Map<String, Button> buttons) {
        long until = reach.nowMs() + Scroll.SETTLE.toMillis();
        Map<String, Rect> last = null;
        while (true) {
            reach.pause(Reach.SCROLL_SETTLE_MS);
            Optional<BufferedImage> frame = reach.look();
            if (frame.isEmpty() || Thread.currentThread().isInterrupted()) return Optional.empty();
            Map<String, Rect> now = new LinkedHashMap<>();
            for (ImageTemplate picture : tree.members()) {
                if (picture == null || !seen.containsKey(picture.id())) continue;
                List<Reach.Seen> copies = reach.copies(buttonOf(picture, buttons), null, Order.NORTH, frame.get());
                if (copies.size() == 1) now.put(picture.id(), copies.getFirst().place());
            }
            boolean held = last != null && Reach.still(last, now) && (seen.isEmpty() || Reach.moved(seen, now));
            if (held || last != null && reach.nowMs() >= until) return frame;
            last = now;
        }
    }

    /** Whether every node of {@code before} still shows within {@link Reach#STILL_PX} of where it was. */
    private static boolean unmoved(Map<String, Rect> before, Map<String, Rect> after) {
        if (before.isEmpty()) return false;
        for (Map.Entry<String, Rect> was : before.entrySet()) {
            Rect now = after.get(was.getKey());
            if (now == null) return false;
            Point a = was.getValue().center();
            Point b = now.center();
            if (Math.abs(a.x() - b.x()) > Reach.STILL_PX || Math.abs(a.y() - b.y()) > Reach.STILL_PX) return false;
        }
        return true;
    }

    private static long distance(Point a, Point b) {
        long dx = a.x() - b.x();
        long dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    /** A node's cut width: where it was cut when known, else its picture's. */
    private static int width(ImageTemplate picture) {
        return picture.place() != null ? picture.place().width() : picture.width();
    }

    private static int height(ImageTemplate picture) {
        return picture.place() != null ? picture.place().height() : picture.height();
    }
}
