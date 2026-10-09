package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.BotConstants;
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

    /** A bot's activities, as its {@code Activities.java} holds them. */
    enum Activities implements Activity { MINING, SELLING, NOTHING }

    private static final Activity MINING = Activities.MINING;
    private static final Activity SELLING = Activities.SELLING;

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

    /** A stale constant must never stop a running bot: one console line, and the flow is untouched. */
    @Test
    void anActivityTheFlowLacksIsANoOp() {
        install(step(MINING, true));

        ActivitySwitch.disable(Activities.NOTHING);

        assertTrue(ActivitySwitch.active(MINING));
    }

    /** An activity the flow does not mention is on — reading an absent one as off would silently stop it. */
    @Test
    void anActivityTheFlowDoesNotMentionIsOn() {
        assertTrue(ActivitySwitch.active(Activities.NOTHING));
    }

    // ---- the activity and outcome values ----------------------------------------------------------------

    /** A bot's constant is its name, and the label the canvas and the trace show is that name as words. */
    @Test
    void aLabelIsTheConstantsNameAsWords() {
        assertEquals("Mining", MINING.label());
        assertEquals("Bag full", Outcomes.BAG_FULL.label());
        assertEquals("Hp below 50", Outcomes.HP_BELOW_50.label());
        assertEquals("NEXT", Outcome.NEXT.label(), "the two every activity has are labelled as written");
        assertEquals("DISABLED", Outcome.DISABLED.label());
        assertEquals("", Activity.NONE.label());
    }

    /** What an editor holds for a bot's constant it cannot load, and the built-in ones by their names. */
    @Test
    void aConstantHeldByNameIsEqualByName() {
        assertEquals(BotConstants.outcome("BAG_FULL"), BotConstants.outcome("BAG_FULL"));
        assertNotEquals(BotConstants.outcome("BAG_FULL"), BotConstants.outcome("BAG_FUL"));
        assertEquals("Bag full", BotConstants.outcome("BAG_FULL").label());
        assertSame(Outcome.NEXT, BotConstants.outcome(null));
        assertSame(Outcome.NEXT, BotConstants.outcome(" "));
        assertSame(Outcome.NEXT, BotConstants.outcome("NEXT"));
        assertSame(Outcome.DISABLED, BotConstants.outcome("DISABLED"));
        assertSame(Activity.NONE, BotConstants.activity(""));
    }

    /** A bot's outcomes, as its {@code Outcomes.java} holds them. */
    enum Outcomes implements Outcome { BAG_FULL, HP_BELOW_50 }
}
