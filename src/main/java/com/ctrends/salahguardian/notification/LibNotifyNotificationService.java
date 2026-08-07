package com.ctrends.salahguardian.notification;

import com.ctrends.salahguardian.utils.ProcessResult;
import com.ctrends.salahguardian.utils.ProcessRunner;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Primary notification backend, driving libnotify through the
 * {@code notify-send} command line tool.
 *
 * <p>{@code notify-send} ships in {@code libnotify-bin} on Debian derivatives
 * and in {@code libnotify} on Fedora, and speaks the standard
 * {@code org.freedesktop.Notifications} D-Bus interface. Using the CLI rather
 * than a D-Bus binding keeps the application free of native dependencies while
 * behaving identically on GNOME, KDE, XFCE, Cinnamon and MATE.</p>
 *
 * <h2>Compatibility notes</h2>
 * <ul>
 *   <li>{@code --app-name} exists in every release since 0.7.0; older builds
 *       simply ignore unknown long options, and a failure here degrades to the
 *       next backend rather than being fatal.</li>
 *   <li>A leading {@code -} in the summary would be parsed as an option, so
 *       {@code --} terminates the option list before the positional
 *       arguments.</li>
 * </ul>
 *
 * <p>Availability is probed once and cached, because {@code which} on every
 * notification would be wasteful.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class LibNotifyNotificationService implements NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(LibNotifyNotificationService.class);

    private static final String BINARY = "notify-send";
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    private volatile Boolean available;

    @Override
    public boolean isAvailable() {
        Boolean cached = available;
        if (cached == null) {
            synchronized (this) {
                if (available == null) {
                    available = ProcessRunner.isCommandAvailable(BINARY);
                    if (available) {
                        LOG.info("Desktop notifications will be delivered through {}", BINARY);
                    } else {
                        LOG.warn("{} is not installed - install 'libnotify-bin' (Debian/Ubuntu/Mint) "
                                + "or 'libnotify' (Fedora) for desktop notifications", BINARY);
                    }
                }
                cached = available;
            }
        }
        return cached;
    }

    @Override
    public boolean send(NotificationRequest request) {
        if (!isAvailable()) {
            return false;
        }
        ProcessResult result = ProcessRunner.run(SEND_TIMEOUT, buildCommand(request));
        if (!result.isSuccess()) {
            LOG.warn("{} failed (exit {}, timedOut={}): {}", BINARY, result.exitCode(),
                    result.timedOut(), result.stderr().trim());
            // The daemon may have gone away, e.g. after a desktop session
            // restart. Re-probe on the next attempt.
            if (result.timedOut()) {
                available = null;
            }
            return false;
        }
        LOG.debug("Notification delivered: {}", request.title());
        return true;
    }

    /**
     * Assembles the {@code notify-send} argument vector.
     *
     * <p>Package private so the unit tests can assert the exact flags without
     * spawning a process.</p>
     *
     * @param request the notification to render
     * @return the full command line
     */
    List<String> buildCommand(NotificationRequest request) {
        List<String> command = new ArrayList<>();
        command.add(BINARY);
        command.add("--app-name=" + NotificationRequest.APP_NAME);
        command.add("--urgency=" + request.urgency().flagValue());
        if (!request.timeout().isZero() && !request.timeout().isNegative()) {
            command.add("--expire-time=" + request.timeout().toMillis());
        }
        if (!request.iconName().isEmpty()) {
            command.add("--icon=" + request.iconName());
        }
        if (!request.category().isEmpty()) {
            command.add("--category=" + request.category());
        }
        // Guard against a summary or body that begins with a dash.
        command.add("--");
        command.add(request.title());
        if (!request.body().isEmpty()) {
            command.add(request.body());
        }
        return command;
    }

    @Override
    public String describe() {
        return "libnotify (" + BINARY + ")";
    }
}
