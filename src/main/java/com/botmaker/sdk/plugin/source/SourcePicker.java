// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.AmbientSource;
import com.botmaker.sdk.internal.capture.AutomaticSource;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.plugin.params.LaunchTargets;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import com.botmaker.sdk.plugin.screen.FrameShotSource;
import com.botmaker.sdk.plugin.screen.OpenWindows;
import com.botmaker.sdk.plugin.screen.ScreenOverlay;
import com.botmaker.session.Preview;
import com.botmaker.session.display.GamescopeHost;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.capture.ScreenCapture;
import com.botmaker.sdk.plugin.emulator.ConnectPhone;
import com.botmaker.shared.emulator.AdbTools;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstanceScanner;
import com.botmaker.shared.emulator.EmulatorProbe;
import com.botmaker.shared.emulator.PhoneState;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.Platforms.PlatformStatus;
import com.botmaker.shared.launch.GameWindows;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.WindowIdentity;
import com.botmaker.shared.vm.GuestOs;
import com.botmaker.shared.vm.GuestWindows;
import com.botmaker.shared.vm.Hypervisor;
import com.botmaker.shared.vm.VmCredentials;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;
import com.botmaker.shared.vm.VmSetup;
import com.botmaker.shared.vnc.GuestWindow;
import com.botmaker.shared.vnc.Keysyms;
import com.botmaker.shared.vnc.VncController;
import com.botmaker.sdk.plugin.settings.VmChoice;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Choose capture source window (layout A, spec {@code 2026-11-25-auto-capture-design.md}), framed like the Game
 * window: a top bar saying what the bot reads, with ▶ Try; the tabs {@link SourceTab} — Automatic, Windows, Emulators
 * &amp; phones, Screens, and the Game VM when the bot has one — opened on the tab of the current value; under them the
 * {@link SourceStrip} of the picked tile, with its region; Cancel and Select.
 *
 * <p>It is the plugin's own vocabulary end to end — a tile stands for one {@link CaptureSource}, which is the SDK's
 * own type and not the host's. The host supplies exactly the two things nobody else can: the current look and the
 * window a modal should be owned by. A caller that only wants a surface — {@link SurfaceMenu}'s "another window" —
 * asks {@link #surfaceOnly()} and gets no Automatic tab and no region.
 *
 * <p>▶ Launch and pick… starts the game and recommends the window it opens ({@link LaunchAndPick}): the way to a
 * game's window that is not open yet.
 *
 * <p>Every grab runs off the FX thread and every one of them is best-effort: a tile with no thumbnail is
 * still a tile the user can pick, because a window that refuses its pixels is still the window they mean.
 */
public final class SourcePicker {

    /**
     * What the user chose: one source, optionally narrowed to {@code region} — a rectangle in the <em>source's
     * own</em> pixel space, where {@code (0,0)} is its top-left, so the narrowing survives the window moving.
     * {@code null} means the whole source. {@link CaptureSource#automatic()} is the Automatic tab's (it replaced the
     * Project default tile, 2026-11-25).
     */
    public sealed interface Selection permits Selection.Concrete {

        record Concrete(CaptureSource target, Rectangle region) implements Selection {
            public Concrete(CaptureSource target) {
                this(target, null);
            }
        }
    }

    private static final double TILE_W = 220;
    private static final double THUMB_H = 124;
    /** Where a tile keeps its title and its thumbnail, for the strip. */
    private static final String NAME = "source-name";
    private static final String IMAGE = "source-image";

    private final StudioServices services;
    private final Window owner;

    private Selection selected;
    /** The source the picker opened on, whose tile it pre-selects; {@code null} for none. */
    private CaptureSource initial;
    /** Whether the Automatic tab and the region are offered; a caller that uses only the surface turns them off. */
    private boolean whole = true;
    /** Whether the picker opened on the Automatic tab, so no hand tile claims the selection. */
    private boolean onAutomatic;
    private VBox selectedTile;
    private Stage stage;
    private ExecutorService thumbExec;
    private SourceStrip strip;
    private AutomaticTab automatic;
    private final Label reads = Styles.on(new Label(), Styles.DIALOG_SUBHEADING);
    private final Label pill = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);

    public SourcePicker(StudioServices services, Window owner) {
        this.services = services;
        this.owner = owner;
    }

    /**
     * The source picker: chooses what the bot reads pixels from, and writes it into the bot's own
     * Java — its {@code Sdk.captureSource} parameter.
     *
     * <p><b>The item's label is constant.</b> A plugin's toolbar list is built with no {@link StudioServices},
     * so a label supplier has no project to read the current source out of.
     */
    public static void choose(ActionContext context) {
        choose(context.services());
    }

    /** As {@link #choose(ActionContext)}, for a window of this plugin's own. */
    public static void choose(StudioServices services) {
        new SourcePicker(services, Modals.owner(services))
                .preselect(CaptureValue.written(services))
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

    /** No Automatic tab and no region: the caller reads only which surface was chosen, never a narrowing of it. */
    public SourcePicker surfaceOnly() {
        this.whole = false;
        return this;
    }

    /** Selects {@code tile} when it stands for the surface this picker opened on, region aside. */
    private void offer(VBox tile, CaptureSource target) {
        if (onAutomatic) return;
        if (CaptureLabels.same(CaptureLabels.whole(initial), target)) pick(tile, target);
    }

    /**
     * Makes {@code tile} clickable for {@code target}: a click picks it, a double click picks it and closes. A
     * click on another surface than the one the region fields were filled for empties them — a region is a
     * rectangle of one surface's pixels, and the same numbers on another are a different place.
     */
    private void clickable(VBox tile, CaptureSource target) {
        tile.setOnMouseClicked(e -> {
            if (!(selected instanceof Selection.Concrete c && CaptureLabels.same(c.target(), target))) {
                strip.setRegion(null);
            }
            pick(tile, target);
            if (e.getClickCount() == 2) use();
        });
    }

    /** Shows the picker modally and returns the chosen source, or empty when it was cancelled. */
    public Optional<Selection> showAndWait() {
        FlowPane windows = category();
        FlowPane monitors = category();
        FlowPane desktop = category();
        FlowPane emulators = category();
        FlowPane phones = category();
        FlowPane vm = category();
        String vmName = VmChoice.current(services);
        LaunchSpec target = LaunchTargets.spec(services);

        strip = new SourceStrip(whole, this::drawRegion, this::use);
        Rect narrowed = CaptureLabels.region(initial);
        strip.setRegion(narrowed == null ? null
                : new Rectangle(narrowed.x(), narrowed.y(), narrowed.width(), narrowed.height()));

        Button tryIt = new Button("▶ Try");
        tryIt.setTooltip(new Tooltip("Grab what the bot would read and show it below"));
        tryIt.setOnAction(e -> tryIt());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, reads, pill, spacer, tryIt);
        top.setAlignment(Pos.CENTER_LEFT);

        Button refreshWindows = new Button("↻");
        refreshWindows.setTooltip(new Tooltip("List the open windows again"));
        refreshWindows.setOnAction(e -> {
            windows.getChildren().clear();
            loadWindows(windows);
        });
        Button refreshDevices = new Button("↻");
        refreshDevices.setTooltip(new Tooltip("List the emulators and phones again"));
        refreshDevices.setOnAction(e -> {
            emulators.getChildren().clear();
            phones.getChildren().clear();
            loadEmulators(emulators, phones);
        });
        Button refreshVm = new Button("↻");
        refreshVm.setTooltip(new Tooltip("List the game VM's windows again"));
        refreshVm.setOnAction(e -> {
            vm.getChildren().clear();
            loadVm(vm, vmName);
        });
        Button launchAndPick = new Button("▶ Launch and pick…");
        launchAndPick.setTooltip(new Tooltip(
                "Start the game this computer launches and pick the window it opens"));
        launchAndPick.setOnAction(e -> LaunchAndPick.show(services, stage).ifPresent(outcome -> {
            switch (outcome) {
                case LaunchAndPick.Outcome.Picked picked -> {
                    strip.setRegion(null);
                    selected = new Selection.Concrete(picked.window());
                    close();
                }
                // The game VM's windows are the guest's, listed in its own tab.
                case LaunchAndPick.Outcome.InVm inVm -> {
                    if (vmName != null) refreshVm.fire();
                }
            }
        }));

        TabPane tabs = new TabPane();
        List<SourceTab> shown = SourceTab.shown(whole, vmName != null);
        for (SourceTab tab : shown) {
            Node content = switch (tab) {
                case AUTOMATIC -> {
                    automatic = new AutomaticTab(services, this::pickAutomatic, () -> {
                        pickAutomatic();
                        use();
                    });
                    yield automatic.node();
                }
                case WINDOWS -> withFooter(scroll(windows), launchAndPick, refreshWindows);
                case DEVICES -> withFooter(scroll(sectionLabel("Emulators"), emulators,
                        sectionLabel("Phones"), phones), refreshDevices);
                case SCREENS -> scroll(sectionLabel("Desktop"), desktop, sectionLabel("Monitors"), monitors);
                case VM -> withFooter(scroll(vm), refreshVm);
            };
            Tab t = new Tab(tab == SourceTab.VM ? tab.displayName() + " " + vmName : tab.displayName(), content);
            t.setClosable(false);
            tabs.getTabs().add(t);
        }
        SourceTab opening = SourceTab.opening(initial, target, whole, vmName != null);
        tabs.getSelectionModel().select(shown.indexOf(opening));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        onAutomatic = opening == SourceTab.AUTOMATIC;
        if (onAutomatic) pickAutomatic();

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> {
            selected = null;
            close();
        });
        Button ok = new Button("Select");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> use());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox bottom = new HBox(8, gap, cancel, ok);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(8, top, tabs, strip.node(), bottom);
        root.setPadding(new Insets(12, 16, 12, 16));

        stage = Modals.window(services, owner, Modals.Frame.modal("Choose capture source", 820, 680, 560, 460),
                root);
        stage.setOnHidden(e -> stopThumbs());

        loadWindows(windows);
        loadDesktopAndScreens(desktop, monitors);
        // A phone on a cable is listed only by an adb server: started first, on the same thread, so the list has it.
        thumbs().submit(AdbTools::wake);
        loadEmulators(emulators, phones);
        if (vmName != null) loadVm(vm, vmName);

        stage.showAndWait();
        return Optional.ofNullable(selected);
    }

    private static Node scroll(Node... sections) {
        VBox content = new VBox(10, sections);
        content.setPadding(new Insets(14));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    /** {@code content} over a row of its own buttons, as the Game window's Windows and Emulators tabs have. */
    private static Node withFooter(Node content, Button... buttons) {
        HBox footer = new HBox(8, buttons);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(6, 14, 8, 14));
        VBox box = new VBox(content, footer);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    /** Use and Select: the picked source, narrowed to the region typed, and the window closes. */
    private void use() {
        applyRegion();
        close();
    }

    private void close() {
        stopThumbs();
        if (stage != null) stage.close();
    }

    /** Narrows the selection to the region fields when they make a rectangle; else it stays the whole source. */
    private void applyRegion() {
        if (!whole || !(selected instanceof Selection.Concrete concrete)) return;
        Rectangle region = strip.region();
        if (region != null) selected = new Selection.Concrete(concrete.target(), region);
    }

    /**
     * A slot pill's words for {@code source}: {@link CaptureLabels#shortLabel}, and for the automatic source the
     * window a run picked for the bot's game, {@code Automatic · picked <title>}.
     */
    public static String label(StudioServices services, CaptureSource source) {
        if (CaptureLabels.region(source) == null && source instanceof AutomaticSource && services != null) {
            return AutomaticTab.pillLabel(services);
        }
        return CaptureLabels.shortLabel(source);
    }

    /** What a grab of {@code target} grabs: the project's source as resolved now for the automatic one. */
    private static CaptureSource grabbed(CaptureSource target) {
        return target instanceof AmbientSource ? null : target;
    }

    /**
     * The Draw… press: grabs the selected surface as a capture would — raised, so what is drawn on is what is
     * on screen — and runs the rubber band on that frame, whose pixels are the surface's own. The drag fills
     * the four fields; the picker comes back to the front, since raising the source covered it.
     */
    private void drawRegion() {
        if (!(selected instanceof Selection.Concrete concrete)) {
            strip.note("Pick a source first.");
            return;
        }
        strip.note("Grabbing " + CaptureLabels.shortLabel(concrete.target()) + "…");
        EditorFrame.grabAsync(services, grabbed(concrete.target()),
                frame -> {
                    strip.note("");
                    new ScreenOverlay(new FrameShotSource(frame)).selectRegion(stage, r -> {
                        strip.setRegion(new Rectangle(r[0], r[1], r[2], r[3]));
                        if (stage != null) stage.toFront();
                    });
                },
                failure -> strip.note(failure.headline() + " " + failure.detail()));
    }

    /** ▶ Try: grabs what the picked source reads now and shows it in the strip. */
    private void tryIt() {
        CaptureSource target = selected instanceof Selection.Concrete concrete ? concrete.target() : null;
        strip.note("Grabbing " + CaptureLabels.shortLabel(target) + "…");
        EditorFrame.grabAsync(services, grabbed(target),
                frame -> {
                    strip.note("");
                    strip.preview(toFx(frame.image()));
                    if (stage != null) stage.toFront();
                },
                failure -> strip.note(failure.headline() + " " + failure.detail()));
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

    /** The Automatic card picked: the top bar says what it reads for this bot now. */
    private void pickAutomatic() {
        CaptureSource auto = CaptureSource.automatic();
        select(automatic.card(), new Selection.Concrete(auto));
        strip.show(CaptureLabels.AUTOMATIC, auto, null);
        reads.setText("📷 Reads: " + CaptureLabels.AUTOMATIC);
        pill.setText(automatic.reads());
    }

    /** {@code tile} picked for {@code target}: the strip shows it and the top bar names it. */
    private void pick(VBox tile, CaptureSource target) {
        select(tile, new Selection.Concrete(target));
        strip.show(String.valueOf(tile.getProperties().getOrDefault(NAME, CaptureLabels.shortLabel(target))), target,
                tile.getProperties().get(IMAGE) instanceof Image image ? image : null);
        reads.setText("📷 Reads: " + CaptureLabels.shortLabel(target));
        pill.setText("");
    }

    /**
     * The "Whole desktop" tile — every monitor combined, which is what a bot with no target sees — and one tile
     * per monitor, all thumbnailed from a <b>single</b> desktop grab.
     *
     * <p>One grab and not one per tile: under Wayland a grab can be a screenshot program run or a portal
     * dialog, and the desktop and each monitor used to be one each.
     *
     * <p><b>Monitors are numbered as {@code CaptureSource.monitor(i)} numbers them</b> — AWT's screen devices,
     * the order the bot captures in — not JavaFX's {@code Screen} list, whose order is its own, so "Screen 2"
     * writes a bot that reads screen 2 on a machine where the two disagree. The thumbnail is cut from the grab with the same bounds the bot's monitor capture uses.
     */
    private void loadDesktopAndScreens(FlowPane desktopInto, FlowPane monitorsInto) {
        VBox desktopTile = tile(CaptureLabels.WHOLE_DESKTOP, "All monitors combined");
        CaptureSource desktop = CaptureSource.desktop();
        clickable(desktopTile, desktop);
        // Preselected only when nothing else is — the "another window" flow's default, where the whole desktop is a
        // better guess than screen 1.
        if (selected == null) pick(desktopTile, desktop);
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
     * which is exactly what somebody setting a project up is doing. Phones go to {@code phonesInto}, each saying
     * where it stands ({@link PhoneState}), then the way to connect one.
     */
    private void loadEmulators(FlowPane into, FlowPane phonesInto) {
        thumbs().submit(() -> {
            EmulatorInstanceScanner.Scan scan;
            try {
                scan = new EmulatorInstanceScanner().scan();
            } catch (Throwable noScan) {
                scan = new EmulatorInstanceScanner.Scan(List.of(), List.of());
            }
            List<EmulatorInstance> instances = scan.instances().stream()
                    .filter(i -> i.platformId() != PlatformId.PHYSICAL).toList();
            List<EmulatorInstance> phones = scan.instances().stream()
                    .filter(i -> i.platformId() == PlatformId.PHYSICAL).toList();
            loadPhones(phonesInto, phones, scan.statuses());
            if (instances.isEmpty()) {
                List<PlatformStatus> statuses = scan.statuses().stream()
                        .filter(s -> s.platformId() != PlatformId.PHYSICAL).toList();
                Platform.runLater(() -> into.getChildren().add(emptyEmulatorsHint(statuses)));
                return;
            }
            for (EmulatorInstance instance : instances) {
                String name = instance.name();
                boolean running = EmulatorProbe.isRunning(instance);
                Image image = running ? toFx(emulatorThumbnail(instance)) : null;
                Platform.runLater(() -> {
                    VBox tile = tile(name, running ? "Emulator · running" : "Emulator · stopped");
                    CaptureSource target = new EmulatorSource(instance.reference());
                    clickable(tile, target);
                    setThumb(tile, image, running ? "No preview" : "Stopped");
                    offer(tile, target);
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
     * One tile per phone, its line saying where it stands: one waiting on "Allow USB debugging?" can be picked
     * already, since it is saved by its serial and read once allowed. Then ＋ Connect a phone…, which lists again.
     * Thumbnail thread.
     */
    private void loadPhones(FlowPane into, List<EmulatorInstance> phones, List<PlatformStatus> statuses) {
        List<PhoneState> states = phones.stream().map(PhoneState::of).toList();
        List<Image> images = new ArrayList<>();
        for (int i = 0; i < phones.size(); i++) {
            images.add(states.get(i).online() ? toFx(emulatorThumbnail(phones.get(i))) : null);
        }
        String why = phones.isEmpty() ? statuses.stream().filter(s -> s.platformId() == PlatformId.PHYSICAL)
                .map(PlatformStatus::statusLine).findFirst().orElse(null) : null;
        // One replace, not adds: a Connect's list and a Refresh's can overlap, and each must leave one copy.
        Platform.runLater(() -> {
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < phones.size(); i++) {
                EmulatorInstance phone = phones.get(i);
                PhoneState state = states.get(i);
                VBox tile = tile(phone.name(), state.displayName());
                if (!state.advice().isEmpty()) Tooltip.install(tile, new Tooltip(state.advice()));
                CaptureSource target = new EmulatorSource(phone.reference());
                clickable(tile, target);
                setThumb(tile, images.get(i), state.online() ? "No preview" : "Not readable yet");
                offer(tile, target);
                nodes.add(tile);
            }
            if (phones.isEmpty()) {
                nodes.add(hint("No phone found: plug one in with USB debugging on, or connect one over Wi-Fi."
                        + (why == null ? "" : " " + why)));
            }
            Button connect = new Button("＋ Connect a phone…");
            connect.setOnAction(e -> {
                ConnectPhone.show(services, stage);
                if (stage == null || !stage.isShowing()) return;
                thumbs().submit(() -> {
                    EmulatorInstanceScanner.Scan scan;
                    try {
                        scan = new EmulatorInstanceScanner().scan();
                    } catch (Throwable noScan) {
                        scan = new EmulatorInstanceScanner.Scan(List.of(), List.of());
                    }
                    loadPhones(into, scan.instances().stream()
                            .filter(i -> i.platformId() == PlatformId.PHYSICAL).toList(), scan.statuses());
                });
            });
            nodes.add(connect);
            into.getChildren().setAll(nodes);
        });
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
        box.getChildren().add(hint("Start an instance with ADB enabled, then press ↻."));
        return box;
    }

    /** How long the picker waits for a guest's window list it has just started. */
    private static final long GUEST_LIST_WAIT_MS = 4_000;

    /**
     * The bot's game VM, {@code name}: its whole screen, which {@code desktop()} reads while the bot runs in the VM,
     * then one tile per window the guest lists, each {@code window(title)} found in the guest. Read from the
     * running VM only: a VM that isn't running offers its screen without a preview, and says how to start it.
     */
    private void loadVm(FlowPane into, String name) {
        VBox screenTile = tile("Whole VM screen", "The guest's desktop");
        CaptureSource desktop = CaptureSource.desktop();
        clickable(screenTile, desktop);
        offer(screenTile, desktop);
        into.getChildren().add(screenTile);
        thumbs().submit(() -> {
            Optional<VmRecord> found = VmInventory.find(name);
            if (found.isEmpty() || !VmInventory.running(found.get())) {
                show(screenTile, null);
                Platform.runLater(() -> into.getChildren().add(hint(found.isEmpty()
                        ? "There's no game VM named \"" + name + "\" on this computer."
                        : "The VM isn't running: open its screen from Sdk.java's vm(), then press ↻ to list "
                                + "its windows.")));
                return;
            }
            VmRecord vm = found.get();
            try {
                VmCredentials credentials = VmCredentials.load(vm.folder())
                        .orElseThrow(() -> new IOException("The VM has lost its passwords."));
                if (vm.hypervisor() == Hypervisor.TART && vm.guestOs() == GuestOs.MACOS) {
                    // Its VNC address is the running VM's, which a start reads back; it lists no windows yet. A
                    // Linux guest's bots each have a display of their own, so it goes on to say none is listed.
                    try (VmSetup.Running running = VmSetup.start(vm, credentials)) {
                        show(screenTile, running.screen().captureScreen());
                    }
                    return;
                }
                GuestWindows.start(vm, credentials);
                // Straight to its screen: a bot's session may be using the VM, and VmSetup.start would open QEMU's
                // monitor beside it.
                try (VncController screen = VncController.connect("127.0.0.1", vm.vncPort(), credentials.vnc(),
                        Keysyms.NativeKeys.VIRTUAL_KEY, vm.name(), GuestWindows.reader(vm, credentials), 10_000)) {
                    long until = System.nanoTime() + GUEST_LIST_WAIT_MS * 1_000_000L;
                    while (screen.guestWindows().isEmpty() && System.nanoTime() < until) Thread.sleep(250);
                    show(screenTile, screen.captureScreen());
                    Set<WindowIdentity> seen = new HashSet<>();
                    for (GuestWindow window : screen.guestWindows()) {
                        String title = window.title();
                        GenericWindow guest = new GenericWindow(window, title, window.rect(), GenericWindow.NO_PID,
                                window.process());
                        WindowIdentity identity = WindowIdentity.of(guest);
                        // One tile per identity, as below; a guest window's class is its program.
                        if (title.isBlank() || identity == null || !seen.add(identity)) continue;
                        Image image = toFx(screen.captureWindow(guest));
                        Platform.runLater(() -> {
                            VBox tile = tile(title, "In the VM · " + identity.windowClass());
                            CaptureSource target = NamedWindow.of(identity);
                            clickable(tile, target);
                            setThumb(tile, image, "No preview");
                            offer(tile, target);
                            into.getChildren().add(tile);
                        });
                    }
                }
            } catch (IOException | RuntimeException e) {
                show(screenTile, null);
                Platform.runLater(() -> into.getChildren().add(hint("The VM's windows couldn't be listed: "
                        + e.getMessage())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // the picker closed
            }
        });
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
            // One tile per identity: the source a tile writes is window(class, title), which matches the first
            // window it names whichever tile was clicked, so a second tile would be the same choice drawn twice.
            // BotMaker's own windows are never the game (2026-10-13), and a window with no class cannot be named.
            Set<WindowIdentity> seen = new HashSet<>();
            for (GenericWindow window : OpenWindows.pickable(found)) {
                String title = window.getTitle();
                WindowIdentity identity = WindowIdentity.of(window);
                if (identity == null || !seen.add(identity)) continue;
                // The window a run finds by this identity, which is what the thumbnail must show when several share it.
                GenericWindow shown = GameWindows.named(identity, found).orElse(window);
                BufferedImage shot;
                try {
                    shot = NativeControllerFactory.get().captureWindow(shown);
                } catch (Throwable notCaptured) {
                    shot = null;
                }
                Image image = toFx(shot);
                Platform.runLater(() -> {
                    VBox tile = tile(title, "Window · " + identity.windowClass());
                    CaptureSource target = NamedWindow.of(identity);
                    clickable(tile, target);
                    setThumb(tile, image, "No preview");
                    offer(tile, target);
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
                        + "listed; native Wayland windows can't be enumerated. Use a Screen or Automatic."
                : "No windows detected. Open the app you want to capture, then press ↻.");
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
        tile.getProperties().put(NAME, name);
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
     * "…" reads as still loading for ever. A tile with no preview is still a tile the user can pick. The picked
     * tile's preview reaches the strip too.
     */
    private void setThumb(VBox tile, Image image, String missing) {
        if (tile.getChildren().isEmpty() || !(tile.getChildren().get(0) instanceof StackPane holder)) return;
        if (image == null) {
            holder.getChildren().setAll(Styles.on(new Label(missing), Styles.MUTED_TEXT));
            return;
        }
        tile.getProperties().put(IMAGE, image);
        if (tile == selectedTile) strip.preview(image);
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
