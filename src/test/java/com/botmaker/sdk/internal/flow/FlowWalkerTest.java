package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.ActivitySwitch;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.PopupGuard;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Run;
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
 * <p>The activities are enums of their outcomes, as a bot's {@code Activities} class holds them (2026-10-10).
 */
class FlowWalkerTest {

    enum A implements Outcome { NEXT, LEFT, RIGHT }

    enum B implements Outcome { NEXT }

    enum C implements Outcome { NEXT }

    enum L implements Outcome { NEXT }

    enum R implements Outcome { NEXT }

    enum Once implements Outcome { NEXT }

    enum Unwritten implements Outcome { NEXT }

    enum Deleted implements Outcome { NEXT }

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
        InstalledRun.use(null);
    }

    /**
     * A body that logs its activity and the popup guard's state, and reports a scripted outcome each time —
     * {@code NEXT} once the script runs out, {@code null} for a scripted null.
     */
    @SafeVarargs
    private <O extends Outcome> ActivityBody<O> body(Class<O> activity, O... outcomes) {
        Deque<O> script = new ArrayDeque<>(List.of(outcomes));
        O next = java.util.Arrays.stream(activity.getEnumConstants()).filter(o -> o.name().equals("NEXT"))
                .findFirst().orElseThrow();
        return () -> {
            log.add(activity.getSimpleName() + (PopupGuard.isEnabled() ? "+popup" : "-popup"));
            assertEquals(Activity.of(activity), FlowWalker.current(), "the walk says which activity is running");
            return script.isEmpty() ? next : script.removeFirst();
        };
    }

    private static <O extends Outcome> Flow.Step step(Class<O> activity, ActivityBody<O> body, boolean enabled,
                                                      boolean goHome, boolean popupCheck) {
        Flow.Step step = Flow.activity(activity, body);
        if (!enabled) step = step.off();
        if (goHome) step = step.goesHome();
        return popupCheck ? step.checksPopups() : step;
    }

    @SafeVarargs
    private <O extends Outcome> Flow.Step on(Class<O> activity, O... outcomes) {
        return step(activity, body(activity, outcomes), true, false, true);
    }

    private <O extends Outcome> Flow.Step off(Class<O> activity) {
        return step(activity, body(activity), false, false, true);
    }

    private static Flow flow(Class<? extends Outcome> start, List<Flow.Step> steps, Flow.Edge... edges) {
        return flow(start, Flow.Limits.DEFAULT, steps, edges);
    }

    private static Flow flow(Class<? extends Outcome> start, Flow.Limits limits, List<Flow.Step> steps,
                             Flow.Edge... edges) {
        return Flow.of(steps, List.of(edges), start, limits);
    }

    /** Installs and walks {@code flow} to its end, with no pause between activities. */
    private static void walk(Flow flow) {
        walk(Run.of(flow));
    }

    /** The same, with {@code run}'s home. */
    private static void walk(Run run) {
        Flow flow = run.flow();
        Run unpaused = new Run(new Flow(flow.steps(), flow.edges(), flow.start(),
                Flow.limits(flow.limits().maxSteps(), 0)), run.home(), run.setup());
        InstalledRun.use(unpaused);
        assertThrows(RuntimeException.class, () -> FlowWalker.run(unpaused),
                "a run ends by stopping the bot, which throws");
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
            walk(flow(A.class, List.of(on(A.class), off(B.class).whenOff(C.class), on(C.class)),
                    Flow.edge(A.NEXT, B.class)));
        } finally {
            com.botmaker.sdk.internal.observe.Bots.removeObserver(observer);
        }
        assertEquals(List.of("A@0", "C@1"), entered, "before each body runs; a skipped activity is not entered");
    }

    // ---- routing ----------------------------------------------------------------------------------------

    @Test
    void anOutcomeGoesWhereItsEdgeSays() {
        walk(flow(A.class, List.of(on(A.class, A.RIGHT), on(L.class), on(R.class)),
                Flow.edge(A.LEFT, L.class), Flow.edge(A.RIGHT, R.class)));

        assertEquals(List.of("A+popup", "R+popup"), log, "RIGHT takes the RIGHT arrow; L never runs");
    }

    /** Two activities' constants of one name are two outcomes: B.NEXT does not take A's NEXT arrow. */
    @Test
    void anOutcomeIsItsOwnActivitys() {
        walk(flow(A.class, List.of(on(A.class), on(B.class), on(C.class)),
                Flow.edge(A.NEXT, B.class), Flow.edge(C.NEXT, A.class)));

        assertEquals(List.of("A+popup", "B+popup"), log);
    }

    @Test
    void aFlowMayLoopBackOnItself() {
        // Three passes, then an outcome with nothing wired to it.
        walk(flow(A.class, List.of(on(A.class, A.NEXT, A.NEXT, A.LEFT)), Flow.edge(A.NEXT, A.class)));

        assertEquals(3, log.size(), "it loops until an outcome has no arrow");
    }

    @Test
    void anUnwiredOutcomeEndsTheRun() {
        walk(flow(A.class, List.of(on(A.class, A.RIGHT), on(B.class)), Flow.edge(A.LEFT, B.class)));

        assertEquals(List.of("A+popup"), log);
    }

    @Test
    void aBodyReportingNothingEndsTheRun() {
        ActivityBody<A> nothing = () -> {
            log.add("nothing");
            return null;
        };
        walk(flow(A.class, List.of(Flow.activity(A.class, nothing), on(B.class)), Flow.edge(A.NEXT, B.class)));

        assertEquals(List.of("nothing"), log);
    }

    @Test
    void anEdgeToAnActivityTheFlowDoesNotHaveEndsTheRun() {
        walk(flow(A.class, List.of(on(A.class)), Flow.edge(A.NEXT, Deleted.class)));

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
        assertEquals(Activity.of(B.class), FlowWalker.start(flow(B.class, List.of(on(A.class), on(B.class)))));
    }

    /** A deleted or renamed start activity must not be the reason a bot does nothing. */
    @Test
    void fallsBackToTheFirstActivityWhenTheStartIsStale() {
        assertEquals(Activity.of(A.class), FlowWalker.start(flow(Deleted.class, List.of(on(A.class), on(B.class)))));
        assertNull(FlowWalker.start(Flow.NONE));
    }

    // ---- per-activity settings --------------------------------------------------------------------------

    @Test
    void aDisabledActivityFollowsItsOffWireWithoutRunning() {
        walk(flow(A.class, List.of(off(A.class).whenOff(B.class), on(B.class)), Flow.edge(A.NEXT, A.class)));

        assertEquals(List.of("B+popup"), log, "the flow passes through a disabled activity, doing nothing");
    }

    @Test
    void aDisabledActivityWithNoOffWireEndsTheRun() {
        walk(flow(A.class, List.of(off(A.class), on(B.class)), Flow.edge(A.NEXT, B.class)));

        assertTrue(log.isEmpty(), "the off-wire is drawn, never inferred from NEXT");
    }

    /** Drawing a flow before writing its code: a card with no body takes its off-wire. */
    @Test
    void anActivityWithNoBodyFallsThroughItsOffWire() {
        walk(flow(Unwritten.class, List.of(Flow.activity(Unwritten.class, ActivityBody.none()).whenOff(B.class),
                on(B.class))));

        assertEquals(List.of("B+popup"), log);
    }

    @Test
    void anOverrideMadeMidRunIsReadOnTheNextPass() {
        ActivityBody<Once> once = () -> {
            log.add("once");
            ActivitySwitch.disable(Once.class);
            return Once.NEXT;
        };
        walk(flow(Once.class, List.of(Flow.activity(Once.class, once).whenOff(B.class), on(B.class)),
                Flow.edge(Once.NEXT, Once.class)));

        assertEquals(List.of("once", "B+popup"), log);
    }

    @Test
    void popupCheckIsSetPerActivityRatherThanInherited() {
        PopupGuard.enabled(false);
        walk(flow(A.class, List.of(on(A.class), step(B.class, body(B.class), true, false, false), on(C.class)),
                Flow.edge(A.NEXT, B.class), Flow.edge(B.NEXT, C.class)));

        assertEquals(List.of("A+popup", "B-popup", "C+popup"), log,
                "PopupGuard is process-global, so every activity states what it wants");
    }

    @Test
    void goHomeRunsOnlyForActivitiesThatAskForItAndOnlyWhenActive() {
        Flow flow = flow(A.class, List.of(step(A.class, body(A.class), false, true, true).whenOff(B.class),
                        on(B.class), step(C.class, body(C.class), true, true, true)),
                Flow.edge(B.NEXT, C.class));
        walk(Run.of(flow).home(() -> log.add("goHome")));

        assertEquals(List.of("B+popup", "goHome", "C+popup"), log,
                "nothing to go home for when the activity won't run");
    }

    // ---- the step budget --------------------------------------------------------------------------------

    @Test
    void aFlowThatNeverEndsGivesUpAfterTheStepBudget() {
        walk(flow(A.class, Flow.limits(4, 0), List.of(on(A.class)), Flow.edge(A.NEXT, A.class)));

        assertEquals(4, log.size(), "four hand-offs, then it gives up rather than looping forever");
    }

    /** {@link Flow.Limits#maxSteps()} documents 0 as "no limit"; it used to stop before the first step. */
    @Test
    void aZeroStepBudgetIsNoLimit() {
        walk(flow(A.class, Flow.limits(0, 0), List.of(on(A.class, A.NEXT, A.NEXT, A.NEXT, A.NEXT, A.NEXT, A.LEFT)),
                Flow.edge(A.NEXT, A.class)));

        assertEquals(6, log.size());
    }
}
