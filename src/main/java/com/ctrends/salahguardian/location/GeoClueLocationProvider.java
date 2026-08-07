package com.ctrends.salahguardian.location;

import com.ctrends.salahguardian.model.GeoLocation;
import com.ctrends.salahguardian.model.LocationSource;
import com.ctrends.salahguardian.utils.ProcessResult;
import com.ctrends.salahguardian.utils.ProcessRunner;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the position through the system's GeoClue2 service.
 *
 * <h2>Why {@code gdbus} rather than a D-Bus binding</h2>
 * A native Java D-Bus binding would drag in JNI or a large pure-Java stack, and
 * would still fail on machines without a session bus. The {@code gdbus} CLI is
 * part of glib2, which every distribution that ships GeoClue also ships, so
 * driving it as a subprocess keeps the dependency footprint at zero while
 * behaving identically on Ubuntu, Mint, Debian and Fedora.
 *
 * <h2>Protocol</h2>
 * <ol>
 *   <li>{@code Manager.GetClient} returns a private client object path.</li>
 *   <li>{@code DesktopId} is set - GeoClue rejects clients without one.</li>
 *   <li>{@code Client.Start} begins resolution.</li>
 *   <li>The {@code Location} property is polled until it stops pointing at
 *       {@code /}, which is GeoClue's "no fix yet" sentinel.</li>
 *   <li>Latitude and Longitude are read from the location object.</li>
 *   <li>{@code Client.Stop} always runs, even on failure.</li>
 * </ol>
 *
 * <p>Any deviation - missing daemon, denied authorisation, no agent running -
 * results in {@link Optional#empty()} and a debug level log entry, letting the
 * IP based provider take over.</p>
 *
 * @author CTrends Software
 */
@Singleton
public class GeoClueLocationProvider implements LocationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(GeoClueLocationProvider.class);

    private static final String BUS_NAME = "org.freedesktop.GeoClue2";
    private static final String MANAGER_PATH = "/org/freedesktop/GeoClue2/Manager";
    private static final String MANAGER_IFACE = "org.freedesktop.GeoClue2.Manager";
    private static final String CLIENT_IFACE = "org.freedesktop.GeoClue2.Client";
    private static final String LOCATION_IFACE = "org.freedesktop.GeoClue2.Location";
    private static final String PROPERTIES_IFACE = "org.freedesktop.DBus.Properties";

    /** Desktop id announced to GeoClue; must match the installed .desktop file. */
    private static final String DESKTOP_ID = "salah-guardian";

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(6);
    private static final int LOCATION_POLL_ATTEMPTS = 12;
    private static final long LOCATION_POLL_INTERVAL_MS = 500L;

    /** Matches the object path inside a gdbus reply such as {@code (objectpath '/org/...',)}. */
    private static final Pattern OBJECT_PATH = Pattern.compile("objectpath\\s+'([^']+)'");

    /** Matches a double inside a gdbus reply such as {@code (<41.0082>,)}. */
    private static final Pattern DOUBLE_VALUE = Pattern.compile("<\\s*(-?\\d+(?:\\.\\d+)?(?:e-?\\d+)?)\\s*>");

    @Override
    public LocationSource source() {
        return LocationSource.GEOCLUE;
    }

    @Override
    public boolean isAvailable() {
        if (!ProcessRunner.isCommandAvailable("gdbus")) {
            LOG.debug("GeoClue unavailable: gdbus is not installed");
            return false;
        }
        // Ping the well known name; a NameHasNoOwner error means the daemon is
        // not running or not installed.
        ProcessResult probe = ProcessRunner.run(Duration.ofSeconds(4), List.of(
                "gdbus", "call", "--system",
                "--dest", "org.freedesktop.DBus",
                "--object-path", "/org/freedesktop/DBus",
                "--method", "org.freedesktop.DBus.NameHasOwner", BUS_NAME));
        boolean present = probe.isSuccess() && probe.trimmedOutput().contains("true");
        if (!present) {
            LOG.debug("GeoClue unavailable: {} has no owner on the system bus", BUS_NAME);
        }
        return present;
    }

    @Override
    public Optional<GeoLocation> resolve() {
        Optional<String> clientPath = createClient();
        if (clientPath.isEmpty()) {
            LOG.debug("GeoClue did not hand out a client object");
            return Optional.empty();
        }
        String client = clientPath.get();
        try {
            if (!configureClient(client)) {
                return Optional.empty();
            }
            if (!startClient(client)) {
                return Optional.empty();
            }
            return pollForLocation(client);
        } finally {
            stopClient(client);
        }
    }

    private Optional<String> createClient() {
        ProcessResult result = ProcessRunner.run(CALL_TIMEOUT, List.of(
                "gdbus", "call", "--system",
                "--dest", BUS_NAME,
                "--object-path", MANAGER_PATH,
                "--method", MANAGER_IFACE + ".GetClient"));
        if (!result.isSuccess()) {
            LOG.debug("GeoClue GetClient failed: {}", result.stderr().trim());
            return Optional.empty();
        }
        Matcher matcher = OBJECT_PATH.matcher(result.stdout());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private boolean configureClient(String clientPath) {
        // GeoClue refuses to start a client that has not identified itself.
        boolean idSet = setProperty(clientPath, "DesktopId", "<'" + DESKTOP_ID + "'>");
        // A 100 m threshold keeps the daemon from waking us for tiny movements.
        setProperty(clientPath, "DistanceThreshold", "<uint32 100>");
        if (!idSet) {
            LOG.debug("GeoClue rejected the DesktopId property - is {}.desktop installed?", DESKTOP_ID);
        }
        return idSet;
    }

    private boolean setProperty(String clientPath, String property, String variantLiteral) {
        ProcessResult result = ProcessRunner.run(CALL_TIMEOUT, List.of(
                "gdbus", "call", "--system",
                "--dest", BUS_NAME,
                "--object-path", clientPath,
                "--method", PROPERTIES_IFACE + ".Set",
                CLIENT_IFACE, property, variantLiteral));
        return result.isSuccess();
    }

    private boolean startClient(String clientPath) {
        ProcessResult result = ProcessRunner.run(CALL_TIMEOUT, List.of(
                "gdbus", "call", "--system",
                "--dest", BUS_NAME,
                "--object-path", clientPath,
                "--method", CLIENT_IFACE + ".Start"));
        if (!result.isSuccess()) {
            LOG.debug("GeoClue Start refused: {}", result.stderr().trim());
        }
        return result.isSuccess();
    }

    private void stopClient(String clientPath) {
        ProcessRunner.run(Duration.ofSeconds(3), List.of(
                "gdbus", "call", "--system",
                "--dest", BUS_NAME,
                "--object-path", clientPath,
                "--method", CLIENT_IFACE + ".Stop"));
    }

    private Optional<GeoLocation> pollForLocation(String clientPath) {
        for (int attempt = 0; attempt < LOCATION_POLL_ATTEMPTS; attempt++) {
            Optional<String> locationPath = readLocationPath(clientPath);
            if (locationPath.isPresent()) {
                return readCoordinates(locationPath.get());
            }
            try {
                Thread.sleep(LOCATION_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        LOG.debug("GeoClue produced no fix within {} ms",
                LOCATION_POLL_ATTEMPTS * LOCATION_POLL_INTERVAL_MS);
        return Optional.empty();
    }

    private Optional<String> readLocationPath(String clientPath) {
        ProcessResult result = ProcessRunner.run(CALL_TIMEOUT, List.of(
                "gdbus", "call", "--system",
                "--dest", BUS_NAME,
                "--object-path", clientPath,
                "--method", PROPERTIES_IFACE + ".Get",
                CLIENT_IFACE, "Location"));
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        Matcher matcher = OBJECT_PATH.matcher(result.stdout());
        if (!matcher.find()) {
            return Optional.empty();
        }
        String path = matcher.group(1);
        // "/" is GeoClue's placeholder while it is still acquiring a fix.
        return "/".equals(path) ? Optional.empty() : Optional.of(path);
    }

    private Optional<GeoLocation> readCoordinates(String locationPath) {
        Optional<Double> latitude = readDoubleProperty(locationPath, "Latitude");
        Optional<Double> longitude = readDoubleProperty(locationPath, "Longitude");
        if (latitude.isEmpty() || longitude.isEmpty()) {
            return Optional.empty();
        }
        double lat = latitude.get();
        double lon = longitude.get();
        if (!GeoLocation.isValidLatitude(lat) || !GeoLocation.isValidLongitude(lon)) {
            LOG.debug("GeoClue returned out-of-range coordinates: {}, {}", lat, lon);
            return Optional.empty();
        }
        LOG.info("GeoClue resolved the position to {}, {}", lat, lon);
        return Optional.of(GeoLocation.of(lat, lon, "", "", LocationSource.GEOCLUE));
    }

    private Optional<Double> readDoubleProperty(String objectPath, String property) {
        ProcessResult result = ProcessRunner.run(CALL_TIMEOUT, List.of(
                "gdbus", "call", "--system",
                "--dest", BUS_NAME,
                "--object-path", objectPath,
                "--method", PROPERTIES_IFACE + ".Get",
                LOCATION_IFACE, property));
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        Matcher matcher = DOUBLE_VALUE.matcher(result.stdout());
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(matcher.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
