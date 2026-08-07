package com.ctrends.salahguardian.service;

import com.ctrends.salahguardian.config.AppConfig;
import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.model.DailyPrayerSchedule;
import com.ctrends.salahguardian.model.ReminderKind;
import com.ctrends.salahguardian.notification.NotificationRequest;
import com.ctrends.salahguardian.notification.NotificationService;
import com.ctrends.salahguardian.notification.PrayerNotificationComposer;
import com.ctrends.salahguardian.prayer.PrayerScheduleService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The background engine that turns prayer times into notifications.
 *
 * <h2>How it schedules</h2>
 * Rather than parking a task on every reminder of the day, the service always
 * has exactly one pending one-shot task: the next reminder. When it fires, the
 * reminder is dispatched and the next one is scheduled. This keeps the executor
 * queue at a constant size and makes a settings change trivial to honour - the
 * pending task is cancelled and re-planned.
 *
 * <h2>Why a watchdog as well</h2>
 * A one-shot task alone is not enough on a laptop. Suspending the machine stops
 * the monotonic clock the executor uses, so a task due at 17:00 fires late by
 * however long the lid was closed. A watchdog therefore runs every
 * {@value #WATCHDOG_INTERVAL_SECONDS} seconds and re-plans whenever it sees
 * that the wall clock has moved differently from the elapsed time, that the
 * date has rolled over, or that the pending task has drifted.
 *
 * <h2>Missed reminders</h2>
 * A reminder whose moment passed while the machine was asleep is still
 * delivered when it is less than {@link #MISSED_GRACE} old, and dropped
 * otherwise - waking up to six hours of stale prayer notifications helps nobody.
 *
 * <h2>Threading</h2>
 * One daemon thread runs everything. Listener callbacks arrive on it and must
 * not block.
 *
 * @author CTrends Software
 */
@Singleton
public class PrayerSchedulerService implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PrayerSchedulerService.class);

    /** Cadence of the drift / rollover watchdog. */
    public static final int WATCHDOG_INTERVAL_SECONDS = 30;

    /** A reminder older than this is dropped rather than delivered late. */
    public static final Duration MISSED_GRACE = Duration.ofMinutes(2);

    /** Wall clock jump beyond this is treated as suspend/resume or an NTP step. */
    private static final Duration CLOCK_JUMP_THRESHOLD = Duration.ofSeconds(90);

    /** Bound on the dedupe memory, comfortably above one day of reminders. */
    private static final int MAX_DELIVERED_KEYS = 256;

    private final PrayerScheduleService scheduleService;
    private final ReminderPlanner planner;
    private final NotificationService notificationService;
    private final PrayerNotificationComposer composer;
    private final ConfigService configService;

    private final List<PrayerEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> deliveredKeys =
            java.util.Collections.synchronizedSet(new LinkedHashSet<>());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object scheduleLock = new Object();

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> pendingReminder;
    private ScheduledFuture<?> watchdog;
    private ReminderEvent nextEvent;
    private ZonedDateTime lastWatchdogWallClock;

    /**
     * @param scheduleService     supplies today's and tomorrow's timetables
     * @param planner             expands a timetable into reminder events
     * @param notificationService delivers the messages
     * @param composer            produces the message wording
     * @param configService       the user's reminder preferences
     */
    @Inject
    public PrayerSchedulerService(PrayerScheduleService scheduleService,
                                  ReminderPlanner planner,
                                  NotificationService notificationService,
                                  PrayerNotificationComposer composer,
                                  ConfigService configService) {
        this.scheduleService = scheduleService;
        this.planner = planner;
        this.notificationService = notificationService;
        this.composer = composer;
        this.configService = configService;
    }

    /**
     * Starts the background thread and schedules the first reminder.
     *
     * <p>Idempotent: calling it twice has no additional effect.</p>
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "salah-scheduler");
            thread.setDaemon(true);
            // Below normal: the UI thread must always win a scheduling contest.
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        lastWatchdogWallClock = scheduleService.now();
        watchdog = executor.scheduleWithFixedDelay(this::runWatchdog,
                WATCHDOG_INTERVAL_SECONDS, WATCHDOG_INTERVAL_SECONDS, TimeUnit.SECONDS);
        reschedule();
        LOG.info("Prayer scheduler started (notifications via {})", notificationService.describe());
    }

    /**
     * Cancels the pending work and shuts the thread down.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        synchronized (scheduleLock) {
            cancel(pendingReminder);
            cancel(watchdog);
            pendingReminder = null;
            watchdog = null;
            nextEvent = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        LOG.info("Prayer scheduler stopped");
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * @return {@code true} while the scheduler thread is alive
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Recomputes the plan and re-arms the pending task. Call after any change
     * to the configuration or the location.
     */
    public void reschedule() {
        if (!running.get()) {
            return;
        }
        synchronized (scheduleLock) {
            cancel(pendingReminder);
            pendingReminder = null;
            nextEvent = null;

            ZonedDateTime now = scheduleService.now();
            List<ReminderEvent> upcoming = collectUpcoming(now);
            if (upcoming.isEmpty()) {
                LOG.info("No further reminders are due today or tomorrow");
                return;
            }
            ReminderEvent next = upcoming.get(0);
            long delayMillis = Math.max(0, Duration.between(now, next.fireAt()).toMillis());
            nextEvent = next;
            pendingReminder = executor.schedule(() -> fire(next),
                    delayMillis, TimeUnit.MILLISECONDS);
            LOG.info("Next reminder: {} for {} at {} (in {})", next.kind(),
                    next.prayer().name().displayName(), next.fireAt().toLocalTime(),
                    Duration.ofMillis(delayMillis));
        }
        notifyScheduleChanged();
    }

    /**
     * @return the reminder currently armed, or {@code null} when none is
     */
    public ReminderEvent nextEvent() {
        synchronized (scheduleLock) {
            return nextEvent;
        }
    }

    /**
     * Registers a listener for reminder callbacks.
     *
     * @param listener the listener to attach
     */
    public void addListener(PrayerEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to detach
     */
    public void removeListener(PrayerEventListener listener) {
        listeners.remove(listener);
    }

    // ----- internals --------------------------------------------------------

    /**
     * Gathers the still-pending events of today and tomorrow, plus any event
     * that was missed within the grace window so it can be delivered late.
     */
    private List<ReminderEvent> collectUpcoming(ZonedDateTime now) {
        AppConfig config = configService.get();
        List<ReminderEvent> all = new ArrayList<>();
        try {
            DailyPrayerSchedule today = scheduleService.today();
            DailyPrayerSchedule tomorrow = scheduleService.tomorrow();
            all.addAll(planner.plan(today, config));
            all.addAll(planner.plan(tomorrow, config));
        } catch (RuntimeException e) {
            LOG.error("Could not build the reminder plan - retrying at the next watchdog tick", e);
            return List.of();
        }

        ZonedDateTime graceStart = now.minus(MISSED_GRACE);
        return all.stream()
                .filter(event -> event.fireAt().isAfter(graceStart))
                .filter(event -> !deliveredKeys.contains(event.dedupeKey()))
                .sorted()
                .toList();
    }

    /**
     * Delivers one reminder and immediately re-arms the next.
     */
    private void fire(ReminderEvent event) {
        try {
            if (!deliveredKeys.add(event.dedupeKey())) {
                LOG.debug("Skipping already delivered reminder {}", event.dedupeKey());
                return;
            }
            trimDeliveredKeys();

            AppConfig config = configService.get();
            if (!config.shouldNotify()) {
                LOG.debug("Reminder suppressed: notifications disabled or silent mode is on");
            } else {
                NotificationRequest request = composer.compose(event.kind(), event.prayer(),
                        event.lead(), isFriday(event), config.isUse24HourClock());
                boolean delivered = notificationService.send(request);
                LOG.info("Reminder {} for {} {}", event.kind(),
                        event.prayer().name().displayName(),
                        delivered ? "delivered" : "could not be shown");
            }
            notifyListeners(event);
        } catch (RuntimeException e) {
            LOG.error("Reminder dispatch failed", e);
        } finally {
            // Always re-arm, even if this delivery failed.
            reschedule();
        }
    }

    private boolean isFriday(ReminderEvent event) {
        return event.prayer().time().getDayOfWeek() == java.time.DayOfWeek.FRIDAY;
    }

    /**
     * Detects suspend/resume, NTP steps and date rollovers, and re-plans when
     * any of them is seen.
     */
    private void runWatchdog() {
        try {
            ZonedDateTime now = scheduleService.now();
            ZonedDateTime previous = lastWatchdogWallClock;
            lastWatchdogWallClock = now;

            if (previous != null) {
                Duration observed = Duration.between(previous, now);
                Duration expected = Duration.ofSeconds(WATCHDOG_INTERVAL_SECONDS);
                Duration drift = observed.minus(expected).abs();
                if (drift.compareTo(CLOCK_JUMP_THRESHOLD) > 0) {
                    LOG.info("Wall clock jumped by {} (suspend/resume or time sync) - re-planning",
                            observed);
                    scheduleService.invalidate();
                    reschedule();
                    return;
                }
                if (!previous.toLocalDate().equals(now.toLocalDate())) {
                    LOG.info("Date rolled over to {} - recalculating the timetable",
                            now.toLocalDate());
                    scheduleService.invalidate();
                    reschedule();
                    return;
                }
            }

            // Self-heal: if the armed task vanished but reminders remain, re-arm.
            synchronized (scheduleLock) {
                boolean idle = pendingReminder == null || pendingReminder.isDone();
                if (idle && !collectUpcoming(now).isEmpty()) {
                    LOG.warn("Scheduler was idle with reminders pending - re-arming");
                    scheduleWithoutLockRelease();
                }
            }
        } catch (RuntimeException e) {
            LOG.error("Watchdog tick failed", e);
        }
    }

    /**
     * Re-arms from inside an already held {@link #scheduleLock}.
     */
    private void scheduleWithoutLockRelease() {
        ZonedDateTime now = scheduleService.now();
        List<ReminderEvent> upcoming = collectUpcoming(now);
        if (upcoming.isEmpty()) {
            return;
        }
        ReminderEvent next = upcoming.get(0);
        long delayMillis = Math.max(0, Duration.between(now, next.fireAt()).toMillis());
        nextEvent = next;
        pendingReminder = executor.schedule(() -> fire(next), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void trimDeliveredKeys() {
        synchronized (deliveredKeys) {
            java.util.Iterator<String> iterator = deliveredKeys.iterator();
            while (deliveredKeys.size() > MAX_DELIVERED_KEYS && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private void notifyListeners(ReminderEvent event) {
        for (PrayerEventListener listener : listeners) {
            try {
                listener.onReminder(event);
            } catch (RuntimeException e) {
                LOG.warn("Reminder listener failed", e);
            }
        }
    }

    private void notifyScheduleChanged() {
        for (PrayerEventListener listener : listeners) {
            try {
                listener.onScheduleChanged();
            } catch (RuntimeException e) {
                LOG.warn("Schedule listener failed", e);
            }
        }
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

}
