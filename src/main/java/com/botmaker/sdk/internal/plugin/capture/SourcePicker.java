package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.authoring.CaptureTargetModel;
import com.botmaker.session.Preview;
import com.botmaker.shared.capture.GamescopeHost;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstanceScanner;
import com.botmaker.shared.emulator.EmulatorProbe;
import com.botmaker.shared.emulator.Platforms.PlatformStatus;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A library-style visual chooser for one capture source: <b>Desktop</b>, <b>Monitors</b>, <b>Emulators</b>
 * and <b>Windows</b>, each drawn as a tile with a live thumbnail, plus an optional <b>Project default</b>
 * tile that means "whatever the project is pointed at, now and later".
 *
 * <p>It is the plugin's own vocabulary end to end — a {@link CaptureTargetModel} is what a tile stands for,
 * and what a capture source <em>is</em> belongs to the SDK's {@code CaptureSource}, not to the host. The host
 * supplies exactly the three things nobody else can: the current look, the window a modal should be owned by,
 * and the conversion of a grabbed {@link BufferedImage} into something JavaFX can draw.
 *
 * <p>Every grab runs off the FX thread and every one of them is best-effort: a tile with no thumbnail is
 * still a tile the user can pick, because a window that refuses its pixels is still the window they mean.
 */
public final class SourcePicker {

    /** What the user chose: "track the project default", or one concrete, frozen target. */
    public sealed interface Selection permits Selection.ProjectDefault, Selection.Concrete {

        /** Follow whatever the project's default target is at the time the bot runs. */
        record ProjectDefault() implements Selection {
        }

        /**
         * One concrete source, optionally narrowed to {@code region} — a rectangle in the <em>source's own</em>
         * pixel space, where {@code (0,0)} is its top-left, so the narrowing survives the window moving.
         * {@code null} means the whole source.
         */
        record Concrete(CaptureTargetModel target, Rectangle region) implements Selection {
            public Concrete(CaptureTargetModel target) {
                this(target, null);
            }
        }
    }

    private static final double TILE_W = 220;
    private static final double THUMB_H = 124;

    private final StudioServices services;
    private final Window owner;
    private final boolean includeProjectDefault;

    private Selection selected;
    private VBox selectedTile;
    private Stage stage;
    private ExecutorService thumbExec;

    public SourcePicker(StudioServices services, Window owner, boolean includeProjectDefault) {
        this.services = services;
        this.owner = owner;
        this.includeProjectDefault = includeProjectDefault;
    }

    /** Shows the picker modally and returns the chosen source, or empty when it was cancelled. */
    public Optional<Selection> showAndWait() {
        FlowPane windows = category();
        FlowPane monitors = category();
        FlowPane desktop = category();
        FlowPane emulators = category();

        VBox content = new VBox(10);
        content.setPadding(new Insets(14));
        if (includeProjectDefault) content.getChildren().add(projectDefaultTile());
        // Desktop and monitors lead: they are the common picks, and below a hundred-window list nobody
        // scrolls far enough to find them.
        content.getChildren().addAll(
                sectionLabel("Desktop"), desktop,
                sectionLabel("Monitors"), monitors,
                sectionLabel("Emulators"), emulators,
                sectionLabel("Windows"), windows);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);

        // The optional region is a rectangle WITHIN the chosen source, in its own pixels. Left blank it is
        // the whole source, which is what almost every pick means.
        TextField rx = regionField("x");
        TextField ry = regionField("y");
        TextField rw = regionField("w");
        TextField rh = regionField("h");
        Label regionLabel = new Label("Region of source (optional):");
        regionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        HBox regionRow = new HBox(6, regionLabel, rx, ry, rw, rh);
        regionRow.setAlignment(Pos.CENTER_LEFT);

