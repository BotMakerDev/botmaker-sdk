package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.Runs;
import com.botmaker.plugin.api.managed.Managed;
import com.botmaker.plugin.api.managed.ManagedValues;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.shared.Diag;
import com.botmaker.shared.capture.NativeControllerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BotSettings} is the bot's {@code @Managed("settings")} value (2026-09-27): what it declares is in force
 * once {@code Bot.run} installs it, and — the part that fails <em>silently</em> — real input has escalated the
 * input backend by then, before the first click.
 */
class BotSettingsTest {

    /** Records only the one call that matters here; the rest of the surface comes from the shared test double. */
    private static final class RecordingInput extends RecordingNativeController {
        private int escalations;

        @Override
        public boolean useReliableInput() {
            escalations++;
            return true;
        }
    }

    /** What a bot's {@code Sdk.java} declares. */
    public static final class Values {
        @Managed("settings")
        public static BotSettings settings() {
            return BotSettings.of(BotSettings.clicks(750, 125, false), BotSettings.vision(0.62, 0.11),
                    BotSettings.input(false, BotSettings.InputBackend.AUTO),
                    BotSettings.session(false, BotSettings.DisplayBackend.XEPHYR), 7, true);
        }
    }

    @AfterEach
    void tearDown() {
        NativeControllerFactory.setForTesting(null);
        BotSettings.resetForTesting();
    }

    @Test
    void aBotWithoutSettingsRunsOnTheDefaults() {
        BotSettings current = BotSettings.current();
        assertEquals(BotSettings.DEFAULTS, current);
        assertEquals(BotSettings.DEFAULT_FOUND_DELAY, current.foundDelay());
        assertEquals(BotSettings.DEFAULT_CONFIDENCE, current.confidence());
        assertTrue(current.session().isolated(), "a bot isolates unless it says otherwise");
        assertFalse(current.realInput());
    }

    @Test
    void theBotsDeclaredSettingsAreInForceOnceInstalled() {
        SdkValues.claim();
        ManagedValues.install(Values.class);

        BotSettings current = BotSettings.current();
        assertEquals(750, current.foundDelay());
        assertEquals(125, current.notFoundDelay());
        assertFalse(current.randomizeClicks());
        assertEquals(0.62, current.confidence());
        assertEquals(0.11, current.compareMargin());
        assertEquals(7, current.maxRetryAttempts());
        assertFalse(ProjectDefaults.sessionIsolated());
        assertEquals("xephyr", ProjectDefaults.sessionBackend());
    }

    @Test
    void realInputEscalatesWhenItIsUsedAndOnlyOnce() {
        RecordingInput input = new RecordingInput();
        NativeControllerFactory.setForTesting(input);

        BotSettings.use(BotSettings.DEFAULTS.realInput(true));
        assertEquals(1, input.escalations, "real input must swap the backend as it is installed");
        BotSettings.use(BotSettings.current().confidence(0.9));
        assertEquals(1, input.escalations, "an unrelated change does not escalate again");
        assertTrue(BotSettings.current().realInput());
    }

    @Test
    void turningRealInputOffCannotUndoTheSwap() {
        NativeControllerFactory.setForTesting(new RecordingInput());
        BotSettings.use(BotSettings.DEFAULTS.realInput(true));

        BotSettings.use(BotSettings.current().realInput(false));

        assertTrue(BotSettings.current().realInput(), "the backend it swapped in is still delivering input");
    }

    @Test
    void aValueOutsideItsRangeIsClampedNotThrown() {
        BotSettings clamped = BotSettings.DEFAULTS.confidence(5).foundDelay(-3).maxRetryAttempts(0);
        assertEquals(1.0, clamped.confidence());
        assertEquals(0, clamped.foundDelay());
        assertEquals(1, clamped.maxRetryAttempts());
    }

    @Test
    void theRunsDebugPropertyWinsOverTheBotsSetting() {
        assertEquals(Runs.DEBUG_PROPERTY, Diag.RUN_PROPERTY, "the SDK reads the property the host writes");
        try {
            System.setProperty(Runs.DEBUG_PROPERTY, "false");
            BotSettings.use(BotSettings.DEFAULTS.debug(true));
            assertFalse(Debug.isEnabled(), "Studio's toggle off silences a bot whose setting is on");

            System.setProperty(Runs.DEBUG_PROPERTY, "true");
            BotSettings.use(BotSettings.DEFAULTS.debug(false));
            assertTrue(Debug.isEnabled(), "and turns on one whose setting is off");

            System.clearProperty(Runs.DEBUG_PROPERTY);
            BotSettings.use(BotSettings.DEFAULTS.debug(false));
            assertFalse(Debug.isEnabled(), "with no toggle the bot decides");
        } finally {
            System.clearProperty(Runs.DEBUG_PROPERTY);
            Debug.enable();
        }
    }

    @Test
    void theLaunchTargetIsTheMachinesRunProperty() {
        String was = System.getProperty(ProjectDefaults.LAUNCH_TARGET);
        try {
            System.clearProperty(ProjectDefaults.LAUNCH_TARGET);
            assertNull(ProjectDefaults.launchTarget());
            System.setProperty(ProjectDefaults.LAUNCH_TARGET, " steam:570 ");
            assertEquals("steam:570", ProjectDefaults.launchTarget());
        } finally {
            if (was == null) System.clearProperty(ProjectDefaults.LAUNCH_TARGET);
            else System.setProperty(ProjectDefaults.LAUNCH_TARGET, was);
        }
    }
}
