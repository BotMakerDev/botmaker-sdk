package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.overlay.OverlayToolContext;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.graph.GraphCanvas;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenStep;
import com.botmaker.sdk.plugin.HistoryKeys;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.pictures.ScreenMapRead;
import com.botmaker.shared.ipc.TelemetryEvent;
import com.botmaker.shared.ipc.TelemetryFrame;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

/**
 * 🗺 Screens: the bot's screen map, its {@code Screens} class, drawn as a graph — the SDK plugin's own window, and a
 * smaller copy in the overlay editor's tool pane. Studio only hosts it and names no screen type.
 *
 * <p>It follows the map as it changes: the Java is read again every {@link #POLL} that {@code Screens.java} was
 * written ({@link ScreensFile}) and the cards drawn again only when one changed, so a screen the assistant's try_input adds fades in while it works. During a run the screen
 * the bot's walk reached is lit, from the run's telemetry ({@link ScreenStep}).
 */
public final class ScreenMap {

    /** How often the map is read again while shown. */
    static final Duration POLL = Duration.seconds(1.5);
    /** The polls after which the map is read again even with {@code Screens.java} unchanged, about 30 s. */
    private static final int READ_ANYWAY = 20;
    /** A search hit's marker. */
    private static final double HIT_THUMB = 26;
    /** The outline's share of the window's width, beside the centre. */
    private static final double OUTLINE_SHARE = 0.26;
    /** Whether the outline and the panel show: per viewer, not per project. */
    private static final Preferences PREFS = Preferences.userNodeForPackage(ScreenMap.class);
    private static final String OUTLINE_KEY = "map.outline";
    private static final String PANEL_KEY = "map.panel";
    private static final String ALL_LINKS_KEY = "map.allLinks";

    /** The window open on each project, by its folder. On the FX thread. */
    private static final Map<Path, ScreenMap> OPEN = new HashMap<>();

    private final StudioServices services;
    private final ScreenCanvas canvas;
    private final ScreenPanel panel;
    private final ScreenOutline outline;
    /** The full window's centre: the graph, or the picked screen big. */
    private final TabPane tabs = new TabPane();
    private final Tab screenTab = new Tab("Screen");
    private final BorderPane root = new BorderPane();
    private final Label said = new Label();
    /** Studio's own ↶ and ↷, in the full window: a map edit and a code edit are one history. */
    private final Button undo = new Button("↶");
    private final Button redo = new Button("↷");
    /** Whether the outline and the side panel show, in the full window; remembered on this computer. */
    private final ToggleButton outlineShown = new ToggleButton("☰ Outline");
    private final ToggleButton panelShown = new ToggleButton("▤ Panel");
    /** The outline beside the centre; null in the overlay's pane. */
    private SplitPane split;
    /** The outline's share of the window's width, as last shown. */
    private double outlineShare = OUTLINE_SHARE;
    private final Timeline poll;
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private Window window;
    private Stage guide;
    private ScreenGraph graph = ScreenGraph.of(Map.of());
    private List<Card> cards;
    private List<Link> links;
    private String selected;
    /** The project this map is of: another one open stops it. */
    private final Path project;
    /** Set when a message is shown that the next read's summary must not replace at once. */
    private boolean saidOnce;
    /** Set when a read found no screen while some were drawn: the next one says whether the map is really empty. */
    private boolean emptyOnce;
    /** Set while a read runs off the FX thread. */
    private boolean reading;
    /** Set once the window or pane closed: a read still running draws nothing. */
    private boolean stopped;
    /** Each read asked, counted: a read off the FX thread that another overtook draws nothing. */
    private long reads;
    /** {@code Screens.java} as the last poll's read found it; null until one drew. A poll reads only when it moved. */
    private ScreensFile lastFile;
    /** Polls since the last read: every {@link #READ_ANYWAY}th reads whatever the file says. */
    private int pollsSinceRead;
    /** What the first drawing with cards does, once: fit the map and pick the screen asked for. Null after. */
    private Runnable firstDrawn;

