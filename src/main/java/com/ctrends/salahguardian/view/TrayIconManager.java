package com.ctrends.salahguardian.view;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.i18n.Messages;
import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.model.UpcomingPrayer;
import com.ctrends.salahguardian.prayer.PrayerScheduleService;
import com.ctrends.salahguardian.utils.DesktopEnvironment;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.CheckboxMenuItem;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Owns the system tray icon and its menu.
 *
 * <p>JavaFX has no tray API, so this uses AWT's {@link SystemTray}. The two
 * toolkits coexist as long as the JVM is not headless and every callback that
 * touches the UI hops onto the JavaFX thread - which is exactly what the menu
 * actions here do.</p>
 *
 * <h2>When there is no tray</h2>
 * GNOME ships without a StatusNotifier host unless the AppIndicator extension
 * is installed, and a bare X session may have none at all. {@link #install()}
 * therefore reports whether it succeeded, and the caller keeps the dashboard
 * visible rather than hiding the application into a tray that does not exist.
 *
 * @author CTrends Software
 */
@Singleton
public class TrayIconManager {

    private static final Logger LOG = LoggerFactory.getLogger(TrayIconManager.class);

    private static final String ICON_RESOURCE = "/icons/salah-guardian-tray.png";
    private static final String FALLBACK_ICON_RESOURCE = "/icons/salah-guardian.png";

    private final ConfigService configService;
    private final PrayerScheduleService scheduleService;

    private TrayIcon trayIcon;
    private Menu timesMenu;
    private CheckboxMenuItem remindersItem;
    private CheckboxMenuItem focusModeItem;
    private CheckboxMenuItem silentItem;

    private Runnable onOpenDashboard = () -> { };
    private Runnable onOpenSettings = () -> { };
    private Runnable onExit = () -> { };

    /**
     * @param configService   preference reads and writes for the toggles
     * @param scheduleService supplies the times shown in the submenu
     */
    @Inject
    public TrayIconManager(ConfigService configService, PrayerScheduleService scheduleService) {
        this.configService = configService;
        this.scheduleService = scheduleService;
    }

    /**
     * @param handler invoked when "Open Dashboard" is chosen
     */
    public void setOnOpenDashboard(Runnable handler) {
        this.onOpenDashboard = orNoop(handler);
    }

    /**
     * @param handler invoked when "Settings" is chosen
     */
    public void setOnOpenSettings(Runnable handler) {
        this.onOpenSettings = orNoop(handler);
    }

    /**
     * @param handler invoked when "Exit" is chosen
     */
    public void setOnExit(Runnable handler) {
        this.onExit = orNoop(handler);
    }

    /**
     * @return {@code true} when a tray is available on this desktop
     */
    public boolean isSupported() {
        try {
            return !java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
        } catch (Throwable t) {
            // A broken or absent AWT toolkit must not take the application down.
            LOG.debug("System tray support could not be determined", t);
            return false;
        }
    }

    /**
     * Creates the icon and adds it to the tray.
     *
     * @return {@code true} when the icon is now visible
     */
    public boolean install() {
        if (trayIcon != null) {
            return true;
        }
        if (!isSupported()) {
            LOG.warn("This desktop ({}) provides no system tray. Salah Guardian will keep the "
                            + "dashboard window open instead. On GNOME, install the "
                            + "'AppIndicator and KStatusNotifierItem Support' extension to get a tray icon.",
                    DesktopEnvironment.describe());
            return false;
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image image = loadIcon(tray.getTrayIconSize().width);

            trayIcon = new TrayIcon(image, "Salah Guardian");
            trayIcon.setImageAutoSize(true);
            trayIcon.setPopupMenu(buildMenu());
            // Left click / double click opens the dashboard on desktops that
            // deliver an action event for the icon.
            trayIcon.addActionListener(event -> runOnFx(onOpenDashboard));

            tray.add(trayIcon);
            refresh();
            LOG.info("System tray icon installed ({})", DesktopEnvironment.describe());
            return true;
        } catch (Exception e) {
            LOG.warn("The system tray icon could not be installed - continuing without it", e);
            trayIcon = null;
            return false;
        }
    }

    /**
     * Removes the icon from the tray.
     */
    public void uninstall() {
        if (trayIcon == null) {
            return;
        }
        try {
            SystemTray.getSystemTray().remove(trayIcon);
            LOG.info("System tray icon removed");
        } catch (Exception e) {
            LOG.debug("Removing the tray icon failed", e);
        } finally {
            trayIcon = null;
        }
    }

    /**
     * @return the live tray icon, or {@code null} when none is installed; used
     *         by the balloon notification fallback
     */
    public TrayIcon trayIcon() {
        return trayIcon;
    }

    /**
     * Rebuilds the tooltip and the times submenu.
     *
     * <p>Safe to call from any thread; AWT menu mutation is thread safe enough
     * for the simple structures used here, and it is always driven by the
     * scheduler or by an explicit user action.</p>
     */
    public void refresh() {
        if (trayIcon == null) {
            return;
        }
        try {
            AppConfig config = configService.get();
            updateTooltip(config);
            updateTimesMenu(config);
            if (remindersItem != null) {
                remindersItem.setState(config.isNotificationsEnabled());
            }
            if (focusModeItem != null) {
                focusModeItem.setState(config.isFocusModeEnabled());
            }
            if (silentItem != null) {
                silentItem.setState(config.isSilentMode());
            }
        } catch (RuntimeException e) {
            LOG.debug("Tray refresh failed", e);
        }
    }

    // ----- menu -------------------------------------------------------------

    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();

        MenuItem open = new MenuItem(Messages.get("tray.openDashboard"));
        open.addActionListener(event -> runOnFx(onOpenDashboard));

        timesMenu = new Menu(Messages.get("tray.todaysTimes"));

        remindersItem = new CheckboxMenuItem(Messages.get("tray.enableReminders"));
        remindersItem.addItemListener(event -> {
            boolean enabled = remindersItem.getState();
            configService.update(config -> config.setNotificationsEnabled(enabled));
            LOG.info("Reminders {} from the tray menu", enabled ? "enabled" : "disabled");
        });

        focusModeItem = new CheckboxMenuItem(Messages.get("tray.enableFocus"));
        focusModeItem.addItemListener(event -> {
            boolean enabled = focusModeItem.getState();
            configService.update(config -> config.setFocusModeEnabled(enabled));
            LOG.info("Prayer focus mode {} from the tray menu", enabled ? "enabled" : "disabled");
        });

        silentItem = new CheckboxMenuItem(Messages.get("tray.silentMode"));
        silentItem.addItemListener(event -> {
            boolean silent = silentItem.getState();
            configService.update(config -> config.setSilentMode(silent));
            LOG.info("Silent mode {} from the tray menu", silent ? "on" : "off");
        });

        MenuItem settings = new MenuItem(Messages.get("tray.settings"));
        settings.addActionListener(event -> runOnFx(onOpenSettings));

        MenuItem exit = new MenuItem(Messages.get("tray.exit"));
        exit.addActionListener(event -> runOnFx(onExit));

        menu.add(open);
        menu.add(timesMenu);
        menu.addSeparator();
        menu.add(remindersItem);
        menu.add(focusModeItem);
        menu.add(silentItem);
        menu.addSeparator();
        menu.add(settings);
        menu.addSeparator();
        menu.add(exit);
        return menu;
    }

    private void updateTooltip(AppConfig config) {
        Optional<UpcomingPrayer> upcoming = scheduleService.nextPrayer();
        String tooltip = upcoming
                .map(next -> Messages.get("app.name") + "\n" + Messages.format("tray.next",
                        next.prayer().name().displayName(),
                        next.prayer().formatted(config.isUse24HourClock()),
                        next.formattedRemaining()))
                .orElse(Messages.get("app.name"));
        trayIcon.setToolTip(tooltip);
    }

    private void updateTimesMenu(AppConfig config) {
        if (timesMenu == null) {
            return;
        }
        timesMenu.removeAll();
        DailyPrayerSchedule today = scheduleService.today();
        for (PrayerTime entry : today.allTimes()) {
            String label = String.format("%-9s %s",
                    entry.name().displayName(today.isFriday()),
                    entry.formatted(config.isUse24HourClock()));
            MenuItem item = new MenuItem(label);
            item.setEnabled(false);
            timesMenu.add(item);
        }
        timesMenu.addSeparator();
        MenuItem openDashboard = new MenuItem(Messages.get("tray.showFullTimetable"));
        openDashboard.addActionListener(event -> runOnFx(onOpenDashboard));
        timesMenu.add(openDashboard);
    }

    // ----- icon -------------------------------------------------------------

    private Image loadIcon(int preferredSize) {
        Optional<Image> icon = readImage(ICON_RESOURCE).or(() -> readImage(FALLBACK_ICON_RESOURCE));
        return icon.orElseGet(() -> generatePlaceholder(Math.max(16, preferredSize)));
    }

    private Optional<Image> readImage(String resource) {
        try (InputStream stream = TrayIconManager.class.getResourceAsStream(resource)) {
            if (stream == null) {
                URL url = TrayIconManager.class.getResource(resource);
                return url == null ? Optional.empty()
                        : Optional.ofNullable(Toolkit.getDefaultToolkit().getImage(url));
            }
            return Optional.ofNullable(ImageIO.read(stream));
        } catch (IOException | RuntimeException e) {
            LOG.debug("Tray icon resource {} could not be read", resource, e);
            return Optional.empty();
        }
    }

    /**
     * Draws a minimal green crescent as a last resort, so the tray entry is
     * never an empty rectangle.
     *
     * @param size edge length in pixels
     * @return the generated image
     */
    private Image generatePlaceholder(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(0x22C55E));
            g.fillOval(1, 1, size - 2, size - 2);
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillOval(size / 4, 1, size - 2, size - 2);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private static Runnable orNoop(Runnable handler) {
        return handler == null ? () -> { } : handler;
    }
}
