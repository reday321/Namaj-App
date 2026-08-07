package com.ctrends.salahguardian.view;

import com.ctrends.salahguardian.config.Theme;
import com.ctrends.salahguardian.model.CalculationMethodOption;
import com.ctrends.salahguardian.model.HighLatitudeRuleOption;
import com.ctrends.salahguardian.model.MadhabOption;
import com.ctrends.salahguardian.view.components.Card;
import com.ctrends.salahguardian.viewmodel.SettingsViewModel;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The settings window.
 *
 * <p>Every control is bound bidirectionally to {@link SettingsViewModel}, which
 * persists each change the moment it happens - hence there is no Save button.
 * The only explicit action is "Apply coordinates", because half-typed latitude
 * and longitude values should not be written on every keystroke.</p>
 *
 * @author CTrends Software
 */
public class SettingsView {

    private final SettingsViewModel viewModel;
    private final Scene scene;

    /**
     * @param viewModel the data source every control binds to
     */
    public SettingsView(SettingsViewModel viewModel) {
        this.viewModel = viewModel;

        VBox column = new VBox(16,
                buildLocationCard(),
                buildCalculationCard(),
                buildReminderCard(),
                buildFocusCard(),
                buildAppearanceCard(),
                buildStartupCard());
        column.setPadding(new Insets(20));

        ScrollPane scroller = new ScrollPane(column);
        scroller.getStyleClass().add("body-scroll");
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildHeader());
        root.setCenter(scroller);
        root.setBottom(buildStatusBar());

        this.scene = new Scene(root, 660, 760);
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
     * Swaps the stylesheet.
     *
     * @param theme the palette to apply
     */
    public final void applyTheme(Theme theme) {
        scene.getStylesheets().clear();
        Theme effective = theme == null ? Theme.DARK : theme;
        var url = SettingsView.class.getResource(effective.stylesheet());
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }

    private Region buildHeader() {
        Label title = new Label("Settings");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Changes are saved automatically");
        subtitle.getStyleClass().add("app-subtitle");
        VBox header = new VBox(-2, title, subtitle);
        header.getStyleClass().add("app-header");
        header.setPadding(new Insets(20, 24, 16, 24));
        return header;
    }

    // ----- cards ------------------------------------------------------------

    private Region buildLocationCard() {
        Card card = new Card("Location").growHorizontally();

        CheckBox autoDetect = new CheckBox("Detect my location automatically");
        autoDetect.setTooltip(new Tooltip(
                "Tries GeoClue first, then IP geolocation. Turn this off to keep "
                        + "the coordinates you enter below."));
        autoDetect.selectedProperty().bindBidirectional(viewModel.autoDetectLocationProperty());

        TextField latitude = numericField(viewModel.latitudeProperty().get());
        TextField longitude = numericField(viewModel.longitudeProperty().get());
        TextField city = new TextField();
        TextField country = new TextField();

        city.textProperty().bindBidirectional(viewModel.cityProperty());
        country.textProperty().bindBidirectional(viewModel.countryProperty());

        // Keep the fields in step when detection updates the view model.
        viewModel.latitudeProperty().addListener((obs, was, is) ->
                latitude.setText(String.format("%.6f", is.doubleValue())));
        viewModel.longitudeProperty().addListener((obs, was, is) ->
                longitude.setText(String.format("%.6f", is.doubleValue())));

        Button apply = new Button("Apply coordinates");
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(event -> {
            parseInto(latitude, viewModel.latitudeProperty());
            parseInto(longitude, viewModel.longitudeProperty());
            viewModel.applyManualLocation();
        });

        Button detect = new Button("Detect now");
        detect.getStyleClass().add("ghost-button-small");
        detect.setOnAction(event -> viewModel.redetectLocation());

        GridPane grid = grid();
        addRow(grid, 0, "Latitude", latitude);
        addRow(grid, 1, "Longitude", longitude);
        addRow(grid, 2, "City", city);
        addRow(grid, 3, "Country", country);

        HBox actions = new HBox(10, apply, detect);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.add(autoDetect, grid, actions);
        return card;
    }

