package com.ctrends.salahguardian.viewmodel;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.config.Theme;
import com.ctrends.salahguardian.i18n.Language;
import com.ctrends.salahguardian.i18n.Messages;
import com.ctrends.salahguardian.location.LocationService;
import com.ctrends.salahguardian.model.CalculationMethodOption;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.HighLatitudeRuleOption;
import com.ctrends.salahguardian.model.MadhabOption;
import com.ctrends.salahguardian.prayer.PrayerScheduleService;
import com.ctrends.salahguardian.service.AutostartService;
import com.ctrends.salahguardian.service.PrayerSchedulerService;
import com.ctrends.salahguardian.service.ScreenLockService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * View model behind the settings window.
 *
 * <p>Every property writes straight through to {@link ConfigService} on change,
 * which is what delivers the "save automatically" requirement: there is no Save
 * button and no way to end up with unsaved edits.</p>
 *
 * <p>A single {@code suppressWrite} guard prevents the feedback loop that would
 * otherwise occur when the view model reloads its own values after a save.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class SettingsViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsViewModel.class);

    private final ConfigService configService;
    private final LocationService locationService;
    private final PrayerScheduleService scheduleService;
    private final PrayerSchedulerService schedulerService;
    private final AutostartService autostartService;
    private final ScreenLockService screenLockService;

    // ----- location ---------------------------------------------------------
    private final BooleanProperty autoDetectLocation = new SimpleBooleanProperty(true);
    private final DoubleProperty latitude = new SimpleDoubleProperty();
    private final DoubleProperty longitude = new SimpleDoubleProperty();
    private final StringProperty city = new SimpleStringProperty("");
    private final StringProperty country = new SimpleStringProperty("");

    // ----- calculation ------------------------------------------------------
    private final ObjectProperty<CalculationMethodOption> calculationMethod =
            new SimpleObjectProperty<>(CalculationMethodOption.MUSLIM_WORLD_LEAGUE);
    private final ObjectProperty<MadhabOption> madhab = new SimpleObjectProperty<>(MadhabOption.SHAFI);
    private final ObjectProperty<HighLatitudeRuleOption> highLatitudeRule =
            new SimpleObjectProperty<>(HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT);
    private final DoubleProperty customFajrAngle = new SimpleDoubleProperty();
    private final DoubleProperty customIshaAngle = new SimpleDoubleProperty();

    // ----- reminders --------------------------------------------------------
    private final BooleanProperty notificationsEnabled = new SimpleBooleanProperty(true);
    private final IntegerProperty reminderMinutes = new SimpleIntegerProperty(5);
    private final BooleanProperty remindAtPrayerTime = new SimpleBooleanProperty(true);
    private final BooleanProperty silentMode = new SimpleBooleanProperty(false);
    private final BooleanProperty fridayReminderEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty ramadanRemindersEnabled = new SimpleBooleanProperty(true);

    // ----- focus mode -------------------------------------------------------
    private final BooleanProperty focusModeEnabled = new SimpleBooleanProperty(true);
    private final IntegerProperty focusDurationSeconds = new SimpleIntegerProperty(300);
    private final BooleanProperty lockScreenAtPrayerTime = new SimpleBooleanProperty(false);
    private final IntegerProperty lockDelaySeconds = new SimpleIntegerProperty(30);

    // ----- appearance & startup --------------------------------------------
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.DARK);
    private final ObjectProperty<Language> language = new SimpleObjectProperty<>(Language.SYSTEM);
    private final BooleanProperty useLocalNumerals = new SimpleBooleanProperty(true);
    private final BooleanProperty use24HourClock = new SimpleBooleanProperty(true);
    private final BooleanProperty startOnLogin = new SimpleBooleanProperty(false);
    private final BooleanProperty startMinimisedToTray = new SimpleBooleanProperty(true);
    private final BooleanProperty showHijriDate = new SimpleBooleanProperty(true);

    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final ExecutorService background = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "settings-background");
        thread.setDaemon(true);
        return thread;
    });

    private boolean suppressWrite;

    /**
     * @param configService    preference persistence
     * @param locationService  manual location entry
     * @param scheduleService  cache invalidation after a calculation change
     * @param schedulerService re-planning after a reminder change
     * @param autostartService desktop autostart entry management
     */
    @Inject
    public SettingsViewModel(ConfigService configService,
                             LocationService locationService,
                             PrayerScheduleService scheduleService,
                             PrayerSchedulerService schedulerService,
                             AutostartService autostartService,
                             ScreenLockService screenLockService) {
        this.configService = configService;
        this.locationService = locationService;
        this.scheduleService = scheduleService;
        this.schedulerService = schedulerService;
        this.autostartService = autostartService;
        this.screenLockService = screenLockService;
        loadFromConfig();
        wireAutoSave();
    }

    /**
     * Copies the persisted configuration into the bound properties.
     */
    public final void loadFromConfig() {
        AppConfig config = configService.get();
        suppressWrite = true;
        try {
            autoDetectLocation.set(config.isAutoDetectLocation());
            latitude.set(config.getLatitude());
            longitude.set(config.getLongitude());
            city.set(config.getCity());
            country.set(config.getCountry());

            calculationMethod.set(config.calculationMethodOption());
            madhab.set(config.madhabOption());
            highLatitudeRule.set(config.highLatitudeRuleOption());
            customFajrAngle.set(config.getCustomFajrAngle());
            customIshaAngle.set(config.getCustomIshaAngle());

            notificationsEnabled.set(config.isNotificationsEnabled());
            reminderMinutes.set(config.getReminderMinutes());
            remindAtPrayerTime.set(config.isRemindAtPrayerTime());
            silentMode.set(config.isSilentMode());
            fridayReminderEnabled.set(config.isFridayReminderEnabled());
            ramadanRemindersEnabled.set(config.isRamadanRemindersEnabled());

            focusModeEnabled.set(config.isFocusModeEnabled());
            focusDurationSeconds.set(config.getFocusDurationSeconds());
            lockScreenAtPrayerTime.set(config.isLockScreenAtPrayerTime());
            lockDelaySeconds.set(config.getLockDelaySeconds());

            theme.set(config.themeOption());
            language.set(config.languageOption());
            useLocalNumerals.set(config.isUseLocalNumerals());
            use24HourClock.set(config.isUse24HourClock());
            startOnLogin.set(config.isStartOnLogin());
            startMinimisedToTray.set(config.isStartMinimisedToTray());
            showHijriDate.set(config.isShowHijriDate());
        } finally {
            suppressWrite = false;
        }
    }

    /**
     * Attaches change listeners that persist each property as it is edited.
     */
    private void wireAutoSave() {
        // Changes that only affect presentation.
        onChange(theme, value -> save(config -> config.setTheme(value.name()), false, false));

        // Language and numerals change how every string is rendered, so the
        // active bundle is switched before the preference is persisted - that
        // way the "Saved." confirmation already appears in the new language.
        onChange(language, value -> {
            Messages.setLanguage(value, useLocalNumerals.get());
            save(config -> config.setLanguage(value.name()), false, false);
        });
        onChange(useLocalNumerals, value -> {
            Messages.setLanguage(language.get(), value);
            save(config -> config.setUseLocalNumerals(value), false, false);
        });
        onChange(use24HourClock, value -> save(config -> config.setUse24HourClock(value), false, false));
        onChange(showHijriDate, value -> save(config -> config.setShowHijriDate(value), false, false));
        onChange(startMinimisedToTray,
                value -> save(config -> config.setStartMinimisedToTray(value), false, false));

        // Changes that alter the arithmetic and therefore need a recalculation.
        onChange(calculationMethod,
                value -> save(config -> config.setCalculationMethod(value.name()), true, true));
        onChange(madhab, value -> save(config -> config.setMadhab(value.name()), true, true));
        onChange(highLatitudeRule,
                value -> save(config -> config.setHighLatitudeRule(value.name()), true, true));
        onChange(customFajrAngle,
                value -> save(config -> config.setCustomFajrAngle(value.doubleValue()), true, true));
        onChange(customIshaAngle,
                value -> save(config -> config.setCustomIshaAngle(value.doubleValue()), true, true));

        // Changes that only alter when reminders fire.
        onChange(notificationsEnabled,
                value -> save(config -> config.setNotificationsEnabled(value), false, true));
        onChange(reminderMinutes,
                value -> save(config -> config.setReminderMinutes(value.intValue()), false, true));
        onChange(remindAtPrayerTime,
                value -> save(config -> config.setRemindAtPrayerTime(value), false, true));
        onChange(silentMode, value -> save(config -> config.setSilentMode(value), false, true));
        onChange(fridayReminderEnabled,
                value -> save(config -> config.setFridayReminderEnabled(value), false, true));
        onChange(ramadanRemindersEnabled,
                value -> save(config -> config.setRamadanRemindersEnabled(value), false, true));

        onChange(focusModeEnabled,
                value -> save(config -> config.setFocusModeEnabled(value), false, false));
        onChange(focusDurationSeconds,
                value -> save(config -> config.setFocusDurationSeconds(value.intValue()), false, false));
        onChange(lockScreenAtPrayerTime, value -> {
            save(config -> config.setLockScreenAtPrayerTime(value), false, false);
            if (value) {
                LOG.info("Screen locking enabled - mechanism: {}", screenLockService.describe());
            }
        });
        onChange(lockDelaySeconds,
                value -> save(config -> config.setLockDelaySeconds(value.intValue()), false, false));

        onChange(autoDetectLocation, value -> {
            save(config -> config.setAutoDetectLocation(value), true, true);
            if (value) {
                redetectLocation();
            }
        });

        // Start on login also has to touch the filesystem.
        onChange(startOnLogin, this::applyStartOnLogin);
    }

    private <T> void onChange(javafx.beans.value.ObservableValue<T> property,
                              java.util.function.Consumer<T> handler) {
        property.addListener((observable, oldValue, newValue) -> {
            if (suppressWrite || newValue == null || newValue.equals(oldValue)) {
                return;
            }
            handler.accept(newValue);
        });
    }

    private void save(java.util.function.Consumer<AppConfig> mutation,
                      boolean invalidateSchedule, boolean reschedule) {
        configService.update(mutation);
        if (invalidateSchedule) {
            scheduleService.invalidate();
        }
        if (reschedule) {
            schedulerService.reschedule();
        }
        statusMessage.set(Messages.get("status.saved"));
    }

    private void applyStartOnLogin(boolean enabled) {
        boolean applied = autostartService.setEnabled(enabled);
        configService.update(config -> config.setStartOnLogin(enabled && applied));
        if (enabled && !applied) {
            suppressWrite = true;
            try {
                startOnLogin.set(false);
            } finally {
                suppressWrite = false;
            }
            statusMessage.set(Messages.get("status.autostartFailed"));
        } else {
            statusMessage.set(Messages.get(enabled
                    ? "status.autostartEnabled" : "status.autostartDisabled"));
        }
    }

    /**
     * Validates and stores hand-entered coordinates.
     *
     * @return {@code true} when the values were accepted
     */
    public boolean applyManualLocation() {
        double lat = latitude.get();
        double lon = longitude.get();
        if (!GeoLocation.isValidLatitude(lat) || !GeoLocation.isValidLongitude(lon)) {
            statusMessage.set(Messages.get("status.invalidCoordinates"));
            return false;
        }
        GeoLocation location = locationService.setManualLocation(lat, lon, city.get(), country.get());
        suppressWrite = true;
        try {
            autoDetectLocation.set(false);
        } finally {
            suppressWrite = false;
        }
        scheduleService.invalidate();
        schedulerService.reschedule();
        statusMessage.set(Messages.format("status.locationSet", location.displayLabel()));
        LOG.info("Manual location applied ({})", location.coarseLabel());
        return true;
    }

    /**
     * Re-runs automatic detection off the UI thread.
     */
    public void redetectLocation() {
        statusMessage.set(Messages.get("status.detecting"));
        background.submit(() -> {
            GeoLocation detected = locationService.refresh();
            scheduleService.invalidate();
            schedulerService.reschedule();
            Platform.runLater(() -> {
                suppressWrite = true;
                try {
                    latitude.set(detected.latitude());
                    longitude.set(detected.longitude());
                    city.set(detected.city());
                    country.set(detected.country());
                } finally {
                    suppressWrite = false;
                }
                statusMessage.set(Messages.format("status.detected",
                        detected.displayLabel(), detected.source().displayName()));
            });
        });
    }

    /**
     * @return {@code true} when the chosen method needs the custom angle fields
     */
    public boolean requiresCustomAngles() {
        CalculationMethodOption method = calculationMethod.get();
        return method != null && method.requiresCustomAngles();
    }

    // ----- property accessors ----------------------------------------------

    public BooleanProperty autoDetectLocationProperty() { return autoDetectLocation; }
    public DoubleProperty latitudeProperty() { return latitude; }
    public DoubleProperty longitudeProperty() { return longitude; }
    public StringProperty cityProperty() { return city; }
    public StringProperty countryProperty() { return country; }
    public ObjectProperty<CalculationMethodOption> calculationMethodProperty() { return calculationMethod; }
    public ObjectProperty<MadhabOption> madhabProperty() { return madhab; }
    public ObjectProperty<HighLatitudeRuleOption> highLatitudeRuleProperty() { return highLatitudeRule; }
    public DoubleProperty customFajrAngleProperty() { return customFajrAngle; }
    public DoubleProperty customIshaAngleProperty() { return customIshaAngle; }
    public BooleanProperty notificationsEnabledProperty() { return notificationsEnabled; }
    public IntegerProperty reminderMinutesProperty() { return reminderMinutes; }
    public BooleanProperty remindAtPrayerTimeProperty() { return remindAtPrayerTime; }
    public BooleanProperty silentModeProperty() { return silentMode; }
    public BooleanProperty fridayReminderEnabledProperty() { return fridayReminderEnabled; }
    public BooleanProperty ramadanRemindersEnabledProperty() { return ramadanRemindersEnabled; }
    public BooleanProperty focusModeEnabledProperty() { return focusModeEnabled; }
    public IntegerProperty focusDurationSecondsProperty() { return focusDurationSeconds; }
    public BooleanProperty lockScreenAtPrayerTimeProperty() { return lockScreenAtPrayerTime; }
    public IntegerProperty lockDelaySecondsProperty() { return lockDelaySeconds; }

    /**
     * @return {@code true} when this desktop offers a way to lock the session,
     *         so the setting is worth enabling
     */
    public boolean isScreenLockAvailable() {
        return screenLockService.isAvailable();
    }
    public ObjectProperty<Theme> themeProperty() { return theme; }
    public ObjectProperty<Language> languageProperty() { return language; }
    public BooleanProperty useLocalNumeralsProperty() { return useLocalNumerals; }
    public BooleanProperty use24HourClockProperty() { return use24HourClock; }
    public BooleanProperty startOnLoginProperty() { return startOnLogin; }
    public BooleanProperty startMinimisedToTrayProperty() { return startMinimisedToTray; }
    public BooleanProperty showHijriDateProperty() { return showHijriDate; }
    public StringProperty statusMessageProperty() { return statusMessage; }
}
