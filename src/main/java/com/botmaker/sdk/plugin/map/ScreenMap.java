package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.overlay.OverlayToolContext;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenStep;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
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
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

/**
 * 🗺 Screens: the bot's screen map, its {@code Screens} class, drawn as a graph — the SDK plugin's own window, and a
 * smaller copy in the overlay editor's tool pane. Studio only hosts it and names no screen type.
 *
 * <p>It follows the map as it changes: the Java is read again every {@link #POLL} and the cards drawn again only
 * when one changed, so a screen the assistant's try_input adds fades in while it works. During a run the screen
 * the bot's walk reached is lit, from the run's telemetry ({@link ScreenStep}).
 */
public final class ScreenMap {

    /** How often the map is read again while shown. */
    static final Duration POLL = Duration.seconds(1.5);

    /** The window open on each project, by its folder. On the FX thread. */
    private static final Map<Path, ScreenMap> OPEN = new HashMap<>();

    private final StudioServices services;
    private final ScreenCanvas canvas;
    private final ScreenPanel panel;
    private final BorderPane root = new BorderPane();
    private final Label said = new Label();
    private final Timeline poll;
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private Window window;
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

    /** The map with its side panel ({@code withPanel}) or without, for the overlay editor's narrow pane. */
    ScreenMap(StudioServices services, boolean withPanel) {
        this.services = services;
        project = services.projectDir();
        canvas = new ScreenCanvas(services.resourcesDir());
        panel = withPanel ? new ScreenPanel(services, canvas, () -> window, this::refresh) : null;
        root.getStyleClass().add("screen-map");
        root.getStylesheets().add(ScreenCanvas.SHEET);
        root.setTop(toolbar(withPanel));
        root.setCenter(canvas.graph());
        if (panel != null) root.setRight(panel.node());
        canvas.graph().selectedProperty().addListener((o, was, is) -> {
            if (is != null) selected = is.constant();
            else if (was != null) selected = null;
            // A screen picked, on the map or through an input that opens it: the panel shows its shot, inputs boxed.
            if (panel != null) panel.show(graph, selected);
        });
        if (panel != null) {
            canvas.setMenus(panel::menuFor, panel::linkMenu);
            canvas.graph().setOnEdgeClicked(panel::showLink);
        }
        poll = new Timeline(new KeyFrame(POLL, e -> refresh()));
        poll.setCycleCount(Timeline.INDEFINITE);
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
        if (project != null) OPEN.put(project, map);
        stage.setOnHidden(e -> {
            map.stop();
            if (project != null) OPEN.remove(project, map);
        });
        stage.show();
        map.start();
        // After start's first read, so the card is drawn to be picked.
        if (constant != null) Platform.runLater(() -> map.canvas.select(constant));
    }

    /** The overlay editor's tab: the map without its side, and a button opening the window. */
    public static Node pane(OverlayToolContext context) {
        ScreenMap map = new ScreenMap(context.services(), false);
        context.onClosed(map::stop);
        map.start();
        return map.node();
    }

    /** Reads the map, starts following it and the run's telemetry. */
    void start() {
        refresh();
        Platform.runLater(canvas.graph()::fit);
        poll.play();
        subscriptions.add(services.runs().onTelemetry(this::telemetry));
        // A run starting or ending leaves no screen known to be on the game.
        subscriptions.add(services.runs().onStateChanged(running -> Platform.runLater(() -> canvas.setHere(null))));
    }

    /** Stops following; what the window or pane does when it closes. */
    void stop() {
        poll.stop();
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
    }

    /** Reads the map again and draws what changed. On the FX thread. */
    void refresh() {
        if (!Objects.equals(project, services.projectDir())) {
            // Another project is open now: this map is of the one that closed.
            stop();
            // The window stays as it was, but is no longer the one its project opens again.
            OPEN.remove(project, this);
            if (panel != null) panel.closed();
            said.setText("The project this map was of is closed.");
            return;
        }
        ScreenGraph read;
        Map<String, String> stages;
        try {
            read = ScreenDesk.read(services);
            stages = ScreenDesk.stages(read);
        } catch (RuntimeException unreadable) {
            said.setText("The map could not be read: "
                    + Objects.requireNonNullElse(unreadable.getMessage(), unreadable.toString()));
            return;
        }
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
        if (!saidOnce) said.setText(summary(nowCards));
        saidOnce = false;
        if (selected != null && !read.screens().containsKey(selected)) selected = null;
        if (panel != null) panel.show(read, selected);
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

    private Node toolbar(boolean full) {
        TextField search = canvas.graph().searchField(card -> card.name() + " " + card.constant());
        search.setPromptText("Find a screen…");
        search.setPrefColumnCount(full ? 16 : 10);
        CheckBox others = new CheckBox("Other ways");
        others.setSelected(true);
        others.setTooltip(new Tooltip("Show the dashed arrows: other ways into a screen, and ways back"));
        others.selectedProperty().addListener((o, was, is) -> canvas.graph().setShowDashed(is));
        Button fit = new Button("Fit");
        fit.setTooltip(new Tooltip("Zoom to show the whole map"));
        fit.setOnAction(e -> canvas.graph().fit());
        Button out = new Button("−");
        out.setTooltip(new Tooltip("Zoom out (the wheel zooms about the pointer; a right drag pans)"));
        out.setOnAction(e -> canvas.graph().zoomOut());
        Button in = new Button("+");
        in.setTooltip(new Tooltip("Zoom in"));
        in.setOnAction(e -> canvas.graph().zoomIn());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        said.getStyleClass().add("screen-panel-path");
        HBox bar = new HBox(8, search, others, out, fit, in);
        if (full) {
            Button png = new Button("⤓ PNG");
            png.setTooltip(new Tooltip("Save the whole map as a picture, at full size"));
            png.setOnAction(e -> savePng());
            bar.getChildren().add(png);
        } else {
            Button open = new Button("🗺 Open");
            open.setTooltip(new Tooltip("Open 🗺 Screens, with each screen's inputs and actions"));
            open.setOnAction(e -> open(() -> services));
            bar.getChildren().add(open);
        }
        bar.getChildren().addAll(gap, said);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 8, 10));
        bar.getStyleClass().add("screen-map-bar");
        return bar;
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
