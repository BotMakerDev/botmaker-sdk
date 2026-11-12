package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.checks.WindowsEmulatorChecks;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Operation;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The Running tab: every running instance as a card with a small live screen, how long it has been up, ADB's answer,
 * CPU and memory, the app in front and the open bot's run; then the instances a bot expects that are stopped. The
 * screens and readings are taken about every two seconds, and only while the tab is shown.
 */
final class RunningTab {

    private static final int THUMB_WIDTH = 168;
    private static final long EVERY_MS = 2000;

    private final EmulatorsWindow window;
    private final BorderPane root = new BorderPane();
    private final VBox groups = new VBox(14);
    private final ChoiceBox<RunningModel.Grouping> grouping = new ChoiceBox<>();
    private final Label headroom = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
    /** The cards drawn, by instance, which each reading updates in place. */
    private final Map<InstanceKey, CardView> drawn = new HashMap<>();
    /** The last reading of each instance, for its CPU share; read on the reader's thread too. */
    private final Map<InstanceKey, RunningModel.Live> lives = new ConcurrentHashMap<>();
    private EmulatorsModel.Snapshot snapshot = EmulatorsModel.Snapshot.EMPTY;
    private ScheduledExecutorService reader;
    private volatile List<RunningModel.Card> watched = List.of();
    /** Each running instance's connection, kept across passes; the reader's thread alone touches it. */
    private final Map<InstanceKey, AdbDevice> connections = new HashMap<>();
    /** What Windows could still give at the last reading, or -1. */
    private long lastAvailable = -1;

    RunningTab(EmulatorsWindow window) {
        this.window = window;
        grouping.getItems().setAll(RunningModel.Grouping.values());
        grouping.setValue(RunningModel.Grouping.BY_EMULATOR);
        grouping.setConverter(new StringConverter<>() {
            @Override
            public String toString(RunningModel.Grouping value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public RunningModel.Grouping fromString(String text) {
                return RunningModel.Grouping.ALL;
            }
        });
        grouping.valueProperty().addListener((o, was, now) -> draw());
        headroom.setWrapText(true);
        HBox.setHgrow(headroom, Priority.ALWAYS);
        headroom.setMaxWidth(Double.MAX_VALUE);
        HBox bar = new HBox(10, new Label("Show"), grouping, headroom);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 8, 10));

