package com.ctrends.salahguardian.service;

/**
 * Callback contract for components that react to scheduled reminders.
 *
 * <p>Implemented by the application controller, which shows the focus overlay,
 * and by the view models, which refresh the dashboard. Callbacks arrive on the
 * scheduler thread, so implementations that touch the UI must marshal onto the
 * JavaFX application thread themselves.</p>
 *
 * @author CTrends Software
 */
public interface PrayerEventListener {

    /**
     * A reminder has just fired.
     *
     * @param event the reminder that was raised
     */
    void onReminder(ReminderEvent event);

    /**
     * The day's timetable has been recomputed, e.g. after midnight, a settings
     * change or a location update.
     */
    default void onScheduleChanged() {
        // optional
    }
}
