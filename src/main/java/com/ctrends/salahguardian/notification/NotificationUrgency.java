package com.ctrends.salahguardian.notification;

import java.util.Locale;

/**
 * Urgency levels defined by the freedesktop desktop notification specification.
 *
 * @author CTrends Software
 */
public enum NotificationUrgency {

    /** Informational, may be silently dropped by do-not-disturb modes. */
    LOW,

    /** Default level used by the advance warning. */
    NORMAL,

    /**
     * Stays on screen until dismissed on most desktops; used for the
     * "it is time to pray" message.
     */
    CRITICAL;

    /**
     * @return the value expected by the {@code --urgency} flag of notify-send
     */
    public String flagValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
