package com.ctrends.salahguardian.location;

import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.LocationSource;

import java.util.Optional;

/**
 * One strategy for discovering where the user is.
 *
 * <p>Implementations are combined by {@link LocationService} into a
 * priority-ordered chain. Every method must be side effect free with respect to
 * application state and must never throw: an unusable provider simply reports
 * {@link Optional#empty()} so the chain can move on to the next candidate.</p>
 *
 * @author CTrends Software
 */
public interface LocationProvider {

    /**
     * @return the source tag stamped onto locations produced by this provider
     */
    LocationSource source();

    /**
     * Cheap pre-flight check, e.g. "is the {@code gdbus} binary installed?".
     * A provider that reports {@code false} is skipped without being invoked.
     *
     * @return {@code true} when it is worth calling {@link #resolve()}
     */
    boolean isAvailable();

    /**
     * Attempts to determine the current position.
     *
     * <p>Implementations must honour their own internal timeout so that the
     * chain cannot stall; this method is always called off the UI thread.</p>
     *
     * @return the detected position, or empty when this strategy could not
     *         produce one
     */
    Optional<GeoLocation> resolve();

    /**
     * @return a short label used in log messages
     */
    default String describe() {
        return source().displayName();
    }
}
