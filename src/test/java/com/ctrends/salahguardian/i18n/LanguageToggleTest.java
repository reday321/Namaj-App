package com.ctrends.salahguardian.i18n;

import com.ctrends.salahguardian.viewmodel.DashboardViewModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for the language resolution behind the dashboard's EN / বাংলা
 * toggle.
 *
 * <p>Only the pure resolution logic is exercised here; the toggle's wiring to
 * {@code ConfigService} and {@code Messages} needs a JavaFX toolkit and is
 * covered by the manual verification described in the user manual.</p>
 */
class LanguageToggleTest {

    @Test
    @DisplayName("an explicit language resolves to itself")
    void explicitLanguageResolvesToItself() {
        assertEquals(Language.ENGLISH, DashboardViewModel.effectiveLanguage(Language.ENGLISH));
        assertEquals(Language.BENGALI, DashboardViewModel.effectiveLanguage(Language.BENGALI));
    }

    @Test
    @DisplayName("SYSTEM resolves to the concrete language it currently means")
    void systemResolvesToConcreteLanguage() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("bn-BD"));
            assertEquals(Language.BENGALI, DashboardViewModel.effectiveLanguage(Language.SYSTEM));

            Locale.setDefault(Locale.US);
            assertEquals(Language.ENGLISH, DashboardViewModel.effectiveLanguage(Language.SYSTEM));

            // A language with no bundle must still yield something selectable,
            // otherwise the toggle would have no segment highlighted.
            Locale.setDefault(Locale.JAPANESE);
            assertEquals(Language.ENGLISH, DashboardViewModel.effectiveLanguage(Language.SYSTEM));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("resolution never returns SYSTEM, so a segment is always highlighted")
    void neverReturnsSystem() {
        for (Language configured : Language.values()) {
            assertNotEquals(Language.SYSTEM, DashboardViewModel.effectiveLanguage(configured),
                    configured + " should resolve to a concrete language");
        }
        assertNotEquals(Language.SYSTEM, DashboardViewModel.effectiveLanguage(null));
    }

    @Test
    @DisplayName("each toggle segment is labelled in its own language")
    void segmentsAreSelfLabelling() {
        // The point of the toggle is that a user stranded in a script they
        // cannot read can still find their way back.
        assertEquals("বাংলা", Language.BENGALI.nativeName());
        assertEquals("English", Language.ENGLISH.nativeName());
    }
}
