package com.ctrends.salahguardian.model;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * The prayer the user is currently counting down to, paired with the live
 * remaining duration. Produced by the scheduler and consumed by the dashboard.
 *
 * @param prayer    the upcoming prayer and its start time
 * @param remaining time left until it starts; never negative
 * @param tomorrow  {@code true} when the prayer belongs to the next civil day
 *                  (i.e. after Isha, the countdown targets tomorrow's Fajr)
 * @author CTrends Software
 */
public record UpcomingPrayer(PrayerTime prayer, Duration remaining, boolean tomorrow) {

    public UpcomingPrayer {
        Objects.requireNonNull(prayer, "prayer");
        Objects.requireNonNull(remaining, "remaining");
        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }
    }

    /**
     * Builds an instance by measuring the gap between {@code now} and the
     * prayer's start time.
     *
     * @param prayer   the target prayer
     * @param now      the current moment
     * @param tomorrow whether the prayer belongs to the following day
     * @return a countdown snapshot
     */
    public static UpcomingPrayer from(PrayerTime prayer, ZonedDateTime now, boolean tomorrow) {
        return new UpcomingPrayer(prayer, Duration.between(now, prayer.time()), tomorrow);
    }

    /**
     * @return the countdown rendered as {@code H:MM:SS} or {@code MM:SS}
     */
    public String formattedRemaining() {
        long totalSeconds = Math.max(0, remaining.getSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }
}
