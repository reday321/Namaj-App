package com.ctrends.salahguardian.i18n;

import java.util.Locale;

/**
 * The interface languages the application ships with.
 *
 * <p>Each constant carries its own name written <em>in that language</em>, so
 * the settings combo box is readable to someone who cannot read the current
 * one - a user who has accidentally switched to a script they do not know must
 * still be able to switch back.</p>
 *
 * @author CTrends Software
 */
public enum Language {

    /** Follow the operating system's locale, falling back to English. */
    SYSTEM("System default", "System default", null),

    /** English. */
    ENGLISH("English", "English", Locale.ENGLISH),

    /** Bengali / Bangla, as used in Bangladesh. */
    BENGALI("Bengali", "বাংলা", Locale.forLanguageTag("bn-BD"));

    private final String englishName;
    private final String nativeName;
    private final Locale locale;

    Language(String englishName, String nativeName, Locale locale) {
        this.englishName = englishName;
        this.nativeName = nativeName;
        this.locale = locale;
    }

    /**
     * @return the language's name in English, used in logs
     */
    public String englishName() {
        return englishName;
    }

    /**
     * @return the language's own name for its own script, e.g. {@code বাংলা}
     */
    public String nativeName() {
        return nativeName;
    }

    /**
     * Resolves the locale to format dates, numbers and messages with.
     *
     * <p>{@link #SYSTEM} consults {@code Locale.getDefault()} and only honours
     * it when the application actually has a translation for it; anything else
     * degrades to English rather than showing untranslated keys.</p>
     *
     * @return a locale a resource bundle exists for, never {@code null}
     */
    public Locale toLocale() {
        if (locale != null) {
            return locale;
        }
        String systemLanguage = Locale.getDefault().getLanguage();
        for (Language candidate : values()) {
            if (candidate.locale != null
                    && candidate.locale.getLanguage().equals(systemLanguage)) {
                return candidate.locale;
            }
        }
        return Locale.ENGLISH;
    }

    /**
     * @return {@code true} when this language is normally written with a
     *         non-Latin digit set, so the numeral preference is worth offering
     */
    public boolean hasOwnNumerals() {
        return this == BENGALI
                || (this == SYSTEM && toLocale().getLanguage().equals("bn"));
    }

    /**
     * Null-safe, case-insensitive parsing used when reading the config file.
     *
     * @param raw      persisted name
     * @param fallback value returned when {@code raw} does not match
     * @return the resolved language
     */
    public static Language parseOrDefault(String raw, Language fallback) {
        if (raw != null) {
            for (Language language : values()) {
                if (language.name().equalsIgnoreCase(raw.trim())) {
                    return language;
                }
            }
        }
        return fallback;
    }

    @Override
    public String toString() {
        // Both forms, so the list stays navigable whatever the current language.
        return englishName.equals(nativeName) ? nativeName : nativeName + " (" + englishName + ")";
    }
}
