package com.ctrends.salahguardian.service;

import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.model.ReminderKind;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * One thing the scheduler intends to do at one specific moment.
 *
 * <p>Events are pure values computed from a day's timetable, which makes the
 * planning logic in {@link ReminderPlanner} straightforward to unit test with a
 * fixed clock.</p>
 *
 * @param kind    what sort of reminder this is
 * @param prayer  the prayer it concerns
 * @param fireAt  the moment the reminder should be raised
 * @param lead    how far ahead of the prayer this event sits; zero for
 *                start-time reminders
 * @author CTrends Software
 */
public record ReminderEvent(ReminderKind kind, PrayerTime prayer,
                            ZonedDateTime fireAt, Duration lead)
        implements Comparable<ReminderEvent> {

    public ReminderEvent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(prayer, "prayer");
        Objects.requireNonNull(fireAt, "fireAt");
        lead = Objects.requireNonNullElse(lead, Duration.ZERO);
    }

    /**
     * @param reference the moment to compare against
     * @return {@code true} when this event is still in the future
     */
    public boolean isPending(ZonedDateTime reference) {
        return fireAt.isAfter(reference);
    }

    /**
     * A stable identity used to remember that an event has already been
     * delivered, so a reschedule cannot fire the same reminder twice.
     *
     * @return the deduplication key
     */
    public String dedupeKey() {
        return kind.name() + '@' + prayer.name().name() + '@' + fireAt.toEpochSecond();
    }

    @Override
    public int compareTo(ReminderEvent other) {
        return fireAt.compareTo(other.fireAt);
    }
}