    /** The map with its side panel ({@code withPanel}) or without, for the overlay editor's narrow pane. */
    ScreenMap(StudioServices services, boolean withPanel) {
        this.services = services;
        project = services.projectDir();
        canvas = new ScreenCanvas(services.resourcesDir());
        panel = withPanel ? new ScreenPanel(services, canvas, () -> window, this::refresh,
                () -> tabs.getSelectionModel().select(screenTab), this::inputPicked, this::liveOn) : null;
        root.getStyleClass().add("screen-map");
        root.getStylesheets().add(ScreenCanvas.SHEET);
        root.setTop(toolbar(withPanel));
        // The full window's outline beside the graph and the picked screen big; the overlay's narrow pane keeps the
        // graph alone. A line picked shows its screen big, and its input picked on it.
        outline = withPanel ? new ScreenOutline((constant, field) -> {
            canvas.select(constant);
            panel.pickInput(field);
            tabs.getSelectionModel().select(screenTab);
        }, panel::menuFor, panel::rowMenuFor, canvas::shotTime) : null;
        if (outline != null) {
            Tab graphTab = new Tab("Graph", canvas.graph());
            screenTab.setContent(panel.board());
            for (Tab tab : List.of(graphTab, screenTab)) tab.setClosable(false);
            tabs.getTabs().setAll(graphTab, screenTab);
            screenTab.selectedProperty().addListener((o, was, is) -> {
                if (!is) panel.boardHidden();
            });
            split = new SplitPane(outline.node(), tabs);
            split.setDividerPositions(OUTLINE_SHARE);
            SplitPane.setResizableWithParent(outline.node(), false);
            root.setCenter(split);
            remembered(outlineShown, OUTLINE_KEY, true, this::showOutline);
        } else {
            root.setCenter(canvas.graph());
        }
        if (panel != null) remembered(panelShown, PANEL_KEY, true, is -> root.setRight(is ? panel.node() : null));
        canvas.graph().selectedProperty().addListener((o, was, is) -> {
            if (is != null) selected = is.constant();
            else if (was != null) selected = null;
            // A screen picked, on the map or through an input that opens it: the panel shows its shot, inputs boxed.
            if (panel != null) panel.show(graph, selected);
            if (outline != null) outline.select(selected);
        });
        if (panel != null) {
            canvas.setMenus(panel::menuFor, panel::linkMenu);
            canvas.graph().setOnEdgeClicked(panel::showLink);
        }
        // A card moved by hand keeps its corner in Screens.java, read back off the FX thread: the canvas has it where
        // it was dropped meanwhile. Refused, the map is drawn again as the Java has it.
        canvas.setOnMoved(corners -> {
            String result = ScreenEdits.move(services, corners);
            if (result.startsWith(ScreenEdits.NOT_WRITTEN)) canvas.redraw();
            else readLater(true);
            tell(result);
        });
        poll = new Timeline(new KeyFrame(POLL, e -> readLater(false)));
        poll.setCycleCount(Timeline.INDEFINITE);
    }

    /** {@code toggle} shown as this computer last left it, and each change applied and kept. */
    private static void remembered(ToggleButton toggle, String key, boolean fallback, Consumer<Boolean> apply) {
        toggle.setSelected(PREFS.getBoolean(key, fallback));
        apply.accept(toggle.isSelected());
        toggle.selectedProperty().addListener((o, was, is) -> {
            PREFS.putBoolean(key, is);
            apply.accept(is);
        });
    }

    /** The outline beside the centre, or the centre alone. */
    private void showOutline(boolean shown) {
        boolean there = split.getItems().contains(outline.node());
        if (shown && !there) {
            split.getItems().addFirst(outline.node());
            split.setDividerPositions(outlineShare);
        } else if (!shown && there) {
            // The width the user dragged it to comes back with it.
            outlineShare = split.getDividerPositions()[0];
            split.getItems().remove(outline.node());
        }
    }

    /** The window full screen, or back: ⛶ and F11. */
    private void fullScreen() {
        if (window instanceof Stage stage) stage.setFullScreen(!stage.isFullScreen());
    }

    /** An input picked on the board or in the panel, its line picked in the outline; null {@code field}: none. */
    private void inputPicked(String constant, String field) {
        if (outline != null) outline.selectInput(constant, field);
    }

