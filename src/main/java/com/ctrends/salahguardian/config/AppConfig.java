package com.ctrends.salahguardian.config;

import com.ctrends.salahguardian.model.CalculationMethodOption;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.HighLatitudeRuleOption;
import com.ctrends.salahguardian.model.LocationSource;
import com.ctrends.salahguardian.i18n.Language;
import com.ctrends.salahguardian.model.MadhabOption;

import java.time.Instant;

/**
 * The serialisable form of every user preference, persisted verbatim as
 * {@code ~/.config/salahguardian/config.json}.
 *
 * <p>This is a deliberately plain mutable bean: Gson maps it field-for-field,
 * which keeps the on-disk document readable and hand-editable. Enum valued
 * preferences are stored as {@code String} so that an unknown or hand-typed
 * value degrades to the default instead of aborting the whole load.</p>
 *
 * <p>All accessors are null-safe; {@link #normalise()} repairs any value that
 * was corrupted or written by an older version.</p>
 *
 * @author CTrends Software
 */
public class AppConfig {

    // ----- schema -----------------------------------------------------------

    /**
     * Current schema version. Bump this whenever a stored value needs
     * reinterpreting, and add the corresponding step to {@link #migrate()}.
     */
    public static final int CURRENT_SCHEMA_VERSION = 3;

    /** Schema version of the document this instance was loaded from. */
    private int schemaVersion = CURRENT_SCHEMA_VERSION;

    // ----- location ---------------------------------------------------------

    private double latitude = GeoLocation.MAKKAH.latitude();
    private double longitude = GeoLocation.MAKKAH.longitude();
    private String city = "";
    private String country = "";
    private String timeZoneId = "";
    private String locationSource = LocationSource.MANUAL.name();
    private long locationResolvedAtEpochSecond = 0L;
    private boolean autoDetectLocation = true;
    /**
     * Whether the user has been asked about the one outbound request this
     * application makes.
     *
     * <p>Three states, not two: {@code null} means "never asked", so the
     * question is put once and the answer respected thereafter. Defaulting to
     * true would make the consent meaningless, and defaulting to false would
     * silently disable detection for existing users, so the distinction
     * matters.</p>
     */
    private Boolean networkLookupConsented = null;

    // ----- calculation ------------------------------------------------------

    private String calculationMethod = CalculationMethodOption.MUSLIM_WORLD_LEAGUE.name();
    private String madhab = MadhabOption.SHAFI.name();
    private String highLatitudeRule = HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT.name();
    private double customFajrAngle = CalculationMethodOption.DEFAULT_CUSTOM_FAJR_ANGLE;
    private double customIshaAngle = CalculationMethodOption.DEFAULT_CUSTOM_ISHA_ANGLE;

    /** Per-prayer manual offsets in minutes, order: Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. */
    private int[] manualAdjustments = new int[6];

    // ----- reminders --------------------------------------------------------

    private boolean notificationsEnabled = true;
    private int reminderMinutes = 5;
    private boolean remindAtPrayerTime = true;
    private boolean silentMode = false;
    private boolean fridayReminderEnabled = true;
    private int fridayReminderHour = 9;
    private boolean ramadanRemindersEnabled = true;

    // ----- focus mode -------------------------------------------------------

    private boolean focusModeEnabled = true;
    private int focusDurationSeconds = 300;
    /**
     * Lock the desktop session when the focus overlay opens.
     *
     * <p>Off by default and deliberately so: locking is disruptive, and an
     * application that starts locking the screen without being asked is an
     * application people uninstall. The user opts in explicitly.</p>
     */
    private boolean lockScreenAtPrayerTime = false;
    /**
     * Grace period between the overlay appearing and the screen locking, in
     * seconds. The overlay counts this down and offers a way out, so a lock
     * never arrives without warning. Zero locks immediately.
     */
    private int lockDelaySeconds = 30;

    // ----- appearance / behaviour ------------------------------------------

    private String theme = Theme.DARK.name();
    private String language = Language.SYSTEM.name();
    /**
     * Render digits in the interface language's own numeral set, e.g. Bengali
     * {@code ১২:০৫}. Ignored for languages written with Latin digits.
     */
    private boolean useLocalNumerals = true;
    /**
     * A 12 hour clock is the default: it is what most of the world reads a
     * prayer timetable in, and it is the norm across South Asia, the Gulf and
     * North America. Users who prefer 24 hour can switch in Settings.
     */
    private boolean use24HourClock = false;
    private boolean startOnLogin = false;
    private boolean startMinimisedToTray = true;
    private boolean showHijriDate = true;

