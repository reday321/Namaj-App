package com.ctrends.salahguardian.model;

import com.ctrends.salahguardian.i18n.Messages;

/**
 * Identifies which strategy produced a {@link GeoLocation}.
 *
 * <p>The ordinal order reflects the resolution priority used by
 * {@code LocationService}: earlier constants are attempted first.</p>
 *
 * @author CTrends Software
 */
public enum LocationSource {

    /** Resolved through the D-Bus GeoClue2 service (most accurate, offline capable). */
    GEOCLUE("GeoClue"),

    /** Resolved through a public IP geolocation endpoint (requires internet). */
    IP_GEOLOCATION("IP Geolocation"),

    /** Entered by the user in the settings screen. */
    MANUAL("Manual"),

    /** Restored from the on-disk configuration written by a previous run. */
    CACHED("Saved");

    private final String displayName;

    LocationSource(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return human readable label shown in the dashboard location card
     */
    public String displayName() {
        return Messages.get("source." + name());
    }
}