    /** The screen the live board sees the game on, lit on the graph and in the outline; null lights none. */
    private void liveOn(String constant) {
        canvas.setHere(constant);
        if (outline != null) outline.setHere(constant);
    }

    /**
     * One step of Studio's history back ({@code back}) or again — whoever made it — then the map read at once, so
     * a card's corner or a renamed screen shows as the Java has it now.
     */
    private void step(boolean back) {
        History history = services.history();
        if (back ? !history.canUndo() : !history.canRedo()) return;
        if (panel != null && panel.busy()) {
            tell("A try runs: undo once it ends.");
            return;
        }
        String label = back ? history.undoLabel() : history.redoLabel();
        if (back) history.undo();
        else history.redo();
        refresh();
        tell((back ? "Took back: " : "Did again: ") + label + ".");
    }

    /** ↶ and ↷ named for the step each takes. On the FX thread. */
    private void showHistory() {
        History history = services.history();
        undo.setDisable(!history.canUndo());
        redo.setDisable(!history.canRedo());
        undo.setTooltip(new Tooltip(history.canUndo() ? "Undo: " + history.undoLabel() + " (Ctrl+Z)" : "Nothing to undo"));
        redo.setTooltip(new Tooltip(history.canRedo() ? "Redo: " + history.redoLabel() + " (Ctrl+Y)" : "Nothing to redo"));
    }

    Node node() {
        return root;
    }

    /** The 🗺 Screens press: the map in a window of its own, beside the editor. */
    public static void open(ActionContext context) {
        open(context.services(), null);
    }

    /**
     * The map in its window with {@code constant} picked: a {@code Screens} constant in the editor asked for it. A
     * project has one such window: asked again, the open one comes to the front.
     */
    public static void open(StudioServices services, String constant) {
        Path project = services.projectDir();
        ScreenMap open = project == null ? null : OPEN.get(project);
        if (open != null && open.window instanceof Stage shown) {
            shown.toFront();
            shown.requestFocus();
            if (constant != null) open.canvas.select(constant);
            return;
        }
        ScreenMap map = new ScreenMap(services, true);
        Stage stage = Modals.window(services, Modals.owner(services),
                Modals.Frame.modeless("Screens", 1180, 720, 760, 460), (BorderPane) map.node());
        map.window = stage;
        // On the window, so the keys work wherever its focus is; the overlay's pane leaves them to Studio's.
        stage.addEventHandler(KeyEvent.KEY_PRESSED, key -> HistoryKeys.handle(key, map::step));
        stage.addEventHandler(KeyEvent.KEY_PRESSED, key -> {
            if (key.getCode() != KeyCode.F11) return;
            key.consume();
            map.fullScreen();
        });
        stage.setFullScreenExitHint("F11 or Esc leaves full screen");
        if (project != null) OPEN.put(project, map);
        stage.setOnHidden(e -> {
            map.stop();
            if (project != null) OPEN.remove(project, map);
        });
        stage.show();
        map.start(constant);
    }

    /** The overlay editor's tab: the map without its side, and a button opening the window. */
    public static Node pane(OverlayToolContext context) {
        ScreenMap map = new ScreenMap(context.services(), false);
        context.onClosed(map::stop);
        map.start(null);
        return map.node();
    }

    /**
     * Reads the map off the FX thread, so the window shows at once, then fits it and picks {@code constant} (null:
     * none); starts following the map and the run's telemetry.
     */
    void start(String constant) {
        firstDrawn = () -> {
            canvas.graph().fit();
            if (constant != null) canvas.select(constant);
        };
        readLater(false);
        poll.play();
        subscriptions.add(services.runs().onTelemetry(this::telemetry));
        // A run starting or ending leaves no screen known to be on the game.
        subscriptions.add(services.runs().onStateChanged(running -> Platform.runLater(() -> canvas.setHere(null))));
        if (panel != null) {
            showHistory();
            subscriptions.add(services.history().onChanged(() -> Platform.runLater(() -> {
                showHistory();
                panel.historyChanged();
            })));
        }
    }

