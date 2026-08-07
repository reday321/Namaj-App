package com.ctrends.salahguardian.utils;

import com.ctrends.salahguardian.config.ConfigPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prepares logging before any logger is created.
 *
 * <p>Logback resolves {@code ${salahguardian.logDir}} inside {@code logback.xml}
 * at configuration time, which happens the first time any class calls
 * {@code LoggerFactory.getLogger}. This class must therefore run before that -
 * {@code Launcher} calls {@link #initialise()} as its very first statement,
 * before it touches a single logging aware class.</p>
 *
 * <p>If the directory cannot be created - a read-only or full home directory -
 * the failure is reported on standard error and the application continues with
 * console logging only, because losing the log file is not a reason to refuse
 * to remind someone to pray.</p>
 *
 * @author CTrends Software
 */
public final class LoggingConfigurator {

    /** Property name referenced by {@code logback.xml}. */
    public static final String LOG_DIR_PROPERTY = "salahguardian.logDir";

    /** Property that switches the root logger to DEBUG. */
    public static final String DEBUG_PROPERTY = "salahguardian.debug";

    private LoggingConfigurator() {
        // utility class
    }

    /**
     * Creates the log directory and publishes its path to Logback.
     *
     * @return the directory logs will be written to
     */
    public static Path initialise() {
        Path logDirectory = ConfigPaths.logDirectory();
        try {
            Files.createDirectories(logDirectory);
        } catch (IOException e) {
            System.err.println("[SalahGuardian] Could not create the log directory "
                    + logDirectory + " (" + e.getMessage() + "). "
                    + "Continuing with console logging only.");
        }
        System.setProperty(LOG_DIR_PROPERTY, logDirectory.toString());
        if (Boolean.getBoolean(DEBUG_PROPERTY)) {
            System.setProperty("logback.configurationFile", "logback.xml");
        }
        return logDirectory;
    }
}
