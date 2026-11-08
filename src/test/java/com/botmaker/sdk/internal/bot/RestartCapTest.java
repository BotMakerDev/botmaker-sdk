package com.botmaker.sdk.internal.bot;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Five restarts within thirty minutes go ahead; the sixth stops the bot; an old one no longer counts. */
class RestartCapTest {

    private static final Instant NOON = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    void theSixthRestartInThirtyMinutesStopsTheBot() {
        RestartCap cap = new RestartCap(5, Duration.ofMinutes(30));
        for (int i = 0; i < 5; i++) {
            assertTrue(cap.restart(NOON.plus(Duration.ofMinutes(i))).isEmpty(), "restart " + (i + 1));
        }

        String why = cap.restart(NOON.plus(Duration.ofMinutes(10))).orElseThrow();

        assertTrue(why.startsWith("The game was restarted 5 times in the last 30 min"), why);
        assertTrue(cap.restart(NOON.plus(Duration.ofMinutes(30))).isEmpty(), "the first, thirty minutes old, is gone");
    }
}
