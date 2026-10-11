package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
import com.botmaker.sdk.internal.flow.choices.Flows;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** This computer's run properties pick the flow and flip the switches the author allowed, and nothing else. */
class RunChoicesTest {

    private static final Activity REST = new Activity("Rest");
    private static final Activity BATTLE = new Activity("Battle");

    private static Flow.Step step(Activity activity, boolean enabled, boolean runnerSwitch) {
        return new Flow.Step(activity, ActivityBody.none(), "", enabled, false, false, runnerSwitch, Activity.NONE);
    }

    private static final Flow DRAWN = new Flow(List.of(step(BATTLE, true, false), step(REST, true, true)), List.of(),
            BATTLE, Flow.Limits.DEFAULT);

    private static Run chosen(Run run, Map<String, String> properties) {
        return RunChoices.chosen(run, Flows.class, properties::get);
    }

    @Test
    void nothingChosenRunsTheDefaultAsDrawn() {
        Run run = Run.of(DRAWN).home(() -> { });
        Run chosen = chosen(run, Map.of());
        assertSame(DRAWN, chosen.flow());
        assertSame(run.home(), chosen.home());
    }

    @Test
    void aChosenFlowIsTheFlowsConstantOfThatName() {
        assertEquals(Flows.DAILY, chosen(Run.of(DRAWN), Map.of(RunChoices.FLOW, " DAILY ")).flow());
        assertSame(DRAWN, chosen(Run.of(DRAWN), Map.of(RunChoices.FLOW, "WEEKLY")).flow(), "an unknown name");
        assertSame(DRAWN, chosen(Run.of(DRAWN), Map.of(RunChoices.FLOW, "NOTE")).flow(), "a constant that is no flow");
        assertSame(DRAWN, RunChoices.chosen(Run.of(DRAWN), null, Map.of(RunChoices.FLOW, "DAILY")::get).flow(),
                "a bot with no Flows class");
    }

    @Test
    void theFlowsClassIsFoundBesideTheValuesClass() {
        assertSame(Flows.class, RunChoices.holder(Flows.class));
        assertNull(RunChoices.holder(RunChoicesTest.class), "no Flows in this package");
        assertNull(RunChoices.holder((Class<?>[]) null));
    }

    @Test
    void onlyASwitchableActivityIsSwitched() {
        Flow switched = chosen(Run.of(DRAWN), Map.of(RunChoices.property(REST), "off",
                RunChoices.property(BATTLE), "off")).flow();
        assertFalse(switched.step(REST).enabled(), "Rest may be switched");
        assertTrue(switched.step(BATTLE).enabled(), "Battle may not, so the switch is ignored");
        assertEquals("botmaker.activity.Rest", RunChoices.property(REST));
    }

    @Test
    void aSwitchMayTurnBackOnWhatTheCanvasHasOff() {
        Flow off = new Flow(List.of(step(REST, false, true)), List.of(), REST, Flow.Limits.DEFAULT);
        assertTrue(RunChoices.switched(off, Map.of(RunChoices.property(REST), "on")::get).step(REST).enabled());
        assertSame(off, RunChoices.switched(off, Map.of(RunChoices.property(REST), "maybe")::get), "unreadable");
        assertSame(off, RunChoices.switched(off, Map.of(RunChoices.property(REST), "off")::get), "already off");
    }
}
