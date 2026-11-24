// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.plugin.api.source.RunParameter;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.internal.explore.Agent;
import com.botmaker.sdk.internal.explore.AgentSummary;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.Coordinator;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 🔭 Explore, the view 🗺 Screens' rail shows (2026-11-09, spec screens-rail goal 9; in tabs since plan
 * 2026-11-24-explore-drive-fixes, phase 4): a run exploring the game, watched and started from here. On top the bar
 * (status, ▶ Keep exploring, Stop), the goal line and a chip per agent of the crew; below, tabs: <em>Run</em> (the
 * game's frame and the feed, {@link ExploreRunTab}), <em>Crew</em> (each agent's live log, {@link ExploreCrewTab}),
 * <em>For you</em> (the user's stops, {@link ExploreYoursTab}) and <em>Summary</em> (findings left open, the goal's
 * witness, the Mechanic's branches).
 *
 * <p>The latest run's journal ({@link Journal}, {@code events.jsonl}), the selected agent's worker logs and, while a
 * run goes, a frame of the game are read once a second off the FX thread, only while the view is shown; the journal
 * and the logs only what was added since. <b>Start</b> starts <em>Keep exploring</em> (its menu: <em>Explore
 * map</em>) through {@link com.botmaker.plugin.api.Runs#start(RunEntry)}, while no run goes; <b>Stop</b> ends the run
 * going.
 */
final class ExploreView {

    /** How often the journal and the frame are read again. */
    private static final Duration TICK = Duration.seconds(1);
    /** How old the Coordinator's heartbeat may be before its chip says it went silent: its own limit. */
    private static final java.time.Duration HEARTBEAT = Coordinator.Settings.DEFAULT.heartbeat();
    private static final String YOURS = "For you";

    private final StudioServices services;
    private final Supplier<ScreenGraph> graph;
    /** The latest run's journal, asked again each read; null while no project is open: nothing is read. */
    private final Supplier<Optional<Journal>> journals;
    /** Reads the latest run's journal on from where it left off; the reading thread's, one read at a time. */
    private JournalView.Reader reader;
    /** The selected agent's sessions read so far; the reading thread's, one read at a time. */
    private final CrewSessions crewSessions = new CrewSessions();
    private final LongSupplier nowMs;

    private final BorderPane root = new BorderPane();
    private final Label status = new Label();
    private final SplitMenuButton start = new SplitMenuButton();
    private final Button stop = new Button("■ Stop");
    /** The goal bar's line: the goal, the way so far and its depth. */
    private final Label goal = new Label();
    /** One chip per agent of the crew; shown for a run given a goal only, the one run that has a crew. */
    private final AgentCards chips = new AgentCards(this::pick);
    private final TabPane tabs = new TabPane();
    private final ExploreRunTab runTab = new ExploreRunTab(new Thumbnails(this::frameFile));
    private final ExploreCrewTab crewTab = new ExploreCrewTab(this::tick);
    private final ExploreYoursTab yoursTab;
    private final ListView<String> summary = new ListView<>();
    private final Tab run = new Tab("Run");
    private final Tab crew = new Tab("Crew");
    private final Tab yours = new Tab(YOURS);
    /** The folder of the journal last read, which a frame named relative to it is in; the reading thread sets it. */
    private volatile Path journalFolder;
    private final Timeline ticker = new Timeline(new KeyFrame(TICK, e -> tick()));
    private final AtomicBoolean reading = new AtomicBoolean();
    private final AutoCloseable runState;

    // On the FX thread from here.
    /** The screen the last frame read shows, by its markers; null for none, or no frame read. */
    private String liveScreen;
    /** The frame drawn now: a website's page held from tick to tick is not drawn again. FX thread only. */
    private Seen showing;
    /** A website run's page as last read off the journal, and when it was written; read on the reading thread only. */
    private volatile Seen lastPage;
    private volatile long lastPageAt;
    private boolean closed;
    /** The journal as last shown. */
    private JournalView last = new JournalView(List.of(), List.of(), List.of());
    /** Whether the run went when the Summary tab was last written. */
    private boolean summedLive;
    /** The journal as last read, with its files' sizes and times then; the reading thread's, one at a time. */
    private volatile Read lastRead = new Read("", last);

    /** A read of the journal and the stamp of its files it was made at, kept together. */
    private record Read(String stamp, JournalView view) {}

    ExploreView(StudioServices services, Supplier<ScreenGraph> graph, Supplier<Optional<Journal>> journals,
                LongSupplier nowMs) {
        this.services = services;
        this.graph = graph;
        this.journals = journals;
        this.nowMs = nowMs;
        yoursTab = new ExploreYoursTab(journals == null ? Optional::empty : journals);
        root.getStylesheets().add(ScreenCanvas.SHEET);
        // The map's colour tokens are published on .screen-map.
        root.getStyleClass().addAll("screen-map", "explore-view");

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
        goal.getStyleClass().add("explore-goal");
        goal.setMinWidth(0);
        goal.setPadding(new Insets(4, 8, 0, 8));
        Node strip = chips.node();
        VBox.setMargin(strip, new Insets(4, 8, 6, 8));
        VBox top = new VBox(bar, goal, strip);
        top.getStyleClass().add("explore-bar");
        root.setTop(top);

        run.setContent(runTab.node());
        crew.setContent(crewTab.node());
        yours.setContent(yoursTab.node());
        summary.setPlaceholder(new Label("No explore run yet."));
        Tab summed = new Tab("Summary", summary);
        tabs.getTabs().setAll(run, crew, yours, summed);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        // The Crew tab's logs are read while it shows: read them at once when it is opened.
        tabs.getSelectionModel().selectedItemProperty().addListener((o, was, is) -> {
            if (is == crew) tick();
        });
        root.setCenter(tabs);

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
        Path project = services.projectDir();
        Supplier<Optional<Journal>> journals = project == null ? null : () -> Journal.latest(project);
        ExploreView view = new ExploreView(services, graph, journals, System::currentTimeMillis);
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

    /** A chip's click: the Crew tab, on {@code agent}. */
    private void pick(Agent agent) {
        crewTab.select(agent);
        tabs.getSelectionModel().select(crew);
        tick();
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
        start.setDisable(running || journals == null);
        // Only an explore run's: the bot's own run is stopped where it was started.
        stop.setDisable(!live(last));
    }

    /**
     * Whether {@code read}'s run is the one the host runs now. The journal says a run goes until it writes its end,
     * which a killed run never does; and a bot's own run started after it is not it: its process began later.
     */
    private boolean live(JournalView read) {
        if (!read.running() || !services.runs().isRunning()) return false;
        OptionalLong pid = services.runs().pid();
        Optional<Instant> started = pid.isEmpty() ? Optional.empty()
                : ProcessHandle.of(pid.getAsLong()).flatMap(process -> process.info().startInstant());
        // A second's slack: a process's start is kept to the clock tick.
        return started.isEmpty() || read.playingSince() >= started.get().toEpochMilli() - 1_000;
    }

    /**
     * Reads the journal, the selected agent's logs while the Crew tab shows, and a frame while an explore run goes, off
     * the FX thread; one read at a time.
     */
    private void tick() {
        buttons();
        if (journals == null || closed || !reading.compareAndSet(false, true)) return;
        ScreenGraph map = graph.get();
        boolean running = live(last);
        PanelContext context = running ? context() : null;
        if (context == null) forgetFrame();
        Agent agent = tabs.getSelectionModel().getSelectedItem() == crew ? crewTab.selected() : null;
        boolean whole = !crewTab.filled();
        Thread.ofVirtual().name("explore-view").start(() -> {
            try {
                Journal journal = journals.get().orElse(null);
                if (journal == null) return;
                journalFolder = journal.folder();
                JournalView read = read(journal);
                Seen seen = !running ? null : page(journal, read)
                        .orElseGet(() -> context == null ? null : look(context, map));
                Optional<List<CrewSessions.Session>> sessions = agent == null ? Optional.empty()
                        : crewSessions.read(journal, read, agent, whole);
                Platform.runLater(() -> {
                    if (closed) return;
                    if (seen != null) shown(seen);
                    show(read);
                    // A read for an agent no longer selected is dropped: the next tick reads the one that is.
                    if (sessions.isPresent() && agent == crewTab.selected()) crewTab.sessions(sessions.get());
                });
            } finally {
                reading.set(false);
            }
        });
    }

    /**
     * {@code journal} read: again only when its files changed, and then only the lines added since (a run of hours
     * writes thousands); a newer run's journal is read from its start. The reading thread's.
     */
    private JournalView read(Journal journal) {
        String stamp = journal.folder() + " " + stamp(journal);
        Read before = lastRead;
        JournalView read;
        if (stamp.equals(before.stamp())) {
            read = before.view();
        } else {
            if (reader == null || !reader.journal().folder().equals(journal.folder())) {
                reader = new JournalView.Reader(journal);
            }
            read = reader.view();
        }
        lastRead = new Read(stamp, read);
        return read;
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
        if (liveScreen == null && (showing == null || showing.areas().isEmpty())) return;
        liveScreen = null;
        if (showing != null) {
            showing = new Seen(showing.image(), null, Map.of());
            runTab.areas(Map.of());
        }
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

    /**
     * The page a website run wrote since it began ({@link Journal#page}): its browser is the run's own, which no
     * capture of this PC sees. Its screen is the journal's to say, so none here.
     */
    private Optional<Seen> page(Journal journal, JournalView read) {
        journal.lastPage(lastPageAt).ifPresent(page -> {
            lastPage = new Seen(page.image(), null, Map.of());
            lastPageAt = page.writtenAt();
        });
        // Unchanged since the last tick: the page held, not decoded again. One of an earlier run is never shown.
        Seen held = lastPage;
        return held != null && lastPageAt >= read.beganAt() ? Optional.of(held) : Optional.empty();
    }

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
        if (seen == showing) return;
        boolean sameImage = showing != null && showing.image() == seen.image();
        showing = seen;
        liveScreen = seen.screen();
        if (sameImage) runTab.areas(seen.areas());
        else runTab.frame(ScreenCapture.toFxImage(seen.image()), seen.areas());
    }

    /** {@code read} drawn: the bar, the chips, and each tab. */
    void show(JournalView read) {
        boolean changed = read != last;
        last = read;
        boolean live = live(read);
        long now = nowMs.getAsLong();
        status.setText(ExploreLines.statusLine(read, graph.get().screens().size(), now, live));
        String goalLine = ExploreLines.goalLine(read);
        goal.setText(goalLine);
        goal.setVisible(!goalLine.isEmpty());
        goal.setManaged(!goalLine.isEmpty());
        boolean crewed = read.crewed();
        chips.node().setVisible(crewed);
        chips.node().setManaged(crewed);
        List<AgentSummary> summaries = !crewed ? List.of() : Agent.CREW.stream()
                .map(agent -> read.agentSummary(agent, now, HEARTBEAT))
                .map(summary -> live ? summary : summary.ended()).toList();
        if (crewed) chips.show(summaries);
        crewTab.crew(summaries);
        runTab.show(read, live, changed);
        if (yoursTab.show(live ? read.forUser() : List.of())) {
            yours.setText(yoursTab.count() == 0 ? YOURS : YOURS + " (" + yoursTab.count() + ")");
        }
        // A killed run writes no end: the summary follows whether the run still goes, not the journal alone.
        if (changed || live != summedLive) {
            summedLive = live;
            List<String> lines = ExploreLines.summaryLines(read, live);
            if (!lines.equals(summary.getItems())) summary.getItems().setAll(lines);
        }
        String on = liveScreen != null ? liveScreen : read.where().orElse(null);
        runTab.screen(on, factLines(on));
        buttons();
    }

    /** The user's queue shown, one line each. */
    List<String> yourLines() {
        return yoursTab.lines();
    }

    /** The For you tab's title. */
    String yoursTitle() {
        return yours.getText();
    }

    /** The feed shown, newest first. */
    List<String> feedLines() {
        return runTab.feedRows().stream().map(ExploreLines.FeedRow::text).toList();
    }

    /** The feed's rows shown, newest first, with their chips and frames. */
    List<ExploreLines.FeedRow> feedRows() {
        return runTab.feedRows();
    }

    /** The crew's chips shown, one line each; empty for a run without a crew. */
    List<String> chipLines() {
        return chips.node().isVisible() ? chips.lines() : List.of();
    }

    /** The Crew tab's list, one line per agent; empty for a run without a crew. */
    List<String> crewLines() {
        return crewTab.crewLines();
    }

    /** The Crew tab's log as shown, and the sessions its picker offers. */
    ExploreCrewTab crewTab() {
        return crewTab;
    }

    /** The tab shown now, by its title. */
    String tabShown() {
        return tabs.getSelectionModel().getSelectedItem().getText();
    }

    /** Clicks {@code agent}'s chip. */
    void pickChip(Agent agent) {
        pick(agent);
    }

    /**
     * Reads the Crew tab's selected agent's logs now, on the calling thread, and shows them: what a tick does off the
     * FX thread.
     */
    void readCrew() {
        Journal journal = journals.get().orElseThrow();
        crewSessions.read(journal, JournalView.of(journal), crewTab.selected(), !crewTab.filled())
                .ifPresent(crewTab::sessions);
    }

    /** Whether a read is under way off the FX thread. */
    boolean reading() {
        return reading.get();
    }

    /** The Summary tab's lines. */
    List<String> summaryLines() {
        return List.copyOf(summary.getItems());
    }

    /** The Run tab, its frame's fit for one. */
    ExploreRunTab runTab() {
        return runTab;
    }

    String goalText() {
        return goal.getText();
    }

    /** The stop pinned and what Claude did last, one line each; empty while nothing is pinned. */
    List<String> pinnedLines() {
        return runTab.pinnedLines();
    }

    String statusText() {
        return status.getText();
    }

    SplitMenuButton startButton() {
        return start;
    }

    Button stopButton() {
        return stop;
    }

    /**
     * The file a record's frame names: as it is when absolute ({@code LivePlay.keep} writes them so), else in the
     * journal's folder; empty for none, or a file gone.
     */
    private Optional<Path> frameFile(String named) {
        if (named == null || named.isBlank()) return Optional.empty();
        try {
            Path path = Path.of(named);
            Path folder = journalFolder;
            Path file = path.isAbsolute() ? path : folder == null ? null : folder.resolve(path);
            return file != null && Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
        } catch (InvalidPathException unnamed) {
            return Optional.empty();
        }
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
