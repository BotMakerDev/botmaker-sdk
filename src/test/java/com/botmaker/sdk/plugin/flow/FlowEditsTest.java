package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.Labels;
import com.botmaker.sdk.plugin.types.FlowTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The flow changes the assistant's activity tools make, as values. */
class FlowEditsTest {

    private static final Flow TWO = FlowEdits.addActivity(FlowEdits.addActivity(Flow.NONE, "Collect", "Collect::body"),
            "Battle", "");

    @Test
    void addingNamesTheBodyAndTheFirstIsTheStart() {
        assertEquals(Set.of("Collect", "Battle"), FlowEdits.labels(TWO));
        assertEquals(FlowNames.activity("Collect"), TWO.start());
        assertEquals("Collect::body", FlowTypes.sourceOf(TWO.steps().getFirst().body()));
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.addActivity(TWO, "collect", ""));
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.addActivity(TWO, "Rest", "not a ref"));
    }

    @Test
    void connectingAddsTheOutcomeAndReplacesItsWire() {
        Flow wired = FlowEdits.connect(TWO, "Collect", "bag full", "Battle");
        Flow.Step collect = wired.step(FlowNames.activity("Collect"));
        assertEquals(List.of(FlowNames.outcome("bag full")), collect.outcomes());
        assertEquals(1, wired.edges().size());

        Flow rewired = FlowEdits.connect(wired, "Collect", "Bag Full", "Collect");
        assertEquals(1, rewired.edges().size());
        assertEquals(List.of(FlowNames.outcome("bag full")), rewired.step(FlowNames.activity("Collect")).outcomes(),
                "the same constant is the same outcome, whatever its case");
        assertEquals(FlowNames.activity("Collect"), rewired.edges().getFirst().to());
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.connect(TWO, "Collect", "", "Nowhere"));

        // The two ports every activity has are wired, never declared.
        Flow disabled = FlowEdits.connect(TWO, "Collect", "disabled", "Battle");
        assertEquals(Outcome.DISABLED, disabled.edges().getFirst().outcome());
        assertTrue(disabled.step(FlowNames.activity("Collect")).outcomes().isEmpty());
        assertEquals(Outcome.NEXT, FlowEdits.connect(TWO, "Collect", "next", "Battle").edges().getFirst().outcome());
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.connect(TWO, "Collect", "!!", "Battle"),
                "a label that makes no constant names no outcome, rather than the NEXT it would fall back to");
    }

    /** A label and its constant go both ways for every upper-case constant, digits included. */
    @Test
    void aLabelNamesItsConstantBack() {
        for (String constant : List.of("COLLECT", "NOTHING_LEFT", "STAGE_2B", "HP_BELOW_50", "A1")) {
            assertEquals(constant, FlowNames.constantFor(Labels.of(constant)), Labels.of(constant));
        }
        assertEquals("Stage 2b", Labels.of("STAGE_2B"));
        assertEquals("Bag full", Labels.of("bagFull"), "another case is shown, though no card names it back");
    }

    @Test
    void disconnectingRemovesOnlyThatArrowAndKeepsTheOutcome() {
        Flow wired = FlowEdits.connect(FlowEdits.connect(TWO, "Collect", "bag full", "Battle"), "Collect", "",
                "Collect");
        Flow cut = FlowEdits.disconnect(wired, "collect", "Bag Full");
        assertEquals(1, cut.edges().size());
        assertEquals(Outcome.NEXT, cut.edges().getFirst().outcome());
        assertEquals(List.of(FlowNames.outcome("bag full")), cut.step(FlowNames.activity("Collect")).outcomes(),
                "a body may still return it");
        assertTrue(FlowEdits.disconnect(cut, "Collect", null).edges().isEmpty(), "blank is NEXT");
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.disconnect(cut, "Battle", ""));
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.disconnect(cut, "Rest", ""));
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.disconnect(cut, "Collect", "1st"),
                "no constant is not NEXT");
    }

    @Test
    void theStartIsAnyActivity() {
        assertEquals(FlowNames.activity("Battle"), FlowEdits.setStart(TWO, "battle").start());
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.setStart(TWO, "Rest"));
    }

    @Test
    void renamingCarriesWiresAndTheStart() {
        Flow renamed = FlowEdits.renameActivity(FlowEdits.connect(TWO, "Collect", "", "Battle"), "Collect", "Gather");
        assertEquals(Set.of("Gather", "Battle"), FlowEdits.labels(renamed));
        assertEquals(FlowNames.activity("Gather"), renamed.start());
        assertEquals(FlowNames.activity("Gather"), renamed.edges().getFirst().from());
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.renameActivity(TWO, "Collect", "Battle"));
    }

    @Test
    void removingDropsItsWiresAndMovesTheStart() {
        Flow removed = FlowEdits.removeActivity(FlowEdits.connect(TWO, "Battle", "", "Collect"), "Collect");
        assertEquals(Set.of("Battle"), FlowEdits.labels(removed));
        assertTrue(removed.edges().isEmpty());
        assertEquals(FlowNames.activity("Battle"), removed.start());
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.removeActivity(TWO, "Rest"));
    }
}
