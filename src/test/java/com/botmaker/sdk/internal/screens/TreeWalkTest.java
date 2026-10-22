package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A node of a tree brought into view by its offsets from a node in view. */
class TreeWalkTest {

    private static ImageTemplate node(String id) {
        return new ImageTemplate("images/" + id + ".png").at(new Rect(0, 0, 100, 100));
    }

    private static final ImageTemplate ROOT = node("root");
    private static final ImageTemplate FIST = node("fist");
    private static final ImageTemplate LONE = node("lone");
    private static final BufferedImage FRAME = new BufferedImage(800, 600, BufferedImage.TYPE_BYTE_GRAY);

    /**
     * A plane of 100×100 nodes at fixed centres, seen through an 800×600 view whose top-left sits at {@code (x, y)} on
     * it, never past the plane's ends. A drag moves the view by {@code from - to} times {@code gain}; a notch moves it
     * 50 px down per notch turned negative. Time is counted, not waited.
     */
    private static class Plane implements Reach {
        final Map<String, List<Point>> nodes = new HashMap<>();
        final List<String> done = new ArrayList<>();
        final int width;
        final int height;
        double gain = 1;
        int x;
        int y;
        long now;

        Plane(int width, int height) {
            this.width = width;
            this.height = height;
        }

        Plane at(ImageTemplate picture, int px, int py) {
            nodes.computeIfAbsent(picture.id(), k -> new ArrayList<>()).add(new Point(px, py));
            return this;
        }

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(FRAME);
        }

        @Override
        public List<Rect> findAll(ImageTemplate picture, BufferedImage frame) {
            List<Rect> found = new ArrayList<>();
            for (Point c : nodes.getOrDefault(picture.id(), List.of())) {
                Rect r = new Rect(c.x() - x - 50, c.y() - y - 50, 100, 100);
                if (r.x() >= 0 && r.y() >= 0 && r.x() + 100 <= 800 && r.y() + 100 <= 600) found.add(r);
            }
            return found;
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            return findAll(picture, frame).stream().findFirst();
        }

        /** Whether the pixels tell how far the view moved; none of these frames do unless set. */
        boolean pixels;
        Point shifted;

        @Override
        public Optional<Point> shift(BufferedImage before, BufferedImage after) {
            return pixels ? Optional.ofNullable(shifted) : Optional.empty();
        }

        @Override
        public void drag(Point from, Point to, Duration time) {
            done.add("drag " + (to.x() - from.x()) + " " + (to.y() - from.y()));
            int wasX = x;
            int wasY = y;
            x = Math.clamp(Math.round(x + (from.x() - to.x()) * gain), 0, width - 800);
            y = Math.clamp(Math.round(y + (from.y() - to.y()) * gain), 0, height - 600);
            shifted = new Point(wasX - x, wasY - y);
        }

        @Override
        public void scroll(Point at, int notches) {
            done.add("scroll " + notches);
            int wasY = y;
            y = Math.clamp(y - notches * 50, 0, height - 600);
            shifted = new Point(0, wasY - y);
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
            done.add("click");
        }

        @Override
        public void keys(Combo combo) {
            done.add("key " + combo.keys().getLast().name());
        }

        @Override
        public void type(String text, boolean clears, boolean submits) {
            done.add("type " + text);
        }

        @Override
        public long nowMs() {
            return now;
        }

        @Override
        public void pause(long ms) {
            now += ms;
        }

