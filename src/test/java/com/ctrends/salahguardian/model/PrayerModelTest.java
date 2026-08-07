package com.ctrends.salahguardian.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PrayerName}, {@link PrayerTime},
 * {@link DailyPrayerSchedule} and {@link UpcomingPrayer}.
 */
class PrayerModelTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    /** A Friday, so the Jumu'ah renaming can be exercised. */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 8, 7);

    private DailyPrayerSchedule schedule(LocalDate date) {
        Map<PrayerName, PrayerTime> times = new EnumMap<>(PrayerName.class);
        times.put(PrayerName.FAJR, at(date, 4, 30));
        times.put(PrayerName.SUNRISE, at(date, 6, 5));
        times.put(PrayerName.DHUHR, at(date, 13, 15));
        times.put(PrayerName.ASR, at(date, 17, 5));
        times.put(PrayerName.MAGHRIB, at(date, 20, 20));
        times.put(PrayerName.ISHA, at(date, 21, 50));
        return new DailyPrayerSchedule(date, GeoLocation.MAKKAH, times);
    }

    private PrayerTime at(LocalDate date, int hour, int minute) {
        PrayerName name = switch (hour) {
            case 4 -> PrayerName.FAJR;
            case 6 -> PrayerName.SUNRISE;
            case 13 -> PrayerName.DHUHR;
            case 17 -> PrayerName.ASR;
            case 20 -> PrayerName.MAGHRIB;
            default -> PrayerName.ISHA;
        };
        return new PrayerTime(name, ZonedDateTime.of(date, java.time.LocalTime.of(hour, minute), ZONE));
    }

    @Test
    @DisplayName("sunrise is not treated as an obligatory prayer")
    void sunriseIsNotObligatory() {
        assertFalse(PrayerName.SUNRISE.isObligatory());
        assertEquals(5, schedule(FRIDAY).obligatoryTimes().size());
        assertEquals(6, schedule(FRIDAY).allTimes().size());
    }

    @Test
    @DisplayName("renames Dhuhr to Jumu'ah on Fridays only")
    void renamesDhuhrOnFriday() {
        assertEquals("Jumu'ah", PrayerName.DHUHR.displayName(true));
        assertEquals("Dhuhr", PrayerName.DHUHR.displayName(false));
        assertEquals("Asr", PrayerName.ASR.displayName(true));
    }

    @Test
    @DisplayName("parses prayer names case-insensitively and rejects unknown ones")
    void parsesNames() {
        assertEquals(Optional.of(PrayerName.MAGHRIB), PrayerName.parse("maghrib"));
        assertEquals(Optional.of(PrayerName.FAJR), PrayerName.parse("  FAJR "));
        assertEquals(Optional.empty(), PrayerName.parse("tahajjud"));
        assertEquals(Optional.empty(), PrayerName.parse(null));
    }

    @Test
    @DisplayName("returns the times of the day in chronological order")
    void ordersTimesChronologically() {
        var ordered = schedule(FRIDAY).allTimes();
        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(ordered.get(i - 1).time().isBefore(ordered.get(i).time()),
                    "entry " + i + " is out of order");
        }
        assertEquals(PrayerName.FAJR, ordered.get(0).name());
        assertEquals(PrayerName.ISHA, ordered.get(ordered.size() - 1).name());
    }

    @Test
    @DisplayName("finds the next obligatory prayer, skipping sunrise")
    void findsNextPrayerSkippingSunrise() {
        DailyPrayerSchedule day = schedule(FRIDAY);
        ZonedDateTime justAfterFajr = ZonedDateTime.of(FRIDAY, java.time.LocalTime.of(5, 0), ZONE);

        assertEquals(PrayerName.DHUHR, day.nextAfter(justAfterFajr, false).orElseThrow().name());
        assertEquals(PrayerName.SUNRISE, day.nextAfter(justAfterFajr, true).orElseThrow().name());
    }

    @Test
    @DisplayName("returns empty once the day's last prayer has passed")
    void returnsEmptyAfterIsha() {
        DailyPrayerSchedule day = schedule(FRIDAY);
        ZonedDateTime afterIsha = ZonedDateTime.of(FRIDAY, java.time.LocalTime.of(23, 30), ZONE);
        assertTrue(day.nextAfter(afterIsha, false).isEmpty());
    }

    @Test
    @DisplayName("identifies the prayer window currently open")
    void identifiesCurrentPrayer() {
        DailyPrayerSchedule day = schedule(FRIDAY);
        ZonedDateTime afternoon = ZonedDateTime.of(FRIDAY, java.time.LocalTime.of(18, 0), ZONE);
        assertEquals(PrayerName.ASR, day.currentAt(afternoon).orElseThrow().name());

        ZonedDateTime beforeFajr = ZonedDateTime.of(FRIDAY, java.time.LocalTime.of(3, 0), ZONE);
        assertTrue(day.currentAt(beforeFajr).isEmpty());
    }

    @Test
    @DisplayName("formats prayer times in both 24 and 12 hour clocks")
    void formatsTimes() {
        PrayerTime maghrib = at(FRIDAY, 20, 20);
        assertEquals("20:20", maghrib.formatted(true));
        assertEquals("08:20 PM", maghrib.formatted(false));
    }

    @Test
    @DisplayName("formats the countdown as MM:SS below an hour and H:MM:SS above")
    void formatsCountdown() {
        PrayerTime target = at(FRIDAY, 20, 20);
        ZonedDateTime tenMinutesBefore = target.time().minusMinutes(10).minusSeconds(30);
        assertEquals("10:30", UpcomingPrayer.from(target, tenMinutesBefore, false).formattedRemaining());

        ZonedDateTime twoHoursBefore = target.time().minusHours(2).minusMinutes(5);
        assertEquals("2:05:00", UpcomingPrayer.from(target, twoHoursBefore, false).formattedRemaining());
    }

    @Test
    @DisplayName("clamps a negative countdown to zero rather than showing negative time")
    void clampsNegativeCountdown() {
        PrayerTime target = at(FRIDAY, 20, 20);
        UpcomingPrayer past = UpcomingPrayer.from(target, target.time().plusMinutes(5), false);
        assertEquals(Duration.ZERO, past.remaining());
        assertEquals("00:00", past.formattedRemaining());
    }

    @Test
    @DisplayName("flags Friday schedules")
    void flagsFriday() {
        assertTrue(schedule(FRIDAY).isFriday());
        assertFalse(schedule(FRIDAY.plusDays(1)).isFriday());
    }
}