    private Region buildCalculationCard() {
        Card card = new Card("Prayer time calculation").growHorizontally();

        ComboBox<CalculationMethodOption> method = new ComboBox<>();
        method.getItems().setAll(CalculationMethodOption.values());
        method.setConverter(converter(CalculationMethodOption::displayName));
        method.valueProperty().bindBidirectional(viewModel.calculationMethodProperty());
        method.setMaxWidth(Double.MAX_VALUE);
        method.setTooltip(new Tooltip("The twilight angle convention used for Fajr and Isha"));

        ComboBox<MadhabOption> madhab = new ComboBox<>();
        madhab.getItems().setAll(MadhabOption.values());
        madhab.setConverter(converter(MadhabOption::displayName));
        madhab.valueProperty().bindBidirectional(viewModel.madhabProperty());
        madhab.setMaxWidth(Double.MAX_VALUE);
        madhab.setTooltip(new Tooltip("Affects the Asr time only"));

        ComboBox<HighLatitudeRuleOption> highLatitude = new ComboBox<>();
        highLatitude.getItems().setAll(HighLatitudeRuleOption.values());
        highLatitude.setConverter(converter(HighLatitudeRuleOption::displayName));
        highLatitude.valueProperty().bindBidirectional(viewModel.highLatitudeRuleProperty());
        highLatitude.setMaxWidth(Double.MAX_VALUE);
        highLatitude.setTooltip(new Tooltip(
                "Used where the sun never reaches the twilight angle, above roughly 48 degrees"));

        Spinner<Double> fajrAngle = angleSpinner(viewModel.customFajrAngleProperty().get());
        Spinner<Double> ishaAngle = angleSpinner(viewModel.customIshaAngleProperty().get());
        fajrAngle.valueProperty().addListener((obs, was, is) ->
                viewModel.customFajrAngleProperty().set(is));
        ishaAngle.valueProperty().addListener((obs, was, is) ->
                viewModel.customIshaAngleProperty().set(is));

        // The angle fields only make sense for the Custom method.
        var customSelected = Bindings.createBooleanBinding(
                viewModel::requiresCustomAngles, viewModel.calculationMethodProperty());
        fajrAngle.disableProperty().bind(customSelected.not());
        ishaAngle.disableProperty().bind(customSelected.not());

        GridPane grid = grid();
        addRow(grid, 0, "Method", method);
        addRow(grid, 1, "Madhab", madhab);
        addRow(grid, 2, "High latitude rule", highLatitude);
        addRow(grid, 3, "Custom Fajr angle", fajrAngle);
        addRow(grid, 4, "Custom Isha angle", ishaAngle);

        card.add(grid);
        return card;
    }

    private Region buildReminderCard() {
        Card card = new Card("Reminders").growHorizontally();

        CheckBox notifications = new CheckBox("Enable desktop notifications");
        notifications.selectedProperty().bindBidirectional(viewModel.notificationsEnabledProperty());

        CheckBox atPrayerTime = new CheckBox("Notify at the prayer time itself");
        atPrayerTime.selectedProperty().bindBidirectional(viewModel.remindAtPrayerTimeProperty());

        CheckBox silent = new CheckBox("Silent mode (mute all reminders)");
        silent.selectedProperty().bindBidirectional(viewModel.silentModeProperty());

        CheckBox friday = new CheckBox("Friday reminder for Jumu'ah");
        friday.selectedProperty().bindBidirectional(viewModel.fridayReminderEnabledProperty());

        CheckBox ramadan = new CheckBox("Suhoor and iftar reminders during Ramadan");
        ramadan.selectedProperty().bindBidirectional(viewModel.ramadanRemindersEnabledProperty());

        Spinner<Integer> minutes = new Spinner<>(0, 60, viewModel.reminderMinutesProperty().get());
        minutes.setEditable(true);
        minutes.setTooltip(new Tooltip("Set to 0 to switch the advance warning off"));
        minutes.valueProperty().addListener((obs, was, is) ->
                viewModel.reminderMinutesProperty().set(is));

        GridPane grid = grid();
        addRow(grid, 0, "Remind me before (minutes)", minutes);

        card.add(notifications, grid, atPrayerTime, friday, ramadan, silent);
        return card;
    }

