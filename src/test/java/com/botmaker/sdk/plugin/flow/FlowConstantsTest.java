package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.flow.FlowConstants.Kind;
import com.botmaker.sdk.plugin.flow.FlowConstants.Op;
import com.botmaker.sdk.plugin.flow.FlowConstants.Plan;
import com.botmaker.sdk.plugin.types.FlowTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a save asks of the bot's {@code Activities} and {@code Outcomes} classes, before the flow is written. */
class FlowConstantsTest {

    private static Map<String, String> members(String... constantThenLabel) {
        Map<String, String> members = new LinkedHashMap<>();
        for (int i = 0; i < constantThenLabel.length; i += 2) members.put(constantThenLabel[i], constantThenLabel[i + 1]);
        return members;
    }

    @Test
    void a_label_no_constant_holds_is_added() {
        Plan plan = FlowConstants.plan(Kind.OUTCOMES, members("WON", "Won"), List.of("Won", "Bag full"), Map.of());
        assertNull(plan.refusal());
        assertEquals(List.of(new Op.Add("BAG_FULL")), plan.ops());
    }

    /** A constant whose name is not the label's is still the label's: it is matched by what it holds. */
    @Test
    void a_constant_holding_the_label_is_reused_whatever_it_is_called() {
        Plan plan = FlowConstants.plan(Kind.ACTIVITIES, members("FARM", "Collect"), List.of("Collect"), Map.of());
        assertEquals(List.of(), plan.ops());
    }

    /** A rename renames the constant that held the old label, rather than adding one beside it. */
    @Test
    void a_canvas_rename_renames_the_constant() {
        Plan plan = FlowConstants.plan(Kind.ACTIVITIES, members("BATTLE", "Battle", "REST", "Rest"),
                List.of("Fight", "Rest"), Map.of("Battle", "Fight"));
        assertNull(plan.refusal());
        assertEquals(List.of(new Op.Rename("Battle", "BATTLE", "FIGHT")), plan.ops());
    }

    /** "Won" to "WON!" is still Outcomes.WON: nothing is renamed, and the canvas's rename is spent. */
    @Test
    void a_rename_to_the_same_constant_renames_nothing() {
        Plan plan = FlowConstants.plan(Kind.OUTCOMES, members("WON", "Won"), List.of("WON!"), Map.of("Won", "WON!"));
        assertEquals(List.of(new Op.Rename("Won", "WON", "WON")), plan.ops());
    }

    /** A card renamed and a new card given the old name: the constant follows the rename, the new card gets one. */
    @Test
    void a_rename_then_a_new_card_of_the_old_name() {
        Plan plan = FlowConstants.plan(Kind.ACTIVITIES, members("BATTLE", "Battle"), List.of("Fight", "Battle"),
                Map.of("Battle", "Fight"));
        assertEquals(List.of(new Op.Rename("Battle", "BATTLE", "FIGHT"), new Op.Add("BATTLE")), plan.ops());
    }

    @Test
    void a_rename_onto_a_constant_holding_something_else_is_refused() {
        Plan plan = FlowConstants.plan(Kind.ACTIVITIES, members("BATTLE", "Battle", "FIGHT", "Fight club"),
                List.of("Fight"), Map.of("Battle", "Fight"));
        assertNotNull(plan.refusal());
        assertTrue(plan.refusal().contains("Activities.FIGHT"), plan.refusal());
        assertEquals(List.of(), plan.ops());
    }

    @Test
    void an_add_onto_a_constant_holding_something_else_is_refused() {
        Plan plan = FlowConstants.plan(Kind.OUTCOMES, members("BAG_FULL", "Bag full"), List.of("bag-full"), Map.of());
        assertNotNull(plan.refusal());
        assertTrue(plan.refusal().contains("Outcomes.BAG_FULL"), plan.refusal());
    }

    /** A rename of a label nothing declared yet is nothing to rename: the label is simply added. */
    @Test
    void a_rename_of_an_undeclared_label_adds_instead() {
        Plan plan = FlowConstants.plan(Kind.OUTCOMES, members(), List.of("Victory"), Map.of("Won", "Victory"));
        assertEquals(List.of(new Op.Add("VICTORY")), plan.ops());
    }

    /** + New outcome… in a body's return slot puts a port on that body's card, and only there. */
    @Test
    void a_new_outcome_is_declared_on_the_card_whose_body_holds_the_slot() {
        Flow flow = Flow.of(List.of(
                        Flow.activity(FlowNames.activity("Battle"), FlowTypes.body("Battle::body")).goesHome()
                                .checksPopups().reports(List.of(FlowNames.outcome("Won"))),
                        Flow.activity(FlowNames.activity("Rest"), ActivityBody.NONE)),
                List.of(), List.of(), FlowNames.activity("Battle"), Flow.Limits.DEFAULT);
        String method = FlowValue.bodySource(flow.steps().getFirst());

        Flow added = FlowConstants.withOutcome(flow, method, FlowNames.outcome("Timeout"));

        assertEquals(List.of(FlowNames.outcome("Won"), FlowNames.outcome("Timeout")), added.steps().get(0).outcomes());
        assertEquals(List.of(), added.steps().get(1).outcomes());
        assertEquals(added, FlowConstants.withOutcome(added, method, FlowNames.outcome("Timeout")),
                "declared once, however often it is asked");
        assertEquals(flow, FlowConstants.withOutcome(flow, "", FlowNames.outcome("Timeout")),
                "a slot in no activity's body declares nothing — not on every card with no body");
    }

    /**
     * A constant written by hand in another case ({@code Collect}) is shown, but the card names it back as
     * {@code COLLECT}: the save refuses rather than write a value no constant holds.
     */
    @Test
    void a_flow_naming_a_constant_the_enum_lacks_is_not_written() {
        Flow flow = Flow.of(List.of(Flow.activity(FlowNames.activity("Collect"), ActivityBody.NONE)
                        .reports(List.of(FlowNames.outcome("Won")))),
                List.of(), List.of(), FlowNames.activity("Collect"), Flow.Limits.DEFAULT);

        assertNull(FlowConstants.unheld(holding(List.of("COLLECT"), List.of("WON")), Kind.ACTIVITIES, flow));
        assertNull(FlowConstants.unheld(holding(List.of("COLLECT"), List.of("WON")), Kind.OUTCOMES, flow));
        String refused = FlowConstants.unheld(holding(List.of("Collect"), List.of("WON")), Kind.ACTIVITIES, flow);
        assertNotNull(refused);
        assertTrue(refused.contains("Activities.COLLECT"), refused);
    }

    /** A project whose {@code Activities} and {@code Outcomes} enums hold these constants. */
    private static PluginValues holding(List<String> activities, List<String> outcomes) {
        return new PluginValues() {
            @Override
            public List<String> ids() {
                return List.of();
            }

            @Override
            public Optional<ValueContext> open(String id) {
                return Optional.empty();
            }

            @Override
            public List<String> members(String id) {
                return id.equals(SdkValues.ACTIVITIES.id()) ? activities
                        : id.equals(SdkValues.OUTCOMES.id()) ? outcomes : List.of();
            }
        };
    }
}
