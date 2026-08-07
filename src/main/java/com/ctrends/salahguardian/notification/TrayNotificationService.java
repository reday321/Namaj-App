package com.ctrends.salahguardian.notification;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.TrayIcon;
import java.util.function.Supplier;

/**
 * Secondary notification backend that uses the AWT system tray balloon.
 *
 * <p>This covers the case where {@code notify-send} is absent - a minimal
 * Debian install, or a distribution that split libnotify's CLI into an optional
 * package - but a tray host is present. It is strictly a fallback: balloon
 * messages support neither urgency nor icons.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class TrayNotificationService implements NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(TrayNotificationService.class);

    private final Supplier<TrayIcon> trayIconSupplier;

    /**
     * @param trayIconSupplier yields the live tray icon, or {@code null} when
     *                         no tray has been installed
     */
    @Inject
    public TrayNotificationService(Supplier<TrayIcon> trayIconSupplier) {
        this.trayIconSupplier = trayIconSupplier;
    }

    @Override
    public boolean isAvailable() {
        try {
            return trayIconSupplier.get() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean send(NotificationRequest request) {
        TrayIcon icon = trayIconSupplier.get();
        if (icon == null) {
            return false;
        }
        try {
            TrayIcon.MessageType type = request.urgency() == NotificationUrgency.CRITICAL
                    ? TrayIcon.MessageType.WARNING
                    : TrayIcon.MessageType.INFO;
            icon.displayMessage(request.title(), request.body(), type);
            LOG.debug("Notification delivered through the tray balloon: {}", request.title());
            return true;
        } catch (RuntimeException e) {
            LOG.debug("Tray balloon notification failed", e);
            return false;
        }
    }

    @Override
    public String describe() {
        return "system tray balloon";
    }
}
