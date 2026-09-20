package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.CaptureModel;
import com.botmaker.sdk.authoring.CaptureTargetModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.sdk.internal.plugin.launch.QuickLaunch;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Managing the project's capture targets — the monitors, windows and emulator instances a bot may be pointed
 * at — and marking one of them the <b>default</b> every on-screen picker then uses.
 *
 * <h2>Why this is the plugin's and not the host's</h2>
 *
 * <p>A capture target is {@code CaptureSource}'s concept, the list of them is {@link CaptureModel}, and the
 * file it lives in is {@code capture.json} — all three the SDK's. The editor holds no copy of any of it and
 * writes none of it: it opens this from the toolbar and that is the whole of its involvement. What the host
 * supplies is the three things nobody else can — which project is open, the current look, and the window this
 * modal should be owned by.
 *
 * <h2>Three writes, one direction</h2>
 *
 * <p>Applying writes {@code capture.json}, projects the default onto {@code botmaker-project.properties}'
 * {@code capture.source} through {@link Authoring#writeCaptureSource}, and since 2026-09-21 writes the same
 * default into the bot's own Java as the expression {@code Sdk.captureSource()} returns
 * ({@link CaptureValue}). None of the three is a second answer: they are one target, written by one code
 * path at one instant, to the three places that have to know — this window, the host's tooling, and the bot.
 *
 * <p>The first two happen off the FX thread, because they are a load-modify-store of files on disk. The
 * third is a value the host writes, so it happens on the FX thread once they have landed.
 */
public final class CaptureTargets {

    /** The one open instance, so pressing the toolbar button twice focuses rather than stacks. */
    private static CaptureTargets active;

    private final StudioServices services;
    private final Window owner;

    private final ObservableList<CaptureTargetModel> rows = FXCollections.observableArrayList();
    private final Label statusLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();

    /** The row currently marked default, or {@code null} for none. */
    private CaptureTargetModel defaultTarget;
    private ListView<CaptureTargetModel> list;
    private Stage stage;

    /** The reference size read in with the targets, carried through a write untouched. */
    private CaptureModel.Resolution reference;

    /** Run once the window is gone, for a caller that opened this to fix something and must look again. */
    private Runnable onClosed;

    /** Cached probes per target, so cell recycling does not re-grab. */
    private final Map<CaptureTargetModel, ThumbEntry> thumbs = new HashMap<>();

    /** One background thread for the blocking native and ADB grabs. */
    private final ExecutorService thumbExec = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "target-thumb");
        thread.setDaemon(true);
        return thread;
    });

    /** A cached probe: the preview image (may be {@code null}) and whether the target exists right now. */
    private record ThumbEntry(Image image, boolean exists) {
    }

    private CaptureTargets(StudioServices services, Window owner) {
        this.services = services;
        this.owner = owner;
    }

    /**
     * Opens the manager, or focuses the one already open.
     *
     * <p>Single-instance for the same reason the capture overlay is: the toolbar button is the only way in, so
     * a second press means "show me the one I opened", never "open another".
     */
    public static void open(StudioServices services, Window owner) {
        open(services, owner, null);
    }

    /**
     * As {@link #open(StudioServices, Window)}, additionally running {@code onClosed} on the FX thread once
     * the window is gone.
     *
     * <p>The stage is modal but showing it does not block, so a caller that opened this to fix something —
     * a runner window listing the target the bot will watch — has no other way to know when to look again.
     */
    public static void open(StudioServices services, Window owner, Runnable onClosed) {
        if (active != null && active.stage != null && active.stage.isShowing()) {
            active.stage.toFront();
            active.stage.requestFocus();
            return;
        }
        active = new CaptureTargets(services, owner);
        active.onClosed = onClosed;
        active.show();
    }

    /**
     * Makes {@code target} the project's default, with no window shown — the write this dialog's Apply does,
     * for a caller that already knows the answer.
     *
     * <p>The one such caller is the overlay editor's row: the host says which window its HUD is drawn over,
     * which is a fact only the host has, and pointing the bot at that window is this plugin's own sentence to
     * write. It is <b>the same write path</b> as Apply, deliberately — {@code capture.json} through
     * {@link Authoring#writeCapture} plus the projection onto {@code capture.source} through
     * {@link Authoring#writeCaptureSource} — because two writers of one file disagree on the first key either
     * of them forgets.
     *
     * <p>A target already in the list is promoted rather than added again, so pressing the button twice over
     * one window does not grow the list. The reference size and every other target are read in and written
     * back untouched, for the reason Apply carries the reference: this is not the window that owns them.
     *
     * <p>Off the FX thread, because it is a read-modify-store of two files on disk. {@code onDone} is called
     * on the FX thread with {@code null} when it is written, or with the failure to report.
     */
    public static void pointDefaultAt(StudioServices services, CaptureTargetModel target,
                                      Consumer<String> onDone) {
        Path resources = services == null ? null : services.resourcesDir();
        if (resources == null || target == null) {
            if (onDone != null) onDone.accept("No project is open.");
            return;
        }
        Thread worker = new Thread(() -> {
            String failure = null;
            try {
                CaptureModel current = Authoring.readCapture(SdkVersion.latest(), resources);
                // By spec, not by equals: a row the user typed carries their own label, and the same window
                // listed twice under two labels is one target.
                int existing = -1;
                for (int i = 0; i < current.targets().size(); i++) {
                    if (current.targets().get(i).spec().equals(target.spec())) existing = i;
                }
                CaptureModel next = existing >= 0
                        ? current.withDefaultIndex(existing)
                        : current.withTarget(target).withDefaultIndex(current.targets().size());
                Authoring.writeCapture(SdkVersion.latest(), resources, next);
                Authoring.writeCaptureSource(SdkVersion.latest(), resources, target.spec());
            } catch (Exception ex) {
                failure = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            String message = failure;
            Platform.runLater(() -> {
                // On the FX thread and after the two files, because it is a value the host writes into the
                // bot's own Java. Nothing here fails: a project with no Sdk.java has nowhere to write it.
                if (message == null) CaptureValue.point(services, target);
                if (onDone != null) onDone.accept(message);
            });
        }, "capture-target-point");
        worker.setDaemon(true);
        worker.start();
    }

    private void show() {
        CaptureModel model = read();
        rows.setAll(model.targets());
        defaultTarget = model.defaultIndex() == null ? null : model.targets().get(model.defaultIndex());
        reference = model.reference();

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.getChildren().addAll(buildList(), buildAddRow(), buildButtonBar());

        stage = new Stage();
        stage.setTitle("Capture Targets");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setScene(services.theme().scene(root, 560, 520));
        stage.setMinWidth(460);
        stage.setMinHeight(360);
        stage.setOnHidden(e -> {
            thumbExec.shutdownNow();
            active = null;
            if (onClosed != null) onClosed.run();
        });
        stage.show();
    }

    /** The project's stored capture model, or an empty one when it cannot be read. */
    private CaptureModel read() {
        try {
            return Authoring.readCapture(SdkVersion.latest(), services.resourcesDir());
        } catch (Exception unreadable) {
            System.err.println("Could not read the project's capture targets: " + unreadable.getMessage());
            return CaptureModel.empty();
        }
    }

    private VBox buildList() {
        list = new ListView<>(rows);
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        list.setPlaceholder(new Label("No capture targets yet. Add a screen or window below."));
        list.setCellFactory(v -> new TargetCell());
        // Double-clicking a row makes it the default, which is what "Set as default" does the slow way.
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                CaptureTargetModel selected = list.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    defaultTarget = selected;
                    list.refresh();
                }
            }
        });

        Button setDefault = new Button("Set as default");
        setDefault.setDisable(true);
        Button remove = new Button("Remove");
        remove.setDisable(true);
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            setDefault.setDisable(selected == null);
            remove.setDisable(selected == null);
        });
        setDefault.setOnAction(e -> {
            CaptureTargetModel selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                defaultTarget = selected;
                list.refresh();
            }
        });
        remove.setOnAction(e -> {
            CaptureTargetModel selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                rows.remove(selected);
                if (selected.equals(defaultTarget)) defaultTarget = null;
            }
        });

        HBox buttons = new HBox(8, setDefault, remove);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Label heading = new Label("Capture targets");
        heading.setStyle("-fx-font-weight: bold;");
        Label hint = new Label("The default target is used by all on-screen pickers. A window is brought "
                + "to the front and captured directly.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        VBox box = new VBox(6, heading, list, hint, buttons);
        VBox.setVgrow(list, Priority.ALWAYS);
        return box;
    }

    /**
     * Renders a target as a live thumbnail, its label, an "available / not found" badge and a default marker.
     * The blocking probe runs off the FX thread and is cached per target, so scrolling does not re-probe.
     */
    private final class TargetCell extends ListCell<CaptureTargetModel> {
        private final ImageView thumb = new ImageView();
        private final Label name = new Label();
        private final Label status = new Label();
        private final HBox box;

        TargetCell() {
            thumb.setFitWidth(128);
            thumb.setFitHeight(76);
            thumb.setPreserveRatio(true);
            name.setStyle("-fx-font-weight: bold;");
            status.setStyle("-fx-font-size: 11px;");
            Region pad = new Region();
            pad.setMinSize(128, 76);
            pad.setStyle("-fx-background-color: rgba(0,0,0,0.08); -fx-background-radius: 4;");
            box = new HBox(10, new StackPane(pad, thumb), new VBox(3, name, status));
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(CaptureTargetModel item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(null);
            setGraphic(box);
            name.setText(item.equals(defaultTarget) ? item.label() + "   ✓ default" : item.label());
            renderThumb(item);
        }

        private void renderThumb(CaptureTargetModel item) {
            ThumbEntry cached = thumbs.get(item);
            if (cached != null) {
                thumb.setImage(cached.image());
                status.setText(cached.exists() ? "● available" : "○ not found");
                status.setTextFill(Color.web(cached.exists() ? "#2e7d32" : "#b00020"));
                return;
            }
            thumb.setImage(null);
            status.setText("probing…");
            status.setTextFill(Color.GRAY);
            thumbs.put(item, new ThumbEntry(null, false));   // sentinel, so the same row submits once
            thumbExec.submit(() -> {
                TargetThumbnail.Result result = TargetThumbnail.grab(item);
                Image image = result.image() == null ? null : ScreenCapture.toFxImage(result.image());
                Platform.runLater(() -> {
                    thumbs.put(item, new ThumbEntry(image, result.exists()));
                    if (stage != null && stage.isShowing()) list.refresh();
                });
            });
        }
    }

    /** One visual "add": the source picker, whose concrete choice becomes a row and the new default. */
    private HBox buildAddRow() {
        Button choose = new Button("＋ Add capture source…");
        choose.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(choose, Priority.ALWAYS);
        choose.setOnAction(e -> new SourcePicker(services, stage, false).showAndWait().ifPresent(selection -> {
            if (selection instanceof SourcePicker.Selection.Concrete concrete) addTarget(concrete.target());
        }));

        HBox row = new HBox(8, choose);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void addTarget(CaptureTargetModel target) {
        if (!rows.contains(target)) rows.add(target);
        defaultTarget = target;     // a newly added source is the one the user just went looking for
        statusLabel.setText("");
        list.refresh();
        list.getSelectionModel().select(target);
    }

    private HBox buildButtonBar() {
        progress.setVisible(false);
        progress.setPrefSize(20, 20);

        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> stage.close());
        Button apply = new Button("Apply");
        apply.setDefaultButton(true);
        apply.setOnAction(e -> apply(apply, cancel));

        // This dialog is where the launch button earns its keep most directly: a game's window cannot be
        // picked as a capture source until the game is up.
        Button launchNow = QuickLaunch.button(services.resourcesDir(), this::report);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, progress, statusLabel, launchNow, spacer, cancel, apply);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /**
     * Writes the list, the default and the projection, off the FX thread.
     *
     * <p>The reference size read in at open is written back unchanged: it shares the file but not this dialog,
     * and dropping it would silently un-set the size every project's overlay is drawn at.
     */
    private void apply(Button apply, Button cancel) {
        List<CaptureTargetModel> result = new ArrayList<>(rows);
        int index = defaultTarget == null ? -1 : result.indexOf(defaultTarget);
        CaptureModel model = new CaptureModel(result, index < 0 ? null : index, reference);
        Path resources = services.resourcesDir();

        setBusy(apply, cancel, true);
        Thread worker = new Thread(() -> {
            String failure = null;
            try {
                Authoring.writeCapture(SdkVersion.latest(), resources, model);
                Authoring.writeCaptureSource(SdkVersion.latest(), resources,
                        defaultTarget == null ? null : defaultTarget.spec());
            } catch (Exception ex) {
                failure = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            String message = failure;
            Platform.runLater(() -> {
                setBusy(apply, cancel, false);
                if (message != null) {
                    error(message);
                    return;
                }
                CaptureValue.point(services, defaultTarget);
                stage.close();
            });
        }, "capture-targets-save");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(Button apply, Button cancel, boolean busy) {
        progress.setVisible(busy);
        apply.setDisable(busy);
        cancel.setDisable(busy);
    }

    /** A launch outcome, on the same status line an Apply failure uses. */
    private void report(boolean ok, String message) {
        if (!ok) {
            error(message);
            return;
        }
        statusLabel.setStyle("-fx-text-fill: #2e7d32;");
        statusLabel.setText(message);
    }

    private void error(String message) {
        statusLabel.setStyle("-fx-text-fill: #b00020;");
        statusLabel.setText(message);
    }
}
