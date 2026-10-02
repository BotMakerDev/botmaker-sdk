package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the flow dialog refuses to save. Every rule here exists because the generator would otherwise emit
 * Java that doesn't compile — the point is to say so in the dialog rather than in a build log.
 *
 * <p>It was the host's until 2026-09-11, over the editor's own record set; it is over {@link Flow} since
 * 2026-09-21, which is the value the editor actually reads and writes.
 */
class ActivityFlowValidationTest {

    private static Flow of(Flow.Step... steps) {
        return Flow.of(List.of(steps), List.of(), List.of(), Activity.NONE, Flow.Limits.DEFAULT);
    }

    private static Flow.Step activity(String name, String... outcomes) {
        return Flow.activity(Activity.named(name), ActivityBody.NONE, "", true, false, true,
                List.of(outcomes).stream().map(Outcome::named).toList());
    }

    @Test
    void anOrdinaryActivityWithOutcomesIsFine() {
        assertNull(ActivityFlowDialog.validate(of(activity("Mining", "BAG_FULL", "NO_ORE"))));
    }

    @Test
    void anOutcomeLabelIsFreeTextButMustMakeAConstant() {
        // "Bag full" is Outcomes.BAG_FULL; a label with no letter first makes no constant at all.
        assertNull(ActivityFlowDialog.validate(of(activity("Mining", "Bag full"))));
        String problem = ActivityFlowDialog.validate(of(activity("Mining", "2nd try")));
        assertNotNull(problem);
        assertTrue(problem.contains("2nd try"), problem);
    }

    @Test
    void twoSpellingsOfOneOutcomeAcrossActivitiesAreRejected() {
        // An outcome is one constant wherever it is declared: the same label twice is shared, two are a clash.
        assertNull(ActivityFlowDialog.validate(of(activity("Mining", "Bag full"), activity("Fishing", "Bag full"))));
        String problem = ActivityFlowDialog.validate(of(activity("Mining", "Bag full"),
                activity("Fishing", "bag-full")));
        assertNotNull(problem);
        assertTrue(problem.contains("BAG_FULL"), problem);
    }

    @Test
    void anOutcomeCannotBeDeclaredTwice() {
        assertNotNull(ActivityFlowDialog.validate(of(activity("Mining", "BAG_FULL", "BAG_FULL"))));
    }

    @Test
    void anOutcomeCannotRedeclareTheImplicitNext() {
        // allOutcomes() de-duplicates it, so this must be caught as a clash rather than silently swallowed —
        // otherwise the user adds a NEXT outcome, gets no port for it, and has nothing to explain why.
        assertNotNull(ActivityFlowDialog.validate(of(activity("Mining", "NEXT"))));
    }

    @Test
    void anOutcomeCannotRedeclareTheImplicitDisabled() {
        // Same reasoning as NEXT, one step further: an activity can't report being switched off, because it
        // didn't run. Declaring it would ask for an enum constant nothing could ever return.
        String problem = ActivityFlowDialog.validate(of(activity("Mining", "DISABLED")));
        assertNotNull(problem);
        assertTrue(problem.contains("DISABLED"), problem);
        assertNull(FlowNames.outcomeProblem(List.of(), List.of(), "Mining", "Bag full", null));
        assertNotNull(FlowNames.outcomeProblem(List.of(), List.of(), "Mining", "DISABLED", null),
                "the dialog must refuse the name while it is being typed, not only on save");
        assertNotNull(FlowNames.outcomeProblem(List.of(), List.of(), "Mining", "next", null),
                "a label whose constant would be NEXT is the implicit outcome in another spelling");
    }

    /** The one step from a label to its constant. */
    @Test
    void a_label_becomes_its_constant() {
        assertEquals("BAG_FULL", FlowNames.constantFor("Bag full"));
        assertEquals("BAG_FULL", FlowNames.constantFor("  bag-full "));
        assertEquals("NO_ORE", FlowNames.constantFor("no.ore"));
        assertEquals("NOTHING_LEFT", FlowNames.constantFor("NOTHING_LEFT"));
        assertEquals("HANDLE_FULL_INVENTORY", FlowNames.constantFor("HandleFullInventory"));
        assertEquals("COLLECT", FlowNames.constantFor("Collect"));
        assertNull(FlowNames.constantFor("2nd try"));
        assertNull(FlowNames.constantFor("—"));
        assertEquals("Bag full", FlowNames.label("  Bag   full "));
    }

