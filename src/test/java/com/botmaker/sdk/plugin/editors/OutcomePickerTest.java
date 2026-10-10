package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.types.FlowTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The outcome picker lists the constants of the activity whose body the slot sits in — the flow links
 * {@code Collect::body} to "Collect", and the host reports the slot's method as the same text — and every
 * activity's, each with its activity, when no activity's body is that method.
 */
class OutcomePickerTest {

    private static final Map<String, List<String>> DECLARED = new LinkedHashMap<>();

    static {
        DECLARED.put("Collect", List.of("NEXT", "BAG_FULL", "NO_ORE"));
        DECLARED.put("Battle", List.of("NEXT", "WON"));
    }

    @Test
    void aBodyIsOfferedItsOwnActivitysOutcomes() {
        assertEquals(List.of("Next", "Bag full", "No ore"), ActivityEditors.outcomeLabels(DECLARED, "Collect"));
        assertEquals(List.of("Next", "Won"), ActivityEditors.outcomeLabels(DECLARED, "Battle"));
    }

    @Test
    void anythingElseIsOfferedEveryOutcomeWithItsActivity() {
        assertEquals(List.of("Collect: Next", "Collect: Bag full", "Collect: No ore", "Battle: Next", "Battle: Won"),
                ActivityEditors.outcomeLabels(DECLARED, null));
    }

    /** A label in the box names the constant of its activity's enum, never another's of the same name. */
    @Test
    void aLabelNamesItsActivitysConstant() {
        assertEquals(BotConstants.outcome("Collect", "BAG_FULL"), ActivityEditors.outcomeOf("Collect", "Bag full"));
        assertEquals(BotConstants.outcome("Battle", "NEXT"), ActivityEditors.outcomeOf(null, "Battle: Next"));
    }

    @Test
    void theActivityPickerOffersTheFlowsActivities() {
        Flow flow = new Flow(List.of(FlowTypes.step(FlowNames.activity("Collect"), FlowTypes.body("Collect::body")),
                FlowTypes.step(FlowNames.activity("Battle"), FlowTypes.body(""))), List.of(), List.of(), null,
                Flow.limits(0, 0), null);
        assertEquals(List.of("Collect", "Battle"), ActivityEditors.activityLabels(flow));
    }
}
