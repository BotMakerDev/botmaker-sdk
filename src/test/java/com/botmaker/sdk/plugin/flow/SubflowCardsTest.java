package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sub-flow card on the canvas (2026-10-12): its label, its ports — the outcomes the flow it runs ends on — the
 * wires in and out of it saved as edges and read back, and which flows one may run.
 */
class SubflowCardsTest {

    /** Each activity enum the bot declares, with its constants. */
    private static final Map<String, List<String>> DECLARED = Map.of(
            "Claim", List.of("DONE", "NONE_LEFT"),
            "Rest", List.of("NEXT"),
            "Collect", List.of("NEXT", "BAG_FULL"));

    private static Flow.Step activity(String type) {
        return new Flow.Step(new Activity(type), null, "", true, false, false, Activity.NONE);
    }

    private static Flow.Step card(String flow) {
        return Flow.subflow(BotConstants.flow(flow));
    }

    /** DAILY: Claim, which goes on to Rest when nothing is left; ends on Claim · Done and Rest · Next. */
    private static final Flow DAILY = new Flow(List.of(activity("Claim"), activity("Rest")),
            List.of(new Flow.Edge(BotConstants.outcome("Claim", "NONE_LEFT"), new Activity("Rest"))),
            new Activity("Claim"), Flow.Limits.DEFAULT);

    /** MAIN: Collect, into DAILY when its bag is full; DAILY's Done back to Collect, and a wire on a gone exit. */
    private static final Flow MAIN = new Flow(List.of(activity("Collect"), card("DAILY")),
            List.of(new Flow.Edge(BotConstants.outcome("Collect", "BAG_FULL"), new Flow.Sub(BotConstants.flow("DAILY"))),
                    new Flow.Edge(BotConstants.flow("DAILY"), BotConstants.outcome("Claim", "DONE"),
                            new Activity("Collect")),
                    new Flow.Edge(BotConstants.flow("DAILY"), BotConstants.outcome("Claim", "GONE"),
                            new Activity("Collect"))),
            new Activity("Collect"), Flow.Limits.DEFAULT);

    /** WEEKLY runs DAILY and nothing else. */
    private static final Flow WEEKLY = new Flow(List.of(card("DAILY")), List.of(),
            new Flow.Sub(BotConstants.flow("DAILY")), Flow.Limits.DEFAULT);

    private static FlowRefs refs(Map<String, Flow> flows) {
        return new FlowRefs(List.copyOf(flows.keySet()), name -> Optional.ofNullable(flows.get(name)), DECLARED);
    }

    private static FlowRefs bot() {
        Map<String, Flow> flows = new java.util.LinkedHashMap<>();
        flows.put("DAILY", DAILY);
        flows.put("MAIN", MAIN);
        flows.put("WEEKLY", WEEKLY);
        return refs(flows);
    }

    @Test
    void aSubflowCardIsLabelledByItsFlowAndNoActivityIs() {
        assertEquals("▸ DAILY", card("DAILY").label());
        assertEquals("DAILY", FlowNames.subflowOf("▸ DAILY"));
        assertNull(FlowNames.subflowOf("Daily"), "an activity's label names no flow");
        assertEquals(new Flow.Sub(BotConstants.flow("DAILY")), FlowNames.node("▸ DAILY"));
        assertEquals(new Activity("Daily"), FlowNames.node("Daily"));
        assertEquals("▸ DAILY", FlowNames.layoutKey("▸ DAILY"), "kept apart from the Daily activity's place");
        assertEquals("Daily", FlowNames.layoutKey("daily"));
    }

    @Test
    void aCardsExitsAreTheEndsOfItsFlowAndAWireOnAGoneOneIsBroken() {
        FlowRefs refs = bot();
        assertEquals(List.of("Claim · Done", "Rest · Next"), refs.exits("DAILY"));
        assertEquals(List.of("Claim · Done", "Rest · Next"), refs.exits("WEEKLY"),
                "a card inside adds its own flow's ends");
        FlowRefs.Exits exits = refs.exitsIn(MAIN, "DAILY");
        assertEquals(List.of("Claim · Done", "Rest · Next"), exits.live());
        assertEquals(List.of("Claim · Gone"), exits.broken());
        assertEquals(List.of("Claim · Done", "Rest · Next", "Claim · Gone"), exits.all());
        assertTrue(refs.exits("NOWHERE").isEmpty(), "a flow the bot lacks ends on nothing");
    }

