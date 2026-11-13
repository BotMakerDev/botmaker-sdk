package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Sourced;
import com.botmaker.shared.emulator.google.InstanceSignIn;
import com.botmaker.shared.emulator.google.PasswordStore;
import com.botmaker.shared.emulator.google.Passwords;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An instance's screen, live at about two frames a second, for when an operation needs the user (a 2-step code, a
 * captcha, a screen BotMaker does not know). A click on it taps the instance there. The Fill panel beside it types an
 * account's email or password into the field the user tapped, and the field below types any other text, both through
 * {@link AdbDevice#text}, whose log line says the length only. A password is read from this PC's store, or asked in
 * the prompt, on the device's thread, typed, and cleared: it never enters a JavaFX control here. One per instance.
 */
final class LiveScreenDialog {

    private static final Map<InstanceKey, LiveScreenDialog> OPEN = new ConcurrentHashMap<>();
    private static final int KEY_BACK = 4;
    private static final int KEY_ENTER = 66;

    private final EmulatorsWindow window;
    private final EmulatorInstance instance;
    private final InstanceKey key;
    private final Stage stage;
    private static final double MIN_W = 560;
    private static final double MIN_H = 420;
    private static final double MIN_DRAWING = 160;
    /** The Fill panel's width, beside the drawing. */
    private static final double PANEL_W = 250;
    /** The window's parts beside and under the drawing, before it has laid out: the padding, the panel and the bars. */
    private static final double CHROME_W = 30 + PANEL_W;
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
    private static final String HOW = "Click the screen to tap it there: tap a field, then fill it from the right.";
    private final Label hint = Styles.on(new Label(HOW), StyleClasses.DIALOG_HINT);
    /** The Fill panel's rows, drawn again each time the dialog is shown. */
    private final VBox accounts = new VBox(10);
    /** Whether this PC has a credential store, read off the FX thread with the panel's rows; the FX thread's. */
    private boolean canRemember;
    /** The row last shown, read again from the window's snapshot at each drawing; the FX thread's. */
    private EmulatorsModel.InstanceRow row;
    /** Counts the panel's loads: only the latest one draws. The FX thread's. */
    private int loads;
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
        open.row = row;
        open.fillPanel();
        open.stage.show();
        open.stage.toFront();
    }

    /** Lists the accounts again in every live screen open: an Apply started or ended, or the instances were read. */
    static void redrawAll() {
        OPEN.values().forEach(LiveScreenDialog::fillPanel);
    }

    private LiveScreenDialog(EmulatorsWindow window, EmulatorsModel.InstanceRow row) {
        this.window = window;
        this.row = row;
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
                    send(d -> {
                        d.tap(tap.x(), tap.y());
                        return "Tapped " + tap.x() + ", " + tap.y() + ".";
                    }));
        });
        holder.setMinSize(MIN_DRAWING, MIN_DRAWING);

        hint.setWrapText(true);

        TextField typed = new TextField();
        typed.setPromptText("Other text to type (a code)");
        HBox.setHgrow(typed, Priority.ALWAYS);
        Button type = new Button("Type");
        type.setDefaultButton(true);
        type.setOnAction(e -> {
            String text = typed.getText();
            typed.clear();
            if (text.isEmpty()) return;
            send(d -> {
                d.text(text);
                return "Typed " + text.length() + " characters.";
            });
        });
        Button back = new Button("Back");
        back.setOnAction(e -> send(d -> {
            d.key(KEY_BACK);
            return "Pressed Back.";
        }));
        Button enter = new Button("Enter");
        enter.setOnAction(e -> send(d -> {
            d.key(KEY_ENTER);
            return "Pressed Enter.";
        }));
        Button hide = new Button("Hide");
        // the stage is built below, from this row: its window is reached through the scene instead
        hide.setOnAction(e -> hide.getScene().getWindow().hide());
        HBox typing = new HBox(8, typed, type, back, enter, hide);
        typing.setAlignment(Pos.CENTER_LEFT);

        Label fillHeading = Styles.on(new Label("Fill"), StyleClasses.STRONG_TEXT);
        Label fillHint = Styles.on(new Label("Tap a field on the screen first, then fill it here."),
                StyleClasses.DIALOG_HINT);
        fillHint.setWrapText(true);
        ScrollPane list = new ScrollPane(accounts);
        list.setFitToWidth(true);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox panel = new VBox(8, fillHeading, fillHint, list);
        panel.setPrefWidth(PANEL_W);
        panel.setMinWidth(PANEL_W);
        panel.setMaxWidth(PANEL_W);
        panel.setPadding(new Insets(0, 0, 0, 10));

        VBox bottom = new VBox(8, hint, typing, said);
        bottom.setPadding(new Insets(8, 10, 10, 10));
        BorderPane root = new BorderPane(holder);
        root.setRight(panel);
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

    // ---- the Fill panel ----

    /**
     * Lists the accounts to fill: the one the instance's Apply signs in first, then the instance's own, then its
     * configuration's. Which passwords this PC remembers is asked of the store off the FX thread, then drawn.
     */
    private void fillPanel() {
        if (window.snapshot() != null) row = window.snapshot().instance(key).orElse(row);
        List<String> signingIn = window.signing(key).stream().flatMap(one -> one.emails().stream()).toList();
        Set<String> onInstance = row.android().flatMap(AndroidState::googleAccounts).orElse(Set.of());
        List<Sourced<AccountRecord>> configured = row.wanted() == null ? List.of() : row.wanted().accounts();
        Set<String> keys = FillAccounts.keys(signingIn, onInstance, configured);
        PasswordStore store = window.store();
        int load = ++loads;
        Thread.ofVirtual().name("live-fill-store").start(() -> {
            boolean available = store.available();
            Set<String> remembered = available ? ApplyPlan.remembered(keys, store) : Set.of();
            Platform.runLater(() -> {
                if (load != loads) return;
                canRemember = available;
                drawAccounts(FillAccounts.rows(signingIn, onInstance, configured, remembered));
            });
        });
    }

    private void drawAccounts(List<FillAccounts.Row> rows) {
        accounts.getChildren().clear();
        if (rows.isEmpty()) {
            Label none = Styles.on(new Label("No Google account on this instance or in its configuration."),
                    StyleClasses.MUTED_TEXT);
            none.setWrapText(true);
            accounts.getChildren().add(none);
            return;
        }
        for (FillAccounts.Row row : rows) {
            Label email = new Label(row.email());
            email.setWrapText(true);
            if (row.highlighted()) Styles.on(email, StyleClasses.STRONG_TEXT);
            Label source = Styles.on(new Label(row.source().displayName()),
                    row.highlighted() ? StyleClasses.OK_TEXT : StyleClasses.MUTED_TEXT);
            Button fillEmail = new Button("Fill email");
            fillEmail.setOnAction(e -> fillEmail(row.email()));
            Button fillPassword = new Button(row.password().displayName());
            fillPassword.setOnAction(e -> {
                if (row.password() == FillAccounts.PasswordFill.REMEMBERED) fillRemembered(row.email());
                else fillAsked(row.email());
            });
            HBox buttons = new HBox(6, fillEmail, fillPassword);
            VBox one = new VBox(3, email, Styles.on(source, StyleClasses.SMALL_TEXT), buttons);
            if (row.highlighted()) {
                Styles.on(one, StyleClasses.TILE);
                one.setPadding(new Insets(6));
            }
            accounts.getChildren().add(one);
        }
    }

    private void fillEmail(String email) {
        if (!InstanceSignIn.typeable(email.toCharArray())) {
            said.setText(UNTYPEABLE);
            return;
        }
        send(d -> {
            d.text(email);
            return "Filled the email, " + email.length() + " characters.";
        });
    }

    /** What the panel says of text {@code input text} would change: Google would get something else. */
    private static final String UNTYPEABLE = "BotMaker can't type that: input text types plain ASCII only, and turns %s"
            + " into a space. Type it on the instance yourself.";

    /** Types the password this PC remembers for {@code email}, read on the device's thread and cleared once typed. */
    private void fillRemembered(String email) {
        PasswordStore store = window.store();
        send(d -> {
            Optional<char[]> password = store.read(email);
            if (password.isEmpty()) return "This PC no longer remembers that password: use Fill password… instead.";
            char[] typed = password.get();
            try {
                if (!InstanceSignIn.typeable(typed)) return UNTYPEABLE;
                d.text(new String(typed));
                return "Filled the password, " + typed.length + " characters.";
            } finally {
                Arrays.fill(typed, '\0');
            }
        });
    }

    /**
     * Asks {@code email}'s password in the prompt, then types it. When the instance's Apply is signing that account in,
     * the batch gets it too, in place of the one it had, and remembers it once the sign-in is done if the user ticked
     * *Remember on this PC*; untouched, a password the Apply's prompt was told to remember would be kept instead.
     */
    private void fillAsked(String email) {
        String account = PasswordStore.key(email);
        Optional<EmulatorsWindow.Signing> signing = window.signing(key).stream()
                .filter(one -> one.emails().stream().anyMatch(signed -> PasswordStore.key(signed).equals(account)))
                .findFirst();
        boolean remembers = canRemember && signing.isPresent();
        Passwords.Answer answer = window.askPassword(email, remembers, remembers
                ? "BotMaker types it into the field you tapped. Remembered, it is kept in this PC's credential store"
                + " once this sign-in is done; no assistant ever sees it."
                : "BotMaker types it into the field you tapped, once, and keeps nothing; no assistant ever sees it.");
        if (answer == null) return;
        char[] password = answer.password();
        if (!InstanceSignIn.typeable(password)) {
            Arrays.fill(password, '\0');
            said.setText(UNTYPEABLE);
            return;
        }
        if (signing.isPresent()) {
            Passwords batch = signing.get().passwords();
            Passwords.Answer handed = new Passwords.Answer(password.clone(), answer.remember());
            // the batch's lock can be held while its own prompt waits on the FX thread: taken elsewhere
            Thread.ofVirtual().name("live-fill-batch").start(() -> {
                if (!batch.typedByUser(email, handed) && handed.remember()) Platform.runLater(() -> said.setText(
                        "The Apply has already signed this account in, or left it out: the password is not remembered."));
            });
        }
        send(d -> {
            try {
                d.text(new String(password));
                return "Filled the password, " + password.length + " characters.";
            } finally {
                Arrays.fill(password, '\0');
            }
        }, () -> Arrays.fill(password, '\0'));
    }

    // ---- frames ----

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

    // ---- the device ----

    /** Runs {@code act} on the device's thread, then says what it answered. */
    private void send(DeviceAct act) {
        send(act, () -> {});
    }

    /**
     * As {@link #send(DeviceAct)}; {@code dropped} runs too when {@code act} failed or never will (the device not
     * reached, the dialog closing), so what it holds is cleared whatever happens.
     */
    private void send(DeviceAct act, Runnable dropped) {
        try {
            device.execute(() -> {
                try {
                    String done = act.on(connected());
                    Platform.runLater(() -> said.setText(done));
                } catch (RuntimeException | Error failed) {
                    dropped.run();
                    closeConnection();
                    Platform.runLater(() -> said.setText("It did not reach " + instance.name() + ": "
                            + failed.getMessage()));
                }
            });
        } catch (RejectedExecutionException closing) {
            dropped.run();
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

    /** One act on the device; answers what the dialog says once it is done. */
    @FunctionalInterface
    private interface DeviceAct {
        String on(AdbDevice device);
    }
}
