package com.ctrends.salahguardian.view.components;

import com.ctrends.salahguardian.viewmodel.PrayerRowViewModel;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * One line of the prayer times table: English name, Arabic name and clock time.
 *
 * <p>Binds directly to a {@link PrayerRowViewModel} and mirrors its two flags
 * onto the {@code :next} and {@code :past} CSS pseudo classes, letting the
 * stylesheet decide entirely how a highlighted or elapsed prayer looks.</p>
 *
 * @author CTrends Software
 */
public class PrayerRow extends HBox {

    private static final PseudoClass NEXT = PseudoClass.getPseudoClass("next");
    private static final PseudoClass PAST = PseudoClass.getPseudoClass("past");

    /**
     * @param viewModel the row's data source
     */
    public PrayerRow(PrayerRowViewModel viewModel) {
        getStyleClass().add("prayer-row");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(14);

        Label name = new Label();
        name.getStyleClass().add("prayer-row-name");
        name.textProperty().bind(viewModel.nameProperty());

        Label arabic = new Label();
        arabic.getStyleClass().add("prayer-row-arabic");
        arabic.textProperty().bind(viewModel.arabicNameProperty());

        VBox names = new VBox(2, name, arabic);
        names.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label time = new Label();
        time.getStyleClass().add("prayer-row-time");
        time.textProperty().bind(viewModel.timeProperty());

        getChildren().addAll(names, spacer, time);

        viewModel.nextProperty().addListener((obs, was, is) -> pseudoClassStateChanged(NEXT, is));
        viewModel.pastProperty().addListener((obs, was, is) -> pseudoClassStateChanged(PAST, is));
        pseudoClassStateChanged(NEXT, viewModel.nextProperty().get());
        pseudoClassStateChanged(PAST, viewModel.pastProperty().get());
    }
}
