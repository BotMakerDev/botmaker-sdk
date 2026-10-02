package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.types.FlowTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The outcome picker lists NEXT, then the outcomes of the activity whose body the slot sits in — the flow links
 * {@code Collect::body} to "Collect", and the host reports the slot's method as the same text — and every
 * outcome when no activity's body is that method.
 */
class OutcomePickerTest {

    private static Flow.Step read(String body, String name, String... outcomes) {
        return FlowTypes.STEP_SHAPE.build(List.of(Activity.named(name), body, "", true, false, false,
                List.of(outcomes).stream().map(Outcome::named).toList()));
    }

    private static final Flow FLOW = Flow.of(
            List.of(read("Collect::body", "Collect", "Bag full", "No ore"), read("Battle::body", "Battle", "Won")),
            List.of(), List.of(), Activity.named("Collect"), Flow.limits(0, 0));

    @Test
    void aBodyIsOfferedItsOwnActivitysOutcomes() {
        assertEquals(List.of("NEXT", "Bag full", "No ore"), ActivityEditors.outcomeLabels(FLOW, "Collect::body"));
        assertEquals(List.of("NEXT", "Won"), ActivityEditors.outcomeLabels(FLOW, "Battle::body"));
    }

    @Test
    void anythingElseIsOfferedEveryOutcome() {
        List<String> every = List.of("NEXT", "Bag full", "No ore", "Won");
        assertEquals(every, ActivityEditors.outcomeLabels(FLOW, "Helpers::mine"));
        assertEquals(every, ActivityEditors.outcomeLabels(FLOW, null));
    }

    @Test
    void theActivityPickerOffersTheFlowsActivities() {
        assertEquals(List.of("Collect", "Battle"), ActivityEditors.activityLabels(FLOW));
    }
}
