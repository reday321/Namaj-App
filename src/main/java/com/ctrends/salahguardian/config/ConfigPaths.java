package com.ctrends.salahguardian.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves every filesystem location the application writes to, honouring the
 * XDG Base Directory specification and falling back to the literal
 * {@code ~/.config} and {@code ~/.local/share} paths when the environment
 * variables are absent - which is what Ubuntu, Mint, Debian and Fedora do by
 * default.
 *
 * <p>This class is intentionally free of side effects: it never creates
 * directories, so it stays trivially testable.</p>
 *
 * @author CTrends Software
 */
public final class ConfigPaths {

    /** Directory name used under both the config and data roots. */
    public static final String APP_DIR_NAME = "salahguardian";

    /** File name of the autostart entry. */
    public static final String DESKTOP_ENTRY_NAME = "salah-guardian.desktop";

    private ConfigPaths() {
        // utility class
    }

    /**
     * @return the user's home directory
     */
    public static Path home() {
        return Paths.get(System.getProperty("user.home", "/tmp"));
    }

    /**
     * @return {@code $XDG_CONFIG_HOME} or {@code ~/.config}
     */
    public static Path configRoot() {
        return fromEnvOrHome("XDG_CONFIG_HOME", ".config");
    }

    /**
     * @return {@code $XDG_DATA_HOME} or {@code ~/.local/share}
     */
    public static Path dataRoot() {
        String env = System.getenv("XDG_DATA_HOME");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        return home().resolve(".local").resolve("share");
    }

    /**
     * @return {@code ~/.config/salahguardian}
     */
    public static Path configDirectory() {
        return configRoot().resolve(APP_DIR_NAME);
    }

    /**
     * @return {@code ~/.config/salahguardian/config.json}
     */
    public static Path configFile() {
        return configDirectory().resolve("config.json");
    }

    /**
     * @return {@code ~/.local/share/salahguardian}
     */
    public static Path dataDirectory() {
        return dataRoot().resolve(APP_DIR_NAME);
    }

    /**
     * @return {@code ~/.local/share/salahguardian/logs}
     */
    public static Path logDirectory() {
        return dataDirectory().resolve("logs");
    }

    /**
     * @return {@code ~/.config/autostart}, where freedesktop compliant desktops
     *         look for entries to launch after login
     */
    public static Path autostartDirectory() {
        return configRoot().resolve("autostart");
    }

    /**
     * @return {@code ~/.config/autostart/salah-guardian.desktop}
     */
    public static Path autostartEntry() {
        return autostartDirectory().resolve(DESKTOP_ENTRY_NAME);
    }

    private static Path fromEnvOrHome(String envVar, String relative) {
        String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        return home().resolve(relative);
    }
}
