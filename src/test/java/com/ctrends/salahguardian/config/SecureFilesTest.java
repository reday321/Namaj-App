package com.ctrends.salahguardian.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SecureFiles} and the permissions of the files the
 * application actually writes.
 *
 * <p>These are the regression tests for audit finding SG-H-03: the config file
 * and the logs held precise coordinates at mode {@code 0664}. Without a test
 * pinning the mode, a future refactor would quietly reintroduce it.</p>
 */
@EnabledOnOs(OS.LINUX)
class SecureFilesTest {

    private static String modeOf(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    @Test
    @DisplayName("creates directories only the owner can enter")
    void createsPrivateDirectory(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("nested").resolve("salahguardian");
        SecureFiles.createPrivateDirectory(target);

        assertTrue(Files.isDirectory(target));
        assertEquals("rwx------", modeOf(target));
    }

    @Test
    @DisplayName("tightens a directory that already exists at a looser mode")
    void tightensExistingDirectory(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("legacy");
        Files.createDirectory(target);
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxrwxr-x"));

        SecureFiles.createPrivateDirectory(target);

        assertEquals("rwx------", modeOf(target),
                "an installation predating this change must be tightened, not left alone");
    }

    @Test
    @DisplayName("creates files at 0600 with no wider window")
    void createsPrivateFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("secret.json");
        try (Writer writer = SecureFiles.newPrivateWriter(file)) {
            writer.write("{}");
        }
        assertEquals("rw-------", modeOf(file));
    }

    @Test
    @DisplayName("replaces a pre-existing looser file rather than inheriting its mode")
    void replacesLooseFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("old.json");
        Files.writeString(file, "stale", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-r--"));

        try (Writer writer = SecureFiles.newPrivateWriter(file)) {
            writer.write("fresh");
        }
        assertEquals("rw-------", modeOf(file));
        assertEquals("fresh", Files.readString(file));
    }

    @Test
    @DisplayName("sweeps an existing log directory and its files")
    void sweepsDirectoryContents(@TempDir Path dir) throws IOException {
        Path logs = dir.resolve("logs");
        Files.createDirectory(logs);
        Files.setPosixFilePermissions(logs, PosixFilePermissions.fromString("rwxrwxr-x"));
        for (String name : new String[]{"a.log", "b.log.gz"}) {
            Path file = logs.resolve(name);
            Files.writeString(file, "x", StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-r--"));
        }

        int tightened = SecureFiles.hardenDirectoryContents(logs);

        assertEquals(2, tightened);
        assertEquals("rwx------", modeOf(logs));
        assertEquals("rw-------", modeOf(logs.resolve("a.log")));
        assertEquals("rw-------", modeOf(logs.resolve("b.log.gz")));
    }

    // ----- the real thing: what the application writes -----------------------

    @Test
    @DisplayName("the saved configuration is never readable by other users")
    void configFileIsPrivate(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("salahguardian").resolve("config.json");
        JsonConfigService service = new JsonConfigService(file);

        service.update(config -> {
            config.setLatitude(23.8103);      // a home address, to four decimals
            config.setLongitude(90.4125);
        });

        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
        assertFalse(perms.contains(PosixFilePermission.OTHERS_READ),
                "coordinates must not be world-readable");
        assertFalse(perms.contains(PosixFilePermission.GROUP_READ),
                "coordinates must not be group-readable - umask 002 is a common default");
        assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE),
                "another user must not be able to rewrite the configuration");
        assertEquals("rw-------", modeOf(file));
        assertEquals("rwx------", modeOf(file.getParent()));
    }

    @Test
    @DisplayName("repeated saves keep the file private")
    void staysPrivateAcrossSaves(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        JsonConfigService service = new JsonConfigService(file);

        service.update(config -> config.setCity("Dhaka"));
        service.update(config -> config.setCity("Chattogram"));
        service.save();

        assertEquals("rw-------", modeOf(file));
        assertEquals("Chattogram", new JsonConfigService(file).get().getCity());
    }

    @Test
    @DisplayName("merely reading an old config tightens it, without needing a save")
    void tightensLegacyConfigOnRead(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"city\":\"Dhaka\"}", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-r--"));

        // A user who never changes a setting never triggers a save, so reading
        // has to be enough or their coordinates stay exposed indefinitely.
        JsonConfigService service = new JsonConfigService(file);
        assertEquals("Dhaka", service.get().getCity());

        assertEquals("rw-------", modeOf(file));
    }

    @Test
    @DisplayName("an upgrade tightens a config file left at 0664 by an older version")
    void tightensLegacyConfig(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"city\":\"Dhaka\"}", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-r--"));

        JsonConfigService service = new JsonConfigService(file);
        service.update(config -> config.setCountry("Bangladesh"));

        assertEquals("rw-------", modeOf(file));
    }
}
