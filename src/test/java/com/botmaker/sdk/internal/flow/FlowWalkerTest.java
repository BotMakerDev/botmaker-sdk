package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.ActivitySwitch;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
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
 *
 * <p>Activities and outcomes are written by name here ({@link #edge}, {@link #on}), as an editor holds a bot's
 * constant: {@code BotConstants.activity("A")} stands for {@code Activities.A}, and two are equal by name.
 */
class FlowWalkerTest {

    private static final String DISABLED = Outcome.DISABLED.label();

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
        return () -> {
            log.add(name + (PopupGuard.isEnabled() ? "+popup" : "-popup"));
            assertEquals(BotConstants.activity(name), FlowWalker.current(), "the walk says which activity is running");
            return script.isEmpty() ? Outcome.NEXT : BotConstants.outcome(script.removeFirst());
        };
    }

    private static Flow.Step step(ActivityBody body, String name, boolean enabled, boolean goHome,
                                  boolean popupCheck) {
        return new Flow.Step(BotConstants.activity(name), body, "", enabled, goHome, popupCheck, List.of());
    }

    private Flow.Step on(String name, String... outcomes) {
        return step(body(name, outcomes), name, true, false, true);
    }

    private Flow.Step off(String name) {
        return step(body(name), name, false, false, true);
    }

    private static Flow.Edge edge(String from, String to, String outcome) {
        return Flow.edge(BotConstants.activity(from), BotConstants.activity(to), BotConstants.outcome(outcome));
    }

    private static Flow flow(String start, List<Flow.Step> steps, Flow.Edge... edges) {
        return flow(start, Flow.Limits.DEFAULT, steps, edges);
    }

    private static Flow flow(String start, Flow.Limits limits, List<Flow.Step> steps, Flow.Edge... edges) {
        return Flow.of(steps, List.of(edges), List.of(), BotConstants.activity(start), limits);
    }

    /** Installs and walks {@code flow} to its end, with no pause between activities. */
    private static void walk(Flow flow, Runnable goHome) {
        Flow unpaused = Flow.of(flow.steps(), flow.edges(), flow.presets(), flow.start(),
                Flow.limits(flow.limits().maxSteps(), 0));
        Flows.use(unpaused);
        assertThrows(RuntimeException.class, () -> FlowWalker.run(unpaused, goHome),
                "a run ends by stopping the bot, which throws");
    }

    private static void walk(Flow flow) {
        walk(flow, null);
    }

    // ---- what an observer hears -------------------------------------------------------------------------

    @Test
    void anObserverHearsEachActivityThatRunsAsItIsEntered() {
        List<String> entered = new ArrayList<>();
        com.botmaker.sdk.internal.observe.BotObserver observer = new com.botmaker.sdk.internal.observe.BotObserver() {
            @Override
            public void onActivity(String label) {
                entered.add(label + "@" + log.size());
            }
        };
        com.botmaker.sdk.internal.observe.Bots.addObserver(observer);
        try {
            walk(flow("A", List.of(on("A"), off("B"), on("C")),
                    edge("A", "B", Outcome.NEXT.label()), edge("B", "C", DISABLED)));
        } finally {
            com.botmaker.sdk.internal.observe.Bots.removeObserver(observer);
        }
        assertEquals(List.of("A@0", "C@1"), entered, "before each body runs; a skipped activity is not entered");
    }

    // ---- routing ----------------------------------------------------------------------------------------

    @Test
    void anOutcomeGoesWhereItsEdgeSays() {
        walk(flow("A", List.of(on("A", "Right"), on("L"), on("R")),
                edge("A", "L", "Left"), edge("A", "R", "Right")));

        assertEquals(List.of("A+popup", "R+popup"), log, "Right takes the Right arrow; L never runs");
    }

    /** {@code Outcome.NEXT} follows the plain arrow, which a blank label also names. */
    @Test
    void nextFollowsThePlainEdge() {
        walk(flow("A", List.of(on("A"), on("B")), Flow.edge(BotConstants.activity("A"), BotConstants.activity("B"),
                Outcome.NEXT)));

        assertEquals(List.of("A+popup", "B+popup"), log);
    }

    @Test
    void aFlowMayLoopBackOnItself() {
        // Three passes, then an outcome with nothing wired to it.
        walk(flow("A", List.of(on("A", "", "", "Left")), edge("A", "A", "")));

        assertEquals(3, log.size(), "it loops until an outcome has no arrow");
    }

    @Test
    void anUnwiredOutcomeEndsTheRun() {
        walk(flow("A", List.of(on("A", "Right"), on("B")), edge("A", "B", "Left")));

        assertEquals(List.of("A+popup"), log);
    }

    @Test
    void anEdgeToAnActivityTheFlowDoesNotHaveEndsTheRun() {
        walk(flow("A", List.of(on("A")), edge("A", "Deleted", "")));

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
        assertEquals(BotConstants.activity("B"), FlowWalker.start(flow("B", List.of(on("A"), on("B")))));
    }

    /** A deleted or renamed start activity must not be the reason a bot does nothing. */
    @Test
    void fallsBackToTheFirstActivityWhenTheStartIsStale() {
        assertEquals(BotConstants.activity("A"), FlowWalker.start(flow("Deleted", List.of(on("A"), on("B")))));
        assertNull(FlowWalker.start(Flow.NONE));
    }

    // ---- per-activity settings --------------------------------------------------------------------------

    @Test
    void aDisabledActivityFollowsItsDisabledArrowWithoutRunning() {
        walk(flow("A", List.of(off("A"), on("B")), edge("A", "A", ""), edge("A", "B", DISABLED)));

        assertEquals(List.of("B+popup"), log, "the flow passes through a disabled activity, doing nothing");
    }

    @Test
    void aDisabledActivityWithNoDisabledArrowEndsTheRun() {
        walk(flow("A", List.of(off("A"), on("B")), edge("A", "B", "")));

        assertTrue(log.isEmpty(), "the arrow is drawn, never inferred from NEXT");
    }

    /** Drawing a flow before writing its code: a card with no body takes its DISABLED arrow. */
    @Test
    void anActivityWithNoBodyFallsThroughItsDisabledArrow() {
        walk(flow("Unwritten", List.of(step(ActivityBody.NONE, "Unwritten", true, false, false), on("B")),
                edge("Unwritten", "B", DISABLED)));

        assertEquals(List.of("B+popup"), log);
    }

    @Test
    void anOverrideMadeMidRunIsReadOnTheNextPass() {
        ActivityBody once = () -> {
            log.add("once");
            ActivitySwitch.disable(BotConstants.activity("Once"));
            return Outcome.NEXT;
        };
        walk(flow("Once", List.of(step(once, "Once", true, false, false), on("B")),
                edge("Once", "Once", ""), edge("Once", "B", DISABLED)));

        assertEquals(List.of("once", "B+popup"), log);
    }

    @Test
    void popupCheckIsSetPerActivityRatherThanInherited() {
        PopupGuard.enabled(false);
        walk(flow("A", List.of(on("A"), step(body("B"), "B", true, false, false), on("C")),
                edge("A", "B", ""), edge("B", "C", "")));

        assertEquals(List.of("A+popup", "B-popup", "C+popup"), log,
                "PopupGuard is process-global, so every activity states what it wants");
    }

    @Test
    void goHomeRunsOnlyForActivitiesThatAskForItAndOnlyWhenActive() {
        walk(flow("A", List.of(step(body("A"), "A", false, true, true), on("B"),
                                step(body("C"), "C", true, true, true)),
                        edge("A", "B", DISABLED), edge("B", "C", "")),
                () -> log.add("goHome"));

        assertEquals(List.of("B+popup", "goHome", "C+popup"), log,
                "nothing to go home for when the activity won't run");
    }

    // ---- the step budget --------------------------------------------------------------------------------

    @Test
    void aFlowThatNeverEndsGivesUpAfterTheStepBudget() {
        walk(flow("A", Flow.limits(4, 0), List.of(on("A")), edge("A", "A", "")));

        assertEquals(4, log.size(), "four hand-offs, then it gives up rather than looping forever");
    }

    /** {@link Flow.Limits#maxSteps()} documents 0 as "no limit"; it used to stop before the first step. */
    @Test
    void aZeroStepBudgetIsNoLimit() {
        walk(flow("A", Flow.limits(0, 0), List.of(on("A", "", "", "", "", "", "End")), edge("A", "A", "")));

        assertEquals(6, log.size());
    }
}
