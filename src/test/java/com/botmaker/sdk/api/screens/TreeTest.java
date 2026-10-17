package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeTest {

    private static ImageTemplate pic(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    static final ImageTemplate DAMAGE = pic("damage"), HEALTH = pic("health"), ARMOR = pic("armor"),
            FIST = pic("fist"), LONE = pic("lone");

    static final Tree TREE = Tree.of(List.of(
            Node.of(DAMAGE),
            Node.of(HEALTH).after(List.of(DAMAGE)).beside(List.of(Beside.of(DAMAGE, 1.2, -0.6))),
            Node.of(ARMOR).after(List.of(DAMAGE)).beside(List.of(Beside.of(DAMAGE, 1.2, 0.6))),
            Node.of(FIST).after(List.of(HEALTH)).beside(List.of(Beside.of(HEALTH, 1.2, -1.2))),
            Node.of(LONE))).dragged();

    private static List<String> steps(List<Tree.Step> path) {
        return path.stream().map(s -> s.from().id() + ">" + s.to().id()).toList();
    }

    @Test
    void thePathFollowsTheOffsetsFromANodeInView() {
        List<Tree.Step> path = TREE.path(Set.of("damage"), "fist");
        assertEquals(List.of("damage>health", "health>fist"), steps(path));
        assertFalse(path.getFirst().reversed());
    }

    @Test
    void anOffsetIsReadBackwards() {
        List<Tree.Step> path = TREE.path(Set.of("fist"), "armor");
        assertEquals(List.of("fist>health", "health>damage", "damage>armor"), steps(path));
        assertTrue(path.get(0).reversed());
        assertTrue(path.get(1).reversed());
        assertFalse(path.get(2).reversed());
    }

    @Test
    void theShortestPathStartsAtTheNearestNodeInView() {
        assertEquals(List.of("health>fist"), steps(TREE.path(Set.of("damage", "health"), "fist")));
    }

    @Test
    void aNodeWithNoOffsetChainHasNoPath() {
        assertTrue(TREE.path(Set.of("damage"), "lone").isEmpty());
        assertTrue(TREE.path(Set.of(), "fist").isEmpty());
    }

    @Test
    void aTreeScrollsByDragOrWheelOrNot() {
        assertTrue(TREE.scrolls());
        assertEquals(-3, Tree.of(List.of()).wheel(-3).notches());
        assertTrue(Tree.of(List.of()).wheel(-3).scrolls());
        assertFalse(Tree.of(List.of()).scrolls());
    }

    @Test
    void aNodeTakenOutLeavesNoLineOrOffsetNamingIt() {
        Tree without = TREE.without("health");
        assertFalse(without.has(HEALTH));
        assertTrue(without.node(FIST).orElseThrow().after().isEmpty());
        assertTrue(without.node(FIST).orElseThrow().beside().isEmpty());
        assertTrue(without.path(Set.of("damage"), "fist").isEmpty());
    }

    @Test
    void everyLookThatStopsAPressBlocks() {
        assertEquals(Set.of(State.DISABLED, State.LOCKED, State.DONE, State.BUSY),
                Arrays.stream(State.values()).filter(State::blocks).collect(Collectors.toSet()));
        for (State look : State.values()) assertEquals(look.blocks(), Pressed.blockedBy(look) != null, look.id());
    }
}
