// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.screen.ScreenOverlay;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;
import com.botmaker.shared.capture.WindowPointer;
import com.botmaker.shared.capture.WindowPointer.Pointed;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.RunningProbe;
import com.botmaker.shared.launch.WindowIdentity;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The user points at the game's window (spec {@code 2026-11-26-point-at-window-design.md}): Studio's windows are
 * iconified, shared's {@link WindowPointer} waits for the click off the FX thread, the windows come back, and the
 * confirm step (layout A) shows what was clicked before it counts. Keep saves it as this PC's window for the game,
 * the doubt picker's path ({@link AutomaticTab#picks}), so a run reads it as it reads a picked one.
 */
final class PointAtWindow {

    static final String BUTTON = "⌖ Point at the game's window";
    static final String HINT = "The game must be open. Studio hides while you point: click the game's window, "
            + "or press Esc.";
    static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final double STILL_WIDTH = 420;
    private static final double STILL_HEIGHT = 240;

    /** Whether this desktop has a pointer, once probed: null until then. */
    private static volatile Boolean available;
    /** Whether a pointing is under way; touched on the FX thread only, so a second click starts none. */
    private static boolean pointing;

    /** How a guard weighs on the confirm step: Keep disabled, or a warning line beside it. */
    enum Level { REFUSED, WARNED }

    /** A line the confirm step shows about the clicked window. */
    record Guard(Level level, String text) {
    }

    private PointAtWindow() {
    }

    /**
     * Runs {@code then} on the FX thread when this desktop lets the user point (not GNOME Wayland, not a headless
     * host); never otherwise. The first ask probes off the FX thread, since KDE's probe asks the session bus.
     */
    static void ifAvailable(Runnable then) {
        Boolean known = available;
        if (known != null) {
            if (known) then.run();
            return;
        }
        Thread.ofVirtual().name("point-at-window-probe").start(() -> {
            boolean answer;
            try {
                answer = WindowPointer.current().available();
            } catch (RuntimeException | LinkageError noPointer) {
                answer = false;
            }
            available = answer;
            if (answer) Platform.runLater(then);
        });
    }

    /** The button both the Automatic tab and the doubt picker show, its hint as a tooltip. */
    static Button button(Runnable onPoint) {
        Button point = new Button(BUTTON);
        point.setTooltip(new Tooltip(HINT));
        point.setOnAction(e -> onPoint.run());
        return point;
    }

    /**
     * What the confirm step says about {@code window}: Studio's own (its pid) and one BotMaker cannot name refused;
     * a launcher's class or a window with no name of its own warned, since the user may mean it; empty for an
     * ordinary window.
     */
    static Optional<Guard> guard(GenericWindow window, long studioPid) {
        if (window.getPid() != GenericWindow.NO_PID && window.getPid() == studioPid) {
            return Optional.of(new Guard(Level.REFUSED, "That's BotMaker Studio — point at the game"));
        }
        if (WindowIdentity.of(window) == null) {
            return Optional.of(new Guard(Level.REFUSED, "BotMaker can't name this window: it has no title of its "
                    + "own yet. Point again once the game shows its own window"));
        }
        if (RunningProbe.launcherClass(window.getWindowClass())) {
            return Optional.of(new Guard(Level.WARNED, "This looks like " + window.getWindowClass()
                    + ", a launcher — the game's window is usually another one"));
        }
        if (WindowIdentity.isPlaceholder(window)) {
            return Optional.of(new Guard(Level.WARNED,
                    "This window has no name yet; the next run may not find it again"));
        }
        return Optional.empty();
    }

    /** {@link #start(StudioServices, LaunchSpec, Window, BooleanSupplier, Consumer)}, always still asked. */
    static void start(StudioServices services, LaunchSpec target, Window owner, Consumer<Optional<String>> done) {
        start(services, target, owner, () -> true, done);
    }

    /**
     * Hides Studio, lets the user point, then asks to keep what was clicked; on the FX thread, and nothing when a
     * pointing is under way. {@code done} gets the kept window's identity ({@link WindowIdentity#spec}), or empty
     * when nothing was kept (Esc, the timeout, Cancel, the dialog closed, or {@code stillAsked} false by then: the
     * question it answers is gone, as a run's doubt once the run took its guess).
     */
    static void start(StudioServices services, LaunchSpec target, Window owner, BooleanSupplier stillAsked,
                      Consumer<Optional<String>> done) {
        if (pointing) return;
        pointing = true;
        List<Stage> hidden = Window.getWindows().stream()
                .filter(w -> w instanceof Stage stage && stage.isShowing() && !stage.isIconified())
                .map(Stage.class::cast).toList();
        hidden.forEach(stage -> stage.setIconified(true));
        long started = System.nanoTime();
        // A platform thread: Windows' hooks belong to the OS thread that installs them and pumps its messages.
        Thread.ofPlatform().daemon().name("point-at-window").start(() -> {
            Pointed pointed;
            try {
                pointed = WindowPointer.current().point(TIMEOUT);
            } catch (RuntimeException | LinkageError failed) {
                pointed = new Pointed.Unreadable("pointing failed: " + failed.getMessage());
            }
            Pointed answer = pointed;
            boolean timedOut = System.nanoTime() - started >= TIMEOUT.toNanos();
            Platform.runLater(() -> {
                pointing = false;
                hidden.forEach(stage -> stage.setIconified(false));
                if (answer instanceof Pointed.Cancelled) {
                    if (timedOut) services.status("No window was clicked within 60 s; nothing changed.");
                    done.accept(Optional.empty());
                } else if (!stillAsked.getAsBoolean()) {
                    services.status("The run took its best guess while you pointed; nothing was kept. Point from "
                            + "the Choose capture source window's Automatic tab for the next runs.");
                    done.accept(Optional.empty());
                } else {
                    new Confirm(services, target, owner, stillAsked, done).show(answer);
                }
            });
        });
    }

