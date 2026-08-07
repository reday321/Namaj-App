package com.ctrends.salahguardian.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ConfigPaths}.
 *
 * <p>The XDG environment variables cannot be changed from inside a running JVM,
 * so these tests assert the layout relative to whatever roots the current
 * environment yields - which is exactly the contract callers depend on.</p>
 */
class ConfigPathsTest {

    @Test
    @DisplayName("places the config file under the config root")
    void configFileLayout() {
        Path file = ConfigPaths.configFile();
        assertEquals("config.json", file.getFileName().toString());
        assertEquals(ConfigPaths.APP_DIR_NAME, file.getParent().getFileName().toString());
        assertTrue(file.startsWith(ConfigPaths.configRoot()));
    }

    @Test
    @DisplayName("places the logs under the data root, matching the documented path")
    void logDirectoryLayout() {
        Path logs = ConfigPaths.logDirectory();
        assertEquals("logs", logs.getFileName().toString());
        assertEquals(ConfigPaths.APP_DIR_NAME, logs.getParent().getFileName().toString());
        assertTrue(logs.startsWith(ConfigPaths.dataRoot()));
    }

    @Test
    @DisplayName("defaults to the documented locations when XDG variables are unset")
    void defaultsMatchDocumentation() {
        // These are only the defaults; a set XDG_CONFIG_HOME legitimately wins.
        if (System.getenv("XDG_CONFIG_HOME") == null) {
            assertEquals(ConfigPaths.home().resolve(".config").resolve("salahguardian")
                    .resolve("config.json"), ConfigPaths.configFile());
        }
        if (System.getenv("XDG_DATA_HOME") == null) {
            assertEquals(ConfigPaths.home().resolve(".local").resolve("share")
                    .resolve("salahguardian").resolve("logs"), ConfigPaths.logDirectory());
        }
    }

    @Test
    @DisplayName("points the autostart entry at the freedesktop location")
    void autostartLayout() {
        Path entry = ConfigPaths.autostartEntry();
        assertEquals(ConfigPaths.DESKTOP_ENTRY_NAME, entry.getFileName().toString());
        assertEquals("autostart", entry.getParent().getFileName().toString());
        assertTrue(entry.startsWith(ConfigPaths.configRoot()));
    }
}
