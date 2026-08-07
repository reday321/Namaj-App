package com.ctrends.salahguardian.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JsonConfigService}, covering persistence, listener
 * notification and the recovery paths for a missing or corrupt file.
 */
class JsonConfigServiceTest {

    @Test
    @DisplayName("starts from defaults when no file exists")
    void startsFromDefaults(@TempDir Path dir) {
        JsonConfigService service = new JsonConfigService(dir.resolve("config.json"));
        assertEquals(5, service.get().getReminderMinutes());
        assertFalse(Files.exists(dir.resolve("config.json")),
                "reading alone must not create the file");
    }

    @Test
    @DisplayName("persists an update and reloads it identically")
    void persistsAndReloads(@TempDir Path dir) {
        Path file = dir.resolve("config.json");
        JsonConfigService writer = new JsonConfigService(file);
        writer.update(config -> {
            config.setReminderMinutes(12);
            config.setCity("Kuala Lumpur");
            config.setTheme(Theme.MIDNIGHT.name());
        });

        assertTrue(Files.isRegularFile(file));

        JsonConfigService reader = new JsonConfigService(file);
        assertEquals(12, reader.get().getReminderMinutes());
        assertEquals("Kuala Lumpur", reader.get().getCity());
        assertEquals(Theme.MIDNIGHT, reader.get().themeOption());
    }

    @Test
    @DisplayName("writes human readable JSON")
    void writesReadableJson(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        JsonConfigService service = new JsonConfigService(file);
        service.update(config -> config.setCity("Lahore"));

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"city\": \"Lahore\""), "expected pretty printed JSON");
        assertTrue(content.contains("\"latitude\""));
    }

    @Test
    @DisplayName("normalises values before writing them")
    void normalisesBeforeWriting(@TempDir Path dir) {
        Path file = dir.resolve("config.json");
        JsonConfigService service = new JsonConfigService(file);
        service.update(config -> config.setReminderMinutes(500));

        assertEquals(60, service.get().getReminderMinutes());
        assertEquals(60, new JsonConfigService(file).get().getReminderMinutes());
    }

    @Test
    @DisplayName("quarantines a corrupt file and continues with defaults")
    void quarantinesCorruptFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{ this is not json at all ", StandardCharsets.UTF_8);

        JsonConfigService service = new JsonConfigService(file);
        assertEquals(5, service.get().getReminderMinutes(), "must fall back to defaults");
        assertTrue(Files.exists(dir.resolve("config.json.corrupt")),
                "the unreadable file should be preserved for inspection");
    }

    @Test
    @DisplayName("treats an empty file as absent")
    void handlesEmptyFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        JsonConfigService service = new JsonConfigService(file);
        assertEquals(5, service.get().getReminderMinutes());
    }

    @Test
    @DisplayName("creates the parent directory on first save")
    void createsParentDirectory(@TempDir Path dir) {
        Path file = dir.resolve("nested").resolve("deeper").resolve("config.json");
        JsonConfigService service = new JsonConfigService(file);
        service.update(config -> config.setCity("Tunis"));
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    @DisplayName("notifies listeners on every update")
    void notifiesListeners(@TempDir Path dir) {
        JsonConfigService service = new JsonConfigService(dir.resolve("config.json"));
        AtomicInteger calls = new AtomicInteger();
        List<String> seenCities = new ArrayList<>();

        java.util.function.Consumer<AppConfig> listener = config -> {
            calls.incrementAndGet();
            seenCities.add(config.getCity());
        };
        service.addChangeListener(listener);

        service.update(config -> config.setCity("Dhaka"));
        service.update(config -> config.setCity("Jakarta"));
        assertEquals(2, calls.get());
        assertEquals(List.of("Dhaka", "Jakarta"), seenCities);

        service.removeChangeListener(listener);
        service.update(config -> config.setCity("Cairo"));
        assertEquals(2, calls.get(), "a removed listener must not be called again");
    }

    @Test
    @DisplayName("a throwing listener does not break the update")
    void toleratesThrowingListener(@TempDir Path dir) {
        JsonConfigService service = new JsonConfigService(dir.resolve("config.json"));
        service.addChangeListener(config -> {
            throw new IllegalStateException("listener blew up");
        });
        AtomicInteger healthy = new AtomicInteger();
        service.addChangeListener(config -> healthy.incrementAndGet());

        service.update(config -> config.setCity("Rabat"));

        assertEquals("Rabat", service.get().getCity());
        assertEquals(1, healthy.get(), "the second listener must still be notified");
    }

    @Test
    @DisplayName("reload discards in-memory changes that were never saved")
    void reloadRereadsFromDisk(@TempDir Path dir) {
        Path file = dir.resolve("config.json");
        JsonConfigService service = new JsonConfigService(file);
        service.update(config -> config.setCity("Tashkent"));

        // Mutate the shared instance directly, bypassing update().
        service.get().setCity("Nowhere");
        service.reload();

        assertEquals("Tashkent", service.get().getCity());
    }

    @Test
    @DisplayName("leaves no temporary file behind after a save")
    void leavesNoTempFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        JsonConfigService service = new JsonConfigService(file);
        service.update(config -> config.setCity("Baku"));

        try (var entries = Files.list(dir)) {
            assertTrue(entries.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }
}