    /** The confirm step: what was clicked, Keep this window, Point again, Cancel. */
    private static final class Confirm {

        private final StudioServices services;
        private final LaunchSpec target;
        private final Window owner;
        private final BooleanSupplier stillAsked;
        private final Consumer<Optional<String>> done;
        private final Label status = Styles.on(new Label(), Styles.DIALOG_HINT);
        private Stage stage;
        private boolean answered;

        Confirm(StudioServices services, LaunchSpec target, Window owner, BooleanSupplier stillAsked,
                Consumer<Optional<String>> done) {
            this.services = services;
            this.target = target;
            this.owner = owner;
            this.stillAsked = stillAsked;
            this.done = done;
        }

        void show(Pointed pointed) {
            Label heading = Styles.on(new Label("Is this the game's window?"), Styles.DIALOG_HEADING);
            ImageView still = new ImageView();
            still.setFitWidth(STILL_WIDTH);
            still.setFitHeight(STILL_HEIGHT);
            still.setPreserveRatio(true);
            StackPane frame = new StackPane(still);
            frame.setMinSize(STILL_WIDTH, STILL_HEIGHT);
            Label title = Styles.on(new Label(), Styles.STRONG_TEXT);
            Label meta = Styles.on(new Label(), Styles.MUTED_TEXT, Styles.SMALL_TEXT);
            Label warning = Styles.on(new Label(), Styles.WARNING_TEXT);
            warning.setWrapText(true);
            status.setWrapText(true);

            Button keep = Styles.on(new Button("Keep this window"), Styles.PRIMARY_BUTTON);
            keep.setDefaultButton(true);
            Button again = new Button("Point again");
            Button cancel = new Button("Cancel");
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> stage.close());
            again.setOnAction(e -> {
                answered = true; // the next pointing answers instead
                stage.close();
                start(services, target, owner, stillAsked, done);
            });

            switch (pointed) {
                case Pointed.Window(GenericWindow window) -> {
                    title.setText(window.getTitle() == null || window.getTitle().isBlank() ? "(untitled)"
                            : window.getTitle());
                    meta.setText(meta(window));
                    Optional<Guard> guard = guard(window, ProcessHandle.current().pid());
                    guard.ifPresent(g -> warning.setText(g.text()));
                    keep.setDisable(guard.map(g -> g.level() == Level.REFUSED).orElse(false));
                    keep.setOnAction(e -> keep(window));
                    grabStill(window, still);
                }
                case Pointed.Unreadable(String why) -> {
                    title.setText("No window kept");
                    warning.setText(why);
                    keep.setDisable(true);
                }
                case Pointed.Cancelled() -> throw new IllegalStateException("a cancelled pointing has nothing to show");
            }
            boolean warned = !warning.getText().isEmpty();
            warning.setVisible(warned);
            warning.setManaged(warned);

            HBox spacer = new HBox();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox bar = new HBox(8, spacer, cancel, again, keep);
            bar.setAlignment(Pos.CENTER_LEFT);
            VBox root = new VBox(10, heading, frame, title, meta, warning, status, bar);
            root.setPadding(new Insets(14));
            stage = Modals.window(services, owner,
                    Modals.Frame.modal("Is this the game's window?", 480, 470, 420, 380), root);
            stage.setOnHidden(e -> {
                if (!answered) done.accept(Optional.empty());
            });
            stage.show();
            stage.toFront();
        }

        /** Class and size, either left out when unknown. */
        private static String meta(GenericWindow window) {
            String windowClass = window.getWindowClass();
            Rectangle r = window.getRect();
            String size = r == null ? null : r.width + "×" + r.height;
            if (windowClass == null || windowClass.isBlank()) return size == null ? "" : size;
            return size == null ? windowClass : windowClass + " · " + size;
        }

        private void keep(GenericWindow window) {
            if (!stillAsked.getAsBoolean()) {
                Styles.pick(status, Styles.WARNING_TEXT, Styles.WARNING_TEXT);
                status.setText("Not kept: the run took its best guess meanwhile.");
                return;
            }
            WindowIdentity identity = WindowIdentity.of(window);
            Optional<String> refused = AutomaticTab.picks(services).save(target, identity);
            if (refused.isPresent()) {
                Styles.pick(status, Styles.WARNING_TEXT, Styles.WARNING_TEXT);
                status.setText("Not kept: " + refused.get());
                return;
            }
            answered = true;
            stage.close();
            done.accept(Optional.of(identity.spec()));
        }

        /** The clicked window's still, off the FX thread; none leaves the frame empty. */
        private static void grabStill(GenericWindow window, ImageView still) {
            Thread.ofVirtual().name("point-at-window-still").start(() -> {
                try {
                    BufferedImage shot = NativeControllerFactory.get().captureWindow(window);
                    if (shot != null) {
                        var image = ScreenOverlay.toFxImage(shot);
                        Platform.runLater(() -> still.setImage(image));
                    }
                } catch (RuntimeException | LinkageError notGrabbed) {
                    // the confirm step still names the window by its title, class and size
                }
            });
        }
    }
}
