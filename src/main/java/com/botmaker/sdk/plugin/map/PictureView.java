package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.PictureEdits;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateUses;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One window beside 🗺 Screens showing an input's picture at its own size: a row's thumbnail is too small to tell a
 * picture by (the user's call, 2026-10-15). Each show replaces what it showed, in the same window, rather than opening
 * another. A screen's shot stays in the side panel (the user's call, 2026-10-15).
 */
final class PictureView {

    /** The most a picture is drawn at; a bigger one is shrunk to fit. */
    private static final double MOST_WIDTH = 720;
    private static final double MOST_HEIGHT = 420;
    /** The most the wheel grows a picture: each of its pixels as a square this many screen pixels wide. */
    static final double MOST_ZOOM = 32;
    /** The widest a zoomed picture is drawn, in screen pixels. */
    private static final double MOST_SIDE = 8192;
    /** How wide a screen's shot opens, and how far the wheel takes it each way, a step at a time. */
    private static final double SCREEN_WIDTH = 880;
    private static final double SCREEN_LEAST = 320;
    private static final double SCREEN_MOST = 3840;
    private static final double SCREEN_STEP = 1.2;
    /** A wheel's notch, in scroll pixels: a touchpad's many small scrolls add up to a step, not a step each. */
    private static final double NOTCH = 40;

    private final StudioServices services;
    private final Supplier<Window> owner;
    private final BorderPane body = new BorderPane();
    private Stage stage;

    PictureView(StudioServices services, Supplier<Window> owner) {
        this.services = services;
        this.owner = owner;
        body.getStyleClass().addAll("screen-map", "screen-picture-view");
        body.getStylesheets().add(ScreenCanvas.SHEET);
    }

    /**
     * The way back from a picture to the screen it was opened from: {@code screen} names it on the button, and
     * {@code show} shows its big shot, false when it has none.
     */
    record Back(String screen, BooleanSupplier show) {}

    /**
     * Shows {@code row}'s picture; {@code back} is the "← screen" button, null for none; {@code tryIt} is ▶ Try, null
     * when the row is tried; {@code renamed} runs after a rename wrote the bot.
     */
    void showPicture(Row row, Back back, Runnable tryIt, Runnable renamed) {
        String name = row.picture();
        if (name == null || services.resourcesDir() == null) return;
        Path file = TemplateLibrary.fileForName(services.resourcesDir(), name);

        Node picture;
        if (Files.isRegularFile(file)) {
            picture = zoomable(new Image(file.toUri().toString()));
        } else {
            StackPane none = new StackPane(new Label("No file for " + name + "."));
            none.setPadding(new Insets(12));
            picture = none;
        }

        String constant = TemplateNames.constantFor(name);
        Label said = text((constant == null ? "" : "Pictures." + constant + "\n") + where(name) + row.label() + ": "
                + row.result() + (row.kind().isEmpty() ? "" : " (" + row.kind() + ")")
                + "\nThe wheel zooms it, a drag moves it.");

        Label status = new Label();
        status.setWrapText(true);
        status.getStyleClass().add("screen-panel-status");
        TextField to = new TextField(name);
        to.setPrefColumnCount(18);
        Button rename = new Button("Rename");
        rename.setOnAction(e -> {
            PictureEdits.Done done = PictureEdits.rename(services, name, to.getText());
            status.setText(done.said());
            if (done.ok()) {
                rename.setDisable(true);
                renamed.run();
            }
        });
        HBox actions = new HBox(8, to, rename);
        actions.setAlignment(Pos.CENTER_LEFT);
        if (tryIt != null) {
            Button play = new Button("▶ Try");
            play.getStyleClass().add("screen-panel-primary");
            play.setOnAction(e -> {
                tryIt.run();
                play.setDisable(true);
            });
            actions.getChildren().addFirst(play);
        }
        VBox side = new VBox(10, title(name), said, actions, status);
        if (back != null) side.getChildren().addFirst(backButton(back, status));
        show(name, picture, side);
    }

    /** The "← screen" button: {@code back}'s shot shown again, else {@code status} says it has none. */
    private static Button backButton(Back back, Label status) {
        Button screen = new Button("← " + back.screen());
        screen.setTooltip(new Tooltip("Show " + back.screen() + "'s shot again"));
        screen.setOnAction(e -> {
            if (!back.show().getAsBoolean()) status.setText(back.screen() + " has no shot yet.");
        });
        return screen;
    }