    @Test
    void aFlowThatRunsTheHostIsNeverOffered() {
        FlowRefs refs = bot();
        assertEquals(List.of("MAIN", "WEEKLY"), refs.usersOf("DAILY"));
        assertTrue(refs.runs("MAIN", "DAILY"));
        assertFalse(refs.runs("DAILY", "MAIN"));
        assertEquals(List.of(), refs.offerable("DAILY"), "itself, and both flows that run it");
        assertEquals(List.of("DAILY", "WEEKLY"), refs.offerable("MAIN"));
        assertEquals(List.of("DAILY"), refs.subflows("MAIN"));
    }

    @Test
    void aHandWrittenCycleEndsOnNothingNewInsteadOfLooping() {
        Flow ping = new Flow(List.of(card("PONG")), List.of(), new Flow.Sub(BotConstants.flow("PONG")),
                Flow.Limits.DEFAULT);
        Flow pong = new Flow(List.of(card("PING")), List.of(), new Flow.Sub(BotConstants.flow("PING")),
                Flow.Limits.DEFAULT);
        Map<String, Flow> flows = new java.util.LinkedHashMap<>();
        flows.put("PING", ping);
        flows.put("PONG", pong);
        FlowRefs refs = refs(flows);
        assertTrue(refs.exits("PING").isEmpty());
        assertTrue(refs.runs("PING", "PONG") && refs.runs("PONG", "PING"));
    }

    @Test
    void theCanvasSavesASubflowCardAndItsWiresAndReadsThemBack() {
        ActivityDraft collect = new ActivityDraft("Collect", "", true, List.of("Next", "Bag full"), false, 0, 0);
        ActivityDraft daily = new ActivityDraft("▸ DAILY", "Once a day", true,
                List.of("Claim · Done", "Rest · Next"), false, 200, 0);
        List<Arrow> arrows = List.of(new Arrow("Collect", "▸ DAILY", "Bag full"),
                new Arrow("▸ DAILY", "Collect", "Claim · Done"), new Arrow("▸ DAILY", "Collect", Arrow.DISABLED));
        Board board = ActivityFlowDialog.board(List.of(collect, daily), arrows, "▸ DAILY", Flow.Limits.DEFAULT);
        Flow flow = board.flow();

        assertEquals(new Flow.Sub(BotConstants.flow("DAILY")), flow.start(), "a flow may start at a sub-flow card");
        assertEquals(Map.of("Collect", List.of("Next", "Bag full")), board.exits(), "no enum is written for the card");
        Flow.Step card = flow.steps().get(1);
        assertNull(card.body());
        assertEquals("Once a day", card.description());
        assertEquals(new Activity("Collect"), card.whenOff());
        Flow.Edge out = flow.edges().get(1);
        assertEquals(BotConstants.flow("DAILY"), out.via());
        assertEquals(BotConstants.outcome("Claim", "DONE"), out.on());

        assertEquals(arrows.subList(0, 2), flow.edges().stream().map(Arrow::of).toList(), "read back as drawn");
        assertEquals(arrows.get(2), Arrow.off(card));
        assertEquals(java.util.Set.of("Collect"), FlowEdits.labels(flow), "a card is not an activity");
        assertNull(FlowEdits.validate(board));
    }

    @Test
    void removingASubflowCardTakesItsWiresAndAnActivityRenameKeepsThemOut() {
        Board board = new Board(MAIN, Map.of("Collect", List.of("Next", "Bag full")));
        Board renamed = FlowEdits.renameActivity(board, "Collect", "Gather");
        Flow.Edge out = renamed.flow().edges().get(1);
        assertEquals(BotConstants.flow("DAILY"), out.via(), "a wire out of the card stays one");
        assertEquals(new Activity("Gather"), out.to());

        Board removed = FlowEdits.removeActivity(board, "▸ DAILY");
        assertEquals(List.of(new Activity("Collect")), removed.flow().steps().stream().map(Flow.Step::node).toList());
        assertTrue(removed.flow().edges().isEmpty());
    }

    @Test
    void anUnwiredExitIsListed() {
        List<Arrow> edges = List.of(new Arrow("▸ DAILY", "Collect", "Claim · Done"));
        assertEquals(List.of("Rest · Next"), FlowRules.unwired("▸ DAILY", List.of("Claim · Done", "Rest · Next"), edges));
    }
}
