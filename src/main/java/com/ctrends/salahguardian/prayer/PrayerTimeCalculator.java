package com.ctrends.salahguardian.prayer;

import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.GeoLocation;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Computes the prayer timetable for a given day, place and set of conventions.
 *
 * <p>Declaring this as an interface keeps the rest of the application unaware
 * of adhan-java: swapping the engine, or stubbing it in a test, needs no change
 * anywhere else.</p>
 *
 * @author CTrends Software
 */
public interface PrayerTimeCalculator {

    /**
     * Calculates one day's prayer times.
     *
     * @param location the position to calculate for
     * @param date     the civil date, interpreted in {@code zone}
     * @param zone     the time zone the resulting times are expressed in
     * @param settings the calculation conventions to apply
     * @return the complete schedule for that day
     */
    DailyPrayerSchedule calculate(GeoLocation location, LocalDate date, ZoneId zone,
                                  CalculationSettings settings);
}
