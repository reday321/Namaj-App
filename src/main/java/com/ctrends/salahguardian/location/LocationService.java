package com.ctrends.salahguardian.location;

import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.LocationSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the user's position by walking a priority-ordered chain of
 * {@link LocationProvider}s and persisting whatever it finds.
 *
 * <h2>Priority</h2>
 * <ol>
 *   <li>{@link GeoClueLocationProvider} - accurate and works without internet
 *       once the daemon has a fix;</li>
 *   <li>{@link IpGeolocationProvider} - city level, needs the internet;</li>
 *   <li>{@link ManualLocationProvider} - whatever is already on disk, either
 *       typed by the user or cached from an earlier successful detection.</li>
 * </ol>
 *
 * <p>If automatic detection is switched off in Settings, the chain is reduced to
 * its last link, so the user's manual coordinates are never overwritten.</p>
 *
 * <h2>Offline behaviour</h2>
 * A successful detection is written to {@code config.json} immediately. Every
 * subsequent launch therefore succeeds through the manual/cached provider even
 * with no network at all, which satisfies the "keeps working offline after the
 * first successful detection" requirement.
 *
 * @author CTrends Software
 */
@Singleton
public class LocationService {

    private static final Logger LOG = LoggerFactory.getLogger(LocationService.class);

    /**
     * Movement smaller than this is treated as noise and does not trigger a
     * rewrite of the configuration file.
     */
    private static final double SIGNIFICANT_MOVE_KM = 5.0;

    private final ConfigService configService;
    private final List<LocationProvider> providers;
    private final AtomicReference<GeoLocation> current = new AtomicReference<>();

    /**
     * Production constructor wiring the standard chain.
     *
     * @param configService  persistence for the detected coordinates
     * @param geoClue        the D-Bus based provider
     * @param ipGeolocation  the network based provider
     * @param manual         the configuration backed provider
     */
    @Inject
    public LocationService(ConfigService configService,
                           GeoClueLocationProvider geoClue,
                           IpGeolocationProvider ipGeolocation,
                           ManualLocationProvider manual) {
        this(configService, List.of(geoClue, ipGeolocation, manual));
    }

    /**
     * Test friendly constructor accepting an arbitrary chain.
     *
     * @param configService persistence for the detected coordinates
     * @param providers     the chain, highest priority first
     */
    public LocationService(ConfigService configService, List<LocationProvider> providers) {
        this.configService = configService;
        this.providers = List.copyOf(providers);
    }

    /**
     * Returns the position, resolving it on first call.
     *
     * <p>Blocking: providers perform I/O. Call from a background thread.</p>
     *
     * @return the best available position; falls back to
     *         {@link GeoLocation#MAKKAH} if literally everything fails, so the
     *         application always has something to calculate with
     */
    public GeoLocation currentLocation() {
        GeoLocation cached = current.get();
        if (cached != null) {
            return cached;
        }
        return refresh();
    }

    /**
     * @return the last resolved position without triggering any I/O
     */
    public Optional<GeoLocation> peek() {
        return Optional.ofNullable(current.get());
    }

    /**
     * Re-runs the provider chain and persists any new position.
     *
     * <p>Blocking: call from a background thread.</p>
     *
     * @return the freshly resolved position, or the previous one when every
     *         provider fails
     */
    public GeoLocation refresh() {
        for (LocationProvider provider : activeChain()) {
            try {
                if (!provider.isAvailable()) {
                    LOG.debug("Skipping {} - reported unavailable", provider.describe());
                    continue;
                }
                Optional<GeoLocation> resolved = provider.resolve();
                if (resolved.isPresent()) {
                    GeoLocation location = resolved.get();
                    persistIfChanged(location);
                    current.set(location);
                    return location;
                }
                LOG.debug("{} produced no location", provider.describe());
            } catch (RuntimeException e) {
                // A misbehaving provider must never break the chain.
                LOG.warn("{} failed unexpectedly", provider.describe(), e);
            }
        }
        return fallback();
    }

    /**
     * Stores coordinates entered by the user and makes them current at once.
     *
     * @param latitude  degrees north
     * @param longitude degrees east
     * @param city      optional label
     * @param country   optional label
     * @return the stored location
     * @throws IllegalArgumentException when the coordinates are out of range
     */
    public GeoLocation setManualLocation(double latitude, double longitude,
                                         String city, String country) {
        GeoLocation manual = GeoLocation.of(latitude, longitude, city, country,
                LocationSource.MANUAL);
        configService.update(config -> {
            config.applyLocation(manual);
            config.setAutoDetectLocation(false);
        });
        current.set(manual);
        LOG.info("Manual location set to {}", manual.coordinateLabel());
        return manual;
    }

    private List<LocationProvider> activeChain() {
        if (configService.get().isAutoDetectLocation()) {
            return providers;
        }
        // Manual mode: only consult providers that read from configuration.
        List<LocationProvider> manualOnly = new ArrayList<>();
        for (LocationProvider provider : providers) {
            if (provider.source() == LocationSource.MANUAL) {
                manualOnly.add(provider);
            }
        }
        return manualOnly;
    }

    private void persistIfChanged(GeoLocation location) {
        GeoLocation stored = configService.get().toGeoLocation();
        boolean firstEver = !configService.get().hasStoredLocation();
        if (firstEver || stored.distanceKmTo(location) >= SIGNIFICANT_MOVE_KM) {
            configService.update(config -> config.applyLocation(location));
            LOG.info("Persisted location {} from {}", location.coordinateLabel(),
                    location.source().displayName());
        }
    }

    private GeoLocation fallback() {
        GeoLocation previous = current.get();
        if (previous != null) {
            LOG.warn("Location refresh failed - keeping the previous position");
            return previous;
        }
        if (configService.get().hasStoredLocation()) {
            GeoLocation stored = configService.get().toGeoLocation();
            current.set(stored);
            LOG.warn("Location detection failed - falling back to the stored position");
            return stored;
        }
        LOG.error("No location could be determined and none was stored - "
                + "defaulting to Makkah until the user sets one in Settings");
        current.set(GeoLocation.MAKKAH);
        return GeoLocation.MAKKAH;
    }
}
