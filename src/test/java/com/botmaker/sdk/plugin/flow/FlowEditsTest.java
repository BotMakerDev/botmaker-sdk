package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.Labels;
import com.botmaker.sdk.plugin.types.FlowTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The flow changes the assistant's activity tools make, as values. */
class FlowEditsTest {

    private static final Board NONE = new Board(Flow.NONE, Map.of());

    private static final Board TWO = FlowEdits.addActivity(FlowEdits.addActivity(NONE, "Collect", "Collect::body"),
            "Battle", "");

    @Test
    void addingNamesTheBodyAndTheFirstIsTheStart() {
        assertEquals(Set.of("Collect", "Battle"), FlowEdits.labels(TWO.flow()));
        assertEquals(FlowNames.activity("Collect"), TWO.flow().start());
        assertEquals("Collect::body", FlowTypes.sourceOf(TWO.flow().steps().getFirst().body()));
        assertEquals(List.of(Arrow.NEXT), TWO.exitsOf("Battle"), "a new activity starts with NEXT");
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.addActivity(TWO, "collect", ""));
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.addActivity(TWO, "Rest", "not a ref"));
    }

    @Test
    void anActivityIsNamedAsAType() {
        Board added = FlowEdits.addActivity(NONE, "daily reward", "");
        assertEquals(new Activity("DailyReward"), added.flow().steps().getFirst().activity());
        assertEquals(Set.of("Daily reward"), FlowEdits.labels(added.flow()));
        assertEquals("DailyReward", FlowNames.typeFor("DailyReward"));
        assertEquals("BAG", FlowNames.typeFor("BAG"), "a type name typed is that name");
        assertEquals("BagFull", FlowNames.typeFor("BAG full"));
    }

    @Test
    void connectingAddsTheOutcomeAndReplacesItsWire() {
        Board wired = FlowEdits.connect(TWO, "Collect", "bag full", "Battle");
        assertEquals(List.of(Arrow.NEXT, "Bag full"), wired.exitsOf("Collect"));
        assertEquals(1, wired.flow().edges().size());
        assertEquals(FlowNames.outcome("Collect", "bag full"), wired.flow().edges().getFirst().on());
        assertEquals(new Activity("Collect"), wired.flow().edges().getFirst().from(), "the outcome's own activity");

        Board rewired = FlowEdits.connect(wired, "Collect", "Bag Full", "Collect");
        assertEquals(1, rewired.flow().edges().size());
        assertEquals(List.of(Arrow.NEXT, "Bag full"), rewired.exitsOf("Collect"),
                "the same constant is the same outcome, whatever its case");
        assertEquals(FlowNames.activity("Collect"), rewired.flow().edges().getFirst().to());
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.connect(TWO, "Collect", "", "Nowhere"));

        // The switched-off port is the step's, never an outcome.
        Board disabled = FlowEdits.connect(TWO, "Collect", "disabled", "Battle");
        assertTrue(disabled.flow().edges().isEmpty());
        assertEquals(FlowNames.activity("Battle"), disabled.flow().step(FlowNames.activity("Collect")).whenOff());
        assertEquals(List.of(Arrow.NEXT), disabled.exitsOf("Collect"));
        assertEquals(FlowNames.outcome("Collect", "NEXT"),
                FlowEdits.connect(TWO, "Collect", "next", "Battle").flow().edges().getFirst().on());
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.connect(TWO, "Collect", "!!", "Battle"),
                "a label that makes no constant names no outcome, rather than the NEXT it would fall back to");
    }

    /** Two activities' outcomes of one name are two constants, each in its own enum. */
    @Test
    void anOutcomeOfOneActivityIsNotAnothers() {
        Board both = FlowEdits.connect(FlowEdits.connect(TWO, "Collect", "", "Battle"), "Battle", "", "Collect");
        assertEquals(2, both.flow().edges().size());
        assertEquals(new Activity("Battle"), both.flow().edges().get(1).from());
    }

    /** A label and its constant go both ways for every upper-case constant, digits included. */
    @Test
    void aLabelNamesItsConstantBack() {
        for (String constant : List.of("COLLECT", "NOTHING_LEFT", "STAGE_2B", "HP_BELOW_50", "A1")) {
            assertEquals(constant, FlowNames.constantFor(Labels.of(constant)), Labels.of(constant));
        }
        for (String type : List.of("Collect", "DailyReward", "Stage2b")) {
            assertEquals(type, FlowNames.typeFor(Labels.of(type)), Labels.of(type));
        }
        assertEquals("Stage 2b", Labels.of("STAGE_2B"));
        assertEquals("Bag full", Labels.of("bagFull"), "another case is shown, though no card names it back");
    }

    @Test
    void disconnectingRemovesOnlyThatArrowAndKeepsTheOutcome() {
        Board wired = FlowEdits.connect(FlowEdits.connect(TWO, "Collect", "bag full", "Battle"), "Collect", "",
                "Collect");
        Board cut = FlowEdits.disconnect(wired, "collect", "Bag Full");
        assertEquals(1, cut.flow().edges().size());
        assertEquals(FlowNames.outcome("Collect", Arrow.NEXT), cut.flow().edges().getFirst().on());
        assertEquals(List.of(Arrow.NEXT, "Bag full"), cut.exitsOf("Collect"), "a body may still return it");
        assertTrue(FlowEdits.disconnect(cut, "Collect", null).flow().edges().isEmpty(), "blank is its first outcome");
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.disconnect(cut, "Battle", ""));
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.disconnect(cut, "Rest", ""));
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.disconnect(cut, "Collect", "1st"),
                "no constant is not NEXT");
        Board off = FlowEdits.connect(TWO, "Collect", "disabled", "Battle");
        assertTrue(FlowEdits.disconnect(off, "Collect", "DISABLED").flow().steps().getFirst().whenOff().isNone());
    }

    @Test
    void theStartIsAnyActivity() {
        assertEquals(FlowNames.activity("Battle"), FlowEdits.setStart(TWO, "battle").flow().start());
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.setStart(TWO, "Rest"));
    }

    @Test
    void renamingCarriesWiresExitsAndTheStart() {
        Board renamed = FlowEdits.renameActivity(FlowEdits.connect(FlowEdits.connect(TWO, "Collect", "", "Battle"),
                "Battle", "disabled", "Collect"), "Collect", "Gather");
        assertEquals(Set.of("Gather", "Battle"), FlowEdits.labels(renamed.flow()));
        assertEquals(FlowNames.activity("Gather"), renamed.flow().start());
        assertEquals(FlowNames.activity("Gather"), renamed.flow().edges().getFirst().from());
        assertEquals(FlowNames.activity("Gather"), renamed.flow().step(FlowNames.activity("Battle")).whenOff());
        assertEquals(List.of(Arrow.NEXT), renamed.exitsOf("Gather"));
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.renameActivity(TWO, "Collect", "Battle"));
    }

    @Test
    void removingDropsItsWiresAndMovesTheStart() {
        Board removed = FlowEdits.removeActivity(FlowEdits.connect(FlowEdits.connect(TWO, "Battle", "", "Collect"),
                "Battle", "disabled", "Collect"), "Collect");
        assertEquals(Set.of("Battle"), FlowEdits.labels(removed.flow()));
        assertTrue(removed.flow().edges().isEmpty());
        assertTrue(removed.flow().steps().getFirst().whenOff().isNone(), "its off-wire into it goes too");
        assertEquals(FlowNames.activity("Battle"), removed.flow().start());
        assertEquals(Set.of("Battle"), removed.exits().keySet());
        assertThrows(IllegalArgumentException.class, () -> FlowEdits.removeActivity(TWO, "Rest"));
    }
}
