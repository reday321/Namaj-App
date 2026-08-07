package com.ctrends.salahguardian.prayer;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.location.LocationService;
import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.LocationSource;
import com.ctrends.salahguardian.model.PrayerName;
import com.ctrends.salahguardian.model.UpcomingPrayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PrayerScheduleService}, driven by a fixed clock so the
 * day-rollover and caching behaviour can be asserted deterministically.
 */
class PrayerScheduleServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final GeoLocation ISTANBUL = new GeoLocation(41.0082, 28.9784,
            "Istanbul", "Turkey", "Europe/Istanbul", LocationSource.MANUAL, Instant.EPOCH);

    /** Minimal in-memory configuration. */
    private static final class InMemoryConfig implements ConfigService {
        private final AppConfig config = new AppConfig();

        InMemoryConfig() {
            config.applyLocation(ISTANBUL);
            config.setTimeZoneId(ZONE.getId());
        }

        @Override public AppConfig get() { return config; }
        @Override public void update(Consumer<AppConfig> mutation) { mutation.accept(config); }
        @Override public void save() { }
        @Override public void reload() { }
        @Override public void addChangeListener(Consumer<AppConfig> listener) { }
        @Override public void removeChangeListener(Consumer<AppConfig> listener) { }
    }

    /** Counts how often the engine was actually invoked. */
    private static final class CountingCalculator implements PrayerTimeCalculator {
        private final PrayerTimeCalculator delegate = new AdhanPrayerTimeCalculator();
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public DailyPrayerSchedule calculate(GeoLocation location, LocalDate date, ZoneId zone,
                                             CalculationSettings settings) {
            invocations.incrementAndGet();
            return delegate.calculate(location, date, zone, settings);
        }
    }

    private InMemoryConfig config;
    private CountingCalculator calculator;
    private LocationService locationService;

    @BeforeEach
    void setUp() {
        config = new InMemoryConfig();
        calculator = new CountingCalculator();
        locationService = new LocationService(config, List.of(new com.ctrends.salahguardian
                .location.LocationProvider() {
            @Override
            public LocationSource source() {
                return LocationSource.MANUAL;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Optional<GeoLocation> resolve() {
                return Optional.of(ISTANBUL);
            }
        }));
    }

    private PrayerScheduleService serviceAt(LocalDateTime moment) {
        Clock clock = Clock.fixed(moment.atZone(ZONE).toInstant(), ZONE);
        return new PrayerScheduleService(calculator, config, locationService, clock);
    }

    @Test
    @DisplayName("uses the time zone recorded with the location")
    void usesConfiguredZone() {
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 12, 0));
        assertEquals(ZONE, service.zone());
        assertEquals(LocalDate.of(2026, 8, 7), service.today().date());
    }

    @Test
    @DisplayName("computes today and tomorrow together")
    void computesTodayAndTomorrow() {
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 12, 0));
        assertEquals(LocalDate.of(2026, 8, 7), service.today().date());
        assertEquals(LocalDate.of(2026, 8, 8), service.tomorrow().date());
    }

    @Test
    @DisplayName("caches the pair instead of recalculating on every read")
    void cachesSchedules() {
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 12, 0));
        service.today();
        service.tomorrow();
        service.today();
        service.nextPrayer();

        assertEquals(2, calculator.invocations.get(),
                "one calculation for today and one for tomorrow");
    }

    @Test
    @DisplayName("recalculates after the cache is invalidated")
    void recalculatesAfterInvalidate() {
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 12, 0));
        service.today();
        int before = calculator.invocations.get();

        service.invalidate();
        service.today();

        assertTrue(calculator.invocations.get() > before);
    }

    @Test
    @DisplayName("recalculates when the calculation settings change")
    void recalculatesWhenSettingsChange() {
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 12, 0));
        var shafiAsr = service.today().timeOf(PrayerName.ASR).orElseThrow().time();

        config.update(c -> c.setMadhab("HANAFI"));
        var hanafiAsr = service.today().timeOf(PrayerName.ASR).orElseThrow().time();

        assertTrue(hanafiAsr.isAfter(shafiAsr),
                "changing the madhab must take effect without an explicit invalidate");
    }

    @Test
    @DisplayName("finds the next prayer later the same day")
    void findsNextPrayerSameDay() {
        // Midday: Dhuhr is around 13:15 in Istanbul in August.
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 10, 0));
        UpcomingPrayer next = service.nextPrayer().orElseThrow();

        assertEquals(PrayerName.DHUHR, next.prayer().name());
        assertFalse(next.tomorrow());
        assertTrue(next.remaining().compareTo(Duration.ZERO) > 0);
    }

    @Test
    @DisplayName("rolls over to tomorrow's Fajr after the last prayer of the day")
    void rollsOverToTomorrowsFajr() {
        // 23:30 is past Isha everywhere in Istanbul in August.
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 23, 30));
        UpcomingPrayer next = service.nextPrayer().orElseThrow();

        assertEquals(PrayerName.FAJR, next.prayer().name());
        assertTrue(next.tomorrow(), "the countdown should target tomorrow");
        assertEquals(LocalDate.of(2026, 8, 8), next.prayer().time().toLocalDate());
        assertTrue(next.remaining().toHours() < 12);
    }

    @Test
    @DisplayName("never proposes sunrise as the next prayer")
    void neverProposesSunrise() {
        // Just after Fajr, when sunrise is the next entry chronologically.
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 4, 45));
        assertEquals(PrayerName.DHUHR, service.nextPrayer().orElseThrow().prayer().name());
    }

    @Test
    @DisplayName("reports the prayer window that is currently open")
    void reportsCurrentPrayer() {
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 18, 0));
        assertEquals(PrayerName.ASR, service.currentPrayer().orElseThrow().name());

        PrayerScheduleService beforeFajr = serviceAt(LocalDateTime.of(2026, 8, 7, 2, 0));
        assertTrue(beforeFajr.currentPrayer().isEmpty());
    }

    @Test
    @DisplayName("computes an arbitrary day without disturbing the cache")
    void computesArbitraryDay() {
        PrayerScheduleService service = serviceAt(LocalDateTime.of(2026, 8, 7, 12, 0));
        service.today();
        int afterWarmUp = calculator.invocations.get();

        DailyPrayerSchedule ramadanDay = service.scheduleFor(LocalDate.of(2027, 2, 10));
        assertEquals(LocalDate.of(2027, 2, 10), ramadanDay.date());

        // The cached pair is still valid and is not recomputed.
        service.today();
        assertEquals(afterWarmUp + 1, calculator.invocations.get());
    }

    @Test
    @DisplayName("returns the current moment in the configured zone")
    void returnsNowInConfiguredZone() {
        LocalDateTime moment = LocalDateTime.of(2026, 8, 7, 15, 30);
        PrayerScheduleService service = serviceAt(moment);
        assertEquals(LocalTime.of(15, 30), service.now().toLocalTime());
        assertEquals(ZONE, service.now().getZone());
    }
}
