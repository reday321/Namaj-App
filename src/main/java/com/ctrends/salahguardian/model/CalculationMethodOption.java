package com.ctrends.salahguardian.model;

import com.ctrends.salahguardian.i18n.Messages;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.PrayerAdjustments;

/**
 * The calculation conventions offered to the user.
 *
 * <p>Most entries delegate straight to a preset in adhan-java. Two do not:</p>
 * <ul>
 *   <li>{@link #TURKEY} - the Diyanet convention is not shipped by adhan-java,
 *       so it is reproduced here (18&deg;/17&deg; plus the Diyanet minute
 *       offsets) exactly as defined by the reference adhan-js implementation.</li>
 *   <li>{@link #CUSTOM} - twilight angles supplied by the user.</li>
 * </ul>
 *
 * @author CTrends Software
 */
public enum CalculationMethodOption {

    /** Muslim World League: 18&deg; Fajr, 17&deg; Isha. */
    MUSLIM_WORLD_LEAGUE("Muslim World League", CalculationMethod.MUSLIM_WORLD_LEAGUE),

    /** Islamic Society of North America: 15&deg; / 15&deg;. */
    ISNA("ISNA (North America)", CalculationMethod.NORTH_AMERICA),

    /** Umm al-Qura University, Makkah: 18.5&deg; Fajr, Isha 90 minutes after Maghrib. */
    UMM_AL_QURA("Umm al-Qura (Makkah)", CalculationMethod.UMM_AL_QURA),

    /** Egyptian General Authority of Survey: 19.5&deg; / 17.5&deg;. */
    EGYPT("Egyptian General Authority", CalculationMethod.EGYPTIAN),

    /** University of Islamic Sciences, Karachi: 18&deg; / 18&deg;. */
    KARACHI("Karachi (Univ. of Islamic Sciences)", CalculationMethod.KARACHI),

    /** Diyanet Isleri Baskanligi, Turkey - reproduced locally, see class notes. */
    TURKEY("Diyanet (Turkey)", null),

    /** Dubai / UAE: 18.2&deg; / 18.2&deg;. */
    DUBAI("Dubai (UAE)", CalculationMethod.DUBAI),

    /** Qatar: 18&deg; Fajr, Isha 90 minutes after Maghrib. */
    QATAR("Qatar", CalculationMethod.QATAR),

    /** Kuwait: 18&deg; / 17.5&deg;. */
    KUWAIT("Kuwait", CalculationMethod.KUWAIT),

    /** Singapore / MUIS: 20&deg; / 18&deg;. */
    SINGAPORE("Singapore (MUIS)", CalculationMethod.SINGAPORE),

    /** Moonsighting Committee Worldwide, includes seasonal adjustment. */
    MOON_SIGHTING_COMMITTEE("Moonsighting Committee", CalculationMethod.MOON_SIGHTING_COMMITTEE),

    /** User supplied Fajr and Isha twilight angles. */
    CUSTOM("Custom angles", null);

    /** Fajr angle applied when no preset defines one. */
    public static final double DEFAULT_CUSTOM_FAJR_ANGLE = 18.0;

    /** Isha angle applied when no preset defines one. */
    public static final double DEFAULT_CUSTOM_ISHA_ANGLE = 17.0;

    private final String displayName;
    private final CalculationMethod adhanMethod;

    CalculationMethodOption(String displayName, CalculationMethod adhanMethod) {
        this.displayName = displayName;
        this.adhanMethod = adhanMethod;
    }

    /**
     * @return label rendered in the settings combo box
     */
    public String displayName() {
        return Messages.get("method." + name());
    }

    /**
     * @return {@code true} when the user must supply the twilight angles
     */
    public boolean requiresCustomAngles() {
        return this == CUSTOM;
    }

    /**
     * Builds the adhan-java parameter object for this convention.
     *
     * <p>The returned instance is freshly created on every call, so callers may
     * safely mutate the madhab / high latitude fields without affecting the
     * library's shared preset objects.</p>
     *
     * @param customFajrAngle Fajr twilight angle used only by {@link #CUSTOM}
     * @param customIshaAngle Isha twilight angle used only by {@link #CUSTOM}
     * @return parameters ready to be handed to {@code PrayerTimes}
     */
    public CalculationParameters toParameters(double customFajrAngle, double customIshaAngle) {
        return switch (this) {
            case TURKEY -> turkeyParameters();
            case CUSTOM -> new CalculationParameters(
                    sanitizeAngle(customFajrAngle, DEFAULT_CUSTOM_FAJR_ANGLE),
                    sanitizeAngle(customIshaAngle, DEFAULT_CUSTOM_ISHA_ANGLE),
                    CalculationMethod.OTHER);
            default -> adhanMethod.getParameters();
        };
    }

    /**
     * Diyanet Isleri Baskanligi convention: 18&deg; Fajr and 17&deg; Isha with
     * fixed minute offsets on sunrise, Dhuhr, Asr and Maghrib.
     *
     * @return a fresh parameter set matching the Turkish official timetable
     */
    private static CalculationParameters turkeyParameters() {
        CalculationParameters parameters =
                new CalculationParameters(18.0, 17.0, CalculationMethod.OTHER);
        return parameters.withMethodAdjustments(
                new PrayerAdjustments(0, -7, 5, 4, 7, 0));
    }

    private static double sanitizeAngle(double angle, double fallback) {
        return Double.isFinite(angle) && angle > 0.0 && angle < 30.0 ? angle : fallback;
    }

    /**
     * Null-safe, case-insensitive parsing used when reading the config file.
     *
     * @param raw      persisted name
     * @param fallback value returned when {@code raw} does not match
     * @return the resolved option
     */
    public static CalculationMethodOption parseOrDefault(String raw, CalculationMethodOption fallback) {
        if (raw != null) {
            for (CalculationMethodOption option : values()) {
                if (option.name().equalsIgnoreCase(raw.trim())) {
                    return option;
                }
            }
        }
        return fallback;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
