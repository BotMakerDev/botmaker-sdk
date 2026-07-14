package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.observe.Bots;
import com.botmaker.sdk.api.observe.MatchEvent;
import com.botmaker.sdk.api.observe.Surface;
import com.botmaker.sdk.api.vision.ClickConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Standalone (no Studio, no OpenCV, no real screen) tests for {@link Watchdog}. Drives it with {@code "miss"}
 * match events (a null result) so no {@code MatchResult} needs constructing, and pins
 * {@link ClickConfig#MAX_RETRY_ATTEMPTS} low for the duration.
 */
class WatchdogTest {

    private int savedMax;

    @BeforeEach
    void setUp() {
        savedMax = ClickConfig.MAX_RETRY_ATTEMPTS;
        ClickConfig.MAX_RETRY_ATTEMPTS = 3;
        Watchdog.enable();
        Watchdog.reset();
    }

    @AfterEach
    void tearDown() {
        Watchdog.disable();
        Watchdog.reset();
        ClickConfig.MAX_RETRY_ATTEMPTS = savedMax;
    }

    private static void fireMiss(int times) {
        for (int i = 0; i < times; i++) {
            Bots.fireMatch(new MatchEvent(Surface.ofScreen(), null, null));
        }
    }

    @Test
    void checkpointThrowsAfterMaxConsecutiveNoProgress() {
        fireMiss(5); // repeats reaches 4 >= MAX(3)
        assertThrows(BotStuckException.class, Watchdog::checkpoint);
    }

    @Test
    void checkpointDoesNotThrowBelowThreshold() {
        fireMiss(2); // repeats == 1 < MAX(3)
        assertDoesNotThrow(Watchdog::checkpoint);
    }

    @Test
    void progressResetsCounter() {
        fireMiss(5);
        Watchdog.progress();
        assertDoesNotThrow(Watchdog::checkpoint);
    }

    @Test
    void checkpointResetsAfterThrowingSoRecoveryStartsClean() {
        fireMiss(5);
        assertThrows(BotStuckException.class, Watchdog::checkpoint);
        // Counter was reset when it threw; a fresh checkpoint must not throw again.
        assertDoesNotThrow(Watchdog::checkpoint);
    }
}
