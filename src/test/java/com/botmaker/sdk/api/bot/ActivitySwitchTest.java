package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.FlowWalker;
import com.botmaker.sdk.internal.flow.Flows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Switching the flow's activities on and off, and the activity and outcome values. */
class ActivitySwitchTest {

    private static final Activity MINING = Activity.named("Mining");
    private static final Activity SELLING = Activity.named("Selling");

    @AfterEach
    void tearDown() {
        FlowWalker.clearOverrides();
        Flows.use(null);
    }

    private static void install(Flow.Step... steps) {
        Flows.use(Flow.of(List.of(steps), List.of(), List.of(), Activity.NONE, Flow.Limits.DEFAULT));
    }

    private static Flow.Step step(Activity activity, boolean enabled) {
        Flow.Step step = Flow.activity(activity, ActivityBody.NONE);
        return enabled ? step : step.off();
    }

    // ---- enablement -------------------------------------------------------------------------------------

    @Test
    void anActivityDefersToTheFlowsOwnSwitch() {
        install(step(MINING, false), step(SELLING, true));

        assertFalse(ActivitySwitch.active(MINING));
        assertTrue(ActivitySwitch.active(SELLING));
    }

    @Test
    void anOverrideOutranksTheFlowInBothDirections() {
        install(step(MINING, false));

        ActivitySwitch.enable(MINING);
        assertTrue(ActivitySwitch.active(MINING));
        ActivitySwitch.disable(MINING);
        assertFalse(ActivitySwitch.active(MINING));
        ActivitySwitch.setEnabled(MINING, true);
        assertTrue(ActivitySwitch.active(MINING));
    }

    /** The "do this once, then stop" pattern, from inside the body. */
    @Test
    void aBodyCanSwitchItsOwnActivityOff() {
        install(step(MINING, true));

        ActivitySwitch.disable(MINING);

        assertFalse(ActivitySwitch.active(MINING), "the override outranks the switch on the canvas");
    }

    /** An activity equal by label is the same activity: a constant and a value read off a file agree. */
    @Test
    void anActivityIsItsLabel() {
        install(step(MINING, false));

        assertFalse(ActivitySwitch.active(Activity.named("Mining")));
        assertNotEquals(MINING, Activity.named("mining"), "the label is compared exactly");
    }

    /** A stale constant must never stop a running bot: one console line, and the flow is untouched. */
    @Test
    void anActivityTheFlowLacksIsANoOp() {
        install(step(MINING, true));

        ActivitySwitch.disable(Activity.named("Minnig"));

        assertTrue(ActivitySwitch.active(MINING));
    }

    /** An activity the flow does not mention is on — reading an absent one as off would silently stop it. */
    @Test
    void anActivityTheFlowDoesNotMentionIsOn() {
        assertTrue(ActivitySwitch.active(Activity.named("Nothing")));
    }

    // ---- the outcome value type -------------------------------------------------------------------------

    @Test
    void outcomesAreEqualWhenTheirLabelsAre() {
        assertEquals(Outcome.named("Bag full"), Outcome.named("Bag full"));
        assertEquals(Outcome.named("Bag full").hashCode(), Outcome.named("Bag full").hashCode());
        assertNotEquals(Outcome.named("Bag full"), Outcome.named("bag full"), "the label is compared exactly");
        assertEquals("Bag full", Outcome.named("Bag full").toString());
    }

    @Test
    void aBlankOrMissingLabelIsNext() {
        assertSame(Outcome.NEXT, Outcome.named(null));
        assertSame(Outcome.NEXT, Outcome.named(" "));
        assertSame(Outcome.NEXT, Outcome.named("NEXT"));
        assertSame(Outcome.DISABLED, Outcome.named("DISABLED"));
    }
}