    /**
     * {@code image} at whole pixels, never blurred, as it opens: shrunk to fit when big, a small one grown so a
     * 30-pixel icon is still readable. The wheel zooms it a step at a time and a drag moves it.
     */
    private static Node zoomable(Image image) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(false);    // a picture is matched pixel for pixel: shown as it is, not blurred
        double width = Math.max(1, image.getWidth());
        double height = Math.max(1, image.getHeight());
        double fit = Math.min(1, Math.min(MOST_WIDTH / width, MOST_HEIGHT / height));
        // Never past what a texture can hold: a big shot stops growing sooner than an icon.
        double most = Math.max(1, Math.min(MOST_ZOOM, Math.floor(MOST_SIDE / Math.max(width, height))));
        double[] zoom = {fit < 1 ? fit : Math.max(1, Math.floor(Math.min(240 / width, 160 / height)))};
        Runnable size = () -> {
            view.setFitWidth(width * zoom[0]);
            view.setFitHeight(height * zoom[0]);
        };
        size.run();
        StackPane holder = new StackPane(view);
        holder.getStyleClass().add("screen-card-shot");
        holder.setPadding(new Insets(12));
        ScrollPane scroller = new ScrollPane(holder);
        scroller.setPannable(true);
        // Centred while it is smaller than the window; scrolled once it is bigger.
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);
        scroller.getStyleClass().add("screen-panel-scroller");
        double[] turned = {0};
        scroller.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() == 0) return;
            e.consume();
            turned[0] += e.getDeltaY();
            if (Math.abs(turned[0]) < NOTCH) return;
            zoom[0] = zoomStep(zoom[0], turned[0] > 0, fit, most);
            turned[0] = 0;
            size.run();
        });
        return scroller;
    }

    /**
     * The zoom one wheel notch takes a picture to from {@code zoom}: up, the next whole multiple (below 1, twice as
     * big, up to 1), never past {@code most}; down, the one before, then half as big, never under {@code least}. A whole
     * multiple draws each of
     * the picture's pixels as a square of the screen's, so none is blurred or dropped.
     */
    static double zoomStep(double zoom, boolean in, double least, double most) {
        if (in) return zoom < 1 ? Math.min(1, zoom * 2) : Math.max(zoom, Math.min(most, Math.floor(zoom) + 1));
        return zoom > 1 ? Math.max(1, Math.ceil(zoom) - 1) : Math.max(least, zoom / 2);
    }

    /**
     * A screen's shot, big: {@code shotAt} draws it at a width with its inputs boxed, again at each zoom, so the boxes
     * stay on their buttons. The wheel zooms it, Ctrl or not. {@code back} is the "← screen" button to the shot it
     * was opened from, null for none. False when there is no shot to draw.
     */
    boolean showScreen(String name, Back back, Function<Double, Optional<Node>> shotAt) {
        double[] width = {SCREEN_WIDTH};
        Optional<Node> first = shotAt.apply(width[0]);
        if (first.isEmpty()) return false;
        StackPane holder = new StackPane(first.get());
        holder.setPadding(new Insets(12));
        ScrollPane scroller = new ScrollPane(holder);
        scroller.setPannable(true);
        scroller.getStyleClass().add("screen-panel-scroller");
        scroller.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() == 0) return;
            e.consume();
            double next = Math.clamp(width[0] * Math.pow(SCREEN_STEP, e.getDeltaY() / NOTCH), SCREEN_LEAST,
                    SCREEN_MOST);
            if (Math.abs(next - width[0]) < 1) return;
            width[0] = next;
            shotAt.apply(next).ifPresent(shot -> holder.getChildren().setAll(shot));
        });
        VBox side = new VBox(6, title(name), text("The wheel zooms, a drag moves it; click a box for what that "
                + "input opens."));
        if (back != null) {
            Label status = new Label();
            status.getStyleClass().add("screen-panel-status");
            side.getChildren().addFirst(backButton(back, status));
            side.getChildren().add(status);
        }
        show(name, scroller, side);
        return true;
    }

    /** Closes the window; what the map's window does when it closes. */
    void close() {
        if (stage != null) stage.close();
    }

    private void show(String title, Node centre, VBox side) {
        side.setPadding(new Insets(14));
        body.setCenter(centre);
        body.setBottom(side);
        if (stage == null) {
            stage = Modals.window(services, owner.get(), Modals.Frame.modeless("Picture", 920, 700, 380, 300), body);
            stage.setOnHidden(e -> {
                // The body is shown again in the next window: a node is the root of one scene at a time.
                stage.getScene().setRoot(new Pane());
                stage = null;
            });
        }
        stage.setTitle(title);
        stage.show();
        stage.toFront();
    }

    private static Label title(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("screen-panel-name");
        return title;
    }

    private static Label text(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("screen-panel-path");
        label.setWrapText(true);
        return label;
    }

    /** Where {@code name} was cut, as its constant says, on a line; empty when it does not say. */
    private String where(String name) {
        FrameCut cut = services.pluginValues() == null ? null : TemplateUses.cuts(services.pluginValues()).get(name);
        if (cut == null || cut.place() == null) return "";
        Rect r = cut.place();
        return "Cut at " + r.x() + "," + r.y() + " (" + r.width() + "×" + r.height() + ")"
                + (cut.frame() == null ? "" : " of a " + cut.frame().width() + "×" + cut.frame().height() + " screen")
                + ".\n";
    }
}
