package com.botmaker.sdk.plugin.flow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules the Activity Flow canvas enforces while wiring. Pure — no JavaFX involved.
 *
 * <p>Most of this file is about what is <em>no longer</em> rejected: forks, joins, self-wires and cycles were
 * all vetoed while the flow had to be a single linear chain, and each is now a shape the user is meant to
 * draw. Asserting they're allowed is the point — they are exactly what a silent regression would take away.
 */
public class FlowRulesTest {

    private static Arrow wire(String from, String to) {
        return new Arrow(from, to, "");
    }

    private static Arrow wire(String from, String to, String outcome) {
        return new Arrow(from, to, outcome);
    }

    private static final List<Arrow> A_TO_B = List.of(wire("A", "B"));

    @Test
    void aFreshWireBetweenUnconnectedActivitiesIsAdded() {
        assertEquals(List.of(wire("A", "B")), FlowRules.rewired(List.of(), "A", "", "B"));
        assertEquals(List.of(wire("A", "B"), wire("B", "C")), FlowRules.rewired(A_TO_B, "B", "", "C"));
    }

    @Test
    void anActivityMayNowWireToItself() {
        // "Didn't work — try again" is a self-wire. The step budget is what stops it spinning, not the editor.
        assertEquals(List.of(wire("A", "A", "FAILED")), FlowRules.rewired(List.of(), "A", "FAILED", "A"));
    }

    @Test
    void aForkOnDifferentOutcomesIsTheWholePoint() {
        // Two wires out of A, one per outcome — this is branching, and it used to be rejected outright.
        List<Arrow> edges = List.of(wire("A", "B", "BAG_FULL"));
        assertEquals(2, FlowRules.rewired(edges, "A", "NO_ORE", "C").size());
        assertEquals(2, FlowRules.rewired(edges, "A", "", "D").size(), "the default outcome is its own wire too");
    }

    @Test
    void aJoinIsAllowedSoBranchesCanMeetAgain() {
        List<Arrow> edges = List.of(wire("A", "C", "BAG_FULL"));
        assertEquals(2, FlowRules.rewired(edges, "B", "", "C").size());
    }

    @Test
    void aCycleIsAllowedBecauseItIsHowABotRepeats() {
        List<Arrow> chain = List.of(wire("A", "B"), wire("B", "C"));
        assertEquals(3, FlowRules.rewired(chain, "C", "DONE", "A").size());
    }

    /** One result still leads to one place: a second wire from the same port moves the first, in its place. */
    @Test
    void wiringAWiredPortMovesItsWire() {
        List<Arrow> edges = List.of(wire("A", "B", "BAG_FULL"), wire("B", "C"));
        assertEquals(List.of(wire("A", "C", "BAG_FULL"), wire("B", "C")),
                FlowRules.rewired(edges, "A", "BAG_FULL", "C"));
    }

    @Test
    void aBlankOutcomeIsTheSameWireAsAnExplicitNext() {
        // Persisted blank vs. "NEXT" must not become two competing wires out of the same port, and the moved
        // wire keeps the spelling the file already had.
        assertEquals(List.of(wire("A", "C", "")), FlowRules.rewired(List.of(wire("A", "B", "")), "A", "NEXT", "C"));
        assertEquals(List.of(wire("A", "C", "NEXT")), FlowRules.rewired(List.of(wire("A", "B", "NEXT")), "A", "", "C"));
    }

    @Test
    void aPortWithoutAWireHoldsNothing() {
        assertTrue(FlowRules.held(A_TO_B, "A", "BAG_FULL").isEmpty());
        assertEquals(wire("A", "B"), FlowRules.held(A_TO_B, "A", "NEXT").orElseThrow());
    }

    @Test
    void reachabilityStartsAtTheNamedStartNotAtWhateverWasPlacedFirst() {
        // Placement order is canvas insertion order and says nothing about the flow; the start node decides.
        List<String> placed = List.of("B", "C", "A");
        List<Arrow> edges = List.of(wire("A", "B"), wire("B", "C"));
        assertEquals(List.of("A", "B", "C"), FlowRules.reachable(placed, edges, "A"));
    }

    @Test
    void activitiesTheFlowNeverReachesAreOrphans() {
        List<String> placed = List.of("A", "B", "Idle");
        assertEquals(List.of("Idle"), FlowRules.orphans(placed, A_TO_B, "A"));
    }

    @Test
    void withNoWiresNothingIsAnOrphan() {
        // Nothing is wired yet, so there is no flow to be outside of — everything still runs, in list order.
        assertEquals(List.of(), FlowRules.orphans(List.of("A", "B"), List.of(), "A"));
    }

    @Test
    void anUnwiredCardOrphansOnlyItself() {
        // The old regression, now structurally impossible: the root used to be *inferred* as "a node nothing
        // wires into", so a lone un-wired card could outrank the real chain and orphan every wired activity.
        // With an explicit start there is nothing to infer, so placement order cannot matter.
        List<Arrow> edges = List.of(wire("A", "B"), wire("B", "C"));
        for (List<String> placed : List.of(
                List.of("D", "A", "B", "C"),   // the un-wired card first — the case that used to fail
                List.of("A", "B", "C", "D"),
                List.of("A", "D", "B", "C"))) {
            assertEquals(List.of("A", "B", "C"), FlowRules.reachable(placed, edges, "A"), "placed: " + placed);
            assertEquals(List.of("D"), FlowRules.orphans(placed, edges, "A"), "placed: " + placed);
        }
    }

    @Test
    void aSecondDisconnectedChainCountsAsOrphaned() {
        // Only what the start can reach runs, so the canvas warns about the rest rather than silently
        // picking one — even though both halves are perfectly well-formed.
        List<String> placed = List.of("A", "B", "X", "Y");
        List<Arrow> edges = List.of(wire("A", "B"), wire("X", "Y"));
        assertEquals(List.of("A", "B"), FlowRules.reachable(placed, edges, "A"));
        assertEquals(List.of("X", "Y"), FlowRules.orphans(placed, edges, "A"));
    }

    @Test
    void aCyclicFlowStillTerminatesTheWalk() {
        List<String> placed = List.of("A", "B", "C");
        List<Arrow> edges = List.of(
                wire("A", "B"), wire("B", "C"), wire("C", "A", "AGAIN"));
        assertEquals(List.of("A", "B", "C"), FlowRules.reachable(placed, edges, "A"));
        assertEquals(List.of(), FlowRules.orphans(placed, edges, "A"));
    }

    @Test
    void aStartThatNoLongerExistsFallsBackToTheFirstPlacedCard() {
        // The start activity was deleted or renamed out from under the flow: still generate something that
        // runs, rather than reporting every card an orphan.
        List<String> placed = List.of("A", "B");
        assertEquals(List.of("A", "B"), FlowRules.reachable(placed, A_TO_B, "Deleted"));
    }
}
