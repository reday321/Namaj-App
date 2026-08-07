package com.ctrends.salahguardian.model;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * A single prayer bound to a concrete moment in the user's local time zone.
 *
 * @param name the prayer this entry represents
 * @param time the zoned instant at which the prayer window opens
 * @author CTrends Software
 */
public record PrayerTime(PrayerName name, ZonedDateTime time) implements Comparable<PrayerTime> {

    private static final DateTimeFormatter TIME_24H = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_12H = DateTimeFormatter.ofPattern("hh:mm a");

    public PrayerTime {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(time, "time");
    }

    /**
     * @param use24Hour {@code true} for {@code 18:42}, {@code false} for {@code 06:42 PM}
     * @return the formatted clock time
     */
    public String formatted(boolean use24Hour) {
        return time.format(use24Hour ? TIME_24H : TIME_12H);
    }

    /**
     * @param reference the moment to compare against
     * @return {@code true} when this prayer's time is strictly after {@code reference}
     */
    public boolean isAfter(ZonedDateTime reference) {
        return time.isAfter(reference);
    }

    /**
     * @param reference the moment to measure from
     * @return the (possibly negative) duration until this prayer starts
     */
    public Duration durationFrom(ZonedDateTime reference) {
        return Duration.between(reference, time);
    }

    @Override
    public int compareTo(PrayerTime other) {
        return time.compareTo(other.time);
    }
}
