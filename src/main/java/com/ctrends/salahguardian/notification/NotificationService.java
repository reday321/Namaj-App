package com.ctrends.salahguardian.notification;

/**
 * Delivers desktop notifications.
 *
 * <p>Implementations must never throw and must never block for more than a
 * couple of seconds: notifications are dispatched from the scheduler thread,
 * which also has prayer times to keep track of.</p>
 *
 * @author CTrends Software
 */
public interface NotificationService {

    /**
     * @return {@code true} when this backend can currently deliver messages
     */
    boolean isAvailable();

    /**
     * Attempts to display a notification.
     *
     * @param request the message to show
     * @return {@code true} when the message was handed to the desktop
     *         successfully
     */
    boolean send(NotificationRequest request);

    /**
     * @return a short label used in log output
     */
    String describe();
}