    /** Adding an outcome another card declares is sharing it; a second spelling of it is a clash. */
    @Test
    void an_outcome_may_be_shared_but_not_respelled_or_merged_by_a_rename() {
        assertNull(FlowNames.outcomeProblem(List.of(), List.of("Won"), "Fishing", "Won", null));
        assertNotNull(FlowNames.outcomeProblem(List.of(), List.of("Won"), "Fishing", "WON", null));
        assertNotNull(FlowNames.outcomeProblem(List.of("Lost"), List.of("Won"), "Fishing", "Won", "Lost"),
                "renaming onto another outcome would merge two constants");
        assertNull(FlowNames.outcomeProblem(List.of("Lost"), List.of("Lost"), "Fishing", "Defeat", "Lost"),
                "another card declaring the outcome being renamed is renamed with it");
    }

    @Test
    void twoActivitiesMakingOneConstantAreRejected() {
        // Two cards that read nearly the same, and one Activities.MINING for both.
        String problem = ActivityFlowDialog.validate(of(activity("Mining"), activity("MINING")));
        assertNotNull(problem);
        assertTrue(problem.contains("Activities.MINING"), problem);
    }

    /** Naming a card refuses what saving would refuse, so a flow never sits unsaved over a name. */
    @Test
    void a_name_making_a_taken_constant_is_refused_where_it_is_typed() {
        assertNotNull(FlowNames.activityNameProblem("MINING", List.of("Mining")));
        assertNotNull(FlowNames.activityNameProblem("Bag full", List.of("bag-full")));
        // A card renamed to its own name in another spelling: its own name is not among the others.
        assertNull(FlowNames.activityNameProblem("MINING", List.of("Fishing")));
        assertNull(FlowNames.activityNameProblem("Handle full inventory", List.of("Fishing")));
    }

    /** Two renames before a save are one; renaming back is none. */
    @Test
    void pending_renames_compose() {
        java.util.Map<String, String> renames = new java.util.LinkedHashMap<>();
        ActivityFlowDialog.renamed(renames, "Battle", "Fight");
        ActivityFlowDialog.renamed(renames, "Fight", "Duel");
        assertEquals(java.util.Map.of("Battle", "Duel"), renames);
        ActivityFlowDialog.renamed(renames, "Duel", "Battle");
        assertTrue(renames.isEmpty());
    }

    @Test
    void a_saved_preset_may_not_take_a_built_in_name() {
        assertNotNull(FlowNames.presetNameProblem("everything", List.of("Everything", "Nothing")));
        assertNotNull(FlowNames.presetNameProblem("  ", List.of("Everything", "Nothing")));
        assertNull(FlowNames.presetNameProblem("Night farm", List.of("Everything", "Nothing")));
    }

    /** A renamed outcome keeps its arrow; another activity's arrow of the same outcome is not touched. */
    @Test
    void renaming_an_outcome_carries_its_arrow() {
        List<Arrow> arrows = List.of(new Arrow("Mining", "Bank", "FULL"), new Arrow("Fishing", "Bank", "FULL"));

        assertEquals(List.of(new Arrow("Mining", "Bank", "BAG_FULL"), new Arrow("Fishing", "Bank", "FULL")),
                ActivityFlowDialog.rewiredOutcome(arrows, "Mining", "FULL", "BAG_FULL"));
    }

    @Test
    void renaming_an_activity_keeps_it_in_its_presets() {
        List<Selection> presets = List.of(new Selection("Night", List.of("Mining", "Bank")));

        assertEquals(List.of(new Selection("Night", List.of("Digging", "Bank"))),
                ActivityFlowDialog.renamedIn(presets, "Mining", "Digging"));
    }

    /** What the canvas draws by label is saved as the values the bot's constants hold, and read back the same. */
    @Test
    void an_arrow_and_a_selection_save_as_typed_values_and_back() {
        Arrow plain = new Arrow("Mining", "Bank", "");
        assertEquals(Flow.edge(Activity.named("Mining"), Activity.named("Bank"), Outcome.NEXT), plain.toEdge());
        assertEquals(plain, Arrow.of(plain.toEdge()));
        Arrow off = new Arrow("Mining", "Bank", Arrow.DISABLED);
        assertTrue(off.toEdge().isDisabled());
        assertEquals(off, Arrow.of(off.toEdge()));

        Selection night = new Selection("Night", List.of("Mining"));
        assertTrue(night.toPreset().enables(Activity.named("Mining")));
        assertEquals(night, Selection.of(night.toPreset()));
    }
}
