package com.ctrends.salahguardian.model;

/**
 * Distinguishes the notification events the scheduler can raise for a prayer.
 *
 * @author CTrends Software
 */
public enum ReminderKind {

    /** Fired {@code reminderMinutes} before the prayer starts. */
    ADVANCE_WARNING,

    /** Fired exactly at the prayer's start time. */
    PRAYER_START,

    /** Fired on Friday morning as a Jumu'ah reminder. */
    FRIDAY,

    /** Fired around Maghrib during Ramadan (iftar) and before Fajr (suhoor). */
    RAMADAN
}
