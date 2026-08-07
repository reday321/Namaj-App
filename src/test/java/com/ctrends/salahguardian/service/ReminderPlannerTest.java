package com.ctrends.salahguardian.service;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.PrayerName;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.model.ReminderKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReminderPlanner} - the component that decides which
 * notifications a day should produce.
 */
class ReminderPlannerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    /** 7 August 2026 is a Friday. */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 8, 7);
    /** 8 August 2026 is a Saturday. */
    private static final LocalDate SATURDAY = LocalDate.of(2026, 8, 8);

    private ReminderPlanner planner;
    private AppConfig config;

    @BeforeEach
    void setUp() {
        planner = new ReminderPlanner();
        config = new AppConfig();
        config.setFridayReminderEnabled(false);
        config.setRamadanRemindersEnabled(false);
    }

    private DailyPrayerSchedule schedule(LocalDate date) {
        Map<PrayerName, PrayerTime> times = new EnumMap<>(PrayerName.class);
        times.put(PrayerName.FAJR, entry(PrayerName.FAJR, date, 4, 30));
        times.put(PrayerName.SUNRISE, entry(PrayerName.SUNRISE, date, 6, 5));
        times.put(PrayerName.DHUHR, entry(PrayerName.DHUHR, date, 13, 15));
        times.put(PrayerName.ASR, entry(PrayerName.ASR, date, 17, 5));
        times.put(PrayerName.MAGHRIB, entry(PrayerName.MAGHRIB, date, 20, 20));
        times.put(PrayerName.ISHA, entry(PrayerName.ISHA, date, 21, 50));
        return new DailyPrayerSchedule(date, GeoLocation.MAKKAH, times);
    }

    private PrayerTime entry(PrayerName name, LocalDate date, int hour, int minute) {
        return new PrayerTime(name, ZonedDateTime.of(date, LocalTime.of(hour, minute), ZONE));
    }

    @Test
    @DisplayName("plans an advance warning and a start reminder for each obligatory prayer")
    void plansTwoEventsPerPrayer() {
        List<ReminderEvent> events = planner.plan(schedule(SATURDAY), config);

        assertEquals(10, events.size(), "five prayers x two reminders");
        assertEquals(5, events.stream()
                .filter(e -> e.kind() == ReminderKind.ADVANCE_WARNING).count());
        assertEquals(5, events.stream()
                .filter(e -> e.kind() == ReminderKind.PRAYER_START).count());
        assertTrue(events.stream().noneMatch(e -> e.prayer().name() == PrayerName.SUNRISE),
                "sunrise is not a prayer and must not be announced");
    }

    @Test
    @DisplayName("places the advance warning exactly the configured number of minutes early")
    void advanceWarningUsesConfiguredLead() {
        config.setReminderMinutes(15);
        List<ReminderEvent> events = planner.plan(schedule(SATURDAY), config);

        ReminderEvent asrWarning = events.stream()
                .filter(e -> e.kind() == ReminderKind.ADVANCE_WARNING)
                .filter(e -> e.prayer().name() == PrayerName.ASR)
                .findFirst().orElseThrow();

        assertEquals(LocalTime.of(16, 50), asrWarning.fireAt().toLocalTime());
        assertEquals(15, asrWarning.lead().toMinutes());
    }

    @Test
    @DisplayName("a reminder lead of zero switches the advance warning off")
    void zeroLeadDisablesAdvanceWarning() {
        config.setReminderMinutes(0);
        List<ReminderEvent> events = planner.plan(schedule(SATURDAY), config);

        assertEquals(5, events.size());
        assertTrue(events.stream().allMatch(e -> e.kind() == ReminderKind.PRAYER_START));
    }

    @Test
    @DisplayName("disabling the start reminder leaves only the advance warnings")
    void canDisableStartReminder() {
        config.setRemindAtPrayerTime(false);
        List<ReminderEvent> events = planner.plan(schedule(SATURDAY), config);

        assertEquals(5, events.size());
        assertTrue(events.stream().allMatch(e -> e.kind() == ReminderKind.ADVANCE_WARNING));
    }

    @Test
    @DisplayName("silent mode and disabled notifications produce no events at all")
    void silentModeProducesNothing() {
        config.setSilentMode(true);
        assertTrue(planner.plan(schedule(SATURDAY), config).isEmpty());

        config.setSilentMode(false);
        config.setNotificationsEnabled(false);
        assertTrue(planner.plan(schedule(SATURDAY), config).isEmpty());
    }

    @Test
    @DisplayName("returns events in chronological order")
    void returnsChronologicalOrder() {
        List<ReminderEvent> events = planner.plan(schedule(SATURDAY), config);
        for (int i = 1; i < events.size(); i++) {
            assertFalse(events.get(i).fireAt().isBefore(events.get(i - 1).fireAt()),
                    "event " + i + " is out of order");
        }
    }

    @Test
    @DisplayName("adds the Jumu'ah reminder on Friday only")
    void addsFridayReminder() {
        config.setFridayReminderEnabled(true);
        config.setFridayReminderHour(9);

        List<ReminderEvent> friday = planner.plan(schedule(FRIDAY), config);
        ReminderEvent jumuah = friday.stream()
                .filter(e -> e.kind() == ReminderKind.FRIDAY)
                .findFirst().orElseThrow();
        assertEquals(LocalTime.of(9, 0), jumuah.fireAt().toLocalTime());
        assertEquals(PrayerName.DHUHR, jumuah.prayer().name());

        List<ReminderEvent> saturday = planner.plan(schedule(SATURDAY), config);
        assertTrue(saturday.stream().noneMatch(e -> e.kind() == ReminderKind.FRIDAY));
    }

    @Test
    @DisplayName("skips the Friday reminder when its hour is already past Dhuhr")
    void skipsLateFridayReminder() {
        config.setFridayReminderEnabled(true);
        config.setFridayReminderHour(20); // after the 13:15 Dhuhr in this fixture

        List<ReminderEvent> events = planner.plan(schedule(FRIDAY), config);
        assertTrue(events.stream().noneMatch(e -> e.kind() == ReminderKind.FRIDAY));
    }

    @Test
    @DisplayName("adds suhoor and iftar reminders only during Ramadan")
    void addsRamadanReminders() {
        config.setRamadanRemindersEnabled(true);

        // 20 March 2025 falls inside Ramadan 1446.
        LocalDate ramadanDay = LocalDate.of(2025, 3, 20);
        List<ReminderEvent> ramadan = planner.plan(schedule(ramadanDay), config);
        List<ReminderEvent> ramadanEvents = ramadan.stream()
                .filter(e -> e.kind() == ReminderKind.RAMADAN).toList();

        assertEquals(2, ramadanEvents.size(), "expected a suhoor and an iftar reminder");
        ReminderEvent suhoor = ramadanEvents.stream()
                .filter(e -> e.prayer().name() == PrayerName.FAJR).findFirst().orElseThrow();
        assertEquals(LocalTime.of(4, 0), suhoor.fireAt().toLocalTime(),
                "suhoor should fire 30 minutes before Fajr");
        ReminderEvent iftar = ramadanEvents.stream()
                .filter(e -> e.prayer().name() == PrayerName.MAGHRIB).findFirst().orElseThrow();
        assertEquals(LocalTime.of(20, 20), iftar.fireAt().toLocalTime());

        // An ordinary day outside Ramadan gets none.
        assertTrue(planner.plan(schedule(SATURDAY), config).stream()
                .noneMatch(e -> e.kind() == ReminderKind.RAMADAN));
    }

    @Test
    @DisplayName("filters out events that have already passed")
    void filtersPastEvents() {
        List<ReminderEvent> events = planner.plan(schedule(SATURDAY), config);
        ZonedDateTime evening = ZonedDateTime.of(SATURDAY, LocalTime.of(20, 30), ZONE);

        List<ReminderEvent> pending = planner.pendingAfter(events, evening);

        assertTrue(pending.stream().allMatch(e -> e.fireAt().isAfter(evening)));
        // Only the Isha advance warning (21:45) and Isha itself (21:50) remain.
        assertEquals(2, pending.size());
    }

    @Test
    @DisplayName("gives every event a distinct deduplication key")
    void producesDistinctDedupeKeys() {
        List<ReminderEvent> events = planner.plan(schedule(SATURDAY), config);
        long distinct = events.stream().map(ReminderEvent::dedupeKey).distinct().count();
        assertEquals(events.size(), distinct);
    }
}
