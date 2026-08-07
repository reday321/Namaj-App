package com.ctrends.salahguardian.controller;

import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.model.ReminderKind;
import com.ctrends.salahguardian.prayer.PrayerScheduleService;
import com.ctrends.salahguardian.service.ReminderEvent;
import com.ctrends.salahguardian.view.FocusOverlayView;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;

/**
 * Decides when the fullscreen focus overlay appears and guarantees that only
 * one is ever on screen.
 *
 * <p>Separating this from {@link ApplicationController} keeps the rule - "show
 * the overlay at prayer time, but only if the user asked for it, and never
 * twice" - in one small, readable place.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class FocusModeController {

    private static final Logger LOG = LoggerFactory.getLogger(FocusModeController.class);

    private final ConfigService configService;
    private final PrayerScheduleService scheduleService;

    private FocusOverlayView overlay;
    private Window owner;

    /**
     * @param configService   tells whether focus mode is enabled
     * @param scheduleService supplies the clock and date shown on the overlay
     */
    @Inject
    public FocusModeController(ConfigService configService, PrayerScheduleService scheduleService) {
        this.configService = configService;
        this.scheduleService = scheduleService;
    }

    /**
     * Sets the window the overlay is owned by, so it inherits the application's
     * taskbar identity.
     *
     * @param owner the dashboard window, may be {@code null}
     */
    public void setOwner(Window owner) {
        this.owner = owner;
    }

    /**
     * Reacts to a reminder by opening the overlay when appropriate.
     *
     * <p>Only {@link ReminderKind#PRAYER_START} triggers it: an advance warning
     * is meant to let the user finish what they are doing, so covering their
     * screen five minutes early would defeat its purpose.</p>
     *
     * @param event the reminder that just fired
     */
    public void handleReminder(ReminderEvent event) {
        if (event.kind() != ReminderKind.PRAYER_START) {
            return;
        }
        if (!configService.get().isFocusModeEnabled()) {
            LOG.debug("Focus mode is disabled - not showing the overlay for {}",
                    event.prayer().name().displayName());
            return;
        }
        Platform.runLater(() -> show(event.prayer()));
    }

    /**
     * Opens the overlay for a prayer, reusing the existing window when one is
     * already on screen.
     *
     * <p>Must be called on the JavaFX application thread.</p>
     *
     * @param prayer the prayer to announce
     */
    public void show(PrayerTime prayer) {
        if (overlay != null && overlay.isShowing()) {
            LOG.debug("Focus overlay is already visible - ignoring the request for {}",
                    prayer.name().displayName());
            return;
        }
        if (overlay == null) {
            overlay = new FocusOverlayView(configService, scheduleService, owner);
            overlay.setOnClosed(skipped ->
                    LOG.info("Focus overlay closed ({})", skipped ? "skipped" : "completed"));
        }
        boolean friday = prayer.time().getDayOfWeek() == DayOfWeek.FRIDAY;
        overlay.show(prayer, friday);
    }

    /**
     * Closes the overlay if it is open, e.g. while the application shuts down.
     */
    public void closeIfOpen() {
        if (overlay != null && overlay.isShowing()) {
            overlay.close(false);
        }
    }
}
