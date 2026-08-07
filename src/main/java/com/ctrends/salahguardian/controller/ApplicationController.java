package com.ctrends.salahguardian.controller;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.location.LocationService;
import com.ctrends.salahguardian.prayer.PrayerScheduleService;
import com.ctrends.salahguardian.service.AutostartService;
import com.ctrends.salahguardian.service.PrayerEventListener;
import com.ctrends.salahguardian.service.PrayerSchedulerService;
import com.ctrends.salahguardian.service.ReminderEvent;
import com.ctrends.salahguardian.view.DashboardView;
import com.ctrends.salahguardian.view.SettingsView;
import com.ctrends.salahguardian.view.TrayIconManager;
import com.ctrends.salahguardian.view.components.IconFactory;
import com.ctrends.salahguardian.viewmodel.DashboardViewModel;
import com.ctrends.salahguardian.viewmodel.SettingsViewModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Application level orchestration: owns the windows, installs the tray, starts
 * the scheduler and coordinates a clean shutdown.
 *
 * <p>This is the only class that knows about all of the pieces at once, which
 * is what lets every other class stay narrow.</p>
 *
 * <h2>Start-up sequence</h2>
 * <ol>
 *   <li>Build the dashboard so the user has something on screen immediately.</li>
 *   <li>Install the tray icon; if there is none, force the window to be shown
 *       so the application cannot become invisible and unreachable.</li>
 *   <li>Resolve the location on a background thread - it may hit the network,
 *       and it must not delay the first paint.</li>
 *   <li>Start the scheduler once a location is known.</li>
 * </ol>
 *
 * @author CTrends Software
 */
