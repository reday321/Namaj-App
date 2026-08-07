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
     * The root of a snap package, when running inside one.
     *
     * <p>A confined snap sees its own filesystem: the binaries it ships live
     * under {@code $SNAP/usr/bin} rather than {@code /usr/bin}. Anything that
     * resolves executables from a fixed list of system directories has to know
     * about this prefix or it will find nothing.</p>
     *
     * @return the snap root, or empty when not running as a snap
     */
    public static java.util.Optional<String> snapRoot() {
        // SNAP alone is not enough. Snap environment variables are inherited by
        // child processes, so a .deb installation launched from a terminal that
        // is itself a snap - VS Code, for instance - would see SNAP set and
        // wrongly conclude it was confined. SNAP_NAME identifies whose snap it
        // is, so require it to be ours.
        String snap = System.getenv("SNAP");
        String snapName = System.getenv("SNAP_NAME");
        if (snap == null || snap.isBlank() || !SNAP_NAME.equals(snapName)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(snap);
    }

    /** The snap this application is published under. */
    private static final String SNAP_NAME = "salah-guardian";

    /**
     * @return {@code true} when running inside a confined snap package
     */
    public static boolean isSnap() {
        return snapRoot().isPresent();
    }

    /**
     * @return a short description used in the log banner and the about dialog
     */
    public static String describe() {
        String desktop = currentDesktop().isEmpty() ? "unknown" : currentDesktop();
        return desktop + " / " + (isWayland() ? "wayland" : "x11") + (isSnap() ? " / snap" : "");
    }
}
