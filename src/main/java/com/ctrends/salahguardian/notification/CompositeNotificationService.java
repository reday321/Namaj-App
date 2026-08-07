package com.ctrends.salahguardian.notification;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Delivers each notification through the first backend that accepts it.
 *
 * <p>Order: libnotify, then the tray balloon, then the log file. Because the
 * log backend never fails, a reminder is never lost outright - at worst it is
 * recorded rather than displayed, which is exactly the graceful degradation the
 * "notification unavailable" requirement calls for.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class CompositeNotificationService implements NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(CompositeNotificationService.class);

    private final List<NotificationService> backends;

    /**
     * Production constructor establishing the standard fallback order.
     *
     * @param libNotify primary backend
     * @param tray      secondary backend
     * @param logging   terminal backend, always succeeds
     */
    @Inject
    public CompositeNotificationService(LibNotifyNotificationService libNotify,
                                        TrayNotificationService tray,
                                        LoggingNotificationService logging) {
        this(List.of(libNotify, tray, logging));
    }

    /**
     * Test friendly constructor accepting an arbitrary chain.
     *
     * @param backends the backends to try, in order
     */
    public CompositeNotificationService(List<NotificationService> backends) {
        this.backends = List.copyOf(backends);
    }

    @Override
    public boolean isAvailable() {
        return backends.stream().anyMatch(NotificationService::isAvailable);
    }

    @Override
    public boolean send(NotificationRequest request) {
        for (NotificationService backend : backends) {
            try {
                if (backend.isAvailable() && backend.send(request)) {
                    return true;
                }
            } catch (RuntimeException e) {
                LOG.warn("Notification backend '{}' threw - trying the next one",
                        backend.describe(), e);
            }
        }
        LOG.error("No notification backend accepted the message: {}", request.title());
        return false;
    }

    @Override
    public String describe() {
        return backends.stream().map(NotificationService::describe)
                .reduce((a, b) -> a + " -> " + b).orElse("none");
    }
}
