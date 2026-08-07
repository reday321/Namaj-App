package com.ctrends.salahguardian.config;

import com.ctrends.salahguardian.i18n.Messages;

/**
 * The visual themes shipped with the application.
 *
 * <p>Each constant maps to a stylesheet on the classpath under {@code /css}.</p>
 *
 * @author CTrends Software
 */
public enum Theme {

    /** Default dark palette with the green accent. */
    DARK("Dark", "/css/dark.css"),

    /** Light palette for bright environments. */
    LIGHT("Light", "/css/light.css"),

    /** Very dark, low contrast palette intended for night use. */
    MIDNIGHT("Midnight", "/css/midnight.css");

    private final String displayName;
    private final String stylesheet;

    Theme(String displayName, String stylesheet) {
        this.displayName = displayName;
        this.stylesheet = stylesheet;
    }

    /**
     * @return label rendered in the settings combo box
     */
    public String displayName() {
        return Messages.get("theme." + name());
    }

    /**
     * @return classpath location of this theme's stylesheet
     */
    public String stylesheet() {
        return stylesheet;
    }

    /**
     * Null-safe, case-insensitive parsing used when reading the config file.
     *
     * @param raw      persisted name
     * @param fallback value returned when {@code raw} does not match
     * @return the resolved theme
     */
    public static Theme parseOrDefault(String raw, Theme fallback) {
        if (raw != null) {
            for (Theme theme : values()) {
                if (theme.name().equalsIgnoreCase(raw.trim())) {
                    return theme;
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