    // ----- derived helpers --------------------------------------------------

    /**
     * Upgrades a configuration written by an older version of the application.
     *
     * <p>Migrations run once, in order, before {@link #normalise()}. Each one
     * must be safe to apply to a document that has already been through it,
     * because a crash between the migration and the save would otherwise leave
     * the file in a half-upgraded state.</p>
     *
     * @return {@code this}, for chaining
     */
    public AppConfig migrate() {
        if (schemaVersion < 3 && hasStoredLocation() && networkLookupConsented == null) {
            // An existing installation has already performed the lookup, so the
            // question is moot for them - recording it as answered avoids a
            // pointless prompt without inventing consent for a request that has
            // not yet happened.
            networkLookupConsented = Boolean.TRUE;
        }
        if (schemaVersion < 2) {
            // v1 defaulted to a 24 hour clock. v2 defaults to 12 hour, which is
            // what most of the world reads a prayer timetable in. Existing users
            // were never asked, so they are moved to the new default rather than
            // left on a value they never chose; anyone who prefers 24 hour can
            // set it in Settings and that choice then survives, because the
            // migration only ever runs against a v1 document.
            use24HourClock = false;
            LoggerHolder.LOG.info("Migrated configuration from schema v{}: "
                    + "the clock now defaults to 12 hour", schemaVersion);
        }
        schemaVersion = CURRENT_SCHEMA_VERSION;
        return this;
    }

    /**
     * Holder so that {@link AppConfig} stays a plain bean for Gson while still
     * being able to report what a migration did.
     */
    private static final class LoggerHolder {
        static final org.slf4j.Logger LOG =
                org.slf4j.LoggerFactory.getLogger(AppConfig.class);
    }

    /**
     * Clamps every field into its valid range. Called after loading and before
     * saving so that neither a hand-edited file nor a UI bug can put the
     * application into an unusable state.
     *
     * @return {@code this}, for chaining
     */
    public AppConfig normalise() {
        if (!GeoLocation.isValidLatitude(latitude)) {
            latitude = GeoLocation.MAKKAH.latitude();
        }
        if (!GeoLocation.isValidLongitude(longitude)) {
            longitude = GeoLocation.MAKKAH.longitude();
        }
        city = city == null ? "" : city.trim();
        country = country == null ? "" : country.trim();
        timeZoneId = timeZoneId == null ? "" : timeZoneId.trim();
        reminderMinutes = clamp(reminderMinutes, 0, 60);
        // Ten minutes, not an hour. A corrupted or hand-edited value of 3600
        // meant an always-on-top fullscreen window for a full hour.
        focusDurationSeconds = clamp(focusDurationSeconds, 30, 600);
        lockDelaySeconds = clamp(lockDelaySeconds, 0, 300);
        fridayReminderHour = clamp(fridayReminderHour, 0, 23);
        customFajrAngle = clampAngle(customFajrAngle, CalculationMethodOption.DEFAULT_CUSTOM_FAJR_ANGLE);
        customIshaAngle = clampAngle(customIshaAngle, CalculationMethodOption.DEFAULT_CUSTOM_ISHA_ANGLE);
        if (manualAdjustments == null || manualAdjustments.length != 6) {
            manualAdjustments = new int[6];
        } else {
            for (int i = 0; i < manualAdjustments.length; i++) {
                manualAdjustments[i] = clamp(manualAdjustments[i], -120, 120);
            }
        }
        if (schemaVersion <= 0) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        return this;
    }

    /**
     * Rebuilds the stored coordinates into a domain object.
     *
     * @return the persisted location, flagged as {@link LocationSource#CACHED}
     *         when it came from a previous automatic detection
     */
    public GeoLocation toGeoLocation() {
        LocationSource source = LocationSource.MANUAL;
        try {
            source = LocationSource.valueOf(locationSource);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // fall through to MANUAL
        }
        return new GeoLocation(latitude, longitude, city, country, timeZoneId,
                source, Instant.ofEpochSecond(Math.max(0, locationResolvedAtEpochSecond)));
    }

