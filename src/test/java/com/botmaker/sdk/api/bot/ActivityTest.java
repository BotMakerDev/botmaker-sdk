package com.botmaker.sdk.api.bot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the runtime enable/disable override on {@link Activity}: {@link Activity#active()} reconciles the
 * configured {@link Activity#isEnabled()} default with any mid-run {@link Activity#setEnabled} override, and
 * the static name-based {@link Activity#disable(String)} / {@link Activity#enable(String)} controls.
 */
class ActivityTest {

    @AfterEach
    void tearDown() {
        // The name registry is process-global; keep tests independent.
        Activity.clearRegistry();
    }

    /** Minimal activity whose configured default is fixed at construction; {@code run()} reports DEFAULT. */
    private static final class Fake extends Activity<Fake.Outcome> {
        enum Outcome { DEFAULT }
        private final boolean configured;
        Fake(boolean configured) { this.configured = configured; }
        Fake(String name, boolean configured) { super(name); this.configured = configured; }
        @Override public boolean isEnabled() { return configured; }
        @Override public Outcome run() { return Outcome.DEFAULT; }
    }

    @Test
    void activeDefersToIsEnabledWhenNoOverrideIsSet() {
        assertTrue(new Fake(true).active(), "no override → follows the enabled configured default");
        assertFalse(new Fake(false).active(), "no override → follows the disabled configured default");
    }

    @Test
    void setEnabledFalseDisablesAnActivityWhoseConfiguredDefaultIsTrue() {
        Fake activity = new Fake(true);
        activity.setEnabled(false);
        assertTrue(activity.isEnabled(), "the configured default is untouched");
        assertFalse(activity.active(), "the runtime override outranks the configured default");
    }

    @Test
    void setEnabledTrueEnablesAnActivityWhoseConfiguredDefaultIsFalse() {
        Fake activity = new Fake(false);
        activity.setEnabled(true);
        assertFalse(activity.isEnabled(), "the configured default is untouched");
        assertTrue(activity.active(), "the runtime override outranks the configured default");
    }

    @Test
    void disableAndEnableAreShortcutsForSetEnabled() {
        Fake activity = new Fake(true);
        activity.disable();
        assertFalse(activity.active(), "disable() == setEnabled(false)");
        activity.enable();
        assertTrue(activity.active(), "enable() == setEnabled(true)");
    }

    @Test
    void staticDisableByNameTogglesThatActivityOnly() {
        Fake mining = new Fake("Mining", true);
        Fake fishing = new Fake("Fishing", true);

        Activity.disable("Mining");
        assertFalse(mining.active(), "the named activity is disabled from anywhere, by name");
        assertTrue(fishing.active(), "other activities are untouched");

        Activity.enable("Mining");
        assertTrue(mining.active(), "and can be re-enabled by name");
    }

    /** An activity with more than one outcome, so {@code execute()} has something to actually carry back. */
    private static final class Branching extends Activity<Branching.Outcome> {
        enum Outcome { DEFAULT, BAG_FULL }
        private final Outcome reported;
        private final StringBuilder trace;
        Branching(Outcome reported, StringBuilder trace) { this.reported = reported; this.trace = trace; }
        @Override public boolean isEnabled() { return true; }
        @Override protected void before() { trace.append("before "); }
        @Override protected void after() { trace.append("after"); }
        @Override public Outcome run() { trace.append("run "); return reported; }
    }

    @Test
    void executeReturnsTheOutcomeRunReported() {
        StringBuilder trace = new StringBuilder();
        Branching activity = new Branching(Branching.Outcome.BAG_FULL, trace);

        // Statically Branching.Outcome, not a marker type — the driver switches over this without a cast.
        Branching.Outcome outcome = activity.execute();

        assertSame(Branching.Outcome.BAG_FULL, outcome, "the flow routes on what run() reported");
        assertEquals("before run after", trace.toString(), "the hooks still wrap run()");
    }

    @Test
    void aStuckActivityReportsNoOutcomeAndStillRethrows() {
        StringBuilder trace = new StringBuilder();
        Activity<Branching.Outcome> stuck = new Activity<>() {
            @Override public boolean isEnabled() { return true; }
            @Override protected void onStuck(BotStuckException e) { trace.append("onStuck "); }
            @Override protected void after() { trace.append("after "); }
            @Override public Branching.Outcome run() { throw new BotStuckException("no ore"); }
        };

        assertThrows(BotStuckException.class, stuck::execute,
                "being stuck is the supervisor's business, not the flow's — it must not become an outcome");
        assertEquals("onStuck ", trace.toString(), "after() is skipped when run() didn't finish");
    }

    @Test
    void staticSetEnabledOnAnUnknownNameWarnsAndIsANoOp() {
        Fake mining = new Fake("Mining", true);
        // A typo'd name must not throw (it would crash a running bot) — it warns and leaves state alone.
        Activity.disable("Minning");
        assertTrue(mining.active(), "an unknown name changes nothing");
    }
}
