package com.ctrends.salahguardian.view;

import com.ctrends.salahguardian.config.Theme;
import com.ctrends.salahguardian.i18n.Messages;
import com.ctrends.salahguardian.view.components.Card;
import com.ctrends.salahguardian.view.components.PrayerRow;
import com.ctrends.salahguardian.viewmodel.DashboardViewModel;
import com.ctrends.salahguardian.viewmodel.PrayerRowViewModel;
import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The main window: a scrollable column of Material inspired cards showing the
 * current location, the live clock, the next prayer with its countdown, and
 * today's and tomorrow's timetables.
 *
 * <p>Built programmatically rather than from FXML on purpose. A single
 * self-contained scene graph has no resource to fail to load at runtime, which
 * matters for a jpackage image that has to behave identically on six different
 * distributions; the styling still lives entirely in CSS.</p>
 *
 * <p>Contains no logic beyond layout and binding - everything it displays comes
 * from {@link DashboardViewModel}.</p>
 *
 * @author CTrends Software
 */
public class DashboardView {

    private final DashboardViewModel viewModel;
    private final BorderPane root = new BorderPane();
    private final Scene scene;

    private Runnable onOpenSettings = () -> { };
    private Runnable onRefreshLocation = () -> { };

    /**
     * @param viewModel the data source every control binds to
     */
    public DashboardView(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
        root.getStyleClass().add("root-pane");
        root.setTop(buildHeader());
        root.setCenter(buildBody());
        root.setBottom(buildStatusBar());
        this.scene = new Scene(root, 940, 720);
        applyTheme(viewModel.themeProperty().get());
        viewModel.themeProperty().addListener((obs, was, is) -> applyTheme(is));
    }

    /**
     * @return the scene to place in a {@code Stage}
     */
    public Scene scene() {
        return scene;
    }

    /**
     * @param handler invoked when the user asks for the settings window
     */
    public void setOnOpenSettings(Runnable handler) {
        this.onOpenSettings = handler == null ? () -> { } : handler;
    }

    /**
     * @param handler invoked when the user asks for a fresh location lookup
     */
    public void setOnRefreshLocation(Runnable handler) {
        this.onRefreshLocation = handler == null ? () -> { } : handler;
    }

    /**
     * Swaps the stylesheet, keeping any caller supplied ones intact.
     *
     * @param theme the palette to apply
     */
    public final void applyTheme(Theme theme) {
        scene.getStylesheets().clear();
        Theme effective = theme == null ? Theme.DARK : theme;
        var url = DashboardView.class.getResource(effective.stylesheet());
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }

    // ----- header -----------------------------------------------------------

