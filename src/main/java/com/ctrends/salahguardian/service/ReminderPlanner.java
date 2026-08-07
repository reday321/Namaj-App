package com.ctrends.salahguardian.service;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.PrayerName;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.model.ReminderKind;
import com.ctrends.salahguardian.utils.TimeUtils;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Expands a day's timetable into the concrete list of reminders that should
 * fire, according to the user's preferences.
 *
 * <p>Deliberately free of threads, clocks and I/O: it takes a schedule plus a
 * configuration and returns values. All of the scheduler's decision making that
 * is worth testing lives here.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class ReminderPlanner {

    /** How long before Fajr the suhoor reminder is raised during Ramadan. */
    public static final Duration SUHOOR_LEAD = Duration.ofMinutes(30);

    /**
     * Builds every reminder for one day.
     *
     * @param schedule the day's prayer times
     * @param config   the user's reminder preferences
     * @return the events for that day, sorted chronologically
     */
    public List<ReminderEvent> plan(DailyPrayerSchedule schedule, AppConfig config) {
        List<ReminderEvent> events = new ArrayList<>();
        if (!config.shouldNotify()) {
            return events;
        }

        Duration lead = Duration.ofMinutes(config.getReminderMinutes());
        boolean wantsAdvance = config.getReminderMinutes() > 0;

        for (PrayerTime prayer : schedule.obligatoryTimes()) {
            if (wantsAdvance) {
                events.add(new ReminderEvent(ReminderKind.ADVANCE_WARNING, prayer,
                        prayer.time().minus(lead), lead));
            }
            if (config.isRemindAtPrayerTime()) {
                events.add(new ReminderEvent(ReminderKind.PRAYER_START, prayer,
                        prayer.time(), Duration.ZERO));
            }
        }

        addFridayReminder(events, schedule, config);
        addRamadanReminders(events, schedule, config);

        events.sort(null);
        return events;
    }

    /**
     * Adds the Jumu'ah reminder on Friday mornings.
     */
    private void addFridayReminder(List<ReminderEvent> events, DailyPrayerSchedule schedule,
                                   AppConfig config) {
        if (!config.isFridayReminderEnabled() || !schedule.isFriday()) {
            return;
        }
        schedule.timeOf(PrayerName.DHUHR).ifPresent(dhuhr -> {
            ZonedDateTime fireAt = dhuhr.time()
                    .withHour(config.getFridayReminderHour())
                    .withMinute(0).withSecond(0).withNano(0);
            // Only worth announcing while it is still ahead of the prayer.
            if (fireAt.isBefore(dhuhr.time())) {
                events.add(new ReminderEvent(ReminderKind.FRIDAY, dhuhr, fireAt,
                        Duration.between(fireAt, dhuhr.time())));
            }
        });
    }

    /**
     * Adds the suhoor and iftar reminders on days that fall inside Ramadan.
     */
    private void addRamadanReminders(List<ReminderEvent> events, DailyPrayerSchedule schedule,
                                     AppConfig config) {
        if (!config.isRamadanRemindersEnabled() || !TimeUtils.isRamadan(schedule.date())) {
            return;
        }
        schedule.timeOf(PrayerName.FAJR).ifPresent(fajr ->
                events.add(new ReminderEvent(ReminderKind.RAMADAN, fajr,
                        fajr.time().minus(SUHOOR_LEAD), SUHOOR_LEAD)));
        schedule.timeOf(PrayerName.MAGHRIB).ifPresent(maghrib ->
                events.add(new ReminderEvent(ReminderKind.RAMADAN, maghrib,
                        maghrib.time(), Duration.ZERO)));
    }

    /**
     * Selects the events that still lie ahead of a reference moment.
     *
     * @param events    candidate events
     * @param reference the current moment
     * @return the pending subset, in chronological order
     */
    public List<ReminderEvent> pendingAfter(List<ReminderEvent> events, ZonedDateTime reference) {
        return events.stream().filter(event -> event.isPending(reference)).sorted().toList();
    }
}
