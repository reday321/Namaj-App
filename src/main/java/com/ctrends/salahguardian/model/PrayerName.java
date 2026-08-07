package com.ctrends.salahguardian.model;

import com.ctrends.salahguardian.i18n.Messages;

import java.util.Optional;

/**
 * The six daily time markers reported by the calculation engine.
 *
 * <p>{@link #SUNRISE} is included because it bounds the Fajr window, but it is
 * not a prayer: {@link #isObligatory()} returns {@code false} for it and the
 * reminder scheduler skips it unless explicitly asked not to.</p>
 *
 * @author CTrends Software
 */
public enum PrayerName {

    /** Dawn prayer. */
    FAJR("Fajr", "الفجر", true),

    /** Sunrise - end of the Fajr window, not a prayer. */
    SUNRISE("Sunrise", "الشروق", false),

    /** Midday prayer. */
    DHUHR("Dhuhr", "الظهر", true),

    /** Afternoon prayer. */
    ASR("Asr", "العصر", true),

    /** Sunset prayer. */
    MAGHRIB("Maghrib", "المغرب", true),

    /** Night prayer. */
    ISHA("Isha", "العشاء", true);

    private final String englishName;
    private final String arabicName;
    private final boolean obligatory;

    PrayerName(String englishName, String arabicName, boolean obligatory) {
        this.englishName = englishName;
        this.arabicName = arabicName;
        this.obligatory = obligatory;
    }

    /**
     * The prayer's name in the active interface language.
     *
     * <p>Resolved through {@link Messages} on every call rather than cached,
     * so a language change takes effect immediately without rebuilding the
     * enum constants.</p>
     *
     * @return the localised name, e.g. {@code "Maghrib"} or {@code "মাগরিব"}
     */
    public String displayName() {
        return Messages.get("prayer." + name());
    }

    /**
     * @return the untranslated English name, used in log messages so that logs
     *         stay readable regardless of the user's interface language
     */
    public String englishName() {
        return englishName;
    }

    /**
     * @return the Arabic script name, e.g. {@code "المغرب"}
     */
    public String arabicName() {
        return arabicName;
    }

    /**
     * @return {@code true} for the five obligatory prayers, {@code false} for sunrise
     */
    public boolean isObligatory() {
        return obligatory;
    }

    /**
     * On Fridays the Dhuhr slot is replaced by the congregational Jumu'ah
     * prayer; the dashboard and notifications use this label.
     *
     * @param friday whether the day in question is a Friday
     * @return the label to render for this entry
     */
    public String displayName(boolean friday) {
        return friday && this == DHUHR ? Messages.get("prayer.JUMUAH") : displayName();
    }

    /**
     * Case-insensitive lookup that never throws.
     *
     * @param raw candidate name, may be {@code null}
     * @return the matching constant when one exists
     */
    public static Optional<PrayerName> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        for (PrayerName prayer : values()) {
            if (prayer.name().equalsIgnoreCase(raw.trim())) {
                return Optional.of(prayer);
            }
        }
        return Optional.empty();
    }
}
