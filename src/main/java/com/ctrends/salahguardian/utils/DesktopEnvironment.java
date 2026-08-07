package com.ctrends.salahguardian.utils;

import java.util.Locale;

/**
 * Detects properties of the running desktop session that change how the
 * application must behave.
 *
 * <p>The two decisions that matter:</p>
 * <ul>
 *   <li><b>Wayland vs X11</b> - a Wayland compositor refuses programmatic
 *       always-on-top and global window positioning, so the focus overlay falls
 *       back to a plain undecorated full screen window.</li>
 *   <li><b>Tray availability</b> - GNOME ships without a StatusNotifier host by
 *       default, so the AWT tray may be unsupported; the application must then
 *       keep the dashboard reachable instead of hiding into a tray that is not
 *       there.</li>
 * </ul>
 *
 * @author CTrends Software
 */
public final class DesktopEnvironment {

    private DesktopEnvironment() {
        // utility class
    }

    /**
     * @return the value of {@code XDG_CURRENT_DESKTOP}, lower cased, or an
     *         empty string when unset
     */
    public static String currentDesktop() {
        String desktop = System.getenv("XDG_CURRENT_DESKTOP");
        return desktop == null ? "" : desktop.toLowerCase(Locale.ROOT);
    }

    /**
     * @return {@code true} when the session runs on a Wayland compositor
     */
    public static boolean isWayland() {
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        if (sessionType != null && sessionType.toLowerCase(Locale.ROOT).contains("wayland")) {
            return true;
        }
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        return waylandDisplay != null && !waylandDisplay.isBlank();
    }

    /**
     * @return {@code true} when a GNOME shell based session is detected, where
     *         tray icons require the AppIndicator extension
     */
    public static boolean isGnome() {
        return currentDesktop().contains("gnome");
    }

    /**
     * @return {@code true} when any graphical session appears to be present
     */
    public static boolean hasDisplay() {
        String display = System.getenv("DISPLAY");
        return (display != null && !display.isBlank()) || isWayland();
    }

    /**
     * @return a short description used in the log banner and the about dialog
     */
    public static String describe() {
        String desktop = currentDesktop().isEmpty() ? "unknown" : currentDesktop();
        return desktop + " / " + (isWayland() ? "wayland" : "x11");
    }
}
