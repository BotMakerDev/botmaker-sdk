package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.internal.input.Hands;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.sdk.plugin.assist.BoardCuts;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.assist.ScreenShots;
import com.botmaker.sdk.plugin.launch.QuickLaunch;
import com.botmaker.sdk.plugin.pictures.TemplateUses;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.screen.CutShape;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Screen tab's game, live (the user's call, 2026-10-18): ● Live draws the game on the board about 8 times a
 * second, as the look tools read it, and a click, a drag, the wheel or a key on it goes to the game through the
 * click, key and scroll tools' own hands. The board says which screen the game shows, by its markers, once a second
 * at most, and lights it on the graph and in the outline; a click on one of its inputs that led to another screen
 * mapped already offers to record that way in.
 *
 * <p>❄ Freeze keeps the frame shown, lossless: its screen's shot taken from it, or a new screen made of it, under the
 * screen last recognised and opened by the input last clicked there. A frame a run saved when a wait timed out opens
 * frozen the same way. ▶ Launch starts the game when it is not up.
 *
 * <p>✂ Cut draws pictures on the frozen frame itself ({@link CutLayer}), each then said what it is ({@link CutForm})
 * and saved as the tools save it ({@link BoardCuts}); a screen's shot is cut on the same way.
 */
final class LiveBoard {

    /** How often a live frame is read: about 8 a second. */
    private static final long FRAME_MS = 125;
    /** How often the screen a live frame shows is read: matching every marker is too dear for each frame. */
    private static final long HERE_MS = 1_000;
    /** A press that moved less than this, in the frame's pixels, is a click; more is a drag. */
    private static final int DRAG_PX = 6;
    /** The shortest a drag is played over, so a game reads it as one. */
    private static final long LEAST_DRAG_MS = 150;
    /** A mouse wheel's notch, in scroll pixels. */
    private static final double NOTCH = 40;

    private final StudioServices services;
    private final ScreenBoard board;
    private final ScreenHands hands;
    private final Supplier<ScreenGraph> graph;
    private final Consumer<String> done;
    private final Consumer<String> select;
    private final Consumer<String> lit;

    private final ToggleButton liveButton = new ToggleButton("● Live");
    private final Button freeze = new Button("❄ Freeze");
    private final MenuButton missed = new MenuButton("Missed frames");
    private final ImageView view = new ImageView();
    private final Pane boxes = new Pane();
    private final StackPane game = new StackPane(view, boxes);
    private final ImageView frozenView = new ImageView();
    /** Cuts drawn on the frozen frame. */
    private final CutLayer cutter = new CutLayer(frozenView, this::showLine);
    private final Work work;
    /** Counts each start and stop: a frame loop of an earlier start ends, and what it read is dropped. */
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean drawing = new AtomicBoolean();
    private final AtomicBoolean reading = new AtomicBoolean();
    /** The last live frame, lossless: what ❄ Freeze keeps. */
    private volatile BufferedImage latest;

    // On the FX thread from here.
    /** The inputs given to the game, one at a time and in order, off the FX thread; made when first needed. */
    private ExecutorService acting;
    private PanelContext context;
    private int frameWidth;
    private int frameHeight;
    /** The screen the game shows now, by its markers; null for none of the map's. */
    private String on;
    private String popup;
    /** The last screen recognised: a new screen frozen goes under it. */
    private String lastKnown;
    private LiveWays.Click lastClick;
    /** When the frame last read for its screen was taken: a click after it is on a screen not read yet. */
    private long readAtMs;
    /** A way in the last click proved, offered until recorded or dismissed. */
    private LiveWays.Proved offer;
    /** Why no frame comes, while none does; null while they do. */
    private String blind;
    /** Whether the game is on the user's own desktop, where an input here takes their mouse. */
    private boolean onDesktop;
    private BufferedImage frozen;
    /** The screen the frozen frame shows, by its markers. */
    private String frozenOn;
    /** Whether {@link #frozenOn} was read off the frozen frame itself yet: until then nothing is offered on it. */
    private boolean frozenRead;
    /** Whether the game still shows the frozen frame, frozen off it just now: a cut on it is then watched still. */
    private boolean frozenWatched;
    private long frozenAtMs;
    /** The screen cuts are noted on, first offered; null: the frame's. */
    private String cutScreen;
    /** What the first cut is, first offered. */
    private CutForm.Kind cutFirst = CutForm.Kind.BUTTON;
    private Point pressedAt;
    private long pressedMs;
    private double wheel;

