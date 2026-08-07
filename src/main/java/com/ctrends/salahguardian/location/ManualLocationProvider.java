package com.ctrends.salahguardian.location;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.LocationSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Last link in the resolution chain: replays whatever the configuration file
 * already holds.
 *
 * <p>This is what makes the application work offline. Once any provider has
 * succeeded even once, its result is written to {@code config.json}; from then
 * on this provider alone can satisfy every start-up, with no network and no
 * D-Bus. It also serves the user who typed coordinates by hand in Settings.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class ManualLocationProvider implements LocationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ManualLocationProvider.class);

    private final ConfigService configService;

    /**
     * @param configService source of the persisted coordinates
     */
    @Inject
    public ManualLocationProvider(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public LocationSource source() {
        return LocationSource.MANUAL;
    }

    @Override
    public boolean isAvailable() {
        return configService.get().hasStoredLocation();
    }

    @Override
    public Optional<GeoLocation> resolve() {
        AppConfig config = configService.get();
        if (!config.hasStoredLocation()) {
            LOG.debug("No location has ever been stored");
            return Optional.empty();
        }
        GeoLocation stored = config.toGeoLocation();
        LOG.info("Using the stored location {} ({})", stored.displayLabel(),
                stored.source().displayName());
        // Re-tag as CACHED unless the user typed it in themselves, so the
        // dashboard can be honest about where the coordinates came from.
        LocationSource tag = stored.source() == LocationSource.MANUAL
                ? LocationSource.MANUAL
                : LocationSource.CACHED;
        return Optional.of(new GeoLocation(stored.latitude(), stored.longitude(),
                stored.city(), stored.country(), stored.timeZoneId(), tag, stored.resolvedAt()));
    }
}
