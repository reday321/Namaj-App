package com.ctrends.salahguardian.viewmodel;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.i18n.Messages;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.prayer.PrayerScheduleService;
import com.ctrends.salahguardian.utils.TimeUtils;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;

/**
 * View model behind the fullscreen prayer focus overlay.
 *
 * <p>Owns the countdown that decides when the overlay closes itself. The view
 * supplies a callback that is invoked once the countdown reaches zero; the view
 * model never touches a {@code Stage}, which keeps it testable and keeps the
 * window handling in one place.</p>
 *
 * @author CTrends Software
 */
public class FocusOverlayViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(FocusOverlayViewModel.class);

    private final ConfigService configService;
    private final PrayerScheduleService scheduleService;

    private final StringProperty prayerName = new SimpleStringProperty("");
    private final StringProperty arabicName = new SimpleStringProperty("");
    private final StringProperty prayerTime = new SimpleStringProperty("");
    private final StringProperty countdown = new SimpleStringProperty("5:00");
    private final StringProperty currentTime = new SimpleStringProperty("");
    private final StringProperty gregorianDate = new SimpleStringProperty("");
    private final StringProperty hijriDate = new SimpleStringProperty("");
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private final StringProperty lockCountdown = new SimpleStringProperty("");
    private final BooleanProperty lockPending = new SimpleBooleanProperty(false);

    private Timeline ticker;
    private Runnable onFinished = () -> { };
    private Runnable onLockDue = () -> { };
    private int totalSeconds;
    private int remainingSeconds;
    private int lockInSeconds = -1;

    /**
     * @param configService   supplies the overlay duration and clock format
     * @param scheduleService supplies the current time and date
     */
    public FocusOverlayViewModel(ConfigService configService,
                                 PrayerScheduleService scheduleService) {
        this.configService = configService;
        this.scheduleService = scheduleService;
    }

    /**
     * Prepares the overlay for a given prayer and starts the countdown.
     *
     * @param prayer     the prayer being announced
     * @param friday     whether today is a Friday
     * @param onFinished invoked on the JavaFX thread when the countdown expires
     */
    public void begin(PrayerTime prayer, boolean friday, Runnable onFinished) {
        begin(prayer, friday, onFinished, () -> { }, false);
    }

    /**
     * Prepares the overlay, optionally arming the screen lock countdown.
     *
     * @param prayer     the prayer being announced
     * @param friday     whether today is a Friday
     * @param onFinished invoked when the overlay's own countdown expires
     * @param onLockDue  invoked when the lock grace period expires
     * @param armLock    whether to count down to a screen lock at all
     */
    public void begin(PrayerTime prayer, boolean friday, Runnable onFinished,
                      Runnable onLockDue, boolean armLock) {
        AppConfig config = configService.get();
        this.onLockDue = onLockDue == null ? () -> { } : onLockDue;
        this.lockInSeconds = armLock ? Math.max(0, config.getLockDelaySeconds()) : -1;
        lockPending.set(armLock);
        updateLockCountdown();
        this.onFinished = onFinished == null ? () -> { } : onFinished;
        this.totalSeconds = Math.max(1, config.getFocusDurationSeconds());
        this.remainingSeconds = totalSeconds;

        prayerName.set(prayer.name().displayName(friday));
        arabicName.set(prayer.name().arabicName());
        prayerTime.set(prayer.formatted(config.isUse24HourClock()));
        updateClock();
        updateCountdown();

        stop();
        ticker = new Timeline(new KeyFrame(Duration.seconds(1), event -> tick()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        LOG.info("Focus overlay opened for {} ({} s)", prayerName.get(), totalSeconds);
    }

    /**
     * Stops the countdown without invoking the completion callback. Used by the
     * Skip and Close buttons, which handle window closing themselves.
     */
    public void stop() {
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
    }

    /**
     * Cancels a pending screen lock, leaving the reminder itself on screen.
     *
     * <p>This is the escape hatch that makes automatic locking safe to offer:
     * the user always sees it coming and can always stop it.</p>
     */
    public void cancelLock() {
        if (lockPending.get()) {
            lockInSeconds = -1;
            lockPending.set(false);
            lockCountdown.set("");
            LOG.info("Screen lock cancelled by the user");
        }
    }

    /**
     * @return seconds still remaining on the countdown
     */
    public int remainingSeconds() {
        return remainingSeconds;
    }

    private void tick() {
        remainingSeconds--;
        updateClock();
        updateCountdown();

        if (lockPending.get()) {
            lockInSeconds--;
            updateLockCountdown();
            if (lockInSeconds <= 0) {
                lockPending.set(false);
                lockCountdown.set("");
                LOG.info("Lock grace period expired - locking the session");
                onLockDue.run();
                return;
            }
        }

        if (remainingSeconds <= 0) {
            stop();
            LOG.info("Focus overlay countdown finished - closing");
            onFinished.run();
        }
    }

    private void updateCountdown() {
        int safe = Math.max(0, remainingSeconds);
        countdown.set(Messages.localiseDigits(
                String.format(java.util.Locale.ROOT, "%d:%02d", safe / 60, safe % 60)));
        progress.set(1.0 - ((double) safe / totalSeconds));
    }

    private void updateLockCountdown() {
        if (!lockPending.get() || lockInSeconds < 0) {
            lockCountdown.set("");
            return;
        }
        lockCountdown.set(Messages.format("focus.locksIn",
                Messages.localiseDigits(String.valueOf(Math.max(0, lockInSeconds)))));
    }

    private void updateClock() {
        AppConfig config = configService.get();
        ZonedDateTime now = scheduleService.now();
        currentTime.set(now.format(TimeUtils.clock(config.isUse24HourClock())));
        gregorianDate.set(now.format(TimeUtils.longDate()));
        hijriDate.set(TimeUtils.toHijriString(now.toLocalDate()));
    }

    public StringProperty prayerNameProperty() { return prayerName; }
    public StringProperty arabicNameProperty() { return arabicName; }
    public StringProperty prayerTimeProperty() { return prayerTime; }
    public StringProperty countdownProperty() { return countdown; }
    public StringProperty currentTimeProperty() { return currentTime; }
    public StringProperty gregorianDateProperty() { return gregorianDate; }
    public StringProperty hijriDateProperty() { return hijriDate; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty lockCountdownProperty() { return lockCountdown; }
    public BooleanProperty lockPendingProperty() { return lockPending; }
}
