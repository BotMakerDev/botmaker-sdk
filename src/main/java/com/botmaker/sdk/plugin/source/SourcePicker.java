package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import com.botmaker.sdk.plugin.screen.FrameShotSource;
import com.botmaker.sdk.plugin.screen.ScreenOverlay;
import com.botmaker.session.Preview;
import com.botmaker.shared.capture.GamescopeHost;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.capture.ScreenCapture;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstanceScanner;
import com.botmaker.shared.emulator.EmulatorProbe;
import com.botmaker.shared.emulator.Platforms.PlatformStatus;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
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
 * <p>It is the plugin's own vocabulary end to end — a tile stands for one {@link CaptureSource}, which is
 * the SDK's own type and not the host's. The host supplies exactly the two things nobody else can: the
 * current look and the window a modal should be owned by.
 *
 * <p>Below the tiles, an optional region narrows the choice to a rectangle of the source's own pixels, typed
 * or drawn on a grab of it ({@code Draw…}). A caller that only wants a surface — {@link SurfaceMenu}'s
 * "another window" — asks {@link #surfaceOnly()} and gets no region row.
 *
 * <p>A tile stands for the source itself, handed to the host as a value and written into the bot's own Java
 * through {@code CaptureTypes}.
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
        record Concrete(CaptureSource target, Rectangle region) implements Selection {
            public Concrete(CaptureSource target) {
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
    /** The source the picker opened on, whose tile it pre-selects; {@code null} for none. */
    private CaptureSource initial;
    /** Whether the region row is shown; a caller that uses only the surface turns it off. */
    private boolean offersRegion = true;
    private VBox selectedTile;
    private Stage stage;
    private ExecutorService thumbExec;

    private final TextField rx = regionField("x");
    private final TextField ry = regionField("y");
    private final TextField rw = regionField("w");
    private final TextField rh = regionField("h");
    /** Why Draw… could not draw, under the region row; empty otherwise. */
    private final Label regionNote = new Label();

    public SourcePicker(StudioServices services, Window owner, boolean includeProjectDefault) {
        this.services = services;
        this.owner = owner;
        this.includeProjectDefault = includeProjectDefault;
    }

    /**
     * The 🎯 Capture Source press: chooses what the bot reads pixels from, and writes it into the bot's own
     * Java — the one expression {@code Sdk.captureSource()} returns.
     *
     * <p><b>The item's label is constant.</b> A plugin's toolbar list is built with no {@link StudioServices},
     * so a label supplier has no project to read the current source out of.
     */
    public static void choose(ActionContext context) {
        choose(context.services());
    }

    /** As {@link #choose(ActionContext)}, for a window of this plugin's own — Project Setup's capture row. */
    public static void choose(StudioServices services) {
        new SourcePicker(services, Modals.owner(services), false)
                .preselect(CaptureValue.current(services))
                .showAndWait()
                .ifPresent(selection -> {
                    if (!(selection instanceof Selection.Concrete concrete)) return;
                    CaptureValue.point(services, concrete.target(), concrete.region());
                    services.status("Capture source is now "
                            + CaptureLabels.longLabel(CaptureValue.current(services)) + ".");
                });
    }

    /**
     * Pre-selects the tile for {@code current} once it is drawn, and pre-fills the region fields when it is
     * narrowed — so reopening a narrowed source and pressing Select keeps the narrowing. {@code null}, or a
     * source no tile stands for, leaves the default selection.
     */
    public SourcePicker preselect(CaptureSource current) {
        this.initial = current;
        return this;
    }

    /** Hides the region row: the caller reads only which surface was chosen, never a narrowing of it. */
    public SourcePicker surfaceOnly() {
        this.offersRegion = false;
        return this;
    }

    /** Selects {@code tile} when it stands for the surface this picker opened on, region aside. */
    private void offer(VBox tile, CaptureSource target) {
        if (CaptureLabels.same(CaptureLabels.whole(initial), target)) select(tile, new Selection.Concrete(target));
    }

    /**
     * Makes {@code tile} clickable for {@code target}: a click selects it, a double click selects and closes. A
     * click on another surface than the one the region fields were filled for empties them — a region is a
     * rectangle of one surface's pixels, and the same numbers on another are a different place.
     */
    private void clickable(VBox tile, CaptureSource target) {
        tile.setOnMouseClicked(e -> {
            if (!(selected instanceof Selection.Concrete c && CaptureLabels.same(c.target(), target))) {
                setRegion(null);
            }
            select(tile, new Selection.Concrete(target));
            if (e.getClickCount() == 2) {
                applyRegion();
                close();
            }
        });
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
        Rect narrowed = CaptureLabels.region(initial);
        setRegion(narrowed == null ? null
                : new Rectangle(narrowed.x(), narrowed.y(), narrowed.width(), narrowed.height()));
        Label regionLabel = new Label("Region of source (optional):");
        Styles.on(regionLabel, Styles.DIALOG_HINT);
        Button draw = new Button("Draw…");
        draw.setTooltip(new javafx.scene.control.Tooltip(
                "Grab the selected source and drag the part of it the bot should read"));
        draw.setOnAction(e -> drawRegion());
        Button clear = new Button("Whole");
        clear.setTooltip(new javafx.scene.control.Tooltip("Read all of the selected source"));
        clear.setOnAction(e -> setRegion(null));
        HBox regionRow = new HBox(6, regionLabel, rx, ry, rw, rh, draw, clear);
        regionRow.setAlignment(Pos.CENTER_LEFT);
        Styles.on(regionNote, Styles.DIALOG_HINT);

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
            applyRegion();
            close();
        });

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, refresh, spacer, cancel, ok);
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox footer = offersRegion ? new VBox(6, regionRow, regionNote, bar) : new VBox(bar);
        footer.setPadding(new Insets(10, 14, 12, 14));

        VBox root = new VBox(scroll, footer);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        stage = Modals.window(services, owner, Modals.Frame.modal("Choose capture source", 760, 560, 560, 420),
                root);
        stage.setOnHidden(e -> stopThumbs());

        loadWindows(windows);
        loadDesktopAndScreens(desktop, monitors);
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
        Styles.on(field, Styles.SMALL_TEXT);
        return field;
    }

    /**
     * Narrows a concrete selection to the four fields when all of them parse to a positive-area rectangle.
     * Blank, partial or invalid input leaves the selection as the whole source; a region on "Project default"
     * is not a thing that can be said, since that choice is not a source yet.
     */
    private void applyRegion() {
        if (!(selected instanceof Selection.Concrete concrete)) return;
        Rectangle region = parseRegion(rx.getText(), ry.getText(), rw.getText(), rh.getText());
        if (region != null) selected = new Selection.Concrete(concrete.target(), region);
    }

    /** The four fields as a rectangle, or {@code null} unless all four are whole numbers with a positive size. */
    static Rectangle parseRegion(String x, String y, String w, String h) {
        Integer px = parseInt(x), py = parseInt(y), pw = parseInt(w), ph = parseInt(h);
        if (px == null || py == null || pw == null || ph == null || px < 0 || py < 0 || pw <= 0 || ph <= 0) {
            return null;
        }
        return new Rectangle(px, py, pw, ph);
    }

    /** Fills the four fields with {@code region}, or empties them for the whole source. */
    private void setRegion(Rectangle region) {
        rx.setText(region == null ? "" : String.valueOf(region.x));
        ry.setText(region == null ? "" : String.valueOf(region.y));
        rw.setText(region == null ? "" : String.valueOf(region.width));
        rh.setText(region == null ? "" : String.valueOf(region.height));
        regionNote.setText("");
    }

    /**
     * The Draw… press: grabs the selected surface as a capture would — raised, so what is drawn on is what is
     * on screen — and runs the rubber band on that frame, whose pixels are the surface's own. The drag fills
     * the four fields; the picker comes back to the front, since raising the source covered it.
     */
    private void drawRegion() {
        if (!(selected instanceof Selection.Concrete concrete)) {
            regionNote.setText("Choose a source tile first: \"Project default\" is not a surface to draw on.");
            return;
        }
        regionNote.setText("Grabbing " + CaptureLabels.shortLabel(concrete.target()) + "…");
        EditorFrame.grabAsync(services, concrete.target(),
                frame -> {
                    regionNote.setText("");
                    new ScreenOverlay(new FrameShotSource(frame)).selectRegion(stage, r -> {
                        setRegion(new Rectangle(r[0], r[1], r[2], r[3]));
                        if (stage != null) stage.toFront();
                    });
                },
                failure -> regionNote.setText(failure.headline() + " " + failure.detail()));
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
        Label label = Styles.on(new Label(text), Styles.DIALOG_SUBHEADING);
        label.setStyle("-fx-padding: 6 0 0 2;");
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
     * The "Whole desktop" tile — every monitor combined, which is what a bot with no target sees — and one tile
     * per monitor, all thumbnailed from a <b>single</b> desktop grab.
     *
     * <p>One grab and not one per tile: under Wayland a grab can be a screenshot program run or a portal
     * dialog, and the desktop and each monitor used to be one each.
     *
     * <p><b>Monitors are numbered as {@code CaptureSource.monitor(i)} numbers them</b> — AWT's screen devices,
     * the order the bot captures in — not JavaFX's {@code Screen} list, whose order is its own. Until
     * 2026-09-28 the tiles followed JavaFX's, so on a machine where the two disagreed "Screen 2" wrote a bot that
     * read screen 1. The thumbnail is cut from the grab with the same bounds the bot's monitor capture uses.
     */
    private void loadDesktopAndScreens(FlowPane desktopInto, FlowPane monitorsInto) {
        VBox desktopTile = tile("Whole desktop", "All monitors combined");
        CaptureSource desktop = CaptureSource.desktop();
        clickable(desktopTile, desktop);
        // Preselected only when the project-default tile did not claim it — so this is the "add a source"
        // flow's default, where the whole desktop is a better guess than screen 1.
        if (selected == null) select(desktopTile, new Selection.Concrete(desktop));
        offer(desktopTile, desktop);
        desktopInto.getChildren().add(desktopTile);

        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        GraphicsDevice primary = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        List<VBox> tiles = new ArrayList<>();
        List<Rectangle> bounds = new ArrayList<>();
        for (int i = 0; i < devices.length; i++) {
            Rectangle b = ScreenCapture.monitorBounds(i);
            VBox tile = tile("Screen %d — %d×%d".formatted(i + 1, b.width, b.height),
                    devices[i] == primary ? "Primary monitor" : "Monitor");
            CaptureSource target = CaptureSource.monitor(i);
            clickable(tile, target);
            offer(tile, target);
            monitorsInto.getChildren().add(tile);
            tiles.add(tile);
            bounds.add(b);
        }
        thumbs().submit(() -> {
            BufferedImage grab = ScreenCapture.captureDesktop();
            show(desktopTile, grab);
            for (int i = 0; i < tiles.size(); i++) {
                show(tiles.get(i), grab == null ? null : EditorFrame.cropped(grab, bounds.get(i)));
            }
        });
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
                    CaptureSource target = new EmulatorSource(name);
                    clickable(tile, target);
                    offer(tile, target);
                    setThumb(tile, image, running ? "No preview" : "Stopped");
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
            // One tile per title: the source a tile writes is window(title), which matches the first window of
            // that title whichever tile was clicked, so a second tile would be the same choice drawn twice.
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (GenericWindow window : found) {
                String title = window.getTitle();
                if (title == null || title.isBlank() || !seen.add(title)) continue;
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
                    CaptureSource target = CaptureSource.window(title);
                    clickable(tile, target);
                    offer(tile, target);
                    setThumb(tile, image, "No preview");
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
        label.setStyle("-fx-padding: 6 2 2 2;");
        return label;
    }

    private static Label hint(String text) {
        Label label = Styles.on(new Label(text), Styles.DIALOG_HINT);
        label.setWrapText(true);
        return label;
    }

    private VBox tile(String name, String subtitle) {
        StackPane holder = new StackPane();
        holder.setMinSize(TILE_W, THUMB_H);
        holder.setPrefSize(TILE_W, THUMB_H);
        holder.setMaxSize(TILE_W, THUMB_H);
        holder.getChildren().add(Styles.on(new Label("…"), Styles.MUTED_TEXT));

        Label nameLabel = Styles.on(new Label(name), Styles.TILE_NAME, Styles.STRONG_TEXT);
        nameLabel.setMaxWidth(TILE_W);
        Label subtitleLabel = Styles.on(new Label(subtitle), Styles.TILE_NAME, Styles.MUTED_TEXT);

        VBox tile = Styles.on(new VBox(4, holder, nameLabel, subtitleLabel), Styles.TILE);
        tile.setMaxWidth(TILE_W + 12);
        tile.setStyle("-fx-cursor: hand;");
        return tile;
    }

    /** Hops to the FX thread and draws {@code shot} on {@code tile}, or says there is no preview. */
    private void show(VBox tile, BufferedImage shot) {
        Image image = toFx(shot);
        Platform.runLater(() -> setThumb(tile, image, "No preview"));
    }

    private Image toFx(BufferedImage image) {
        return image == null ? null : ScreenOverlay.toFxImage(image);
    }

    /**
     * Replaces the tile's "…" with {@code image}, or with {@code missing} when there is none — a tile left on
     * "…" reads as still loading for ever. A tile with no preview is still a tile the user can pick.
     */
    private void setThumb(VBox tile, Image image, String missing) {
        if (tile.getChildren().isEmpty() || !(tile.getChildren().get(0) instanceof StackPane holder)) return;
        if (image == null) {
            holder.getChildren().setAll(Styles.on(new Label(missing), Styles.MUTED_TEXT));
            return;
        }
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(TILE_W);
        view.setFitHeight(THUMB_H);
        holder.getChildren().setAll(view);
    }

    private void select(VBox tile, Selection selection) {
        if (selectedTile != null) selectedTile.pseudoClassStateChanged(SELECTED, false);
        selectedTile = tile;
        selected = selection;
        tile.pseudoClassStateChanged(SELECTED, true);
    }

    /** What {@code Styles.TILE} draws a picked tile with, in the host's theme. */
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

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
