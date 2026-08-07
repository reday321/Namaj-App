package com.ctrends.salahguardian.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GeoLocation}.
 */
class GeoLocationTest {

    @Test
    @DisplayName("rejects coordinates outside the valid range")
    void rejectsOutOfRangeCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeoLocation(91.0, 0.0, "", "", "", LocationSource.MANUAL, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> new GeoLocation(0.0, 181.0, "", "", "", LocationSource.MANUAL, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> new GeoLocation(Double.NaN, 0.0, "", "", "", LocationSource.MANUAL, Instant.EPOCH));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.0, -45.5, 0.0, 21.4225, 90.0})
    @DisplayName("accepts every latitude within range")
    void acceptsValidLatitudes(double latitude) {
        assertTrue(GeoLocation.isValidLatitude(latitude));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.001, 90.001, Double.POSITIVE_INFINITY})
    @DisplayName("rejects every latitude outside range")
    void rejectsInvalidLatitudes(double latitude) {
        assertFalse(GeoLocation.isValidLatitude(latitude));
    }

    @Test
    @DisplayName("normalises null text fields to empty strings")
    void normalisesNullText() {
        GeoLocation location = new GeoLocation(0, 0, null, null, null, null, null);
        assertEquals("", location.city());
        assertEquals("", location.country());
        assertEquals("", location.timeZoneId());
        assertEquals(LocationSource.MANUAL, location.source());
        assertEquals(Instant.EPOCH, location.resolvedAt());
    }

    @Test
    @DisplayName("builds a City, Country label when both are known")
    void buildsFullDisplayLabel() {
        GeoLocation location = GeoLocation.of(41.0082, 28.9784, "Istanbul", "Turkey",
                LocationSource.IP_GEOLOCATION);
        assertEquals("Istanbul, Turkey", location.displayLabel());
    }

    @Test
    @DisplayName("falls back to coordinates when no place name is known")
    void fallsBackToCoordinateLabel() {
        GeoLocation location = GeoLocation.of(41.0082, -28.9784, "", "", LocationSource.GEOCLUE);
        assertEquals("41.0082°N, 28.9784°W", location.displayLabel());
    }

    @Test
    @DisplayName("measures the great-circle distance between two cities")
    void measuresDistance() {
        GeoLocation makkah = GeoLocation.MAKKAH;
        GeoLocation madinah = GeoLocation.of(24.4686, 39.6142, "Madinah", "Saudi Arabia",
                LocationSource.MANUAL);
        // The real road/air distance between the two cities is about 340 km.
        double distance = makkah.distanceKmTo(madinah);
        assertTrue(distance > 330 && distance < 350,
                "expected roughly 340 km but measured " + distance);
    }

    @Test
    @DisplayName("reports zero distance to itself")
    void zeroDistanceToSelf() {
        assertEquals(0.0, GeoLocation.MAKKAH.distanceKmTo(GeoLocation.MAKKAH), 1e-9);
    }
}
