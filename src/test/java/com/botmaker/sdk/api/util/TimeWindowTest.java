package com.botmaker.sdk.api.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calendar predicates. All read the real clock, so the windows here are built <em>relative to now</em>
 * rather than pinned to a literal time — a test that hard-codes 05:30 passes only between 05:30 and 05:31.
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
        assertThrows(IllegalArgumentException.class, () -> Time.isBetweenUtc(LocalTime.NOON, null));
    }

    @Test
    void theUtcWindowIsTheSameRuleOnTheOtherClock() {
        // The whole point of the UTC variant is that it does not follow the machine's zone — a window around
        // local now must not match when the two clocks are hours apart.
        LocalTime utcNow = LocalTime.now(ZoneId.of("UTC"));
        assertTrue(Time.isBetweenUtc(utcNow.minusMinutes(1), utcNow.plusMinutes(1)));
        assertFalse(Time.isBetweenUtc(utcNow.plusMinutes(10), utcNow.plusMinutes(20)));
        assertTrue(Time.isBetweenUtc(utcNow.minusMinutes(1), utcNow.minusMinutes(2)), "wraps midnight too");
    }

    @Test
    void isDayMatchesTodayOnly() {
        DayOfWeek today = Time.dayOfWeek();
        assertTrue(Time.isDay(today));
        assertTrue(Time.isDay(today.plus(1), today), "any of the listed days counts");
        assertFalse(Time.isDay(today.plus(1), today.plus(2)));
        assertFalse(Time.isDay(), "no days listed is no match, not every day");
    }

    @Test
    void isMonthMatchesThisMonthOnly() {
        Month thisMonth = Time.month();
        assertTrue(Time.isMonth(thisMonth));
        assertTrue(Time.isMonth(thisMonth.plus(1), thisMonth), "any of the listed months counts");
        assertFalse(Time.isMonth(thisMonth.plus(1), thisMonth.plus(2)));
        assertFalse(Time.isMonth(), "no months listed is no match, not every month");
    }

    @Test
    void theMonthIsTheOneTheDateNames() {
        // The off-by-one this type exists to prevent: JANUARY is 1, not 0, and a bot that read the old int as
        // zero-based was a month out all year without ever failing to compile.
        assertEquals(Time.today().getMonth(), Time.month());
        assertEquals(Time.today().getMonthValue(), Time.month().getValue());
    }
}
