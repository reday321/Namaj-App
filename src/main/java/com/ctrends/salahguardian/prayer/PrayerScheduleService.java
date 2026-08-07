package com.ctrends.salahguardian.prayer;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.location.LocationService;
import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.model.UpcomingPrayer;
import com.ctrends.salahguardian.utils.TimeUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * The application's single source of truth for "what are today's times and what
 * comes next".
 *
 * <p>Holds a small cache of today's and tomorrow's schedules, keyed by date,
 * location and calculation settings. The cache is invalidated automatically
 * when any of those three change, which covers midnight rollover, the user
 * travelling, and the user switching calculation method - without any explicit
 * invalidation call from the callers.</p>
 *
 * <p>Thread safe: the scheduler thread and the JavaFX thread both read through
 * these methods.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class PrayerScheduleService {

    private static final Logger LOG = LoggerFactory.getLogger(PrayerScheduleService.class);

    private final PrayerTimeCalculator calculator;
    private final ConfigService configService;
    private final LocationService locationService;
    private final Clock clock;

    private final Object cacheLock = new Object();
    private CacheKey cachedKey;
    private DailyPrayerSchedule cachedToday;
    private DailyPrayerSchedule cachedTomorrow;

    /**
     * @param calculator      the engine that does the arithmetic
     * @param configService   the user's calculation preferences
     * @param locationService the position to calculate for
     */
    @Inject
    public PrayerScheduleService(PrayerTimeCalculator calculator,
                                 ConfigService configService,
                                 LocationService locationService) {
        this(calculator, configService, locationService, Clock.systemDefaultZone());
    }

    /**
     * Test friendly constructor that accepts a fixed clock.
     *
     * @param calculator      the engine that does the arithmetic
     * @param configService   the user's calculation preferences
     * @param locationService the position to calculate for
     * @param clock           the time source
     */
    public PrayerScheduleService(PrayerTimeCalculator calculator,
                                 ConfigService configService,
                                 LocationService locationService,
                                 Clock clock) {
        this.calculator = calculator;
        this.configService = configService;
        this.locationService = locationService;
        this.clock = clock;
    }

    /**
     * @return the zone prayer times are expressed in: the one recorded with the
     *         location when available, otherwise the system zone
     */
    public ZoneId zone() {
        return TimeUtils.resolveZone(configService.get().getTimeZoneId());
    }

    /**
     * @return the current moment in the user's zone
     */
    public ZonedDateTime now() {
        return ZonedDateTime.now(clock.withZone(zone()));
    }

    /**
     * @return today's complete timetable
     */
    public DailyPrayerSchedule today() {
        return schedules().today();
    }

    /**
     * Tomorrow's timetable, computed eagerly alongside today's so that the
     * countdown after Isha and the first reminder after midnight are always
     * ready without a recalculation.
     *
     * @return tomorrow's complete timetable
     */
    public DailyPrayerSchedule tomorrow() {
        return schedules().tomorrow();
    }

    /**
     * Computes an arbitrary day's timetable without touching the cache. Used by
     * the dashboard's "other day" lookups.
     *
     * @param date the day to compute
     * @return that day's timetable
     */
    public DailyPrayerSchedule scheduleFor(LocalDate date) {
        AppConfig config = configService.get();
        GeoLocation location = locationService.currentLocation();
        return calculator.calculate(location, date, zone(), CalculationSettings.from(config));
    }

    /**
     * Finds the next prayer, rolling into tomorrow's Fajr once Isha has passed.
     *
     * @return the upcoming prayer with its live countdown; empty only if the
     *         calculator failed to produce any entry for two consecutive days
     */
    public Optional<UpcomingPrayer> nextPrayer() {
        ZonedDateTime reference = now();
        Schedules schedules = schedules();

        Optional<PrayerTime> todayNext = schedules.today().nextAfter(reference, false);
        if (todayNext.isPresent()) {
            return Optional.of(UpcomingPrayer.from(todayNext.get(), reference, false));
        }
        Optional<PrayerTime> tomorrowFirst = schedules.tomorrow().nextAfter(reference, false);
        return tomorrowFirst.map(prayer -> UpcomingPrayer.from(prayer, reference, true));
    }

    /**
     * @return the prayer whose window is currently open, or empty before Fajr
     */
    public Optional<PrayerTime> currentPrayer() {
        return today().currentAt(now());
    }

    /**
     * Drops the cached schedules so the next read recomputes them. Called after
     * a settings change or a location refresh.
     */
    public void invalidate() {
        synchronized (cacheLock) {
            cachedKey = null;
            cachedToday = null;
            cachedTomorrow = null;
        }
        LOG.debug("Prayer schedule cache invalidated");
    }

    private Schedules schedules() {
        AppConfig config = configService.get();
        GeoLocation location = locationService.currentLocation();
        ZoneId zone = zone();
        LocalDate date = LocalDate.now(clock.withZone(zone));
        CalculationSettings settings = CalculationSettings.from(config);
        CacheKey key = new CacheKey(date, location.latitude(), location.longitude(),
                zone.getId(), settings);

        synchronized (cacheLock) {
            if (key.equals(cachedKey) && cachedToday != null && cachedTomorrow != null) {
                return new Schedules(cachedToday, cachedTomorrow);
            }
            cachedToday = calculator.calculate(location, date, zone, settings);
            cachedTomorrow = calculator.calculate(location, date.plusDays(1), zone, settings);
            cachedKey = key;
            LOG.info("Recalculated prayer times for {} and {} at {}",
                    date, date.plusDays(1), location.displayLabel());
            return new Schedules(cachedToday, cachedTomorrow);
        }
    }

    /**
     * Identity of a cached pair of schedules.
     */
    private record CacheKey(LocalDate date, double latitude, double longitude,
                            String zoneId, CalculationSettings settings) {
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CacheKey that)) {
                return false;
            }
            return date.equals(that.date)
                    && Double.compare(latitude, that.latitude) == 0
                    && Double.compare(longitude, that.longitude) == 0
                    && Objects.equals(zoneId, that.zoneId)
                    && settings.equals(that.settings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(date, latitude, longitude, zoneId, settings);
        }
    }

    /**
     * A consistent today/tomorrow pair returned by the cache.
     */
    private record Schedules(DailyPrayerSchedule today, DailyPrayerSchedule tomorrow) {
    }
}
