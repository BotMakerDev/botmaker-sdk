package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.config.InstanceKey;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An instance's screen, live at about two frames a second, for when an operation needs the user (a 2-step code, a
 * captcha, a screen BotMaker does not know). A click on it taps the instance there; the field types into the
 * instance's focused field through {@link AdbDevice#text}, whose log line says the length only. One per instance.
 */
final class LiveScreenDialog {

    private static final Map<InstanceKey, LiveScreenDialog> OPEN = new ConcurrentHashMap<>();
    private static final int KEY_BACK = 4;
    private static final int KEY_ENTER = 66;

    private final EmulatorInstance instance;
    private final InstanceKey key;
    private final Stage stage;
    private final ImageView screen = new ImageView();
    private final Label said = Styles.on(new Label("Connecting…"), StyleClasses.MUTED_TEXT);
    private static final String HOW = "Click the screen to tap it there; type below to type into the field it has open.";
    private final Label hint = Styles.on(new Label(HOW), StyleClasses.DIALOG_HINT);
    /** The device thread's own: opened, used and closed there only. */
    private AdbDevice connection;
    /** Frames on a schedule; taps and typing on the same thread, so each reaches the device in turn. */
    private final ScheduledExecutorService device;
    private volatile int frameW;
    private volatile int frameH;

    /** Shows {@code row}'s screen, the dialog already open for it when there is one. */
    static void open(EmulatorsWindow window, EmulatorsModel.InstanceRow row, String why) {
        LiveScreenDialog open = OPEN.get(row.key());
        if (open == null) {
            open = new LiveScreenDialog(window, row);
            OPEN.put(row.key(), open);
        }
        // a reason given now replaces the one shown, so an open screen still says why it is needed
        if (why != null && !why.isBlank()) open.hint.setText(why + " " + HOW);
        open.stage.show();
        open.stage.toFront();
    }

    private LiveScreenDialog(EmulatorsWindow window, EmulatorsModel.InstanceRow row) {
        this.instance = row.instance();
        this.key = row.key();
        this.device = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "emulators-live-" + instance.name());
            thread.setDaemon(true);
            return thread;
        });

        screen.setPreserveRatio(true);
        screen.setFitWidth(420);
        screen.setFitHeight(720);
        screen.setCursor(Cursor.HAND);
        screen.setOnMouseClicked(e -> {
            double shownW = screen.getBoundsInLocal().getWidth();
            double shownH = screen.getBoundsInLocal().getHeight();
            RunningModel.tap(e.getX(), e.getY(), shownW, shownH, frameW, frameH).ifPresent(tap ->
                    send("Tapped " + tap.x() + ", " + tap.y() + ".", d -> d.tap(tap.x(), tap.y())));
        });
        StackPane holder = new StackPane(screen);
        holder.setMinSize(240, 240);

        hint.setWrapText(true);

        TextField typed = new TextField();
        typed.setPromptText("Text to type on the instance");
        HBox.setHgrow(typed, Priority.ALWAYS);
        Button type = new Button("Type");
        type.setDefaultButton(true);
        type.setOnAction(e -> {
            String text = typed.getText();
            typed.clear();
            if (text.isEmpty()) return;
            int length = text.length();
            send("Typed " + length + " characters.", d -> d.text(text));
        });
        Button enter = new Button("Enter");
        enter.setOnAction(e -> send("Pressed Enter.", d -> d.key(KEY_ENTER)));
        Button back = new Button("Back");
        back.setOnAction(e -> send("Pressed Back.", d -> d.key(KEY_BACK)));
        HBox typing = new HBox(8, typed, type, enter, back);
        typing.setAlignment(Pos.CENTER_LEFT);

        VBox bottom = new VBox(8, hint, typing, said);
        bottom.setPadding(new Insets(8, 10, 10, 10));
        BorderPane root = new BorderPane(holder);
        root.setBottom(bottom);
        root.setPadding(new Insets(10, 10, 0, 10));

        stage = Modals.window(window.services(), window.stage(),
                Modals.Frame.modeless(instance.name() + " — live screen", 480, 900, 320, 420), root);
        stage.setOnShown(e -> device.scheduleWithFixedDelay(this::frame, 0, 500, TimeUnit.MILLISECONDS));
        stage.setOnHidden(e -> {
            OPEN.remove(key, this);
            // the connection is the device thread's: it closes it after what it is doing, then ends
            device.execute(this::closeConnection);
            device.shutdown();
        });
    }

    /** One frame, on the device's thread. */
    private void frame() {
        try {
            BufferedImage frame = connected().screencap();
            if (frame == null) return;
            frameW = frame.getWidth();
            frameH = frame.getHeight();
            Platform.runLater(() -> {
                screen.setImage(ScreenCapture.toFxImage(frame));
                if (said.getText().equals("Connecting…") || said.getText().startsWith("No frame")) said.setText("");
            });
        } catch (RuntimeException | Error failed) {
            // nothing may leave a scheduled frame: it would end the schedule and freeze the screen. dadb surfaces
            // some failures as Errors.
            closeConnection();
            Platform.runLater(() -> said.setText("No frame from " + instance.name() + ": " + failed.getMessage()));
        }
    }

    /** Runs {@code act} on the device's thread, then says {@code done}. */
    private void send(String done, DeviceAct act) {
        try {
            device.execute(() -> {
                try {
                    act.on(connected());
                    Platform.runLater(() -> said.setText(done));
                } catch (RuntimeException | Error failed) {
                    closeConnection();
                    Platform.runLater(() -> said.setText("It did not reach " + instance.name() + ": "
                            + failed.getMessage()));
                }
            });
        } catch (RejectedExecutionException closing) {
            // the dialog is closing
        }
    }

    private AdbDevice connected() {
        if (connection == null || !connection.isConnected()) {
            closeConnection();
            connection = AdbDevice.connect(instance.adb());
        }
        return connection;
    }

    private void closeConnection() {
        AdbDevice was = connection;
        connection = null;
        if (was == null) return;
        try {
            was.close();
        } catch (Exception | Error ignored) {
            // already gone
        }
    }

    @FunctionalInterface
    private interface DeviceAct {
        void on(AdbDevice device);
    }
}
