package com.ctrends.salahguardian.notification;

import com.ctrends.salahguardian.i18n.Messages;
import com.ctrends.salahguardian.model.PrayerName;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.model.ReminderKind;
import com.ctrends.salahguardian.utils.TimeUtils;
import jakarta.inject.Singleton;

import java.time.Duration;

/**
 * Turns a scheduled reminder into the exact words shown on screen.
 *
 * <p>Kept apart from both the scheduler and the notification backends so the
 * wording can be unit tested directly and later localised without touching
 * either.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class PrayerNotificationComposer {

    /** Mosque glyph prefixed to every prayer related message. */
    public static final String MOSQUE = "🕌";

    /** Crescent glyph used for the Ramadan messages. */
    public static final String CRESCENT = "🌙";

    /**
     * Advance warning, e.g. {@code "🕌 Asr prayer starts in 5 minutes."}
     *
     * @param prayer    the prayer that is approaching
     * @param lead      how long until it starts
     * @param friday    whether today is Friday, which renames Dhuhr to Jumu'ah
     * @param use24Hour clock format for the body line
     * @return the notification to display
     */
    public NotificationRequest advanceWarning(PrayerTime prayer, Duration lead,
                                              boolean friday, boolean use24Hour) {
        String title = Messages.format("notify.advance",
                prayer.name().displayName(friday), TimeUtils.humanise(lead));
        String body = Messages.format("notify.advanceBody",
                prayer.formatted(use24Hour), prayer.name().arabicName());
        return NotificationRequest.info(title, body);
    }

    /**
     * Prayer start, e.g. {@code "🕌 It's time for Maghrib."}
     *
     * @param prayer    the prayer whose window has just opened
     * @param friday    whether today is Friday
     * @param use24Hour clock format for the body line
     * @return the notification to display
     */
    public NotificationRequest prayerStart(PrayerTime prayer, boolean friday, boolean use24Hour) {
        String title = Messages.format("notify.start", prayer.name().displayName(friday));
        String body = Messages.format("notify.startBody",
                prayer.formatted(use24Hour), prayer.name().arabicName());
        return NotificationRequest.critical(title, body);
    }

    /**
     * Friday morning reminder about the congregational prayer.
     *
     * @param jumuah    the Dhuhr entry of the current Friday
     * @param use24Hour clock format for the body line
     * @return the notification to display
     */
    public NotificationRequest fridayReminder(PrayerTime jumuah, boolean use24Hour) {
        return NotificationRequest.info(Messages.get("notify.friday"),
                Messages.format("notify.fridayBody", jumuah.formatted(use24Hour)));
    }

    /**
     * Suhoor reminder, fired shortly before Fajr during Ramadan.
     *
     * @param fajr      today's Fajr entry
     * @param lead      how long remains before Fajr
     * @param use24Hour clock format for the body line
     * @return the notification to display
     */
    public NotificationRequest suhoorReminder(PrayerTime fajr, Duration lead, boolean use24Hour) {
        return NotificationRequest.info(
                Messages.format("notify.suhoor", TimeUtils.humanise(lead)),
                Messages.format("notify.suhoorBody", fajr.formatted(use24Hour)));
    }

    /**
     * Iftar reminder, fired at Maghrib during Ramadan.
     *
     * @param maghrib   today's Maghrib entry
     * @param use24Hour clock format for the body line
     * @return the notification to display
     */
    public NotificationRequest iftarReminder(PrayerTime maghrib, boolean use24Hour) {
        return NotificationRequest.critical(Messages.get("notify.iftar"),
                Messages.format("notify.iftarBody", maghrib.formatted(use24Hour)));
    }

    /**
     * Dispatches to the right wording for a given reminder kind.
     *
     * @param kind      which reminder is being raised
     * @param prayer    the prayer it concerns
     * @param lead      time remaining, ignored by start-time reminders
     * @param friday    whether today is Friday
     * @param use24Hour clock format for the body line
     * @return the notification to display
     */
    public NotificationRequest compose(ReminderKind kind, PrayerTime prayer, Duration lead,
                                       boolean friday, boolean use24Hour) {
        return switch (kind) {
            case ADVANCE_WARNING -> advanceWarning(prayer, lead, friday, use24Hour);
            case PRAYER_START -> prayerStart(prayer, friday, use24Hour);
            case FRIDAY -> fridayReminder(prayer, use24Hour);
            case RAMADAN -> prayer.name() == PrayerName.FAJR
                    ? suhoorReminder(prayer, lead, use24Hour)
                    : iftarReminder(prayer, use24Hour);
        };
    }
}
