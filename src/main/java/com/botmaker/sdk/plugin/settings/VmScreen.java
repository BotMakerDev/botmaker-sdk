package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.shared.vm.VmCredentials;
import com.botmaker.shared.vm.VmRecord;
import com.botmaker.shared.vm.VmSetup;
import com.botmaker.shared.vnc.VncController;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * A game VM's screen in a window of Studio's, to use it by hand: install a game, sign in to Steam. The mouse and
 * keyboard over the picture go to the guest, over the same VNC connection a bot uses, so the hypervisor's own
 * window is never needed. Opening it starts the VM when it is off; closing it leaves the VM running.
 */
final class VmScreen {

    private final VmRecord vm;
    private final ImageView view = new ImageView();
    private final Label status = new Label();
    private volatile VncController screen;
    private long shown = -1;
    /** Keys pressed over the screen and not yet released, on the JavaFX thread. */
    private final Set<Integer> held = new HashSet<>();

    private VmScreen(VmRecord vm) {
        this.vm = vm;
    }

    /** Opens {@code vm}'s screen over {@code owner}. Call it on the JavaFX application thread. */
    static void open(StudioServices services, Window owner, VmRecord vm) {
        new VmScreen(vm).show(services, owner);
    }

    private void show(StudioServices services, Window owner) {
        view.setPreserveRatio(true);
        view.setFocusTraversable(true);
        StackPane picture = new StackPane(view);
        picture.setMinSize(0, 0);
        view.fitWidthProperty().bind(picture.widthProperty());
        view.fitHeightProperty().bind(picture.heightProperty());
        status.setText("Starting " + vm.name() + " and connecting to its screen…");
        BorderPane root = new BorderPane(picture);
        root.setBottom(status);
        BorderPane.setMargin(status, new Insets(6, 10, 6, 10));
        Stage stage = Modals.window(services, owner,
                Modals.Frame.modeless("Game VM — " + vm.name(), 1040, 720, 480, 360), root);

        AnimationTimer frames = new AnimationTimer() {
            @Override
            public void handle(long now) {
                VncController s = screen;
                if (s == null) return;
                if (!s.alive()) {
                    // Before the frame check: a dropped screen sends no more frames to notice it by.
                    status.setText("The VM's screen closed" + (s.failure() != null ? ": " + s.failure() : "."));
                    stop();
                    return;
                }
                long count = s.frames();
                if (count == shown) return;
                shown = count;
                BufferedImage frame = s.captureScreen();
                if (frame != null) view.setImage(ScreenCapture.toFxImage(frame));
            }
        };
        input();
        stage.setOnHidden(e -> {
            frames.stop();
            releaseKeys();
            VncController s = screen;
            screen = null;
            if (s != null) s.close();
        });
        stage.show();

        Async.load("vm-screen-" + vm.name(), () -> {
            try {
                VmCredentials credentials = VmCredentials.load(vm.folder())
                        .orElseThrow(() -> new IOException("The VM has lost its passwords."));
                return VmSetup.start(vm, credentials).screen();
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Stopped.", e);
            }
        }, connected -> {
            if (!stage.isShowing()) {
                connected.close(); // closed while it was connecting
                return;
            }
            screen = connected;
            status.setText("Your mouse and keyboard go to the VM while the pointer is over its screen. Closing "
                    + "this window leaves the VM running.");
            frames.start();
            view.requestFocus();
        }, why -> status.setText("The VM's screen didn't open: " + why));
    }

    /** Mouse and keys over the picture, in the guest's own pixels. */
    private void input() {
        view.setOnMouseMoved(this::move);
        view.setOnMouseDragged(this::move);
        view.setOnMousePressed(e -> {
            view.requestFocus();
            button(e, true);
        });
        view.setOnMouseReleased(e -> button(e, false));
        view.setOnScroll((ScrollEvent e) -> {
            VncController s = screen;
            if (s != null && e.getDeltaY() != 0) s.scroll(e.getDeltaY() > 0 ? 1 : -1);
        });
        view.setOnKeyPressed((KeyEvent e) -> {
            VncController s = screen;
            int key = VmKeys.virtualKey(e.getCode().getCode());
            if (s != null && held.add(key)) s.keyDown(key);
            e.consume();
        });
        view.setOnKeyReleased((KeyEvent e) -> {
            VncController s = screen;
            int key = VmKeys.virtualKey(e.getCode().getCode());
            if (s != null && held.remove(key)) s.keyUp(key);
            e.consume();
        });
        // Alt+Tab away releases Alt somewhere else: let go of what is held, or the guest keeps it down.
        view.focusedProperty().addListener((o, was, now) -> {
            if (!now) releaseKeys();
        });
    }

    /** Lets go, in the guest, of every key still held down there. */
    private void releaseKeys() {
        VncController s = screen;
        if (s != null) held.forEach(s::keyUp);
        held.clear();
    }

    private void move(MouseEvent e) {
        VncController s = screen;
        int[] at = guest(e);
        if (s != null && at != null) s.mouseMove(at[0], at[1]);
    }

    private void button(MouseEvent e, boolean press) {
        VncController s = screen;
        int[] at = guest(e);
        if (s == null || at == null) return;
        int button = e.getButton() == MouseButton.SECONDARY ? 3 : e.getButton() == MouseButton.MIDDLE ? 2 : 1;
        s.mouseMove(at[0], at[1]);
        s.mouseButton(button, press);
    }

    /** Where {@code e} falls on the guest's screen, or {@code null} before the first frame. */
    private int[] guest(MouseEvent e) {
        if (view.getImage() == null) return null;
        double shownWidth = view.getBoundsInLocal().getWidth();
        double shownHeight = view.getBoundsInLocal().getHeight();
        if (shownWidth <= 0 || shownHeight <= 0) return null;
        double w = view.getImage().getWidth();
        double h = view.getImage().getHeight();
        int x = (int) Math.clamp(e.getX() * w / shownWidth, 0, w - 1);
        int y = (int) Math.clamp(e.getY() * h / shownHeight, 0, h - 1);
        return new int[] {x, y};
    }
}
