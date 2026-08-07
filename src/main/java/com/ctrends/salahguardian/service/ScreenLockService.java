package com.ctrends.salahguardian.service;

/**
 * Locks the current desktop session.
 *
 * <p>Locking is always performed against the user's <em>own</em> session and
 * never requires elevated privileges: every mechanism behind this interface is
 * one the desktop already exposes to unprivileged clients.</p>
 *
 * @author CTrends Software
 */
public interface ScreenLockService {

    /**
     * @return {@code true} when a working lock mechanism was found on this
     *         desktop, so the feature is worth offering in Settings
     */
    boolean isAvailable();

    /**
     * Locks the session.
     *
     * <p>Must never throw: a failure to lock is reported through the return
     * value and logged, because an exception escaping here would propagate into
     * the reminder scheduler.</p>
     *
     * @return {@code true} when a lock command was accepted by the desktop
     */
    boolean lock();

    /**
     * @return a short description of the mechanism in use, for logs and the
     *         settings screen
     */
    String describe();
}
