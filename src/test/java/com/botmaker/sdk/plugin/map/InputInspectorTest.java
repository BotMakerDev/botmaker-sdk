package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.internal.screens.MapFixtures;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.plugin.map.InputInspector.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void aKeyAnotherHotkeyOfTheScreenHasIsTaken() {
        MapScreen home = MapFixtures.screen("Home", Hotkey.of(Key.Q), MapFixtures.click("buy"));
        String q = MapFixtures.fieldOf(Hotkey.of(Key.Q));
        assertEquals(Optional.of(q), InputInspector.keyTaken(home, null, Key.Q));
        assertEquals(Optional.empty(), InputInspector.keyTaken(home, q, Key.Q), "its own key is its own");
        assertEquals(Optional.empty(), InputInspector.keyTaken(home, null, Key.W));
    }
}
