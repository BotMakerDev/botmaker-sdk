package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.types.FlowTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the flow dialog refuses to save. Every rule here exists because the save would otherwise write Java
 * that doesn't compile — the point is to say so in the dialog rather than in a build log.
 *
 * <p>Over a {@link Board} since 2026-10-10: an activity's outcomes are its enum's constants, which the flow
 * no longer lists.
 */
class ActivityFlowValidationTest {

    /** One activity: its label, then its outcome labels. */
    private record Card(String name, List<String> outcomes) {}

    private static Card activity(String name, String... outcomes) {
        List<String> all = new ArrayList<>(List.of(Arrow.NEXT));
        all.addAll(List.of(outcomes));
        return new Card(name, all);
    }

    private static Board of(Card... cards) {
        List<Flow.Step> steps = new ArrayList<>();
        Map<String, List<String>> exits = new LinkedHashMap<>();
        for (Card card : cards) {
            steps.add(FlowTypes.step(FlowNames.activity(card.name()), ActivityBody.none()));
            exits.put(card.name(), card.outcomes());
        }
        return new Board(new Flow(steps, List.of(), List.of(), null, Flow.Limits.DEFAULT, null), exits);
    }

    @Test
    void anOrdinaryActivityWithOutcomesIsFine() {
        assertNull(ActivityFlowDialog.validate(of(activity("Mining", "BAG_FULL", "NO_ORE"))));
    }

    @Test
    void anOutcomeLabelIsTypedFreelyButMustMakeAConstant() {
        // "Bag full" is BAG_FULL; a label with no letter first makes no constant at all.
        assertNull(ActivityFlowDialog.validate(of(activity("Mining", "Bag full"))));
        String problem = FlowNames.outcomeProblem(List.of(), "Mining", "2nd try", null);
        assertNotNull(problem);
        assertTrue(problem.contains("2nd try"), problem);
    }

    /** Two activities' outcomes of one name are two constants, one in each enum. */
    @Test
    void twoActivitiesMayEachHaveAnOutcomeOfOneName() {
        assertEquals("Bag full", FlowNames.label("bag-full"));
        assertEquals(FlowNames.outcome("Mining", "Bag full"), FlowNames.outcome("Mining", "bag-full"));
        assertNull(ActivityFlowDialog.validate(of(activity("Mining", "Bag full"), activity("Fishing", "bag-full"))));
        assertNull(FlowNames.outcomeProblem(List.of("Next"), "Fishing", "Bag full", null));
    }

    @Test
    void anOutcomeCannotBeDeclaredTwice() {
        assertNotNull(ActivityFlowDialog.validate(of(activity("Mining", "BAG_FULL", "BAG_FULL"))));
        assertNotNull(ActivityFlowDialog.validate(of(activity("Mining", "NEXT"))), "NEXT is there already");
    }

    @Test
    void anOutcomeCannotBeTheSwitchedOffPort() {
        // An activity can't report being switched off, because it didn't run.
        String problem = ActivityFlowDialog.validate(of(activity("Mining", "DISABLED")));
        assertNotNull(problem);
        assertTrue(problem.contains("DISABLED"), problem);
        assertNull(FlowNames.outcomeProblem(List.of(), "Mining", "Bag full", null));
        assertNotNull(FlowNames.outcomeProblem(List.of(), "Mining", "DISABLED", null),
                "the dialog must refuse the name while it is being typed, not only on save");
        assertNull(FlowNames.outcomeProblem(List.of("Won"), "Mining", "next", null), "NEXT is an ordinary outcome");
        assertNotNull(FlowNames.outcomeProblem(List.of("Next"), "Mining", "NEXT", null), "but one of its own");
    }

    /** NEXT is seeded, not kept: an activity may drop it for another, but an enum keeps one constant to return. */
    @Test
    void an_activity_keeps_one_outcome_whichever_it_is() {
        assertNull(ActivityFlowDialog.validate(of(new Card("Mining", List.of("Won")))));
        String problem = ActivityFlowDialog.validate(of(new Card("Mining", List.of())));
        assertNotNull(problem);
        assertTrue(problem.contains("needs an outcome"), problem);
    }

    /** The one step from a label to its constant, and to an activity's enum. */
    @Test
    void a_label_becomes_its_name() {
        assertEquals("BAG_FULL", FlowNames.constantFor("Bag full"));
        assertEquals("BAG_FULL", FlowNames.constantFor("  bag-full "));
        assertEquals("NO_ORE", FlowNames.constantFor("no.ore"));
        assertEquals("NOTHING_LEFT", FlowNames.constantFor("NOTHING_LEFT"));
        assertEquals("HANDLE_FULL_INVENTORY", FlowNames.constantFor("HandleFullInventory"));
        assertNull(FlowNames.constantFor("2nd try"));
        assertNull(FlowNames.constantFor("—"));
        assertEquals("Bag full", FlowNames.label("  Bag   full "));
        assertEquals("HandleFullInventory", FlowNames.typeFor("Handle full inventory"));
        assertEquals("COLLECT", FlowNames.typeFor("COLLECT"), "a type name is its own, so a hand-written enum round-trips");
        for (String type : List.of("COLLECT", "XP", "XPFarm", "Daily_Reward", "DailyReward", "Stage2b")) {
            assertEquals(type, FlowNames.typeFor(FlowNames.activityLabel(type)), FlowNames.activityLabel(type));
        }
        assertNull(FlowNames.typeFor("2nd try"));
        assertEquals("Daily reward", FlowNames.activityLabel("daily-reward"));
    }

