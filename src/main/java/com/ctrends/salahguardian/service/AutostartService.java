package com.ctrends.salahguardian.service;

import com.ctrends.salahguardian.config.ConfigPaths;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
     * Directory prefixes a legitimately installed launcher may live under.
     *
     * <p>Anything outside these is refused. Without this, the working directory
     * could dictate what runs at every login.</p>
     */
    private static final List<Path> TRUSTED_PREFIXES = List.of(
            Path.of("/opt"), Path.of("/usr/bin"), Path.of("/usr/local/bin"),
            Path.of("/usr/lib"), Path.of("/usr/share"), Path.of("/snap"),
            ConfigPaths.home().resolve(".local"));

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
            Path parent = entryFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
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
        if (jpackagePath != null && !jpackagePath.isBlank()) {
            Optional<String> validated = validate(Path.of(jpackagePath));
            if (validated.isPresent()) {
                return validated;
            }
        }
        for (String candidate : KNOWN_INSTALL_PATHS) {
            Optional<String> validated = validate(Path.of(candidate));
            if (validated.isPresent()) {
                return validated;
            }
        }
        // A --user install of the portable image lives under the home directory.
        return validate(ConfigPaths.home().resolve(".local/lib/salah-guardian/bin/SalahGuardian"));
    }

    /**
     * Accepts a candidate launcher only if it is genuinely safe to run at login.
     *
     * <p>Three checks, each closing a distinct hole:</p>
     * <ul>
     *   <li><b>Canonicalisation.</b> {@code toRealPath} resolves symlinks, so a
     *       link pointing out of a trusted directory cannot smuggle a target
     *       past the prefix check.</li>
     *   <li><b>Prefix allow-list.</b> Only system install locations and the
     *       user's own {@code ~/.local} qualify. This is what removes the
     *       former working-directory candidate: a launcher found relative to
     *       {@code user.dir} meant that running the application from a
     *       directory an attacker controlled, then enabling "start on login",
     *       wrote an autostart entry pointing at their binary - persistent
     *       code execution on every subsequent login.</li>
     *   <li><b>Control characters.</b> Newlines are legal in Linux filenames,
     *       and a desktop entry is line-oriented, so a path containing one
     *       could inject additional keys such as a second {@code Exec=}.</li>
     * </ul>
     *
     * @param candidate the path to check
     * @return its canonical form when it passes every check
     */
    private Optional<String> validate(Path candidate) {
        try {
            Path real = candidate.toRealPath();
            if (!Files.isRegularFile(real) || !Files.isExecutable(real)) {
                return Optional.empty();
            }
            if (TRUSTED_PREFIXES.stream().noneMatch(real::startsWith)) {
                LOG.warn("Refusing an autostart target outside a trusted location: {}", real);
                return Optional.empty();
            }
            String path = real.toString();
            if (path.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
                LOG.warn("Refusing an autostart target containing control characters");
                return Optional.empty();
            }
            return Optional.of(path);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Quotes a path for the {@code Exec} key of a desktop entry.
     *
     * <p>The freedesktop specification reserves a set of characters inside a
     * quoted argument; an unquoted path also breaks outright on the first
     * space, which any {@code ~/.local} install can easily contain.</p>
     *
     * @param path the executable path
     * @return the path quoted and escaped per the specification
     */
    static String quoteExec(String path) {
        return '"' + path.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("`", "\\`")
                .replace("$", "\\$") + '"';
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
                """.formatted(quoteExec(executable));
    }

    /**
     * @return the path of the managed entry, for logging and tests
     */
    public Path entryFile() {
        return entryFile;
    }
}
