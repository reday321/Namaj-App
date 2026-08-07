package com.ctrends.salahguardian.notification;

import com.ctrends.salahguardian.model.PrayerName;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.model.ReminderKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the notification layer: message wording, the notify-send
 * command line, and the fallback chain.
 */
class NotificationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private PrayerTime prayer(PrayerName name, int hour, int minute) {
        return new PrayerTime(name, ZonedDateTime.of(
                LocalDate.of(2026, 8, 7), LocalTime.of(hour, minute), ZONE));
    }

    // ----- composer ---------------------------------------------------------

    @Test
    @DisplayName("advance warning matches the wording in the specification")
    void composesAdvanceWarning() {
        PrayerNotificationComposer composer = new PrayerNotificationComposer();
        NotificationRequest request = composer.advanceWarning(
                prayer(PrayerName.ASR, 17, 5), Duration.ofMinutes(5), false, true);

        assertEquals("🕌 Asr prayer starts in 5 minutes.", request.title());
        assertTrue(request.body().contains("17:05"));
        assertTrue(request.body().contains("العصر"));
        assertEquals(NotificationUrgency.NORMAL, request.urgency());
    }

    @Test
    @DisplayName("prayer start matches the wording in the specification")
    void composesPrayerStart() {
        PrayerNotificationComposer composer = new PrayerNotificationComposer();
        NotificationRequest request = composer.prayerStart(
                prayer(PrayerName.MAGHRIB, 20, 20), false, true);

        assertEquals("🕌 It's time for Maghrib.", request.title());
        assertEquals(NotificationUrgency.CRITICAL, request.urgency(),
                "the prayer start should persist until dismissed");
        assertTrue(request.timeout().isZero(), "critical notifications should not auto-expire");
    }

    @Test
    @DisplayName("uses Jumu'ah instead of Dhuhr on Fridays")
    void usesJumuahOnFriday() {
        PrayerNotificationComposer composer = new PrayerNotificationComposer();
        assertEquals("🕌 It's time for Jumu'ah.",
                composer.prayerStart(prayer(PrayerName.DHUHR, 13, 15), true, true).title());
        assertEquals("🕌 It's time for Dhuhr.",
                composer.prayerStart(prayer(PrayerName.DHUHR, 13, 15), false, true).title());
    }

    @Test
    @DisplayName("pluralises the lead time correctly")
    void pluralisesLeadTime() {
        PrayerNotificationComposer composer = new PrayerNotificationComposer();
        PrayerTime asr = prayer(PrayerName.ASR, 17, 5);

        assertTrue(composer.advanceWarning(asr, Duration.ofMinutes(1), false, true)
                .title().contains("in 1 minute."));
        assertTrue(composer.advanceWarning(asr, Duration.ofMinutes(20), false, true)
                .title().contains("in 20 minutes."));
        assertTrue(composer.advanceWarning(asr, Duration.ofMinutes(60), false, true)
                .title().contains("in 1 hour."));
    }

    @Test
    @DisplayName("routes Ramadan reminders to suhoor or iftar by prayer")
    void routesRamadanReminders() {
        PrayerNotificationComposer composer = new PrayerNotificationComposer();

        NotificationRequest suhoor = composer.compose(ReminderKind.RAMADAN,
                prayer(PrayerName.FAJR, 4, 30), Duration.ofMinutes(30), false, true);
        assertTrue(suhoor.title().contains("Suhoor"));

        NotificationRequest iftar = composer.compose(ReminderKind.RAMADAN,
                prayer(PrayerName.MAGHRIB, 20, 20), Duration.ZERO, false, true);
        assertTrue(iftar.title().contains("Iftar"));
    }

    @Test
    @DisplayName("honours the 12 hour clock preference")
    void honoursTwelveHourClock() {
        PrayerNotificationComposer composer = new PrayerNotificationComposer();
        NotificationRequest request = composer.prayerStart(
                prayer(PrayerName.MAGHRIB, 20, 20), false, false);
        assertTrue(request.body().contains("08:20 PM"), "expected a 12 hour clock in " + request.body());
    }

    // ----- notify-send command ---------------------------------------------

    @Test
    @DisplayName("builds a notify-send command with the expected flags")
    void buildsNotifySendCommand() {
        LibNotifyNotificationService service = new LibNotifyNotificationService();
        List<String> command = service.buildCommand(
                NotificationRequest.info("Title", "Body"));

        assertEquals("notify-send", command.get(0));
        assertTrue(command.contains("--app-name=Salah Guardian"));
        assertTrue(command.contains("--urgency=normal"));
        assertTrue(command.contains("--expire-time=10000"));
        assertTrue(command.contains("--icon=appointment-soon"));

        int terminator = command.indexOf("--");
        assertTrue(terminator > 0, "the option list must be terminated");
        assertEquals("Title", command.get(terminator + 1));
        assertEquals("Body", command.get(terminator + 2));
    }

    @Test
    @DisplayName("omits the expiry flag for critical notifications")
    void omitsExpiryForCritical() {
        LibNotifyNotificationService service = new LibNotifyNotificationService();
        List<String> command = service.buildCommand(
                NotificationRequest.critical("Time for Maghrib", ""));

        assertTrue(command.contains("--urgency=critical"));
        assertTrue(command.stream().noneMatch(arg -> arg.startsWith("--expire-time")));
    }

    @Test
    @DisplayName("passes a summary beginning with a dash as a positional argument")
    void protectsAgainstLeadingDash() {
        LibNotifyNotificationService service = new LibNotifyNotificationService();
        List<String> command = service.buildCommand(
                new NotificationRequest("--not-an-option", "", NotificationUrgency.LOW,
                        Duration.ZERO, "", ""));

        int terminator = command.indexOf("--");
        assertEquals("--not-an-option", command.get(terminator + 1),
                "the summary must come after the option terminator");
    }

    @Test
    @DisplayName("omits an empty body rather than passing an empty argument")
    void omitsEmptyBody() {
        LibNotifyNotificationService service = new LibNotifyNotificationService();
        List<String> command = service.buildCommand(
                new NotificationRequest("Only a title", "", NotificationUrgency.NORMAL,
                        Duration.ZERO, "", ""));
        assertEquals("Only a title", command.get(command.size() - 1));
    }

    // ----- fallback chain ---------------------------------------------------

    /** Recording stub standing in for a notification backend. */
    private static final class StubBackend implements NotificationService {
        private final String name;
        private final boolean available;
        private final boolean succeeds;
        private final List<String> log;

        StubBackend(String name, boolean available, boolean succeeds, List<String> log) {
            this.name = name;
            this.available = available;
            this.succeeds = succeeds;
            this.log = log;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean send(NotificationRequest request) {
            log.add(name);
            return succeeds;
        }

        @Override
        public String describe() {
            return name;
        }
    }

    @Test
    @DisplayName("uses the first backend that succeeds and stops there")
    void usesFirstSuccessfulBackend() {
        List<String> calls = new ArrayList<>();
        CompositeNotificationService composite = new CompositeNotificationService(List.of(
                new StubBackend("libnotify", true, true, calls),
                new StubBackend("tray", true, true, calls)));

        assertTrue(composite.send(NotificationRequest.info("t", "b")));
        assertEquals(List.of("libnotify"), calls);
    }

    @Test
    @DisplayName("falls through to the next backend when one fails")
    void fallsThroughOnFailure() {
        List<String> calls = new ArrayList<>();
        CompositeNotificationService composite = new CompositeNotificationService(List.of(
                new StubBackend("libnotify", true, false, calls),
                new StubBackend("tray", true, false, calls),
                new StubBackend("log", true, true, calls)));

        assertTrue(composite.send(NotificationRequest.info("t", "b")));
        assertEquals(List.of("libnotify", "tray", "log"), calls);
    }

    @Test
    @DisplayName("skips unavailable backends without calling them")
    void skipsUnavailableBackends() {
        List<String> calls = new ArrayList<>();
        CompositeNotificationService composite = new CompositeNotificationService(List.of(
                new StubBackend("libnotify", false, true, calls),
                new StubBackend("log", true, true, calls)));

        assertTrue(composite.send(NotificationRequest.info("t", "b")));
        assertEquals(List.of("log"), calls);
    }

    @Test
    @DisplayName("a throwing backend does not stop the chain")
    void survivesThrowingBackend() {
        List<String> calls = new ArrayList<>();
        NotificationService exploding = new NotificationService() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public boolean send(NotificationRequest request) {
                throw new IllegalStateException("dbus went away");
            }

            @Override
            public String describe() {
                return "exploding";
            }
        };
        CompositeNotificationService composite = new CompositeNotificationService(
                List.of(exploding, new StubBackend("log", true, true, calls)));

        assertTrue(composite.send(NotificationRequest.info("t", "b")));
        assertEquals(List.of("log"), calls);
    }

    @Test
    @DisplayName("reports failure only when every backend declines")
    void reportsFailureWhenAllDecline() {
        List<String> calls = new ArrayList<>();
        CompositeNotificationService composite = new CompositeNotificationService(List.of(
                new StubBackend("a", true, false, calls),
                new StubBackend("b", false, true, calls)));

        assertFalse(composite.send(NotificationRequest.info("t", "b")));
    }

    @Test
    @DisplayName("the logging backend always accepts, so reminders are never lost")
    void loggingBackendAlwaysAccepts() {
        LoggingNotificationService logging = new LoggingNotificationService();
        assertTrue(logging.isAvailable());
        assertTrue(logging.send(NotificationRequest.info("Time for Fajr", "")));
    }
}
