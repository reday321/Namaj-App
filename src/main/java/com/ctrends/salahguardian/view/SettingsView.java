package com.ctrends.salahguardian.view;

import com.ctrends.salahguardian.config.Theme;
import com.ctrends.salahguardian.i18n.Language;
import com.ctrends.salahguardian.i18n.Messages;
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
        Label title = new Label(Messages.get("settings.title"));
        title.getStyleClass().add("app-title");
        Label subtitle = new Label(Messages.get("settings.subtitle"));
        subtitle.getStyleClass().add("app-subtitle");
        VBox header = new VBox(-2, title, subtitle);
        header.getStyleClass().add("app-header");
        header.setPadding(new Insets(20, 24, 16, 24));
        return header;
    }

    // ----- cards ------------------------------------------------------------

    private Region buildLocationCard() {
        Card card = new Card(Messages.get("settings.location")).growHorizontally();

        CheckBox autoDetect = new CheckBox(Messages.get("settings.autoDetect"));
        autoDetect.setTooltip(new Tooltip(Messages.get("settings.tooltip.autoDetect")));
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

        Button apply = new Button(Messages.get("settings.applyCoordinates"));
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(event -> {
            parseInto(latitude, viewModel.latitudeProperty());
            parseInto(longitude, viewModel.longitudeProperty());
            viewModel.applyManualLocation();
        });

        Button detect = new Button(Messages.get("settings.detectNow"));
        detect.getStyleClass().add("ghost-button-small");
        detect.setOnAction(event -> viewModel.redetectLocation());

        GridPane grid = grid();
        addRow(grid, 0, Messages.get("settings.latitude"), latitude);
        addRow(grid, 1, Messages.get("settings.longitude"), longitude);
        addRow(grid, 2, Messages.get("settings.city"), city);
        addRow(grid, 3, Messages.get("settings.country"), country);

        HBox actions = new HBox(10, apply, detect);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.add(autoDetect, grid, actions);
        return card;
    }

    private Region buildCalculationCard() {
        Card card = new Card(Messages.get("settings.calculation")).growHorizontally();

        ComboBox<CalculationMethodOption> method = new ComboBox<>();
        method.getItems().setAll(CalculationMethodOption.values());
        method.setConverter(converter(CalculationMethodOption::displayName));
        method.valueProperty().bindBidirectional(viewModel.calculationMethodProperty());
        method.setMaxWidth(Double.MAX_VALUE);
        method.setTooltip(new Tooltip(Messages.get("settings.tooltip.method")));

        ComboBox<MadhabOption> madhab = new ComboBox<>();
        madhab.getItems().setAll(MadhabOption.values());
        madhab.setConverter(converter(MadhabOption::displayName));
        madhab.valueProperty().bindBidirectional(viewModel.madhabProperty());
        madhab.setMaxWidth(Double.MAX_VALUE);
        madhab.setTooltip(new Tooltip(Messages.get("settings.tooltip.madhab")));

        ComboBox<HighLatitudeRuleOption> highLatitude = new ComboBox<>();
        highLatitude.getItems().setAll(HighLatitudeRuleOption.values());
        highLatitude.setConverter(converter(HighLatitudeRuleOption::displayName));
        highLatitude.valueProperty().bindBidirectional(viewModel.highLatitudeRuleProperty());
        highLatitude.setMaxWidth(Double.MAX_VALUE);
        highLatitude.setTooltip(new Tooltip(Messages.get("settings.tooltip.highLatitude")));

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
        addRow(grid, 0, Messages.get("settings.method"), method);
        addRow(grid, 1, Messages.get("settings.madhab"), madhab);
        addRow(grid, 2, Messages.get("settings.highLatitude"), highLatitude);
        addRow(grid, 3, Messages.get("settings.customFajrAngle"), fajrAngle);
        addRow(grid, 4, Messages.get("settings.customIshaAngle"), ishaAngle);

        card.add(grid);
        return card;
    }

    private Region buildReminderCard() {
        Card card = new Card(Messages.get("settings.reminders")).growHorizontally();

        CheckBox notifications = new CheckBox(Messages.get("settings.enableNotifications"));
        notifications.selectedProperty().bindBidirectional(viewModel.notificationsEnabledProperty());

        CheckBox atPrayerTime = new CheckBox(Messages.get("settings.remindAtTime"));
        atPrayerTime.selectedProperty().bindBidirectional(viewModel.remindAtPrayerTimeProperty());

        CheckBox silent = new CheckBox(Messages.get("settings.silentMode"));
        silent.selectedProperty().bindBidirectional(viewModel.silentModeProperty());

        CheckBox friday = new CheckBox(Messages.get("settings.fridayReminder"));
        friday.selectedProperty().bindBidirectional(viewModel.fridayReminderEnabledProperty());

        CheckBox ramadan = new CheckBox(Messages.get("settings.ramadanReminders"));
        ramadan.selectedProperty().bindBidirectional(viewModel.ramadanRemindersEnabledProperty());

        Spinner<Integer> minutes = new Spinner<>(0, 60, viewModel.reminderMinutesProperty().get());
        minutes.setEditable(true);
        minutes.setTooltip(new Tooltip(Messages.get("settings.tooltip.remindBefore")));
        minutes.valueProperty().addListener((obs, was, is) ->
                viewModel.reminderMinutesProperty().set(is));

        GridPane grid = grid();
        addRow(grid, 0, Messages.get("settings.remindBefore"), minutes);

        card.add(notifications, grid, atPrayerTime, friday, ramadan, silent);
        return card;
    }

    private Region buildFocusCard() {
        Card card = new Card(Messages.get("settings.focusMode")).growHorizontally();

        CheckBox enabled = new CheckBox(Messages.get("settings.enableFocus"));
        enabled.setTooltip(new Tooltip(Messages.get("settings.tooltip.focus")));
        enabled.selectedProperty().bindBidirectional(viewModel.focusModeEnabledProperty());

        Spinner<Integer> duration = new Spinner<>(30, 3600,
                viewModel.focusDurationSecondsProperty().get(), 30);
        duration.setEditable(true);
        duration.valueProperty().addListener((obs, was, is) ->
                viewModel.focusDurationSecondsProperty().set(is));
        duration.disableProperty().bind(enabled.selectedProperty().not());

        CheckBox lockScreen = new CheckBox(Messages.get("settings.lockScreen"));
        lockScreen.setTooltip(new Tooltip(Messages.get("settings.tooltip.lockScreen")));
        lockScreen.selectedProperty().bindBidirectional(viewModel.lockScreenAtPrayerTimeProperty());

        Spinner<Integer> lockDelay = new Spinner<>(0, 300,
                viewModel.lockDelaySecondsProperty().get(), 5);
        lockDelay.setEditable(true);
        lockDelay.setTooltip(new Tooltip(Messages.get("settings.tooltip.lockDelay")));
        lockDelay.valueProperty().addListener((obs, was, is) ->
                viewModel.lockDelaySecondsProperty().set(is));
        lockDelay.disableProperty().bind(lockScreen.selectedProperty().not());

        Label unavailable = new Label(Messages.get("settings.lockUnavailable"));
        unavailable.getStyleClass().add("field-warning");
        unavailable.setWrapText(true);
        // Offering a switch that cannot do anything is worse than hiding it.
        boolean lockPossible = viewModel.isScreenLockAvailable();
        lockScreen.setDisable(!lockPossible);
        unavailable.setVisible(!lockPossible);
        unavailable.setManaged(!lockPossible);

        GridPane grid = grid();
        addRow(grid, 0, Messages.get("settings.focusDuration"), duration);
        addRow(grid, 1, Messages.get("settings.lockDelay"), lockDelay);

        card.add(enabled, lockScreen, unavailable, grid);
        return card;
    }

    private Region buildAppearanceCard() {
        Card card = new Card(Messages.get("settings.appearance")).growHorizontally();

        ComboBox<Theme> theme = new ComboBox<>();
        theme.getItems().setAll(Theme.values());
        theme.setConverter(converter(Theme::displayName));
        theme.valueProperty().bindBidirectional(viewModel.themeProperty());
        theme.setMaxWidth(Double.MAX_VALUE);

        CheckBox clock24 = new CheckBox(Messages.get("settings.clock24"));
        clock24.selectedProperty().bindBidirectional(viewModel.use24HourClockProperty());

        CheckBox hijri = new CheckBox(Messages.get("settings.showHijri"));
        hijri.selectedProperty().bindBidirectional(viewModel.showHijriDateProperty());

        ComboBox<Language> language = new ComboBox<>();
        language.getItems().setAll(Language.values());
        language.setConverter(converter(Language::toString));
        language.valueProperty().bindBidirectional(viewModel.languageProperty());
        language.setMaxWidth(Double.MAX_VALUE);
        language.setTooltip(new Tooltip(Messages.get("settings.tooltip.language")));

        CheckBox localNumerals = new CheckBox(Messages.get("settings.localNumerals"));
        localNumerals.selectedProperty().bindBidirectional(viewModel.useLocalNumeralsProperty());
        // Only meaningful for languages written with their own digits.
        localNumerals.disableProperty().bind(Bindings.createBooleanBinding(
                () -> viewModel.languageProperty().get() == null
                        || !viewModel.languageProperty().get().hasOwnNumerals(),
                viewModel.languageProperty()));

        GridPane grid = grid();
        addRow(grid, 0, Messages.get("settings.language"), language);
        addRow(grid, 1, Messages.get("settings.theme"), theme);

        card.add(grid, localNumerals, clock24, hijri);
        return card;
    }

    private Region buildStartupCard() {
        Card card = new Card(Messages.get("settings.startup")).growHorizontally();

        CheckBox startOnLogin = new CheckBox(Messages.get("settings.startOnLogin"));
        startOnLogin.setTooltip(new Tooltip(Messages.get("settings.tooltip.startOnLogin")));
        startOnLogin.selectedProperty().bindBidirectional(viewModel.startOnLoginProperty());

        CheckBox minimised = new CheckBox(Messages.get("settings.startMinimised"));
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
