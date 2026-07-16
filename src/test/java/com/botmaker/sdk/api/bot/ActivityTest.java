package com.botmaker.sdk.api.bot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** Minimal activity whose configured default is fixed at construction; {@code run()} is a no-op. */
    private static final class Fake extends Activity {
        private final boolean configured;
        Fake(boolean configured) { this.configured = configured; }
        Fake(String name, boolean configured) { super(name); this.configured = configured; }
        @Override public boolean isEnabled() { return configured; }
        @Override public void run() {}
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

    @Test
    void staticSetEnabledOnAnUnknownNameWarnsAndIsANoOp() {
        Fake mining = new Fake("Mining", true);
        // A typo'd name must not throw (it would crash a running bot) — it warns and leaves state alone.
        Activity.disable("Minning");
        assertTrue(mining.active(), "an unknown name changes nothing");
    }
}
