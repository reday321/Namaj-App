package com.ctrends.salahguardian.location;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.LocationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LocationService} and {@link IpGeolocationProvider}.
 *
 * <p>No network and no D-Bus are touched: the provider chain is built from
 * stubs, and the IP provider is exercised through its package private payload
 * parser with recorded responses.</p>
 */
class LocationTest {

    /** In-memory {@link ConfigService} standing in for the JSON file. */
    private static final class InMemoryConfig implements ConfigService {
        private final AppConfig config = new AppConfig();
        private int saves;

        @Override
        public AppConfig get() {
            return config;
        }

        @Override
        public void update(Consumer<AppConfig> mutation) {
            mutation.accept(config);
            config.normalise();
            saves++;
        }

        @Override
        public void save() {
            saves++;
        }

        @Override
        public void reload() {
            // nothing to reload
        }

        @Override
        public void addChangeListener(Consumer<AppConfig> listener) {
            // not needed here
        }

        @Override
        public void removeChangeListener(Consumer<AppConfig> listener) {
            // not needed here
        }
    }

    /** Provider stub that records whether it was consulted. */
    private static final class StubProvider implements LocationProvider {
        private final LocationSource source;
        private final boolean available;
        private final GeoLocation result;
        private final List<String> log;
        private final RuntimeException failure;

        StubProvider(LocationSource source, boolean available, GeoLocation result,
                     List<String> log, RuntimeException failure) {
            this.source = source;
            this.available = available;
            this.result = result;
            this.log = log;
            this.failure = failure;
        }

        @Override
        public LocationSource source() {
            return source;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public Optional<GeoLocation> resolve() {
            log.add(source.name());
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(result);
        }
    }

    private InMemoryConfig config;
    private List<String> calls;

    @BeforeEach
    void setUp() {
        config = new InMemoryConfig();
        calls = new ArrayList<>();
    }

    private GeoLocation istanbul(LocationSource source) {
        return GeoLocation.of(41.0082, 28.9784, "Istanbul", "Turkey", source);
    }

    private GeoLocation cairo(LocationSource source) {
        return GeoLocation.of(30.0444, 31.2357, "Cairo", "Egypt", source);
    }

    @Test
    @DisplayName("prefers GeoClue over the later providers")
    void prefersGeoClue() {
        LocationService service = new LocationService(config, List.of(
                new StubProvider(LocationSource.GEOCLUE, true, istanbul(LocationSource.GEOCLUE), calls, null),
                new StubProvider(LocationSource.IP_GEOLOCATION, true, cairo(LocationSource.IP_GEOLOCATION), calls, null)));

        GeoLocation resolved = service.currentLocation();

        assertEquals("Istanbul", resolved.city());
        assertEquals(List.of("GEOCLUE"), calls, "later providers must not be consulted");
    }

    @Test
    @DisplayName("falls back to IP geolocation when GeoClue is unavailable")
    void fallsBackToIpGeolocation() {
        LocationService service = new LocationService(config, List.of(
                new StubProvider(LocationSource.GEOCLUE, false, istanbul(LocationSource.GEOCLUE), calls, null),
                new StubProvider(LocationSource.IP_GEOLOCATION, true, cairo(LocationSource.IP_GEOLOCATION), calls, null)));

        assertEquals("Cairo", service.currentLocation().city());
        assertEquals(List.of("IP_GEOLOCATION"), calls,
                "an unavailable provider must not be invoked at all");
    }

    @Test
    @DisplayName("continues past a provider that returns nothing")
    void continuesPastEmptyProvider() {
        LocationService service = new LocationService(config, List.of(
                new StubProvider(LocationSource.GEOCLUE, true, null, calls, null),
                new StubProvider(LocationSource.IP_GEOLOCATION, true, cairo(LocationSource.IP_GEOLOCATION), calls, null)));

        assertEquals("Cairo", service.currentLocation().city());
        assertEquals(List.of("GEOCLUE", "IP_GEOLOCATION"), calls);
    }

    @Test
    @DisplayName("continues past a provider that throws")
    void continuesPastThrowingProvider() {
        LocationService service = new LocationService(config, List.of(
                new StubProvider(LocationSource.GEOCLUE, true, null, calls,
                        new IllegalStateException("dbus exploded")),
                new StubProvider(LocationSource.IP_GEOLOCATION, true, cairo(LocationSource.IP_GEOLOCATION), calls, null)));

        assertEquals("Cairo", service.currentLocation().city());
    }

    @Test
    @DisplayName("persists the first detected location so later runs work offline")
    void persistsFirstDetection() {
        LocationService service = new LocationService(config, List.of(
                new StubProvider(LocationSource.IP_GEOLOCATION, true, cairo(LocationSource.IP_GEOLOCATION), calls, null)));

        assertFalse(config.get().hasStoredLocation());
        service.currentLocation();

        assertTrue(config.get().hasStoredLocation());
        assertEquals(30.0444, config.get().getLatitude(), 1e-6);
        assertEquals("Cairo", config.get().getCity());
    }

