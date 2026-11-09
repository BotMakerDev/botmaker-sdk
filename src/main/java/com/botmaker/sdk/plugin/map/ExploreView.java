package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.plugin.api.source.RunParameter;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.sdk.plugin.run.SdkRuns;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 🔭 Explore, the view 🗺 Screens' rail shows (2026-11-09, spec screens-rail goal 9; continuous-explore spec §7,
 * layout A): a run exploring the game, watched and started from here. A status line; on the left <em>Now</em>, the
 * game's frame with its readouts' areas drawn as the live board draws them, the screen's name and the facts it reads;
 * on the right the feed, newest first, with the stop the run waits on pinned above it and the last tool Claude called.
 *
 * <p>The journal ({@link Journal}, {@code events.jsonl}) and, while a run goes, a frame of the game are read once a
 * second off the FX thread, only while the view is shown. <b>Start</b> starts <em>Keep exploring</em> (its menu:
 * <em>Explore map</em>) through {@link com.botmaker.plugin.api.Runs#start(RunEntry)}, while no run goes; <b>Stop</b>
 * ends the run going.
 */
final class ExploreView {

    /** How often the journal and the frame are read again. */
    private static final Duration TICK = Duration.seconds(1);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final StudioServices services;
    private final Supplier<ScreenGraph> graph;
    /** Null while no project is open: nothing is read. */
    private final Journal journal;
    private final LongSupplier nowMs;

    private final BorderPane root = new BorderPane();
    private final Label status = new Label();
    private final SplitMenuButton start = new SplitMenuButton();
    private final Button stop = new Button("■ Stop");
    private final ImageView frame = new ImageView();
    private final Pane boxes = new Pane();
    private final Label screenName = new Label();
    private final VBox facts = new VBox(2);
    private final Label pinnedStop = new Label();
    private final Label pinnedClaude = new Label();
    private final VBox pinned = new VBox(2, pinnedStop, pinnedClaude);
    private final ListView<String> feed = new ListView<>();
    private final Timeline ticker = new Timeline(new KeyFrame(TICK, e -> tick()));
    private final AtomicBoolean reading = new AtomicBoolean();
    private final AutoCloseable runState;

    // On the FX thread from here.
    /** The screen the last frame read shows, by its markers; null for none, or no frame read. */
    private String liveScreen;
    private int frameWidth;
    private Map<String, Rect> areas = Map.of();
    private boolean closed;
    /** The journal as last read; read again only once its files changed. */
    private JournalView last = new JournalView(List.of(), List.of(), List.of());
    /** The journal files' sizes and times when {@link #last} was read; the reading thread's, one at a time. */
    private volatile String lastStamp = "";

    ExploreView(StudioServices services, Supplier<ScreenGraph> graph, Journal journal, LongSupplier nowMs) {
        this.services = services;
        this.graph = graph;
        this.journal = journal;
        this.nowMs = nowMs;
        root.getStylesheets().add(ScreenCanvas.SHEET);
        root.getStyleClass().add("explore-view");

        start.setText("▶ " + SdkRuns.KEEP_EXPLORING);
        start.setTooltip(new Tooltip("Starts a run that explores the game until stopped, and stops for Claude on what "
                + "the bot does not know. ▾ for a single pass over the map."));
        start.setOnAction(e -> startRun(SdkRuns.KEEP_EXPLORING));
        MenuItem keep = new MenuItem(SdkRuns.KEEP_EXPLORING);
        keep.setOnAction(e -> startRun(SdkRuns.KEEP_EXPLORING));
        MenuItem once = new MenuItem(SdkRuns.EXPLORE_MAP);
        once.setOnAction(e -> startRun(SdkRuns.EXPLORE_MAP));
        start.getItems().setAll(keep, once);
        stop.setTooltip(new Tooltip("Stops the run going."));
        stop.setOnAction(e -> services.runs().stop());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        status.getStyleClass().add("explore-status");
        status.setMinWidth(0);
        HBox bar = new HBox(8, status, gap, start, stop);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 8, 6, 8));
        bar.getStyleClass().add("explore-bar");
        root.setTop(bar);

        frame.setPreserveRatio(true);
        frame.setFitWidth(420);
        boxes.setMouseTransparent(true);
        StackPane game = new StackPane(frame, boxes);
        game.setAlignment(Pos.TOP_LEFT);
        game.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label nowTitle = new Label("Now");
        nowTitle.getStyleClass().add("explore-heading");
        screenName.getStyleClass().add("explore-screen");
        VBox now = new VBox(6, nowTitle, game, screenName, facts);
        now.setPadding(new Insets(8));
        now.setMinWidth(0);
        // The frame follows the column's width.
        now.widthProperty().addListener((o, was, is) -> {
            frame.setFitWidth(Math.max(120, is.doubleValue() - 16));
            drawAreas();
        });

        pinned.getStyleClass().add("explore-pinned");
        pinned.setPadding(new Insets(6));
        pinnedStop.setWrapText(true);
        pinnedClaude.setWrapText(true);
        pinnedClaude.getStyleClass().add("explore-claude");
        feed.setPlaceholder(new Label("Nothing yet: ▶ " + SdkRuns.KEEP_EXPLORING + " explores the game, and what it finds shows here."));
        Label feedTitle = new Label("Feed");
        feedTitle.getStyleClass().add("explore-heading");
        VBox right = new VBox(6, feedTitle, pinned, feed);
        VBox.setVgrow(feed, Priority.ALWAYS);
        right.setPadding(new Insets(8));
        right.setMinWidth(0);

        SplitPane split = new SplitPane(now, right);
        split.setDividerPositions(0.45);
        root.setCenter(split);

        runState = services.runs().onStateChanged(running -> Platform.runLater(this::buttons));
        ticker.setCycleCount(Timeline.INDEFINITE);
        root.sceneProperty().addListener((o, was, is) -> {
            if (is != null && !closed) {
                ticker.play();
                tick();
            } else {
                ticker.stop();
            }
        });
        show(new JournalView(List.of(), List.of(), List.of()));
    }

    /** The view of the bot open in {@code services}; {@code onClosed} is handed what ends it when the window closes. */
    static Node view(StudioServices services, Supplier<ScreenGraph> graph, Consumer<Runnable> onClosed) {
        Journal journal = services.projectDir() == null ? null : Journal.of(services.projectDir());
        ExploreView view = new ExploreView(services, graph, journal, System::currentTimeMillis);
        onClosed.accept(view::close);
        return view.node();
    }

    Node node() {
        return root;
    }

    /** Nothing here reads any more: the window or the project closed. */
    void close() {
        closed = true;
        ticker.stop();
        try {
            runState.close();
        } catch (Exception ignored) {
            // the host has let go of it already
        }
    }

    private void startRun(String title) {
        if (services.runs().isRunning()) {
            services.status("A run is going: stop it first.");
            return;
        }
        Optional<RunEntry> entry = new SdkRuns().runs().stream().filter(r -> r.title().equals(title)).findFirst();
        entry.ifPresent(services.runs()::start);
    }

    private void buttons() {
        boolean running = services.runs().isRunning();
        start.setDisable(running || journal == null);
        // Only an explore run's: the bot's own run is stopped where it was started.
        stop.setDisable(!running || !last.running());
    }

    /** Reads the journal, and a frame while a run goes, off the FX thread; one read at a time. */
    private void tick() {
        buttons();
        if (journal == null || closed || !reading.compareAndSet(false, true)) return;
        ScreenGraph map = graph.get();
        PanelContext context = services.runs().isRunning() ? context() : null;
        if (context == null) forgetFrame();
        JournalView before = last;
        Thread.ofVirtual().name("explore-view").start(() -> {
            try {
                // A run of hours writes thousands of lines: parsed again only when the files changed.
                String stamp = stamp(journal);
                JournalView read = stamp.equals(lastStamp) ? before : JournalView.of(journal);
                lastStamp = stamp;
                Seen seen = context == null ? null : look(context, map);
                Platform.runLater(() -> {
                    if (closed) return;
                    if (seen != null) shown(seen);
                    show(read);
                });
            } finally {
                reading.set(false);
            }
        });
    }

    /** The journal's files as their sizes and last changes: equal while neither was written. */
    private static String stamp(Journal journal) {
        StringBuilder stamp = new StringBuilder();
        for (String name : List.of(Journal.EVENTS, Journal.QUEUE)) {
            Path file = journal.folder().resolve(name);
            try {
                stamp.append(Files.size(file)).append('@').append(Files.getLastModifiedTime(file).toMillis());
            } catch (IOException missing) {
                stamp.append('-');
            }
            stamp.append(' ');
        }
        return stamp.toString();
    }

    /** No run goes: the screen a frame showed is not where the game is any more. The frame stays, as last seen. */
    private void forgetFrame() {
        if (liveScreen == null && areas.isEmpty()) return;
        liveScreen = null;
        areas = Map.of();
        drawAreas();
    }

    private PanelContext context() {
        try {
            return services.pluginValues() == null ? null : new PanelContext(services, CaptureValue.current(services));
        } catch (RuntimeException unread) {
            return null;
        }
    }

    /** A frame of the game, the screen it shows and where that screen's readouts read on it. */
    private record Seen(BufferedImage image, String screen, Map<String, Rect> areas) {}

    private static Seen look(PanelContext context, ScreenGraph map) {
        try {
            PanelContext found = context.found();
            BufferedImage image = ScreenDesk.look(found).orElse(null);
            if (image == null) return null;
            String screen = ScreenDesk.here(found, image).map(ScreenDesk.Here::screen).orElse(null);
            Map<String, Rect> placed;
            try {
                placed = ScreenDesk.readoutAreas(found, map, screen, image);
            } catch (RuntimeException unplaced) {
                placed = Map.of();
            }
            return new Seen(image, screen, placed);
        } catch (RuntimeException unread) {
            return null;
        }
    }

    private void shown(Seen seen) {
        frame.setImage(ScreenCapture.toFxImage(seen.image()));
        frameWidth = seen.image().getWidth();
        liveScreen = seen.screen();
        areas = seen.areas();
        drawAreas();
    }

    private void drawAreas() {
        boxes.getChildren().clear();
        if (frameWidth == 0) return;
        FrameBoxes.areas(boxes, areas, frame.getFitWidth() / frameWidth);
    }

    /** {@code read} drawn: the status line, the stop pinned, the feed and the facts of the screen it is on. */
    void show(JournalView read) {
        last = read;
        // The journal says a run goes until it writes its end, which a killed run never does: the host says too.
        boolean live = read.running() && services.runs().isRunning();
        status.setText(statusLine(read, graph.get().screens().size(), nowMs.getAsLong(), live));
        Optional<Stop> open = live ? read.open() : Optional.empty();
        pinned.setVisible(open.isPresent());
        pinned.setManaged(open.isPresent());
        open.ifPresent(waiting -> {
            pinnedStop.setText("⏸ " + waiting.id() + " — " + waiting.kind().displayName() + " on "
                    + (waiting.screen() == null ? "an unknown screen" : waiting.screen()) + ": " + waiting.missing());
            pinnedClaude.setText(lastClaude(read).map(call -> "Claude: " + call).orElse("Claude: not on it yet"));
        });
        List<String> lines = feedLines(read);
        if (!lines.equals(feed.getItems())) feed.getItems().setAll(lines);
        String on = liveScreen != null ? liveScreen : read.where().orElse(null);
        screenName.setText(on == null ? "No screen of the map" : on);
        List<String> factLines = factLines(on);
        List<String> factsShown = facts.getChildren().stream().map(n -> ((Label) n).getText()).toList();
        if (!factLines.equals(factsShown)) facts.getChildren().setAll(factLines.stream().map(Label::new).toList());
        buttons();
    }

    /** The feed shown, newest first. */
    List<String> feedLines() {
        return List.copyOf(feed.getItems());
    }

    /** The stop pinned and what Claude did last, one line each; empty while nothing is pinned. */
    List<String> pinnedLines() {
        return pinned.isVisible() ? List.of(pinnedStop.getText(), pinnedClaude.getText()) : List.of();
    }

    String statusText() {
        return status.getText();
    }

    SplitMenuButton startButton() {
        return start;
    }

    /** The status line: how long, how many screens, how many stops fixed, and whether it runs or waits on Claude. */
    static String statusLine(JournalView read, int screensKnown, long nowMs, boolean live) {
        if (!read.began()) return "No explore run yet · " + screensKnown + " screens known";
        long fresh = read.events().stream()
                .filter(e -> e instanceof ExploreEvent.NewScreen(String s, String from, String by, long at) && from != null)
                .count();
        // A run that never wrote its end ran until its last event.
        long until = live || read.ended().isPresent() || read.events().isEmpty() ? nowMs
                : read.events().getLast().at();
        java.time.Duration ran = read.ranFor(until);
        String took = String.format("%d:%02d:%02d", ran.toHours(), ran.toMinutesPart(), ran.toSecondsPart());
        String state = read.ended().map(end -> "ended: " + end.displayName())
                .orElse(!live ? "ended" : read.open().isPresent() ? "⏸ waiting on Claude" : "running");
        return took + " · " + screensKnown + " screens known (+" + fresh + " new) · "
                + read.count(ExploreEvent.Fixed.class) + " stops fixed · " + state;
    }

    /** The latest run's events as feed lines, newest first; Claude's calls are the pinned stop's, not the feed's. */
    static List<String> feedLines(JournalView read) {
        List<String> lines = new ArrayList<>();
        for (ExploreEvent event : read.events()) {
            String line = line(event);
            if (line != null) lines.addFirst(clock(event.at()) + "  " + line);
        }
        return lines;
    }

    private static String line(ExploreEvent event) {
        return switch (event) {
            case ExploreEvent.Began e -> "▶ Began";
            case ExploreEvent.Ended e -> "■ Ended: " + (e.end() == null ? "unknown" : e.end().displayName());
            case ExploreEvent.Claude e -> null;
            case ExploreEvent.NewScreen e -> "✚ " + e.screen()
                    + (e.from() == null ? ", where it began" : ", from " + e.from() + " by " + e.by());
            case ExploreEvent.Tried e -> "→ " + e.screen() + "." + e.by()
                    + (e.mode() == null ? "" : " (" + e.mode().displayName() + ")")
                    + (e.arrival() == null ? "" : ": " + e.arrival().name().toLowerCase().replace('_', ' '));
            case ExploreEvent.Refused e -> "🛡 " + e.screen() + "." + e.by() + " refused"
                    + (e.guard() == null ? "" : " by " + e.guard().name().toLowerCase().replace('_', ' '))
                    + (e.why() == null || e.why().isBlank() ? "" : ": " + e.why());
            case ExploreEvent.GoalMet e -> "🎯 " + e.goal() + " met";
            case ExploreEvent.Stopped e -> "⏸ " + e.stop().id() + (e.again() ? " again" : "") + " — "
                    + e.stop().kind().displayName() + ": " + e.stop().missing();
            case ExploreEvent.Fixed e -> "✔ " + e.stopId() + " fixed" + (e.why() == null ? "" : ": " + e.why());
            case ExploreEvent.Parked e -> "⏸ " + e.stopId() + " parked: nobody answered, the run goes on elsewhere";
            case ExploreEvent.Reloaded e -> e.refused()
                    ? "⏸ " + e.stopId() + ": the bot did not compile, " + e.errors().getFirst()
                    : "✔ " + e.stopId() + ": the bot reloaded, generation " + e.generation();
        };
    }

    private static Optional<String> lastClaude(JournalView read) {
        return read.events().stream().filter(ExploreEvent.Claude.class::isInstance)
                .map(e -> ((ExploreEvent.Claude) e).call()).reduce((first, last) -> last);
    }

    private static String clock(long atMs) {
        return LocalTime.ofInstant(Instant.ofEpochMilli(atMs), ZoneId.systemDefault()).format(CLOCK);
    }

    /** The facts {@code constant}'s readouts read into, each with its value kept now; none off the map. */
    private List<String> factLines(String constant) {
        MapScreen screen = constant == null ? null : graph.get().screens().get(constant);
        if (screen == null) return List.of();
        Map<String, String> kept = new LinkedHashMap<>();
        if (services.pluginValues() != null) {
            for (RunParameter fact : services.pluginValues().runParameters()) {
                kept.put(fact.group() + "::" + fact.name(), fact.value());
            }
        }
        List<String> lines = new ArrayList<>();
        for (Readout readout : screen.readouts().values()) {
            String fact = ScreenTypes.factOf(readout);
            if (fact.isEmpty()) continue;
            String value = kept.get(fact);
            lines.add(fact + " = " + (value == null || value.isBlank() ? "unknown" : value));
        }
        return lines;
    }
}
