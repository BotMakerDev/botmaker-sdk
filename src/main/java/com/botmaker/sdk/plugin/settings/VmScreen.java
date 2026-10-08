package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.shared.vm.GuestLauncher;
import com.botmaker.shared.vm.VmCredentials;
import com.botmaker.shared.vm.VmRecord;
import com.botmaker.shared.vm.VmSetup;
import com.botmaker.shared.vnc.VncController;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A game VM's screen in a window of Studio's, to use it by hand: install a game, sign in to Steam. The mouse and
 * keyboard over the picture go to the guest, over the same VNC connection a bot uses, so the hypervisor's own
 * window is never needed. Opening it starts the VM when it is off; closing it leaves the VM running.
 */
final class VmScreen {

    /** As the start left it; read off the JavaFX thread by the launcher checks. */
    private volatile VmRecord vm;
    private final ImageView view = new ImageView();
    private final Label status = new Label();
    private volatile VncController screen;
    private long shown = -1;
    /** How long the launcher check waits for a VM this window started to sign in. */
    private static final java.time.Duration GUEST_READY = java.time.Duration.ofMinutes(5);
    private final Map<GuestLauncher, Button> launcherButtons = new EnumMap<>(GuestLauncher.class);
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
        root.setTop(launcherBar());
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

        Async.load("vm-screen-" + vm.name(), () -> GuestCalls.unchecked(() -> VmSetup.start(vm, credentials())), started -> {
            if (!stage.isShowing()) {
                started.close(); // closed while it was connecting
                return;
            }
            vm = started.vm(); // a start can move its ports
            screen = started.screen();
            status.setText("Your mouse and keyboard go to the VM while the pointer is over its screen. Closing "
                    + "this window leaves the VM running.");
            checkLaunchers(null);
            frames.start();
            view.requestFocus();
        }, why -> status.setText("The VM's screen didn't open: " + why));
    }

    /**
     * The store launchers in the VM, one button each: ✓ when the guest has it, else Install, which downloads and
     * installs it silently. The VM is a Windows of its own, so a Steam or Epic game needs its launcher there,
     * signed in on this screen, and the game installed through it.
     */
    private HBox launcherBar() {
        HBox bar = new HBox(8, new Label("Game launchers in the VM:"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 10, 6, 10));
        for (GuestLauncher launcher : GuestLauncher.installable()) {
            Button b = new Button(launcher.displayName() + " …");
            b.setDisable(true);
            b.setOnAction(e -> install(launcher));
            launcherButtons.put(launcher, b);
            bar.getChildren().add(b);
        }
        return bar;
    }

    /**
     * Asks the guest which launchers it has, off the JavaFX thread, once its Windows has signed in and its tools
     * answer: a VM this window just started has a screen long before that. {@code said} stays on the status line;
     * a failed check is added to it.
     */
    private void checkLaunchers(String said) {
        Async.load("vm-launchers-" + vm.name(), () -> GuestCalls.unchecked(() -> {
            VmCredentials credentials = credentials();
            long until = System.nanoTime() + GUEST_READY.toNanos();
            while (!VmSetup.guestReady(vm, credentials)) {
                if (System.nanoTime() > until) throw new IOException("its Windows hasn't signed in yet.");
                Thread.sleep(3_000);
            }
            Map<GuestLauncher, Boolean> found = new EnumMap<>(GuestLauncher.class);
            for (GuestLauncher l : GuestLauncher.installable()) found.put(l, VmSetup.guestHas(vm, credentials, l));
            return found;
        }), found -> found.forEach((l, has) -> {
            Button b = launcherButtons.get(l);
            b.setText(has ? "✓ " + l.displayName() : "Install " + l.displayName());
            b.setDisable(has);
        }), why -> {
            // Offered again, so a failed look leaves something to press.
            launcherButtons.forEach((l, b) -> {
                b.setText("Install " + l.displayName());
                b.setDisable(false);
            });
            status.setText((said == null ? "" : said + " ") + "Couldn't ask the VM for its launchers: " + why);
        });
    }

    private void install(GuestLauncher launcher) {
        launcherButtons.values().forEach(b -> b.setDisable(true));
        status.setText("Installing " + launcher.displayName() + " in the VM: it downloads there, a few minutes…");
        Async.load("vm-install-" + launcher.id(), () -> GuestCalls.unchecked(() -> {
            VmSetup.installInGuest(vm, credentials(), launcher);
            return true;
        }), done -> {
            String said = "✓ " + launcher.displayName() + " is installed in the VM. Open it on this screen and sign "
                    + "in, then install the game through it.";
            status.setText(said);
            checkLaunchers(said);
        }, why -> {
            String said = launcher.displayName() + " didn't install: " + why;
            status.setText(said);
            checkLaunchers(said);
        });
    }

    private VmCredentials credentials() throws IOException {
        return VmCredentials.load(vm.folder()).orElseThrow(() -> new IOException("The VM has lost its passwords."));
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
