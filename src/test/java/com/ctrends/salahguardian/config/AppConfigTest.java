package com.ctrends.salahguardian.config;

import com.ctrends.salahguardian.model.CalculationMethodOption;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.LocationSource;
import com.ctrends.salahguardian.model.MadhabOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AppConfig}, focusing on the self-repair performed by
 * {@link AppConfig#normalise()} - the mechanism that keeps a hand-edited or
 * outdated config.json from breaking start-up.
 */
class AppConfigTest {

    @Test
    @DisplayName("ships with sensible defaults")
    void hasSensibleDefaults() {
        AppConfig config = new AppConfig();
        assertEquals(5, config.getReminderMinutes());
        assertEquals(300, config.getFocusDurationSeconds());
        assertTrue(config.isNotificationsEnabled());
        assertTrue(config.isAutoDetectLocation());
        assertFalse(config.isSilentMode());
        assertEquals(Theme.DARK, config.themeOption());
        assertEquals(CalculationMethodOption.MUSLIM_WORLD_LEAGUE, config.calculationMethodOption());
        assertEquals(MadhabOption.SHAFI, config.madhabOption());
    }

    @Test
    @DisplayName("repairs out-of-range numeric values")
    void repairsOutOfRangeValues() {
        AppConfig config = new AppConfig();
        config.setLatitude(1000);
        config.setLongitude(-2000);
        config.setReminderMinutes(999);
        config.setFocusDurationSeconds(1);
        config.setFridayReminderHour(48);
        config.setCustomFajrAngle(-4);

        config.normalise();

        assertEquals(GeoLocation.MAKKAH.latitude(), config.getLatitude(), 1e-9);
        assertEquals(GeoLocation.MAKKAH.longitude(), config.getLongitude(), 1e-9);
        assertEquals(60, config.getReminderMinutes());
        assertEquals(30, config.getFocusDurationSeconds());
        assertEquals(23, config.getFridayReminderHour());
        assertEquals(CalculationMethodOption.DEFAULT_CUSTOM_FAJR_ANGLE,
                config.getCustomFajrAngle(), 1e-9);
    }

    @Test
    @DisplayName("repairs a manual adjustment array of the wrong length")
    void repairsAdjustmentArray() {
        AppConfig config = new AppConfig();
        config.setManualAdjustments(new int[]{1, 2});
        config.normalise();
        assertArrayEquals(new int[6], config.getManualAdjustments());

        config.setManualAdjustments(null);
        config.normalise();
        assertArrayEquals(new int[6], config.getManualAdjustments());

        config.setManualAdjustments(new int[]{500, -500, 0, 0, 0, 0});
        config.normalise();
        assertEquals(120, config.getManualAdjustments()[0]);
        assertEquals(-120, config.getManualAdjustments()[1]);
    }

    @Test
    @DisplayName("falls back to defaults for unknown enum names")
    void fallsBackForUnknownEnums() {
        AppConfig config = new AppConfig();
        config.setCalculationMethod("SOME_METHOD_THAT_DOES_NOT_EXIST");
        config.setMadhab(null);
        config.setTheme("neon");

        assertEquals(CalculationMethodOption.MUSLIM_WORLD_LEAGUE, config.calculationMethodOption());
        assertEquals(MadhabOption.SHAFI, config.madhabOption());
        assertEquals(Theme.DARK, config.themeOption());
    }

    @Test
    @DisplayName("round-trips a location through the config fields")
    void roundTripsLocation() {
        AppConfig config = new AppConfig();
        GeoLocation istanbul = new GeoLocation(41.0082, 28.9784, "Istanbul", "Turkey",
                "Europe/Istanbul", LocationSource.GEOCLUE, Instant.ofEpochSecond(1_700_000_000L));

        config.applyLocation(istanbul);
        GeoLocation restored = config.toGeoLocation();

        assertEquals(istanbul.latitude(), restored.latitude(), 1e-9);
        assertEquals(istanbul.longitude(), restored.longitude(), 1e-9);
        assertEquals("Istanbul", restored.city());
        assertEquals("Europe/Istanbul", restored.timeZoneId());
        assertEquals(LocationSource.GEOCLUE, restored.source());
        assertTrue(config.hasStoredLocation());
    }

    @Test
    @DisplayName("reports no stored location before the first detection")
    void reportsNoStoredLocationInitially() {
        assertFalse(new AppConfig().hasStoredLocation());
    }

    @Test
    @DisplayName("silent mode suppresses notifications without clearing the preference")
    void silentModeSuppressesNotifications() {
        AppConfig config = new AppConfig();
        assertTrue(config.shouldNotify());

        config.setSilentMode(true);
        assertFalse(config.shouldNotify());
        assertTrue(config.isNotificationsEnabled(), "the underlying preference must be preserved");

        config.setSilentMode(false);
        assertTrue(config.shouldNotify());
    }

    @Test
    @DisplayName("copies detach the adjustment array from the original")
    void copyIsIndependent() {
        AppConfig original = new AppConfig();
        original.setCity("Cairo");
        original.setManualAdjustments(new int[]{1, 0, 0, 0, 0, 0});

        AppConfig copy = original.copy();
        copy.setCity("Amman");
        copy.getManualAdjustments()[0] = 9;

        assertEquals("Cairo", original.getCity());
        assertEquals(1, original.getManualAdjustments()[0]);
        assertNotSame(original.getManualAdjustments(), copy.getManualAdjustments());
    }
}
