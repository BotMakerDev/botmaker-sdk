package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.ActivityBody;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Flows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shape a {@link Flow} can take, walked.
 *
 * <p>A run always ends in {@code Bot.stop()}, which throws — so {@link #walk} asserts the throw, and a test
 * that never reached the end would fail rather than pass by accident.
 */
class FlowWalkerTest {

    private final List<String> log = new ArrayList<>();

    /**
     * A do-nothing check, because {@link PopupGuard#isEnabled()} is "switched on <em>and</em> installed" — with
     * no handler it reads false whatever the walker set, and every popup assertion below would pass vacuously.
     */
    @BeforeEach
    void installAPopupCheck() {
        PopupGuard.install(() -> {});
    }

    @AfterEach
    void tearDown() {
        PopupGuard.uninstall();
        PopupGuard.enabled(true);
        FlowWalker.clearOverrides();
        Flows.use(null);
    }

    /** A body that logs its name and the popup guard's state, and reports a scripted outcome each time. */
    private ActivityBody body(String name, String... outcomes) {
        Deque<String> script = new ArrayDeque<>(List.of(outcomes));
        return ctx -> {
            log.add(name + (PopupGuard.isEnabled() ? "+popup" : "-popup"));
            return script.isEmpty() ? ctx.done() : ctx.outcome(script.removeFirst());
        };
    }

    private Flow.Activity on(String name, String... outcomes) {
        return Flow.activity(body(name, outcomes), name, "", true, false, true, List.of());
    }

    private Flow.Activity off(String name) {
        return Flow.activity(body(name), name, "", false, false, true, List.of());
    }

    private static Flow flow(String start, List<Flow.Activity> activities, Flow.Edge... edges) {
        return flow(start, Flow.Limits.DEFAULT, activities, edges);
    }

    private static Flow flow(String start, Flow.Limits limits, List<Flow.Activity> activities,
                             Flow.Edge... edges) {
        return Flow.of(activities, List.of(edges), List.of(), start, limits);
    }

    /** Installs and walks {@code flow} to its end, with no pause between activities. */
    private static void walk(Flow flow, Runnable goHome) {
        Flow unpaused = Flow.of(flow.activities(), flow.edges(), flow.presets(), flow.start(),
                Flow.limits(flow.limits().maxSteps(), 0));
        Flows.use(unpaused);
        assertThrows(RuntimeException.class, () -> FlowWalker.run(unpaused, goHome),
                "a run ends by stopping the bot, which throws");
    }

    private static void walk(Flow flow) {
        walk(flow, null);
    }

    // ---- routing ----------------------------------------------------------------------------------------

    @Test
    void anOutcomeGoesWhereItsEdgeSays() {
        walk(flow("A", List.of(on("A", "RIGHT"), on("L"), on("R")),
                Flow.edge("A", "L", "LEFT"), Flow.edge("A", "R", "RIGHT")));

        assertEquals(List.of("A+popup", "R+popup"), log, "RIGHT takes the RIGHT wire; L never runs");
    }

    /** A blank stored outcome is NEXT, which is what {@code ctx.done()} reports. */
    @Test
    void doneFollowsTheBlankEdge() {
        walk(flow("A", List.of(on("A"), on("B")), Flow.edge("A", "B", "")));

        assertEquals(List.of("A+popup", "B+popup"), log);
    }

    @Test
    void aFlowMayLoopBackOnItself() {
        // Three passes, then an outcome with nothing wired to it.
        walk(flow("A", List.of(on("A", "", "", "LEFT")), Flow.edge("A", "A", "")));

        assertEquals(3, log.size(), "it loops until an outcome runs out of wire");
    }

    @Test
    void anUnwiredOutcomeEndsTheRun() {
        walk(flow("A", List.of(on("A", "RIGHT"), on("B")), Flow.edge("A", "B", "LEFT")));

        assertEquals(List.of("A+popup"), log);
    }

    @Test
    void anEdgeToAnActivityTheFlowDoesNotHaveEndsTheRun() {
        walk(flow("A", List.of(on("A")), Flow.edge("A", "Deleted", "")));

        assertEquals(List.of("A+popup"), log);
    }

    @Test
    void anEmptyFlowStopsWithoutRunningAnything() {
        walk(Flow.NONE);

        assertTrue(log.isEmpty());
    }

    // ---- the start --------------------------------------------------------------------------------------

    @Test
    void keepsTheStartWhenItNamesAnActivity() {
        assertEquals("B", FlowWalker.start(flow("B", List.of(on("A"), on("B")))));
    }

    /** A deleted or renamed start activity must not be the reason a bot does nothing. */
    @Test
    void fallsBackToTheFirstActivityWhenTheStartIsStale() {
        assertEquals("A", FlowWalker.start(flow("Deleted", List.of(on("A"), on("B")))));
        assertNull(FlowWalker.start(Flow.NONE));
    }

    // ---- per-activity settings --------------------------------------------------------------------------

    @Test
    void aDisabledActivityFollowsItsDisabledWireWithoutRunning() {
        walk(flow("A", List.of(off("A"), on("B")),
                Flow.edge("A", "A", ""), Flow.edge("A", "B", Flow.Edge.DISABLED)));

        assertEquals(List.of("B+popup"), log, "the flow passes through a disabled activity, doing nothing");
    }

    @Test
    void aDisabledActivityWithNoDisabledWireEndsTheRun() {
        walk(flow("A", List.of(off("A"), on("B")), Flow.edge("A", "B", "")));

        assertTrue(log.isEmpty(), "the wire is drawn, never inferred from NEXT");
    }

    /** Drawing a flow before writing its code: a card with no body takes its DISABLED wire. */
    @Test
    void anActivityWithNoBodyFallsThroughItsDisabledWire() {
        Flow.Activity unwritten = Flow.activity(ActivityBody.NONE, "Unwritten", "", true, false, false,
                List.of());
        walk(flow("Unwritten", List.of(unwritten, on("B")),
                Flow.edge("Unwritten", "B", Flow.Edge.DISABLED)));

        assertEquals(List.of("B+popup"), log);
    }

    @Test
    void anOverrideMadeMidRunIsReadOnTheNextPass() {
        ActivityBody once = ctx -> {
            log.add("once");
            ctx.disable();
            return ctx.done();
        };
        walk(flow("Once", List.of(Flow.activity(once, "Once", "", true, false, false, List.of()), on("B")),
                Flow.edge("Once", "Once", ""), Flow.edge("Once", "B", Flow.Edge.DISABLED)));

        assertEquals(List.of("once", "B+popup"), log);
    }

    @Test
    void popupCheckIsSetPerActivityRatherThanInherited() {
        PopupGuard.enabled(false);
        Flow.Activity quiet = Flow.activity(body("B"), "B", "", true, false, false, List.of());
        walk(flow("A", List.of(on("A"), quiet, on("C")), Flow.edge("A", "B", ""), Flow.edge("B", "C", "")));

        assertEquals(List.of("A+popup", "B-popup", "C+popup"), log,
                "PopupGuard is process-global, so every activity states what it wants");
    }

    @Test
    void goHomeRunsOnlyForActivitiesThatAskForItAndOnlyWhenActive() {
        Flow.Activity offHoming = Flow.activity(body("A"), "A", "", false, true, true, List.of());
        Flow.Activity homing = Flow.activity(body("C"), "C", "", true, true, true, List.of());
        walk(flow("A", List.of(offHoming, on("B"), homing),
                        Flow.edge("A", "B", Flow.Edge.DISABLED), Flow.edge("B", "C", "")),
                () -> log.add("goHome"));

        assertEquals(List.of("B+popup", "goHome", "C+popup"), log,
                "nothing to go home for when the activity won't run");
    }

    // ---- the step budget --------------------------------------------------------------------------------

    @Test
    void aFlowThatNeverEndsGivesUpAfterTheStepBudget() {
        walk(flow("A", Flow.limits(4, 0), List.of(on("A")), Flow.edge("A", "A", "")));

        assertEquals(4, log.size(), "four hand-offs, then it gives up rather than looping forever");
    }

    /** {@link Flow.Limits#maxSteps()} documents 0 as "no limit"; it used to stop before the first step. */
    @Test
    void aZeroStepBudgetIsNoLimit() {
        walk(flow("A", Flow.limits(0, 0), List.of(on("A", "", "", "", "", "", "END")),
                Flow.edge("A", "A", "")));

        assertEquals(6, log.size());
    }
}
