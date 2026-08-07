package com.ctrends.salahguardian.model;

import com.ctrends.salahguardian.i18n.Messages;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A single prayer bound to a concrete moment in the user's local time zone.
 *
 * @param name the prayer this entry represents
 * @param time the zoned instant at which the prayer window opens
 * @author CTrends Software
 */
public record PrayerTime(PrayerName name, ZonedDateTime time) implements Comparable<PrayerTime> {

    private static final String PATTERN_24H = "HH:mm";
    private static final String PATTERN_12H = "hh:mm a";

    public PrayerTime {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(time, "time");
    }

    /**
     * @param use24Hour {@code true} for {@code 18:42}, {@code false} for {@code 06:42 PM}
     * @return the formatted clock time
     */
    public String formatted(boolean use24Hour) {
        // Built per call rather than cached in a static: the formatter carries
        // both the locale and the numeral set, and both change when the user
        // switches language.
        return time.format(Messages.formatter(use24Hour ? PATTERN_24H : PATTERN_12H));
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
