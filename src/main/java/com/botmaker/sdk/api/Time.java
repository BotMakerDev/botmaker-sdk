package com.botmaker.sdk.api;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Time and date utilities for BotMaker SDK.
 *
 * <p>Provides comprehensive time/date functionality including current time, date components,
 * UTC variants, timezone handling, and formatting. All methods are static and return values
 * based on the current moment unless otherwise specified.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code Time.now()} - Get current date and time</li>
 *   <li>{@code Time.hour()} - Get current hour (0-23)</li>
 *   <li>{@code Time.nowUtc()} - Get current UTC date and time</li>
 *   <li>{@code Time.format("yyyy-MM-dd HH:mm:ss")} - Format current time</li>
 * </ul>
 */
@ApiId("time")
public final class Time {

    /** Default timezone used when no explicit timezone is specified. */
    private static volatile ZoneId defaultTimeZone = ZoneId.systemDefault();

    private Time() {} // Utility class - prevent instantiation

    // --- Current time ---

    /**
     * Returns the current local date and time.
     *
     * @return the current date and time in the system default timezone
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(defaultTimeZone);
    }

    /**
     * Returns today's date.
     *
     * @return the current date in the system default timezone
     */
    public static LocalDate today() {
        return LocalDate.now(defaultTimeZone);
    }

    /**
     * Returns the current time of day.
     *
     * @return the current time in the system default timezone
     */
    public static LocalTime currentTime() {
        return LocalTime.now(defaultTimeZone);
    }

    // --- Time components ---

    /**
     * Returns the current hour (0-23).
     *
     * @return the current hour in the system default timezone
     */
    public static int hour() {
        return currentTime().getHour();
    }

    /**
     * Returns the current minute (0-59).
     *
     * @return the current minute in the system default timezone
     */
    public static int minute() {
        return currentTime().getMinute();
    }

    /**
     * Returns the current second (0-59).
     *
     * @return the current second in the system default timezone
     */
    public static int second() {
        return currentTime().getSecond();
    }

    /**
     * Returns the current millisecond (0-999).
     *
     * @return the current millisecond in the system default timezone
     */
    public static int millisecond() {
        return currentTime().getNano() / 1_000_000;
    }

    // --- Date components ---

    /**
     * Returns the current day of the month (1-31).
     *
     * @return the current day of the month in the system default timezone
     */
    public static int dayOfMonth() {
        return today().getDayOfMonth();
    }

    /**
     * Returns the current month.
     *
     * <p>Typed rather than a 1–12 {@code int} for the same reason {@link #dayOfWeek()} is: the number is
     * ambiguous by one either way (is 1 January or is it zero-based, as it is in half the languages a bot
     * author has met?) and nothing in an {@code int} says which. {@code Month.JANUARY} says it.
     *
     * @return the current month in the system default timezone
     */
    public static Month month() {
        return today().getMonth();
    }

    /**
     * Returns the current year.
     *
     * @return the current year in the system default timezone
     */
    public static int year() {
        return today().getYear();
    }

    /**
     * Returns the current day of the week.
     *
     * @return the current day of the week in the system default timezone
     */
    public static DayOfWeek dayOfWeek() {
        return today().getDayOfWeek();
    }

    // --- UTC variants ---

    /**
     * Returns the current UTC date and time.
     *
     * @return the current date and time in UTC
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneId.of("UTC"));
    }

    /**
     * Returns the current UTC hour (0-23).
     *
     * @return the current hour in UTC
     */
    public static int hourUtc() {
        return LocalTime.now(ZoneId.of("UTC")).getHour();
    }

    /**
     * Returns the current UTC minute (0-59).
     *
     * @return the current minute in UTC
     */
    public static int minuteUtc() {
        return LocalTime.now(ZoneId.of("UTC")).getMinute();
    }

    /**
     * Returns the current UTC second (0-59).
     *
     * @return the current second in UTC
     */
    public static int secondUtc() {
        return LocalTime.now(ZoneId.of("UTC")).getSecond();
    }

    /**
     * Returns the current UTC millisecond (0-999).
     *
     * @return the current millisecond in UTC
     */
    public static int millisecondUtc() {
        return LocalTime.now(ZoneId.of("UTC")).getNano() / 1_000_000;
    }

    // --- Timezone handling ---

    /**
     * Returns the current date and time in the specified timezone.
     *
     * @param zone the timezone to use
     * @return the current date and time in the specified timezone
     */
    public static LocalDateTime now(ZoneId zone) {
        return LocalDateTime.now(zone);
    }

    /**
     * Returns the current date and time in the timezone with the specified ID.
     *
     * @param zoneId the timezone ID (e.g., "America/New_York", "Europe/London")
     * @return the current date and time in the specified timezone
     * @throws IllegalArgumentException if the zone ID is not recognized
     */
    public static LocalDateTime now(String zoneId) {
        return LocalDateTime.now(ZoneId.of(zoneId));
    }

    /**
     * Returns the default timezone used by this Time API.
     *
     * @return the default timezone
     */
    public static ZoneId getDefaultTimeZone() {
        return defaultTimeZone;
    }

