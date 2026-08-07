package com.ctrends.salahguardian.view.components;

import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Optional;

/**
 * Loads the bundled application icons from the classpath.
 *
 * <p>Icon loading is wrapped because a missing resource must never prevent the
 * window from opening: the caller simply gets an empty {@link Optional} and
 * falls back to text.</p>
 *
 * @author CTrends Software
 */
public final class IconFactory {

    private static final Logger LOG = LoggerFactory.getLogger(IconFactory.class);

    /** Classpath location of the main application icon. */
    public static final String APP_ICON = "/icons/salah-guardian.png";

    /** Classpath location of the monochrome tray icon. */
    public static final String TRAY_ICON = "/icons/salah-guardian-tray.png";

    private IconFactory() {
        // utility class
    }

    /**
     * Loads an image from the classpath.
     *
     * @param resource absolute classpath location
     * @return the image when it could be read
     */
    public static Optional<Image> load(String resource) {
        try (InputStream stream = IconFactory.class.getResourceAsStream(resource)) {
            if (stream == null) {
                LOG.debug("Icon resource {} is not on the classpath", resource);
                return Optional.empty();
            }
            return Optional.of(new Image(stream));
        } catch (Exception e) {
            LOG.debug("Icon resource {} could not be loaded", resource, e);
            return Optional.empty();
        }
    }
}