    /**
     * Copies a freshly detected location into this configuration.
     *
     * @param location the location to persist
     */
    public void applyLocation(GeoLocation location) {
        this.latitude = location.latitude();
        this.longitude = location.longitude();
        this.city = location.city();
        this.country = location.country();
        this.timeZoneId = location.timeZoneId();
        this.locationSource = location.source().name();
        this.locationResolvedAtEpochSecond = location.resolvedAt().getEpochSecond();
    }

    /**
     * @return {@code true} once a location has been stored at least once, which
     *         is what allows the application to run fully offline
     */
    public boolean hasStoredLocation() {
        return locationResolvedAtEpochSecond > 0L;
    }

    /**
     * @return the resolved calculation convention
     */
    public CalculationMethodOption calculationMethodOption() {
        return CalculationMethodOption.parseOrDefault(calculationMethod,
                CalculationMethodOption.MUSLIM_WORLD_LEAGUE);
    }

    /**
     * @return the resolved juristic school
     */
    public MadhabOption madhabOption() {
        return MadhabOption.parseOrDefault(madhab, MadhabOption.SHAFI);
    }

    /**
     * @return the resolved high latitude strategy
     */
    public HighLatitudeRuleOption highLatitudeRuleOption() {
        return HighLatitudeRuleOption.parseOrDefault(highLatitudeRule,
                HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT);
    }

    /**
     * @return the resolved theme
     */
    public Theme themeOption() {
        return Theme.parseOrDefault(theme, Theme.DARK);
    }

    /**
     * @return the resolved interface language
     */
    public Language languageOption() {
        return Language.parseOrDefault(language, Language.SYSTEM);
    }

    /**
     * @return {@code true} when notifications should actually be delivered,
     *         i.e. enabled and not muted by silent mode
     */
    public boolean shouldNotify() {
        return notificationsEnabled && !silentMode;
    }

