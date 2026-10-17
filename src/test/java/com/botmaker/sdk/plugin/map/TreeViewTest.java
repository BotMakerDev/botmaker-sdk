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
    void anEmptyTreeLaysOutNothing() {
        assertEquals(List.of(), TreeView.layout(Tree.of(List.of()), id -> null));
    }
}
