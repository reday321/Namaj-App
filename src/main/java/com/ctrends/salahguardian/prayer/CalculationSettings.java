package com.ctrends.salahguardian.prayer;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.model.CalculationMethodOption;
import com.ctrends.salahguardian.model.HighLatitudeRuleOption;
import com.ctrends.salahguardian.model.MadhabOption;

import java.util.Arrays;
import java.util.Objects;

/**
 * The immutable subset of the user's preferences that actually influences the
 * arithmetic of a prayer timetable.
 *
 * <p>Isolating these fields from {@link AppConfig} means the calculator can be
 * unit tested without touching the filesystem, and lets the schedule cache
 * detect - by simple value equality - whether a settings change requires a
 * recalculation.</p>
 *
 * @param method           the twilight angle convention
 * @param madhab           the juristic school, affecting Asr only
 * @param highLatitudeRule the polar region strategy
 * @param customFajrAngle  Fajr angle used when {@code method} is
 *                         {@link CalculationMethodOption#CUSTOM}
 * @param customIshaAngle  Isha angle used when {@code method} is
 *                         {@link CalculationMethodOption#CUSTOM}
 * @param manualAdjustments six per-prayer offsets in minutes, ordered
 *                          Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha
 * @author CTrends Software
 */
public record CalculationSettings(
        CalculationMethodOption method,
        MadhabOption madhab,
        HighLatitudeRuleOption highLatitudeRule,
        double customFajrAngle,
        double customIshaAngle,
        int[] manualAdjustments) {

    /** Number of per-prayer offsets, one per {@code PrayerName} constant. */
    public static final int ADJUSTMENT_COUNT = 6;

    public CalculationSettings {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(madhab, "madhab");
        Objects.requireNonNull(highLatitudeRule, "highLatitudeRule");
        manualAdjustments = manualAdjustments == null || manualAdjustments.length != ADJUSTMENT_COUNT
                ? new int[ADJUSTMENT_COUNT]
                : manualAdjustments.clone();
    }

    /**
     * Builds a settings snapshot from the persisted configuration.
     *
     * @param config the user's preferences
     * @return the calculation relevant subset
     */
    public static CalculationSettings from(AppConfig config) {
        return new CalculationSettings(
                config.calculationMethodOption(),
                config.madhabOption(),
                config.highLatitudeRuleOption(),
                config.getCustomFajrAngle(),
                config.getCustomIshaAngle(),
                config.getManualAdjustments());
    }

    /**
     * @return the default conventions (Muslim World League, Shafi)
     */
    public static CalculationSettings defaults() {
        return new CalculationSettings(
                CalculationMethodOption.MUSLIM_WORLD_LEAGUE,
                MadhabOption.SHAFI,
                HighLatitudeRuleOption.MIDDLE_OF_THE_NIGHT,
                CalculationMethodOption.DEFAULT_CUSTOM_FAJR_ANGLE,
                CalculationMethodOption.DEFAULT_CUSTOM_ISHA_ANGLE,
                new int[ADJUSTMENT_COUNT]);
    }

    @Override
    public int[] manualAdjustments() {
        return manualAdjustments.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CalculationSettings that)) {
            return false;
        }
        return method == that.method
                && madhab == that.madhab
                && highLatitudeRule == that.highLatitudeRule
                && Double.compare(customFajrAngle, that.customFajrAngle) == 0
                && Double.compare(customIshaAngle, that.customIshaAngle) == 0
                && Arrays.equals(manualAdjustments, that.manualAdjustments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, madhab, highLatitudeRule, customFajrAngle, customIshaAngle)
                * 31 + Arrays.hashCode(manualAdjustments);
    }

    @Override
    public String toString() {
        return "CalculationSettings[" + method + ", " + madhab + ", " + highLatitudeRule
                + ", adjustments=" + Arrays.toString(manualAdjustments) + "]";
    }
}