        TreeWalk.Walked walk(ImageTemplate target, Tree tree) {
            return TreeWalk.reach(this, Button.of(target), tree, Map.of(), FRAME);
        }
    }

    /** {@code ROOT} and {@code FIST}, {@code FIST} {@code across} root's widths right of it and {@code down} below. */
    private static Tree pair(double across, double down) {
        return Tree.of(List.of(Node.of(ROOT), Node.of(FIST).beside(List.of(Beside.of(ROOT, across, down))))).dragged();
    }

    @Test
    void aNodeInViewIsSeenWithNoMove() {
        Plane plane = new Plane(4000, 600).at(ROOT, 400, 300).at(FIST, 600, 300);
        TreeWalk.Walked walked = plane.walk(FIST, pair(2, 0));
        assertEquals(new Rect(550, 250, 100, 100), walked.seen().place());
        assertEquals(List.of(), plane.done);
    }

    @Test
    void aNodeOffToTheEastIsDraggedTo() {
        Plane plane = new Plane(4000, 600).at(ROOT, 400, 300).at(FIST, 1500, 300);
        TreeWalk.Walked walked = plane.walk(FIST, pair(11, 0));
        assertTrue(walked.found().isPresent(), walked.why());
        assertTrue(plane.done.getFirst().startsWith("drag -"), "dragged west: " + plane.done);
        assertEquals(new Point(400, 300), walked.seen().place().center(), "brought to the middle");
    }

    @Test
    void aFarNodeTakesSeveralMovesEachUnderTheFrame() {
        Plane plane = new Plane(4000, 600).at(ROOT, 400, 300).at(FIST, 3400, 300);
        TreeWalk.Walked walked = plane.walk(FIST, pair(30, 0));
        assertTrue(walked.found().isPresent(), walked.why());
        assertTrue(plane.done.size() >= 4, "several moves: " + plane.done);
        assertTrue(plane.done.stream().allMatch(d -> Math.abs(Integer.parseInt(d.split(" ")[1])) <= 640),
                "each under the frame: " + plane.done);
    }

    @Test
    void anOvershootIsCorrected() {
        Plane plane = new Plane(4000, 600).at(ROOT, 400, 300).at(FIST, 2400, 300);
        plane.gain = 1.3;
        TreeWalk.Walked walked = plane.walk(FIST, pair(20, 0));
        assertTrue(walked.found().isPresent(), walked.why() + " " + plane.done);
    }

    @Test
    void aRepeatedIconIsTheCopyNearestItsPlace() {
        // The fist's picture also matches a node to its left; its offset puts it at (600, 300).
        Plane plane = new Plane(800, 600).at(ROOT, 400, 300).at(FIST, 100, 300).at(FIST, 600, 300);
        TreeWalk.Walked walked = plane.walk(FIST, pair(2, 0));
        assertEquals(new Point(600, 300), walked.seen().place().center());
    }

    @Test
    void noNodeInViewIsMissedNotDragged() {
        Plane plane = new Plane(4000, 600).at(ROOT, 3000, 300).at(FIST, 3200, 300);
        TreeWalk.Walked walked = plane.walk(FIST, pair(2, 0));
        assertEquals(TreeWalk.Missed.NO_NODE_IN_VIEW, walked.missed());
        assertEquals(List.of(), plane.done);
    }

    @Test
    void aTargetWithNoChainIsMissed() {
        Plane plane = new Plane(4000, 600).at(ROOT, 400, 300).at(LONE, 2000, 300);
        Tree tree = Tree.of(List.of(Node.of(ROOT), Node.of(LONE))).dragged();
        TreeWalk.Walked walked = plane.walk(LONE, tree);
        assertEquals(TreeWalk.Missed.NO_PATH, walked.missed());
        assertTrue(walked.why().contains("root"), walked.why());
        assertEquals(List.of(), plane.done);
    }

    @Test
    void aViewThatStopsMovingMetItsEnd() {
        // The offset puts the fist past the plane's east end: the view stops there.
        Plane plane = new Plane(1000, 600).at(ROOT, 700, 300);
        TreeWalk.Walked walked = plane.walk(FIST, pair(10, 0));
        assertEquals(TreeWalk.Missed.END_MET, walked.missed(), walked.why());
        assertTrue(plane.done.size() <= 2, "no endless drag: " + plane.done);
        assertNull(walked.seen());
    }

    @Test
    void aWheelTreeTurnsTheWheel() {
        Plane plane = new Plane(800, 2000).at(ROOT, 400, 300).at(FIST, 400, 1000);
        // How far a notch moves the view only the pixels tell.
        plane.pixels = true;
        Tree tree = Tree.of(List.of(Node.of(ROOT), Node.of(FIST).beside(List.of(Beside.of(ROOT, 0, 7))))).wheel(-3);
        TreeWalk.Walked walked = plane.walk(FIST, tree);
        assertTrue(walked.found().isPresent(), walked.why() + " " + plane.done);
        assertTrue(!plane.done.isEmpty() && plane.done.stream().allMatch(d -> d.equals("scroll -3")), "" + plane.done);
    }

    @Test
    void anOffsetWrittenOnTheOtherNodeIsReadBackwards() {
        // The root is written beside the fist: the fist sits 3 widths left of the root.
        Tree tree = Tree.of(List.of(Node.of(FIST), Node.of(ROOT).beside(List.of(Beside.of(FIST, 3, 0))))).dragged();
        Plane plane = new Plane(800, 600).at(ROOT, 500, 300).at(FIST, 200, 300).at(FIST, 700, 300);
        assertEquals(new Point(200, 300), plane.walk(FIST, tree).seen().place().center());
    }
}