    /** A second spelling of one of its own outcomes is a clash, and so is renaming onto one. */
    @Test
    void an_outcome_may_not_be_respelled_or_merged_by_a_rename() {
        assertNotNull(FlowNames.outcomeProblem(List.of("Won"), "Fishing", "WON", null));
        assertNotNull(FlowNames.outcomeProblem(List.of("Lost", "Won"), "Fishing", "Won", "Lost"),
                "renaming onto another outcome would merge two constants");
        assertNull(FlowNames.outcomeProblem(List.of("Lost"), "Fishing", "Defeat", "Lost"));
    }

    @Test
    void twoActivitiesMakingOneEnumAreRejected() {
        String problem = ActivityFlowDialog.validate(of(activity("Mining"), activity("mining!")));
        assertNotNull(problem);
        assertTrue(problem.contains("Duplicate activity name: 'Mining'") || problem.contains("Activities.Mining"),
                problem);
        assertTrue(FlowNames.activityNameProblem("mining!", List.of("Mining")).contains("Activities.Mining"));
    }

    /** Naming a card refuses what saving would refuse, so a flow never sits unsaved over a name. */
    @Test
    void a_name_making_a_taken_enum_is_refused_where_it_is_typed() {
        assertNotNull(FlowNames.activityNameProblem("mining", List.of("Mining")));
        assertNotNull(FlowNames.activityNameProblem("Bag full", List.of("bag-full")));
        // A card renamed to its own name in another spelling: its own name is not among the others.
        assertNull(FlowNames.activityNameProblem("MINING", List.of("Fishing")));
        assertNull(FlowNames.activityNameProblem("Handle full inventory", List.of("Fishing")));
        assertNotNull(FlowNames.activityNameProblem("Outcome", List.of()), "it would hide the interface");
        assertNotNull(FlowNames.activityNameProblem("activities", List.of()), "it would hide its own class");
    }

    /** Two renames before a save are one; renaming back is none. */
    @Test
    void pending_renames_compose() {
        Map<String, String> renames = new LinkedHashMap<>();
        ActivityFlowDialog.renamed(renames, "Battle", "Fight");
        ActivityFlowDialog.renamed(renames, "Fight", "Duel");
        assertEquals(Map.of("Battle", "Duel"), renames);
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

        assertEquals(List.of(new Arrow("Mining", "Bank", "Bag full"), new Arrow("Fishing", "Bank", "Full")),
                ActivityFlowDialog.rewiredOutcome(arrows, "Mining", "Full", "Bag full"));
    }

    @Test
    void renaming_an_activity_keeps_it_in_its_presets() {
        List<Selection> presets = List.of(new Selection("Night", List.of("Mining", "Bank")));

        assertEquals(List.of(new Selection("Night", List.of("Digging", "Bank"))),
                ActivityFlowDialog.renamedIn(presets, "Mining", "Digging"));
    }

    /** What the canvas draws by label is saved as the values the bot's names hold, and read back the same. */
    @Test
    void an_arrow_and_a_selection_save_as_typed_values_and_back() {
        Arrow plain = new Arrow("Mining", "Bank", "next");
        assertEquals(Arrow.NEXT, plain.outcome(), "kept as its constant's label");
        assertEquals(new Flow.Edge(FlowNames.outcome("Mining", "NEXT"), FlowNames.activity("Bank")), plain.toEdge());
        assertEquals(plain, Arrow.of(plain.toEdge()));

        Selection night = new Selection("Night", List.of("Mining"));
        assertTrue(night.toPreset().enables(FlowNames.activity("Mining")));
        assertEquals(night, Selection.of(night.toPreset()));
    }

    /** The canvas's cards and arrows make the board a save writes: a DISABLED arrow is its card's off-wire. */
    @Test
    void the_canvas_makes_a_board() {
        ActivityDraft mining = new ActivityDraft("Mining", "", true, List.of(Arrow.NEXT, "Bag full"), false, true, 0, 0,
                "Mining::body");
        ActivityDraft bank = new ActivityDraft("Bank", "", false, List.of(Arrow.NEXT), false, true, 0, 0);
        Board board = ActivityFlowDialog.board(List.of(mining, bank), List.of(new Arrow("Mining", "Bank", "Bag full"),
                        new Arrow("Bank", "Mining", Arrow.DISABLED)), List.of(), "Mining", Flow.Limits.DEFAULT,
                null);

        assertEquals(List.of(new Flow.Edge(FlowNames.outcome("Mining", "Bag full"), FlowNames.activity("Bank"))),
                board.flow().edges());
        assertEquals(FlowNames.activity("Mining"), board.flow().steps().get(1).whenOff());
        assertEquals(List.of(Arrow.NEXT, "Bag full"), board.exitsOf("Mining"));
        assertEquals(List.of(Arrow.NEXT), board.exitsOf("Bank"));
        assertEquals("Mining::body", FlowValue.bodySource(board.flow().steps().getFirst()));
    }
}
