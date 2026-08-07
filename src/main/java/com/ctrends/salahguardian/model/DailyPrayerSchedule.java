package com.ctrends.salahguardian.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The complete set of prayer times for one calendar day at one location.
 *
 * <p>Instances are immutable and safe to publish across the scheduler thread
 * and the JavaFX application thread.</p>
 *
 * @author CTrends Software
 */
public final class DailyPrayerSchedule {

    private final LocalDate date;
    private final GeoLocation location;
    private final Map<PrayerName, PrayerTime> times;
    private final List<PrayerTime> ordered;
    private final boolean approximated;

    /**
     * @param date     the civil date these times belong to
     * @param location the position the times were computed for
     * @param times    every prayer of the day; must contain all six entries
     */
    public DailyPrayerSchedule(LocalDate date, GeoLocation location, Map<PrayerName, PrayerTime> times) {
        this(date, location, times, false);
    }

    /**
     * @param date         the civil date these times belong to
     * @param location     the position the times were computed for
     * @param times        every prayer of the day
     * @param approximated {@code true} when the times were derived through the
     *                     nearest-latitude fallback because the true position
     *                     has no astronomical twilight on this date
     */
    public DailyPrayerSchedule(LocalDate date, GeoLocation location,
                               Map<PrayerName, PrayerTime> times, boolean approximated) {
        this.approximated = approximated;
        this.date = Objects.requireNonNull(date, "date");
        this.location = Objects.requireNonNull(location, "location");
        Objects.requireNonNull(times, "times");
        EnumMap<PrayerName, PrayerTime> copy = new EnumMap<>(PrayerName.class);
        copy.putAll(times);
        this.times = Collections.unmodifiableMap(copy);
        this.ordered = copy.values().stream().sorted().toList();
    }

    /**
     * @return the civil date of this schedule
     */
    public LocalDate date() {
        return date;
    }

    /**
     * @return the position used for the calculation
     */
    public GeoLocation location() {
        return location;
    }

    /**
     * @return all six entries sorted chronologically, unmodifiable
     */
    public List<PrayerTime> allTimes() {
        return ordered;
    }

    /**
     * @return only the five obligatory prayers, sorted chronologically
     */
    public List<PrayerTime> obligatoryTimes() {
        return ordered.stream().filter(t -> t.name().isObligatory()).toList();
    }

    /**
     * @param name the prayer to look up
     * @return the matching entry when present
     */
    public Optional<PrayerTime> timeOf(PrayerName name) {
        return Optional.ofNullable(times.get(name));
    }

    /**
     * Finds the first prayer of this day that starts strictly after the given
     * moment.
     *
     * @param reference        the moment to search from
     * @param includeSunrise   whether sunrise counts as a candidate
     * @return the next entry, or empty when the day is already over
     */
    public Optional<PrayerTime> nextAfter(ZonedDateTime reference, boolean includeSunrise) {
        return ordered.stream()
                .filter(t -> includeSunrise || t.name().isObligatory())
                .filter(t -> t.isAfter(reference))
                .findFirst();
    }

    /**
     * Finds the prayer whose window currently contains the given moment.
     *
     * @param reference the moment to test
     * @return the active prayer, or empty before Fajr of this day
     */
    public Optional<PrayerTime> currentAt(ZonedDateTime reference) {
        PrayerTime current = null;
        for (PrayerTime candidate : ordered) {
            if (!candidate.time().isAfter(reference)) {
                current = candidate;
            } else {
                break;
            }
        }
        return Optional.ofNullable(current);
    }

    /**
     * Indicates that the times could not be derived from the true position and
     * were computed at the nearest usable latitude instead.
     *
     * <p>This happens inside the polar circles, where for part of the year the
     * sun never crosses the twilight angles - or never sets at all - so Fajr,
     * Maghrib and Isha have no astronomical definition. The dashboard surfaces
     * this so the user knows the times are a convention rather than an
     * observation.</p>
     *
     * @return {@code true} when the nearest-latitude fallback was used
     */
    public boolean isApproximated() {
        return approximated;
    }

    /**
     * @return {@code true} when every one of the five obligatory prayers has a
     *         time
     */
    public boolean isComplete() {
        return times.keySet().stream().filter(PrayerName::isObligatory).count() == 5;
    }

    /**
     * @return {@code true} when this schedule falls on a Friday, in which case
     *         Dhuhr is rendered and announced as Jumu'ah
     */
    public boolean isFriday() {
        return date.getDayOfWeek() == DayOfWeek.FRIDAY;
    }

    @Override
    public String toString() {
        return "DailyPrayerSchedule{" + date + " @ " + location.displayLabel() + " " + ordered + "}";
    }
}
