package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.shared.Spawn;
import com.botmaker.shared.launch.UriLauncher;
import com.botmaker.shared.vm.Hypervisor;
import com.botmaker.shared.vm.Qemu;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;
import com.botmaker.shared.vm.VmSetup;
import com.botmaker.shared.vm.VmSize;
import com.botmaker.shared.vm.VmwareWorkstation;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Set up a game VM…": from nothing to a Windows VM a bot can run its game in, in one window.
 * <ol>
 *   <li>the hypervisor: VMware Workstation when it is installed, else QEMU, which this window installs (one
 *       administrator prompt), with the Windows Hypervisor Platform it runs on;</li>
 *   <li>the Windows disc image, which the user downloads from Microsoft and picks;</li>
 *   <li>its size, from this computer's;</li>
 *   <li>the setup itself: one sentence per step and the guest's screen as Windows installs, 20–40 minutes.</li>
 * </ol>
 * Closing the window or Cancel stops following the setup, and the VM stays as far as it got: Bot Settings
 * lists it, and this window resumes it ({@link #resume}).
 */
final class VmSetupWindow {

    /** Windows Setup's whole run with room to spare; {@code install} stops waiting after this. */
    private static final Duration INSTALL_TIMEOUT = Duration.ofHours(3);

    private final StudioServices services;
    private final Consumer<VmRecord> onReady;
    private final TextField name = new TextField();
    private final Label hypervisorStatus = note("Looking for VMware Workstation and QEMU…");
    private final Button installQemu = new Button("Install QEMU");
    private final Button platformOn = new Button("Turn on the Windows Hypervisor Platform");
    private final Label isoPath = note("No disc image chosen.");
    private final Spinner<Integer> memoryGb = new Spinner<>();
    private final Spinner<Integer> cores = new Spinner<>();
    private final Spinner<Integer> diskGb = new Spinner<>();
    private final Label progress = new Label();
    private final ImageView guest = new ImageView();
    private final Button setUp = new Button("Set up");
    private final Button stop = new Button("Stop");
    private final Button openScreen = new Button("Open VM screen");
    private final Button use = new Button("Run this bot's game in it");

    private Stage stage;
    private Hypervisor hypervisor = Hypervisor.UNKNOWN;
    private Path iso;
    private VmRecord vm;
    /** The setup thread this window follows; a stopped one's late answer is not its. Set on the FX thread. */
    private volatile Thread running;

    private VmSetupWindow(StudioServices services, VmRecord vm, Consumer<VmRecord> onReady) {
        this.services = services;
        this.vm = vm;
        this.onReady = onReady;
    }

    /** A new VM. {@code onReady} hears the VM once it is ready and the user picks it, on the JavaFX thread. */
    static void open(StudioServices services, Window owner, Consumer<VmRecord> onReady) {
        new VmSetupWindow(services, null, onReady).show(owner);
    }

    /** Goes on setting {@code vm} up, from wherever it stopped. */
    static void resume(StudioServices services, Window owner, VmRecord vm, Consumer<VmRecord> onReady) {
        new VmSetupWindow(services, vm, onReady).show(owner);
    }

    private void show(Window owner) {
        Label heading = new Label(vm == null ? "Set up a game VM" : "Set up " + vm.name());
        Styles.on(heading, Styles.DIALOG_HEADING);
        Label intro = note("A Windows of its own for the game: the bot plays there while you keep using your "
                + "computer. Windows installs by itself, in 20–40 minutes; the VM then stays on this computer.");

        VBox body = new VBox(14, heading, intro);
        if (vm == null) {
            body.getChildren().addAll(new Separator(), nameRow(), hypervisorPane(), new Separator(), isoPane(),
                    new Separator(), sizePane());
        }
        body.getChildren().addAll(new Separator(), setupPane());
        body.setPadding(new Insets(18));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);

        Button close = new Button("Close");
        close.setCancelButton(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, spacer, close);
        bar.setPadding(new Insets(10, 18, 14, 18));
        VBox root = new VBox(scroll, bar);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        stage = Modals.window(services, owner, Modals.Frame.modeless("Game VM", 720, 820, 560, 480), root);
        close.setOnAction(e -> stage.close());
        stage.setOnHidden(e -> stopSetup());
        stage.show();

        if (vm == null) {
            findHypervisor();
        } else {
            startSetup();
        }
    }

    private HBox nameRow() {
        name.setText(freeName());
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox row = new HBox(8, new Label("Name"), name);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** "Game VM", or "Game VM 2" and on when that is taken. */
    private static String freeName() {
        List<String> taken = VmInventory.list().stream().map(VmRecord::name).toList();
        String base = "Game VM";
        if (!taken.contains(base)) return base;
        for (int i = 2; ; i++) {
            if (!taken.contains(base + " " + i)) return base + " " + i;
        }
    }

    private VBox hypervisorPane() {
        installQemu.setOnAction(e -> installQemu());
        platformOn.setOnAction(e -> turnPlatformOn());
        Button vmware = new Button("VMware's download page");
        vmware.setOnAction(e -> browse(VmwareWorkstation.DOWNLOAD_PAGE));
        for (Button b : List.of(installQemu, platformOn)) {
            b.setVisible(false);
            b.setManaged(false);
        }
        Label which = note("QEMU installs from here and suits 2D and click-driven games: its Windows draws in "
                + "software. For 3D games, install VMware Workstation (a free download behind a Broadcom sign-in), "
                + "then open this window again: its Windows gets a DirectX 11 GPU.");
        HBox buttons = new HBox(8, installQemu, platformOn, vmware);
        return new VBox(8, title("1. Hypervisor"), hypervisorStatus, buttons, which);
    }

    private VBox isoPane() {
        Button page = new Button("Microsoft's download page");
        page.setOnAction(e -> browse(VmSetup.WINDOWS_DOWNLOAD_PAGE));
        Button choose = new Button("Choose the .iso…");
        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("The Windows 11 disc image");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Disc images", "*.iso"));
            File picked = chooser.showOpenDialog(stage);
            if (picked != null) {
                iso = picked.toPath();
                isoPath.setText(iso.toString());
            }
        });
        Label how = note("On Microsoft's page, under \"Download Windows 11 Disk Image (ISO)\", pick Windows 11 and "
                + "your language, and download the 64-bit file (about 7 GB). Then choose it here.");
        return new VBox(8, title("2. Windows"), how, new HBox(8, page, choose), isoPath);
    }

    private VBox sizePane() {
        VmSize defaults = VmSize.forThisHost();
        int hostGb = (int) (VmSize.hostMemoryMb() / 1024);
        int hostCores = Runtime.getRuntime().availableProcessors();
        setRange(memoryGb, VmSize.MIN_MEMORY_MB / 1024, Math.max(VmSize.MIN_MEMORY_MB / 1024, hostGb - 2),
                defaults.memoryMb() / 1024);
        setRange(cores, 1, hostCores, defaults.cpus());
        setRange(diskGb, VmSize.MIN_DISK_GB, 1024, defaults.diskGb());
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Memory (GB)"), memoryGb, note("This computer has " + hostGb + " GB."));
        grid.addRow(1, new Label("Cores"), cores, note("This computer has " + hostCores + "."));
        grid.addRow(2, new Label("Disk (GB)"), diskGb, note("Windows 11's minimum. It takes only what is written."));
        return new VBox(8, title("3. Size"), grid);
    }

    private static void setRange(Spinner<Integer> spinner, int min, int max, int value) {
        spinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(min, max,
                Math.clamp(value, min, max)));
        spinner.setEditable(true);
        spinner.setPrefWidth(110);
    }

    private VBox setupPane() {
        progress.setWrapText(true);
        guest.setPreserveRatio(true);
        guest.setFitWidth(640);
        setUp.setDefaultButton(true);
        setUp.setOnAction(e -> startSetup());
        stop.setOnAction(e -> {
            stopSetup();
            progress.setText(progress.getText() + "\nStopped. The VM stays as far as it got: set it up again from "
                    + "Bot Settings to go on.");
        });
        openScreen.setOnAction(e -> VmScreen.open(services, stage, vm));
        use.setOnAction(e -> {
            onReady.accept(vm);
            stage.close();
        });
        for (Button b : List.of(stop, openScreen, use)) {
            b.setVisible(false);
            b.setManaged(false);
        }
        Label done = note("Once it's ready, Open VM screen lets you use the VM by hand: install the game, sign in "
                + "to Steam or Epic. The bot then starts the game there itself.");
        return new VBox(8, title(vm == null ? "4. Set up" : "Setup"), new HBox(8, setUp, stop, openScreen, use),
                progress, guest, done);
    }

    /** The hypervisor found, and whether what it runs on is on: always for VMware, which brings its own. */
    private record Found(Hypervisor hypervisor, boolean platformOn) {}

    /** Finds the hypervisor and what it lacks, off the JavaFX thread: the platform check runs PowerShell. */
    private void findHypervisor() {
        Async.load("vm-hypervisor", () -> {
            Hypervisor found = Hypervisor.detect();
            return new Found(found, found != Hypervisor.QEMU || VmSetup.hypervisorPlatformOn());
        }, found -> {
            hypervisor = found.hypervisor();
            boolean platform = found.platformOn();
            show(installQemu, hypervisor == Hypervisor.UNKNOWN);
            show(platformOn, hypervisor == Hypervisor.QEMU && !platform);
            hypervisorStatus.setText(switch (hypervisor) {
                case VMWARE -> "✓ VMware Workstation: its Windows gets a GPU, for 2D and 3D games.";
                case QEMU -> platform ? "✓ QEMU, on the Windows Hypervisor Platform."
                        : "QEMU is installed, but the Windows Hypervisor Platform it runs on is off. Turn it on, "
                        + "then restart Windows.";
                case UNKNOWN -> "Neither VMware Workstation nor QEMU is installed. Install QEMU from here (Windows "
                        + "asks once for administrator rights).";
            });
        });
    }

    private void installQemu() {
        installQemu.setDisable(true);
        hypervisorStatus.setText("Installing QEMU with winget: Windows asks for administrator rights…");
        Async.load("vm-install-qemu", () -> {
            try {
                return Qemu.install();
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Stopped.", e);
            }
        }, done -> {
            installQemu.setDisable(false);
            if (!done.ok()) hypervisorStatus.setText("QEMU didn't install: " + lastLine(done));
            findHypervisor();
        }, why -> {
            installQemu.setDisable(false);
            hypervisorStatus.setText("QEMU didn't install: " + why);
        });
    }

    private void turnPlatformOn() {
        platformOn.setDisable(true);
        hypervisorStatus.setText("Turning on the Windows Hypervisor Platform: Windows asks for administrator rights…");
        Async.load("vm-platform", () -> {
            try {
                return VmSetup.enableHypervisorPlatform();
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Stopped.", e);
            }
        }, done -> {
            platformOn.setDisable(false);
            hypervisorStatus.setText(done.ok()
                    ? "The Windows Hypervisor Platform turns on when Windows restarts. Restart, then open this "
                    + "window again."
                    : "It didn't turn on: " + lastLine(done));
        }, why -> {
            platformOn.setDisable(false);
            hypervisorStatus.setText("It didn't turn on: " + why);
        });
    }

    /** Prepares the VM when it is new, then installs Windows, following each step here. */
    private void startSetup() {
        String chosenName = vm == null ? name.getText().strip() : vm.name();
        VmSize size;
        try {
            size = vm == null ? new VmSize(cores.getValue(), memoryGb.getValue() * 1024, diskGb.getValue()) : vm.size();
        } catch (IllegalArgumentException e) {
            progress.setText(e.getMessage());
            return;
        }
        Hypervisor chosenHypervisor = hypervisor;
        Path chosenIso = iso;
        showRunning(true);
        progress.setText("Starting…");
        VmSetup.Listener listener = new VmSetup.Listener() {
            @Override
            public void step(String sentence) {
                Thread me = Thread.currentThread();
                Platform.runLater(() -> {
                    if (running == me) progress.setText(sentence);
                });
            }

            @Override
            public void frame(BufferedImage screen) {
                Thread me = Thread.currentThread();
                Platform.runLater(() -> {
                    if (running == me) guest.setImage(ScreenCapture.toFxImage(screen));
                });
            }
        };
        VmRecord known = vm;
        running = Thread.ofPlatform().daemon().name("vm-setup-" + chosenName).start(() -> {
            Thread me = Thread.currentThread();
            try {
                VmRecord made = known != null ? known
                        : VmSetup.prepare(chosenName, chosenIso, chosenHypervisor, size, listener);
                Platform.runLater(() -> vm = made);
                VmRecord ready = VmSetup.install(made, listener, INSTALL_TIMEOUT);
                Platform.runLater(() -> {
                    if (running == me) ready(ready);
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (running == me) failed(e.getMessage());
                });
            } catch (InterruptedException e) {
                // Stopped from this window: it already said so.
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    if (running == me) failed(e.toString());
                });
            }
        });
    }

    private void stopSetup() {
        Thread t = running;
        running = null;
        if (t != null) t.interrupt();
        showRunning(false);
    }

    private void ready(VmRecord ready) {
        vm = ready;
        running = null;
        showRunning(false);
        setUp.setVisible(false);
        setUp.setManaged(false);
        show(openScreen, true);
        show(use, true);
        progress.setText("✓ " + ready.name() + " is ready: Windows is installed and signed in.");
    }

    private void failed(String why) {
        running = null;
        showRunning(false);
        progress.setText("⚠ " + why + (vm != null ? "\nWhat was done stays: Set up goes on from there." : ""));
    }

    private void showRunning(boolean on) {
        setUp.setDisable(on);
        show(stop, on);
    }

    private static void show(Button button, boolean on) {
        button.setVisible(on);
        button.setManaged(on);
    }

    private static String lastLine(Spawn.Completed done) {
        String[] lines = done.output().strip().split("\\R");
        return lines.length == 0 || lines[lines.length - 1].isBlank() ? "exit code " + done.exitCode()
                : lines[lines.length - 1];
    }

    private static void browse(String url) {
        Async.run("open-browser", () -> UriLauncher.open(url), null);
    }

    private static Label title(String text) {
        return Styles.on(new Label(text), Styles.STRONG_TEXT);
    }

    private static Label note(String text) {
        Label l = Styles.on(new Label(text), Styles.DIALOG_HINT);
        l.setWrapText(true);
        return l;
    }
}