    /** Stops following; what the window or pane does when it closes. */
    void stop() {
        stopped = true;
        poll.stop();
        if (panel != null) panel.stopLive();
        for (AutoCloseable subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // the host has let go of it already
            }
        }
        subscriptions.clear();
        canvas.setTrying(null);
        if (panel != null) panel.close();
        if (guide != null) guide.close();
    }

    /** The legend of the map's signs, one window: asked again, it comes to the front. */
    private void showGuide(Node from) {
        if (guide != null) {
            guide.setIconified(false);
            guide.toFront();
            return;
        }
        guide = MapGuide.open(services, from.getScene() == null ? null : from.getScene().getWindow());
        guide.setOnHidden(e -> guide = null);
        guide.show();
    }

    /** The map as one read found it, or why it could not be read ({@code failed} non-null). */
    private record Read(ScreenGraph graph, Map<String, String> stages, String failed) {}

    private Read read() {
        try {
            ScreenGraph read = ScreenDesk.read(services);
            return new Read(read, ScreenDesk.stages(read), null);
        } catch (RuntimeException unreadable) {
            return new Read(null, null, Objects.requireNonNullElse(unreadable.getMessage(), unreadable.toString()));
        }
    }

    /**
     * Reads the map again and draws what changed, at once: after the user's own edit, so the next drag or redraw
     * starts from it. On the FX thread.
     */
    void refresh() {
        reads++;
        // The reads still out are older than this one, and dropped: the next poll may read.
        reading = false;
        if (stillOpen()) apply(read());
    }

    /**
     * Reads the map on a thread of its own, as the poll does, then draws what changed on the FX thread. One read at a
     * time; one overtaken by {@link #refresh} or an edit's read is dropped, being older than what it drew.
     *
     * @param edited after the user's own edit, whose result the canvas shows already (a card dropped): read whatever
     *               the file says, overtaking a poll's read, and leave the FX thread free meanwhile (2026-10-22: a
     *               drop's read held it 300 to 900 ms)
     */
    private void readLater(boolean edited) {
        if (reading && !edited || !stillOpen()) return;
        reading = true;
        long asked = ++reads;
        ScreensFile was = lastFile;
        boolean due = edited || ++pollsSinceRead >= READ_ANYWAY;
        // Asked of what is drawn now, answered off the FX thread: stating every shot and picture held it each poll.
        BooleanSupplier cardFiles = canvas.filesCheck();
        Supplier<ScreenPanel.Written> panelFiles = panel == null ? () -> null : panel.filesCheck();
        Thread.ofVirtual().name("screen-map-read").start(() -> {
            try {
                readOff(asked, was, due, cardFiles, panelFiles);
            } catch (RuntimeException lost) {
                // Lost before it could answer (a file that could not be stated): the next poll reads again.
                Platform.runLater(() -> {
                    if (asked == reads) reading = false;
                });
            }
        });
    }

    /** {@link #readLater}'s read, off the FX thread: {@code asked} is its number, drawn only while still the latest. */
    private void readOff(long asked, ScreensFile was, boolean due, BooleanSupplier cardFiles,
                         Supplier<ScreenPanel.Written> panelFiles) {
        Optional<ScreensFile> file = ScreensFile.of(project, was == null ? null : was.file());
        if (!due && file.isPresent() && file.get().equals(was)) {
            // Screens.java as it was: only a picture or shot rewritten since is drawn again.
            boolean filesChanged = cardFiles.getAsBoolean();
            ScreenPanel.Written seen = panelFiles.get();
            Platform.runLater(() -> {
                if (asked != reads) return;
                reading = false;
                if (stopped || !stillOpen()) return;
                if (filesChanged) {
                    canvas.redraw();
                    // A shot retaken moves its screen to the top of Recent.
                    if (outline != null) outline.show(graph, cards, links);
                }
                if (panel != null) panel.filesSeen(seen);
            });
            return;
        }
        // Changed on disk, as by a hand outside Studio: the read kept is of the class as it was.
        if (file.isEmpty() || !file.get().equals(was)) ScreenMapRead.forget();
        Read read;
        try {
            read = read();
        } catch (Throwable broken) {
            // A half-built plugin jar fails with an Error, not an exception: said, and the next poll reads again.
            read = new Read(null, null, Objects.requireNonNullElse(broken.getMessage(), broken.toString()));
        }
        Read done = read;
        Platform.runLater(() -> {
            // Only the latest read frees the next: an older one ending early would let a poll start beside it.
            if (asked != reads) return;
            reading = false;
            if (stopped || !stillOpen()) return;
            apply(done);
            // Kept only for a read that drew: a failed one, or an empty one the last drawing stayed over, is read
            // again at the next poll.
            if (done.failed() == null && !emptyOnce) {
                lastFile = file.orElse(null);
                pollsSinceRead = 0;
            }
        });
    }

    /** Whether the project this map is of is still open; when not, the map stops following it. On the FX thread. */
    private boolean stillOpen() {
        if (Objects.equals(project, services.projectDir())) return true;
        // Another project is open now: this map is of the one that closed.
        stop();
        // The window stays as it was, but is no longer the one its project opens again.
        OPEN.remove(project, this);
        if (panel != null) panel.closed();
        said.setText("The project this map was of is closed.");
        return false;
    }

    private void apply(Read map) {
        if (map.failed() != null) {
            said.setText("The map could not be read: " + map.failed());
            return;
        }
        ScreenGraph read = map.graph();
        Map<String, String> stages = map.stages();
        if (read.screens().isEmpty() && cards != null && !cards.isEmpty() && !emptyOnce) {
            // Read while Screens.java was being written, the canvas went blank for a poll (2026-10-15): the last
            // drawing stays for one read. Empty twice running, it is empty: its last screen was deleted.
            emptyOnce = true;
            return;
        }
        emptyOnce = false;
        graph = read;
        List<Card> nowCards = ScreenCards.cards(read, stages);
        List<Link> nowLinks = ScreenCards.links(read);
        if (!nowCards.equals(cards) || !nowLinks.equals(links)) {
            cards = nowCards;
            links = nowLinks;
            canvas.show(cards, links, ScreenCards.root(read).orElse(null));
        } else if (canvas.filesChanged()) {
            canvas.redraw();
        }
        // Its lines hold each input's row, which an edit changes with no card or link changing; it redraws on a change.
        if (outline != null) outline.show(read, cards, links);
        if (!saidOnce) said.setText(summary(nowCards));
        saidOnce = false;
        if (selected != null && !read.screens().containsKey(selected)) selected = null;
        if (panel != null) panel.show(read, selected);
        if (firstDrawn != null && !cards.isEmpty()) {
            // Once there are cards to fit and pick: a first read that failed or found none leaves it to the next.
            Runnable first = firstDrawn;
            firstDrawn = null;
            first.run();
        }
    }

    private static String summary(List<Card> cards) {
        long finished = cards.stream().filter(c -> c.state() == ScreenCards.State.FINISHED).count();
        return cards.isEmpty() ? "No screen yet." : cards.size() + (cards.size() == 1 ? " screen" : " screens") + ", "
                + finished + " finished";
    }

    /** A run's frame, off the FX thread: a screen its walk reached lights that screen. */
    private void telemetry(byte[] frame) {
        TelemetryEvent event;
        try {
            event = TelemetryFrame.decode(frame);
        } catch (TelemetryFrame.FrameFormatException unreadable) {
            return;
        }
        if (!(event instanceof TelemetryEvent.Step step)) return;
        ScreenStep.screen(step.action()).ifPresent(shown -> Platform.runLater(() -> {
            if (!graph.screens().containsKey(shown.constant())) return;
            canvas.setHere(shown.constant());
            // Followed only while the user looks at no screen of their own.
            if (selected == null) canvas.centreOn(shown.constant());
        }));
    }

    /** A search hit's row: the screen's marker and name, and what matched when that was not its name. */
    private Node hitRow(GraphCanvas.Hit<Card> hit) {
        Label name = new Label(hit.node().name());
        HBox row = new HBox(8, canvas.thumb(hit.node().marker(), HIT_THUMB, "screen-row-thumb"), name);
        row.setAlignment(Pos.CENTER_LEFT);
        if (!hit.matched().isEmpty()) {
            Label matched = new Label(hit.matched());
            matched.getStyleClass().add("screen-panel-path");
            row.getChildren().add(matched);
        }
        return row;
    }

    private Node toolbar(boolean full) {
        // Found by more than its name, read off the map the canvas draws: a hit picked zooms to its card.
        TextField search = canvas.graph().searchField(card -> ScreenCards.terms(graph, card.constant()), this::hitRow);
        search.setPromptText("Find a screen…");
        search.setPrefColumnCount(full ? 16 : 10);
        CheckBox others = new CheckBox("Other ways");
        others.setSelected(true);
        others.setTooltip(new Tooltip(MapGuide.OTHER_WAYS));
        others.selectedProperty().addListener((o, was, is) -> canvas.graph().setShowDashed(is));
        // Faint until a screen is focused, unless every link is asked for: a whole map of arrows hid the work.
        ToggleButton all = new ToggleButton("⇄ All links");
        all.setTooltip(new Tooltip(MapGuide.ALL_LINKS));
        remembered(all, ALL_LINKS_KEY, false, is -> canvas.graph().setFocusLinks(!is));
        Button fit = new Button("Fit");
        fit.setTooltip(new Tooltip(MapGuide.FIT));
        fit.setOnAction(e -> canvas.graph().fit());
        Button out = new Button("−");
        out.setTooltip(new Tooltip(MapGuide.ZOOM_OUT));
        out.setOnAction(e -> canvas.graph().zoomOut());
        Button in = new Button("+");
        in.setTooltip(new Tooltip(MapGuide.ZOOM_IN));
        in.setOnAction(e -> canvas.graph().zoomIn());
        Button help = new Button("?");
        help.setTooltip(new Tooltip(MapGuide.GUIDE));
        help.setOnAction(e -> showGuide(help));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        said.getStyleClass().add("screen-panel-path");
        HBox bar = new HBox(8, search, others, all, out, fit, in);
        if (full) {
            undo.setOnAction(e -> step(true));
            redo.setOnAction(e -> step(false));
            bar.getChildren().addAll(undo, redo);
            Button tidy = new Button("Tidy");
            tidy.setTooltip(new Tooltip(MapGuide.TIDY));
            tidy.setOnAction(e -> tidy());
            Button png = new Button("⤓ PNG");
            png.setTooltip(new Tooltip(MapGuide.PNG));
            png.setOnAction(e -> savePng());
            outlineShown.setTooltip(new Tooltip(MapGuide.OUTLINE));
            panelShown.setTooltip(new Tooltip(MapGuide.PANEL));
            Button whole = new Button("⛶");
            whole.setTooltip(new Tooltip(MapGuide.FULL_SCREEN));
            whole.setOnAction(e -> fullScreen());
            bar.getChildren().addAll(tidy, png, outlineShown, panelShown, whole);
        } else {
            Button open = new Button("🗺 Open");
            open.setTooltip(new Tooltip(MapGuide.OPEN));
            open.setOnAction(e -> open(() -> services));
            bar.getChildren().add(open);
        }
        bar.getChildren().addAll(help, gap, said);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 8, 10));
        bar.getStyleClass().add("screen-map-bar");
        return bar;
    }

    /** Tidy, after asking: no card keeps a corner of its own. */
    private void tidy() {
        if (cards == null || cards.stream().allMatch(card -> card.at() == null)) {
            tell("No card was moved: each is where the layout puts it.");
            return;
        }
        Label question = new Label("Put every card back where the layout puts it? The places you moved them to are "
                + "taken out of Screens.java.");
        question.setWrapText(true);
        Modals.form(services, "Tidy the map", new VBox(question), () -> {
            tell(ScreenEdits.tidy(services));
            refresh();
        });
    }

    /** {@code text} in the toolbar, kept over the next read's summary. */
    private void tell(String text) {
        said.setText(text);
        saidOnce = true;
    }

    private void savePng() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save the screen map");
        chooser.setInitialFileName("screen-map.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG picture", "*.png"));
        File file = chooser.showSaveDialog(window);
        if (file == null) return;
        try {
            canvas.graph().savePng(file.toPath());
            said.setText("Saved " + file.getName() + ".");
        } catch (IOException failed) {
            said.setText("Not saved: " + Objects.requireNonNullElse(failed.getMessage(), failed.toString()));
        }
        saidOnce = true;
    }
}
