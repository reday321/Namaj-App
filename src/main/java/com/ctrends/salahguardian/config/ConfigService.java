package com.ctrends.salahguardian.config;

import java.util.function.Consumer;

/**
 * Read/write access to the persisted user preferences.
 *
 * <p>Implementations must be thread safe: the scheduler thread reads the
 * configuration while the JavaFX thread writes it.</p>
 *
 * @author CTrends Software
 */
public interface ConfigService {

    /**
     * Returns the live configuration, loading it from disk on first access.
     * The returned instance is the one the whole application shares - callers
     * that intend to edit should use {@link #update(Consumer)} instead of
     * mutating it directly, so that listeners fire and the file is written.
     *
     * @return the current configuration, never {@code null}
     */
    AppConfig get();

    /**
     * Applies a mutation and persists the result atomically, then notifies
     * every registered listener.
     *
     * <p>Persistence failures are logged and swallowed: a read-only home
     * directory must not take the application down.</p>
     *
     * @param mutation the change to apply to the live configuration
     */
    void update(Consumer<AppConfig> mutation);

    /**
     * Forces a write of the current in-memory state.
     */
    void save();

    /**
     * Discards the in-memory state and re-reads the file from disk.
     */
    void reload();

    /**
     * Registers a listener invoked after every successful {@link #update}.
     * Listeners are invoked on the calling thread.
     *
     * @param listener receives the updated configuration
     */
    void addChangeListener(Consumer<AppConfig> listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to detach
     */
    void removeChangeListener(Consumer<AppConfig> listener);
}
