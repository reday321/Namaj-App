package com.ctrends.salahguardian.notification;

import java.time.Duration;
import java.util.Objects;

/**
 * A desktop notification to be delivered, expressed independently of any
 * particular backend.
 *
 * @param title     summary line, e.g. {@code "🕌 Time for Maghrib"}
 * @param body      detail line, may be empty
 * @param urgency   how insistent the desktop should be
 * @param timeout   how long it should stay on screen; {@link Duration#ZERO}
 *                  means "let the desktop decide"
 * @param iconName  freedesktop icon name or absolute path, may be empty
 * @param category  freedesktop category hint, may be empty
 * @author CTrends Software
 */
public record NotificationRequest(String title, String body, NotificationUrgency urgency,
                                  Duration timeout, String iconName, String category) {

    /** Application identity reported to the notification daemon. */
    public static final String APP_NAME = "Salah Guardian";

    public NotificationRequest {
        title = title == null ? "" : title;
        body = body == null ? "" : body;
        urgency = Objects.requireNonNullElse(urgency, NotificationUrgency.NORMAL);
        timeout = Objects.requireNonNullElse(timeout, Duration.ZERO);
        iconName = iconName == null ? "" : iconName;
        category = category == null ? "" : category;
    }

    /**
     * Builds a normal-urgency notification with default styling.
     *
     * @param title summary line
     * @param body  detail line
     * @return the request
     */
    public static NotificationRequest info(String title, String body) {
        return new NotificationRequest(title, body, NotificationUrgency.NORMAL,
                Duration.ofSeconds(10), "appointment-soon", "im.received");
    }

    /**
     * Builds a critical notification that persists until dismissed.
     *
     * @param title summary line
     * @param body  detail line
     * @return the request
     */
    public static NotificationRequest critical(String title, String body) {
        return new NotificationRequest(title, body, NotificationUrgency.CRITICAL,
                Duration.ZERO, "alarm", "alarm");
    }
}
