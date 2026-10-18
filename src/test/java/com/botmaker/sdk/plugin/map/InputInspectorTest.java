package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.MapFixtures;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.plugin.map.InputInspector.Result;
import com.botmaker.sdk.plugin.map.InputInspector.ScrollKind;
import com.botmaker.sdk.plugin.map.InputInspector.TreeScroll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the picked input's editors write, as plain values. */
class InputInspectorTest {

    private static final Look OFF = Look.of(MapFixtures.picture("buy_off"), State.DISABLED);
    private static final Look BADGE = Look.of(MapFixtures.picture("buy_badge"), State.BADGE);
    private static final Look PICKED = Look.of(MapFixtures.picture("buy_on"), State.SELECTED);

    @Test
    void aLookMovesTakesAnotherStateOrGoes() {
        List<Look> looks = List.of(OFF, BADGE, PICKED);
        assertEquals(List.of(BADGE, OFF, PICKED), InputInspector.moved(looks, 0, 1));
        assertEquals(List.of(PICKED, OFF, BADGE), InputInspector.moved(looks, 2, 0));
        assertEquals(looks, InputInspector.moved(looks, 0, -1), "nothing above the first");
        assertEquals(looks, InputInspector.moved(looks, 2, 3), "nothing below the last");
        assertEquals(List.of(OFF, PICKED), InputInspector.without(looks, 1));
        assertEquals(List.of(OFF, Look.of(BADGE.picture(), State.BUSY), PICKED),
                InputInspector.restated(looks, 1, State.BUSY));
    }

    @Test
    void theResultShownIsWhatTheInputDid() {
        Button buy = MapFixtures.click("buy");
        assertEquals(Result.UNTRIED, Result.of(buy, false));
        assertEquals(Result.NOTHING, Result.of(buy.tried(), false));
        assertEquals(Result.LEAVES, Result.of(buy.leaves(), false));
        assertEquals(Result.OPENS, Result.of(buy, true), "a way it opens says it first");
    }

    @Test
    void aWayPickIsWrittenAsItsFactoryWritesIt() {
        assertEquals(Pick.untilNone(), InputInspector.wayPick(Pick.Mode.UNTIL_NONE, 3, Order.WEST),
                "until none counts from the north, of no copy");
        assertEquals(Pick.each(Order.EAST), InputInspector.wayPick(Pick.Mode.EACH, 3, Order.EAST));
        assertEquals(Pick.nth(3, Order.EAST), InputInspector.wayPick(Pick.Mode.NTH, 3, Order.EAST));
        assertEquals(Pick.first(Order.SOUTH), InputInspector.wayPick(Pick.Mode.FIRST, 3, Order.SOUTH));
    }

    @Test
    void aStripsScrollIsTheWheelADragOrNone() {
        assertEquals(Scroll.wheel(-5), InputInspector.scrollOf(ScrollKind.WHEEL, -5, Order.EAST, 300, 500));
        assertEquals(Scroll.drag(Order.WEST, 300, Duration.ofMillis(500)),
                InputInspector.scrollOf(ScrollKind.DRAG, -5, Order.WEST, 300, 500));
        assertEquals(Order.NORTH, InputInspector.scrollOf(ScrollKind.DRAG, 0, Order.CENTER, 300, 500).drag(),
                "a drag goes toward a side");
        assertFalse(InputInspector.scrollOf(ScrollKind.NONE, -5, Order.EAST, 300, 500).moves(), "none: the strip off");
        assertEquals(ScrollKind.WHEEL, ScrollKind.of(Scroll.wheel(3).step(120)));
        assertEquals(ScrollKind.DRAG, ScrollKind.of(Scroll.drag(Order.NORTH, 200, Duration.ZERO)));
        assertEquals(ScrollKind.NONE, ScrollKind.of(Scroll.wheel(0)));
        assertEquals(ScrollKind.NONE, ScrollKind.of(null));
    }

    @Test
    void aStripTakesOnlyAButtonInNoStripOrTree() {
        Button tavern = MapFixtures.click("tavern");
        Button arena = MapFixtures.click("arena");
        Button guild = MapFixtures.click("guild");
        Button rune = MapFixtures.click("rune");
        MapScreen home = MapFixtures.screen("Home", tavern, arena, guild, rune, Hotkey.of(Key.Q),
                Strip.of(Scroll.wheel(-5), List.of(tavern.picture(), arena.picture())),
                Tree.of(List.of(Node.of(rune.picture()))));
        assertEquals(List.of(guild.picture()), InputInspector.joinable(home));
        assertEquals(List.of(), InputInspector.joinable(null));
        assertEquals(List.of("b", "a", "c"), InputInspector.moved(List.of("a", "b", "c"), 0, 1), "members move too");
    }

    @Test
    void aTreesScrollIsADragTheWheelOrNoneAndKeepsItsTraits() {
        Tree tree = Tree.of(List.of(Node.of(MapFixtures.picture("a")))).dragged().sometimes();
        assertEquals(TreeScroll.DRAG, TreeScroll.of(tree));
        Tree wheel = InputInspector.scrolled(tree, TreeScroll.WHEEL, -3);
        assertEquals(TreeScroll.WHEEL, TreeScroll.of(wheel));
        assertEquals(-3, wheel.notches());
        assertFalse(wheel.isDragged());
        assertTrue(wheel.comesAndGoes(), "its traits stay");
        Tree fits = InputInspector.scrolled(wheel, TreeScroll.NONE, -3);
        assertEquals(TreeScroll.NONE, TreeScroll.of(fits));
        assertFalse(fits.scrolls(), "a tree that fits is still a tree, scrolled by nothing");
        assertEquals(0, InputInspector.scrolled(tree, TreeScroll.DRAG, -3).notches(), "notches are the wheel's");
    }

    @Test
    void aNodeSaysWhatUnlocksItAndWhereItSits() {
        ImageTemplate a = MapFixtures.picture("a");
        assertEquals("No line leads to it; no offset written on it.", InputInspector.nodeSaid(Node.of(a)));
        assertEquals("Unlocked by a; placed (1.2, -0.6) from a.", InputInspector.nodeSaid(Node.of(
                MapFixtures.picture("b")).after(List.of(a)).beside(List.of(Beside.of(a, 1.2, -0.6)))));
    }

    @Test
    void aKeyAnotherHotkeyOfTheScreenHasIsTaken() {
        MapScreen home = MapFixtures.screen("Home", Hotkey.of(Key.Q), MapFixtures.click("buy"));
        String q = MapFixtures.fieldOf(Hotkey.of(Key.Q));
        assertEquals(Optional.of(q), InputInspector.keyTaken(home, null, Key.Q));
        assertEquals(Optional.empty(), InputInspector.keyTaken(home, q, Key.Q), "its own key is its own");
        assertEquals(Optional.empty(), InputInspector.keyTaken(home, null, Key.W));
    }
}
