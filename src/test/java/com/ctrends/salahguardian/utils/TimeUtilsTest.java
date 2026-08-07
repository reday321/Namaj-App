package com.ctrends.salahguardian.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TimeUtils}.
 */
class TimeUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "0,       less than a minute",
            "1,       1 minute",
            "5,       5 minutes",
            "59,      59 minutes",
            "60,      1 hour",
            "61,      1 hour 1 minute",
            "125,     2 hours 5 minutes",
            "120,     2 hours"
    })
    @DisplayName("describes a duration in words, singular and plural")
    void humanisesDurations(long minutes, String expected) {
        assertEquals(expected, TimeUtils.humanise(Duration.ofMinutes(minutes)));
    }

    @Test
    @DisplayName("treats a negative duration as zero")
    void humanisesNegativeAsZero() {
        assertEquals("less than a minute", TimeUtils.humanise(Duration.ofMinutes(-10)));
    }

    @ParameterizedTest
    @CsvSource({
            "0,      00:00",
            "59,     00:59",
            "60,     01:00",
            "3599,   59:59",
            "3600,   1:00:00",
            "7325,   2:02:05"
    })
    @DisplayName("formats a countdown clock")
    void formatsCountdown(long seconds, String expected) {
        assertEquals(expected, TimeUtils.countdown(Duration.ofSeconds(seconds)));
    }

    @Test
    @DisplayName("clamps a negative countdown to zero")
    void clampsNegativeCountdown() {
        assertEquals("00:00", TimeUtils.countdown(Duration.ofSeconds(-30)));
    }

    @Test
    @DisplayName("converts a Gregorian date to its Hijri equivalent")
    void convertsToHijri() {
        String hijri = TimeUtils.toHijriString(LocalDate.of(2026, 8, 7));
        assertTrue(hijri.endsWith(" AH"), "expected an AH suffix but got " + hijri);
        assertTrue(hijri.matches("\\d{1,2} [\\p{L}' -]+ \\d{4} AH"),
                "unexpected Hijri format: " + hijri);
    }

    @Test
    @DisplayName("names every Hijri month and degrades gracefully out of range")
    void namesHijriMonths() {
        assertEquals("Muharram", TimeUtils.hijriMonthName(1));
        assertEquals("Ramadan", TimeUtils.hijriMonthName(TimeUtils.RAMADAN_MONTH));
        assertEquals("Dhu al-Hijjah", TimeUtils.hijriMonthName(12));
        assertEquals("0", TimeUtils.hijriMonthName(0));
        assertEquals("13", TimeUtils.hijriMonthName(13));
    }

    @Test
    @DisplayName("detects Ramadan")
    void detectsRamadan() {
        // Ramadan 1446 ran from roughly 1 to 30 March 2025.
        assertTrue(TimeUtils.isRamadan(LocalDate.of(2025, 3, 15)),
                "mid March 2025 should fall inside Ramadan 1446");
        assertFalse(TimeUtils.isRamadan(LocalDate.of(2025, 8, 15)));
    }

    @Test
    @DisplayName("resolves a valid zone id and falls back to the system zone otherwise")
    void resolvesZones() {
        assertEquals(ZoneId.of("Europe/Istanbul"), TimeUtils.resolveZone("Europe/Istanbul"));
        assertEquals(ZoneId.of("Europe/Istanbul"), TimeUtils.resolveZone("  Europe/Istanbul  "));
        assertEquals(ZoneId.systemDefault(), TimeUtils.resolveZone(null));
        assertEquals(ZoneId.systemDefault(), TimeUtils.resolveZone(""));
        assertEquals(ZoneId.systemDefault(), TimeUtils.resolveZone("Mars/Olympus_Mons"));
    }

    @Test
    @DisplayName("converts a legacy Date, tolerating null")
    void convertsLegacyDate() {
        java.util.Date date = new java.util.Date(1_700_000_000_000L);
        var zoned = TimeUtils.toZoned(date, ZoneId.of("UTC"));
        assertEquals(1_700_000_000L, zoned.toEpochSecond());
        org.junit.jupiter.api.Assertions.assertNull(TimeUtils.toZoned((java.util.Date) null, ZoneId.of("UTC")));
    }
}
