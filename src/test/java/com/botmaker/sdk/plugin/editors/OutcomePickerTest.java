package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.types.FlowTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The outcome picker lists the outcomes of the activity whose body the slot sits in — the flow links
 * {@code Collect::body} to "Collect", and the host reports the slot's method as the same text — and every
 * outcome when no activity's body is that method.
 */
class OutcomePickerTest {

    private static Flow.Activity read(String body, String name, String... outcomes) {
        return FlowTypes.ACTIVITY_SHAPE.build(List.of(body, name, "", true, false, false, List.of(outcomes)));
    }

    private static final Flow FLOW = Flow.of(
            List.of(read("Collect::body", "Collect", "BAG_FULL", "NO_ORE"), read("Battle::body", "Battle", "WON")),
            List.of(), List.of(), "Collect", Flow.limits(0, 0));

    @Test
    void aBodyIsOfferedItsOwnActivitysOutcomes() {
        assertEquals(List.of("BAG_FULL", "NO_ORE"), ActivityEditors.outcomeNames(FLOW, "Collect::body"));
        assertEquals(List.of("WON"), ActivityEditors.outcomeNames(FLOW, "Battle::body"));
    }

    @Test
    void anythingElseIsOfferedEveryOutcome() {
        assertEquals(List.of("BAG_FULL", "NO_ORE", "WON"), ActivityEditors.outcomeNames(FLOW, "Helpers::mine"));
        assertEquals(List.of("BAG_FULL", "NO_ORE", "WON"), ActivityEditors.outcomeNames(FLOW, null));
    }
}
