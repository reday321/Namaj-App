package com.ctrends.salahguardian.model;

import com.batoulapps.adhan.Madhab;

/**
 * Juristic school selection, which only affects the Asr calculation.
 *
 * <p>Shafi (also Maliki and Hanbali) place Asr when an object's shadow equals
 * its own length; Hanafi places it at twice the object's length.</p>
 *
 * @author CTrends Software
 */
public enum MadhabOption {

    /** Shafi'i, Maliki and Hanbali - shadow ratio 1. */
    SHAFI("Shafi / Maliki / Hanbali", Madhab.SHAFI),

    /** Hanafi - shadow ratio 2, giving a noticeably later Asr. */
    HANAFI("Hanafi", Madhab.HANAFI);

    private final String displayName;
    private final Madhab adhanMadhab;

    MadhabOption(String displayName, Madhab adhanMadhab) {
        this.displayName = displayName;
        this.adhanMadhab = adhanMadhab;
    }

    /**
     * @return label rendered in the settings combo box
     */
    public String displayName() {
        return displayName;
    }

    /**
     * @return the equivalent constant in the adhan-java library
     */
    public Madhab toAdhanMadhab() {
        return adhanMadhab;
    }

    /**
     * Null-safe, case-insensitive parsing used when reading the config file.
     *
     * @param raw persisted name
     * @param fallback value returned when {@code raw} does not match
     * @return the resolved option
     */
    public static MadhabOption parseOrDefault(String raw, MadhabOption fallback) {
        if (raw != null) {
            for (MadhabOption option : values()) {
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
