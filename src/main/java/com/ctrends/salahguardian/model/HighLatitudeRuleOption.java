package com.ctrends.salahguardian.model;

import com.ctrends.salahguardian.i18n.Messages;

import com.batoulapps.adhan.HighLatitudeRule;

/**
 * Strategy for estimating Fajr and Isha at latitudes where the sun never
 * reaches the required twilight angle during part of the year.
 *
 * @author CTrends Software
 */
public enum HighLatitudeRuleOption {

    /** Fajr and Isha never cross the midpoint between sunset and sunrise. */
    MIDDLE_OF_THE_NIGHT("Middle of the night", HighLatitudeRule.MIDDLE_OF_THE_NIGHT),

    /** The night is split 1/7 for Isha and 1/7 for Fajr. */
    SEVENTH_OF_THE_NIGHT("Seventh of the night", HighLatitudeRule.SEVENTH_OF_THE_NIGHT),

    /** Night fractions derived from the configured twilight angles. */
    TWILIGHT_ANGLE("Twilight angle", HighLatitudeRule.TWILIGHT_ANGLE);

    private final String displayName;
    private final HighLatitudeRule adhanRule;

    HighLatitudeRuleOption(String displayName, HighLatitudeRule adhanRule) {
        this.displayName = displayName;
        this.adhanRule = adhanRule;
    }

    /**
     * @return label rendered in the settings combo box
     */
    public String displayName() {
        return Messages.get("highLatitude." + name());
    }

    /**
     * @return the equivalent constant in the adhan-java library
     */
    public HighLatitudeRule toAdhanRule() {
        return adhanRule;
    }

    /**
     * Null-safe, case-insensitive parsing used when reading the config file.
     *
     * @param raw      persisted name
     * @param fallback value returned when {@code raw} does not match
     * @return the resolved option
     */
    public static HighLatitudeRuleOption parseOrDefault(String raw, HighLatitudeRuleOption fallback) {
        if (raw != null) {
            for (HighLatitudeRuleOption option : values()) {
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
