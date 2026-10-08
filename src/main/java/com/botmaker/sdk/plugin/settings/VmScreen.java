package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.vm.GameCopy;
import com.botmaker.shared.vm.GuestLauncher;
import com.botmaker.shared.vm.GuestOs;
import com.botmaker.shared.vm.LinuxDisplay;
import com.botmaker.shared.vm.VmCredentials;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;
import com.botmaker.shared.vm.VmSetup;
import com.botmaker.shared.vnc.VncController;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
    /** What {@link #screen} belongs to, closed with the window; on the JavaFX thread. */
    private Opened opened;
    /** A Linux VM's: starts Steam on this window's display, to sign in. */
    private final Button startSteam = new Button("Start Steam here");
    private long shown = -1;
    /** How long the launcher check waits for a VM this window started to sign in. */
    private static final java.time.Duration GUEST_READY = java.time.Duration.ofMinutes(5);
    private final Map<GuestLauncher, Button> launcherButtons = new EnumMap<>(GuestLauncher.class);
    /** This PC's Steam and Epic games, listed when first opened, each copied into the VM when chosen. */
    private final MenuButton copyGame = new MenuButton("Copy a game from this PC");
    /** Studio's, set when the window shows. */
    private StudioServices services;
    /** Not while a game is copied: the copy would end mid-way. */
    private final Button shutDown = new Button("Shut down VM");
    /** Keys pressed over the screen and not yet released, on the JavaFX thread. */
    private final Set<Integer> held = new HashSet<>();
    /** Shut down from this window: the screen dropping is expected. On the JavaFX thread. */
    private boolean stoppingOnPurpose;

    private VmScreen(VmRecord vm) {
        this.vm = vm;
    }

    /** Opens {@code vm}'s screen over {@code owner}. Call it on the JavaFX application thread. */
    static void open(StudioServices services, Window owner, VmRecord vm) {
        new VmScreen(vm).show(services, owner);
    }

    private void show(StudioServices services, Window owner) {
        this.services = services;
        view.setPreserveRatio(true);
        view.setFocusTraversable(true);
        StackPane picture = new StackPane(view);
        picture.setMinSize(0, 0);
        view.fitWidthProperty().bind(picture.widthProperty());
        view.fitHeightProperty().bind(picture.heightProperty());
        status.setText("Starting " + vm.name() + " and connecting to its screen…");
        BorderPane root = new BorderPane(picture);
        HBox bar = launcherBar();
        root.setTop(bar);
        root.setBottom(status);
        BorderPane.setMargin(status, new Insets(6, 10, 6, 10));
        Stage stage = Modals.window(services, owner,
                Modals.Frame.modeless("Game VM — " + vm.name(), 1040, 720, 480, 360), root);
        shutDown.setOnAction(e -> {
            shutDown.setDisable(true);
            launcherButtons.values().forEach(b -> b.setDisable(true));
            copyGame.setDisable(true);
            stoppingOnPurpose = true;
            status.setText("Shutting the VM down: " + vm.guestOs().displayName()
                    + " closes its programs first, up to 3 minutes…");
            VmPower.shutDown(services, vm, done -> stage.close(), failed -> {
                stoppingOnPurpose = false;
                shutDown.setDisable(false);
                status.setText(failed);
                checkLaunchers(failed); // gives the launcher buttons back
            });
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(spacer, shutDown);

        AnimationTimer frames = new AnimationTimer() {
            @Override
            public void handle(long now) {
                VncController s = screen;
                if (s == null) return;
                if (!s.alive()) {
                    // Before the frame check: a dropped screen sends no more frames to notice it by.
                    if (!stoppingOnPurpose) {
                        status.setText("The VM's screen closed.");
                        // Most often the VM itself stopped (shut down elsewhere, or its system restarting): that, not
                        // the connection's own error, is what to say.
                        String failure = s.failure();
                        VmRecord was = vm;
                        Async.load("vm-screen-why-" + was.name(), () -> VmInventory.running(was), running ->
                                status.setText(running
                                        ? "The VM's screen closed" + (failure != null ? ": " + failure : ".")
                                        : "The VM " + was.name() + " stopped (shut down, or "
                                                + was.guestOs().displayName() + " restarting). "
                                                + "Open its screen again to start it."));
                    }
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
            Opened was = opened;
            opened = null;
            screen = null;
            // Off the JavaFX thread: ending a Linux display waits on the guest and on QEMU.
            if (was != null) Async.run("vm-screen-close-" + vm.name(), was::close, null);
        });
        stage.show();

        Async.load("vm-screen-" + vm.name(), () -> GuestCalls.unchecked(this::connect), started -> {
            if (!stage.isShowing()) {
                Async.run("vm-screen-close-" + vm.name(), started::close, null); // closed while it was connecting
                return;
            }
            vm = started.vm(); // a start can move its ports
            opened = started;
            screen = started.screen();
            status.setText("Your mouse and keyboard go to the VM while the pointer is over its screen. Closing "
                    + "this window leaves the VM running" + (started.display() != null ? "; this screen closes, with "
                    + "what runs on it." : "."));
            show(startSteam, started.display() != null);
            checkLaunchers(null);
            frames.start();
            view.requestFocus();
        }, why -> status.setText("The VM's screen didn't open: " + why));
    }

    /**
     * What this window shows: a Windows VM's own screen; a Linux VM's display of this window's own, as a bot has
     * one, opened once its guest has signed in, so Steam signs in where the user sees it.
     */
    private record Opened(VmRecord vm, VncController screen, VmSetup.Running running, LinuxDisplay display)
            implements AutoCloseable {

        @Override
        public void close() {
            if (display != null) display.close();
            running.close();
        }
    }

    /** Starts the VM when it is off and opens what this window shows ({@link Opened}). Off the JavaFX thread. */
    private Opened connect() throws IOException, InterruptedException {
        VmSetup.Running running = VmSetup.start(vm, credentials());
        if (running.vm().guestOs() != GuestOs.LINUX) return new Opened(running.vm(), running.screen(), running, null);
        try {
            long until = System.nanoTime() + GUEST_READY.toNanos();
            while (!VmSetup.guestReady(running.vm(), credentials())) {
                if (System.nanoTime() > until) throw new IOException("its Linux hasn't started yet.");
                Thread.sleep(2_000);
            }
            LinuxDisplay display = LinuxDisplay.open(running.vm(), "Game VM — " + running.vm().name());
            return new Opened(running.vm(), display.screen(), running, display);
        } catch (IOException | InterruptedException | RuntimeException e) {
            running.close();
            throw e;
        }
    }

    /**
     * The store launchers in the VM, one button each: ✓ when the guest has it, else Install, which downloads and
     * installs it silently. The VM is a Windows of its own, so a Steam or Epic game needs its launcher there,
     * signed in on this screen, and the game installed through it. A Linux VM has Steam and Legendary from its
     * setup: one button starts Steam on this window's display, to sign in.
     */
    private HBox launcherBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 10, 6, 10));
        if (vm.guestOs() == GuestOs.LINUX) {
            startSteam.setOnAction(e -> startSteam());
            show(startSteam, false);
            bar.getChildren().addAll(new Label("Steam and Legendary (Epic) were installed with this "
                    + vm.guestOs().displayName() + " VM."), startSteam);
            return bar;
        }
        bar.getChildren().add(new Label("Game launchers in the VM:"));
        for (GuestLauncher launcher : GuestLauncher.installable()) {
            Button b = new Button(launcher.displayName() + " …");
            b.setDisable(true);
            b.setOnAction(e -> install(launcher));
            launcherButtons.put(launcher, b);
            bar.getChildren().add(b);
        }
        copyGame.setDisable(true);
        // Listed afresh at each opening: a game installed on this PC meanwhile shows up.
        copyGame.setOnShowing(e -> {
            copyGame.getItems().setAll(note("Looking for this PC's games…"));
            Async.load("vm-copy-games", GameCopy::onThisPc, games -> {
                copyGame.getItems().clear();
                for (GameCopy.Source game : games) {
                    MenuItem item = new MenuItem(game.toString());
                    item.setOnAction(chosen -> copy(game));
                    copyGame.getItems().add(item);
                }
                if (games.isEmpty()) copyGame.getItems().add(note("No Steam or Epic game on this PC."));
            });
        });
        bar.getChildren().add(copyGame);
        return bar;
    }

    /** A menu row that only says something. */
    private static MenuItem note(String text) {
        MenuItem item = new MenuItem(text);
        item.setDisable(true);
        return item;
    }

    /**
     * Copies {@code game} from this PC into the VM and records it in the VM's launcher, so the launcher there
     * checks the files instead of downloading them. Off the JavaFX thread; its progress on the status line. The
     * window closed meanwhile, the copy goes on and says how it ended in Studio's status bar.
     */
    private void copy(GameCopy.Source game) {
        copyGame.setDisable(true);
        shutDown.setDisable(true);
        launcherButtons.values().forEach(b -> b.setDisable(true));
        status.setText("Copying " + game.name() + " into the VM…");
        Async.load("vm-copy-" + game.id(), () -> GuestCalls.unchecked(() -> {
            GameCopy.copy(vm, credentials(), game, said -> Platform.runLater(() -> status.setText(said)));
            return true;
        }), done -> ended("✓ " + game.name() + " is in the VM. Open " + game.launcher().displayName()
                + " on this screen: it checks the game's files, then lists it installed."),
                why -> ended(game.name() + " wasn't copied: " + why));
    }

    private void ended(String said) {
        services.status(said);
        if (screen == null) return; // the window has closed
        status.setText(said);
        shutDown.setDisable(false);
        checkLaunchers(said); // gives the launcher buttons and the copy back
    }

    /**
     * Asks the guest which launchers it has, off the JavaFX thread, once its Windows has signed in and its tools
     * answer: a VM this window just started has a screen long before that. {@code said} stays on the status line;
     * a failed check is added to it.
     */
    private void checkLaunchers(String said) {
        if (launcherButtons.isEmpty()) return; // a Linux VM: nothing to ask
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
        }), found -> {
            found.forEach((l, has) -> {
                Button b = launcherButtons.get(l);
                b.setText(has ? "✓ " + l.displayName() : "Install " + l.displayName());
                b.setDisable(has);
            });
            copyGame.setDisable(false);
        }, why -> {
            // Offered again, so a failed look leaves something to press.
            launcherButtons.forEach((l, b) -> {
                b.setText("Install " + l.displayName());
                b.setDisable(false);
            });
            copyGame.setDisable(false);
            status.setText((said == null ? "" : said + " ") + "Couldn't ask the VM for its launchers: " + why);
        });
    }

    /** Starts Steam on this window's Linux display, where its first start updates itself and asks to sign in. */
    private void startSteam() {
        Opened on = opened;
        if (on == null || on.display() == null) return;
        startSteam.setDisable(true);
        status.setText("Starting Steam. The first time, click Install in its window; it then updates itself (about "
                + "3 minutes) and asks you to sign in. Keep this window open while it does: closing it ends Steam.");
        Async.load("vm-steam-" + vm.name(), () -> GuestCalls.unchecked(() -> {
            // Closed meanwhile: its number may be another bot's now.
            if (!on.display().alive()) throw new IOException("this screen closed.");
            on.display().launch(LaunchSpec.parse("cli:steam"));
            return true;
        }), done -> startSteam.setDisable(false), why -> {
            startSteam.setDisable(false);
            status.setText("Steam didn't start: " + why);
        });
    }

    private static void show(Node node, boolean on) {
        node.setVisible(on);
        node.setManaged(on);
    }

    private void install(GuestLauncher launcher) {
        launcherButtons.values().forEach(b -> b.setDisable(true));
        copyGame.setDisable(true);
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
