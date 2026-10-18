package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.plugin.map.TreeView.Place;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 🗺's drawing of a tree lays each node at the sum of its offsets from the first, each in its own node's size. */
class TreeViewTest {

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    private static Map<String, Place> byId(List<Place> places) {
        return places.stream().collect(Collectors.toMap(Place::id, p -> p));
    }

    @Test
    void aNodeSitsAtItsParentsCentrePlusItsOffsetInTheParentsSize() {
        Tree tree = Tree.of(List.of(Node.of(picture("a")),
                Node.of(picture("b")).after(List.of(picture("a"))).beside(List.of(Beside.of(picture("a"), 1.5, 0))),
                Node.of(picture("c")).after(List.of(picture("b"))).beside(List.of(Beside.of(picture("b"), 0, -2)))));
        Map<String, Size> sizes = Map.of("a", new Size(40, 20), "b", new Size(60, 30), "c", new Size(10, 10));
        Map<String, Place> at = byId(TreeView.layout(tree, sizes::get));
        assertEquals(0, at.get("a").x());
        assertEquals(0, at.get("a").y());
        assertEquals(60, at.get("b").x(), "1.5 of a's 40 px width");
        assertEquals(0, at.get("b").y());
        assertEquals(60, at.get("c").x());
        assertEquals(-60, at.get("c").y(), "-2 of b's 30 px height");
        assertTrue(at.values().stream().allMatch(Place::placed));
    }

    @Test
    void anOffsetWrittenOnTheFirstNodesChildPlacesItsParentTheOtherWay() {
        // b is first, so a is placed from b: back along b's offset from a, in a's size.
        Tree tree = Tree.of(List.of(
                Node.of(picture("b")).after(List.of(picture("a"))).beside(List.of(Beside.of(picture("a"), 1, 1))),
                Node.of(picture("a"))));
        Map<String, Place> at = byId(TreeView.layout(tree, Map.of("a", new Size(50, 40), "b", new Size(50, 40))::get));
        assertEquals(-50, at.get("a").x());
        assertEquals(-40, at.get("a").y());
    }

    @Test
    void aNodeNoOffsetReachesSitsBelowThePlacedOnesAndAPictureNotCutHasAStandInSize() {
        Tree tree = Tree.of(List.of(Node.of(picture("a")),
                Node.of(picture("b")).beside(List.of(Beside.of(picture("a"), 0, 1))),
                Node.of(picture("lost"))));
        Map<String, Place> at = byId(TreeView.layout(tree, Map.of("a", new Size(20, 20), "b", new Size(20, 20))::get));
        Place lost = at.get("lost");
        assertFalse(lost.placed());
        assertEquals(TreeView.UNKNOWN.width(), lost.width());
        assertTrue(lost.top() > at.get("b").bottom(), "below b, the lowest placed: " + lost);
    }

    @Test
    void aNodeDroppedSnapsBesideTheNearestLinkedNodePlacedInThatNodesSize() {
        // b is linked to a by a line, lost to nothing; c is placed but not linked to b.
        Tree tree = Tree.of(List.of(Node.of(picture("a")),
                Node.of(picture("b")).after(List.of(picture("a"))),
                Node.of(picture("c")).beside(List.of(Beside.of(picture("a"), 3, 0))),
                Node.of(picture("lost"))));
        List<Place> places = TreeView.layout(tree, Map.of("a", new Size(40, 20), "b", new Size(10, 10),
                "c", new Size(40, 20))::get);
        TreeView.Drop drop = TreeView.snapped(tree, places, "b", 62.4, -31.4).orElseThrow();
        assertEquals("a", drop.other());
        assertEquals(1.55, drop.across(), "62.4 px is 1.56 of a's 40 px width, to the nearest 0.05");
        assertEquals(-1.55, drop.down());
        assertTrue(TreeView.snapped(tree, places, "lost", 0, 0).isEmpty(), "linked to nothing placed");
        assertEquals("a", TreeView.snapped(tree, places, "c", 100, 0).orElseThrow().other(), "an offset links too");
        // c is placed from a: dropped nearer d, a node it is also linked to, it moves its offset from a.
        Tree both = tree.nodes(List.of(tree.nodes().get(0), tree.nodes().get(1),
                Node.of(picture("c")).beside(List.of(Beside.of(picture("a"), 3, 0))),
                Node.of(picture("d")).after(List.of(picture("c"))).beside(List.of(Beside.of(picture("a"), 0, 5)))));
        List<Place> laid = TreeView.layout(both, id -> new Size(40, 20));
        assertEquals("a", TreeView.snapped(both, laid, "c", 0, 95).orElseThrow().other());
    }

    @Test
    void anEmptyTreeLaysOutNothing() {
        assertEquals(List.of(), TreeView.layout(Tree.of(List.of()), id -> null));
    }
}
