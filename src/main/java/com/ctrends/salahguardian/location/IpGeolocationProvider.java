package com.ctrends.salahguardian.location;

import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.LocationSource;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Approximates the position from the machine's public IP address.
 *
 * <p>Two independent endpoints are tried in order so that a single provider
 * being rate limited or offline does not defeat detection. Both are free,
 * require no API key and answer over HTTPS:</p>
 * <ol>
 *   <li>{@code https://ipapi.co/json/}</li>
 *   <li>{@code http://ip-api.com/json/} (plain HTTP is the only free tier)</li>
 * </ol>
 *
 * <p>Accuracy is city level at best, which is entirely adequate for prayer
 * times - a 25 km error moves Fajr by well under a minute. The user can always
 * override the result manually.</p>
 *
 * <p>This provider is the only part of the application that needs the internet,
 * and it is needed exactly once: after the first success the coordinates live in
 * the configuration file and every later start works offline.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class IpGeolocationProvider implements LocationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(IpGeolocationProvider.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final String USER_AGENT = "SalahGuardian/1.0 (Linux desktop prayer reminder)";

    private final HttpClient httpClient;
    private final List<Endpoint> endpoints;

    /**
     * Creates a provider using the default endpoint list.
     */
    public IpGeolocationProvider() {
        this(HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                defaultEndpoints());
    }

    /**
     * Creates a provider with an injected HTTP client and endpoint list, which
     * is what the unit tests use.
     *
     * @param httpClient the client to issue requests with
     * @param endpoints  the services to try, in order
     */
    public IpGeolocationProvider(HttpClient httpClient, List<Endpoint> endpoints) {
        this.httpClient = httpClient;
        this.endpoints = List.copyOf(endpoints);
    }

    @Override
    public LocationSource source() {
        return LocationSource.IP_GEOLOCATION;
    }

    @Override
    public boolean isAvailable() {
        // Cheap to attempt and self-limiting through its own timeouts, so it is
        // always considered available; a dead network simply yields empty().
        return true;
    }

    @Override
    public Optional<GeoLocation> resolve() {
        for (Endpoint endpoint : endpoints) {
            Optional<GeoLocation> location = query(endpoint);
            if (location.isPresent()) {
                LOG.info("IP geolocation via {} resolved {}", endpoint.name(),
                        location.get().displayLabel());
                return location;
            }
        }
        LOG.warn("Every IP geolocation endpoint failed - the machine is probably offline");
        return Optional.empty();
    }

    private Optional<GeoLocation> query(Endpoint endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.url()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                LOG.debug("{} answered HTTP {}", endpoint.name(), response.statusCode());
                return Optional.empty();
            }
            return parse(response.body(), endpoint);
        } catch (IOException e) {
            LOG.debug("{} unreachable: {}", endpoint.name(), e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.debug("{} returned something unusable", endpoint.name(), e);
            return Optional.empty();
        }
    }

    /**
     * Parses one endpoint's JSON body into a location.
     *
     * <p>Package private so the unit tests can exercise the parsing rules with
     * recorded payloads instead of live network calls.</p>
     *
     * @param body     the raw response body
     * @param endpoint the endpoint that produced it, describing the field names
     * @return the parsed location when the payload contained usable coordinates
     */
    Optional<GeoLocation> parse(String body, Endpoint endpoint) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject json = root.getAsJsonObject();

            // ip-api.com signals failure with {"status":"fail"}.
            if (json.has("status") && "fail".equalsIgnoreCase(asString(json, "status"))) {
                return Optional.empty();
            }
            // ipapi.co signals failure with {"error": true}.
            if (json.has("error") && json.get("error").isJsonPrimitive()
                    && json.get("error").getAsJsonPrimitive().isBoolean()
                    && json.get("error").getAsBoolean()) {
                return Optional.empty();
            }

            Double latitude = asDouble(json, endpoint.latitudeField());
            Double longitude = asDouble(json, endpoint.longitudeField());
            if (latitude == null || longitude == null
                    || !GeoLocation.isValidLatitude(latitude)
                    || !GeoLocation.isValidLongitude(longitude)) {
                return Optional.empty();
            }
            String city = asString(json, endpoint.cityField());
            String country = asString(json, endpoint.countryField());
            String timeZone = asString(json, endpoint.timeZoneField());

            return Optional.of(new GeoLocation(latitude, longitude, city, country, timeZone,
                    LocationSource.IP_GEOLOCATION, Instant.now()));
        } catch (JsonSyntaxException | IllegalStateException | NumberFormatException e) {
            LOG.debug("Malformed payload from {}", endpoint.name(), e);
            return Optional.empty();
        }
    }

    private static Double asDouble(JsonObject json, String field) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asString(JsonObject json, String field) {
        JsonElement element = json.get(field);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    /**
     * The default endpoint chain: an HTTPS service first, then a widely
     * mirrored HTTP one.
     *
     * @return the endpoints to try, in priority order
     */
    public static List<Endpoint> defaultEndpoints() {
        return List.of(
                new Endpoint("ipapi.co", "https://ipapi.co/json/",
                        "latitude", "longitude", "city", "country_name", "timezone"),
                new Endpoint("ip-api.com", "http://ip-api.com/json/",
                        "lat", "lon", "city", "country", "timezone"));
    }

    /**
     * Describes one IP geolocation service and the JSON field names it uses.
     *
     * @param name           label used in log output
     * @param url            fully qualified request URL
     * @param latitudeField  JSON field holding the latitude
     * @param longitudeField JSON field holding the longitude
     * @param cityField      JSON field holding the city name
     * @param countryField   JSON field holding the country name
     * @param timeZoneField  JSON field holding the IANA time zone id
     */
    public record Endpoint(String name, String url, String latitudeField, String longitudeField,
                           String cityField, String countryField, String timeZoneField) {
    }
}