    /**
     * Runs {@code work}, said as {@code doing}, off the FX thread, the map read again once it wrote it; false when it
     * did not start, a try or a walk running.
     */
    @FunctionalInterface
    interface Work {
        boolean run(String doing, String screen, Supplier<String> work);
    }

    /** How long after ❄ Freeze the game is taken to show the frame still: a cut is watched still only until then. */
    private static final long WATCHED_MS = 60_000;

    /**
     * @param done   what an action answered, said on the panel
     * @param select picks a screen on the map
     * @param lit    the screen the game shows, lit on the graph and in the outline; null for none
     * @param work   runs a save of the cuts
     */
    LiveBoard(StudioServices services, ScreenBoard board, ScreenHands hands, Supplier<ScreenGraph> graph,
              Consumer<String> done, Consumer<String> select, Consumer<String> lit, Work work) {
        this.work = work;
        this.services = services;
        this.board = board;
        this.hands = hands;
        this.graph = graph;
        this.done = done;
        this.select = select;
        this.lit = lit;
        liveButton.setTooltip(new Tooltip(MapGuide.LIVE));
        liveButton.setOnAction(e -> {
            if (liveButton.isSelected()) start();
            else stop();
        });
        freeze.setTooltip(new Tooltip(MapGuide.FREEZE));
        freeze.setOnAction(e -> freeze());
        freeze.disableProperty().bind(liveButton.selectedProperty().not());
        missed.setTooltip(new Tooltip(MapGuide.MISSED));
        missed.setOnShowing(e -> missed.getItems().setAll(missedItems()));
        missed.getItems().add(new MenuItem());
        Button launch = QuickLaunch.button(services, (ok, message) -> done.accept(message));
        board.controls(liveButton, freeze, launch, missed);
        view.setPreserveRatio(true);
        frozenView.setPreserveRatio(true);
        boxes.setMouseTransparent(true);
        game.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        game.setFocusTraversable(true);
        game.getStyleClass().add("screen-live");
        game.addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        game.addEventHandler(MouseEvent.MOUSE_RELEASED, this::released);
        game.addEventHandler(ScrollEvent.SCROLL, this::wheeled);
        game.addEventHandler(KeyEvent.KEY_PRESSED, this::keyed);
    }

    /** The Screen tab was left: the game is no longer read, nor lit. A frozen frame stays. */
    void pause() {
        if (liveButton.isSelected()) stop();
        else lit.accept(null);
    }

    /** The window or the project closed: nothing here reads or acts any more. */
    void close() {
        pause();
        if (acting != null) acting.shutdownNow();
        acting = null;
    }

    private void start() {
        if (services.projectDir() == null) {
            liveButton.setSelected(false);
            done.accept("No project is open.");
            return;
        }
        context = new PanelContext(services, CaptureValue.current(services));
        liveButton.setSelected(true);
        frozen = null;
        cutter.reset(0, 0);
        board.holdStill(false);
        blind = null;
        // Nothing of the last time shows until the first frame comes.
        view.setImage(null);
        frameWidth = 0;
        frameHeight = 0;
        on = null;
        popup = null;
        boxes.getChildren().clear();
        board.cover(this::drawLive, true);
        showLine();
        long mine = generation.incrementAndGet();
        PanelContext reads = context;
        Thread.ofVirtual().name("live-board").start(() -> loop(mine, reads));
    }

    private void stop() {
        generation.incrementAndGet();
        liveButton.setSelected(false);
        if (frozen == null) board.cover(null, false);
        on = null;
        popup = null;
        lit.accept(null);
        showLine();
    }

