package com.botmaker.sdk.api;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two daily-reset predicates. Both read the real clock, so the windows here are built <em>relative to
 * now</em> rather than pinned to a literal time — a test that hard-codes 05:30 passes only between 05:30 and
 * 05:31.
 *
 * <p>The case worth the test is the wrap: a window whose end is before its start spans midnight, and reading
 * it as "start ≤ now ≤ end" makes a 23:50–00:10 reset window match nothing, all day, silently.
 */
class TimeWindowTest {

    @Test
    void aWindowAroundNowMatchesAndOneAheadOfNowDoesNot() {
        LocalTime now = Time.currentTime();
        assertTrue(Time.isBetween(now.minusMinutes(1), now.plusMinutes(1)));
        assertFalse(Time.isBetween(now.plusMinutes(10), now.plusMinutes(20)));
    }

    @Test
    void theWrapAroundWindowIsEverythingOutsideTheHole() {
        LocalTime now = Time.currentTime();
        // start after end: 'from a minute from now, round to a minute ago' — every moment but the one we are in.
        assertFalse(Time.isBetween(now.plusMinutes(2), now.minusMinutes(2)),
                "now sits in the hole the wrapped window leaves");
        // ...and the complementary wrapped window, whose hole is elsewhere, does contain now.
        assertTrue(Time.isBetween(now.minusMinutes(1), now.minusMinutes(2)));
    }

    @Test
    void bothEndsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> Time.isBetween(LocalTime.NOON, null));
        assertThrows(IllegalArgumentException.class, () -> Time.isBetween(null, LocalTime.NOON));
    }

    @Test
    void isDayMatchesTodayOnly() {
        DayOfWeek today = Time.dayOfWeek();
        assertTrue(Time.isDay(today));
        assertTrue(Time.isDay(today.plus(1), today), "any of the listed days counts");
        assertFalse(Time.isDay(today.plus(1), today.plus(2)));
        assertFalse(Time.isDay(), "no days listed is no match, not every day");
    }
}
