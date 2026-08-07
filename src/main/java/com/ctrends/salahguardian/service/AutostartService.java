package com.ctrends.salahguardian.service;

import com.ctrends.salahguardian.config.ConfigPaths;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Manages the freedesktop autostart entry that launches the application after
 * login.
 *
 * <p>Writing {@code ~/.config/autostart/salah-guardian.desktop} is the portable
 * mechanism honoured by GNOME, KDE Plasma, XFCE, Cinnamon, MATE and LXQt alike,
 * which is why it is preferred here over a systemd user unit: it needs no
 * {@code systemctl}, works on distributions without systemd, and the user can
 * see and remove it from their desktop's own "Startup Applications" tool.</p>
 *
 * <h2>Finding the executable</h2>
 * The {@code Exec=} line must point at something that actually exists after
 * installation. Candidates are tried in order:
 * <ol>
 *   <li>{@code jpackage.app-path} - set by the JVM inside any jpackage produced
 *       launcher, and therefore exact;</li>
 *   <li>well known install locations of the .deb / .rpm packages;</li>
 *   <li>the launcher script from {@code installDist}, for developer runs.</li>
 * </ol>
 * When none can be found the entry is not written and the failure is reported,
 * rather than leaving a broken autostart file behind.
 *
 * @author CTrends Software
 */
@Singleton
public class AutostartService {

    private static final Logger LOG = LoggerFactory.getLogger(AutostartService.class);

    /** System property jpackage sets on every generated launcher. */
    private static final String JPACKAGE_APP_PATH = "jpackage.app-path";

    /**
     * Locations the native packages install the launcher into.
     *
     * <p>The first entry is what the generated .deb and .rpm actually use:
     * jpackage builds the directory from {@code --linux-package-name}
     * ({@code salah-guardian}) and the launcher from {@code --name}
     * ({@code SalahGuardian}). The rest cover the portable installer and any
     * distribution package that puts a wrapper on the PATH.</p>
     */
    private static final String[] KNOWN_INSTALL_PATHS = {
            "/opt/salah-guardian/bin/SalahGuardian",
            "/opt/salahguardian/bin/SalahGuardian",
            "/opt/SalahGuardian/bin/SalahGuardian",
            "/usr/bin/salah-guardian",
            "/usr/local/bin/salah-guardian"
    };

    private final Path entryFile;

    /**
     * Creates a service writing to the standard autostart location.
     */
    public AutostartService() {
        this(ConfigPaths.autostartEntry());
    }

    /**
     * Creates a service writing to an explicit file, used by the unit tests.
     *
     * @param entryFile the desktop entry to manage
     */
    public AutostartService(Path entryFile) {
        this.entryFile = entryFile;
    }

    /**
     * @return {@code true} when the autostart entry currently exists
     */
    public boolean isEnabled() {
        return Files.isRegularFile(entryFile);
    }

    /**
     * Creates or removes the autostart entry.
     *
     * @param enabled {@code true} to start on login
     * @return {@code true} when the filesystem now matches the request
     */
    public boolean setEnabled(boolean enabled) {
        return enabled ? enable() : disable();
    }

    /**
     * Writes the desktop entry.
     *
     * @return {@code true} on success
     */
    public boolean enable() {
        Optional<String> executable = resolveExecutable();
        if (executable.isEmpty()) {
            LOG.warn("Start on login could not be enabled: the application executable was not "
                    + "found. This is expected when running from a development build - install "
                    + "the .deb/.rpm package and try again.");
            return false;
        }
        try {
            Files.createDirectories(entryFile.getParent());
            Files.writeString(entryFile, desktopEntryContent(executable.get()),
                    StandardCharsets.UTF_8);
            LOG.info("Autostart entry written to {}", entryFile);
            return true;
        } catch (IOException e) {
            LOG.error("Could not write the autostart entry {}", entryFile, e);
            return false;
        }
    }

    /**
     * Removes the desktop entry.
     *
     * @return {@code true} when the entry is gone afterwards
     */
    public boolean disable() {
        try {
            boolean removed = Files.deleteIfExists(entryFile);
            if (removed) {
                LOG.info("Autostart entry removed from {}", entryFile);
            }
            return true;
        } catch (IOException e) {
            LOG.error("Could not remove the autostart entry {}", entryFile, e);
            return false;
        }
    }

    /**
     * Locates the command the autostart entry should run.
     *
     * @return the absolute path of an executable launcher, when one exists
     */
    public Optional<String> resolveExecutable() {
        String jpackagePath = System.getProperty(JPACKAGE_APP_PATH);
        if (jpackagePath != null && !jpackagePath.isBlank() && isExecutable(Path.of(jpackagePath))) {
            return Optional.of(jpackagePath);
        }
        for (String candidate : KNOWN_INSTALL_PATHS) {
            Path path = Path.of(candidate);
            if (isExecutable(path)) {
                return Optional.of(path.toString());
            }
        }
        // A --user install of the portable image lives under the home directory.
        Path userInstall = ConfigPaths.home()
                .resolve(".local/lib/salah-guardian/bin/SalahGuardian");
        if (isExecutable(userInstall)) {
            return Optional.of(userInstall.toString());
        }
        return developmentLauncher();
    }

    /**
     * Finds the {@code installDist} launcher script when running from a source
     * checkout, so the feature is at least testable during development.
     */
    private Optional<String> developmentLauncher() {
        String userDir = System.getProperty("user.dir", "");
        if (userDir.isBlank()) {
            return Optional.empty();
        }
        Path script = Path.of(userDir, "build", "install", "salah-guardian", "bin", "salah-guardian");
        return isExecutable(script) ? Optional.of(script.toString()) : Optional.empty();
    }

    private static boolean isExecutable(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }

    /**
     * Builds the desktop entry body.
     *
     * <p>Package private so its contents can be asserted in a unit test.</p>
     *
     * @param executable the command to run at login
     * @return a complete freedesktop desktop entry
     */
    String desktopEntryContent(String executable) {
        return """
                [Desktop Entry]
                Type=Application
                Version=1.0
                Name=Salah Guardian
                GenericName=Prayer Times
                Comment=Reminds you of prayer times based on your location
                Exec=%s --minimised
                Icon=salah-guardian
                Terminal=false
                Categories=Utility;Clock;
                Keywords=prayer;salah;namaz;adhan;islam;qibla;
                StartupNotify=false
                X-GNOME-Autostart-enabled=true
                X-GNOME-Autostart-Delay=10
                X-KDE-autostart-after=panel
                """.formatted(executable);
    }

    /**
     * @return the path of the managed entry, for logging and tests
     */
    public Path entryFile() {
        return entryFile;
    }
}