    /** Reads the game until another start or a stop, drawing each frame the FX thread is ready for. */
    private void loop(long mine, PanelContext reads) {
        boolean desktop = ScreenDesk.hands(reads) instanceof Hands.OnDesktop;
        Platform.runLater(() -> {
            if (generation.get() != mine) return;
            onDesktop = desktop;
            showLine();
        });
        long lastHere = 0;
        while (generation.get() == mine) {
            long began = System.currentTimeMillis();
            Optional<BufferedImage> got;
            try {
                got = ScreenDesk.look(reads);
            } catch (RuntimeException unreadable) {
                got = Optional.empty();
            }
            // Stopped or started again while the game was read: this loop's frame is not the board's any more.
            if (generation.get() != mine) return;
            if (got.isPresent()) {
                BufferedImage image = got.get();
                latest = image;
                if (drawing.compareAndSet(false, true)) {
                    try {
                        Image shown = ScreenCapture.toFxImage(image);
                        Platform.runLater(() -> {
                            drawing.set(false);
                            if (generation.get() == mine) shown(shown, image.getWidth(), image.getHeight());
                        });
                    } catch (RuntimeException undrawn) {
                        drawing.set(false);
                    }
                }
                if (began - lastHere >= HERE_MS && reading.compareAndSet(false, true)) {
                    lastHere = began;
                    long takenMs = began;
                    Thread.ofVirtual().name("live-here").start(() -> readHere(mine, reads, image, takenMs));
                }
            } else {
                Platform.runLater(() -> {
                    if (generation.get() == mine && blind == null) {
                        blind = "No frame: the game is not up, or the bot looks at no window. ▶ Launch starts it.";
                        showLine();
                    }
                });
            }
            long rest = FRAME_MS - (System.currentTimeMillis() - began);
            if (rest <= 0) continue;
            try {
                Thread.sleep(rest);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Which screen {@code image}, taken at {@code takenMs}, shows, handed to the FX thread; one read at a time. */
    private void readHere(long mine, PanelContext reads, BufferedImage image, long takenMs) {
        try {
            Optional<ScreenDesk.Here> seen = ScreenDesk.here(reads, image);
            Platform.runLater(() -> {
                if (generation.get() == mine) heard(seen.orElse(null), takenMs);
            });
        } catch (RuntimeException unread) {
            // The next second reads it again.
        } finally {
            reading.set(false);
        }
    }

    private void shown(Image image, int width, int height) {
        view.setImage(image);
        if (blind != null) {
            blind = null;
            showLine();
        }
        if (width != frameWidth || height != frameHeight) {
            frameWidth = width;
            frameHeight = height;
            drawBoxes();
        }
    }

    /** The game drawn {@code width} wide, the inputs of the screen it shows boxed on it. */
    private Node drawLive(double width) {
        view.setFitWidth(width);
        drawBoxes();
        return game;
    }

    private Node drawFrozen(double width) {
        frozenView.setFitWidth(width);
        cutter.redraw();
        return cutter.node();
    }

    private void drawBoxes() {
        boxes.getChildren().clear();
        if (on == null || frameWidth == 0) return;
        double scale = view.getFitWidth() / frameWidth;
        for (Rect box : boxesOf(on).values()) {
            Rectangle drawn = new Rectangle(box.x() * scale, box.y() * scale, box.width() * scale,
                    box.height() * scale);
            drawn.getStyleClass().add("screen-live-box");
            boxes.getChildren().add(drawn);
        }
    }

    /** Where {@code constant}'s inputs are on a live frame, by field. */
    private Map<String, Rect> boxesOf(String constant) {
        ScreenGraph map = graph.get();
        if (!map.screens().containsKey(constant) || services.pluginValues() == null || frameWidth == 0) return Map.of();
        return LiveWays.boxes(ScreenCards.rows(map, constant), TemplateUses.cuts(services.pluginValues(),
                map.pictures(constant)), frameWidth, frameHeight);
    }

    // --- input on the game ---

    private void pressed(MouseEvent e) {
        if (!liveButton.isSelected() || frameWidth == 0) return;
        game.requestFocus();
        pressedAt = toFrame(e.getX(), e.getY());
        pressedMs = System.currentTimeMillis();
        e.consume();
    }

    private void released(MouseEvent e) {
        if (!liveButton.isSelected() || pressedAt == null) return;
        Point from = pressedAt;
        Point to = toFrame(e.getX(), e.getY());
        pressedAt = null;
        e.consume();
        if (Math.abs(to.x() - from.x()) < DRAG_PX && Math.abs(to.y() - from.y()) < DRAG_PX) {
            // A click on an input not yet followed by a read of the game is kept: the screen this one fell on is not
            // known, and that click's way in is still to be proved.
            boolean pending = lastClick != null && lastClick.field() != null && readAtMs < lastClick.atMs();
            if (!pending) {
                String screen = on != null ? on : lastKnown;
                String field = screen == null ? null : LiveWays.fieldAt(boxesOf(screen), from).orElse(null);
                lastClick = new LiveWays.Click(screen, field, System.currentTimeMillis());
            }
            act(h -> h.click(from.x(), from.y()));
        } else {
            long ms = Math.max(LEAST_DRAG_MS, System.currentTimeMillis() - pressedMs);
            act(h -> h.drag(from.x(), from.y(), to.x(), to.y(), ms));
        }
    }

    private void wheeled(ScrollEvent e) {
        if (!liveButton.isSelected() || frameWidth == 0) return;
        e.consume();
        wheel += e.getDeltaY();
        int notches = (int) (wheel / NOTCH);
        if (notches == 0) return;
        wheel -= notches * NOTCH;
        Point at = toFrame(e.getX(), e.getY());
        act(h -> h.scroll(at.x(), at.y(), notches));
    }

    private void keyed(KeyEvent e) {
        if (!liveButton.isSelected()) return;
        Optional<Key> key = Key.named(e.getCode().name()).or(() -> Key.named(e.getCode().getName()));
        if (key.isEmpty()) return;
        e.consume();
        act(h -> h.key(key.get()));
    }

    /** A point of the board, in the frame's pixels, kept on the frame. */
    private Point toFrame(double x, double y) {
        double scale = frameWidth / Math.max(1, view.getFitWidth());
        return new Point(Math.clamp((long) (x * scale), 0, frameWidth - 1),
                Math.clamp((long) (y * scale), 0, frameHeight - 1));
    }

    /** Gives {@code input} to the game by the hands the look tools read through; what failed is said. */
    private void act(Consumer<Hands> input) {
        PanelContext acts = context;
        if (acting == null) {
            acting = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("live-input").factory());
        }
        try {
            acting.execute(() -> {
                String failed = null;
                try {
                    input.accept(ScreenDesk.hands(acts));
                } catch (UnsupportedOperationException cannot) {
                    failed = cannot.getMessage();
                } catch (RuntimeException broke) {
                    failed = "The input failed: " + broke.getMessage();
                }
                if (failed != null) {
                    String said = failed;
                    Platform.runLater(() -> done.accept(said));
                }
            });
        } catch (RejectedExecutionException closed) {
            // The project closed: nothing acts any more.
        }
    }

    // --- what the game shows ---

    /** What the frame taken at {@code takenMs} shows. */
    private void heard(ScreenDesk.Here seen, long takenMs) {
        String now = seen == null ? null : seen.screen();
        popup = seen == null ? null : seen.popup();
        String was = lastKnown;
        readAtMs = takenMs;
        if (!Objects.equals(now, on)) {
            on = now;
            lit.accept(now);
        }
        // Each read: a way recorded or a picture recut meanwhile moves the boxes too.
        drawBoxes();
        if (now != null) {
            lastKnown = now;
            // Proved by a frame taken after the click only: one taken before says nothing of what it opened.
            Optional<LiveWays.Proved> proved = LiveWays.proved(lastClick, was, now, takenMs)
                    .filter(p -> ScreenEdits.wayRefused(graph.get(), p.to(), p.from(), p.field()).isEmpty());
            if (proved.isPresent()) {
                offer = proved.get();
                lastClick = null;
            }
        }
        showLine();
    }

    /** What the line under the bar says: the screen the game shows and what may be done there, or the frozen frame's. */
    private void showLine() {
        ScreenGraph map = graph.get();
        List<Node> said = new ArrayList<>();
        if (frozen != null) {
            String shows = !frozenRead ? "reading which screen it is…" : frozenOn == null ? "no screen of the map"
                    : map.shown(frozenOn);
            said.add(new Label("❄ Frozen, " + frozen.getWidth() + "×" + frozen.getHeight() + ": " + shows));
            if (cutter.isOn()) {
                said.addAll(cutTools());
                board.line(said.toArray(Node[]::new));
                return;
            }
            said.add(button("✂ Cut", MapGuide.CUT_FROZEN, () -> beginCut(frozenOn, CutForm.Kind.BUTTON)));
            if (frozenRead && frozenOn != null) {
                said.add(button("Retake " + map.shown(frozenOn) + "'s shot", MapGuide.RETAKE_FROZEN, this::retake));
            }
            if (frozenRead) {
                said.add(button(frozenOn == null ? "New screen…" : "New screen from here…", MapGuide.NEW_FROM_FROZEN,
                        this::newScreen));
            }
            said.add(button("● Back to live", null, this::start));
            said.add(button("✕", "Close the frame: the picked screen's shot again", this::unfreeze));
        } else if (liveButton.isSelected()) {
            if (blind != null) {
                said.add(new Label(blind));
            } else if (on == null) {
                said.add(new Label("On no screen of the map"));
            } else {
                String shown = on;
                Hyperlink at = new Hyperlink("On " + map.shown(shown));
                at.setTooltip(new Tooltip("Pick it on the map"));
                at.setOnAction(e -> select.accept(shown));
                said.add(at);
            }
            if (popup != null) said.add(new Label("· the popup " + map.shown(popup) + " shows over it"));
            if (onDesktop) {
                Label mouse = new Label("· the game is on your desktop: an input here takes your mouse");
                mouse.getStyleClass().add("screen-panel-path");
                said.add(mouse);
            }
            if (offer != null && map.screens().containsKey(offer.from()) && map.screens().containsKey(offer.to())) {
                LiveWays.Proved way = offer;
                said.add(button("Record: " + map.shown(way.from()) + " · " + way.field() + " → " + map.shown(way.to()),
                        MapGuide.RECORD_WAY, () -> {
                            offer = null;
                            done.accept(ScreenEdits.addWay(services, way.to(), way.from(), way.field()));
                            showLine();
                        }));
                said.add(button("✕", "Not that way", () -> {
                    offer = null;
                    showLine();
                }));
            }
        }
        board.line(said.toArray(Node[]::new));
    }

    // --- frozen ---

    private void freeze() {
        BufferedImage image = latest;
        if (image == null) {
            done.accept("Nothing to freeze yet: no frame came from the game.");
            return;
        }
        frozen(image, null, false, true);
        // Read again off this very frame: the live read may be a second older, of the screen before.
        PanelContext reads = context();
        Thread.ofVirtual().name("live-frozen").start(() -> {
            String shows;
            try {
                shows = ScreenDesk.here(reads, image).map(ScreenDesk.Here::screen).orElse(null);
            } catch (RuntimeException unread) {
                shows = null;
            }
            String read = shows;
            Platform.runLater(() -> {
                if (frozen != image) return;
                frozenOn = read;
                frozenRead = true;
                lit.accept(read);
                showLine();
            });
        });
    }

    /**
     * Shows {@code image} frozen, as showing {@code shows} once {@code read}, {@code watched} when the game still shows
     * it; whatever read the game live stops.
     */
    private void frozen(BufferedImage image, String shows, boolean read, boolean watched) {
        generation.incrementAndGet();
        liveButton.setSelected(false);
        frozen = image;
        frozenOn = shows;
        frozenRead = read;
        frozenWatched = watched;
        frozenAtMs = System.currentTimeMillis();
        frozenView.setImage(ScreenCapture.toFxImage(image));
        cutter.reset(image.getWidth(), image.getHeight());
        board.holdStill(false);
        on = null;
        lit.accept(shows);
        board.cover(this::drawFrozen, false);
        showLine();
    }

    /** Closes the frozen frame: the board shows the picked screen's shot again. */
    private void unfreeze() {
        frozen = null;
        frozenOn = null;
        frozenView.setImage(null);
        cutter.reset(0, 0);
        board.holdStill(false);
        lit.accept(null);
        board.cover(null, false);
        showLine();
    }

    private void retake() {
        String constant = frozenOn;
        BufferedImage image = frozen;
        PanelContext reads = context();
        Thread.ofVirtual().name("live-retake").start(() -> {
            String said = ScreenEdits.retakeShot(reads, constant, image);
            Platform.runLater(() -> done.accept(said));
        });
    }

    /**
     * A new screen of the frozen frame: under the screen it shows (a panel opened over it) or else the screen last
     * recognised, opened by the input last clicked there; its shot is this frame.
     */
    private void newScreen() {
        String parent = frozenOn != null ? frozenOn : lastKnown;
        String via = lastClick != null && parent != null && parent.equals(lastClick.screen()) ? lastClick.field() : null;
        BufferedImage image = frozen;
        hands.newScreen(parent, via, false, name -> ScreenDesk.find(ScreenDesk.read(services), name)
                .ifPresent(made -> Thread.ofVirtual().name("live-shot").start(() -> {
                    ScreenDesk.keepShot(services, made, image);
                    Platform.runLater(() -> select.accept(made));
                })));
    }

    /** The frames the latest run saved when a wait timed out, each opened frozen. */
    private List<MenuItem> missedItems() {
        Path project = services.projectDir();
        List<Path> frames = project == null ? List.of() : MissFrames.latest(project);
        if (frames.isEmpty()) {
            MenuItem none = new MenuItem("The latest run saved no frame: no wait timed out.");
            none.setDisable(true);
            return List.of(none);
        }
        return frames.stream().map(file -> {
            MenuItem item = new MenuItem(file.getFileName().toString());
            item.setOnAction(e -> openMissed(file));
            return item;
        }).toList();
    }

    private void openMissed(Path file) {
        if (liveButton.isSelected()) stop();
        PanelContext reads = context();
        Thread.ofVirtual().name("live-missed").start(() -> {
            BufferedImage image;
            try {
                image = ImageIO.read(file.toFile());
            } catch (IOException unreadable) {
                image = null;
            }
            if (image == null) {
                Platform.runLater(() -> done.accept(file.getFileName() + " could not be read."));
                return;
            }
            BufferedImage read = image;
            String shows = ScreenDesk.here(reads, read).map(ScreenDesk.Here::screen).orElse(null);
            Platform.runLater(() -> frozen(read, shows, true, false));
        });
    }

    // --- cutting on the frozen frame ---

    /**
     * ✂ from the panel, for {@code screen}: cuts on the frame frozen, else on the game frozen now, else on the
     * screen's shot; {@code marker} when the first cut is its marker.
     */
    void cut(String screen, boolean marker) {
        CutForm.Kind first = marker ? CutForm.Kind.MARKER : CutForm.Kind.BUTTON;
        if (frozen == null && liveButton.isSelected() && latest != null) freeze();
        if (frozen == null) {
            Optional<BufferedImage> shot = shot(screen);
            if (shot.isEmpty()) {
                done.accept("Nothing to cut on yet: ● Live, then ❄ Freeze while the game shows "
                        + graph.get().shown(screen) + ", then ✂ Cut. Or cut over the game window (✂ ▾).");
                return;
            }
            frozen(shot.get(), screen, true, false);
        }
        beginCut(screen, first);
    }

    /** {@code constant}'s shot, lossless; empty when it has none. */
    private Optional<BufferedImage> shot(String constant) {
        Path resources = services.resourcesDir();
        if (resources == null || constant == null || !graph.get().screens().containsKey(constant)) {
            return Optional.empty();
        }
        Path file = ScreenShots.file(resources, constant);
        try {
            return Files.isRegularFile(file) ? Optional.ofNullable(ImageIO.read(file.toFile())) : Optional.empty();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private void beginCut(String screen, CutForm.Kind first) {
        cutScreen = screen;
        cutFirst = first;
        cutter.setOn(true);
        board.holdStill(true);
        showLine();
    }

    private void endCut() {
        cutter.clear();
        cutter.setOn(false);
        board.holdStill(false);
        showLine();
    }

    /** The line while cutting: the shape, ▨ the part skipped, undo, save and stop. */
    private List<Node> cutTools() {
        ToggleGroup shapes = new ToggleGroup();
        List<Node> tools = new ArrayList<>();
        for (CutShape shape : CutShape.values()) {
            ToggleButton one = new ToggleButton(switch (shape) {
                case RECT -> "▢";
                case ELLIPSE -> "⬭";
                case POLYGON -> "✎";
            });
            one.setTooltip(new Tooltip(switch (shape) {
                case RECT -> "A box: drag it";
                case ELLIPSE -> "An oval, transparent outside: drag its box (Shift: a circle)";
                case POLYGON -> "A free shape, transparent outside: click each corner; a click on the first corner, a "
                        + "double-click, Enter or ✓ Close shape closes it, Backspace drops a corner";
            }));
            one.setToggleGroup(shapes);
            one.setSelected(cutter.shape() == shape);
            one.setOnAction(e -> {
                one.setSelected(true);
                cutter.setShape(shape);
            });
            tools.add(one);
        }
        ToggleButton ignore = new ToggleButton("▨ Skip a part");
        ignore.setTooltip(new Tooltip(MapGuide.CUT_IGNORE));
        ignore.setSelected(cutter.ignoring());
        ignore.setOnAction(e -> cutter.setIgnoring(ignore.isSelected()));
        tools.add(ignore);
        if (cutter.corners() > 0) {
            Button close = button("✓ Close shape", "Ends the free shape at its last corner (Enter)",
                    cutter::closeShape);
            close.setDisable(cutter.corners() < 3);
            tools.add(close);
            tools.add(hint(cutter.corners() < 3 ? "click its corners: at least 3"
                    : "click the first corner, or double-click, to close it"));
        } else if (cutter.hasPicked()) {
            tools.add(button("✕ Take it off", "Takes the picked cut off (Delete)", cutter::removePicked));
            tools.add(hint("drag it to move it, its corners to resize it"));
        } else if (!cutter.cuts().isEmpty()) {
            tools.add(hint(cutter.shape() == CutShape.POLYGON ? "click a cut's number to pick it"
                    : "click a cut to pick it"));
        }
        int drawn = cutter.cuts().size();
        Button undo = button("↶", "Take back the last cut", cutter::undo);
        undo.setDisable(drawn == 0);
        Button save = button(drawn <= 1 ? "✓ Save it…" : "✓ Save " + drawn + "…", MapGuide.CUT_SAVE, this::askCuts);
        save.setDisable(drawn == 0);
        save.setDefaultButton(false);
        tools.add(undo);
        tools.add(save);
        tools.add(button("✕ Stop cutting", "The cuts not saved are dropped", this::endCut));
        return tools;
    }

    /** Asks what the cuts are, then saves them as the tools do, off the FX thread. */
    private void askCuts() {
        List<CutLayer.Drawn> drawn = cutter.cuts();
        BufferedImage image = frozen;
        if (drawn.isEmpty() || image == null) return;
        String shows = cutScreen != null ? cutScreen : frozenOn;
        CutForm.ask(services, graph.get(), image, drawn, shows, cutFirst, answer -> {
            PanelContext reads = context();
            // Frozen a while ago, the game may show something else by now: the frame is then not watched.
            boolean watched = frozenWatched && System.currentTimeMillis() - frozenAtMs < WATCHED_MS;
            ScreenGraph map = graph.get();
            String where = answer.screen() == null ? "" : " on " + (map.screens().containsKey(answer.screen())
                    ? map.shown(answer.screen()) : answer.screen());
            boolean started = work.run("Saving " + answer.cuts().size() + (answer.cuts().size() == 1 ? " cut" : " cuts")
                    + where + "…", answer.screen(), () -> {
                BoardCuts.Saved saved = BoardCuts.save(reads, answer.screen(), answer.tags(), answer.cuts(), image,
                        watched);
                // A refused cut is kept on the board, to fix and ✓ Save again; a saved one is done with.
                Platform.runLater(() -> {
                    if (saved.anyRefused()) cutter.keepOnly(drawn, saved.refused());
                    else endCut();
                });
                return saved.said() + (saved.anyRefused() ? "\n" + (saved.refused().size() == 1 ? "The cut refused is"
                        : "The " + saved.refused().size() + " cuts refused are") + " kept: fix and ✓ Save again." : "");
            });
            if (!started) done.accept("A try or a walk is running: the cuts are kept; ✓ Save again once it ends.");
        }, done);
    }

    private PanelContext context() {
        if (context == null) context = new PanelContext(services, CaptureValue.current(services));
        return context;
    }

    private static Label hint(String text) {
        Label hint = new Label(text);
        hint.getStyleClass().add("screen-panel-path");
        return hint;
    }

    private static Button button(String text, String tip, Runnable action) {
        Button button = new Button(text);
        if (tip != null) button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> action.run());
        return button;
    }
}
