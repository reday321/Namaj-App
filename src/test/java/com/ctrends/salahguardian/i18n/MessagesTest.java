package com.ctrends.salahguardian.i18n;

import com.ctrends.salahguardian.model.PrayerName;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.notification.PrayerNotificationComposer;
import com.ctrends.salahguardian.utils.TimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the internationalisation layer.
 *
 * <p>Every test restores English afterwards, because {@link Messages} holds
 * process-wide state and a leaked Bengali locale would break unrelated tests
 * that assert English strings.</p>
 */
class MessagesTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    @AfterEach
    void restoreEnglish() {
        Messages.setLanguage(Language.ENGLISH, false);
    }

    private PrayerTime prayer(PrayerName name, int hour, int minute) {
        return new PrayerTime(name, ZonedDateTime.of(
                LocalDate.of(2026, 8, 8), LocalTime.of(hour, minute), ZONE));
    }

    // ----- bundle integrity -------------------------------------------------

    /** Reads a bundle straight from the classpath, bypassing the cache. */
    private Set<String> keysOf(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream(resource)) {
            org.junit.jupiter.api.Assertions.assertNotNull(in, resource + " is missing");
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return new LinkedHashSet<>(properties.stringPropertyNames());
    }

    @Test
    @DisplayName("every English key has a Bengali translation, and none are orphaned")
    void bundlesHaveIdenticalKeys() throws IOException {
        Set<String> english = keysOf("/i18n/messages.properties");
        Set<String> bengali = keysOf("/i18n/messages_bn.properties");

        Set<String> missing = new LinkedHashSet<>(english);
        missing.removeAll(bengali);
        Set<String> orphaned = new LinkedHashSet<>(bengali);
        orphaned.removeAll(english);

        assertTrue(missing.isEmpty(), "keys missing from the Bengali bundle: " + missing);
        assertTrue(orphaned.isEmpty(), "keys in Bengali with no English original: " + orphaned);
        assertTrue(english.size() > 100, "the bundle looks truncated: " + english.size() + " keys");
    }

    @Test
    @DisplayName("no translation is left as an untranslated copy of the English")
    void bengaliIsActuallyTranslated() throws IOException {
        Properties en = new Properties();
        Properties bn = new Properties();
        try (InputStream a = MessagesTest.class.getResourceAsStream("/i18n/messages.properties");
             InputStream b = MessagesTest.class.getResourceAsStream("/i18n/messages_bn.properties")) {
            en.load(new java.io.InputStreamReader(a, StandardCharsets.UTF_8));
            bn.load(new java.io.InputStreamReader(b, StandardCharsets.UTF_8));
        }
        // A handful of keys are legitimately identical: the Arabic tagline and
        // placeholder-only bodies carry no translatable words.
        Set<String> allowedIdentical = Set.of("app.tagline", "notify.advanceBody", "notify.startBody");

        for (String key : en.stringPropertyNames()) {
            if (allowedIdentical.contains(key)) {
                continue;
            }
            assertNotEquals(en.getProperty(key), bn.getProperty(key),
                    "key '" + key + "' was never translated");
        }
    }

    // ----- lookup behaviour -------------------------------------------------

    @Test
    @DisplayName("returns the translation for the active language")
    void returnsActiveTranslation() {
        Messages.setLanguage(Language.ENGLISH, false);
        assertEquals("Settings", Messages.get("settings.title"));

        Messages.setLanguage(Language.BENGALI, false);
        assertEquals("সেটিংস", Messages.get("settings.title"));
    }

    @Test
    @DisplayName("an unknown key degrades to a visible marker instead of throwing")
    void unknownKeyDoesNotThrow() {
        assertEquals("!no.such.key!", Messages.get("no.such.key"));
        assertEquals("", Messages.get(null));
    }

    @Test
    @DisplayName("substitutes positional arguments")
    void formatsArguments() {
        Messages.setLanguage(Language.ENGLISH, false);
        assertEquals("until Asr", Messages.format("dashboard.until", "Asr"));
        assertEquals("via GeoClue", Messages.format("dashboard.via", "GeoClue"));
    }

    @Test
    @DisplayName("a translation may reorder the placeholders")
    void allowsReorderedPlaceholders() {
        // English: "Detected {0} via {1}."  Bengali: "{1} থেকে {0} শনাক্ত হয়েছে।"
        Messages.setLanguage(Language.BENGALI, false);
        String text = Messages.format("status.detected", "ঢাকা", "GeoClue");
        assertTrue(text.indexOf("GeoClue") < text.indexOf("ঢাকা"),
                "Bengali should place the source before the place name: " + text);
    }

    // ----- numerals ---------------------------------------------------------

    @Test
    @DisplayName("renders Bengali numerals when asked, Latin digits otherwise")
    void localisesDigits() {
        Messages.setLanguage(Language.BENGALI, true);
        assertEquals("১২:০৫", Messages.localiseDigits("12:05"));
        assertEquals("০১:৩০ PM", Messages.localiseDigits("01:30 PM"));
        assertTrue(Messages.usesLocalNumerals());

        Messages.setLanguage(Language.BENGALI, false);
        assertEquals("12:05", Messages.localiseDigits("12:05"));
        assertFalse(Messages.usesLocalNumerals());

        Messages.setLanguage(Language.ENGLISH, true);
        assertEquals("12:05", Messages.localiseDigits("12:05"),
                "English has no numerals of its own to switch to");
    }

    @Test
    @DisplayName("digit localisation leaves non-digits untouched and tolerates null")
    void digitLocalisationIsSafe() {
        Messages.setLanguage(Language.BENGALI, true);
        assertEquals("ফজর ০৪:১০", Messages.localiseDigits("ফজর 04:10"));
        org.junit.jupiter.api.Assertions.assertNull(Messages.localiseDigits(null));
        assertEquals("", Messages.localiseDigits(""));
    }

    // ----- integration with the domain --------------------------------------

    @Test
    @DisplayName("prayer names follow the active language")
    void prayerNamesAreTranslated() {
        Messages.setLanguage(Language.ENGLISH, false);
        assertEquals("Maghrib", PrayerName.MAGHRIB.displayName());
        assertEquals("Jumu'ah", PrayerName.DHUHR.displayName(true));

        Messages.setLanguage(Language.BENGALI, false);
        assertEquals("মাগরিব", PrayerName.MAGHRIB.displayName());
        assertEquals("যোহর", PrayerName.DHUHR.displayName());
        assertEquals("জুমুআ", PrayerName.DHUHR.displayName(true));
    }

    @Test
    @DisplayName("the English name stays English for log messages")
    void englishNameIsStableForLogs() {
        Messages.setLanguage(Language.BENGALI, true);
        assertEquals("Maghrib", PrayerName.MAGHRIB.englishName(),
                "logs must stay readable regardless of interface language");
    }

    @Test
    @DisplayName("prayer times render in Bengali numerals when enabled")
    void prayerTimesUseBengaliNumerals() {
        PrayerTime maghrib = prayer(PrayerName.MAGHRIB, 18, 37);

        Messages.setLanguage(Language.ENGLISH, false);
        assertEquals("06:37 PM", maghrib.formatted(false));
        assertEquals("18:37", maghrib.formatted(true));

        Messages.setLanguage(Language.BENGALI, true);
        String bengali = maghrib.formatted(false);
        assertTrue(bengali.startsWith("০৬:৩৭"),
                "expected Bengali numerals but got " + bengali);
    }

    @Test
    @DisplayName("durations are translated and pluralised")
    void durationsAreTranslated() {
        Messages.setLanguage(Language.ENGLISH, false);
        assertEquals("5 minutes", TimeUtils.humanise(Duration.ofMinutes(5)));
        assertEquals("1 hour", TimeUtils.humanise(Duration.ofMinutes(60)));

        Messages.setLanguage(Language.BENGALI, true);
        assertEquals("৫ মিনিট", TimeUtils.humanise(Duration.ofMinutes(5)));
        assertEquals("১ ঘণ্টা", TimeUtils.humanise(Duration.ofMinutes(60)));
    }

    @Test
    @DisplayName("Hijri dates are translated")
    void hijriDatesAreTranslated() {
        Messages.setLanguage(Language.ENGLISH, false);
        assertTrue(TimeUtils.toHijriString(LocalDate.of(2026, 8, 8)).endsWith("AH"));

        Messages.setLanguage(Language.BENGALI, true);
        String bengali = TimeUtils.toHijriString(LocalDate.of(2026, 8, 8));
        assertTrue(bengali.endsWith("হিজরি"), "expected a Bengali era suffix: " + bengali);
        assertTrue(bengali.codePoints().anyMatch(c -> c >= '০' && c <= '৯'),
                "expected Bengali digits: " + bengali);
    }

    @Test
    @DisplayName("notifications are composed in the active language")
    void notificationsAreTranslated() {
        PrayerNotificationComposer composer = new PrayerNotificationComposer();
        PrayerTime asr = prayer(PrayerName.ASR, 15, 29);

        Messages.setLanguage(Language.ENGLISH, false);
        assertEquals("🕌 Asr prayer starts in 5 minutes.",
                composer.advanceWarning(asr, Duration.ofMinutes(5), false, false).title());
        assertEquals("🕌 It's time for Asr.", composer.prayerStart(asr, false, false).title());

        Messages.setLanguage(Language.BENGALI, true);
        assertEquals("🕌 আসর নামাজ শুরু হবে ৫ মিনিট পরে।",
                composer.advanceWarning(asr, Duration.ofMinutes(5), false, false).title());
        assertEquals("🕌 আসর নামাজের সময় হয়েছে।",
                composer.prayerStart(asr, false, false).title());
    }

    // ----- language plumbing ------------------------------------------------

    @ParameterizedTest
    @EnumSource(Language.class)
    @DisplayName("every language resolves to a usable locale and bundle")
    void everyLanguageResolves(Language language) {
        Messages.setLanguage(language, true);
        assertFalse(Messages.get("app.name").startsWith("!"),
                language + " should resolve app.name");
        org.junit.jupiter.api.Assertions.assertNotNull(Messages.locale());
    }

    @Test
    @DisplayName("SYSTEM falls back to English for an unsupported system locale")
    void systemFallsBackToEnglish() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.JAPANESE);
            assertEquals(Locale.ENGLISH, Language.SYSTEM.toLocale());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("SYSTEM picks Bengali when that is the system locale")
    void systemPicksBengali() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("bn-BD"));
            assertEquals("bn", Language.SYSTEM.toLocale().getLanguage());
            assertTrue(Language.SYSTEM.hasOwnNumerals());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("listeners fire on every language change")
    void notifiesListeners() {
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;
        Messages.addChangeListener(listener);
        try {
            Messages.setLanguage(Language.BENGALI, true);
            Messages.setLanguage(Language.ENGLISH, false);
            assertEquals(2, calls.get());
        } finally {
            Messages.removeChangeListener(listener);
        }
    }

    @Test
    @DisplayName("a throwing listener does not prevent the language change")
    void survivesThrowingListener() {
        Runnable bad = () -> {
            throw new IllegalStateException("listener blew up");
        };
        Messages.addChangeListener(bad);
        try {
            Messages.setLanguage(Language.BENGALI, false);
            assertEquals("সেটিংস", Messages.get("settings.title"));
        } finally {
            Messages.removeChangeListener(bad);
        }
    }

    @Test
    @DisplayName("language names are shown in their own script")
    void languageNamesAreNative() {
        assertEquals("বাংলা", Language.BENGALI.nativeName());
        assertTrue(Language.BENGALI.toString().contains("বাংলা"));
        assertTrue(Language.BENGALI.toString().contains("Bengali"),
                "both scripts, so the list stays navigable in either language");
    }

    @Test
    @DisplayName("parses persisted names and falls back safely")
    void parsesPersistedNames() {
        assertEquals(Language.BENGALI, Language.parseOrDefault("BENGALI", Language.SYSTEM));
        assertEquals(Language.BENGALI, Language.parseOrDefault(" bengali ", Language.SYSTEM));
        assertEquals(Language.SYSTEM, Language.parseOrDefault("klingon", Language.SYSTEM));
        assertEquals(Language.SYSTEM, Language.parseOrDefault(null, Language.SYSTEM));
    }
}
