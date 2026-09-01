package com.botmaker.sdk.internal.plugin.templates;

import com.botmaker.plugin.api.StudioServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link TemplateGallery} as a modal picker — what a template slot opens instead of a menu of tag submenus.
 *
 * <p>"Capture new…" is an action <em>inside</em> the gallery rather than an entry in a menu in front of it,
 * which is what lets the picker show pictures: a menu that also has to offer that has to be a menu, and a menu
 * cannot show a hundred thumbnails.
 *
 * <p>The result arrives through {@code onChosen} rather than as a return value, because capture is
 * asynchronous: the dialog closes, the screen overlay takes over, and the saved template comes back long after
 * a {@code showAndWait} would have returned. Handing back a list keeps the single- and multi-select callers on
 * one path — a single-select dialog simply never yields more than one.
 *
 * <p>It was Studio's until 2026-09-01 and it was never Studio's subject: everything it shows is this plugin's
 * picture folder, read through this plugin's {@link com.botmaker.sdk.authoring.TemplateLibrary}. What it takes
 * from the host is a theme and an owner window, which is all the contract offers and all it needs.
 */
public final class TemplateGalleryDialog {

    private TemplateGalleryDialog() {}

    /** How this caller captures a new template, when it offers capture at all. */
    @FunctionalInterface
    public interface CaptureAction {
        /**
         * Runs the capture flow. The gallery is already closed, so the overlay has the screen to itself;
         * {@code onSaved} is called with the new template's file only if one was actually saved.
         */
        void capture(Window owner, Consumer<Path> onSaved);
    }

    /**
     * @param title       the window title — say what the pick is for ("Choose an image")
     * @param multiSelect whether several templates may come back at once
     * @param filter      which templates to offer, or {@code null} for the whole library
     * @param capture     the capture flow, or {@code null} where capturing makes no sense (a narrowed group
     *                    row: a fresh image is by definition not in the group being narrowed to)
     */
    public record Options(String title, boolean multiSelect, Predicate<Path> filter, CaptureAction capture) {

        public static Options pickOne(String title) {
            return new Options(title, false, null, null);
        }

        public Options withFilter(Predicate<Path> filter) {
            return new Options(title, multiSelect, filter, capture);
        }

        public Options multi() {
            return new Options(title, true, filter, capture);
        }

        public Options withCapture(CaptureAction capture) {
            return new Options(title, multiSelect, filter, capture);
        }
    }

    /** Opens the gallery; {@code onChosen} gets the picked templates, and is not called if nothing is picked. */
    public static void open(StudioServices services, Window owner, Options options,
                            Consumer<List<Path>> onChosen) {
        Stage stage = new Stage();
        stage.setTitle(options.title());
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);

        TemplateGallery gallery = new TemplateGallery(services.resourcesDir(), options.multiSelect());
        if (options.filter() != null) gallery.setFilter(options.filter());

        Button choose = new Button(options.multiSelect() ? "Add" : "Choose");
        choose.setDefaultButton(true);
        choose.setDisable(true);
        gallery.setOnSelectionChanged(() -> choose.setDisable(gallery.selectedFiles().isEmpty()));
        choose.setOnAction(e -> {
            List<Path> picked = gallery.selectedFiles();
            stage.close();
            if (!picked.isEmpty()) onChosen.accept(picked);
        });
        // Double-click is the same act as selecting and pressing Choose, and is how anyone picking one image
        // will actually do it.
        gallery.setOnActivate(file -> {
            stage.close();
            onChosen.accept(List.of(file));
        });

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_LEFT);

        if (options.capture() != null) {
            Button capture = new Button("Capture new…");
            capture.setOnAction(e -> {
                stage.close();  // the capture overlay needs the screen, and this window is on it
                options.capture().capture(owner, file -> onChosen.accept(List.of(file)));
            });
            buttons.getChildren().add(capture);
        }
        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        buttons.getChildren().addAll(spacer, choose, cancel);

        VBox root = new VBox(12, gallery, buttons);
        VBox.setVgrow(gallery, Priority.ALWAYS);
        root.setPadding(new Insets(16));

        stage.setScene(services.theme().scene(root, 760, 560));
        stage.setMinWidth(560);
        stage.setMinHeight(420);
        stage.show();
    }
}
