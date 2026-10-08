package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.session.display.SessionBackends;
import com.botmaker.session.SessionBackend;
import com.botmaker.shared.platform.Os;
import com.botmaker.shared.vm.GuestOs;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * ⚙ Bot Settings: the bot's {@code @Managed("settings")} value — how it clicks and looks, and where the game
 * runs: a private display (Linux) or a game VM (Windows, {@link VmSetupWindow} sets one up), or the desktop with
 * or without taking over the mouse and keyboard.
 *
 * <p>The settings are Java the bot compiles, so the window belongs to the plugin that owns the type, and it edits the one expression
 * {@code Sdk.settings()} returns through the host — the host writes the call, this window never sees Java.
 *
 * <p><b>A project whose {@code Sdk.java} has no {@code settings()}</b> — every project made before the method
 * existed — gets the method's text to paste, with Save off: the host writes a whole holder file when there is
 * none, and never adds a method to one the user owns. Until the method is there the bot runs on
 * {@link BotSettings#DEFAULTS}, which is what the window then shows.
 */
public final class BotSettingsWindow {

    /** The value, declared once in {@link SdkValues}. */
    public static final ManagedHandle<BotSettings> SETTINGS = ManagedHandle.of(SdkValues.SETTINGS);

    /** Only Linux has a private display to offer. */
    private static final boolean LINUX = Os.current() == Os.LINUX;

    private final StudioServices services;
    private final Window owner;

    private final ToggleGroup where = new ToggleGroup();
    private final RadioButton privateDisplay = new RadioButton(BotSettings.Where.PRIVATE_DISPLAY.displayName());
    private final RadioButton myDesktop = new RadioButton(BotSettings.Where.MY_DESKTOP.displayName());
    private final RadioButton inVm = new RadioButton(BotSettings.Where.VM.displayName());
    /** Windows: this computer's game VMs; the one picked is the {@link VmChoice} run property. */
    private final ComboBox<VmRecord> vms = new ComboBox<>();
    private final Label vmStatus = note("");
    /** Windows: the picked VM's power; {@code null} on Linux. */
    private VmPower power;
    /**
     * The bot's Java's choice, read on Windows: a private display is the desktop there and is kept when the
     * desktop is picked, for the bot on Linux. On Linux a VM bot shows as isolated, and is saved as what the
     * window shows: a private display.
     */
    private BotSettings.Where windowsWhere = BotSettings.Where.PRIVATE_DISPLAY;
    private final CheckBox takeOver = new CheckBox("Take over the mouse and keyboard");
    private final CheckBox randomizeClicks = new CheckBox("Click a random point inside the match, not its centre");
    /**
     * The bot's {@code debug}, kept as it was: debug output is the host's (Studio's 🐞 Debug, the
     * {@code botmaker.debug} run property), so this window shows no tick for it, and nothing here may change a
     * value the user cannot see.
     */
    @SuppressWarnings("deprecation")
    private boolean debug = BotSettings.DEFAULTS.debug();
    private final Spinner<Integer> foundDelay = new Spinner<>(0, 600_000, 500, 50);
    private final Spinner<Integer> notFoundDelay = new Spinner<>(0, 600_000, 200, 50);
    private final Spinner<Double> confidence = new Spinner<>(0.0, 1.0, 0.8, 0.05);
    private final Spinner<Double> compareMargin = new Spinner<>(0.0, 1.0, 0.05, 0.01);
    private final Spinner<Integer> maxRetryAttempts = new Spinner<>(1, 600_000, 20, 1);
    private final ComboBox<BotSettings.InputBackend> inputBackend = new ComboBox<>();
    private final ComboBox<BotSettings.DisplayBackend> displayBackend = new ComboBox<>();
    private final Label backendStatus = note("");
    private final Button installBackend = new Button();

    private BotSettingsWindow(StudioServices services, Window owner) {
        this.services = services;
        this.owner = owner;
    }

    /** The ⚙ Bot Settings press. */
    public static void open(ActionContext context) {
        StudioServices services = context.services();
        open(services, Modals.owner(services));
    }

    /** Opens the window over {@code owner}. Call it on the JavaFX application thread. */
    public static void open(StudioServices services, Window owner) {
        new BotSettingsWindow(services, owner).show();
    }

    /**
     * The bot's settings as its Java says them, or {@link BotSettings#DEFAULTS} when it declares none this can
     * read — what the bot would run with. Never writes anything.
     */
    public static BotSettings current(StudioServices services) {
        return SETTINGS.read(services).orElse(BotSettings.DEFAULTS);
    }

    private void show() {
        // Asks the host to write Sdk.java first when the project has none; empty when there is still no method.
        Optional<ValueContext> ctx = SETTINGS.openOrCreate(services);
        BotSettings current = ctx.flatMap(SETTINGS::read).orElse(BotSettings.DEFAULTS);
        seed(current);

        Label heading = new Label("How this bot clicks and looks");
        Styles.on(heading, Styles.DIALOG_HEADING);
        Label intro = note("Written into Sdk.settings(), in your bot's own Java, and applied before its first "
                + "click — wherever the bot runs.");

        VBox root = new VBox();
        Stage stage = Modals.window(services, owner, Modals.Frame.modal("Bot Settings", 620, 720, 520, 420), root);
        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Save");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            BotSettings chosen = collect();
            // Which VM is this computer's fact, kept beside the project rather than in the bot's Java.
            // Only a VM that is set up: one still installing would boot its installer disc for the bot.
            VmRecord picked = vms.getValue();
            if (!LINUX && inVm.isSelected() && picked != null && picked.stage() == VmRecord.Stage.READY) {
                VmChoice.set(services, picked.name());
            }
            // The VM's own settings, whichever bot uses it: kept in its folder.
            if (power != null) power.save();
            // Nothing changed, nothing written: a Save that rewrote an untouched value was one more entry in the
            // project's history and, for BotSettings.DEFAULTS, a chance to spell it differently.
            if (chosen.equals(current)) {
                services.status("Bot settings unchanged.");
            } else {
                ctx.ifPresent(c -> c.set(chosen));
                services.status("Bot settings saved in Sdk.settings().");
            }
            stage.close();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, spacer, cancel, save);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(14, heading, intro);
        if (ctx.isEmpty()) {
            save.setDisable(true);
            body.getChildren().add(missing());
        } else if (!SETTINGS.readable(ctx.get())) {
            save.setDisable(true);
            body.getChildren().add(note("Sdk.settings() is not a single `return BotSettings.of(…);`, so this "
                    + "window cannot rewrite it — it is your code, and stays as you wrote it. The defaults are "
                    + "shown."));
        }
        body.getChildren().addAll(new Separator(), runInPane(stage), new Separator(), visionPane());
        body.setPadding(new Insets(18));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        root.getChildren().addAll(scroll, bar);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        bar.setPadding(new Insets(10, 18, 14, 18));
        stage.show();
    }

    /** What to paste into an {@code Sdk.java} that predates the method, with a button that copies it. */
    private VBox missing() {
        String snippet = """
                @Managed("settings")
                public static BotSettings settings() {
                    return BotSettings.DEFAULTS;
                }""";
        Label says = note("This project's Sdk.java has no settings() method, so the bot runs on the defaults "
                + "below and there is nothing here to save into. Paste this into the class Sdk (importing "
                + "com.botmaker.sdk.api.bot.BotSettings and com.botmaker.plugin.api.managed.Managed), then open "
                + "this window again:");
        TextArea code = new TextArea(snippet);
        code.setEditable(false);
        code.setPrefRowCount(4);
        Styles.on(code, Styles.MONO_TEXT);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(snippet);
            Clipboard.getSystemClipboard().setContent(content);
        });
        return new VBox(6, says, code, copy);
    }

    /**
     * Where the game runs, asked once. The take-over tick shows only for the desktop, the one place it does
     * anything; the two backends sit under Advanced, for a machine that needs one pinned. Windows has no private
     * display, so it shows the tick alone.
     */
    private VBox runInPane(Stage stage) {
        inputBackend.getItems().setAll(BotSettings.InputBackend.values());
        inputBackend.setConverter(labels(BotSettings.InputBackend::displayName));
        inputBackend.setMaxWidth(Double.MAX_VALUE);
        displayBackend.getItems().setAll(BotSettings.DisplayBackend.values());
        displayBackend.setConverter(labels(BotSettings.DisplayBackend::displayName));
        displayBackend.setMaxWidth(Double.MAX_VALUE);
        Label takeOverHint = note("Off: events are sent to the game's window, and some games don't notice them. "
                + "Turn this on if clicks are ignored — the pointer then moves to each click and returns.");
        if (!LINUX) {
            // Windows watches a run's first background clicks and says so in the run's output when none of them
            // changed the game's window (shared's IgnoredClickWatch).
            takeOverHint.setText(takeOverHint.getText() + " A run says so in its output when its first clicks "
                    + "change nothing in the game.");
            return windowsRunInPane(stage, takeOverHint);
        }
        privateDisplay.setToggleGroup(where);
        myDesktop.setToggleGroup(where);
        Label privateHint = note("The bot brings up a display of its own and launches the game there. The game "
                + "never appears on your desktop, and the bot never takes your cursor or focus.");
        installBackend.setOnAction(e -> BackendInstallPrompt.offer(services, stage, neededBackend(),
                (installed, message) -> {
                    backendStatus.setText(message);
                    if (installed) refreshBackend();
                }));
        HBox missing = new HBox(8, backendStatus, installBackend);
        missing.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(backendStatus, Priority.ALWAYS);
        VBox onPrivate = new VBox(6, privateHint, missing);
        onPrivate.setPadding(new Insets(0, 0, 0, 24));
        VBox onDesktop = new VBox(6, takeOver, takeOverHint);
        onDesktop.setPadding(new Insets(0, 0, 0, 24));
        showWhen(onPrivate, privateDisplay);
        showWhen(onDesktop, myDesktop);
        displayBackend.disableProperty().bind(privateDisplay.selectedProperty().not());
        inputBackend.disableProperty().bind(myDesktop.selectedProperty().and(takeOver.selectedProperty()).not());
        displayBackend.valueProperty().addListener((o, was, now) -> refreshBackend());
        privateDisplay.selectedProperty().addListener((o, was, now) -> refreshBackend());

        GridPane grid = grid();
        grid.addRow(0, new Label("Private display"), displayBackend);
        grid.addRow(1, new Label("Take-over input"), inputBackend);
        GridPane.setHgrow(displayBackend, Priority.ALWAYS);
        GridPane.setHgrow(inputBackend, Priority.ALWAYS);
        TitledPane advanced = new TitledPane("Advanced", new VBox(8, grid,
                note("Automatic is right almost always. gamescope puts a real GPU in the private display; Xephyr "
                        + "renders in software, which crashes 3D games and store launchers. Pin an input backend "
                        + "only if this machine works with one in particular; -Dbotmaker.linux.input on the "
                        + "command line still wins.")));
        advanced.setExpanded(false);
        return new VBox(8, title("Run the game in"), privateDisplay, onPrivate, myDesktop, onDesktop, advanced);
    }

    /**
     * Windows: the desktop, or a game VM. The take-over tick is the desktop's alone: in a VM every click is the
     * hypervisor's own mouse, which the guest takes as hardware.
     */
    private VBox windowsRunInPane(Stage stage, Label takeOverHint) {
        myDesktop.setToggleGroup(where);
        inVm.setToggleGroup(where);
        Label vmHint = note("The game runs in a computer of its own, Windows or Linux, which Studio sets up. You "
                + "keep using yours; the bot's clicks reach the game as a real mouse's.");
        vms.setConverter(labels(vm -> vm.name() + " (" + vm.guestOs().displayName() + ")"
                + (vm.stage() == VmRecord.Stage.READY ? "" : " — " + vm.stage().displayName())));
        vms.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(vms, Priority.ALWAYS);
        Button setUpVm = new Button("Set up a game VM…");
        Button openScreen = new Button("Open VM screen");
        power = new VmPower(services);
        Runnable refresh = () -> {
            VmRecord picked = vms.getValue();
            boolean ready = picked != null && picked.stage() == VmRecord.Stage.READY;
            openScreen.setDisable(!ready);
            power.show(picked);
            setUpVm.setText(picked != null && !ready ? "Go on setting " + picked.name() + " up…" : "Set up a game VM…");
            vmStatus.setText(vms.getItems().isEmpty() ? "No game VM on this computer yet."
                    : picked != null && picked.guestOs() == GuestOs.LINUX
                    ? "Each bot gets a screen of its own in this VM, so several can share it." : "");
        };
        setUpVm.setOnAction(e -> {
            // Owned by the editor, not by this window: a setup takes 20–40 minutes and must outlive Save or
            // Cancel here. Its "Use this VM" writes the choice itself, so nothing here waits for it.
            VmRecord picked = vms.getValue();
            Consumer<VmRecord> use = ready -> useVm(services, ready);
            stage.close();
            if (picked != null && picked.stage() != VmRecord.Stage.READY) {
                VmSetupWindow.resume(services, owner, picked, use);
            } else {
                VmSetupWindow.open(services, owner, use);
            }
        });
        openScreen.setOnAction(e -> {
            if (vms.getValue() != null) VmScreen.open(services, stage, vms.getValue());
        });
        vms.valueProperty().addListener((o, was, now) -> refresh.run());
        loadVms(VmChoice.current(services));
        refresh.run();

        HBox vmRow = new HBox(8, vms, openScreen);
        vmRow.setAlignment(Pos.CENTER_LEFT);
        VBox onVm = new VBox(6, vmHint, vmRow, new HBox(8, setUpVm, vmStatus), power.node());
        onVm.setPadding(new Insets(0, 0, 0, 24));
        VBox onDesktop = new VBox(6, takeOver, takeOverHint);
        onDesktop.setPadding(new Insets(0, 0, 0, 24));
        showWhen(onDesktop, myDesktop);
        showWhen(onVm, inVm);
        return new VBox(8, title("Run the game in"), myDesktop, onDesktop, inVm, onVm);
    }

    /**
     * Runs the bot's game in {@code vm}: {@code Where.VM} in its Java, unless it says so already or isn't a
     * value this window can rewrite, and {@code vm} as this computer's {@link VmChoice}.
     */
    static void useVm(StudioServices services, VmRecord vm) {
        VmChoice.set(services, vm.name());
        Optional<ValueContext> ctx = SETTINGS.openOrCreate(services);
        BotSettings s = ctx.flatMap(SETTINGS::read).orElse(BotSettings.DEFAULTS);
        if (ctx.isPresent() && SETTINGS.readable(ctx.get()) && s.where() != BotSettings.Where.VM) {
            ctx.get().set(new BotSettings(s.clicks(), s.vision(), new BotSettings.RunIn(BotSettings.Where.VM,
                    s.takeOver(), s.runIn().displayBackend(), s.runIn().inputBackend()), s.maxRetryAttempts(),
                    s.debug()));
        }
        services.status("The bot's game runs in the game VM " + vm.name() + ".");
    }

    /** Lists this computer's game VMs, picking {@code preferred} when it is one, else the first. */
    private void loadVms(String preferred) {
        List<VmRecord> all = VmInventory.list();
        vms.getItems().setAll(all);
        vms.setValue(all.stream().filter(vm -> vm.name().equals(preferred)).findFirst()
                .orElse(all.isEmpty() ? null : all.getFirst()));
    }

    /** Shows {@code pane} only while {@code choice} is selected, taking no room otherwise. */
    private static void showWhen(Region pane, RadioButton choice) {
        pane.visibleProperty().bind(choice.selectedProperty());
        pane.managedProperty().bind(choice.selectedProperty());
    }

    /** The private display the game would get with the window's current choices. */
    private SessionBackend neededBackend() {
        BotSettings.DisplayBackend pinned = displayBackend.getValue();
        return SessionBootstrap.backendFor(LaunchTargetValue.spec(services), pinned);
    }

    /** Says whether the private display's backend is installed, with the offer to install it when it isn't. */
    private void refreshBackend() {
        SessionBackend needed = neededBackend();
        boolean missing = privateDisplay.isSelected() && !SessionBackends.isAvailable(needed);
        backendStatus.setText(missing ? "⚠ " + needed.binaryName() + " isn't installed — the game can't run in a "
                + "private display without it." : "");
        installBackend.setText("Install " + needed.binaryName() + "…");
        installBackend.setVisible(missing);
        installBackend.setManaged(missing);
    }

    /** Delays, confidence and retries — what the bot does around each match attempt. */
    private VBox visionPane() {
        for (Spinner<?> s : new Spinner<?>[] {foundDelay, notFoundDelay, confidence, compareMargin,
                maxRetryAttempts}) {
            s.setEditable(true);
            s.setPrefWidth(120);
        }
        GridPane grid = grid();
        grid.addRow(0, new Label("Pause after a match (ms)"), foundDelay,
                note("Let the game's animation finish before the next look."));
        grid.addRow(1, new Label("Pause after a miss (ms)"), notFoundDelay,
                note("How fast the bot retries when it doesn't see what it wants."));
        grid.addRow(2, new Label("Match confidence"), confidence,
                note("0–1. Lower finds more, and finds wrong things more."));
        grid.addRow(3, new Label("Compare margin"), compareMargin,
                note("How far the right template must beat a look-alike to win."));
        grid.addRow(4, new Label("Stuck after N no-progress checks"), maxRetryAttempts,
                note("When the watchdog decides the bot is stuck and restarts it."));
        return new VBox(8, title("Clicks & matching"), grid, randomizeClicks);
    }

    @SuppressWarnings("deprecation")
    private void seed(BotSettings s) {
        windowsWhere = s.where();
        if (LINUX) {
            (s.where() == BotSettings.Where.MY_DESKTOP ? myDesktop : privateDisplay).setSelected(true);
        } else {
            (s.where() == BotSettings.Where.VM ? inVm : myDesktop).setSelected(true);
        }
        takeOver.setSelected(s.takeOver());
        inputBackend.setValue(s.runIn().inputBackend());
        displayBackend.setValue(s.runIn().displayBackend());
        if (LINUX) refreshBackend();
        foundDelay.getValueFactory().setValue(s.foundDelay());
        notFoundDelay.getValueFactory().setValue(s.notFoundDelay());
        confidence.getValueFactory().setValue(s.confidence());
        compareMargin.getValueFactory().setValue(s.compareMargin());
        maxRetryAttempts.getValueFactory().setValue(s.maxRetryAttempts());
        randomizeClicks.setSelected(s.randomizeClicks());
        debug = s.debug();
    }

    /**
     * What the controls say, as the value {@code Sdk.settings()} will return.
     *
     * <p>A number typed into a spinner reaches its value only on Enter or on leaving the field, so one typed
     * and followed straight by Save would be dropped. Each field's text is committed first; text
     * that is not a number leaves the last good value.
     */
    BotSettings collect() {
        for (Spinner<?> s : new Spinner<?>[] {foundDelay, notFoundDelay, confidence, compareMargin,
                maxRetryAttempts}) {
            commitTyped(s);
        }
        return BotSettings.of(
                BotSettings.clicks(foundDelay.getValue(), notFoundDelay.getValue(), randomizeClicks.isSelected()),
                BotSettings.vision(confidence.getValue(), compareMargin.getValue()),
                BotSettings.runIn(chosenWhere(), takeOver.isSelected(), displayBackend.getValue(),
                        inputBackend.getValue()),
                maxRetryAttempts.getValue(), debug);
    }

    private BotSettings.Where chosenWhere() {
        if (!LINUX) {
            if (inVm.isSelected()) return BotSettings.Where.VM;
            // The desktop, which a private display also is on Windows: that one is kept for the bot on Linux.
            return windowsWhere == BotSettings.Where.VM ? BotSettings.Where.MY_DESKTOP : windowsWhere;
        }
        return myDesktop.isSelected() ? BotSettings.Where.MY_DESKTOP : BotSettings.Where.PRIVATE_DISPLAY;
    }

    private static <T> void commitTyped(Spinner<T> spinner) {
        String text = spinner.getEditor().getText();
        if (text == null || text.isBlank()) return;
        try {
            T typed = spinner.getValueFactory().getConverter().fromString(text.trim());
            if (typed != null) spinner.getValueFactory().setValue(typed);
        } catch (RuntimeException notANumber) {
            // the last value the spinner held stands
        }
    }

    private static <T> StringConverter<T> labels(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override public String toString(T v) { return v == null ? "" : label.apply(v); }
            @Override public T fromString(String s) { return null; }
        };
    }

    private static Label title(String text) {
        return Styles.on(new Label(text), Styles.STRONG_TEXT);
    }

    private static Label note(String text) {
        Label l = Styles.on(new Label(text), Styles.DIALOG_HINT);
        l.setWrapText(true);
        return l;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        return grid;
    }
}