        Button refresh = new Button("↻ Refresh");
        refresh.setOnAction(e -> {
            windows.getChildren().clear();
            loadWindows(windows);
            emulators.getChildren().clear();
            loadEmulators(emulators);
        });
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> {
            selected = null;
            close();
        });
        Button ok = new Button("Select");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> {
            applyRegion(rx, ry, rw, rh);
            close();
        });

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, refresh, spacer, regionRow, cancel, ok);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 12, 14));

        VBox root = new VBox(scroll, bar);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        stage = new Stage();
        stage.setTitle("Choose capture source");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setScene(services.theme().scene(root, 760, 560));
        stage.setMinWidth(560);
        stage.setMinHeight(420);
        stage.setOnHidden(e -> stopThumbs());

        loadWindows(windows);
        loadScreens(monitors);
        loadDesktop(desktop);
        loadEmulators(emulators);

        stage.showAndWait();
        return Optional.ofNullable(selected);
    }

    private void close() {
        stopThumbs();
        if (stage != null) stage.close();
    }

    /** A narrow numeric field for one region coordinate. */
    private static TextField regionField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefColumnCount(3);
        field.setStyle("-fx-font-size: 11px;");
        return field;
    }

    /**
     * Narrows a concrete selection to the four fields when all of them parse to a positive-area rectangle.
     * Blank, partial or invalid input leaves the selection as the whole source; a region on "Project default"
     * is not a thing that can be said, since that choice is not a source yet.
     */
    private void applyRegion(TextField rx, TextField ry, TextField rw, TextField rh) {
        if (!(selected instanceof Selection.Concrete concrete)) return;
        Integer x = parseInt(rx.getText());
        Integer y = parseInt(ry.getText());
        Integer w = parseInt(rw.getText());
        Integer h = parseInt(rh.getText());
        if (x == null || y == null || w == null || h == null || w <= 0 || h <= 0) return;
        selected = new Selection.Concrete(concrete.target(), new Rectangle(x, y, w, h));
    }

    private static Integer parseInt(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 6 0 0 2;");
        return label;
    }

    private static FlowPane category() {
        FlowPane pane = new FlowPane(12, 12);
        pane.setPadding(new Insets(2));
        return pane;
    }

    // --- tiles -------------------------------------------------------------------------------------------

    private VBox projectDefaultTile() {
        VBox tile = tile("Project default", "Tracks the project's default capture target");
        select(tile, new Selection.ProjectDefault());
        tile.setOnMouseClicked(e -> {
            select(tile, new Selection.ProjectDefault());
            if (e.getClickCount() == 2) close();
        });
        return tile;
    }

    /**
     * One tile per monitor, thumbnailed from a <b>single</b> whole-desktop grab cropped per screen.
     *
     * <p>One grab and not one per tile: a per-monitor capture under Wayland goes through the portal, so N
     * monitors would be N confirmation dialogs before the picker had drawn itself.
     */
    private void loadScreens(FlowPane into) {
        List<Screen> screens = Screen.getScreens();
        List<VBox> tiles = new ArrayList<>();
        for (int i = 0; i < screens.size(); i++) {
            Screen screen = screens.get(i);
            Rectangle2D bounds = screen.getBounds();
            String name = String.format("Screen %d — %d×%d", i + 1,
                    (int) bounds.getWidth(), (int) bounds.getHeight());
            VBox tile = tile(name, screen.equals(Screen.getPrimary()) ? "Primary monitor" : "Monitor");
            CaptureTargetModel target = CaptureTargetModel.monitor(i);
            tile.setOnMouseClicked(e -> {
                select(tile, new Selection.Concrete(target));
                if (e.getClickCount() == 2) close();
            });
            into.getChildren().add(tile);
            tiles.add(tile);
        }
        thumbs().submit(() -> {
            // Qualified, and both names are load-bearing: shared's ScreenCapture grabs pixels, this
            // package's converts one to a JavaFX Image. An import of either shadows the other.
            BufferedImage desktop = com.botmaker.shared.capture.ScreenCapture.captureDesktop();
            if (desktop == null) return;
            for (int i = 0; i < tiles.size() && i < screens.size(); i++) {
                BufferedImage shot = EditorFrame.cropped(desktop, toAwt(screens.get(i).getBounds()));
                VBox tile = tiles.get(i);
                show(tile, shot);
            }
        });
    }

    /** One "Whole desktop" tile — every monitor combined, which is what a bot with no target sees. */
    private void loadDesktop(FlowPane into) {
        VBox tile = tile("Whole desktop", "All monitors combined");
        CaptureTargetModel target = CaptureTargetModel.desktop();
        tile.setOnMouseClicked(e -> {
            select(tile, new Selection.Concrete(target));
            if (e.getClickCount() == 2) close();
        });
        // Preselected only when the project-default tile did not claim it — so this is the "add a source"
        // flow's default, where the whole desktop is a better guess than screen 1.
        if (selected == null) select(tile, new Selection.Concrete(target));
        into.getChildren().add(tile);
        thumbs().submit(() -> show(tile, com.botmaker.shared.capture.ScreenCapture.captureDesktop()));
    }

    /**
     * One tile per configured emulator instance across every product, with a live ADB {@code screencap} when
     * the instance is running. A stopped instance still gets a tile: it can be chosen before it is booted,
     * which is exactly what somebody setting a project up is doing.
     */
    private void loadEmulators(FlowPane into) {
        thumbs().submit(() -> {
            EmulatorInstanceScanner.Scan scan;
            try {
                scan = new EmulatorInstanceScanner().scan();
            } catch (Throwable noScan) {
                scan = new EmulatorInstanceScanner.Scan(List.of(), List.of());
            }
            List<EmulatorInstance> instances = scan.instances();
            if (instances.isEmpty()) {
                List<PlatformStatus> statuses = scan.statuses();
                Platform.runLater(() -> into.getChildren().add(emptyEmulatorsHint(statuses)));
                return;
            }
            for (EmulatorInstance instance : instances) {
                String name = instance.name();
                boolean running = EmulatorProbe.isRunning(instance);
                Image image = running ? toFx(emulatorThumbnail(instance)) : null;
                Platform.runLater(() -> {
                    VBox tile = tile(name, running ? "Emulator · running" : "Emulator · stopped");
                    CaptureTargetModel target = CaptureTargetModel.emulator(name);
                    tile.setOnMouseClicked(e -> {
                        select(tile, new Selection.Concrete(target));
                        if (e.getClickCount() == 2) close();
                    });
                    if (image != null) setThumb(tile, image);
                    into.getChildren().add(tile);
                });
            }
        });
    }

    /**
     * One emulator thumbnail: ADB {@code screencap}, falling back to the compositor's output window when that
     * comes back blank.
     *
     * <p>{@code screencap} is not reliable on a GPU-composited container — under Waydroid it returns a fully
     * black frame, which is what once put a black tile beside a perfectly correct "gamescope" one in this same
     * grid. The window grab is lossy (the desktop sizes that window, so the image is scaled and letterboxed),
     * which is fine for a thumbnail and is <em>not</em> how a bot reads the same emulator.
     */
    private static BufferedImage emulatorThumbnail(EmulatorInstance instance) {
        BufferedImage shot = EmulatorProbe.screencap(instance);
        if (shot != null && !Preview.isBlank(shot)) return shot;
        try {
            GenericWindow host = GamescopeHost.firstIn(NativeControllerFactory.get().getAllWindows());
            BufferedImage composited = host == null ? null : NativeControllerFactory.get().captureWindow(host);
            return composited != null ? composited : shot;
        } catch (Throwable noHost) {
            return shot;
        }
    }

    /**
     * Shown when no emulator instance was found: a per-product line, so an absent install reads differently
     * from an installed product with nothing running.
     */
    private static Node emptyEmulatorsHint(List<PlatformStatus> statuses) {
        VBox box = new VBox(2);
        box.setStyle("-fx-padding: 6 2 2 2;");
        box.getChildren().add(hint("No emulator instances found:"));
        for (PlatformStatus status : statuses) box.getChildren().add(hint("• " + status.statusLine()));
        box.getChildren().add(hint("Start an instance with ADB enabled, then press ↻ Refresh."));
        return box;
    }

    private void loadWindows(FlowPane into) {
        thumbs().submit(() -> {
            List<GenericWindow> found;
            try {
                found = NativeControllerFactory.get().getAllWindows();
            } catch (Throwable noWindows) {
                found = List.of();
            }
            long named = found.stream().filter(w -> w.getTitle() != null && !w.getTitle().isBlank()).count();
            if (named == 0) {
                Platform.runLater(() -> into.getChildren().add(emptyWindowsHint()));
                return;
            }
            for (GenericWindow window : found) {
                String title = window.getTitle();
                if (title == null || title.isBlank()) continue;
                // A compositor's output window is not an application: whatever is inside it is already listed
                // under its own name, as the emulator tile or as the session.
                if (GamescopeHost.isHost(window)) continue;
                BufferedImage shot;
                try {
                    shot = NativeControllerFactory.get().captureWindow(window);
                } catch (Throwable notCaptured) {
                    shot = null;
                }
                Image image = toFx(shot);
                Platform.runLater(() -> {
                    VBox tile = tile(title, "Window");
                    CaptureTargetModel target = CaptureTargetModel.window(title);
                    tile.setOnMouseClicked(e -> {
                        select(tile, new Selection.Concrete(target));
                        if (e.getClickCount() == 2) close();
                    });
                    if (image != null) setThumb(tile, image);
                    into.getChildren().add(tile);
                });
            }
        });
    }

    /**
     * Shown when no titled window can be enumerated. On GNOME/Wayland the X11 client list only sees XWayland
     * applications — a native Wayland window is invisible to us — so an empty grid is a legitimate answer
     * rather than a failure.
     */
    private static Node emptyWindowsHint() {
        boolean wayland = System.getenv("WAYLAND_DISPLAY") != null;
        Label label = hint(wayland
                ? "No windows detected. On Wayland only X11/XWayland apps (e.g. many games via Proton) are\n"
                        + "listed; native Wayland windows can't be enumerated. Use a Screen or the project default."
                : "No windows detected. Open the app you want to capture, then press ↻ Refresh.");
        label.setStyle(label.getStyle() + " -fx-padding: 6 2 2 2;");
        return label;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        return label;
    }

    private VBox tile(String name, String subtitle) {
        StackPane holder = new StackPane();
        holder.setMinSize(TILE_W, THUMB_H);
        holder.setPrefSize(TILE_W, THUMB_H);
        holder.setMaxSize(TILE_W, THUMB_H);
        holder.setStyle("-fx-background-color: #101216; -fx-background-radius: 6;");
        Label loading = new Label("…");
        loading.setStyle("-fx-text-fill: #6b7280;");
        holder.getChildren().add(loading);

        Label nameLabel = new Label(name);
        nameLabel.setMaxWidth(TILE_W);
        nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        VBox tile = new VBox(4, holder, nameLabel, subtitleLabel);
        tile.setPadding(new Insets(6));
        tile.setMaxWidth(TILE_W + 12);
        tile.getStyleClass().add("capture-tile");
        tile.setStyle(tileStyle(false));
        return tile;
    }

    /** Hops to the FX thread and draws {@code shot} on {@code tile}, if there is anything to draw. */
    private void show(VBox tile, BufferedImage shot) {
        Image image = toFx(shot);
        if (image != null) Platform.runLater(() -> setThumb(tile, image));
    }

    private Image toFx(BufferedImage image) {
        return image == null ? null : ScreenCapture.toFxImage(image);
    }

    private void setThumb(VBox tile, Image image) {
        if (tile.getChildren().isEmpty() || !(tile.getChildren().get(0) instanceof StackPane holder)) return;
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(TILE_W);
        view.setFitHeight(THUMB_H);
        holder.getChildren().setAll(view);
    }

    private void select(VBox tile, Selection selection) {
        if (selectedTile != null) selectedTile.setStyle(tileStyle(false));
        selectedTile = tile;
        selected = selection;
        tile.setStyle(tileStyle(true));
    }

    private static String tileStyle(boolean isSelected) {
        return "-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-width: 2; -fx-cursor: hand;"
                + " -fx-border-color: " + (isSelected ? "#3498db" : "transparent") + ";"
                + " -fx-background-color: " + (isSelected ? "rgba(52,152,219,0.10)" : "transparent") + ";";
    }

    private static Rectangle toAwt(Rectangle2D bounds) {
        return new Rectangle((int) Math.round(bounds.getMinX()), (int) Math.round(bounds.getMinY()),
                (int) Math.round(bounds.getWidth()), (int) Math.round(bounds.getHeight()));
    }

    private synchronized ExecutorService thumbs() {
        if (thumbExec == null || thumbExec.isShutdown()) {
            thumbExec = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "capture-picker-thumbs");
                thread.setDaemon(true);
                return thread;
            });
        }
        return thumbExec;
    }

    private synchronized void stopThumbs() {
        if (thumbExec != null) {
            thumbExec.shutdownNow();
            thumbExec = null;
        }
    }
}
