package com.ctrends.salahguardian.service;

import com.ctrends.salahguardian.config.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the screen lock feature.
 *
 * <p>These deliberately never call {@link ScreenLockService#lock()} against the
 * real desktop - a test run must not lock the developer out of their session.
 * What is asserted here is the safety envelope around locking: that it is off
 * by default, that the grace period is bounded, and that the implementation
 * only ever reaches for unprivileged mechanisms.</p>
 */
class ScreenLockServiceTest {

    @Test
    @DisplayName("locking is off by default")
    void lockingIsOffByDefault() {
        AppConfig config = new AppConfig();
        assertFalse(config.isLockScreenAtPrayerTime(),
                "an app that locks the screen uninvited is an app people uninstall");
    }

    @Test
    @DisplayName("the default grace period gives the user time to react")
    void defaultGracePeriodIsUsable() {
        assertEquals(30, new AppConfig().getLockDelaySeconds());
    }

    @Test
    @DisplayName("the grace period is clamped to a sane range")
    void gracePeriodIsClamped() {
        AppConfig config = new AppConfig();

        config.setLockDelaySeconds(-5);
        config.normalise();
        assertEquals(0, config.getLockDelaySeconds(), "0 means lock immediately");

        config.setLockDelaySeconds(99_999);
        config.normalise();
        assertEquals(300, config.getLockDelaySeconds(), "five minutes is the ceiling");
    }

    @Test
    @DisplayName("lock settings survive a copy")
    void settingsSurviveCopy() {
        AppConfig original = new AppConfig();
        original.setLockScreenAtPrayerTime(true);
        original.setLockDelaySeconds(45);

        AppConfig copy = original.copy();
        assertTrue(copy.isLockScreenAtPrayerTime());
        assertEquals(45, copy.getLockDelaySeconds());
    }

    @Test
    @DisplayName("availability can be queried without locking anything")
    void availabilityIsSideEffectFree() {
        LinuxScreenLockService service = new LinuxScreenLockService();
        // Whatever the answer on this machine, asking must be harmless and
        // must not throw on a desktop with no locker at all.
        boolean available = service.isAvailable();
        assertNotNull(service.describe());
        assertEquals(available, service.isAvailable(), "the probe should be stable");
    }

    @Test
    @DisplayName("describe() reports a mechanism name, never a raw path")
    void describeIsReadable() {
        LinuxScreenLockService service = new LinuxScreenLockService();
        String description = service.describe();
        assertFalse(description.startsWith("/"),
                "the settings screen should show a name, not a filesystem path: " + description);
    }

    @Test
    @DisplayName("no lock mechanism escalates privileges")
    void neverEscalatesPrivileges() throws Exception {
        // Reading the source is the honest way to assert this: the class must
        // never reach for sudo, pkexec, or anything that ends the session.
        java.nio.file.Path source = java.nio.file.Path.of(
                "src/main/java/com/ctrends/salahguardian/service/LinuxScreenLockService.java");
        if (!java.nio.file.Files.exists(source)) {
            return;   // running from a packaged build, nothing to inspect
        }
        String code = java.nio.file.Files.readString(source);
        for (String forbidden : new String[]{
                "sudo", "pkexec", "poweroff", "reboot", "shutdown",
                "loginctl terminate", "kill-session", "logout"}) {
            assertFalse(code.contains("\"" + forbidden),
                    "screen locking must never use '" + forbidden + "'");
        }
        assertTrue(code.contains("lock-session"), "logind should be the preferred mechanism");
    }
}
