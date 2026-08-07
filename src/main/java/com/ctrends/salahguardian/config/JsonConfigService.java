package com.ctrends.salahguardian.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Gson backed {@link ConfigService} that stores preferences as pretty printed
 * JSON in {@code ~/.config/salahguardian/config.json}.
 *
 * <h2>Durability</h2>
 * Saves are atomic: the document is written to a sibling {@code .tmp} file and
 * then moved over the target. A crash mid-write therefore leaves the previous
 * good configuration intact rather than a truncated file.
 *
 * <h2>Resilience</h2>
 * A missing file yields defaults. A corrupt file is moved aside to
 * {@code config.json.corrupt} and defaults are used, so the user never faces a
 * start-up failure caused by bad JSON.
 *
 * @author CTrends Software
 */
@Singleton
public class JsonConfigService implements ConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(JsonConfigService.class);

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path configFile;
    private final List<Consumer<AppConfig>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    private volatile AppConfig config;

    /**
     * Creates a service backed by the standard XDG location.
     */
    public JsonConfigService() {
        this(ConfigPaths.configFile());
    }

    /**
     * Creates a service backed by an explicit file, which is what the unit
     * tests use to stay away from the real home directory.
     *
     * @param configFile destination document
     */
    public JsonConfigService(Path configFile) {
        this.configFile = configFile;
    }

    @Override
    public AppConfig get() {
        AppConfig local = config;
        if (local == null) {
            synchronized (lock) {
                if (config == null) {
                    config = load();
                }
                local = config;
            }
        }
        return local;
    }

    @Override
    public void update(Consumer<AppConfig> mutation) {
        AppConfig current = get();
        synchronized (lock) {
            mutation.accept(current);
            current.normalise();
            writeToDisk(current);
        }
        notifyListeners(current);
    }

    @Override
    public void save() {
        AppConfig current = get();
        synchronized (lock) {
            current.normalise();
            writeToDisk(current);
        }
    }

    @Override
    public void reload() {
        AppConfig reloaded;
        synchronized (lock) {
            config = load();
            reloaded = config;
        }
        notifyListeners(reloaded);
    }

    @Override
    public void addChangeListener(Consumer<AppConfig> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeChangeListener(Consumer<AppConfig> listener) {
        listeners.remove(listener);
    }

    /**
     * @return the file this service reads from and writes to
     */
    public Path configFile() {
        return configFile;
    }

    private AppConfig load() {
        if (!Files.isRegularFile(configFile)) {
            LOG.info("No configuration at {} - starting with defaults", configFile);
            return new AppConfig().normalise();
        }
        // Tighten on read, not only on write. A file left at 0664 by a version
        // that predates SecureFiles would otherwise keep the user's coordinates
        // group- and world-readable indefinitely, because a user who never
        // changes a setting never triggers a save.
        tightenExistingFile();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            AppConfig loaded = gson.fromJson(reader, AppConfig.class);
            if (loaded == null) {
                LOG.warn("Configuration at {} was empty - using defaults", configFile);
                return new AppConfig().normalise();
            }
            LOG.info("Loaded configuration from {}", configFile);
            return loaded.migrate().normalise();
        } catch (JsonSyntaxException e) {
            quarantineCorruptFile(e);
            return new AppConfig().normalise();
        } catch (IOException e) {
            LOG.error("Unable to read configuration {} - using defaults", configFile, e);
            return new AppConfig().normalise();
        }
    }

    /**
     * Brings an existing configuration file up to {@code 0600}, and its parent
     * directory to {@code 0700}, reporting only when something actually changed.
     */
    private void tightenExistingFile() {
        try {
            Set<PosixFilePermission> before = Files.getPosixFilePermissions(configFile);
            if (before.equals(SecureFiles.OWNER_ONLY_FILE)) {
                return;
            }
            if (SecureFiles.harden(configFile, SecureFiles.OWNER_ONLY_FILE)) {
                LOG.info("Tightened permissions on {} - it held your coordinates at a mode "
                        + "other users could read", configFile);
            }
            Path parent = configFile.getParent();
            if (parent != null) {
                SecureFiles.harden(parent, SecureFiles.OWNER_ONLY_DIRECTORY);
            }
        } catch (IOException | UnsupportedOperationException e) {
            LOG.debug("Could not inspect permissions on {}", configFile, e);
        }
    }

    private void quarantineCorruptFile(Exception cause) {
        Path corrupt = configFile.resolveSibling(configFile.getFileName() + ".corrupt");
        LOG.error("Configuration {} is not valid JSON - moving it to {} and using defaults",
                configFile, corrupt, cause);
        try {
            Files.move(configFile, corrupt, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveFailure) {
            LOG.warn("Could not quarantine the corrupt configuration file", moveFailure);
        }
    }

    private void writeToDisk(AppConfig toWrite) {
        Path parent = configFile.getParent();
        Path temp = null;
        try {
            if (parent != null) {
                SecureFiles.createPrivateDirectory(parent);
            }
            temp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            // The document holds the user's coordinates, so it is created 0600
            // rather than chmod-ed afterwards: doing it afterwards would leave
            // a window in which a home address sat at the ambient umask.
            try (Writer writer = SecureFiles.newPrivateWriter(temp)) {
                gson.toJson(toWrite, writer);
            }
            try {
                Files.move(temp, configFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException atomicUnsupported) {
                // Some network / fuse filesystems cannot move atomically.
                LOG.debug("Atomic move unsupported on this filesystem, falling back", atomicUnsupported);
                Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
            // ATOMIC_MOVE preserves the temp file's mode, but a REPLACE_EXISTING
            // fallback onto an older 0664 file does not, so confirm either way.
            SecureFiles.harden(configFile, SecureFiles.OWNER_ONLY_FILE);
            LOG.debug("Configuration saved to {}", configFile);
        } catch (IOException e) {
            LOG.error("Failed to save configuration to {} - changes stay in memory only",
                    configFile, e);
            deleteQuietly(temp);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }

    private void notifyListeners(AppConfig current) {
        for (Consumer<AppConfig> listener : listeners) {
            try {
                listener.accept(current);
            } catch (RuntimeException e) {
                LOG.warn("Configuration change listener failed", e);
            }
        }
    }
}
