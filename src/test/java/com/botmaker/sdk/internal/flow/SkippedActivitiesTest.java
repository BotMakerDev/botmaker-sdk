package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bot's skip parameter switches off only what the flow lets the runner switch. */
class SkippedActivitiesTest {

    enum Collect implements Outcome { NEXT }

    enum Rest implements Outcome { NEXT }

    enum Daily implements Outcome { NEXT }

    private static final Flow FLOW = Flow.of(List.of(
                    Flow.activity(Collect.class, ActivityBody.none()),
                    Flow.activity(Rest.class, ActivityBody.none()).switchable()),
            List.of(), Collect.class, Flow.Limits.DEFAULT);

    @Test
    void aSwitchableActivityListedIsOff() {
        Flow skipped = SkippedActivities.applied(FLOW, Set.of(Rest.class));
        assertFalse(skipped.step(Activity.of(Rest.class)).enabled());
        assertTrue(skipped.step(Activity.of(Collect.class)).enabled());
    }

    @Test
    void anActivityTheRunnerMayNotSwitchRunsAnyway() {
        assertTrue(SkippedActivities.applied(FLOW, Set.of(Collect.class)).step(Activity.of(Collect.class)).enabled());
    }

    @Test
    void nothingListedOrNothingThisFlowHasLeavesTheFlowAsDrawn() {
        assertSame(FLOW, SkippedActivities.applied(FLOW, Set.of()));
        assertSame(FLOW, SkippedActivities.applied(FLOW, null));
        assertSame(FLOW, SkippedActivities.applied(FLOW, Set.of(Daily.class)), "an activity of another flow");
    }
}
