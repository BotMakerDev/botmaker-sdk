package com.botmaker.sdk.api;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
     * Returns the current month (1-12).
     *
     * @return the current month in the system default timezone
     */
    public static int month() {
        return today().getMonthValue();
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
     * Checks if the current time is between the specified hours (inclusive).
     * Useful for creating time-based conditions.
     *
     * @param startHour the start hour (0-23)
     * @param endHour the end hour (0-23)
     * @return true if current hour is between startHour and endHour (inclusive)
     * @throws IllegalArgumentException if hours are not in range 0-23
     */
    public static boolean isBetween(int startHour, int endHour) {
        if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException("Hours must be between 0 and 23");
        }

        int currentHour = hour();

        if (startHour <= endHour) {
            // Normal case: startHour <= currentHour <= endHour
            return currentHour >= startHour && currentHour <= endHour;
        } else {
            // Wrap-around case: currentHour >= startHour OR currentHour <= endHour
            return currentHour >= startHour || currentHour <= endHour;
        }
    }

    /**
     * Checks if the current UTC time is between the specified hours (inclusive).
     *
     * @param startHour the start hour (0-23)
     * @param endHour the end hour (0-23)
     * @return true if current UTC hour is between startHour and endHour (inclusive)
     * @throws IllegalArgumentException if hours are not in range 0-23
     */
    public static boolean isBetweenUtc(int startHour, int endHour) {
        if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException("Hours must be between 0 and 23");
        }

        int currentHour = hourUtc();

        if (startHour <= endHour) {
            // Normal case: startHour <= currentHour <= endHour
            return currentHour >= startHour && currentHour <= endHour;
        } else {
            // Wrap-around case: currentHour >= startHour OR currentHour <= endHour
            return currentHour >= startHour || currentHour <= endHour;
        }
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