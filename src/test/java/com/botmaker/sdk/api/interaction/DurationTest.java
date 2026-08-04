package com.botmaker.sdk.api.interaction;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Duration}'s two jobs: the unit conversions are exact (a bot that meant 1.5s must not wait 1s), and a
 * range re-rolls per read — the property the humanized wait is built on, and the one a naive implementation
 * that samples once at construction silently loses.
 */
class DurationTest {

    @Test
    void theFactoriesAllLandOnMilliseconds() {
        assertEquals(1500, Duration.ms(1500).millis());
        assertEquals(1500, Duration.seconds(1.5).millis());
        assertEquals(90_000, Duration.minutes(1.5).millis());
        assertEquals(Duration.ms(1500), Duration.seconds(1.5), "the same length is the same value");
    }

    @Test
    void aFractionOfAMillisecondRoundsRatherThanTruncating() {
        // (int)(0.0015 * 1000) would be 1; the wait a bot asked for is nearer 2.
        assertEquals(2, Duration.seconds(0.0015).millis());
    }

    @Test
    void aFixedDurationIsNotARangeAndAlwaysReadsTheSame() {
        Duration fixed = Duration.seconds(2);
        assertFalse(fixed.isRange());
        for (int i = 0; i < 50; i++) assertEquals(2000, fixed.millis());
    }

    @Test
    void aRangeStaysInsideItsBoundsAndVariesBetweenReads() {
        Duration range = Duration.between(Duration.ms(800), Duration.ms(1500));
        assertTrue(range.isRange());
        assertEquals(800, range.minMillis());
        assertEquals(1500, range.maxMillis());

        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            long ms = range.millis();
            assertTrue(ms >= 800 && ms <= 1500, "outside the range: " + ms);
            seen.add(ms);
        }
        assertTrue(seen.size() > 1, "a range that always reads the same is not humanizing anything");
    }

    @Test
    void betweenTakesTheWidestSpanWhicheverWayRound() {
        assertEquals(Duration.between(Duration.ms(800), Duration.ms(1500)),
                Duration.between(Duration.ms(1500), Duration.ms(800)),
                "order must not matter: the shorter end is the floor");
        assertEquals(new Duration(500, 3000),
                Duration.between(Duration.between(Duration.ms(500), Duration.ms(900)), Duration.seconds(3)),
                "a range bound contributes its own ends");
    }

    @Test
    void aNegativeOrInvertedDurationIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> Duration.ms(-1));
        assertThrows(IllegalArgumentException.class, () -> Duration.seconds(-0.5));
        assertThrows(IllegalArgumentException.class, () -> new Duration(1500, 800));
    }

    @Test
    void waitingIsANoOpForNullAndZero() {
        long before = System.currentTimeMillis();
        Wait.time(null);
        Wait.time(Duration.ms(0));
        assertTrue(System.currentTimeMillis() - before < 200, "neither should have slept");
    }

    @Test
    void waitingForARangeSleepsWithinIt() {
        long before = System.currentTimeMillis();
        Wait.time(Duration.between(Duration.ms(30), Duration.ms(60)));
        long elapsed = System.currentTimeMillis() - before;
        assertTrue(elapsed >= 25, "slept " + elapsed + "ms, expected at least the floor");
    }
}
