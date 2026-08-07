package com.ctrends.salahguardian.prayer;

import com.ctrends.salahguardian.model.CalculationMethodOption;
import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.HighLatitudeRuleOption;
import com.ctrends.salahguardian.model.LocationSource;
import com.ctrends.salahguardian.model.MadhabOption;
import com.ctrends.salahguardian.model.PrayerName;
import com.ctrends.salahguardian.model.PrayerTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AdhanPrayerTimeCalculator}.
 *
 * <p>The assertions are deliberately about invariants and relative behaviour
 * rather than hard-coded clock times: the underlying astronomical library owns
 * the exact minutes, and pinning them here would turn a library upgrade into a
 * spurious test failure. What must never change is that the ordering holds,
 * that the madhab moves Asr and nothing else, and that the user's offsets are
 * applied exactly.</p>
 */
class AdhanPrayerTimeCalculatorTest {

    private static final GeoLocation ISTANBUL = new GeoLocation(41.0082, 28.9784,
            "Istanbul", "Turkey", "Europe/Istanbul", LocationSource.MANUAL, java.time.Instant.EPOCH);
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 7);

    private AdhanPrayerTimeCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AdhanPrayerTimeCalculator();
    }

    private DailyPrayerSchedule calculate(CalculationSettings settings) {
        return calculator.calculate(ISTANBUL, DATE, ISTANBUL_ZONE, settings);
    }

    @Test
    @DisplayName("produces all six entries for a mid-latitude city")
    void producesAllSixEntries() {
        DailyPrayerSchedule schedule = calculate(CalculationSettings.defaults());
        assertEquals(6, schedule.allTimes().size());
        for (PrayerName prayer : PrayerName.values()) {
            assertTrue(schedule.timeOf(prayer).isPresent(), prayer + " should have a time");
        }
    }

    @Test
    @DisplayName("returns the times in the correct order and on the requested date")
    void returnsOrderedTimesOnRequestedDate() {
        DailyPrayerSchedule schedule = calculate(CalculationSettings.defaults());
        var times = schedule.allTimes();

        assertEquals(PrayerName.FAJR, times.get(0).name());
        assertEquals(PrayerName.SUNRISE, times.get(1).name());
        assertEquals(PrayerName.DHUHR, times.get(2).name());
        assertEquals(PrayerName.ASR, times.get(3).name());
        assertEquals(PrayerName.MAGHRIB, times.get(4).name());
        assertEquals(PrayerName.ISHA, times.get(5).name());

        for (PrayerTime entry : times) {
            assertEquals(DATE, entry.time().toLocalDate(),
                    entry.name() + " should fall on the requested date");
            assertEquals(ISTANBUL_ZONE, entry.time().getZone());
        }
    }

    @Test
    @DisplayName("places Hanafi Asr later than Shafi Asr, leaving every other time untouched")
    void hanafiAsrIsLaterThanShafi() {
        DailyPrayerSchedule shafi = calculate(CalculationSettings.defaults());
        DailyPrayerSchedule hanafi = calculate(new CalculationSettings(
                CalculationMethodOption.MUSLIM_WORLD_LEAGUE, MadhabOption.HANAFI,
                HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT, 18, 17, new int[6]));

        var shafiAsr = shafi.timeOf(PrayerName.ASR).orElseThrow().time();
        var hanafiAsr = hanafi.timeOf(PrayerName.ASR).orElseThrow().time();
        assertTrue(hanafiAsr.isAfter(shafiAsr),
                "Hanafi Asr (" + hanafiAsr + ") should be after Shafi Asr (" + shafiAsr + ")");

        for (PrayerName prayer : PrayerName.values()) {
            if (prayer != PrayerName.ASR) {
                assertEquals(shafi.timeOf(prayer).orElseThrow().time(),
                        hanafi.timeOf(prayer).orElseThrow().time(),
                        prayer + " must not depend on the madhab");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(CalculationMethodOption.class)
    @DisplayName("every calculation method produces a usable, ordered timetable")
    void everyMethodProducesUsableTimes(CalculationMethodOption method) {
        DailyPrayerSchedule schedule = calculate(new CalculationSettings(
                method, MadhabOption.SHAFI, HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT,
                18, 17, new int[6]));

        assertEquals(6, schedule.allTimes().size(), method + " should produce six entries");
        var times = schedule.allTimes();
        for (int i = 1; i < times.size(); i++) {
            assertTrue(times.get(i - 1).time().isBefore(times.get(i).time()),
                    method + ": " + times.get(i).name() + " should follow " + times.get(i - 1).name());
        }
    }

    @Test
    @DisplayName("the Turkey preset differs from Muslim World League by the Diyanet offsets")
    void turkeyAppliesDiyanetOffsets() {
        DailyPrayerSchedule mwl = calculate(CalculationSettings.defaults());
        DailyPrayerSchedule turkey = calculate(new CalculationSettings(
                CalculationMethodOption.TURKEY, MadhabOption.SHAFI,
                HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT, 18, 17, new int[6]));

        // Diyanet shifts sunrise by -7 and Maghrib by +7 minutes relative to
        // the plain 18/17 angles, which MWL also uses.
        long sunriseDelta = Duration.between(
                mwl.timeOf(PrayerName.SUNRISE).orElseThrow().time(),
                turkey.timeOf(PrayerName.SUNRISE).orElseThrow().time()).toMinutes();
        long maghribDelta = Duration.between(
                mwl.timeOf(PrayerName.MAGHRIB).orElseThrow().time(),
                turkey.timeOf(PrayerName.MAGHRIB).orElseThrow().time()).toMinutes();

        assertEquals(-7, sunriseDelta);
        assertEquals(7, maghribDelta);
    }

    @Test
    @DisplayName("custom angles actually change Fajr and Isha")
    void customAnglesChangeFajrAndIsha() {
        CalculationSettings shallow = new CalculationSettings(
                CalculationMethodOption.CUSTOM, MadhabOption.SHAFI,
                HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT, 12.0, 12.0, new int[6]);
        CalculationSettings steep = new CalculationSettings(
                CalculationMethodOption.CUSTOM, MadhabOption.SHAFI,
                HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT, 20.0, 20.0, new int[6]);

        DailyPrayerSchedule shallowTimes = calculate(shallow);
        DailyPrayerSchedule steepTimes = calculate(steep);

        // A larger angle means the sun must be further below the horizon, so
        // Fajr comes earlier and Isha later.
        assertTrue(steepTimes.timeOf(PrayerName.FAJR).orElseThrow().time()
                .isBefore(shallowTimes.timeOf(PrayerName.FAJR).orElseThrow().time()));
        assertTrue(steepTimes.timeOf(PrayerName.ISHA).orElseThrow().time()
                .isAfter(shallowTimes.timeOf(PrayerName.ISHA).orElseThrow().time()));
    }

    @Test
    @DisplayName("applies the user's per-prayer minute offsets exactly")
    void appliesManualAdjustments() {
        int[] offsets = {2, -3, 4, -5, 6, -7};
        DailyPrayerSchedule plain = calculate(CalculationSettings.defaults());
        DailyPrayerSchedule adjusted = calculate(new CalculationSettings(
                CalculationMethodOption.MUSLIM_WORLD_LEAGUE, MadhabOption.SHAFI,
                HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT, 18, 17, offsets));

        PrayerName[] order = {PrayerName.FAJR, PrayerName.SUNRISE, PrayerName.DHUHR,
                PrayerName.ASR, PrayerName.MAGHRIB, PrayerName.ISHA};
        for (int i = 0; i < order.length; i++) {
            long delta = Duration.between(plain.timeOf(order[i]).orElseThrow().time(),
                    adjusted.timeOf(order[i]).orElseThrow().time()).toMinutes();
            assertEquals(offsets[i], delta, order[i] + " offset was not applied");
        }
    }

    @Test
    @DisplayName("expresses the same instant differently in different zones")
    void respectsTheRequestedZone() {
        DailyPrayerSchedule local = calculator.calculate(ISTANBUL, DATE, ISTANBUL_ZONE,
                CalculationSettings.defaults());
        DailyPrayerSchedule utc = calculator.calculate(ISTANBUL, DATE, ZoneId.of("UTC"),
                CalculationSettings.defaults());

        var localDhuhr = local.timeOf(PrayerName.DHUHR).orElseThrow().time();
        var utcDhuhr = utc.timeOf(PrayerName.DHUHR).orElseThrow().time();

        assertEquals(localDhuhr.toInstant(), utcDhuhr.toInstant(),
                "the underlying instant must be identical");
        assertNotEquals(localDhuhr.toLocalTime(), utcDhuhr.toLocalTime(),
                "the wall clock reading must differ");
    }

    @Test
    @DisplayName("uses the nearest-latitude fallback during the polar midnight sun")
    void copesWithHighLatitude() {
        GeoLocation tromso = new GeoLocation(69.6492, 18.9553, "Tromso", "Norway",
                "Europe/Oslo", LocationSource.MANUAL, java.time.Instant.EPOCH);
        CalculationSettings settings = new CalculationSettings(
                CalculationMethodOption.MUSLIM_WORLD_LEAGUE, MadhabOption.SHAFI,
                HighLatitudeRuleOption.SEVENTH_OF_THE_NIGHT, 18, 17, new int[6]);

        // At 69 degrees north in late June the sun never sets, so no twilight
        // angle is ever crossed and the engine alone yields nothing at all.
        // The calculator must fall back to the nearest usable latitude and say
        // so, rather than handing the user an empty day.
        DailyPrayerSchedule polarSummer = calculator.calculate(tromso,
                LocalDate.of(2026, 6, 21), ZoneId.of("Europe/Oslo"), settings);

        assertTrue(polarSummer.isApproximated(),
                "the polar summer result should be flagged as approximate");
        assertTrue(polarSummer.isComplete(),
                "the fallback should still deliver all five obligatory prayers");
        assertEquals(6, polarSummer.allTimes().size());
        assertEquals(tromso.latitude(), polarSummer.location().latitude(), 1e-9,
                "the schedule must still report the user's real position");

        // Outside the polar day the true position works and no fallback is used.
        DailyPrayerSchedule equinox = calculator.calculate(tromso,
                LocalDate.of(2026, 9, 21), ZoneId.of("Europe/Oslo"), settings);
        assertFalse(equinox.isApproximated());
        assertTrue(equinox.isComplete());
    }

    @Test
    @DisplayName("never approximates at ordinary latitudes")
    void doesNotApproximateAtOrdinaryLatitudes() {
        DailyPrayerSchedule istanbul = calculate(CalculationSettings.defaults());
        assertFalse(istanbul.isApproximated());
        assertTrue(istanbul.isComplete());

        GeoLocation southernHemisphere = new GeoLocation(-33.8688, 151.2093, "Sydney",
                "Australia", "Australia/Sydney", LocationSource.MANUAL, java.time.Instant.EPOCH);
        DailyPrayerSchedule sydney = calculator.calculate(southernHemisphere, DATE,
                ZoneId.of("Australia/Sydney"), CalculationSettings.defaults());
        assertFalse(sydney.isApproximated());
        assertTrue(sydney.isComplete());
    }

    @Test
    @DisplayName("approximates the Antarctic winter in the southern hemisphere too")
    void approximatesSouthernPolarRegion() {
        GeoLocation ushuaiaSouth = new GeoLocation(-69.0, 39.0, "", "",
                "Antarctica/Syowa", LocationSource.MANUAL, java.time.Instant.EPOCH);
        DailyPrayerSchedule schedule = calculator.calculate(ushuaiaSouth,
                LocalDate.of(2026, 12, 21), ZoneId.of("Antarctica/Syowa"),
                CalculationSettings.defaults());

        assertTrue(schedule.isApproximated());
        assertTrue(schedule.isComplete());
    }

    @Test
    @DisplayName("returns times truncated to whole minutes, with no millisecond drift")
    void returnsWholeMinutes() {
        DailyPrayerSchedule first = calculate(CalculationSettings.defaults());
        DailyPrayerSchedule second = calculate(CalculationSettings.defaults());

        for (PrayerTime entry : first.allTimes()) {
            assertEquals(0, entry.time().getSecond(), entry.name() + " should land on a whole minute");
            assertEquals(0, entry.time().getNano(), entry.name() + " should carry no sub-second part");
        }
        // Two calculations of the same day must be bit-for-bit identical.
        assertEquals(first.allTimes(), second.allTimes());
    }

    @Test
    @DisplayName("calculation settings compare by value, including the offset array")
    void settingsCompareByValue() {
        CalculationSettings a = new CalculationSettings(CalculationMethodOption.KARACHI,
                MadhabOption.HANAFI, HighLatitudeRuleOption.TWILIGHT_ANGLE, 18, 17,
                new int[]{1, 0, 0, 0, 0, 0});
        CalculationSettings b = new CalculationSettings(CalculationMethodOption.KARACHI,
                MadhabOption.HANAFI, HighLatitudeRuleOption.TWILIGHT_ANGLE, 18, 17,
                new int[]{1, 0, 0, 0, 0, 0});
        CalculationSettings c = new CalculationSettings(CalculationMethodOption.KARACHI,
                MadhabOption.HANAFI, HighLatitudeRuleOption.TWILIGHT_ANGLE, 18, 17,
                new int[]{2, 0, 0, 0, 0, 0});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
