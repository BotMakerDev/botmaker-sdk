package com.botmaker.sdk.api;

import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.config.ProjectProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BotSettings} is what a project's tuning became when the generated {@code BotSettings.java} was
 * dropped, so these cover the two things that file used to guarantee by being called at the top of
 * {@code main}: that the project's values are in force before anything reads one, and — the part that fails
 * <em>silently</em> — that {@code input.real} has escalated the input backend before the first click.
 */
class BotSettingsTest {

    /** Records only the one call that matters here; the rest of the surface comes from the shared test double. */
    private static final class RecordingInput extends RecordingNativeController {
        private boolean escalated;

        @Override
        public boolean useReliableInput() {
            escalated = true;
            return true;
        }
    }

    private static void configure(String... keyValuePairs) {
        Properties p = new Properties();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            p.setProperty(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        ProjectProperties.setForTesting(p);
        BotSettings.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        ProjectProperties.setForTesting(null);
        NativeControllerFactory.setForTesting(null);
        BotSettings.resetForTesting();
    }

    @Test
    void everyValueIsSeededFromTheProjectFile() {
        configure(ProjectProperties.KEY_CLICKS_FOUND_DELAY, "750",
                ProjectProperties.KEY_CLICKS_NOT_FOUND_DELAY, "125",
                ProjectProperties.KEY_CLICKS_RANDOMIZE, "false",
                ProjectProperties.KEY_VISION_CONFIDENCE, "0.62",
                ProjectProperties.KEY_VISION_COMPARE_MARGIN, "0.11",
                ProjectProperties.KEY_BOT_MAX_RETRY_ATTEMPTS, "7");

        assertEquals(750, BotSettings.foundDelay());
        assertEquals(125, BotSettings.notFoundDelay());
        assertFalse(BotSettings.randomizeClicks());
        assertEquals(0.62, BotSettings.confidence());
        assertEquals(0.11, BotSettings.compareMargin());
        assertEquals(7, BotSettings.maxRetryAttempts());
    }

    @Test
    void anAbsentKeyLeavesTheSdkDefault() {
        configure(ProjectProperties.KEY_CLICKS_FOUND_DELAY, "750");

        assertEquals(750, BotSettings.foundDelay());
        assertEquals(BotSettings.DEFAULT_CONFIDENCE, BotSettings.confidence());
        assertEquals(BotSettings.DEFAULT_MAX_RETRY_ATTEMPTS, BotSettings.maxRetryAttempts());
    }

    @Test
    void anOutOfRangeValueLeavesTheDefaultRatherThanThrowing() {
        // The setters throw on a bad value, and this load happens inside whatever call first reads a setting —
        // so a hand-typed confidence of 5 must degrade to the default, not blow up a bot's first vision call.
        configure(ProjectProperties.KEY_VISION_CONFIDENCE, "5",
                ProjectProperties.KEY_CLICKS_FOUND_DELAY, "-1",
                ProjectProperties.KEY_BOT_MAX_RETRY_ATTEMPTS, "0");

        assertEquals(BotSettings.DEFAULT_CONFIDENCE, BotSettings.confidence());
        assertEquals(BotSettings.DEFAULT_FOUND_DELAY, BotSettings.foundDelay());
        assertEquals(BotSettings.DEFAULT_MAX_RETRY_ATTEMPTS, BotSettings.maxRetryAttempts());
    }

    /**
     * The one that fails silently in production: real input swaps the process-wide Linux backend one-way and
     * must happen <em>before</em> the first click, or the click is dropped with neither OS reporting it. With
     * no generated {@code apply()} left to call, the guarantee is that reading any setting performs the swap —
     * and every click path reads one.
     */
    @Test
    void readingAnySettingEscalatesInputWhenTheProjectAsksForIt() {
        RecordingInput controller = new RecordingInput();
        NativeControllerFactory.setForTesting(controller);
        configure(ProjectProperties.KEY_INPUT_REAL, "true");

        // A plain read, exactly as ImageClicker makes before it clicks.
        BotSettings.confidence();

        assertTrue(controller.escalated, "the backend must be swapped by the first read, not by a later call");
        assertTrue(BotSettings.realInput());
    }

    @Test
    void inputIsNotEscalatedWhenTheProjectDoesNotAskForIt() {
        RecordingInput controller = new RecordingInput();
        NativeControllerFactory.setForTesting(controller);
        configure(ProjectProperties.KEY_CLICKS_FOUND_DELAY, "750");

        BotSettings.confidence();

        assertFalse(controller.escalated, "an ordinary bot must keep the cursor-safe backend");
        assertFalse(BotSettings.realInput());
    }

    @Test
    void theLinuxBackendPinIsAppliedAsTheSystemPropertyTheControllerReads() {
        String saved = System.getProperty("botmaker.linux.input");
        System.clearProperty("botmaker.linux.input");
        try {
            configure(ProjectProperties.KEY_INPUT_LINUX_BACKEND, "uinput");

            BotSettings.confidence();

            assertEquals("uinput", System.getProperty("botmaker.linux.input"));
        } finally {
            if (saved == null) {
                System.clearProperty("botmaker.linux.input");
            } else {
                System.setProperty("botmaker.linux.input", saved);
            }
        }
    }

    @Test
    void aRuntimeSetterWinsOverTheProjectValue() {
        configure(ProjectProperties.KEY_VISION_CONFIDENCE, "0.62");

        BotSettings.setDefaultConfidence(0.95);

        assertEquals(0.95, BotSettings.confidence(), "the project seeds the value; bot code overrides it");
    }

    @Test
    void aSetterStillRejectsAnOutOfRangeArgument() {
        configure();
        assertThrows(IllegalArgumentException.class, () -> BotSettings.setDefaultConfidence(1.5));
        assertThrows(IllegalArgumentException.class, () -> BotSettings.setFoundDelay(-1));
        assertThrows(IllegalArgumentException.class, () -> BotSettings.setMaxRetryAttempts(0));
    }

    @Test
    void resetToDefaultsDiscardsTheProjectValuesRatherThanReloadingThem() {
        configure(ProjectProperties.KEY_VISION_CONFIDENCE, "0.62");
        assertEquals(0.62, BotSettings.confidence());

        BotSettings.resetToDefaults();

        assertEquals(BotSettings.DEFAULT_CONFIDENCE, BotSettings.confidence());
    }
}
