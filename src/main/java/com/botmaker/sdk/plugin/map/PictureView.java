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
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;
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
     * Shows {@code row}'s picture; {@code tryIt} is ▶ Try, null when the row is tried; {@code renamed} runs after a
     * rename wrote the bot.
     */
    void showPicture(Row row, Runnable tryIt, Runnable renamed) {
        String name = row.picture();
        if (name == null || services.resourcesDir() == null) return;
        Path file = TemplateLibrary.fileForName(services.resourcesDir(), name);

        StackPane picture = new StackPane();
        picture.getStyleClass().add("screen-card-shot");
        picture.setPadding(new Insets(12));
        if (Files.isRegularFile(file)) {
            ImageView view = new ImageView(new Image(file.toUri().toString()));
            view.setPreserveRatio(true);
            view.setSmooth(false);    // a picture is matched pixel for pixel: shown as it is, not blurred
            Image image = view.getImage();
            double scale = Math.min(1, Math.min(MOST_WIDTH / Math.max(1, image.getWidth()),
                    MOST_HEIGHT / Math.max(1, image.getHeight())));
            // Small pictures shown larger, whole pixels, so a 30-pixel icon is still readable.
            double grow = scale < 1 ? scale : Math.max(1, Math.floor(Math.min(240 / Math.max(1, image.getWidth()),
                    160 / Math.max(1, image.getHeight()))));
            view.setFitWidth(image.getWidth() * grow);
            view.setFitHeight(image.getHeight() * grow);
            picture.getChildren().add(view);
        } else {
            picture.getChildren().add(new Label("No file for " + name + "."));
        }

        String constant = TemplateNames.constantFor(name);
        Label said = text((constant == null ? "" : "Pictures." + constant + "\n") + where(name) + row.label() + ": "
                + row.result() + (row.kind().isEmpty() ? "" : " (" + row.kind() + ")"));

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
        show(name, picture, new VBox(10, title(name), said, actions, status));
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
            stage.setOnHidden(e -> stage = null);
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
