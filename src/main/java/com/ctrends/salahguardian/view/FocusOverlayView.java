package com.ctrends.salahguardian.view;

import com.ctrends.salahguardian.config.ConfigService;
import com.ctrends.salahguardian.model.PrayerTime;
import com.ctrends.salahguardian.prayer.PrayerScheduleService;
import com.ctrends.salahguardian.utils.DesktopEnvironment;
import com.ctrends.salahguardian.view.components.IslamicPattern;
import com.ctrends.salahguardian.viewmodel.FocusOverlayViewModel;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * The fullscreen prayer focus overlay.
 *
 * <p>Rather than locking the screen - which would need a privileged helper and
 * would behave differently on every desktop - the overlay is an undecorated,
 * always-on-top, fullscreen window over a dark translucent backdrop. It is a
 * firm reminder that the user can still dismiss deliberately, which is both
 * safer and more portable.</p>
 *
 * <h2>How the "cannot be dismissed accidentally" requirement is met</h2>
 * <ul>
 *   <li><b>Escape</b> - JavaFX exits fullscreen on Escape by default; that is
 *       disabled with {@link Stage#setFullScreenExitKeyCombination} plus a key
 *       filter that consumes the event outright.</li>
 *   <li><b>Alt+F4</b> - the window manager turns this into a close request,
 *       which {@link Stage#setOnCloseRequest} consumes.</li>
 *   <li><b>Losing focus</b> - a focus listener re-asserts always-on-top and
 *       pulls the window back to the front.</li>
 *   <li>Only the Skip and Close buttons, or the countdown expiring, actually
 *       close it.</li>
 * </ul>
 *
 * <h2>Wayland</h2>
 * A Wayland compositor may refuse programmatic always-on-top. The overlay still
 * opens fullscreen and still counts down; the difference is logged once so the
 * behaviour is not mistaken for a bug.
 *
 * @author CTrends Software
 */
public class FocusOverlayView {

    private static final Logger LOG = LoggerFactory.getLogger(FocusOverlayView.class);

    private final FocusOverlayViewModel viewModel;
    private final Stage stage;
    private Consumer<Boolean> onClosed = skipped -> { };
    private boolean closing;

    /**
     * @param configService   supplies the overlay duration and clock format
     * @param scheduleService supplies the current date and time
     * @param owner           the dashboard window, may be {@code null}
     */
    public FocusOverlayView(ConfigService configService,
                            PrayerScheduleService scheduleService,
                            Window owner) {
        this.viewModel = new FocusOverlayViewModel(configService, scheduleService);
        this.stage = buildStage(owner);
    }

    /**
     * Shows the overlay for a prayer and begins the countdown.
     *
     * @param prayer the prayer being announced
     * @param friday whether today is a Friday
     */
    public void show(PrayerTime prayer, boolean friday) {
        closing = false;
        viewModel.begin(prayer, friday, () -> close(false));
        stage.show();
        stage.setFullScreen(true);
        assertOnTop();
        animateIn();
        LOG.info("Focus overlay displayed for {}", prayer.name().displayName(friday));
    }

    /**
     * @param handler receives {@code true} when the user pressed Skip,
     *                {@code false} when the overlay closed for any other reason
     */
    public void setOnClosed(Consumer<Boolean> handler) {
        this.onClosed = handler == null ? skipped -> { } : handler;
    }

    /**
     * @return {@code true} while the overlay is on screen
     */
    public boolean isShowing() {
        return stage.isShowing();
    }

    /**
     * Closes the overlay and stops its countdown.
     *
     * @param skipped whether the user dismissed it with the Skip button
     */
    public void close(boolean skipped) {
        if (closing) {
            return;
        }
        closing = true;
        viewModel.stop();
        FadeTransition fade = new FadeTransition(Duration.millis(220), stage.getScene().getRoot());
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setOnFinished(event -> {
            stage.setFullScreen(false);
            stage.hide();
            stage.getScene().getRoot().setOpacity(1.0);
            onClosed.accept(skipped);
        });
        fade.play();
    }

    // ----- window construction ---------------------------------------------

    private Stage buildStage(Window owner) {
        Stage overlay = new Stage();
        overlay.initStyle(StageStyle.TRANSPARENT);
        if (owner != null) {
            overlay.initOwner(owner);
            overlay.initModality(Modality.NONE);
        }
        overlay.setTitle("Salah Guardian - Prayer Focus");
        overlay.setAlwaysOnTop(true);
        overlay.setResizable(false);

        // Escape must not drop out of fullscreen, and there must be no hint
        // telling the user that it would.
        overlay.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        overlay.setFullScreenExitHint("");

        // Alt+F4 and any other window manager close request are ignored.
        overlay.setOnCloseRequest(event -> {
            LOG.debug("Focus overlay close request ignored - use Skip or Close");
            event.consume();
        });

        Scene scene = new Scene(buildContent(), Color.TRANSPARENT);
        var stylesheet = FocusOverlayView.class.getResource("/css/focus-overlay.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        // Consume Escape before any control can act on it.
        scene.addEventFilter(KeyEvent.ANY, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
            }
        });

        overlay.setScene(scene);

        // Some compositors drop always-on-top when another window is raised;
        // re-assert it whenever we lose focus.
        overlay.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused && overlay.isShowing() && !closing) {
                Platform.runLater(this::assertOnTop);
            }
        });
        return overlay;
    }

    private void assertOnTop() {
        stage.setAlwaysOnTop(true);
        stage.toFront();
        stage.requestFocus();
        if (DesktopEnvironment.isWayland()) {
            LOG.debug("Running on Wayland - the compositor decides whether always-on-top "
                    + "is honoured for the focus overlay");
        }
    }

    private void animateIn() {
        var root = stage.getScene().getRoot();
        FadeTransition fade = new FadeTransition(Duration.millis(280), root);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(320), root);
        scale.setFromX(1.03);
        scale.setFromY(1.03);
        scale.setToX(1.0);
        scale.setToY(1.0);

        fade.play();
        scale.play();
    }

    // ----- scene graph ------------------------------------------------------

    private StackPane buildContent() {
        StackPane root = new StackPane();
        root.getStyleClass().add("focus-root");

        IslamicPattern pattern = new IslamicPattern(Color.web("#4ade80"), 0.10);
        // A Canvas reports no preferred size, so a layout pane would give it
        // zero. Binding to the root keeps the backdrop filling the screen.
        pattern.widthProperty().bind(root.widthProperty());
        pattern.heightProperty().bind(root.heightProperty());
        root.getChildren().add(pattern);

        BorderPane layout = new BorderPane();
        layout.setCenter(buildCentrePiece());
        layout.setBottom(buildFooter());
        layout.setPadding(new Insets(48));
        root.getChildren().add(layout);
        return root;
    }

    private VBox buildCentrePiece() {
        Label ornament = new Label("۞");
        ornament.getStyleClass().add("focus-ornament");

        Label callToPrayer = new Label("It is time for");
        callToPrayer.getStyleClass().add("focus-eyebrow");

        Label prayerName = new Label();
        prayerName.getStyleClass().add("focus-prayer-name");
        prayerName.textProperty().bind(viewModel.prayerNameProperty());

        Label arabicName = new Label();
        arabicName.getStyleClass().add("focus-prayer-arabic");
        arabicName.textProperty().bind(viewModel.arabicNameProperty());

        Label prayerTime = new Label();
        prayerTime.getStyleClass().add("focus-prayer-time");
        prayerTime.textProperty().bind(
                Bindings.concat("Prayer time ", viewModel.prayerTimeProperty()));

        StackPane countdownRing = buildCountdownRing();

        Label remaining = new Label();
        remaining.getStyleClass().add("focus-remaining-caption");
        remaining.textProperty().bind(
                Bindings.concat("This reminder closes in ", viewModel.countdownProperty()));

        VBox centre = new VBox(10, ornament, callToPrayer, prayerName, arabicName,
                prayerTime, countdownRing, remaining);
        centre.setAlignment(Pos.CENTER);
        centre.getStyleClass().add("focus-centre");
        return centre;
    }

    /**
     * Builds the countdown ring.
     *
     * <p>Drawn from an explicit {@link Arc} rather than a
     * {@code ProgressIndicator}: the stock control renders a filled pie whose
     * appearance depends on the platform theme, whereas a stroked arc gives the
     * thin, predictable ring the design calls for on every desktop.</p>
     *
     * @return the ring with the countdown text centred inside it
     */
    private StackPane buildCountdownRing() {
        final double radius = 104;
        final double thickness = 8;

        Circle track = new Circle(radius);
        track.getStyleClass().add("focus-ring-track");
        track.setFill(Color.TRANSPARENT);
        track.setStrokeWidth(thickness);

        Arc progress = new Arc(0, 0, radius, radius, 90, 0);
        progress.getStyleClass().add("focus-ring-progress");
        progress.setType(ArcType.OPEN);
        progress.setFill(Color.TRANSPARENT);
        progress.setStrokeWidth(thickness);
        progress.setStrokeLineCap(StrokeLineCap.ROUND);

        // The arc sweeps clockwise from twelve o'clock as the countdown runs
        // down, so a negative length is what fills the ring.
        viewModel.progressProperty().addListener((obs, was, value) ->
                progress.setLength(-360.0 * Math.max(0, Math.min(1, value.doubleValue()))));

        Label countdown = new Label();
        countdown.getStyleClass().add("focus-countdown");
        countdown.textProperty().bind(viewModel.countdownProperty());

        StackPane ringStack = new StackPane(track, progress, countdown);
        ringStack.setMinSize(2 * radius + thickness, 2 * radius + thickness);
        ringStack.setPrefSize(2 * radius + thickness, 2 * radius + thickness);
        ringStack.setMaxSize(2 * radius + thickness, 2 * radius + thickness);
        ringStack.setPadding(new Insets(8, 0, 8, 0));
        return ringStack;
    }

    private HBox buildFooter() {
        Label date = new Label();
        date.getStyleClass().add("focus-date");
        date.textProperty().bind(viewModel.gregorianDateProperty());

        Label hijri = new Label();
        hijri.getStyleClass().add("focus-hijri");
        hijri.textProperty().bind(viewModel.hijriDateProperty());

        Label clock = new Label();
        clock.getStyleClass().add("focus-clock");
        clock.textProperty().bind(viewModel.currentTimeProperty());

        VBox dates = new VBox(2, date, hijri);
        dates.setAlignment(Pos.CENTER_LEFT);

        Button skip = new Button("Skip");
        skip.getStyleClass().add("focus-button-ghost");
        skip.setOnAction(event -> {
            LOG.info("Focus overlay skipped by the user");
            close(true);
        });

        Button dismiss = new Button("Close");
        dismiss.getStyleClass().add("focus-button-primary");
        dismiss.setDefaultButton(true);
        dismiss.setOnAction(event -> {
            LOG.info("Focus overlay closed by the user");
            close(false);
        });

        HBox buttons = new HBox(12, skip, dismiss);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox footer = new HBox(18, dates, spacer, clock, buttons);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("focus-footer");
        return footer;
    }
}
