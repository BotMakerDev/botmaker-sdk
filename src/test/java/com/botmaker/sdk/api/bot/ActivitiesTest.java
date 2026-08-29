package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.internal.bot.ActivityRegistry;
import com.botmaker.sdk.internal.config.ProjectData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An activity written as a lambda, which is how one is written since 2026-08-29.
 *
 * <p>The thing most worth pinning here is that <b>there is one registry</b>. A bot may hold activities
 * written both ways — a lambda handed to {@link Activities#define}, and a {@link Activity} subclass a
 * pre-2026-08-29 project still has — and {@code Activity.disable("Mining")} has to reach either. Two maps
 * would make that call silently do nothing for half of them, which reads to a user as the flow being wrong
 * rather than as a bug.
 */
class ActivitiesTest {

    @AfterEach
    void tearDown() {
        // The name registry is process-global; keep tests independent.
        ActivityRegistry.clear();
        ProjectData.use(null);
    }

    /** A legacy activity, to prove the two kinds share one registry. */
    private static final class Smelting extends Activity<Smelting.Outcome> {
        enum Outcome { NEXT, DONE }

        @Override public boolean isEnabled() { return true; }
        @Override public Outcome run() { return Outcome.DONE; }
    }

    // ---- defining ---------------------------------------------------------------------------------------

    @Test
    void aDefinedActivityIsRegisteredUnderItsName() {
        Activities.define("Mining", ctx -> ctx.done());

        ActivityRegistry.Runner mining = ActivityRegistry.get("Mining");
        assertNotNull(mining);
        assertEquals("Mining", mining.name());
    }

    @Test
    void theBodyRunsAndItsOutcomeComesBack() {
        Activities.define("Mining", ctx -> ctx.outcome("BAG_FULL"));

        assertEquals("BAG_FULL", ActivityRegistry.get("Mining").execute().name());
    }

    /**
     * A lambda whose last statement fell through has nothing special to report, which is exactly what
     * {@code done()} means. Answering {@code null} must not be a crash mid-flow.
     */
    @Test
    void aBodyThatAnswersNothingIsTreatedAsDone() {
        Activities.define("Mining", ctx -> null);

        assertEquals("NEXT", ActivityRegistry.get("Mining").execute().name());
    }

    @Test
    void definingTheSameNameTwiceKeepsTheLaterBody() {
        Activities.define("Mining", ctx -> ctx.outcome("FIRST"));
        Activities.define("Mining", ctx -> ctx.outcome("SECOND"));

        assertEquals("SECOND", ActivityRegistry.get("Mining").execute().name());
        assertEquals(List.of("Mining"), ActivityRegistry.names());
    }

    @Test
    void aDefinitionWithNoNameOrNoBodyIsIgnoredRatherThanThrown() {
        Activities.define(null, ctx -> ctx.done());
        Activities.define("  ", ctx -> ctx.done());
        Activities.define("Mining", null);

        assertEquals(List.of(), ActivityRegistry.names());
    }

    // ---- one registry -----------------------------------------------------------------------------------

    @Test
    void bothKindsOfActivityLiveInTheOneRegistry() {
        Activities.define("Mining", ctx -> ctx.done());
        new Smelting();

        assertEquals(List.of("Mining", "Smelting"), ActivityRegistry.names());
    }

    /**
     * The reason there is one registry. This call has to reach an activity however it was written; a bot
     * mixing the two kinds is the ordinary state of one being migrated.
     */
    @Test
    void disableByNameReachesADefinedActivityAndASubclassAlike() {
        ProjectData.use(ProjectData.of("""
                { "activities": [ { "name": "Mining", "enabled": true } ] }
                """));
        Activities.define("Mining", ctx -> ctx.done());
        Smelting smelting = new Smelting();

        assertTrue(ActivityRegistry.get("Mining").active());
        assertTrue(smelting.active());

        Activity.disable("Mining");
        Activity.disable("Smelting");

        assertFalse(ActivityRegistry.get("Mining").active());
        assertFalse(smelting.active());
    }

    @Test
    void disableByAnUnknownNameIsANoOp() {
        Activity.disable("Nothing");

        assertNull(ActivityRegistry.get("Nothing"));
    }

    // ---- enablement -------------------------------------------------------------------------------------

    /**
     * {@code active()} reads the project's own file rather than caching it, so a value changed in the editor
     * is picked up without the definition knowing anything about files.
     */
    @Test
    void aDefinedActivityDefersToTheValueSetInTheEditor() {
        ProjectData.use(ProjectData.of("""
                { "activities": [ { "name": "Mining", "enabled": false },
                                  { "name": "Selling", "enabled": true } ] }
                """));
        Activities.define("Mining", ctx -> ctx.done());
        Activities.define("Selling", ctx -> ctx.done());

        assertFalse(ActivityRegistry.get("Mining").active());
        assertTrue(ActivityRegistry.get("Selling").active());
    }

    /** The "do this once, then stop" pattern, from inside the body. */
    @Test
    void aBodyCanSwitchItsOwnActivityOffMidRun() {
        ProjectData.use(ProjectData.of("""
                { "activities": [ { "name": "Mining", "enabled": true } ] }
                """));
        AtomicInteger runs = new AtomicInteger();
        Activities.define("Mining", ctx -> {
            runs.incrementAndGet();
            ctx.disable();
            return ctx.done();
        });

        ActivityRegistry.Runner mining = ActivityRegistry.get("Mining");
        assertTrue(mining.active());
        mining.execute();
        assertFalse(mining.active(), "the override outranks the value set in the editor");
        assertEquals(1, runs.get());
    }

    @Test
    void anOverrideOutranksTheEditorInBothDirections() {
        ProjectData.use(ProjectData.of("""
                { "activities": [ { "name": "Mining", "enabled": false } ] }
                """));
        Activities.define("Mining", ctx -> ctx.done());

        Activity.enable("Mining");
        assertTrue(ActivityRegistry.get("Mining").active());
        Activity.disable("Mining");
        assertFalse(ActivityRegistry.get("Mining").active());
    }

    // ---- the context ------------------------------------------------------------------------------------

    @Test
    void theContextKnowsWhichActivityItIs() {
        List<String> seen = new ArrayList<>();
        Activities.define("Mining", ctx -> {
            seen.add(ctx.name());
            return ctx.done();
        });

        ActivityRegistry.get("Mining").execute();
        assertEquals(List.of("Mining"), seen);
    }

    /**
     * An outcome the canvas does not declare is not refused. It becomes an outcome nothing is wired to,
     * which ends the run — the same answer as an outcome the user declared and never wired, and deliberately
     * the same: a bot must not die mid-flow over a name.
     */
    @Test
    void anUndeclaredOutcomeIsStillReported() {
        ProjectData.use(ProjectData.of("""
                { "activities": [ { "name": "Mining", "enabled": true, "outcomes": [ "BAG_FULL" ] } ] }
                """));
        Activities.define("Mining", ctx -> ctx.outcome("BAG_FUL"));

        assertEquals("BAG_FUL", ActivityRegistry.get("Mining").execute().name());
    }

    @Test
    void doneIsTheImplicitOutcomeAndSoIsABlankName() {
        Activities.define("Mining", ctx -> ctx.done());
        Activities.define("Selling", ctx -> ctx.outcome("  "));

        assertEquals("NEXT", ActivityRegistry.get("Mining").execute().name());
        assertEquals("NEXT", ActivityRegistry.get("Selling").execute().name());
    }

    // ---- the outcome value type -------------------------------------------------------------------------

    @Test
    void outcomesAreEqualWhenTheirNamesAre() {
        assertEquals(Outcome.of("BAG_FULL"), Outcome.of("BAG_FULL"));
        assertEquals(Outcome.of("BAG_FULL").hashCode(), Outcome.of("BAG_FULL").hashCode());
        assertNotEquals(Outcome.of("BAG_FULL"), Outcome.of("bag_full"), "the canvas stores what was typed");
        assertEquals("BAG_FULL", Outcome.of("BAG_FULL").toString());
    }

    @Test
    void aBlankOrMissingOutcomeNameIsTheImplicitOne() {
        assertEquals("NEXT", Outcome.of(null).name());
        assertEquals("NEXT", Outcome.of("").name());
    }
}
