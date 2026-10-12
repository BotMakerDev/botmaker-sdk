package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bot's skip parameter switches off only what the flow, or one of its sub-flows, lets the runner switch. */
class SkippedActivitiesTest {

    enum Collect implements Outcome { NEXT }

    enum Rest implements Outcome { NEXT }

    enum Daily implements Outcome { NEXT }

    private static final Flow INNER = Flow.of(List.of(Flow.activity(Daily.class, ActivityBody.none()).switchable()),
            List.of(), Daily.class, Flow.Limits.DEFAULT);

    private static final Flow FLOW = Flow.of(List.of(
                    Flow.activity(Collect.class, ActivityBody.none()),
                    Flow.activity(Rest.class, ActivityBody.none()).switchable(),
                    Flow.subflow(INNER)),
            List.of(), Collect.class, Flow.Limits.DEFAULT);

    @AfterEach
    void tearDown() {
        InstalledRun.walk(null);
        InstalledRun.skip(null);
    }

    private static boolean on(Class<? extends Outcome> activity, Set<Class<? extends Outcome>> skip) {
        InstalledRun.walk(FLOW);
        InstalledRun.skip(SkippedActivities.of(FLOW, skip));
        return InstalledRun.enabled(Activity.of(activity));
    }

    @Test
    void aSwitchableActivityListedIsOff() {
        assertFalse(on(Rest.class, Set.of(Rest.class)));
        assertTrue(on(Collect.class, Set.of(Rest.class)));
    }

    @Test
    void aSwitchableActivityOfASubFlowListedIsOff() {
        assertFalse(on(Daily.class, Set.of(Daily.class)));
    }

    @Test
    void anActivityTheRunnerMayNotSwitchRunsAnyway() {
        assertTrue(on(Collect.class, Set.of(Collect.class)));
    }

    @Test
    void nothingListedSwitchesNothingOff() {
        assertEquals(Set.of(), SkippedActivities.of(FLOW, Set.of()));
        assertEquals(Set.of(), SkippedActivities.of(FLOW, null));
        assertTrue(on(Rest.class, Set.of()));
    }
}
