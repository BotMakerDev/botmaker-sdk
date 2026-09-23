package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Flows;
import com.botmaker.sdk.internal.flow.FlowWalker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Switching the flow's activities on and off by name, and the context a body is handed.
 */
class ActivitiesTest {

    @AfterEach
    void tearDown() {
        FlowWalker.clearOverrides();
        Flows.use(null);
    }

    private static void install(Flow.Activity... activities) {
        Flows.use(Flow.of(List.of(activities), List.of(), List.of(), "", Flow.Limits.DEFAULT));
    }

    private static Flow.Activity activity(String name, boolean enabled, String... outcomes) {
        return Flow.activity(ActivityBody.NONE, name, "", enabled, false, false, List.of(outcomes));
    }

    // ---- enablement -------------------------------------------------------------------------------------

    @Test
    void anActivityDefersToTheFlowsOwnSwitch() {
        install(activity("Mining", false), activity("Selling", true));

        assertFalse(Activities.active("Mining"));
        assertTrue(Activities.active("Selling"));
    }

    @Test
    void anOverrideOutranksTheFlowInBothDirections() {
        install(activity("Mining", false));

        Activities.enable("Mining");
        assertTrue(Activities.active("Mining"));
        Activities.disable("Mining");
        assertFalse(Activities.active("Mining"));
        Activities.setEnabled("Mining", true);
        assertTrue(Activities.active("Mining"));
    }

    /** The "do this once, then stop" pattern, from inside the body. */
    @Test
    void aBodyCanSwitchItsOwnActivityOff() {
        install(activity("Mining", true));

        new ActivityContext("Mining").disable();

        assertFalse(Activities.active("Mining"), "the override outranks the switch on the canvas");
    }

    /** A typo must never stop a running bot: one console line, and the flow is untouched. */
    @Test
    void anUnknownNameIsANoOp() {
        install(activity("Mining", true));

        Activities.disable("Minnig");

        assertTrue(Activities.active("Mining"));
    }

    /** An activity the flow does not mention is on — reading an absent name as off would silently stop it. */
    @Test
    void anActivityTheFlowDoesNotMentionIsOn() {
        assertTrue(Activities.active("Nothing"));
    }

    // ---- the context ------------------------------------------------------------------------------------

    @Test
    void theContextKnowsWhichActivityItIs() {
        assertEquals("Mining", new ActivityContext("Mining").name());
    }

    /**
     * An outcome the canvas does not declare is not refused. It becomes an outcome nothing is wired to, which
     * ends the run — the same answer as an outcome the user declared and never wired.
     */
    @Test
    void anUndeclaredOutcomeIsStillReported() {
        install(activity("Mining", true, "BAG_FULL"));

        assertEquals("BAG_FUL", new ActivityContext("Mining").outcome("BAG_FUL").name());
    }

    @Test
    void doneIsTheImplicitOutcomeAndSoIsABlankName() {
        ActivityContext ctx = new ActivityContext("Mining");

        assertEquals("NEXT", ctx.done().name());
        assertEquals("NEXT", ctx.outcome("  ").name());
    }

    // ---- the outcome value type -------------------------------------------------------------------------

    @Test
    void outcomesAreEqualWhenTheirNamesAre() {
        assertEquals(Outcome.of("BAG_FULL"), Outcome.of("BAG_FULL"));
        assertEquals(Outcome.of("BAG_FULL").hashCode(), Outcome.of("BAG_FULL").hashCode());
        assertNotEquals(Outcome.of("BAG_FULL"), Outcome.of("bag_full"), "the canvas stores what was typed");
        assertEquals("BAG_FULL", Outcome.of("BAG_FULL").toString());
    }

    @Test
    void aBlankOrMissingOutcomeNameIsTheImplicitOne() {
        assertEquals("NEXT", Outcome.of(null).name());
        assertEquals("NEXT", Outcome.of("").name());
    }
}