    /**
     * Creates a detached copy, used by the settings screen so that edits can be
     * discarded without touching the live configuration.
     *
     * @return a deep enough copy for editing purposes
     */
    public AppConfig copy() {
        AppConfig c = new AppConfig();
        c.schemaVersion = schemaVersion;
        c.latitude = latitude;
        c.longitude = longitude;
        c.city = city;
        c.country = country;
        c.timeZoneId = timeZoneId;
        c.locationSource = locationSource;
        c.locationResolvedAtEpochSecond = locationResolvedAtEpochSecond;
        c.autoDetectLocation = autoDetectLocation;
        c.networkLookupConsented = networkLookupConsented;
        c.calculationMethod = calculationMethod;
        c.madhab = madhab;
        c.highLatitudeRule = highLatitudeRule;
        c.customFajrAngle = customFajrAngle;
        c.customIshaAngle = customIshaAngle;
        c.manualAdjustments = manualAdjustments == null ? new int[6] : manualAdjustments.clone();
        c.notificationsEnabled = notificationsEnabled;
        c.reminderMinutes = reminderMinutes;
        c.remindAtPrayerTime = remindAtPrayerTime;
        c.silentMode = silentMode;
        c.fridayReminderEnabled = fridayReminderEnabled;
        c.fridayReminderHour = fridayReminderHour;
        c.ramadanRemindersEnabled = ramadanRemindersEnabled;
        c.focusModeEnabled = focusModeEnabled;
        c.focusDurationSeconds = focusDurationSeconds;
        c.lockScreenAtPrayerTime = lockScreenAtPrayerTime;
        c.lockDelaySeconds = lockDelaySeconds;
        c.theme = theme;
        c.language = language;
        c.useLocalNumerals = useLocalNumerals;
        c.use24HourClock = use24HourClock;
        c.startOnLogin = startOnLogin;
        c.startMinimisedToTray = startMinimisedToTray;
        c.showHijriDate = showHijriDate;
        return c;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampAngle(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 && value < 30.0 ? value : fallback;
    }

    // ----- generated accessors ---------------------------------------------

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getTimeZoneId() { return timeZoneId; }
    public void setTimeZoneId(String timeZoneId) { this.timeZoneId = timeZoneId; }

    public String getLocationSource() { return locationSource; }
    public void setLocationSource(String locationSource) { this.locationSource = locationSource; }

    public long getLocationResolvedAtEpochSecond() { return locationResolvedAtEpochSecond; }
    public void setLocationResolvedAtEpochSecond(long value) { this.locationResolvedAtEpochSecond = value; }

    /**
     * @return {@code true} when the user has agreed to the IP geolocation
     *         lookup, {@code false} when they declined
     */
    public boolean isNetworkLookupConsented() {
        return Boolean.TRUE.equals(networkLookupConsented);
    }

    /**
     * @return {@code true} when the question has never been put to the user
     */
    public boolean isNetworkLookupUndecided() {
        return networkLookupConsented == null;
    }

    public Boolean getNetworkLookupConsented() { return networkLookupConsented; }
    public void setNetworkLookupConsented(Boolean value) { this.networkLookupConsented = value; }

    public boolean isAutoDetectLocation() { return autoDetectLocation; }
    public void setAutoDetectLocation(boolean autoDetectLocation) { this.autoDetectLocation = autoDetectLocation; }

    public String getCalculationMethod() { return calculationMethod; }
    public void setCalculationMethod(String calculationMethod) { this.calculationMethod = calculationMethod; }

    public String getMadhab() { return madhab; }
    public void setMadhab(String madhab) { this.madhab = madhab; }

    public String getHighLatitudeRule() { return highLatitudeRule; }
    public void setHighLatitudeRule(String highLatitudeRule) { this.highLatitudeRule = highLatitudeRule; }

    public double getCustomFajrAngle() { return customFajrAngle; }
    public void setCustomFajrAngle(double customFajrAngle) { this.customFajrAngle = customFajrAngle; }

    public double getCustomIshaAngle() { return customIshaAngle; }
    public void setCustomIshaAngle(double customIshaAngle) { this.customIshaAngle = customIshaAngle; }

    public int[] getManualAdjustments() { return manualAdjustments; }
    public void setManualAdjustments(int[] manualAdjustments) { this.manualAdjustments = manualAdjustments; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

    public int getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }

    public boolean isRemindAtPrayerTime() { return remindAtPrayerTime; }
    public void setRemindAtPrayerTime(boolean remindAtPrayerTime) { this.remindAtPrayerTime = remindAtPrayerTime; }

    public boolean isSilentMode() { return silentMode; }
    public void setSilentMode(boolean silentMode) { this.silentMode = silentMode; }

    public boolean isFridayReminderEnabled() { return fridayReminderEnabled; }
    public void setFridayReminderEnabled(boolean fridayReminderEnabled) { this.fridayReminderEnabled = fridayReminderEnabled; }

    public int getFridayReminderHour() { return fridayReminderHour; }
    public void setFridayReminderHour(int fridayReminderHour) { this.fridayReminderHour = fridayReminderHour; }

    public boolean isRamadanRemindersEnabled() { return ramadanRemindersEnabled; }
    public void setRamadanRemindersEnabled(boolean ramadanRemindersEnabled) { this.ramadanRemindersEnabled = ramadanRemindersEnabled; }

    public boolean isFocusModeEnabled() { return focusModeEnabled; }
    public void setFocusModeEnabled(boolean focusModeEnabled) { this.focusModeEnabled = focusModeEnabled; }

    public int getFocusDurationSeconds() { return focusDurationSeconds; }
    public void setFocusDurationSeconds(int focusDurationSeconds) { this.focusDurationSeconds = focusDurationSeconds; }

    public boolean isLockScreenAtPrayerTime() { return lockScreenAtPrayerTime; }
    public void setLockScreenAtPrayerTime(boolean value) { this.lockScreenAtPrayerTime = value; }

    public int getLockDelaySeconds() { return lockDelaySeconds; }
    public void setLockDelaySeconds(int lockDelaySeconds) { this.lockDelaySeconds = lockDelaySeconds; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public boolean isUseLocalNumerals() { return useLocalNumerals; }
    public void setUseLocalNumerals(boolean useLocalNumerals) { this.useLocalNumerals = useLocalNumerals; }

    public boolean isUse24HourClock() { return use24HourClock; }
    public void setUse24HourClock(boolean use24HourClock) { this.use24HourClock = use24HourClock; }

    public boolean isStartOnLogin() { return startOnLogin; }
    public void setStartOnLogin(boolean startOnLogin) { this.startOnLogin = startOnLogin; }

    public boolean isStartMinimisedToTray() { return startMinimisedToTray; }
    public void setStartMinimisedToTray(boolean startMinimisedToTray) { this.startMinimisedToTray = startMinimisedToTray; }

    public boolean isShowHijriDate() { return showHijriDate; }
    public void setShowHijriDate(boolean showHijriDate) { this.showHijriDate = showHijriDate; }
}
