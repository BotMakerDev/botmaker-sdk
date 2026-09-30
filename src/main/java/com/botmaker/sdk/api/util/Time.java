package com.botmaker.sdk.api.util;

import com.botmaker.plugin.api.palette.Palette;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;

/**
 * The clock, as a bot block asks it: what time and day it is, and whether now falls in a window.
 *
 * <pre>{@code
 * if (Time.isBetween(LocalTime.of(5, 30), LocalTime.of(6, 0))) collectDailyReward();
 * if (Time.isDay(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) runWeekendEvent();
 * }</pre>
 *
 * <p>Everything reads the machine's own time zone. <b>Ten members since 2026-09-30</b>, down from thirty-one:
 * the UTC twins, the zone setters, the date parts ({@code second}, {@code year}, …), the elapsed-time helpers and
 * the {@code System} passthroughs were deleted, not deprecated — nothing used them, and each was either a second
 * spelling of a member kept here or a question a {@code java.time} value already answers
 * ({@code Time.today().getYear()}).
 */
@Palette(category = "util", categoryLabel = "Utilities")
public final class Time {

    private Time() {}

    // --- Now ---

    /** The current date and time. */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /** Today's date. */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /** The current time of day. */
    public static LocalTime currentTime() {
        return LocalTime.now();
    }

    /** The current hour, 0–23. */
    public static int hour() {
        return currentTime().getHour();
    }

    /** The current minute, 0–59. */
    public static int minute() {
        return currentTime().getMinute();
    }

    /** Today's day of the week. */
    public static DayOfWeek dayOfWeek() {
        return today().getDayOfWeek();
    }

    // --- Windows ---

    /**
     * Whether the current time of day falls between {@code start} and {@code end} (both inclusive to the
     * minute) — {@code Time.isBetween(LocalTime.of(5, 30), LocalTime.of(6, 0))}.
     *
     * <p>Wraps around midnight: a window whose end is before its start is read as spanning midnight, which is
     * what "the reset window is 23:50 to 00:10" means.
     *
     * @throws IllegalArgumentException if either bound is null
     */
    public static boolean isBetween(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Both ends of the window are required");
        }
        LocalTime now = currentTime().withSecond(0).withNano(0);
        if (!start.isAfter(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        return !now.isBefore(start) || !now.isAfter(end);   // wraps midnight
    }

    /**
     * Whether today is one of {@code days} — {@code Time.isDay(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)}.
     *
     * @param days the days to test against; none given ⇒ false
     */
    public static boolean isDay(DayOfWeek... days) {
        if (days == null) return false;
        DayOfWeek todayIs = dayOfWeek();
        for (DayOfWeek day : days) {
            if (todayIs == day) return true;
        }
        return false;
    }

    /**
     * Whether this month is one of {@code months} — for seasonal content ({@code Time.isMonth(Month.DECEMBER)}).
     *
     * @param months the months to test against; none given ⇒ false
     */
    public static boolean isMonth(Month... months) {
        if (months == null) return false;
        Month thisMonth = today().getMonth();
        for (Month candidate : months) {
            if (thisMonth == candidate) return true;
        }
        return false;
    }

    // --- Text ---

    /**
     * The current date and time in {@code pattern} ({@link DateTimeFormatter} letters) —
     * {@code Time.format("yyyy-MM-dd HH:mm")}.
     *
     * @throws IllegalArgumentException if the pattern is invalid
     */
    public static String format(String pattern) {
        return now().format(DateTimeFormatter.ofPattern(pattern));
    }
}
