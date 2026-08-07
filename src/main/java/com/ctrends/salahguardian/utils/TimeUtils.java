package com.ctrends.salahguardian.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.Locale;

/**
 * Date, time and duration helpers shared by the scheduler, the view models and
 * the notification texts.
 *
 * @author CTrends Software
 */
public final class TimeUtils {

    /** Long Gregorian format, e.g. {@code Friday, 7 August 2026}. */
    public static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /** Wall clock with seconds, e.g. {@code 19:04:22}. */
    public static final DateTimeFormatter CLOCK_24H =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);

    /** Wall clock with seconds in 12 hour form, e.g. {@code 07:04:22 PM}. */
    public static final DateTimeFormatter CLOCK_12H =
            DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

    /** Islamic month names in the order used by {@link HijrahChronology}. */
    private static final String[] HIJRI_MONTHS = {
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    };

    /** Index of Ramadan in the Hijri calendar. */
    public static final int RAMADAN_MONTH = 9;

    private TimeUtils() {
        // utility class
    }

    /**
     * Converts a legacy {@link Date} - which is what adhan-java returns - into a
     * zoned date time.
     *
     * @param date the instant to convert, may be {@code null}
     * @param zone the target zone
     * @return the zoned equivalent, or {@code null} when {@code date} is null
     */
    public static ZonedDateTime toZoned(Date date, ZoneId zone) {
        return date == null ? null : ZonedDateTime.ofInstant(date.toInstant(), zone);
    }

    /**
     * Converts an instant into a zoned date time.
     *
     * @param instant the moment to convert
     * @param zone    the target zone
     * @return the zoned equivalent
     */
    public static ZonedDateTime toZoned(Instant instant, ZoneId zone) {
        return ZonedDateTime.ofInstant(instant, zone);
    }

    /**
     * Formats a duration for notification bodies, e.g. {@code "5 minutes"},
     * {@code "1 hour 20 minutes"} or {@code "less than a minute"}.
     *
     * @param duration the span to describe
     * @return a human readable, singular/plural aware description
     */
    public static String humanise(Duration duration) {
        long totalMinutes = Math.max(0, duration.toMinutes());
        if (totalMinutes == 0) {
            return "less than a minute";
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        StringBuilder text = new StringBuilder();
        if (hours > 0) {
            text.append(hours).append(hours == 1 ? " hour" : " hours");
        }
        if (minutes > 0) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }
        return text.toString();
    }

    /**
     * Formats a duration as a countdown clock: {@code H:MM:SS} when at least an
     * hour remains, otherwise {@code MM:SS}.
     *
     * @param duration the span to render
     * @return the formatted countdown
     */
    public static String countdown(Duration duration) {
        long totalSeconds = Math.max(0, duration.getSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Renders the Hijri equivalent of a Gregorian date, e.g.
     * {@code "24 Safar 1448 AH"}.
     *
     * <p>Uses the {@code Hijrah-umalqura} variant bundled with the JDK. The
     * observed lunar calendar may differ by a day depending on local moon
     * sighting; the dashboard states this in its tooltip.</p>
     *
     * @param date the Gregorian date to convert
     * @return the formatted Hijri date, or an empty string if conversion fails
     */
    public static String toHijriString(LocalDate date) {
        try {
            HijrahDate hijri = HijrahChronology.INSTANCE.date(date);
            int day = hijri.get(ChronoField.DAY_OF_MONTH);
            int month = hijri.get(ChronoField.MONTH_OF_YEAR);
            int year = hijri.get(ChronoField.YEAR);
            return day + " " + hijriMonthName(month) + " " + year + " AH";
        } catch (RuntimeException e) {
            // Dates outside the supported Umm al-Qura range (before 1300 AH).
            return "";
        }
    }

    /**
     * @param date the Gregorian date to test
     * @return {@code true} when the date falls inside Ramadan
     */
    public static boolean isRamadan(LocalDate date) {
        try {
            return HijrahChronology.INSTANCE.date(date).get(ChronoField.MONTH_OF_YEAR) == RAMADAN_MONTH;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @param month 1 based Hijri month number
     * @return the month's name, or the raw number when out of range
     */
    public static String hijriMonthName(int month) {
        return month >= 1 && month <= 12 ? HIJRI_MONTHS[month - 1] : String.valueOf(month);
    }

    /**
     * Resolves the zone to use for prayer calculations: the zone recorded with
     * the location when it is valid, otherwise the system default.
     *
     * @param timeZoneId an IANA zone id, may be {@code null} or blank
     * @return a usable zone, never {@code null}
     */
    public static ZoneId resolveZone(String timeZoneId) {
        if (timeZoneId != null && !timeZoneId.isBlank()) {
            try {
                return ZoneId.of(timeZoneId.trim());
            } catch (RuntimeException ignored) {
                // fall through to the system zone
            }
        }
        return ZoneId.systemDefault();
    }
}