@Singleton
public class ApplicationController implements PrayerEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationController.class);

    private final ConfigService configService;
    private final LocationService locationService;
    private final PrayerScheduleService scheduleService;
    private final PrayerSchedulerService schedulerService;
    private final DashboardViewModel dashboardViewModel;
    private final SettingsViewModel settingsViewModel;
    private final TrayIconManager trayIconManager;
    private final FocusModeController focusModeController;
    private final AutostartService autostartService;

    private final ExecutorService startupExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "salah-startup");
        thread.setDaemon(true);
        return thread;
    });

    private Stage dashboardStage;
    private Stage settingsStage;
    private DashboardView dashboardView;
    private boolean trayAvailable;

    /**
     * @param configService       user preferences
     * @param locationService     position resolution
     * @param scheduleService     timetable cache
     * @param schedulerService    background reminder engine
     * @param dashboardViewModel  dashboard state
     * @param settingsViewModel   settings state
     * @param trayIconManager     system tray integration
     * @param focusModeController fullscreen overlay policy
     * @param autostartService    desktop autostart entry
     */
    @Inject
    public ApplicationController(ConfigService configService,
                                 LocationService locationService,
                                 PrayerScheduleService scheduleService,
                                 PrayerSchedulerService schedulerService,
                                 DashboardViewModel dashboardViewModel,
                                 SettingsViewModel settingsViewModel,
                                 TrayIconManager trayIconManager,
                                 FocusModeController focusModeController,
                                 AutostartService autostartService) {
        this.configService = configService;
        this.locationService = locationService;
        this.scheduleService = scheduleService;
        this.schedulerService = schedulerService;
        this.dashboardViewModel = dashboardViewModel;
        this.settingsViewModel = settingsViewModel;
        this.trayIconManager = trayIconManager;
        this.focusModeController = focusModeController;
        this.autostartService = autostartService;
    }

    /**
     * Boots the user interface and the background services.
     *
     * @param primaryStage the stage handed over by the JavaFX toolkit
     * @param startHidden  {@code true} to start in the tray without showing the
     *                     dashboard, e.g. when launched by the autostart entry
     */
    public void start(Stage primaryStage, boolean startHidden) {
        // Closing the last window must not terminate a tray resident app.
        Platform.setImplicitExit(false);

        this.dashboardStage = primaryStage;
        this.dashboardView = new DashboardView(dashboardViewModel);
        dashboardView.setOnOpenSettings(this::showSettings);
        dashboardView.setOnRefreshLocation(dashboardViewModel::refreshLocation);

        primaryStage.setTitle("Salah Guardian");
        primaryStage.setScene(dashboardView.scene());
        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(560);
        IconFactory.load(IconFactory.APP_ICON).ifPresent(icon ->
                primaryStage.getIcons().add(icon));

        primaryStage.setOnCloseRequest(event -> {
            if (trayAvailable) {
                // Hide to tray rather than quit.
                event.consume();
                hideDashboard();
            } else {
                shutdown();
            }
        });

        focusModeController.setOwner(primaryStage);
        schedulerService.addListener(this);

        trayAvailable = installTray();
        dashboardViewModel.start();

        boolean hide = startHidden && trayAvailable
                && configService.get().isStartMinimisedToTray();
        if (hide) {
            LOG.info("Starting minimised to the system tray");
        } else {
            showDashboard();
        }

        syncAutostartState();
        resolveLocationInBackground();
    }

    /**
     * Brings the dashboard to the front, showing it if it was hidden.
     */
    public void showDashboard() {
        if (dashboardStage == null) {
            return;
        }
        if (!dashboardStage.isShowing()) {
            dashboardStage.show();
        }
        if (dashboardStage.isIconified()) {
            dashboardStage.setIconified(false);
        }
        dashboardStage.toFront();
        dashboardStage.requestFocus();
        dashboardViewModel.resume();
    }

    /**
     * Hides the dashboard, leaving the application running in the tray.
     */
    public void hideDashboard() {
        if (dashboardStage != null && dashboardStage.isShowing()) {
            dashboardStage.hide();
            dashboardViewModel.pause();
            LOG.debug("Dashboard hidden - Salah Guardian keeps running in the tray");
        }
    }

    /**
     * Opens the settings window, creating it on first use.
     */
    public void showSettings() {
        if (settingsStage == null) {
            SettingsView settingsView = new SettingsView(settingsViewModel);
            settingsStage = new Stage();
            settingsStage.setTitle("Salah Guardian - Settings");
            settingsStage.setScene(settingsView.scene());
            settingsStage.setMinWidth(560);
            settingsStage.setMinHeight(600);
            IconFactory.load(IconFactory.APP_ICON).ifPresent(icon ->
                    settingsStage.getIcons().add(icon));
            settingsStage.setOnHidden(event -> {
                // Pick up anything the settings screen changed.
                dashboardViewModel.refreshAll();
                trayIconManager.refresh();
            });
        }
        settingsViewModel.loadFromConfig();
        settingsStage.show();
        settingsStage.toFront();
        settingsStage.requestFocus();
    }

    /**
     * Stops every service and terminates the JVM.
     */
    public void shutdown() {
        LOG.info("Shutting down Salah Guardian");
        try {
            focusModeController.closeIfOpen();
            schedulerService.removeListener(this);
            schedulerService.stop();
            dashboardViewModel.close();
            trayIconManager.uninstall();
            configService.save();
            startupExecutor.shutdownNow();
            try {
                startupExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (RuntimeException e) {
            LOG.warn("Something failed during shutdown - exiting anyway", e);
        } finally {
            Platform.exit();
            // JavaFX leaves the AWT tray thread alive; make the exit definite.
            Runtime.getRuntime().halt(0);
        }
    }

    @Override
    public void onReminder(ReminderEvent event) {
        focusModeController.handleReminder(event);
        trayIconManager.refresh();
    }

    @Override
    public void onScheduleChanged() {
        trayIconManager.refresh();
    }

    // ----- internals --------------------------------------------------------

    private boolean installTray() {
        trayIconManager.setOnOpenDashboard(this::showDashboard);
        trayIconManager.setOnOpenSettings(this::showSettings);
        trayIconManager.setOnExit(this::shutdown);
        boolean installed = trayIconManager.install();
        if (!installed) {
            LOG.info("No system tray - the dashboard window will stay the way to reach the app");
        }
        return installed;
    }

    /**
     * Resolves the location off the UI thread, then starts the scheduler.
     */
    private void resolveLocationInBackground() {
        startupExecutor.submit(() -> {
            try {
                var location = locationService.currentLocation();
                LOG.info("Startup location: {} ({})", location.displayLabel(),
                        location.source().displayName());
                scheduleService.invalidate();
                schedulerService.start();
                Platform.runLater(() -> {
                    dashboardViewModel.refreshAll();
                    trayIconManager.refresh();
                });
            } catch (RuntimeException e) {
                LOG.error("Start-up location resolution failed - the app will keep running "
                        + "with whatever is stored in the configuration", e);
                Platform.runLater(() -> dashboardViewModel.statusMessageProperty().set(
                        "Location could not be detected. Set it manually in Settings."));
                schedulerService.start();
            }
        });
    }

    /**
     * Keeps the stored "start on login" flag honest: the user may have deleted
     * the autostart entry with their desktop's own tool.
     */
    private void syncAutostartState() {
        AppConfig config = configService.get();
        boolean present = autostartService.isEnabled();
        if (config.isStartOnLogin() != present) {
            LOG.info("Autostart entry {} on disk - updating the stored preference",
                    present ? "found" : "missing");
            configService.update(c -> c.setStartOnLogin(present));
        }
    }
}