    @Test
    @DisplayName("does not rewrite the configuration for insignificant movement")
    void ignoresSmallMovement() {
        LocationService first = new LocationService(config, List.of(
                new StubProvider(LocationSource.IP_GEOLOCATION, true, cairo(LocationSource.IP_GEOLOCATION), calls, null)));
        first.currentLocation();
        int savesAfterFirst = config.saves;

        // About 1 km away - well inside the 5 km significance threshold.
        GeoLocation nudged = GeoLocation.of(30.0534, 31.2357, "Cairo", "Egypt",
                LocationSource.IP_GEOLOCATION);
        LocationService second = new LocationService(config, List.of(
                new StubProvider(LocationSource.IP_GEOLOCATION, true, nudged, calls, null)));
        second.refresh();

        assertEquals(savesAfterFirst, config.saves,
                "a small move should not trigger another write");
    }

    @Test
    @DisplayName("rewrites the configuration after a real relocation")
    void persistsSignificantMovement() {
        LocationService first = new LocationService(config, List.of(
                new StubProvider(LocationSource.IP_GEOLOCATION, true, cairo(LocationSource.IP_GEOLOCATION), calls, null)));
        first.currentLocation();
        int savesAfterFirst = config.saves;

        LocationService second = new LocationService(config, List.of(
                new StubProvider(LocationSource.IP_GEOLOCATION, true, istanbul(LocationSource.IP_GEOLOCATION), calls, null)));
        second.refresh();

        assertTrue(config.saves > savesAfterFirst);
        assertEquals("Istanbul", config.get().getCity());
    }

    @Test
    @DisplayName("caches the resolved location instead of re-running the chain")
    void cachesResolvedLocation() {
        LocationService service = new LocationService(config, List.of(
                new StubProvider(LocationSource.IP_GEOLOCATION, true, cairo(LocationSource.IP_GEOLOCATION), calls, null)));

        GeoLocation first = service.currentLocation();
        GeoLocation second = service.currentLocation();

        assertSame(first, second);
        assertEquals(1, calls.size(), "the chain should run only once");
    }

    @Test
    @DisplayName("skips the automatic providers when auto-detection is off")
    void honoursManualMode() {
        config.update(c -> c.setAutoDetectLocation(false));
        LocationService service = new LocationService(config, List.of(
                new StubProvider(LocationSource.GEOCLUE, true, istanbul(LocationSource.GEOCLUE), calls, null),
                new StubProvider(LocationSource.MANUAL, true, cairo(LocationSource.MANUAL), calls, null)));

        assertEquals("Cairo", service.currentLocation().city());
        assertEquals(List.of("MANUAL"), calls);
    }

    @Test
    @DisplayName("falls back to Makkah only when nothing at all is available")
    void fallsBackToMakkah() {
        LocationService service = new LocationService(config, List.of(
                new StubProvider(LocationSource.GEOCLUE, false, null, calls, null)));

        GeoLocation resolved = service.currentLocation();
        assertEquals(GeoLocation.MAKKAH.latitude(), resolved.latitude(), 1e-6);
    }

    @Test
    @DisplayName("falls back to the stored position rather than Makkah when one exists")
    void prefersStoredPositionOverMakkah() {
        config.update(c -> c.applyLocation(istanbul(LocationSource.IP_GEOLOCATION)));
        LocationService service = new LocationService(config, List.of(
                new StubProvider(LocationSource.GEOCLUE, false, null, calls, null)));

        assertEquals(41.0082, service.currentLocation().latitude(), 1e-6);
    }

    @Test
    @DisplayName("stores manual coordinates and switches auto-detection off")
    void storesManualCoordinates() {
        LocationService service = new LocationService(config, List.of());

        GeoLocation manual = service.setManualLocation(24.7136, 46.6753, "Riyadh", "Saudi Arabia");

        assertEquals(LocationSource.MANUAL, manual.source());
        assertFalse(config.get().isAutoDetectLocation());
        assertEquals("Riyadh", config.get().getCity());
        assertEquals(24.7136, service.currentLocation().latitude(), 1e-6);
    }

    // ----- IP payload parsing ----------------------------------------------

    @Test
    @DisplayName("parses an ipapi.co payload")
    void parsesIpapiPayload() {
        IpGeolocationProvider provider = new IpGeolocationProvider();
        IpGeolocationProvider.Endpoint endpoint = IpGeolocationProvider.defaultEndpoints().get(0);

        String body = """
                {"ip":"81.213.0.1","city":"Istanbul","region":"Istanbul",
                 "country_name":"Turkey","latitude":41.0138,"longitude":28.9497,
                 "timezone":"Europe/Istanbul"}
                """;

        GeoLocation parsed = provider.parse(body, endpoint).orElseThrow();
        assertEquals(41.0138, parsed.latitude(), 1e-6);
        assertEquals(28.9497, parsed.longitude(), 1e-6);
        assertEquals("Istanbul", parsed.city());
        assertEquals("Turkey", parsed.country());
        assertEquals("Europe/Istanbul", parsed.timeZoneId());
        assertEquals(LocationSource.IP_GEOLOCATION, parsed.source());
    }

