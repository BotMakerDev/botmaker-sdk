package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.api.bot.ActivityBody;
import com.botmaker.sdk.api.flow.activities.Selling;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The graph an installed {@link Flow} describes.
 *
 * <p>This is the test for what replaced two generated files. The old {@code FlowDriver} table was checked by
 * compiling it — a route built from another activity's outcome constant did not compile — so what is checked
 * here is everything that check used to cover: the right activity behind each node, the routes, the
 * {@code DISABLED} slot, and the start resolved against what is actually placed.
 *
 * <p><b>It was over {@code activities.json} until 2026-09-21</b>, with each case written as a JSON document.
 * The flow is a value in the bot's own Java now, so each case is a {@code Flow.of(…)} — which is not merely
 * a different spelling of the same test: the value is the thing the bot installs, so what is asserted here
 * is the object the running bot actually walks rather than a parse of a file that stands in for it.
 *
 * <p>Every activity here has {@link ActivityBody#NONE} for a body, which keeps the <b>older</b> half under
 * test: with nothing named in the flow, the body is looked for by convention at
 * {@code <the anchor's package>.activities.<Name>}, so the anchor is this test class and the activities
 * really do sit beside it. A flow whose bodies are method references needs none of that lookup, which
 * {@code aBodyNamedInTheFlowNeedsNoLookup} covers.
 */
class FlowLoadTest {

    /** An activity with no body named in the flow, so it is looked for by convention. */
    private static Flow.Activity unwritten(String name, boolean goHome, boolean popupCheck,
                                           String... outcomes) {
        return Flow.activity(ActivityBody.NONE, name, "", true, goHome, popupCheck, List.of(outcomes));
    }

    /** One placed activity and nothing wired. */
    private static final Flow ONE = Flow.of(
            List.of(unwritten("Mining", true, true)),
            List.of(), List.of(), "Mining", Flow.Limits.DEFAULT);

    /** A branch, a loop, a DISABLED wire, a stale wire, and an activity placed but never wired to. */
    private static final Flow WIRED = Flow.of(
            List.of(unwritten("Mining", false, false, "BAG_FULL"),
                    unwritten("Selling", true, true)),
            List.of(Flow.edge("Mining", "Selling", "BAG_FULL"),
                    Flow.edge("Mining", "Mining", ""),
                    Flow.edge("Mining", "Selling", "DISABLED"),
                    Flow.edge("Selling", "Nowhere", "")),
            List.of(), "Selling", Flow.limits(50, 0));

    private static FlowGraph graph(Flow flow) {
        return FlowGraph.assemble(FlowLoadTest.class, flow);
    }

    @Test
    void buildsANodePerPlacedActivityAndFindsItsClassByConvention() {
        FlowGraph flow = graph(ONE);

        assertEquals("Mining", flow.start());
        FlowGraph.Node mining = flow.nodeNamed("Mining");
        assertNotNull(mining, "com.botmaker.sdk.api.flow.activities.Mining should have been found");
        assertEquals("Mining", mining.activity().name());
        assertEquals(PopupCheck.ON, mining.popupCheck());
        assertEquals(Recovery.GO_HOME, mining.recovery());
    }

    @Test
    void readsEachActivitysOwnFlagsSeparately() {
        FlowGraph flow = graph(WIRED);

        assertEquals(PopupCheck.OFF, flow.nodeNamed("Mining").popupCheck());
        assertEquals(Recovery.NONE, flow.nodeNamed("Mining").recovery());
        assertEquals(PopupCheck.ON, flow.nodeNamed("Selling").popupCheck());
        assertEquals(Recovery.GO_HOME, flow.nodeNamed("Selling").recovery());
    }

    @Test
    void routesOnOutcomeNames() {
        FlowGraph.Node mining = graph(WIRED).nodeNamed("Mining");

        assertEquals("Selling", mining.target(com.botmaker.sdk.api.flow.activities.Mining.Outcome.BAG_FULL));
        // A blank stored outcome is NEXT — blank-means-implicit is what let that constant be renamed once
        // already, and reading it any other way here would undo that.
        assertEquals("Mining", mining.target(com.botmaker.sdk.api.flow.activities.Mining.Outcome.NEXT));
    }

    @Test
    void anUnwiredOutcomeGoesNowhereWhichIsHowARunEnds() {
        assertNull(graph(ONE).nodeNamed("Mining")
                .target(com.botmaker.sdk.api.flow.activities.Mining.Outcome.BAG_FULL));
    }

    @Test
    void disabledIsASlotOnTheNodeAndNeverARoute() {
        FlowGraph.Node mining = graph(WIRED).nodeNamed("Mining");

        assertEquals("Selling", mining.whenDisabled());
        // An activity with no DISABLED wire ends the run when it is switched off, rather than inheriting
        // one — the wire is drawn, never inferred from NEXT, which is what the editor's own note records.
        assertNull(graph(WIRED).nodeNamed("Selling").whenDisabled());
    }

    @Test
    void keepsTheStoredStartWhenItNamesAPlacedActivity() {
        assertEquals("Selling", graph(WIRED).start());
    }

    @Test
    void fallsBackToTheFirstPlacedActivityWhenTheStartIsStale() {
        // The start activity was deleted or renamed. Refusing to run would make one stale string in a file
        // the reason a bot does nothing; the editor's own resolvedStart answers this the same way.
        assertEquals("Mining", graph(Flow.of(List.of(unwritten("Mining", true, true)),
                List.of(), List.of(), "Deleted", Flow.Limits.DEFAULT)).start());
    }

    /**
     * The deliberate reversal of 2026-08-29: an activity with no body used to be dropped, so a wire into it
     * ended the run. Now it is a node with no runner, and the walk treats that exactly as a disabled
     * activity — the flow passes through and takes the {@code DISABLED} wire.
     *
     * <p>Which is what makes drawing a flow before writing its code an ordinary way to work: every card is
     * on the canvas, the run walks through them, and the ones with no {@code Activities.define} yet fall
     * through rather than stopping the bot at the first of them.
     */
    @Test
    void anActivityWithNoBodyIsANodeThatDoesNothing() {
        FlowGraph flow = graph(Flow.of(List.of(unwritten("Smithing", true, true)),
                List.of(Flow.edge("Smithing", "Smithing", "DISABLED")),
                List.of(), "Smithing", Flow.Limits.DEFAULT));

        FlowGraph.Node node = flow.nodeNamed("Smithing");
        assertEquals("Smithing", flow.start());
        assertNotNull(node, "it is on the canvas, so it is in the graph");
        assertNull(node.runner(), "nothing has been written for it");
        assertNull(node.activity(), "and it is certainly not an Activity subclass");
        assertEquals("Smithing", node.whenDisabled(), "which is the wire the walk will take");
    }

    @Test
    void anEmptyModelIsAnEmptyGraphRatherThanAFailure() {
        FlowGraph flow = FlowGraph.assemble(FlowLoadTest.class, Flow.NONE);

        assertNull(flow.start());
        assertNull(flow.nodeNamed("Mining"));
    }

    @Test
    void constructingTheActivitiesIsWhatRegistersThemByName() {
        // The generated registry's ALL field existed for this side effect and nothing else: it is what makes
        // Activity.disable("Mining") resolve from inside another activity's body.
        Selling.on = true;
        FlowGraph flow = graph(WIRED);
        assertTrue(flow.nodeNamed("Selling").activity().active());

        com.botmaker.sdk.api.bot.Activity.disable("Selling");
        assertFalse(flow.nodeNamed("Selling").activity().active());

        com.botmaker.sdk.api.bot.Activity.enable("Selling");
    }

    @Test
    void theLimitsAreTheFlowsOwn() {
        assertEquals(50, WIRED.limits().maxSteps());
        // An explicit 0 is a user asking for no pause and must survive: it is a number in the bot's own
        // source now rather than a key that might be absent, so there is nothing left to confuse it with.
        assertEquals(0, WIRED.limits().stepDelayMs());
        assertEquals(1000, ONE.limits().maxSteps());
        assertEquals(1000, ONE.limits().stepDelayMs());
    }

    /**
     * A body named in the flow is run directly, with no lookup by convention and no class of that name.
     *
     * <p>This is what the whole change bought: {@code Collect::body} is resolved by javac, so there is no
     * name in the middle to go stale. {@code Nowhere} has no class beside this test and never could be
     * found by convention, and the node still runs.
     */
    @Test
    void aBodyNamedInTheFlowNeedsNoLookup() {
        Flow flow = Flow.of(List.of(Flow.activity(ctx -> ctx.outcome("DONE"), "Nowhere", "",
                        true, false, false, List.of("DONE"))),
                List.of(), List.of(), "Nowhere", Flow.Limits.DEFAULT);

        FlowGraph.Node node = graph(flow).nodeNamed("Nowhere");
        assertNotNull(node.runner(), "the flow named its body, so nothing had to be looked up");
        assertTrue(node.runner().active());
        assertEquals("DONE", node.runner().execute().name());
    }

    /** An activity switched off in the flow is switched off in the graph, before anything overrides it. */
    @Test
    void theEnableFlagComesFromTheFlow() {
        Flow flow = Flow.of(List.of(Flow.activity(ctx -> ctx.done(), "Resting", "",
                        false, false, false, List.of())),
                List.of(), List.of(), "Resting", Flow.Limits.DEFAULT);

        assertFalse(graph(flow).nodeNamed("Resting").runner().active());
    }
}
