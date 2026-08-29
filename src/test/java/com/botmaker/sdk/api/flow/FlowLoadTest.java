package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.api.flow.activities.Selling;
import com.botmaker.sdk.internal.config.ProjectData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The graph a project's own {@code activities.json} describes.
 *
 * <p>This is the test for what replaced two generated files. The old {@code FlowDriver} table was checked by
 * compiling it — a route built from another activity's outcome constant did not compile — and reading the
 * same table from a file gives that up, so what is checked here is everything that check used to cover: the
 * right activity behind each node, the routes, the {@code DISABLED} slot, and the start resolved against
 * what is actually placed.
 *
 * <p>The other half is the convention. An activity's class is {@code <the anchor's package>.activities.<Name>}
 * and nothing writes that down anywhere — which is the point, a manifest being a second statement of the same
 * fact — so the anchor here is this test class and the activities really do sit beside it.
 */
class FlowLoadTest {

    /** A model with one placed activity and nothing wired. */
    private static final String ONE = """
            { "activities": [ { "name": "Mining", "enabled": true, "goHome": true, "popupCheck": true } ],
              "flow": { "nodes": [ { "activity": "Mining" } ], "edges": [], "start": "Mining" } }
            """;

    /** A branch, a loop, a DISABLED wire, a stale wire, and an activity placed but never wired to. */
    private static final String WIRED = """
            { "activities": [ { "name": "Mining", "enabled": true, "goHome": false, "popupCheck": false },
                              { "name": "Selling", "enabled": true, "goHome": true, "popupCheck": true } ],
              "flow": { "nodes": [ { "activity": "Mining" }, { "activity": "Selling" } ],
                        "edges": [ { "from": "Mining", "to": "Selling", "outcome": "BAG_FULL" },
                                   { "from": "Mining", "to": "Mining", "outcome": "" },
                                   { "from": "Mining", "to": "Selling", "outcome": "DISABLED" },
                                   { "from": "Selling", "to": "Nowhere", "outcome": "" } ],
                        "start": "Selling", "maxSteps": 50, "stepDelayMs": 0 } }
            """;

    private static FlowGraph graph(String json) {
        return FlowGraph.assemble(FlowLoadTest.class, ProjectData.of(json));
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
        assertEquals("Mining", graph("""
                { "activities": [ { "name": "Mining" } ],
                  "flow": { "nodes": [ { "activity": "Mining" } ], "start": "Deleted" } }
                """).start());
    }

    @Test
    void anActivityWithNoClassBehindItIsNotANodeAtAll() {
        // It is in the configuration and not in the source — renamed by hand, or never written. One line on
        // the console and no node; a wire into it ends the run, which is what an unwired outcome does.
        FlowGraph flow = graph("""
                { "activities": [ { "name": "Smithing" } ],
                  "flow": { "nodes": [ { "activity": "Smithing" } ], "start": "Smithing" } }
                """);

        assertNull(flow.nodeNamed("Smithing"));
        assertNull(flow.start(), "with no node to begin at, the run ends immediately");
    }

    @Test
    void anEmptyModelIsAnEmptyGraphRatherThanAFailure() {
        FlowGraph flow = FlowGraph.assemble(FlowLoadTest.class, ProjectData.empty());

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
    void readsTheStepBudgetAndThePauseFromTheFile() {
        ProjectData wired = ProjectData.of(WIRED);
        assertEquals(50, wired.maxSteps());
        // An explicit 0 is a user asking for no pause and must survive; absent is the default instead.
        assertEquals(0, wired.stepDelayMs());
        assertEquals(1000, ProjectData.of(ONE).maxSteps());
        assertEquals(1000, ProjectData.of(ONE).stepDelayMs());
    }
}
