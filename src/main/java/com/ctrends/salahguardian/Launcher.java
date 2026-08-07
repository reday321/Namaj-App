package com.ctrends.salahguardian;

import com.ctrends.salahguardian.utils.LoggingConfigurator;
import javafx.application.Application;

import java.nio.file.Path;

/**
 * Process entry point.
 *
 * <p>This class exists for two reasons:</p>
 * <ol>
 *   <li><b>Packaging.</b> A {@code main} class that does not extend
 *       {@code javafx.application.Application} lets the JVM start with JavaFX
 *       on the ordinary classpath. That is what makes the non-modular jpackage
 *       image work, and it avoids the "JavaFX runtime components are missing"
 *       error that a direct {@code Application} main produces.</li>
 *   <li><b>Logging.</b> {@link LoggingConfigurator#initialise()} has to run
 *       before any class touches SLF4J, because Logback resolves the log
 *       directory when it configures itself. Doing it here guarantees that
 *       ordering.</li>
 * </ol>
 *
 * @author CTrends Software
 */
public final class Launcher {

    private Launcher() {
        // not instantiable
    }

    /**
     * Starts the application.
     *
     * @param args command line arguments; {@code --minimised} starts into the
     *             system tray without showing the dashboard
     */
    public static void main(String[] args) {
        Path logDirectory = LoggingConfigurator.initialise();
        // Deliberately a plain println: the logging backend is only being
        // configured on the next line, when the first logger is created.
        System.out.println("[SalahGuardian] logging to " + logDirectory);

        // AWT must not be headless: the system tray depends on it.
        System.setProperty("java.awt.headless", "false");

        Application.launch(SalahGuardianApp.class, args);
    }
}