    @Test
    @DisplayName("parses the secondary endpoint's payload")
    void parsesSecondaryPayload() {
        IpGeolocationProvider provider = new IpGeolocationProvider();
        IpGeolocationProvider.Endpoint endpoint = IpGeolocationProvider.defaultEndpoints().get(1);

        String body = """
                {"success":true,"country":"Egypt","city":"Cairo",
                 "latitude":30.0444,"longitude":31.2357,"timezone":"Africa/Cairo"}
                """;

        GeoLocation parsed = provider.parse(body, endpoint).orElseThrow();
        assertEquals(30.0444, parsed.latitude(), 1e-6);
        assertEquals("Cairo", parsed.city());
    }

    @Test
    @DisplayName("every shipped endpoint uses TLS, and a cleartext one is refused")
    void refusesCleartextEndpoints() {
        for (IpGeolocationProvider.Endpoint endpoint : IpGeolocationProvider.defaultEndpoints()) {
            assertTrue(endpoint.url().startsWith("https://"),
                    endpoint.name() + " must use TLS but was " + endpoint.url());
        }
        // Constructing with a plaintext endpoint must fail loudly, not silently
        // downgrade the user's location lookup.
        assertThrows(IllegalArgumentException.class, () -> new IpGeolocationProvider(
                java.net.http.HttpClient.newHttpClient(),
                List.of(new IpGeolocationProvider.Endpoint("evil", "http://evil.example/json",
                        "lat", "lon", "city", "country", "tz"))));
    }

    @Test
    @DisplayName("the user agent does not disclose what the application is")
    void userAgentIsAnonymous() throws Exception {
        java.lang.reflect.Field field =
                IpGeolocationProvider.class.getDeclaredField("USER_AGENT");
        field.setAccessible(true);
        String userAgent = (String) field.get(null);
        for (String leak : new String[]{"Salah", "Guardian", "prayer", "Prayer", "islam", "Islam"}) {
            assertFalse(userAgent.contains(leak),
                    "the user agent must not disclose religious use: " + userAgent);
        }
    }

    @Test
    @DisplayName("hostile place names are bounded and stripped of control characters")
    void sanitisesPlaceNames() {
        IpGeolocationProvider provider = new IpGeolocationProvider();
        var endpoint = IpGeolocationProvider.defaultEndpoints().get(0);

        // A newline here would forge log entries; the length would bloat config.
        String body = "{\"latitude\":23.8,\"longitude\":90.4,"
                + "\"city\":\"Dhaka\\nINFO Security - audit passed\","
                + "\"country_name\":\"" + "x".repeat(500) + "\"}";

        GeoLocation parsed = provider.parse(body, endpoint).orElseThrow();
        assertFalse(parsed.city().contains("\n"), "newlines must be stripped");
        assertTrue(parsed.country().length() <= 64, "place names must be bounded");
    }

    @Test
    @DisplayName("rejects failure payloads from either endpoint")
    void rejectsFailurePayloads() {
        IpGeolocationProvider provider = new IpGeolocationProvider();
        var ipapi = IpGeolocationProvider.defaultEndpoints().get(0);
        var ipApi = IpGeolocationProvider.defaultEndpoints().get(1);

        assertTrue(provider.parse("{\"status\":\"fail\",\"message\":\"private range\"}", ipApi).isEmpty());
        assertTrue(provider.parse("{\"error\":true,\"reason\":\"RateLimited\"}", ipapi).isEmpty());
    }

    @Test
    @DisplayName("rejects malformed, empty and out-of-range payloads")
    void rejectsUnusablePayloads() {
        IpGeolocationProvider provider = new IpGeolocationProvider();
        var endpoint = IpGeolocationProvider.defaultEndpoints().get(0);

        assertTrue(provider.parse(null, endpoint).isEmpty());
        assertTrue(provider.parse("", endpoint).isEmpty());
        assertTrue(provider.parse("not json", endpoint).isEmpty());
        assertTrue(provider.parse("[1,2,3]", endpoint).isEmpty());
        assertTrue(provider.parse("{\"latitude\":null,\"longitude\":null}", endpoint).isEmpty());
        assertTrue(provider.parse("{\"latitude\":999,\"longitude\":0}", endpoint).isEmpty());
    }

    @Test
    @DisplayName("tolerates a payload without city or country")
    void toleratesMissingPlaceNames() {
        IpGeolocationProvider provider = new IpGeolocationProvider();
        var endpoint = IpGeolocationProvider.defaultEndpoints().get(0);

        GeoLocation parsed = provider
                .parse("{\"latitude\":21.4225,\"longitude\":39.8262}", endpoint).orElseThrow();
        assertEquals("", parsed.city());
        assertEquals("21.4225°N, 39.8262°E", parsed.displayLabel());
    }
}
