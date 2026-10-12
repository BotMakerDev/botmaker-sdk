package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.flow.FlowLayout;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which flow is edited, how a flow is named, and its cards following a rename or a delete. */
class FlowValueTest {

    @Test
    void mainIsTheDefaultElseTheFirst() {
        assertEquals("MAIN", FlowValue.defaultName(List.of()));
        assertEquals("MAIN", FlowValue.defaultName(List.of("DAILY", "MAIN")));
        assertEquals("DAILY", FlowValue.defaultName(List.of("DAILY", "NIGHTLY")));
    }

    @Test
    void aFlowNameIsAConstantNoOtherFlowHas() {
        assertNull(FlowValue.nameProblem("daily", "DAILY", List.of("MAIN")));
        assertTrue(FlowValue.nameProblem("Main", "MAIN", List.of("MAIN")).contains("already has a flow Flows.MAIN"));
        assertTrue(FlowValue.nameProblem("2nd", null, List.of()).contains("makes no constant name"));
    }

    @Test
    void aFlowsCardsFollowItsRenameAndGoWithIt() {
        Map<String, FlowLayout.Cards> flows = new LinkedHashMap<>();
        flows.put("MAIN", FlowLayout.cards(Map.of("Collect", FlowLayout.at(1, 2))));
        flows.put("DAILY", FlowLayout.cards(Map.of("Rest", FlowLayout.at(3, 4))));
        FlowLayout layout = FlowLayout.of(flows, false);

        FlowLayout renamed = FlowValue.moved(layout, "DAILY", "FARM");
        assertEquals(List.of("MAIN", "FARM"), List.copyOf(renamed.flows().keySet()));
        assertEquals(FlowLayout.at(3, 4), renamed.cardsOf("FARM").spot("Rest"));
        assertEquals(false, renamed.goHomeByDefault());

        assertEquals(List.of("MAIN"), List.copyOf(FlowValue.moved(layout, "DAILY", null).flows().keySet()));
        assertSame(layout, FlowValue.moved(layout, "NIGHTLY", "X"), "a flow with no cards moves nothing");
    }

    @Test
    void aSubflowCardKeepsItsPlaceWhenItsFlowIsRenamed() {
        Map<String, FlowLayout.Cards> flows = new LinkedHashMap<>();
        flows.put("MAIN", FlowLayout.cards(Map.of("Collect", FlowLayout.at(1, 2), "▸ DAILY", FlowLayout.at(5, 6))));
        flows.put("DAILY", FlowLayout.cards(Map.of("Rest", FlowLayout.at(3, 4))));
        FlowLayout renamed = FlowValue.subflowMoved(FlowLayout.of(flows, true), "DAILY", "FARM");
        assertEquals(FlowLayout.at(5, 6), renamed.cardsOf("MAIN").spot("▸ FARM"));
        assertNull(renamed.cardsOf("MAIN").spot("▸ DAILY"));
        assertEquals(FlowLayout.at(1, 2), renamed.cardsOf("MAIN").spot("Collect"));
    }
}
