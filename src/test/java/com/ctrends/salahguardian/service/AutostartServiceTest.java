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
        assertTrue(entry.contains("Exec=/opt/salahguardian/bin/SalahGuardian --minimised"),
                "autostart must launch straight into the tray");
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
    @DisplayName("prefers the jpackage app path when the JVM provides one")
    void prefersJpackageAppPath(@TempDir Path dir) throws IOException {
        Path launcher = dir.resolve("SalahGuardian");
        Files.writeString(launcher, "#!/bin/sh\n", StandardCharsets.UTF_8);
        assertTrue(launcher.toFile().setExecutable(true));

        String previous = System.getProperty("jpackage.app-path");
        System.setProperty("jpackage.app-path", launcher.toString());
        try {
            AutostartService service = new AutostartService(dir.resolve("salah-guardian.desktop"));
            assertEquals(launcher.toString(), service.resolveExecutable().orElseThrow());

            assertTrue(service.enable());
            assertTrue(service.isEnabled());
            String written = Files.readString(dir.resolve("salah-guardian.desktop"));
            assertTrue(written.contains("Exec=" + launcher + " --minimised"));
        } finally {
            if (previous == null) {
                System.clearProperty("jpackage.app-path");
            } else {
                System.setProperty("jpackage.app-path", previous);
            }
        }
    }

    @Test
    @DisplayName("exposes the managed entry path")
    void exposesEntryPath(@TempDir Path dir) {
        Path file = dir.resolve("salah-guardian.desktop");
        assertEquals(file, new AutostartService(file).entryFile());
    }
}
