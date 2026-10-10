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

    /** A bot's activities, as its {@code Activities.java} holds them: one enum of outcomes each. */
    static final class Activities {
        enum Mining implements Outcome { NEXT, BAG_FULL, HP_BELOW_50 }

        enum Selling implements Outcome { NEXT }

        enum DailyReward implements Outcome { NEXT }
    }

    @AfterEach
    void tearDown() {
        FlowWalker.clearOverrides();
        Flows.use(null);
    }

    private static void install(Flow.Step... steps) {
        Flows.use(Flow.of(List.of(steps), List.of(), List.of(), null, Flow.Limits.DEFAULT));
    }

    private static Flow.Step step(Class<? extends Outcome> activity, boolean enabled) {
        Flow.Step step = Flow.activity(activity, ActivityBody.none());
        return enabled ? step : step.off();
    }

    // ---- enablement -------------------------------------------------------------------------------------

    @Test
    void anActivityDefersToTheFlowsOwnSwitch() {
        install(step(Activities.Mining.class, false), step(Activities.Selling.class, true));

        assertFalse(ActivitySwitch.active(Activities.Mining.class));
        assertTrue(ActivitySwitch.active(Activities.Selling.class));
    }

    @Test
    void anOverrideOutranksTheFlowInBothDirections() {
        install(step(Activities.Mining.class, false));

        ActivitySwitch.enable(Activities.Mining.class);
        assertTrue(ActivitySwitch.active(Activities.Mining.class));
        ActivitySwitch.disable(Activities.Mining.class);
        assertFalse(ActivitySwitch.active(Activities.Mining.class));
        ActivitySwitch.setEnabled(Activities.Mining.class, true);
        assertTrue(ActivitySwitch.active(Activities.Mining.class));
    }

    /** The "do this once, then stop" pattern, from inside the body. */
    @Test
    void aBodyCanSwitchItsOwnActivityOff() {
        install(step(Activities.Mining.class, true));

        ActivitySwitch.disable(Activities.Mining.class);

        assertFalse(ActivitySwitch.active(Activities.Mining.class), "the override outranks the switch on the canvas");
    }

    /** A stale activity must never stop a running bot: one console line, and the flow is untouched. */
    @Test
    void anActivityTheFlowLacksIsANoOp() {
        install(step(Activities.Mining.class, true));

        ActivitySwitch.disable(Activities.DailyReward.class);

        assertTrue(ActivitySwitch.active(Activities.Mining.class));
    }

    /** An activity the flow does not mention is on — reading an absent one as off would silently stop it. */
    @Test
    void anActivityTheFlowDoesNotMentionIsOn() {
        assertTrue(ActivitySwitch.active(Activities.DailyReward.class));
    }

    // ---- the activity and outcome values ----------------------------------------------------------------

    /** An activity is its enum's simple name, an outcome its constant, and each label is the name as words. */
    @Test
    void aLabelIsTheNameAsWords() {
        assertEquals("Mining", Activity.of(Activities.Mining.class).label());
        assertEquals("Daily reward", Activity.of(Activities.DailyReward.class).label());
        assertEquals("Bag full", Activities.Mining.BAG_FULL.label());
        assertEquals("Hp below 50", Activities.Mining.HP_BELOW_50.label());
        assertEquals("Next", Activities.Mining.NEXT.label(), "NEXT is an ordinary constant");
        assertEquals("", Activity.NONE.label());
    }

    /** An outcome's activity is the enum declaring it, so two enums' NEXTs are two outcomes. */
    @Test
    void anOutcomeBelongsToItsEnum() {
        assertEquals(Activity.of(Activities.Mining.class), Activities.Mining.NEXT.activity());
        assertEquals(new Activity("Selling"), Activities.Selling.NEXT.activity());
        assertNotEquals(Activities.Mining.NEXT.activity(), Activities.Selling.NEXT.activity());
    }

    /** What an editor holds for a bot's name it cannot load: equal by both names, its activity its enum's. */
    @Test
    void aConstantHeldByNameIsEqualByName() {
        assertEquals(BotConstants.outcome("Mining", "BAG_FULL"), BotConstants.outcome("Mining", "BAG_FULL"));
        assertNotEquals(BotConstants.outcome("Mining", "NEXT"), BotConstants.outcome("Selling", "NEXT"));
        assertEquals("Bag full", BotConstants.outcome("Mining", "BAG_FULL").label());
        assertEquals(Activity.of(Activities.Mining.class), BotConstants.outcome("Mining", "NEXT").activity());
        assertSame(Activity.NONE, BotConstants.activity(""));
        assertEquals(Activity.of(Activities.Mining.class), BotConstants.activity("Mining"));
    }
}
