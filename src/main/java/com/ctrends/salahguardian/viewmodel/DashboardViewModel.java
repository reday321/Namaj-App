package com.ctrends.salahguardian.viewmodel;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.config.Theme;
import com.ctrends.salahguardian.location.LocationService;
import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.model.UpcomingPrayer;
import com.ctrends.salahguardian.prayer.PrayerScheduleService;
import com.ctrends.salahguardian.service.PrayerEventListener;
import com.ctrends.salahguardian.service.PrayerSchedulerService;
import com.ctrends.salahguardian.service.ReminderEvent;
import com.ctrends.salahguardian.utils.TimeUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * View model behind the dashboard window.
 *
 * <p>Exposes everything the view binds to as JavaFX properties and owns the
 * one-second {@link Timeline} that keeps the clock and countdown live. The view
 * itself contains no logic beyond layout and binding, which is what keeps the
 * MVVM separation honest.</p>
 *
 * <h2>Threading</h2>
 * Every property is written on the JavaFX application thread. Work that can
 * block - re-resolving the location - runs on a small background executor and
 * hops back via {@link Platform#runLater}.
 *
 * @author CTrends Software
 */
@Singleton
public class DashboardViewModel implements PrayerEventListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardViewModel.class);

    private final PrayerScheduleService scheduleService;
    private final LocationService locationService;
    private final ConfigService configService;
    private final PrayerSchedulerService schedulerService;

    // ----- location card ----------------------------------------------------
    private final StringProperty locationLabel = new SimpleStringProperty("Detecting…");
    private final StringProperty coordinateLabel = new SimpleStringProperty("");
    private final StringProperty locationSourceLabel = new SimpleStringProperty("");

    // ----- date & clock cards ----------------------------------------------
    private final StringProperty gregorianDate = new SimpleStringProperty("");
    private final StringProperty hijriDate = new SimpleStringProperty("");
    private final StringProperty currentTime = new SimpleStringProperty("");

    // ----- next prayer card -------------------------------------------------
    private final StringProperty nextPrayerName = new SimpleStringProperty("—");
    private final StringProperty nextPrayerArabic = new SimpleStringProperty("");
    private final StringProperty nextPrayerTime = new SimpleStringProperty("");
    private final StringProperty countdown = new SimpleStringProperty("--:--");
    private final StringProperty countdownCaption = new SimpleStringProperty("");

    // ----- timetable --------------------------------------------------------
    private final ObservableList<PrayerRowViewModel> todayRows = FXCollections.observableArrayList();
    private final ObservableList<PrayerRowViewModel> tomorrowRows = FXCollections.observableArrayList();

    // ----- state flags ------------------------------------------------------
    private final BooleanProperty remindersEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty focusModeEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty silentMode = new SimpleBooleanProperty(false);
    private final BooleanProperty ramadan = new SimpleBooleanProperty(false);
    private final StringProperty approximationNotice = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.DARK);

    private final ExecutorService background = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dashboard-background");
        thread.setDaemon(true);
        return thread;
    });

    private Timeline ticker;

    /** The civil date the timetable rows currently show. */
    private LocalDate renderedDate;

    /**
     * @param scheduleService  supplies the timetables
     * @param locationService  supplies the position
     * @param configService    supplies the preferences
     * @param schedulerService the background reminder engine
     */
    @Inject
    public DashboardViewModel(PrayerScheduleService scheduleService,
                              LocationService locationService,
                              ConfigService configService,
                              PrayerSchedulerService schedulerService) {
        this.scheduleService = scheduleService;
        this.locationService = locationService;
        this.configService = configService;
        this.schedulerService = schedulerService;

        schedulerService.addListener(this);
        configService.addChangeListener(config -> Platform.runLater(this::applyConfig));
        applyConfig();
    }

    /**
     * Starts the one-second refresh loop. Safe to call more than once.
     */
    public void start() {
        if (ticker != null) {
            return;
        }
        ticker = new Timeline(new KeyFrame(Duration.seconds(1), event -> tick()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        refreshAll();
        LOG.debug("Dashboard view model started");
    }

    /**
     * Stops the refresh loop while the window is hidden, so a tray-resident
     * application costs nothing.
     */
    public void pause() {
        if (ticker != null) {
            ticker.pause();
        }
    }

    /**
     * Resumes the refresh loop and immediately brings the view up to date.
     */
    public void resume() {
        if (ticker == null) {
            start();
            return;
        }
        ticker.play();
        refreshAll();
    }

    @Override
    public void close() {
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
        schedulerService.removeListener(this);
        background.shutdownNow();
    }

    /**
     * Recomputes every card. Must be called on the JavaFX thread.
     */
    public void refreshAll() {
        try {
            AppConfig config = configService.get();
            updateLocationCard();
            updateTimetable(config);
            tick();
        } catch (RuntimeException e) {
            LOG.error("Dashboard refresh failed", e);
            statusMessage.set("Could not refresh prayer times - see the log for details.");
        }
    }

    /**
     * Re-runs location detection off the UI thread, then refreshes.
     */
    public void refreshLocation() {
        statusMessage.set("Detecting your location…");
        background.submit(() -> {
            GeoLocation location = locationService.refresh();
            scheduleService.invalidate();
            Platform.runLater(() -> {
                statusMessage.set("Location updated: " + location.displayLabel());
                refreshAll();
                schedulerService.reschedule();
            });
        });
    }

    @Override
    public void onReminder(ReminderEvent event) {
        Platform.runLater(this::refreshAll);
    }

    @Override
    public void onScheduleChanged() {
        Platform.runLater(this::refreshAll);
    }

    // ----- per-second update ------------------------------------------------

    private void tick() {
        AppConfig config = configService.get();
        ZonedDateTime now = scheduleService.now();

        currentTime.set(now.format(config.isUse24HourClock()
                ? TimeUtils.CLOCK_24H : TimeUtils.CLOCK_12H));
        gregorianDate.set(now.format(TimeUtils.LONG_DATE));
        hijriDate.set(config.isShowHijriDate() ? TimeUtils.toHijriString(now.toLocalDate()) : "");
        ramadan.set(TimeUtils.isRamadan(now.toLocalDate()));

        Optional<UpcomingPrayer> upcoming = scheduleService.nextPrayer();
        if (upcoming.isPresent()) {
            UpcomingPrayer next = upcoming.get();
            boolean friday = next.prayer().time().getDayOfWeek() == java.time.DayOfWeek.FRIDAY;
            nextPrayerName.set(next.prayer().name().displayName(friday));
            nextPrayerArabic.set(next.prayer().name().arabicName());
            nextPrayerTime.set(next.prayer().formatted(config.isUse24HourClock()));
            countdown.set(next.formattedRemaining());
            countdownCaption.set(next.tomorrow() ? "until tomorrow's " + next.prayer().name().displayName()
                    : "until " + nextPrayerName.get());
            highlightRows(next.prayer(), now);
        } else {
            nextPrayerName.set("—");
            nextPrayerArabic.set("");
            nextPrayerTime.set("");
            countdown.set("--:--");
            countdownCaption.set("No upcoming prayer could be calculated");
        }

        // The timetable is rebuilt only when the civil day actually changes.
        if (!todayRowsMatch(now.toLocalDate())) {
            updateTimetable(config);
        }
    }

    private boolean todayRowsMatch(LocalDate date) {
        return date.equals(renderedDate) && !todayRows.isEmpty();
    }

    private void updateTimetable(AppConfig config) {
        DailyPrayerSchedule today = scheduleService.today();
        DailyPrayerSchedule tomorrow = scheduleService.tomorrow();
        renderedDate = today.date();

        todayRows.setAll(toRows(today, config));
        tomorrowRows.setAll(toRows(tomorrow, config));

        // Be explicit when the times are a convention rather than an
        // observation, which happens inside the polar circles.
        approximationNotice.set(today.isApproximated()
                ? "Approximate times: at this latitude the sun does not cross the twilight "
                        + "angles today, so the nearest-latitude convention (45°) is used."
                : "");
    }

    private List<PrayerRowViewModel> toRows(DailyPrayerSchedule schedule, AppConfig config) {
        return schedule.allTimes().stream()
                .map(entry -> new PrayerRowViewModel(entry, schedule.isFriday(),
                        config.isUse24HourClock()))
                .toList();
    }

    private void highlightRows(PrayerTime next, ZonedDateTime now) {
        DailyPrayerSchedule today = scheduleService.today();
        for (PrayerRowViewModel row : todayRows) {
            Optional<PrayerTime> entry = today.timeOf(row.prayer());
            boolean isPast = entry.map(t -> !t.time().isAfter(now)).orElse(false);
            row.pastProperty().set(isPast);
            row.nextProperty().set(row.prayer() == next.name() && !isPast);
        }
    }

    private void updateLocationCard() {
        GeoLocation location = locationService.peek()
                .orElseGet(() -> configService.get().toGeoLocation());
        locationLabel.set(location.displayLabel());
        coordinateLabel.set(location.coordinateLabel());
        locationSourceLabel.set("via " + location.source().displayName());
    }

    private void applyConfig() {
        AppConfig config = configService.get();
        remindersEnabled.set(config.isNotificationsEnabled());
        focusModeEnabled.set(config.isFocusModeEnabled());
        silentMode.set(config.isSilentMode());
        theme.set(config.themeOption());
    }

    // ----- toggles used by the tray menu and the dashboard header ------------

    /**
     * Turns reminder notifications on or off and persists the choice.
     *
     * @param enabled the new state
     */
    public void setRemindersEnabled(boolean enabled) {
        configService.update(config -> config.setNotificationsEnabled(enabled));
        schedulerService.reschedule();
        statusMessage.set(enabled ? "Reminders enabled." : "Reminders disabled.");
    }

    /**
     * Turns the fullscreen focus overlay on or off and persists the choice.
     *
     * @param enabled the new state
     */
    public void setFocusModeEnabled(boolean enabled) {
        configService.update(config -> config.setFocusModeEnabled(enabled));
        statusMessage.set(enabled ? "Prayer focus mode enabled." : "Prayer focus mode disabled.");
    }

    /**
     * Mutes or unmutes every reminder without forgetting the individual
     * notification preferences.
     *
     * @param silent the new state
     */
    public void setSilentMode(boolean silent) {
        configService.update(config -> config.setSilentMode(silent));
        schedulerService.reschedule();
        statusMessage.set(silent ? "Silent mode on - reminders are muted." : "Silent mode off.");
    }

    // ----- property accessors ----------------------------------------------

    public StringProperty locationLabelProperty() { return locationLabel; }
    public StringProperty coordinateLabelProperty() { return coordinateLabel; }
    public StringProperty locationSourceLabelProperty() { return locationSourceLabel; }
    public StringProperty gregorianDateProperty() { return gregorianDate; }
    public StringProperty hijriDateProperty() { return hijriDate; }
    public StringProperty currentTimeProperty() { return currentTime; }
    public StringProperty nextPrayerNameProperty() { return nextPrayerName; }
    public StringProperty nextPrayerArabicProperty() { return nextPrayerArabic; }
    public StringProperty nextPrayerTimeProperty() { return nextPrayerTime; }
    public StringProperty countdownProperty() { return countdown; }
    public StringProperty countdownCaptionProperty() { return countdownCaption; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public ObservableList<PrayerRowViewModel> todayRows() { return todayRows; }
    public ObservableList<PrayerRowViewModel> tomorrowRows() { return tomorrowRows; }
    public BooleanProperty remindersEnabledProperty() { return remindersEnabled; }
    public BooleanProperty focusModeEnabledProperty() { return focusModeEnabled; }
    public BooleanProperty silentModeProperty() { return silentMode; }
    public BooleanProperty ramadanProperty() { return ramadan; }
    public StringProperty approximationNoticeProperty() { return approximationNotice; }
    public ObjectProperty<Theme> themeProperty() { return theme; }
}
