package com.ctrends.salahguardian.viewmodel;

import com.ctrends.salahguardian.model.PrayerName;
import com.ctrends.salahguardian.model.PrayerTime;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * View model for a single row of the prayer times card.
 *
 * <p>The {@code next} and {@code past} flags drive the row's CSS pseudo classes,
 * which is how the highlight moves down the list as the day progresses without
 * the view rebuilding anything.</p>
 *
 * @author CTrends Software
 */
public class PrayerRowViewModel {

    private final PrayerName prayer;
    private final ReadOnlyStringWrapper name = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper arabicName = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper time = new ReadOnlyStringWrapper();
    private final BooleanProperty next = new SimpleBooleanProperty(false);
    private final BooleanProperty past = new SimpleBooleanProperty(false);

    /**
     * @param entry     the prayer this row represents
     * @param friday    whether the day is a Friday, renaming Dhuhr to Jumu'ah
     * @param use24Hour clock format
     */
    public PrayerRowViewModel(PrayerTime entry, boolean friday, boolean use24Hour) {
        this.prayer = entry.name();
        this.name.set(entry.name().displayName(friday));
        this.arabicName.set(entry.name().arabicName());
        this.time.set(entry.formatted(use24Hour));
    }

    /**
     * @return the prayer this row shows
     */
    public PrayerName prayer() {
        return prayer;
    }

    /**
     * @return the transliterated name property
     */
    public ReadOnlyStringWrapper nameProperty() {
        return name;
    }

    /**
     * @return the Arabic name property
     */
    public ReadOnlyStringWrapper arabicNameProperty() {
        return arabicName;
    }

    /**
     * @return the formatted clock time property
     */
    public ReadOnlyStringWrapper timeProperty() {
        return time;
    }

    /**
     * @return whether this row is the upcoming prayer
     */
    public BooleanProperty nextProperty() {
        return next;
    }

    /**
     * @return whether this prayer's time has already passed
     */
    public BooleanProperty pastProperty() {
        return past;
    }
}
