package com.ctrends.salahguardian.view;

import com.ctrends.salahguardian.config.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for audit finding SG-H-05.
 *
 * <p>The fullscreen overlay consumed Escape unconditionally <em>and</em> pulled
 * focus back every time it lost it, for up to an hour. Together that left a
 * keyboard-only or screen-reader user with no way out at all, and made every
 * other window on the desktop unusable meanwhile.</p>
 *
 * <p>Driving the real window needs a JavaFX toolkit, which this headless suite
 * does not start, so the behavioural guarantees are asserted at source level
 * and the bounds through configuration. They exist to make a regression loud:
 * reintroducing the unbounded loop, or removing the keyboard exit, fails the
 * build.</p>
 */
class FocusOverlaySafetyTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/ctrends/salahguardian/view/FocusOverlayView.java");

    /** Skips the source-level checks when running from a packaged build. */
    static boolean sourceAvailable() {
        return Files.isRegularFile(SOURCE);
    }

    private static String source() throws Exception {
        return Files.readString(SOURCE);
    }

    @Test
    @EnabledIf("sourceAvailable")
    @DisplayName("focus re-assertion is bounded, not a loop")
    void focusReassertionIsBounded() throws Exception {
        String code = source();
        assertTrue(code.contains("MAX_FOCUS_REASSERTIONS"),
                "the overlay must give up pulling focus back after a few attempts");
        assertTrue(code.contains("focusReassertions++ < MAX_FOCUS_REASSERTIONS"),
                "the counter must gate the re-assertion");
        assertTrue(code.contains("setAlwaysOnTop(false)"),
                "once it gives up it must also stop forcing itself above other windows");
    }

    @Test
    @EnabledIf("sourceAvailable")
    @DisplayName("a keyboard exit exists and is discoverable")
    void keyboardExitExists() throws Exception {
        String code = source();
        assertTrue(code.contains("ESCAPE_HOLD_NANOS"),
                "holding Escape must dismiss the overlay - a mouse-only exit is a keyboard trap");
        assertTrue(code.contains("close(true)"),
                "the hold gesture must actually close the overlay");
        assertTrue(code.contains("focus.escapeHint"),
                "the gesture must be shown on screen; an undiscoverable exit is not an exit");
    }

    @Test
    @EnabledIf("sourceAvailable")
    @DisplayName("a single Escape tap still does not dismiss it")
    void singleTapStillIgnored() throws Exception {
        String code = source();
        assertTrue(code.contains("event.consume()"),
                "Escape must still be consumed so a reflexive tap does nothing");
        assertTrue(code.contains("System.nanoTime() - escapePressedAt > ESCAPE_HOLD_NANOS"),
                "dismissal must require the hold duration to elapse");
    }

    @Test
    @EnabledIf("sourceAvailable")
    @DisplayName("Alt+F4 is still ignored, and the overlay never exits the app")
    void closeRequestStillConsumed() throws Exception {
        String code = source();
        assertTrue(code.contains("setOnCloseRequest"),
                "a window manager close must still be intercepted");
        assertFalse(code.contains("Platform.exit()"),
                "the overlay must never terminate the application");
    }

    @Test
    @DisplayName("the overlay cannot be configured to last an hour")
    void overlayDurationIsBounded() {
        AppConfig config = new AppConfig();

        config.setFocusDurationSeconds(3600);
        config.normalise();
        assertEquals(600, config.getFocusDurationSeconds(),
                "ten minutes is the ceiling; an hour of always-on-top fullscreen is a hazard");

        config.setFocusDurationSeconds(5);
        config.normalise();
        assertEquals(30, config.getFocusDurationSeconds());

        config.setFocusDurationSeconds(300);
        config.normalise();
        assertEquals(300, config.getFocusDurationSeconds(), "the default must survive");
    }
}
