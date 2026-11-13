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
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.util.List;
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
    private static final double MIN_W = 320;
    private static final double MIN_H = 420;
    private static final double MIN_DRAWING = 160;
    /** The window's parts beside and under the drawing, before it has laid out: the padding and the bars. */
    private static final double CHROME_W = 20;
    private static final double CHROME_H = 150;
    /** The frame the window opens for before the first one arrives: a portrait game's. */
    private static final double GUESS_W = 1080;
    private static final double GUESS_H = 1920;

    private final ImageView screen = new ImageView();
    /** Draws the frame as large as the window gives it, keeping its ratio, centred; it never asks to grow. */
    private final Pane holder = new Pane(screen) {
        @Override
        protected void layoutChildren() {
            Image image = screen.getImage();
            if (image == null) return;
            LiveScreenLayout.Size size = LiveScreenLayout.fitted(image.getWidth(), image.getHeight(), getWidth(),
                    getHeight());
            // a fit of zero means "no fit", the frame at its own size over the bars: hidden until there is room
            screen.setVisible(size.width() > 0);
            if (!screen.isVisible()) return;
            screen.setFitWidth(size.width());
            screen.setFitHeight(size.height());
            screen.relocate((getWidth() - size.width()) / 2, (getHeight() - size.height()) / 2);
        }

        @Override
        protected double computePrefWidth(double height) {
            return MIN_DRAWING;
        }

        @Override
        protected double computePrefHeight(double width) {
            return MIN_DRAWING;
        }
    };
    /** Whether the window was fitted to the instance's frame, or keeps a size the user chose; the FX thread's. */
    private boolean fitted;
    /** The window's size once shown: a different one at the first frame is the user's doing. The FX thread's. */
    private double shownW;
    private double shownH;
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
        screen.setCursor(Cursor.HAND);
        screen.setOnMouseClicked(e -> {
            double shownW = screen.getBoundsInLocal().getWidth();
            double shownH = screen.getBoundsInLocal().getHeight();
            RunningModel.tap(e.getX(), e.getY(), shownW, shownH, frameW, frameH).ifPresent(tap ->
                    send("Tapped " + tap.x() + ", " + tap.y() + ".", d -> d.tap(tap.x(), tap.y())));
        });
        holder.setMinSize(MIN_DRAWING, MIN_DRAWING);

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

        // opened for a portrait game within the owner's screen; the first frame fits it to the instance's own shape
        Rectangle2D visual = visualBounds(window.stage());
        LiveScreenLayout.Size opening = LiveScreenLayout.window(GUESS_W, GUESS_H, CHROME_W, CHROME_H,
                visual.getWidth(), visual.getHeight(), MIN_W, MIN_H);
        stage = Modals.window(window.services(), window.stage(), Modals.Frame.modeless(instance.name() + " — live screen",
                opening.width(), opening.height(), MIN_W, MIN_H), root);
        stage.setOnShown(e -> {
            // a size remembered from the last time this window closed is the user's: the first frame keeps it
            if (Math.abs(stage.getScene().getWidth() - opening.width()) > 1
                    || Math.abs(stage.getScene().getHeight() - opening.height()) > 1) fitted = true;
            place(Math.min(stage.getWidth(), visualBounds(stage).getWidth()),
                    Math.min(stage.getHeight(), visualBounds(stage).getHeight()));
            shownW = stage.getWidth();
            shownH = stage.getHeight();
            device.scheduleWithFixedDelay(this::frame, 0, 500, TimeUnit.MILLISECONDS);
        });
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
            boolean reshaped = frame.getWidth() != frameW || frame.getHeight() != frameH;
            frameW = frame.getWidth();
            frameH = frame.getHeight();
            Platform.runLater(() -> {
                screen.setImage(ScreenCapture.toFxImage(frame));
                if (reshaped) holder.requestLayout();
                if (!fitted) {
                    fitted = true;
                    fitWindow(frame.getWidth(), frame.getHeight());
                }
                if (said.getText().equals("Connecting…") || said.getText().startsWith("No frame")) said.setText("");
            });
        } catch (RuntimeException | Error failed) {
            // nothing may leave a scheduled frame: it would end the schedule and freeze the screen. dadb surfaces
            // some failures as Errors.
            closeConnection();
            Platform.runLater(() -> said.setText("No frame from " + instance.name() + ": " + failed.getMessage()));
        }
    }

    /**
     * Sizes the window to the instance's frame within the screen it is on; once, at the first frame, and only while the
     * window is as it opened: one the user resized or maximized meanwhile keeps what they did.
     */
    private void fitWindow(int width, int height) {
        if (stage.isMaximized() || stage.isFullScreen()
                || Math.abs(stage.getWidth() - shownW) > 1 || Math.abs(stage.getHeight() - shownH) > 1) return;
        double chromeW = stage.getWidth() - holder.getWidth();
        double chromeH = stage.getHeight() - holder.getHeight();
        if (!(chromeW >= 0) || !(chromeH >= 0)) return;
        Rectangle2D visual = visualBounds(stage);
        LiveScreenLayout.Size size = LiveScreenLayout.window(width, height, chromeW, chromeH,
                visual.getWidth(), visual.getHeight(), MIN_W, MIN_H);
        place(size.width(), size.height());
    }

    /** Gives the window {@code width}×{@code height} about its centre, kept inside the screen it is on. */
    private void place(double width, double height) {
        Rectangle2D visual = visualBounds(stage);
        double x = LiveScreenLayout.centred(stage.getX(), stage.getWidth(), width, visual.getMinX(), visual.getMaxX());
        double y = LiveScreenLayout.centred(stage.getY(), stage.getHeight(), height, visual.getMinY(), visual.getMaxY());
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(x);
        stage.setY(y);
    }

    /** The free area of the screen holding most of {@code window}, the primary screen's when it is on none yet. */
    private static Rectangle2D visualBounds(Window window) {
        if (window == null || !(window.getWidth() > 0)) return Screen.getPrimary().getVisualBounds();
        Rectangle2D bounds = new Rectangle2D(window.getX(), window.getY(), window.getWidth(), window.getHeight());
        List<Screen> on = Screen.getScreensForRectangle(bounds);
        Screen most = Screen.getPrimary();
        double largest = -1;
        for (Screen screen : on) {
            Rectangle2D area = screen.getBounds();
            double shared = Math.max(0, Math.min(area.getMaxX(), bounds.getMaxX()) - Math.max(area.getMinX(), bounds.getMinX()))
                    * Math.max(0, Math.min(area.getMaxY(), bounds.getMaxY()) - Math.max(area.getMinY(), bounds.getMinY()));
            if (shared > largest) {
                largest = shared;
                most = screen;
            }
        }
        return most.getVisualBounds();
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
