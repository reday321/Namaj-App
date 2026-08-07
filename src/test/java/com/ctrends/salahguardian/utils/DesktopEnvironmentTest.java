package com.ctrends.salahguardian.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DesktopEnvironment}.
 */
class DesktopEnvironmentTest {

    @Test
    @DisplayName("another application's snap environment is not mistaken for ours")
    void ignoresForeignSnapEnvironment() {
        // Snap variables are inherited by child processes. Launching a .deb
        // installation from a terminal that is itself a snap (VS Code, for
        // example) sets SNAP and SNAP_NAME to that snap's values. Treating that
        // as confinement would send binary lookups into the wrong tree.
        String snapName = System.getenv("SNAP_NAME");
        if (snapName != null && !snapName.isBlank() && !"salah-guardian".equals(snapName)) {
            assertFalse(DesktopEnvironment.isSnap(),
                    "SNAP_NAME=" + snapName + " belongs to another snap, not to us");
            assertTrue(DesktopEnvironment.snapRoot().isEmpty());
        }
    }

    @Test
    @DisplayName("describe() never throws and always names a session type")
    void describeIsSafe() {
        String description = DesktopEnvironment.describe();
        assertTrue(description.contains("wayland") || description.contains("x11"),
                "expected a session type in: " + description);
    }
}
