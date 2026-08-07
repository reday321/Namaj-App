package com.ctrends.salahguardian;

import com.ctrends.salahguardian.controller.ApplicationController;
import com.ctrends.salahguardian.di.AppModule;
import com.ctrends.salahguardian.utils.DesktopEnvironment;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JavaFX {@link Application} for Salah Guardian.
 *
 * <p>Kept deliberately thin: it builds the Guice injector, hands control to
 * {@link ApplicationController} and installs a last-resort exception handler.
 * All behaviour lives in the services and view models.</p>
 *
 * <p>Note that this class is <em>not</em> the {@code main} entry point -
 * {@link Launcher} is. Launching through a class that does not extend
 * {@code Application} is what allows JavaFX to sit on the plain classpath,
 * which in turn is what lets jpackage bundle the app without a module path.</p>
 *
 * @author CTrends Software
 */
public class SalahGuardianApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(SalahGuardianApp.class);

    /** Command line flag that starts the application straight into the tray. */
    public static final String FLAG_MINIMISED = "--minimised";

    /** Command line flag that opens the settings window on start-up. */
    public static final String FLAG_SETTINGS = "--settings";

    private Injector injector;
    private ApplicationController controller;

    @Override
    public void init() {
        LOG.info("Salah Guardian starting on {} (Java {})",
                DesktopEnvironment.describe(), System.getProperty("java.version"));
        injector = Guice.createInjector(new AppModule());
    }

    @Override
    public void start(Stage primaryStage) {
        Thread.setDefaultUncaughtExceptionHandler(this::handleUncaught);
        Thread.currentThread().setUncaughtExceptionHandler(this::handleUncaught);

        var arguments = getParameters().getRaw();
        boolean startHidden = arguments.contains(FLAG_MINIMISED);
        boolean openSettings = arguments.contains(FLAG_SETTINGS);
        try {
            controller = injector.getInstance(ApplicationController.class);
            controller.start(primaryStage, startHidden);
            if (openSettings) {
                // Honours the "Settings" action on the desktop entry.
                controller.showSettings();
            }
        } catch (RuntimeException e) {
            LOG.error("Salah Guardian could not start", e);
            showFatalError(e);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    /**
     * Logs anything that escaped a handler. The application keeps running: a
     * failed repaint or a misbehaving listener is not a reason to stop
     * reminding someone to pray.
     *
     * @param thread    the thread the exception escaped from
     * @param throwable what went wrong
     */
    private void handleUncaught(Thread thread, Throwable throwable) {
        LOG.error("Uncaught exception on thread '{}'", thread.getName(), throwable);
    }

    private void showFatalError(Throwable cause) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Salah Guardian");
            alert.setHeaderText("Salah Guardian could not start");
            alert.setContentText(String.valueOf(cause.getMessage())
                    + "\n\nSee ~/.local/share/salahguardian/logs/ for details.");
            alert.showAndWait();
        } catch (RuntimeException ignored) {
            // No display available; the log entry above is all we can offer.
        }
    }
}
