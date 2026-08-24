package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.sdk.api.bot.PopupGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shape an Activity Flow can take, walked.
 *
 * <p>None of this was testable before: the walk was text inside a Studio generator, so the only way to find
 * out what a branch did was to create a project, draw one, and run the bot. It is now a table and a loop, and
 * the loop is here.
 *
 * <p>Two things every test has to work around. A run always ends in {@code Bot.stop()}, which throws — so
 * {@link #walk} catches, and a test that never reaches the end would fail on the missing throw rather than
 * pass by accident. And activities register themselves globally by name, so each test builds its own with
 * distinct names.
 */
class FlowWalkerTest {

    /** The three outcomes every fake reports from; shared so routes stay readable. */
    enum Out { DEFAULT, LEFT, RIGHT }

    /**
     * A do-nothing check, because {@link PopupGuard#isEnabled()} is "switched on <em>and</em> installed" — with
     * no handler it reads false whatever the walker set, and every popup assertion below would pass vacuously.
     */
    @BeforeEach
    void installAPopupCheck() {
        PopupGuard.install(() -> {});
    }

    @AfterEach
    void removeIt() {
        PopupGuard.uninstall();
        PopupGuard.enabled(true);
    }

    /** Records that it ran and what the popup guard was set to, and reports a scripted outcome each time. */
    private static final class Fake extends Activity<Out> {

        private final boolean enabled;
        private final Deque<Out> script;
        private final List<String> log;

        Fake(String name, List<String> log, boolean enabled, Out... outcomes) {
            super(name);
            this.enabled = enabled;
            this.log = log;
            this.script = new ArrayDeque<>(List.of(outcomes));
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Out run() {
            log.add(name() + (PopupGuard.isEnabled() ? "+popup" : "-popup"));
            return script.isEmpty() ? Out.DEFAULT : script.removeFirst();
        }
    }

    /** Runs a flow to its end, asserting it actually ended rather than returning. */
    private static void walk(FlowGraph graph, int maxSteps, Runnable goHome) {
        assertThrows(RuntimeException.class, () -> FlowGraph.walk(graph, maxSteps, 0, goHome),
                "a run ends by stopping the bot, which throws");
    }

    private static void walk(FlowGraph graph) {
        walk(graph, 100, null);
    }

    // ------------------------------------------------------------------
    // routing
    // ------------------------------------------------------------------

    @Test
    void anOutcomeGoesWhereItsRouteSays() {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("branch-A", log, true, Out.RIGHT);
        Fake left = new Fake("branch-L", log, true);
        Fake right = new Fake("branch-R", log, true);
        walk(FlowGraph.of("branch-A",
                FlowGraph.node("branch-A", a, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.LEFT, "branch-L"),
                        FlowGraph.route(Out.RIGHT, "branch-R")),
                FlowGraph.node("branch-L", left, PopupCheck.ON, Recovery.NONE, null),
                FlowGraph.node("branch-R", right, PopupCheck.ON, Recovery.NONE, null)));
        assertEquals(List.of("branch-A+popup", "branch-R+popup"), log,
                "the RIGHT outcome takes the RIGHT wire and the other node never runs");
    }

    @Test
    void twoNodesMayLeadToTheSameOne() {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("join-A", log, true, Out.LEFT);
        Fake b = new Fake("join-B", log, true, Out.DEFAULT);
        Fake end = new Fake("join-E", log, true);
        walk(FlowGraph.of("join-A",
                FlowGraph.node("join-A", a, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.LEFT, "join-B")),
                FlowGraph.node("join-B", b, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.DEFAULT, "join-E")),
                FlowGraph.node("join-E", end, PopupCheck.ON, Recovery.NONE, null)));
        assertEquals(List.of("join-A+popup", "join-B+popup", "join-E+popup"), log);
    }

    @Test
    void aFlowMayLoopBackOnItself() {
        List<String> log = new ArrayList<>();
        // Three passes, then an outcome with nothing wired to it.
        Fake a = new Fake("loop-A", log, true, Out.DEFAULT, Out.DEFAULT, Out.LEFT);
        walk(FlowGraph.of("loop-A",
                FlowGraph.node("loop-A", a, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.DEFAULT, "loop-A"))));
        assertEquals(3, log.size(), "it loops until an outcome runs out of wire");
    }

    @Test
    void anUnwiredOutcomeEndsTheRun() {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("unwired-A", log, true, Out.RIGHT);
        Fake b = new Fake("unwired-B", log, true);
        walk(FlowGraph.of("unwired-A",
                FlowGraph.node("unwired-A", a, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.LEFT, "unwired-B")),
                FlowGraph.node("unwired-B", b, PopupCheck.ON, Recovery.NONE, null)));
        assertEquals(List.of("unwired-A+popup"), log, "RIGHT has no wire, so the run stops there");
    }

    @Test
    void aRouteToANodeTheGraphDoesNotHaveEndsTheRun() {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("stale-A", log, true, Out.DEFAULT);
        walk(FlowGraph.of("stale-A",
                FlowGraph.node("stale-A", a, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.DEFAULT, "deleted"))));
        assertEquals(List.of("stale-A+popup"), log);
    }

    @Test
    void anEmptyFlowStopsWithoutRunningAnything() {
        walk(FlowGraph.of(null));
    }

    // ------------------------------------------------------------------
    // per-node settings
    // ------------------------------------------------------------------

    @Test
    void aDisabledActivityFollowsItsFallThroughWireWithoutRunning() {
        List<String> log = new ArrayList<>();
        Fake off = new Fake("off-A", log, false, Out.DEFAULT);
        Fake next = new Fake("off-B", log, true);
        walk(FlowGraph.of("off-A",
                FlowGraph.node("off-A", off, PopupCheck.ON, Recovery.NONE, "off-B",
                        FlowGraph.route(Out.DEFAULT, "off-A")),
                FlowGraph.node("off-B", next, PopupCheck.ON, Recovery.NONE, null)));
        assertEquals(List.of("off-B+popup"), log,
                "the flow still passes through a disabled node, it just does nothing there");
    }

    @Test
    void aDisabledActivityWithNothingToFallThroughToEndsTheRun() {
        List<String> log = new ArrayList<>();
        Fake off = new Fake("dead-A", log, false);
        walk(FlowGraph.of("dead-A",
                FlowGraph.node("dead-A", off, PopupCheck.ON, Recovery.NONE, null)));
        assertTrue(log.isEmpty());
    }

    @Test
    void popupCheckIsSetPerNodeRatherThanInherited() {
        List<String> log = new ArrayList<>();
        Fake on = new Fake("pop-A", log, true, Out.DEFAULT);
        Fake offNode = new Fake("pop-B", log, true, Out.DEFAULT);
        Fake back = new Fake("pop-C", log, true);
        PopupGuard.enabled(false);
        walk(FlowGraph.of("pop-A",
                FlowGraph.node("pop-A", on, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.DEFAULT, "pop-B")),
                FlowGraph.node("pop-B", offNode, PopupCheck.OFF, Recovery.NONE, null,
                        FlowGraph.route(Out.DEFAULT, "pop-C")),
                FlowGraph.node("pop-C", back, PopupCheck.ON, Recovery.NONE, null)));
        assertEquals(List.of("pop-A+popup", "pop-B-popup", "pop-C+popup"), log,
                "PopupGuard is process-global, so every node states what it wants");
    }

    @Test
    void goHomeRunsOnlyForNodesThatAskForItAndOnlyWhenTheActivityIsActive() {
        List<String> log = new ArrayList<>();
        Fake off = new Fake("home-A", log, false);
        Fake plain = new Fake("home-B", log, true, Out.DEFAULT);
        Fake homing = new Fake("home-C", log, true);
        walk(FlowGraph.of("home-A",
                FlowGraph.node("home-A", off, PopupCheck.ON, Recovery.GO_HOME, "home-B"),
                FlowGraph.node("home-B", plain, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.DEFAULT, "home-C")),
                FlowGraph.node("home-C", homing, PopupCheck.ON, Recovery.GO_HOME, null)),
                100, () -> log.add("goHome"));
        assertEquals(List.of("home-B+popup", "goHome", "home-C+popup"), log,
                "nothing to go home for when the activity won't run");
    }

    // ------------------------------------------------------------------
    // the step budget
    // ------------------------------------------------------------------

    @Test
    void aFlowThatNeverEndsGivesUpAfterTheStepBudget() {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("budget-A", log, true);   // always DEFAULT, always back to itself
        walk(FlowGraph.of("budget-A",
                FlowGraph.node("budget-A", a, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.DEFAULT, "budget-A"))),
                4, null);
        assertEquals(4, log.size(), "four hand-offs, then it gives up rather than looping forever");
    }

    // ------------------------------------------------------------------
    // the table itself
    // ------------------------------------------------------------------

    @Test
    void twoNodesCannotShareAName() {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("dup-A", log, true);
        assertThrows(IllegalArgumentException.class, () -> FlowGraph.of("dup-A",
                FlowGraph.node("dup-A", a, PopupCheck.ON, Recovery.NONE, null),
                FlowGraph.node("dup-A", a, PopupCheck.ON, Recovery.NONE, null)));
    }

    @Test
    void oneOutcomeCannotLeadToTwoPlaces() {
        List<String> log = new ArrayList<>();
        Fake a = new Fake("twice-A", log, true);
        assertThrows(IllegalArgumentException.class, () ->
                FlowGraph.node("twice-A", a, PopupCheck.ON, Recovery.NONE, null,
                        FlowGraph.route(Out.DEFAULT, "x"),
                        FlowGraph.route(Out.DEFAULT, "y")));
    }
}
