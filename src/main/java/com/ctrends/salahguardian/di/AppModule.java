package com.ctrends.salahguardian.di;

import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.config.JsonConfigService;
import com.ctrends.salahguardian.notification.CompositeNotificationService;
import com.ctrends.salahguardian.notification.NotificationService;
import com.ctrends.salahguardian.prayer.AdhanPrayerTimeCalculator;
import com.ctrends.salahguardian.prayer.PrayerTimeCalculator;
import com.ctrends.salahguardian.view.TrayIconManager;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.awt.TrayIcon;
import java.util.function.Supplier;

/**
 * Guice bindings for the whole application.
 *
 * <p>Only the seams worth abstracting are bound here - configuration storage,
 * the calculation engine and notification delivery - because those are the
 * three places a different implementation is genuinely plausible. Everything
 * else is a concrete {@code @Singleton} that Guice constructs directly from its
 * {@code @Inject} constructor, which keeps the module short and the wiring
 * obvious.</p>
 *
 * @author CTrends Software
 */
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ConfigService.class).to(JsonConfigService.class).in(Singleton.class);
        bind(PrayerTimeCalculator.class).to(AdhanPrayerTimeCalculator.class).in(Singleton.class);
        bind(NotificationService.class).to(CompositeNotificationService.class).in(Singleton.class);
    }

    /**
     * Supplies the live tray icon to the balloon notification fallback.
     *
     * <p>Expressed as a {@link Supplier} rather than injecting the icon itself
     * because the icon does not exist until the tray has been installed, and it
     * may never exist at all on a desktop without a tray.</p>
     *
     * @param trayIconManager the owner of the tray icon
     * @return a supplier that yields the icon, or {@code null} when there is none
     */
    @Provides
    @Singleton
    public Supplier<TrayIcon> provideTrayIconSupplier(TrayIconManager trayIconManager) {
        return trayIconManager::trayIcon;
    }
}
