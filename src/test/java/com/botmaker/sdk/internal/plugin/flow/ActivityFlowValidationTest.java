package com.botmaker.sdk.internal.plugin.flow;

import com.botmaker.sdk.api.bot.ActivityBody;
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

    private static Flow of(Flow.Activity... activities) {
        return Flow.of(List.of(activities), List.of(), List.of(), "", Flow.Limits.DEFAULT);
    }

    private static Flow.Activity activity(String name, String... outcomes) {
        return Flow.activity(ActivityBody.NONE, name, "", true, false, true, List.of(outcomes));
    }

    @Test
    void anOrdinaryActivityWithOutcomesIsFine() {
        assertNull(ActivityFlowDialog.validate(of(activity("Mining", "BAG_FULL", "NO_ORE"))));
    }

    @Test
    void anOutcomeMustBeAValidJavaIdentifier() {
        // It becomes a constant of the generated Outcome enum.
        String problem = ActivityFlowDialog.validate(of(activity("Mining", "bag full")));
        assertNotNull(problem);
        assertTrue(problem.contains("bag full"), problem);
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
        assertNull(FlowNames.outcomeProblem(List.of(), "Mining", "BAG_FULL", null));
        assertNotNull(FlowNames.outcomeProblem(List.of(), "Mining", "DISABLED", null),
                "the dialog must refuse the name while it is being typed, not only on save");
    }

    @Test
    void anOutcomeTypedWithSpacesIsNormalisedRatherThanRejected() {
        // "bag full" is a perfectly clear thing to type; turning it into the enum constant is our job.
        assertEquals("BAG_FULL", FlowNames.normalizeOutcome("bag full"));
        assertEquals("BAG_FULL", FlowNames.normalizeOutcome("  bag-full "));
        assertEquals("NO_ORE", FlowNames.normalizeOutcome("no.ore"));
        assertNull(ActivityFlowDialog.validate(of(activity("Mining",
                FlowNames.normalizeOutcome("bag full")))));
    }

    @Test
    void twoActivitiesDifferingOnlyInCaseAreRejected() {
        // Two cards that read the same, and two stub files that collide on a case-insensitive filesystem.
        String problem = ActivityFlowDialog.validate(of(activity("Mining"), activity("MINING")));
        assertNotNull(problem);
        assertTrue(problem.contains("case"), problem);
    }
}
