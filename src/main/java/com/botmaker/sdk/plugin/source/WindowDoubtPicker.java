// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.internal.observe.RunEvent;
import com.botmaker.sdk.internal.observe.RunFrames;
import com.botmaker.sdk.plugin.screen.ScreenOverlay;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.WindowIdentity;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The window a run asks with when it cannot tell which of the windows its launch opened is the game's (2026-11-25,
 * spec {@code 2026-11-25-window-doubt-picker-design.md}, layout C): the selected window's still on the left, every
 * window on the right with a small one, the run's guess selected. The pick is kept as this PC's window for the game
 * ({@link AutomaticTab#picks}), which the waiting run reads at once; "Not now" closes it, and the run takes its guess
 * when its wait is over. The run's {@link RunEvent.WindowSettled}, or its end, closes it too.
 *
 * <p>Bound to the open project ({@code SdkPlugin}'s steps), Studio with a UI only. Studio relays the frames whole and
 * writes a value of this PC it does not read: it learns nothing of the SDK.
 */
public final class WindowDoubtPicker {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final double BIG_WIDTH = 420;
    private static final double BIG_HEIGHT = 260;
    private static final double SMALL_WIDTH = 64;
    private static final double SMALL_HEIGHT = 40;

    private static volatile List<AutoCloseable> subscriptions = List.of();
    /** The question shown, by game; touched on the FX thread only. */
    private static final Map<String, Stage> SHOWN = new HashMap<>();

    private final StudioServices services;
    private final RunEvent.WindowDoubt doubt;
    private final ImageView big = new ImageView();
    private final Label status = Styles.on(new Label(), Styles.DIALOG_HINT);
    private final List<HBox> rows = new ArrayList<>();
    private int selected;
    private Stage stage;

    private WindowDoubtPicker(StudioServices services, RunEvent.WindowDoubt doubt) {
        this.services = services;
        this.doubt = doubt;
    }

    /** Listens to the project's runs for a question about the game's window, until {@link #unbind}. */
    public static void bind(StudioServices services) {
        unbind();
        AutoCloseable telemetry = services.runs().onTelemetry(frame -> {
            RunEvent event;
            try {
                event = RunFrames.decode(frame);
            } catch (RunFrames.Unreadable unreadable) {
                return;
            }
            switch (event) {
                case RunEvent.WindowDoubt asked -> Platform.runLater(() -> show(services, asked));
                case RunEvent.WindowSettled over -> Platform.runLater(() -> close(over.game()));
                default -> { }
            }
        });
        AutoCloseable state = services.runs().onStateChanged(running -> {
            if (!running) Platform.runLater(WindowDoubtPicker::closeAll);
        });
        subscriptions = List.of(telemetry, state);
    }

    /** Stops listening and closes a question still shown. */
    public static void unbind() {
        for (AutoCloseable subscription : subscriptions) {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // a handle that fails to close leaves a listener that opens a window nobody asked for
            }
        }
        subscriptions = List.of();
        if (Platform.isFxApplicationThread()) closeAll();
        else Platform.runLater(WindowDoubtPicker::closeAll);
    }

    private static void show(StudioServices services, RunEvent.WindowDoubt doubt) {
        if (doubt.windows().isEmpty()) return;
        close(doubt.game());
        WindowDoubtPicker picker = new WindowDoubtPicker(services, doubt);
        picker.open();
        SHOWN.put(doubt.game(), picker.stage);
    }

    private static void close(String game) {
        Stage shown = SHOWN.remove(game);
        if (shown != null) shown.close();
    }

    private static void closeAll() {
        for (Stage shown : List.copyOf(SHOWN.values())) shown.close();
        SHOWN.clear();
    }

    private void open() {
        Label heading = Styles.on(new Label("Which window is the game?"), Styles.DIALOG_HEADING);
        Label sub = Styles.on(new Label(doubt.game() + " opened windows BotMaker can't tell apart. Pick the game's:"
                + " it is kept for the next runs."), Styles.DIALOG_HINT);
        sub.setWrapText(true);

        big.setFitWidth(BIG_WIDTH);
        big.setFitHeight(BIG_HEIGHT);
        big.setPreserveRatio(true);
        StackPane preview = new StackPane(big);
        preview.setMinSize(BIG_WIDTH, BIG_HEIGHT);
        HBox.setHgrow(preview, Priority.ALWAYS);

        VBox list = new VBox(4);
        Image[] stills = new Image[doubt.windows().size()];
        for (int i = 0; i < doubt.windows().size(); i++) list.getChildren().add(row(i, stills));
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(240);

        HBox body = new HBox(10, preview, scroll);
        VBox.setVgrow(body, Priority.ALWAYS);

        Label countdown = Styles.on(new Label(), Styles.WARNING_TEXT);
        Button notNow = new Button("Not now");
        notNow.setCancelButton(true);
        notNow.setOnAction(e -> stage.close());
        Button use = Styles.on(new Button("Use this window"), Styles.PRIMARY_BUTTON);
        use.setDefaultButton(true);
        use.setOnAction(e -> use());
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, countdown, spacer, notNow, use);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, heading, sub, body, status, bar);
        root.setPadding(new Insets(14));
        stage = Modals.window(services, Modals.Frame.modeless("Which window is the game?", 720, 420, 560, 340),
                root);

        // The run's own wait, as it said it; counted from the frame's arrival, a moment after it asked.
        int[] left = {doubt.seconds()};
        countdown.setText(countdown(left[0]));
        Timeline tick = new Timeline(new KeyFrame(Duration.seconds(1), e -> countdown.setText(countdown(--left[0]))));
        tick.setCycleCount(Math.max(1, doubt.seconds()));
        stage.setOnHidden(e -> {
            tick.stop();
            SHOWN.remove(doubt.game(), stage);
        });
        select(0, stills);
        grabStills(stills);
        tick.play();
        stage.show();
        stage.toFront();
    }

    private static String countdown(int seconds) {
        return seconds > 0 ? "Best guess in " + seconds + " s" : "The run took its best guess";
    }

    private HBox row(int index, Image[] stills) {
        RunEvent.DoubtfulWindow window = doubt.windows().get(index);
        ImageView small = new ImageView();
        small.setFitWidth(SMALL_WIDTH);
        small.setFitHeight(SMALL_HEIGHT);
        small.setPreserveRatio(true);
        StackPane thumb = new StackPane(small);
        thumb.setMinSize(SMALL_WIDTH, SMALL_HEIGHT);
        Label title = Styles.on(new Label(window.title()), Styles.STRONG_TEXT, Styles.TILE_NAME);
        String size = window.bounds().width() + "×" + window.bounds().height();
        Label meta = Styles.on(new Label((index == 0 ? "best guess · " : "") + windowClass(window) + " · " + size),
                Styles.MUTED_TEXT, Styles.SMALL_TEXT);
        HBox row = Styles.on(new HBox(8, thumb, new VBox(2, title, meta)), Styles.TILE);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-cursor: hand;");
        row.setUserData(small);
        row.setOnMouseClicked(e -> {
            select(index, stills);
            if (e.getClickCount() == 2) use();
        });
        rows.add(row);
        return row;
    }

    private static String windowClass(RunEvent.DoubtfulWindow window) {
        try {
            return WindowIdentity.parse(window.identity()).windowClass();
        } catch (RuntimeException unreadable) {
            return window.identity();
        }
    }

    private void select(int index, Image[] stills) {
        rows.get(selected).pseudoClassStateChanged(SELECTED, false);
        selected = index;
        rows.get(index).pseudoClassStateChanged(SELECTED, true);
        big.setImage(stills[index]);
    }

    /** One still of each window, off the FX thread; a window that cannot be grabbed keeps an empty frame. */
    private void grabStills(Image[] stills) {
        List<RunEvent.DoubtfulWindow> windows = doubt.windows();
        Thread.ofVirtual().name("window-doubt-stills").start(() -> {
            List<GenericWindow> open;
            try {
                open = NativeControllerFactory.get().getAllWindows();
            } catch (RuntimeException | LinkageError noWindows) {
                return;
            }
            for (int i = 0; i < windows.size(); i++) {
                Image image = still(open, windows.get(i));
                if (image == null) continue;
                int index = i;
                Platform.runLater(() -> {
                    stills[index] = image;
                    if (rows.get(index).getUserData() instanceof ImageView small) small.setImage(image);
                    if (selected == index) big.setImage(image);
                });
            }
        });
    }

    private static Image still(List<GenericWindow> open, RunEvent.DoubtfulWindow wanted) {
        for (GenericWindow window : open) {
            if (window == null || !String.valueOf(window.getNativeHandle()).equals(wanted.handle())) continue;
            try {
                BufferedImage shot = NativeControllerFactory.get().captureWindow(window);
                return shot == null ? null : ScreenOverlay.toFxImage(shot);
            } catch (RuntimeException | LinkageError notGrabbed) {
                return null;
            }
        }
        return null;
    }

    /** Keeps the selected window as the game's on this PC; the waiting run reads it and says it was picked by you. */
    private void use() {
        RunEvent.DoubtfulWindow window = doubt.windows().get(selected);
        Optional<String> refused;
        try {
            refused = AutomaticTab.picks(services).save(LaunchSpec.parse(doubt.game()),
                    WindowIdentity.parse(window.identity()));
        } catch (RuntimeException unreadable) {
            refused = Optional.of("the run named a window this Studio cannot read: " + unreadable.getMessage());
        }
        if (refused.isPresent()) {
            Styles.pick(status, Styles.WARNING_TEXT, Styles.WARNING_TEXT);
            status.setText("Not kept: " + refused.get());
            return;
        }
        stage.close();
    }
}
