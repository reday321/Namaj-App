package com.ctrends.salahguardian.model;

import com.ctrends.salahguardian.i18n.Messages;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable geographic position together with the metadata describing how
 * and when it was obtained.
 *
 * <p>Latitude and longitude are validated on construction, which guarantees
 * that every downstream consumer (prayer calculation, persistence, UI) can
 * treat a {@code GeoLocation} instance as trustworthy.</p>
 *
 * @param latitude  degrees north, in the range {@code [-90, 90]}
 * @param longitude degrees east, in the range {@code [-180, 180]}
 * @param city      best effort city name, never {@code null} (may be empty)
 * @param country   best effort country name, never {@code null} (may be empty)
 * @param timeZoneId IANA zone id such as {@code Europe/Istanbul}, may be empty
 * @param source    the strategy that produced this location
 * @param resolvedAt the instant the location was captured
 * @author CTrends Software
 */
public record GeoLocation(
        double latitude,
        double longitude,
        String city,
        String country,
        String timeZoneId,
        LocationSource source,
        Instant resolvedAt) {

    /** Fallback position (Makkah al-Mukarramah) used when every provider fails. */
    public static final GeoLocation MAKKAH = new GeoLocation(
            21.4225, 39.8262, "Makkah", "Saudi Arabia", "Asia/Riyadh",
            LocationSource.MANUAL, Instant.EPOCH);

    public GeoLocation {
        if (!isValidLatitude(latitude)) {
            throw new IllegalArgumentException("Latitude out of range [-90, 90]: " + latitude);
        }
        if (!isValidLongitude(longitude)) {
            throw new IllegalArgumentException("Longitude out of range [-180, 180]: " + longitude);
        }
        city = city == null ? "" : city.trim();
        country = country == null ? "" : country.trim();
        timeZoneId = timeZoneId == null ? "" : timeZoneId.trim();
        source = Objects.requireNonNullElse(source, LocationSource.MANUAL);
        resolvedAt = Objects.requireNonNullElse(resolvedAt, Instant.EPOCH);
    }

    /**
     * Convenience factory that stamps the location with the current instant.
     *
     * @param latitude  degrees north
     * @param longitude degrees east
     * @param city      city label, may be {@code null}
     * @param country   country label, may be {@code null}
     * @param source    producing strategy
     * @return a new location resolved "now"
     */
    public static GeoLocation of(double latitude, double longitude, String city,
                                 String country, LocationSource source) {
        return new GeoLocation(latitude, longitude, city, country, "", source, Instant.now());
    }

    /**
     * @param latitude candidate latitude
     * @return {@code true} when the value is a usable latitude
     */
    public static boolean isValidLatitude(double latitude) {
        return Double.isFinite(latitude) && latitude >= -90.0 && latitude <= 90.0;
    }

    /**
     * @param longitude candidate longitude
     * @return {@code true} when the value is a usable longitude
     */
    public static boolean isValidLongitude(double longitude) {
        return Double.isFinite(longitude) && longitude >= -180.0 && longitude <= 180.0;
    }

    /**
     * @return {@code "City, Country"}, degrading gracefully to coordinates when
     *         no reverse-geocoded label is available
     */
    public String displayLabel() {
        if (!city.isEmpty() && !country.isEmpty()) {
            return city + ", " + country;
        }
        if (!city.isEmpty()) {
            return city;
        }
        if (!country.isEmpty()) {
            return country;
        }
        return coordinateLabel();
    }

    /**
     * @return coordinates formatted as {@code 41.0082°N, 28.9784°E}
     */
    public String coordinateLabel() {
        // Locale.ROOT keeps the decimal point a dot regardless of locale;
        // the digits are then converted to the active numeral set separately.
        return Messages.localiseDigits(String.format(java.util.Locale.ROOT, "%.4f°%s, %.4f°%s",
                Math.abs(latitude), latitude >= 0 ? "N" : "S",
                Math.abs(longitude), longitude >= 0 ? "E" : "W"));
    }

    /**
     * Distance to another location using the haversine formula. Used to decide
     * whether a freshly detected position is different enough to warrant a
     * recalculation of the prayer schedule.
     *
     * @param other the location to measure against
     * @return great-circle distance in kilometres
     */
    public double distanceKmTo(GeoLocation other) {
        final double earthRadiusKm = 6371.0088;
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
