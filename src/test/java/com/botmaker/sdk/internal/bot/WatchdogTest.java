package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.api.vision.FoundMatches;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.observe.MatchEvent;
import com.botmaker.sdk.internal.observe.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Standalone (no Studio, no OpenCV, no real screen) tests for {@link Watchdog}: matches no longer count toward stuck
 * (2026-11-09, the match-repeat count went with flows); a plan run counts its own progress.
 */
class WatchdogTest {

    @BeforeEach
    void setUp() {
        Watchdog.enable();
        Watchdog.reset();
    }

    @AfterEach
    void tearDown() {
        Watchdog.disable();
        Watchdog.reset();
    }

    @Test
    void theSamePictureFoundInPlaceIsNotStuck() {
        // A plan's walk finds the same markers at the same place on every look.
        for (int i = 0; i < 50; i++) {
            Bots.fireMatch(new MatchEvent(Surface.ofScreen(), null, FoundMatches.at("claim", 100, 200)));
        }
        assertDoesNotThrow(Watchdog::checkpoint);
    }
}
