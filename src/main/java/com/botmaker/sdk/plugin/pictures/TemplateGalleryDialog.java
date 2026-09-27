package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
 * {@link TemplateGallery} as a modal picker — what a picture slot, a picture row and the Resource Manager's own
 * pickers open: the tag rail, the search and the large tiles, without the manager's rename and delete.
 *
 * <p>"Capture new…" is an action <em>inside</em> the gallery rather than an entry in a menu in front of it,
 * which is what lets the picker show pictures: a menu that also has to offer that has to be a menu, and a menu
 * cannot show a hundred thumbnails. It hides this window while Capture Templates has the screen, then brings
 * it back with the pictures just saved <b>selected</b> — the last one when one is picked, all of them when
 * several are — and still waits for the user to confirm.
 *
 * <p>The result arrives through {@code onChosen} rather than as a return value: hiding a stage inside
 * {@code showAndWait} would end the wait, and the capture comes back long after it. Handing back a list keeps
 * the single- and multi-select callers on one path — a single-select dialog simply never yields more than one.
 *
 * <p>Everything it shows is this plugin's picture folder, read through this plugin's {@link TemplateLibrary}.
 * What it takes from the host is a theme and an owner window, which is all the contract offers and all it needs.
 */
public final class TemplateGalleryDialog {

    private TemplateGalleryDialog() {}

    /**
     * @param title       the window title — say what the pick is for ("Choose a picture")
     * @param multiSelect whether several pictures may come back at once
     * @param filter      which pictures to offer, or {@code null} for the whole library
     * @param capture     whether to offer Capture new…
     * @param note        a sentence shown above the gallery saying why it is narrowed, or {@code null}
     */
    public record Options(String title, boolean multiSelect, Predicate<Path> filter, boolean capture,
                          String note) {

        public static Options pickOne(String title) {
            return new Options(title, false, null, false, null);
        }

        public Options withFilter(Predicate<Path> filter) {
            return new Options(title, multiSelect, filter, capture, note);
        }

        public Options multi() {
            return new Options(title, true, filter, capture, note);
        }

        public Options withCapture() {
            return new Options(title, multiSelect, filter, true, note);
        }

        public Options withNote(String note) {
            return new Options(title, multiSelect, filter, capture, note);
        }
    }

    /** Opens the gallery; {@code onChosen} gets the picked pictures, and is not called if nothing is picked. */
    public static void open(StudioServices services, Window owner, Options options,
                            Consumer<List<Path>> onChosen) {
        Path resources = services.resourcesDir();
        if (resources == null) {
            services.status("Open a project to choose a picture.");
            return;
        }
        Stage stage = new Stage();
        stage.setTitle(options.title());
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);

        TemplateGallery gallery = new TemplateGallery(resources, options.multiSelect());
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

        if (options.capture()) {
            Button capture = new Button("Capture new…");
            capture.setOnAction(e -> {
                stage.hide();  // the capture overlay needs the screen, and this window is on it
                CaptureTemplates.open(services, owner, null, gallery.selectedRealTag(), saved -> {
                    stage.show();
                    gallery.reload();
                    gallery.setSelection(savedFiles(resources, saved, options));
                });
            });
            buttons.getChildren().add(capture);
        }
        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        buttons.getChildren().addAll(spacer, choose, cancel);

        VBox root = new VBox(12, gallery, buttons);
        if (options.note() != null) {
            Label note = new Label(options.note());
            note.setWrapText(true);
            root.getChildren().add(0, note);
        }
        VBox.setVgrow(gallery, Priority.ALWAYS);
        root.setPadding(new Insets(16));

        stage.setScene(services.theme().scene(root, 820, 580));
        stage.setMinWidth(560);
        stage.setMinHeight(420);
        stage.show();
    }

    /** The files a capture saved that this dialog should select, per {@link ChooserSelection#afterCapture}. */
    private static List<Path> savedFiles(Path resources, List<String> saved, Options options) {
        Predicate<Path> offered = options.filter() == null ? file -> true : options.filter();
        List<Path> library = TemplateLibrary.list(resources);
        List<String> usable = saved.stream()
                .filter(name -> library.stream().anyMatch(f -> TemplateLibrary.baseName(f).equals(name)
                                                              && offered.test(f)))
                .toList();
        List<String> wanted = ChooserSelection.afterCapture(usable, options.multiSelect(), null);
        return wanted.stream()
                .flatMap(name -> library.stream().filter(f -> TemplateLibrary.baseName(f).equals(name)))
                .toList();
    }
}
