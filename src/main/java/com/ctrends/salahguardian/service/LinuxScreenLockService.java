package com.ctrends.salahguardian.service;

import com.ctrends.salahguardian.utils.DesktopEnvironment;
import com.ctrends.salahguardian.utils.ProcessResult;
import com.ctrends.salahguardian.utils.ProcessRunner;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Locks the session through whichever of the standard desktop mechanisms is
 * present, trying them in order of reliability.
 *
 * <h2>None of these need root</h2>
 * Every command here acts on the caller's own session:
 * <ol>
 *   <li>{@code loginctl lock-session} - systemd-logind. The most reliable
 *       option and the only one that works uniformly across Wayland and X11,
 *       GNOME, KDE and everything else logind manages.</li>
 *   <li>{@code org.gnome.ScreenSaver.Lock} over the session bus - GNOME.</li>
 *   <li>{@code org.freedesktop.ScreenSaver.Lock} - the cross-desktop
 *       equivalent, honoured by KDE, XFCE, MATE and Cinnamon.</li>
 *   <li>{@code xdg-screensaver lock} - the lowest common denominator.</li>
 * </ol>
 *
 * <p>Deliberately absent: anything that would need a privileged helper, and
 * anything that suspends, logs out or powers off. This class can make the user
 * type their password again; it can never cost them their session or their
 * unsaved work.</p>
 *
 * <h2>Binary resolution</h2>
 * Executables are resolved from a fixed list of system directories rather than
 * through {@code $PATH}. Locking is a security-relevant action, and a writable
 * {@code $PATH} entry would otherwise let a local attacker substitute the
 * binary that runs five times a day.
 *
 * @author CTrends Software
 */
@Singleton
public class LinuxScreenLockService implements ScreenLockService {

    private static final Logger LOG = LoggerFactory.getLogger(LinuxScreenLockService.class);

    /**
     * Where a legitimate system binary lives. {@code $PATH} is not consulted,
     * because a writable entry on it would let a local attacker substitute a
     * binary that runs unattended five times a day.
     *
     * <p>Inside a snap the package's own binaries are under {@code $SNAP}, and
     * that prefix is trusted for the same reason {@code /usr/bin} is: it is
     * read-only and owned by the packaging, not by the user.</p>
     */
    private static List<String> trustedBinDirs() {
        List<String> dirs = new ArrayList<>();
        DesktopEnvironment.snapRoot().ifPresent(snap -> {
            dirs.add(snap + "/usr/bin");
            dirs.add(snap + "/bin");
        });
        dirs.add("/usr/bin");
        dirs.add("/bin");
        dirs.add("/usr/local/bin");
        return dirs;
    }

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(6);

    private volatile List<List<String>> strategies;
    private volatile String activeDescription = "none";

    @Override
    public boolean isAvailable() {
        return !candidates().isEmpty();
    }

    @Override
    public boolean lock() {
        List<List<String>> available = candidates();
        if (available.isEmpty()) {
            LOG.warn("No screen lock mechanism is available on this desktop - "
                    + "the prayer reminder will be shown without locking");
            return false;
        }
        for (List<String> command : available) {
            ProcessResult result = ProcessRunner.run(LOCK_TIMEOUT, command);
            if (result.isSuccess()) {
                activeDescription = command.get(0);
                LOG.info("Session locked via {}", describeCommand(command));
                return true;
            }
            LOG.debug("Lock attempt via {} failed (exit {}): {}",
                    describeCommand(command), result.exitCode(), result.stderr().trim());
        }
        LOG.error("Every screen lock mechanism failed - the session stays unlocked");
        return false;
    }

    @Override
    public String describe() {
        List<List<String>> available = candidates();
        return available.isEmpty() ? "unavailable" : describeCommand(available.get(0));
    }

    /**
     * Builds the ordered list of lock commands that actually exist here.
     *
     * <p>Computed once and cached: the set of installed binaries does not
     * change while the application is running.</p>
     *
     * @return the usable commands, most reliable first
     */
    private List<List<String>> candidates() {
        List<List<String>> cached = strategies;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (strategies == null) {
                strategies = buildStrategies();
                LOG.info("Screen lock mechanisms available: {}",
                        strategies.isEmpty() ? "none"
                                : strategies.stream().map(this::describeCommand).toList());
            }
            return strategies;
        }
    }

    private List<List<String>> buildStrategies() {
        List<List<String>> commands = new ArrayList<>();

        resolve("loginctl").ifPresent(bin ->
                commands.add(List.of(bin, "lock-session")));

        // logind over plain D-Bus. Equivalent to `loginctl lock-session` but
        // without needing the systemd package, which matters inside a snap
        // where staging systemd is heavy and prone to breaking the build.
        resolve("dbus-send").ifPresent(bin -> commands.add(List.of(bin,
                "--system", "--type=method_call",
                "--dest=org.freedesktop.login1",
                "/org/freedesktop/login1/session/self",
                "org.freedesktop.login1.Session.Lock")));

        resolve("busctl").ifPresent(bin -> commands.add(List.of(bin,
                "--user", "call",
                "org.gnome.ScreenSaver", "/org/gnome/ScreenSaver",
                "org.gnome.ScreenSaver", "Lock")));

        resolve("dbus-send").ifPresent(bin -> commands.add(List.of(bin,
                "--session", "--type=method_call",
                "--dest=org.freedesktop.ScreenSaver",
                "/org/freedesktop/ScreenSaver",
                "org.freedesktop.ScreenSaver.Lock")));

        resolve("xdg-screensaver").ifPresent(bin ->
                commands.add(List.of(bin, "lock")));

        return List.copyOf(commands);
    }

    /**
     * Finds an executable in a trusted system directory.
     *
     * @param executable the bare binary name
     * @return its absolute path when present in a trusted location
     */
    private Optional<String> resolve(String executable) {
        if (executable == null || executable.indexOf('/') >= 0) {
            return Optional.empty();
        }
        for (String directory : trustedBinDirs()) {
            Path candidate = Path.of(directory, executable);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate.toString());
            }
        }
        return Optional.empty();
    }

    private String describeCommand(List<String> command) {
        String binary = Path.of(command.get(0)).getFileName().toString();
        return command.size() > 1 && !command.get(1).startsWith("--")
                ? binary + " " + command.get(1)
                : binary;
    }

    /**
     * @return the mechanism that last succeeded, for diagnostics
     */
    public String lastUsed() {
        return activeDescription;
    }
}