        ScrollPane scroll = new ScrollPane(groups);
        scroll.setFitToWidth(true);
        groups.setPadding(new Insets(4, 10, 10, 10));
        root.setTop(bar);
        root.setCenter(scroll);
    }

    Node node() {
        return root;
    }

    void show(EmulatorsModel.Snapshot read) {
        snapshot = read;
        draw();
    }

    /** Starts or stops the readings: they run only while the tab is shown in a shown window. */
    void visible(boolean shown) {
        if (shown && reader == null) {
            reader = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "emulators-running");
                thread.setDaemon(true);
                return thread;
            });
            reader.scheduleWithFixedDelay(this::readAll, 0, EVERY_MS, TimeUnit.MILLISECONDS);
        } else if (!shown && reader != null) {
            // the connections are the reader's: it closes them, then ends
            reader.execute(() -> closeAll(connections.keySet()));
            reader.shutdown();
            reader = null;
        }
    }

    /** Draws the cards again, keeping the last readings and screens. */
    void draw() {
        String bot = window.botRunning();
        List<RunningModel.Group> all = RunningModel.groups(snapshot, grouping.getValue(), bot,
                bot == null ? null : window.botInstance());
        Map<InstanceKey, CardView> was = new HashMap<>(drawn);
        drawn.clear();
        groups.getChildren().clear();
        drawHeadroom(-2);
        if (all.isEmpty()) {
            groups.getChildren().add(Styles.on(new Label("No instance is running, and no bot expects a stopped one."
                    + " Start one from the Instances tab."), StyleClasses.MUTED_TEXT));
        }
        for (RunningModel.Group group : all) {
            Label title = Styles.on(new Label(group.title()), StyleClasses.STRONG_TEXT);
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox header = new HBox(10, title, gap);
            header.setAlignment(Pos.CENTER_LEFT);
            if (!group.running().isEmpty()) {
                Button stopAll = new Button("Stop all");
                stopAll.setOnAction(e -> window.stop(group.running()));
                header.getChildren().add(stopAll);
            }
            FlowPane cards = new FlowPane(10, 10);
            for (RunningModel.Card card : group.cards()) {
                CardView view = new CardView(card, was.get(card.key()));
                drawn.put(card.key(), view);
                cards.getChildren().add(view.node);
            }
            groups.getChildren().add(new VBox(6, header, cards));
        }
        watched = all.stream().flatMap(group -> group.cards().stream()).filter(card -> !card.expected()).toList();
    }

    /** The line above the cards; {@code availableMb} -2 keeps the last one read. */
    private void drawHeadroom(long availableMb) {
        if (availableMb != -2) lastAvailable = availableMb;
        headroom.setText(RunningModel.Headroom.of(lastAvailable, snapshot).line());
    }

    // ---- the readings ----

    /**
     * One pass over the running instances, on the reader's thread. Each instance keeps its connection from pass to
     * pass; one that fails is closed and opened again next time. Nothing thrown leaves the pass, which would end the
     * schedule.
     */
    private void readAll() {
        try {
            long available = WindowsEmulatorChecks.availableMb();
            Platform.runLater(() -> drawHeadroom(available));
            List<RunningModel.Card> cards = watched;
            Set<InstanceKey> gone = new HashSet<>(connections.keySet());
            cards.forEach(card -> gone.remove(card.key()));
            closeAll(gone);
            for (RunningModel.Card card : cards) readOne(card);
        } catch (Throwable unexpected) {
            // the next pass tries again
        }
    }

    private void readOne(RunningModel.Card card) {
        InstanceKey key = card.key();
        RunningModel.Live live;
        BufferedImage thumb = null;
        try {
            AdbDevice device = connections.get(key);
            if (device == null || !device.isConnected()) {
                closeAll(Set.of(key));
                device = AdbDevice.connect(card.row().instance().adb());
                connections.put(key, device);
            }
            live = RunningModel.read(device, lives.get(key));
            if (live.adbMs() >= 0) thumb = thumbnail(device.screencap());
            else closeAll(Set.of(key));
        } catch (RuntimeException | Error noAnswer) {
            // dadb surfaces some failures as Errors
            closeAll(Set.of(key));
            live = RunningModel.Live.NO_ANSWER;
        }
        lives.put(key, live);
        RunningModel.Live read = live;
        BufferedImage shot = thumb;
        Platform.runLater(() -> {
            CardView view = drawn.get(key);
            if (view != null) view.update(read, shot);
        });
    }

    /** Closes the connections of {@code keys}; on the reader's thread only. */
    private void closeAll(Set<InstanceKey> keys) {
        for (InstanceKey key : List.copyOf(keys)) {
            AdbDevice device = connections.remove(key);
            if (device == null) continue;
            try {
                device.close();
            } catch (Exception | Error ignored) {
                // already gone
            }
        }
    }

    private static BufferedImage thumbnail(BufferedImage frame) {
        if (frame == null || frame.getWidth() <= 0) return null;
        double scale = Math.min(1.0, (double) THUMB_WIDTH / frame.getWidth());
        int w = Math.max(1, (int) Math.round(frame.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(frame.getHeight() * scale));
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(frame, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return small;
    }

    // ---- Show ----

    /** Brings the instance's own window to the front, found by its title; says so when none matches. */
    private void bringToFront(EmulatorsModel.InstanceRow row) {
        String name = row.instance().name();
        Async.load("emulators-show", () -> {
            try {
                NativeController controller = NativeControllerFactory.get();
                List<GenericWindow> windows = controller.getAllWindows();
                Optional<String> title = RunningModel.windowTitle(name,
                        windows.stream().map(GenericWindow::getTitle).toList());
                if (title.isEmpty()) return "No window titled \"" + name + "\" was found: show it from "
                        + row.key().platform().displayName() + "'s own manager.";
                windows.stream().filter(w -> title.get().equals(w.getTitle())).findFirst()
                        .ifPresent(controller::focusWindow);
                return "";
            } catch (RuntimeException | LinkageError failed) {
                return name + "'s window could not be shown: " + failed.getMessage();
            }
        }, window::say);
    }

    // ---- a card ----

    private final class CardView {

        private final VBox node = new VBox(4);
        private final ImageView screen = new ImageView();
        private final Label uptime = new Label();
        private final Label adb = new Label();
        private final Label load = new Label();
        private final Label front = new Label();

        CardView(RunningModel.Card card, CardView was) {
            EmulatorsModel.InstanceRow row = card.row();
            node.getStyleClass().add(StyleClasses.TILE);
            node.setPadding(new Insets(8));
            node.setPrefWidth(THUMB_WIDTH + 16);
            node.setMaxWidth(THUMB_WIDTH + 16);
            Label name = Styles.on(new Label(row.instance().name()), StyleClasses.STRONG_TEXT);
            Label product = Styles.on(new Label(row.key().platform().displayName()), StyleClasses.MUTED_TEXT);
            node.getChildren().addAll(name, product);

            if (card.expected()) {
                Label line = Styles.on(new Label(card.expectedLine()), StyleClasses.WARNING_TEXT);
                line.setWrapText(true);
                Button start = new Button("Start");
                start.setOnAction(e -> window.start(List.of(row)));
                node.getChildren().addAll(line, start);
                return;
            }

            screen.setPreserveRatio(true);
            screen.setFitWidth(THUMB_WIDTH);
            screen.setFitHeight(THUMB_WIDTH * 16 / 9.0);
            screen.setOnMouseClicked(e -> LiveScreenDialog.open(window, row, null));
            node.getChildren().add(0, screen);
            for (Label label : List.of(uptime, adb, load, front)) {
                label.setWrapText(true);
                Styles.on(label, StyleClasses.MUTED_TEXT);
            }
            node.getChildren().addAll(uptime, adb, load, front);

            Optional<Operation.Snapshot> needs = window.needsYou(row.key());
            needs.ifPresent(op -> {
                Label line = Styles.on(new Label("Needs you: " + (op.needsYou() == null ? op.title() : op.needsYou())),
                        StyleClasses.WARNING_TEXT);
                line.setWrapText(true);
                Button look = new Button("Open its screen");
                look.setOnAction(e -> LiveScreenDialog.open(window, row, op.needsYou()));
                node.getChildren().addAll(line, look);
            });

            MenuButton stop = new MenuButton("Stop");
            MenuItem stopEmulator = new MenuItem("Stop the instance");
            stopEmulator.setOnAction(e -> window.stop(List.of(row)));
            stop.getItems().add(stopEmulator);
            if (card.bot() != null) {
                Label bot = Styles.on(new Label("Bot: " + card.bot() + " is running"), StyleClasses.OK_TEXT);
                bot.setWrapText(true);
                node.getChildren().add(bot);
                MenuItem stopBot = new MenuItem("Stop the bot " + card.bot());
                stopBot.setOnAction(e -> window.stopBot());
                stop.getItems().add(0, stopBot);
            }
            Button show = new Button("Show");
            show.setOnAction(e -> bringToFront(row));
            Button look = new Button("Live…");
            look.setOnAction(e -> LiveScreenDialog.open(window, row, null));
            HBox buttons = new HBox(6, show, look, stop);
            node.getChildren().add(buttons);

            update(lives.get(row.key()), null);
            if (was != null && was.screen.getImage() != null) screen.setImage(was.screen.getImage());
        }

        void update(RunningModel.Live live, BufferedImage shot) {
            if (live == null) {
                uptime.setText("Reading…");
                return;
            }
            uptime.setText(live.uptime());
            adb.setText(live.adb());
            Styles.pick(adb, live.adbMs() < 0 ? StyleClasses.ERROR_TEXT : StyleClasses.MUTED_TEXT,
                    StyleClasses.ERROR_TEXT, StyleClasses.MUTED_TEXT);
            load.setText(live.cpuAndMemory());
            front.setText(live.inFront());
            if (shot != null) screen.setImage(ScreenCapture.toFxImage(shot));
        }
    }
}
