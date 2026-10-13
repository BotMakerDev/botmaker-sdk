package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.vision.FoundMatches;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.observe.MatchEvent;
import com.botmaker.sdk.internal.observe.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Standalone (no Studio, no OpenCV, no real screen) tests for {@link Watchdog}. Drives it with match events —
 * the same picture found at the same place, or a miss (a null result) — and pins
 * {@link BotSettings#maxRetryAttempts()} low for the duration.
 */
class WatchdogTest {

    private BotSettings saved;

    @BeforeEach
    void setUp() {
        saved = BotSettings.current();
        BotSettings.use(saved.maxRetryAttempts(3));
        Watchdog.enable();
        Watchdog.reset();
    }

    @AfterEach
    void tearDown() {
        Watchdog.disable();
        Watchdog.reset();
        BotSettings.use(saved);
    }

    private static void fireSame(int times) {
        for (int i = 0; i < times; i++) {
            Bots.fireMatch(new MatchEvent(Surface.ofScreen(), null, FoundMatches.at("claim", 100, 200)));
        }
    }

    private static void fireMiss(int times) {
        for (int i = 0; i < times; i++) {
            Bots.fireMatch(new MatchEvent(Surface.ofScreen(), null, null));
        }
    }

    @Test
    void checkpointThrowsAfterMaxConsecutiveNoProgress() {
        fireSame(5); // repeats reaches 4 >= MAX(3)
        assertThrows(BotStuckException.class, Watchdog::checkpoint);
    }

    @Test
    void checkpointDoesNotThrowBelowThreshold() {
        fireSame(2); // repeats == 1 < MAX(3)
        assertDoesNotThrow(Watchdog::checkpoint);
    }

    @Test
    void missesAreWaitingNotStuck() {
        // A minute's poll for a picture that is not there yet: the activity's own bound ends it.
        fireMiss(50);
        assertDoesNotThrow(Watchdog::checkpoint);
    }

    @Test
    void aMissEndsARunOfTheSameMatch() {
        fireSame(3);
        fireMiss(1);
        fireSame(2);
        assertDoesNotThrow(Watchdog::checkpoint);
    }

    @Test
    void progressResetsCounter() {
        fireSame(5);
        Watchdog.progress();
        assertDoesNotThrow(Watchdog::checkpoint);
    }

    @Test
    void checkpointResetsAfterThrowingSoRecoveryStartsClean() {
        fireSame(5);
        assertThrows(BotStuckException.class, Watchdog::checkpoint);
        // Counter was reset when it threw; a fresh checkpoint must not throw again.
        assertDoesNotThrow(Watchdog::checkpoint);
    }
}
