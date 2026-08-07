package com.ctrends.salahguardian.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The application's translation lookup.
 *
 * <h2>Why a mutable singleton rather than an injected service</h2>
 * Language is a display concern that reaches almost every class that renders
 * text, including static factory methods and enum accessors that have no
 * constructor to inject into. Threading a {@code Messages} instance through all
 * of them would add far more coupling than it removes. The trade-off is
 * accepted deliberately, and confined: the mutable state is a single
 * {@code volatile} bundle reference, and everything else is a pure function of
 * it.
 *
 * <h2>Missing translations</h2>
 * A key absent from the active bundle falls back to the English bundle, and a
 * key absent from both returns the key itself wrapped in exclamation marks.
 * A half-translated language therefore degrades to mixed English rather than to
 * a crash - which matters because a {@code MissingResourceException} thrown
 * while building a scene graph would take the window down.
 *
 * @author CTrends Software
 */
public final class Messages {

    private static final Logger LOG = LoggerFactory.getLogger(Messages.class);

    /** Classpath base name of the translation bundles. */
    public static final String BUNDLE = "i18n.messages";

    /** Listeners notified after every language change, so views can rebuild. */
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile ResourceBundle bundle = load(Locale.ENGLISH);
    private static volatile ResourceBundle fallback = load(Locale.ENGLISH);
    private static volatile Locale locale = Locale.ENGLISH;
    private static volatile boolean localNumerals;

    private Messages() {
        // utility class
    }

    /**
     * Switches the active language.
     *
     * @param language      the language to display in
     * @param useOwnNumerals whether to render digits in the language's own
     *                       numeral set, e.g. Bengali {@code ১২:০৫}
     */
    public static void setLanguage(Language language, boolean useOwnNumerals) {
        Language resolved = language == null ? Language.SYSTEM : language;
        Locale target = resolved.toLocale();
        localNumerals = useOwnNumerals && resolved.hasOwnNumerals();

        // The Unicode "nu" extension is what makes java.time render digits in
        // the language's own numeral set; without it CLDR uses Latin digits.
        locale = localNumerals
                ? Locale.forLanguageTag(target.toLanguageTag() + "-u-nu-" + numberingSystem(target))
                : target;
        bundle = load(target);
        LOG.info("Interface language set to {} (locale {}, own numerals: {})",
                resolved.englishName(), locale.toLanguageTag(), localNumerals);
        LISTENERS.forEach(listener -> {
            try {
                listener.run();
            } catch (RuntimeException e) {
                LOG.warn("Language change listener failed", e);
            }
        });
    }

    /**
     * @return the locale to format dates, times and numbers with
     */
    public static Locale locale() {
        return locale;
    }

    /**
     * @return {@code true} when digits render in a non-Latin numeral set
     */
    public static boolean usesLocalNumerals() {
        return localNumerals;
    }

    /**
     * Looks up a translated string.
     *
     * @param key the bundle key, e.g. {@code dashboard.nextPrayer}
     * @return the translation, or an English fallback, or {@code !key!}
     */
    public static String get(String key) {
        if (key == null) {
            return "";
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException primaryMiss) {
            try {
                LOG.debug("Missing translation for '{}' in {} - using English", key, locale);
                return fallback.getString(key);
            } catch (MissingResourceException bothMiss) {
                LOG.warn("Untranslated key '{}' in every bundle", key);
                return '!' + key + '!';
            }
        }
    }

    /**
     * Looks up a translated string and substitutes positional arguments.
     *
     * <p>Uses {@link MessageFormat}, so translations may reorder the
     * placeholders - essential when the target language puts the verb or the
     * number somewhere English does not.</p>
     *
     * @param key       the bundle key
     * @param arguments values for the {@code {0}}, {@code {1}} placeholders
     * @return the formatted translation
     */
    public static String format(String key, Object... arguments) {
        String pattern = get(key);
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        try {
            return new MessageFormat(pattern, locale).format(arguments);
        } catch (IllegalArgumentException e) {
            LOG.warn("Malformed message pattern for key '{}': {}", key, pattern);
            return pattern;
        }
    }

    /**
     * Builds a date/time formatter bound to the active language.
     *
     * <p>Both halves matter. {@code ofPattern(pattern, locale)} alone gives
     * translated month and day names but leaves the digits as ASCII, because a
     * {@code DateTimeFormatter} defaults to {@link DecimalStyle#STANDARD} and
     * ignores the locale's {@code nu} numbering-system extension. Attaching
     * {@code DecimalStyle.of(locale)} is what actually produces
     * {@code ০৬:৩৭} rather than {@code 06:37}.</p>
     *
     * @param pattern a {@link DateTimeFormatter} pattern
     * @return a formatter in the active language and numeral set
     */
    public static DateTimeFormatter formatter(String pattern) {
        Locale active = locale;
        return DateTimeFormatter.ofPattern(pattern, active)
                .withDecimalStyle(DecimalStyle.of(active));
    }

    /**
     * Converts the Latin digits in a string into the active numeral set.
     *
     * <p>Needed for text that java.time does not format, such as a countdown
     * assembled by hand from minutes and seconds.</p>
     *
     * @param text any string, may be {@code null}
     * @return the text with its digits localised, unchanged when Latin digits
     *         are in use
     */
    public static String localiseDigits(String text) {
        if (text == null || text.isEmpty() || !localNumerals) {
            return text;
        }
        int zero = zeroDigitFor(locale);
        if (zero == '0') {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c >= '0' && c <= '9' ? (char) (zero + (c - '0')) : c);
        }
        return out.toString();
    }

    /**
     * Registers a callback invoked after every language change.
     *
     * @param listener the callback, typically a view rebuild
     */
    public static void addChangeListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /**
     * Removes a previously registered callback.
     *
     * @param listener the callback to detach
     */
    public static void removeChangeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    private static ResourceBundle load(Locale target) {
        try {
            // Java 9+ reads .properties bundles as UTF-8, so the translations
            // are stored in their own script rather than as ASCII escapes.
            // (Note: a literal backslash-u sequence cannot appear even in a
            // comment - javac decodes unicode escapes before it tokenises.)
            return ResourceBundle.getBundle(BUNDLE, target);
        } catch (MissingResourceException e) {
            LOG.error("No translation bundle for {} - falling back to English", target, e);
            return ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH);
        }
    }

    /** CLDR numbering system identifier for a language's own digits. */
    private static String numberingSystem(Locale target) {
        return "bn".equals(target.getLanguage()) ? "beng" : "latn";
    }

    /** The code point of digit zero in the active numeral set. */
    private static int zeroDigitFor(Locale target) {
        String numbers = target.getUnicodeLocaleType("nu");
        return "beng".equals(numbers) ? '০' : '0';
    }
}