    private Region buildHeader() {
        Label title = new Label(Messages.get("app.name"));
        title.getStyleClass().add("app-title");

        Label arabic = new Label(Messages.get("app.tagline"));
        arabic.getStyleClass().add("app-title-arabic");

        VBox titles = new VBox(-2, title, arabic);
        titles.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToggleButton silent = new ToggleButton(Messages.get("dashboard.silent"));
        silent.getStyleClass().add("pill-toggle");
        silent.setTooltip(new Tooltip(Messages.get("dashboard.tooltip.silent")));
        silent.setSelected(viewModel.silentModeProperty().get());
        viewModel.silentModeProperty().addListener((obs, was, is) -> silent.setSelected(is));
        silent.setOnAction(event -> viewModel.setSilentMode(silent.isSelected()));

        ToggleButton reminders = new ToggleButton(Messages.get("dashboard.reminders"));
        reminders.getStyleClass().add("pill-toggle");
        reminders.setTooltip(new Tooltip(Messages.get("dashboard.tooltip.reminders")));
        reminders.setSelected(viewModel.remindersEnabledProperty().get());
        viewModel.remindersEnabledProperty().addListener((obs, was, is) -> reminders.setSelected(is));
        reminders.setOnAction(event -> viewModel.setRemindersEnabled(reminders.isSelected()));

        Button settings = new Button(Messages.get("dashboard.settings"));
        settings.getStyleClass().add("ghost-button");
        settings.setOnAction(event -> onOpenSettings.run());

        HBox header = new HBox(12, titles, spacer, reminders, silent, settings);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 24, 16, 24));
        return header;
    }

    // ----- body -------------------------------------------------------------

    private Region buildBody() {
        VBox column = new VBox(16,
                buildHeroRow(),
                buildContextRow(),
                buildTimetableCard(Messages.get("dashboard.todaysTimes"), viewModel.todayRows()),
                buildTimetableCard(Messages.get("dashboard.tomorrowsTimes"), viewModel.tomorrowRows()));
        column.setPadding(new Insets(4, 24, 24, 24));

        ScrollPane scroller = new ScrollPane(column);
        scroller.getStyleClass().add("body-scroll");
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroller;
    }

    /**
     * The dominant row: next prayer plus its countdown.
     */
    private Region buildHeroRow() {
        Label prayerName = new Label();
        prayerName.getStyleClass().add("hero-prayer-name");
        prayerName.textProperty().bind(viewModel.nextPrayerNameProperty());

        Label prayerArabic = new Label();
        prayerArabic.getStyleClass().add("hero-prayer-arabic");
        prayerArabic.textProperty().bind(viewModel.nextPrayerArabicProperty());

        Label prayerTime = new Label();
        prayerTime.getStyleClass().add("hero-prayer-time");
        prayerTime.textProperty().bind(viewModel.nextPrayerTimeProperty());

        VBox left = new VBox(2, prayerName, prayerArabic, prayerTime);
        left.setAlignment(Pos.CENTER_LEFT);

        Label countdown = new Label();
        countdown.getStyleClass().add("hero-countdown");
        countdown.textProperty().bind(viewModel.countdownProperty());

        Label caption = new Label();
        caption.getStyleClass().add("hero-countdown-caption");
        caption.textProperty().bind(viewModel.countdownCaptionProperty());

        VBox right = new VBox(-4, countdown, caption);
        right.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(20, left, spacer, right);
        row.setAlignment(Pos.CENTER_LEFT);

        Label approximation = new Label();
        approximation.getStyleClass().add("approximation-notice");
        approximation.setWrapText(true);
        approximation.textProperty().bind(viewModel.approximationNoticeProperty());
        approximation.visibleProperty().bind(
                Bindings.isNotEmpty(viewModel.approximationNoticeProperty()));
        approximation.managedProperty().bind(approximation.visibleProperty());

        Card card = new Card(Messages.get("dashboard.nextPrayer")).asAccent().growHorizontally();
        card.add(row, approximation);

        // A gentle fade whenever the prayer name changes, so the transition to
        // the next prayer is noticeable without being distracting.
        viewModel.nextPrayerNameProperty().addListener((obs, was, is) -> {
            FadeTransition fade = new FadeTransition(Duration.millis(320), row);
            fade.setFromValue(0.35);
            fade.setToValue(1.0);
            fade.play();
        });
        return card;
    }

    /**
     * Location, date and clock, laid out in a flow so the window stays usable
     * when it is narrowed.
     */
    private Region buildContextRow() {
        Card location = new Card(Messages.get("dashboard.currentLocation")).growHorizontally();
        Label place = new Label();
        place.getStyleClass().add("card-primary-value");
        place.textProperty().bind(viewModel.locationLabelProperty());
        Label coordinates = new Label();
        coordinates.getStyleClass().add("card-secondary-value");
        coordinates.textProperty().bind(viewModel.coordinateLabelProperty());
        Label source = new Label();
        source.getStyleClass().add("card-caption");
        source.textProperty().bind(viewModel.locationSourceLabelProperty());
        Button refresh = new Button(Messages.get("dashboard.detectAgain"));
        refresh.getStyleClass().add("ghost-button-small");
        refresh.setOnAction(event -> onRefreshLocation.run());
        location.add(place, coordinates, source, refresh);

        Card date = new Card(Messages.get("dashboard.date")).growHorizontally();
        Label gregorian = new Label();
        gregorian.getStyleClass().add("card-primary-value");
        gregorian.textProperty().bind(viewModel.gregorianDateProperty());
        Label hijri = new Label();
        hijri.getStyleClass().add("card-secondary-value");
        hijri.textProperty().bind(viewModel.hijriDateProperty());
        hijri.visibleProperty().bind(Bindings.isNotEmpty(viewModel.hijriDateProperty()));
        hijri.managedProperty().bind(hijri.visibleProperty());
        Label ramadan = new Label(Messages.get("dashboard.ramadanBadge"));
        ramadan.getStyleClass().add("badge-ramadan");
        ramadan.visibleProperty().bind(viewModel.ramadanProperty());
        ramadan.managedProperty().bind(ramadan.visibleProperty());
        date.add(gregorian, hijri, ramadan);

        Card clock = new Card(Messages.get("dashboard.currentTime")).growHorizontally();
        Label time = new Label();
        time.getStyleClass().add("card-clock");
        time.textProperty().bind(viewModel.currentTimeProperty());
        clock.add(time);

        FlowPane row = new FlowPane(16, 16, location, date, clock);
        row.setPrefWrapLength(900);
        return row;
    }

    /**
     * A timetable card whose rows rebuild themselves when the underlying list
     * changes.
     */
    private Region buildTimetableCard(String title, ObservableList<PrayerRowViewModel> rows) {
        Card card = new Card(title).growHorizontally();
        VBox container = new VBox(2);
        card.add(container);

        Runnable rebuild = () -> {
            container.getChildren().clear();
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    Separator separator = new Separator();
                    separator.getStyleClass().add("row-separator");
                    container.getChildren().add(separator);
                }
                container.getChildren().add(new PrayerRow(rows.get(i)));
            }
        };
        rows.addListener((ListChangeListener<PrayerRowViewModel>) change -> rebuild.run());
        rebuild.run();
        return card;
    }

    // ----- status bar -------------------------------------------------------

    private Region buildStatusBar() {
        Label status = new Label();
        status.getStyleClass().add("status-label");
        status.textProperty().bind(viewModel.statusMessageProperty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label(Messages.get("dashboard.trayHint"));
        hint.getStyleClass().add("status-hint");

        HBox bar = new HBox(12, status, spacer, hint);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 24, 14, 24));
        return bar;
    }
}
