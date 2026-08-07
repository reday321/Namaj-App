package com.ctrends.salahguardian.notification;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terminal backend of the notification chain: writes the message to the log.
 *
 * <p>It always succeeds, which guarantees that
 * {@link CompositeNotificationService} never reports total failure and that a
 * headless or misconfigured desktop still leaves a durable record of every
 * reminder in {@code ~/.local/share/salahguardian/logs/}.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class LoggingNotificationService implements NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingNotificationService.class);

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean send(NotificationRequest request) {
        LOG.info("[NOTIFICATION] {} - {}", request.title(),
                request.body().isEmpty() ? "(no body)" : request.body());
        return true;
    }

    @Override
    public String describe() {
        return "log file";
    }
}