    private Region buildFocusCard() {
        Card card = new Card("Prayer focus mode").growHorizontally();

        CheckBox enabled = new CheckBox("Show the fullscreen reminder at prayer time");
        enabled.setTooltip(new Tooltip(
                "Opens a fullscreen overlay instead of locking the screen. "
                        + "Escape and Alt+F4 are ignored; use Skip or Close."));
        enabled.selectedProperty().bindBidirectional(viewModel.focusModeEnabledProperty());

        Spinner<Integer> duration = new Spinner<>(30, 3600,
                viewModel.focusDurationSecondsProperty().get(), 30);
        duration.setEditable(true);
        duration.valueProperty().addListener((obs, was, is) ->
                viewModel.focusDurationSecondsProperty().set(is));
        duration.disableProperty().bind(enabled.selectedProperty().not());

        GridPane grid = grid();
        addRow(grid, 0, "Overlay duration (seconds)", duration);

        card.add(enabled, grid);
        return card;
    }

    private Region buildAppearanceCard() {
        Card card = new Card("Appearance").growHorizontally();

        ComboBox<Theme> theme = new ComboBox<>();
        theme.getItems().setAll(Theme.values());
        theme.setConverter(converter(Theme::displayName));
        theme.valueProperty().bindBidirectional(viewModel.themeProperty());
        theme.setMaxWidth(Double.MAX_VALUE);

        CheckBox clock24 = new CheckBox("Use a 24 hour clock");
        clock24.selectedProperty().bindBidirectional(viewModel.use24HourClockProperty());

        CheckBox hijri = new CheckBox("Show the Hijri date");
        hijri.selectedProperty().bindBidirectional(viewModel.showHijriDateProperty());

        GridPane grid = grid();
        addRow(grid, 0, "Theme", theme);

        card.add(grid, clock24, hijri);
        return card;
    }

    private Region buildStartupCard() {
        Card card = new Card("Startup").growHorizontally();

        CheckBox startOnLogin = new CheckBox("Start Salah Guardian when I log in");
        startOnLogin.setTooltip(new Tooltip(
                "Writes ~/.config/autostart/salah-guardian.desktop. "
                        + "Requires the installed package rather than a development build."));
        startOnLogin.selectedProperty().bindBidirectional(viewModel.startOnLoginProperty());

        CheckBox minimised = new CheckBox("Start minimised to the system tray");
        minimised.selectedProperty().bindBidirectional(viewModel.startMinimisedToTrayProperty());

        card.add(startOnLogin, minimised);
        return card;
    }

    private Region buildStatusBar() {
        Label status = new Label();
        status.getStyleClass().add("status-label");
        status.textProperty().bind(viewModel.statusMessageProperty());
        HBox bar = new HBox(status);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(10, 24, 14, 24));
        return bar;
    }

    // ----- small helpers ----------------------------------------------------

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        javafx.scene.layout.ColumnConstraints labels = new javafx.scene.layout.ColumnConstraints();
        labels.setMinWidth(180);
        javafx.scene.layout.ColumnConstraints fields = new javafx.scene.layout.ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setFillWidth(true);
        grid.getColumnConstraints().addAll(labels, fields);
        return grid;
    }

    private static void addRow(GridPane grid, int row, String label, Region control) {
        Label caption = new Label(label);
        caption.getStyleClass().add("field-label");
        control.setMaxWidth(Double.MAX_VALUE);
        grid.add(caption, 0, row);
        grid.add(control, 1, row);
    }

    private static TextField numericField(double initial) {
        TextField field = new TextField(String.format("%.6f", initial));
        field.getStyleClass().add("numeric-field");
        return field;
    }

    private static void parseInto(TextField field, javafx.beans.property.DoubleProperty target) {
        try {
            target.set(Double.parseDouble(field.getText().trim().replace(',', '.')));
        } catch (NumberFormatException e) {
            // Leave the previous value; the view model reports the problem.
            field.setText(String.format("%.6f", target.get()));
        }
    }

    private static Spinner<Double> angleSpinner(double initial) {
        Spinner<Double> spinner = new Spinner<>();
        spinner.setValueFactory(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(8.0, 25.0, initial, 0.5));
        spinner.setEditable(true);
        return spinner;
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> naming) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : naming.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null; // combo boxes here are not editable
            }
        };
    }
}