    /**
     * Sets the default timezone used by this Time API.
     *
     * @param zone the timezone to use as default
     */
    public static void setDefaultTimeZone(ZoneId zone) {
        if (zone == null) {
            throw new IllegalArgumentException("Default timezone cannot be null");
        }
        defaultTimeZone = zone;
    }

    /**
     * Sets the default timezone used by this Time API by zone ID.
     *
     * @param zoneId the timezone ID (e.g., "America/New_York")
     */
    public static void setDefaultTimeZone(String zoneId) {
        setDefaultTimeZone(ZoneId.of(zoneId));
    }

    // --- Formatting ---

    /**
     * Formats the current date and time using the specified pattern.
     *
     * @param pattern the formatting pattern (see java.time.format.DateTimeFormatter)
     * @return the formatted current date and time string
     * @throws IllegalArgumentException if the pattern is invalid
     */
    public static String format(String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return now().format(formatter);
    }

    /**
     * Formats the current UTC date and time using the specified pattern.
     *
     * @param pattern the formatting pattern (see java.time.format.DateTimeFormatter)
     * @return the formatted current UTC date and time string
     * @throws IllegalArgumentException if the pattern is invalid
     */
    public static String formatUtc(String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.now(ZoneId.of("UTC")).format(formatter);
    }

    // --- Time arithmetic ---

    /**
     * Returns the number of milliseconds elapsed since the specified start time.
     *
     * @param startTime the start time in milliseconds (typically from System.currentTimeMillis())
     * @return the number of milliseconds elapsed
     */
    public static long elapsedMillis(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Returns the number of seconds elapsed since the specified start time.
     *
     * @param startTime the start time in milliseconds (typically from System.currentTimeMillis())
     * @return the number of seconds elapsed
     */
    public static long elapsedSeconds(long startTime) {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    // --- Time ranges ---

    /**
     * Checks whether the current time of day falls between {@code start} and {@code end} (both inclusive to
     * the minute) — for a window that doesn't start on the hour:
     * {@code Time.isBetween(LocalTime.of(5, 30), LocalTime.of(6, 0))}.
     *
     * <p>Wraps around midnight: a window whose end is before its start is read as spanning midnight, which is
     * what "the reset window is 23:50 to 00:10" means.
     *
     * <p>This replaced an {@code isBetween(int startHour, int endHour)} that took bare hours. Whole-hour
     * windows are still one call — {@code isBetween(LocalTime.of(5, 0), LocalTime.of(7, 0))} — and the pair of
     * bare numbers could say nothing about being hours, which cost both a runtime range check the type makes
     * impossible to fail and, in the Studio, a {@code (method, argIndex)} lookup that had to know where in
     * each overload the hours sat.
     *
     * @param start the start of the window
     * @param end   the end of the window
     * @return true when now is inside the window
     * @throws IllegalArgumentException if either bound is null
     */
    public static boolean isBetween(LocalTime start, LocalTime end) {
        return isWithin(currentTime(), start, end);
    }

    /**
     * The UTC counterpart of {@link #isBetween(LocalTime, LocalTime)} — for a window pinned to a server's
     * reset rather than to the machine's local clock.
     *
     * @param start the start of the window, in UTC
     * @param end   the end of the window, in UTC
     * @return true when now is inside the window
     * @throws IllegalArgumentException if either bound is null
     */
    public static boolean isBetweenUtc(LocalTime start, LocalTime end) {
        return isWithin(LocalTime.now(ZoneId.of("UTC")), start, end);
    }

    /** Both windows, one rule: inclusive to the minute, and a window that reads backwards spans midnight. */
    private static boolean isWithin(LocalTime now, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Both ends of the window are required");
        }
        LocalTime nowTime = now.withSecond(0).withNano(0);
        if (!start.isAfter(end)) {
            return !nowTime.isBefore(start) && !nowTime.isAfter(end);
        }
        return !nowTime.isBefore(start) || !nowTime.isAfter(end);   // wraps midnight
    }

    /**
     * Checks whether today is one of {@code days} — for a task that only runs on certain weekdays
     * ({@code Time.isDay(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)}).
     *
     * @param days the days to test against; none given ⇒ false
     * @return true when today is one of them
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
     * Checks whether this month is one of {@code months} — for seasonal content
     * ({@code Time.isMonth(Month.DECEMBER)}).
     *
     * @param months the months to test against; none given ⇒ false
     * @return true when this month is one of them
     */
    public static boolean isMonth(Month... months) {
        if (months == null) return false;
        Month thisMonth = month();
        for (Month candidate : months) {
            if (thisMonth == candidate) return true;
        }
        return false;
    }

    // --- Current timestamp ---

    /**
     * Returns the current system time in milliseconds since epoch.
     * Useful for timing measurements.
     *
     * @return current time in milliseconds since epoch
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Returns the current system time in nanoseconds.
     * Useful for precise timing measurements.
     *
     * @return current time in nanoseconds since some arbitrary origin
     */
    public static long nanoTime() {
        return System.nanoTime();
    }
}
