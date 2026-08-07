package com.ctrends.salahguardian.prayer;

import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.data.DateComponents;
import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.PrayerName;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.utils.TimeUtils;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

/**
 * {@link PrayerTimeCalculator} backed by the
 * <a href="https://github.com/batoulapps/adhan-java">adhan-java</a> library.
 *
 * <p>The library returns {@link Date} instants computed from the astronomical
 * position of the sun, so they are absolute moments independent of the JVM's
 * default zone. This class converts them into the caller's zone and applies the
 * user's per-prayer minute offsets.</p>
 *
 * <p>Stateless and therefore thread safe.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class AdhanPrayerTimeCalculator implements PrayerTimeCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(AdhanPrayerTimeCalculator.class);

    /** Order matching {@code CalculationSettings.manualAdjustments()}. */
    private static final PrayerName[] ADJUSTMENT_ORDER = {
            PrayerName.FAJR, PrayerName.SUNRISE, PrayerName.DHUHR,
            PrayerName.ASR, PrayerName.MAGHRIB, PrayerName.ISHA
    };

    /** Latitude beyond which the nearest-latitude fallback may be needed. */
    private static final double POLAR_THRESHOLD = 48.0;

    /**
     * Latitude the fallback calculates at. 45 degrees is the value used by the
     * widely followed "nearest latitude" ({@code aqrab al-bilad}) convention
     * for regions where the twilight signs do not occur.
     */
    private static final double NEAREST_USABLE_LATITUDE = 45.0;

    @Override
    public DailyPrayerSchedule calculate(GeoLocation location, LocalDate date, ZoneId zone,
                                         CalculationSettings settings) {
        DailyPrayerSchedule direct = computeAt(location, location.latitude(), date, zone,
                settings, false);
        if (direct.isComplete() || Math.abs(location.latitude()) <= POLAR_THRESHOLD) {
            return direct;
        }

        // Inside the polar circles there are stretches of the year with no
        // astronomical night at all, and adhan correctly declines to invent
        // one. Rather than leaving the user with an empty timetable, fall back
        // to the nearest latitude at which the twilight signs do occur, and
        // mark the result as approximate.
        double fallbackLatitude = Math.copySign(NEAREST_USABLE_LATITUDE, location.latitude());
        DailyPrayerSchedule approximated = computeAt(location, fallbackLatitude, date, zone,
                settings, true);
        LOG.info("No astronomical twilight at {} on {} - approximating from latitude {} "
                        + "(nearest-latitude convention), {} entries produced",
                location.coordinateLabel(), date, fallbackLatitude, approximated.allTimes().size());
        return approximated;
    }

    /**
     * Runs the engine at an explicit latitude.
     *
     * @param location       the position recorded on the resulting schedule
     * @param latitude       the latitude actually fed to the engine
     * @param date           the civil date
     * @param zone           the zone the times are expressed in
     * @param settings       the calculation conventions
     * @param approximated   flag stamped onto the resulting schedule
     * @return the computed schedule
     */
    private DailyPrayerSchedule computeAt(GeoLocation location, double latitude, LocalDate date,
                                          ZoneId zone, CalculationSettings settings,
                                          boolean approximated) {
        Coordinates coordinates = new Coordinates(latitude, location.longitude());
        DateComponents dateComponents =
                new DateComponents(date.getYear(), date.getMonthValue(), date.getDayOfMonth());

        CalculationParameters parameters = settings.method()
                .toParameters(settings.customFajrAngle(), settings.customIshaAngle());
        parameters.madhab = settings.madhab().toAdhanMadhab();
        parameters.highLatitudeRule = settings.highLatitudeRule().toAdhanRule();

        PrayerTimes computed = new PrayerTimes(coordinates, dateComponents, parameters);

        Map<PrayerName, PrayerTime> times = new EnumMap<>(PrayerName.class);
        int[] adjustments = settings.manualAdjustments();
        Date[] raw = {
                computed.fajr, computed.sunrise, computed.dhuhr,
                computed.asr, computed.maghrib, computed.isha
        };

        for (int i = 0; i < ADJUSTMENT_ORDER.length; i++) {
            PrayerName prayer = ADJUSTMENT_ORDER[i];
            ZonedDateTime moment = TimeUtils.toZoned(raw[i], zone);
            if (moment == null) {
                // At extreme latitudes adhan can legitimately fail to produce a
                // twilight based time. Skipping the entry is better than
                // aborting the whole day; the caller decides whether to retry
                // through the nearest-latitude fallback.
                LOG.debug("No {} time at latitude {} on {} - entry omitted",
                        prayer.displayName(), latitude, date);
                continue;
            }
            // adhan-java rounds to the nearest minute but does not clear the
            // millisecond field, so the current wall clock leaks into the
            // result. Truncating makes the times deterministic and lets the
            // scheduler fire exactly on the minute.
            ZonedDateTime exact = moment.truncatedTo(ChronoUnit.MINUTES)
                    .plusMinutes(adjustments[i]);
            times.put(prayer, new PrayerTime(prayer, exact));
        }

        DailyPrayerSchedule schedule =
                new DailyPrayerSchedule(date, location, times, approximated);
        LOG.debug("Computed {} entries for {} using {}", times.size(), date, settings.method());
        return schedule;
    }
}
