package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.checks.WindowsEmulatorChecks;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.Applies;
import com.botmaker.shared.emulator.config.Apply;
import com.botmaker.shared.emulator.config.ApplyHost;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.EmulatorOperations;
import com.botmaker.shared.emulator.config.InstanceFacts;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Operation;
import com.botmaker.shared.emulator.google.KeyringStore;
import com.botmaker.shared.emulator.google.PasswordStore;
import com.botmaker.shared.emulator.google.Passwords;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The Emulators window: this PC's emulators and their instances, what each should be and what it is, and what
 * BotMaker is doing to them (spec {@code 2026-11-12-emulators-window-design.md}). One window per process; its tabs
 * draw one {@link EmulatorsModel.Snapshot}, read again by Refresh and after every operation.
 *
 * <p>The operations queue outlives the window: closing it leaves a sign-in or an install running, and opening it
 * again shows them.
 */
public final class EmulatorsWindow {

    /** What BotMaker is doing to the instances; the process's, so an operation survives the window. */
    private static final EmulatorOperations OPERATIONS = new EmulatorOperations();

    /** The operations by id, for Cancel: a snapshot is all the queue's listeners hear. */
    private static final Map<String, Operation> RUNNING = new ConcurrentHashMap<>();

    /**
     * This PC's credential store, for the Google passwords the user asks to remember; one per process, opened on
     * first use, and {@code available()} false where the PC has none.
     */
    private static final PasswordStore STORE = new KeyringStore();
    /** The Applies signing accounts in, by operation id. */
    private static final Map<String, Signing> SIGNING = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private static EmulatorsWindow current;

    private final StudioServices services;
    private final Stage stage;
    private final Label readAt = Styles.on(new Label("Reading…"), StyleClasses.MUTED_TEXT);
    private final Label status = Styles.on(new Label(), StyleClasses.WARNING_TEXT);
    private final Button refresh = new Button("Refresh");
    private final Tab emulatorsTab = new Tab("Emulators");
    private final Tab instancesTab = new Tab("Instances");
    private final Tab accountsTab = new Tab("Accounts");
    private final Tab configurationsTab = new Tab("Configurations");
    private final Tab runningTab = new Tab("Running");
    private final TabPane tabs;
    private final VBox strip = new VBox(4);
    private final EmulatorsTab emulators;
    private final InstancesTab instances;
    private final AccountsTab accounts;
    private final ConfigurationsTab configurations;
    private final RunningTab running;
    /** The operations whose live screen was opened for them once already. */
    private final Set<String> shownNeedsYou = ConcurrentHashMap.newKeySet();
    /** The operations waiting on the user now. */
    private final Set<String> waiting = ConcurrentHashMap.newKeySet();
    private AutoCloseable botListener;
    private String botInstance;
    /** The configuration view open over the Emulators or the Instances tab, or null. */
    private ConfigView open;
    private final Consumer<Operation.Snapshot> listener = snapshot -> Platform.runLater(() -> heard(snapshot));

    private EmulatorsModel.Snapshot snapshot = EmulatorsModel.Snapshot.EMPTY;
    private boolean reading;
    private boolean readAgain;

    /** The toolbar's press: shows the window, the one already open when there is one. */
    public static void open(ActionContext context) {
        open(context.services());
    }

    public static void open(StudioServices services) {
        if (current != null && current.services != services) {
            current.stage.close();
        }
        if (current == null) current = new EmulatorsWindow(services);
        current.stage.show();
        current.stage.toFront();
    }

    /** Whether this window clones, renames and deletes {@code product}'s instances: the picker's <i>Manage…</i>. */
    public static boolean manages(PlatformId product) {
        return ManageModel.manageable(product);
    }

    /** The emulator picker's <i>Manage…</i>: the window on its Instances tab, filtered to {@code instance}'s name. */
    public static void open(StudioServices services, EmulatorInstance instance) {
        open(services);
        current.tabs.getSelectionModel().select(current.instancesTab);
        current.instances.focus(instance);
    }

    /**
     * The Accounts window's <i>N instances</i>: a Google account's row of the Accounts tab; a Facebook or mail record,
     * which the grid does not list, the Instances tab filtered by its name.
     */
    public static void openForAccount(StudioServices services, AccountRecord account) {
        open(services);
        if (account.kind() == AccountRecord.Kind.GOOGLE) {
            current.tabs.getSelectionModel().select(current.accountsTab);
            current.accounts.showAccount(account.key());
        } else {
            current.tabs.getSelectionModel().select(current.instancesTab);
            current.instances.showAccount(account.name());
        }
    }

    /** This PC's credential store, the one the Applies remember passwords in: it can block, so never asked on FX. */
    public static PasswordStore passwords() {
        return STORE;
    }

    private EmulatorsWindow(StudioServices services) {
        this.services = services;
        this.emulators = new EmulatorsTab(this);
        this.instances = new InstancesTab(this);
        this.accounts = new AccountsTab(this);
        this.configurations = new ConfigurationsTab(this);
        this.running = new RunningTab(this);

        emulatorsTab.setContent(emulators.node());
        instancesTab.setContent(instances.node());
        accountsTab.setContent(accounts.node());
        configurationsTab.setContent(configurations.node());
        runningTab.setContent(running.node());
        tabs = new TabPane(emulatorsTab, instancesTab, accountsTab, configurationsTab, runningTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        refresh.setOnAction(e -> refresh());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        status.setWrapText(true);
        HBox header = new HBox(10, status, gap, readAt, refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 10, 4, 10));

        ScrollPane operations = new ScrollPane(strip);
        operations.setFitToWidth(true);
        operations.setMaxHeight(150);
        strip.setPadding(new Insets(6, 10, 8, 10));

        BorderPane root = new BorderPane(tabs);
        root.setTop(header);
        root.setBottom(operations);

        stage = Modals.window(services, Modals.Frame.modeless("Emulators", 1080, 680, 780, 420), root);
        tabs.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            running.visible(now == runningTab && stage.isShowing());
            accounts.visible(now == accountsTab);
        });
        stage.setOnShown(e -> {
            OPERATIONS.addListener(listener);
            readBotInstance();
            botListener = services.runs().onStateChanged(runs -> Platform.runLater(() -> {
                readBotInstance();
                running.draw();
            }));
            drawStrip();
            refresh();
            running.visible(tabs.getSelectionModel().getSelectedItem() == runningTab);
        });
        stage.setOnHidden(e -> {
            OPERATIONS.removeListener(listener);
            running.visible(false);
            try {
                if (botListener != null) botListener.close();
            } catch (Exception ignored) {
                // the host's listener is gone already
            }
            botListener = null;
            // what was heard stops here: an operation that ends while hidden is not heard ending
            shownNeedsYou.clear();
            waiting.clear();
            if (current == this) current = null;
        });
    }

    // ---- the open bot's run ----

    /** The open bot's name while it runs, else null. */
    String botRunning() {
        if (!services.runs().isRunning() || services.projectDir() == null
                || services.projectDir().getFileName() == null) return null;
        return services.projectDir().getFileName().toString();
    }

    /** The instance reference the open bot launches on this PC, or null; read as the window shows and as a run starts. */
    String botInstance() {
        return botInstance;
    }

    /** Reads the open bot's launch target again (its values: on the UI thread, once per run-state change). */
    private void readBotInstance() {
        try {
            LaunchSpec spec = LaunchTargetValue.spec(services);
            botInstance = spec == null ? null : spec.emulatorInstance();
        } catch (RuntimeException unread) {
            botInstance = null;
        }
    }

    void stopBot() {
        services.runs().stop();
    }

    /** The operation waiting on the user at {@code key}, if one is. */
    Optional<Operation.Snapshot> needsYou(InstanceKey key) {
        return OPERATIONS.all().stream()
                .filter(one -> one.state() == Operation.State.NEEDS_YOU && key.equals(one.instance())).findFirst();
    }

    /** Opens the live screen of the instance {@code one} waits on, when it was read. */
    private void openScreen(Operation.Snapshot one) {
        snapshot.instance(one.instance()).ifPresentOrElse(row -> LiveScreenDialog.open(this, row, one.needsYou()),
                () -> say("The instance " + one.title() + " needs was not read yet: Refresh, then open it from"
                        + " Running."));
    }

    StudioServices services() {
        return services;
    }

    Stage stage() {
        return stage;
    }

    EmulatorsModel.Snapshot snapshot() {
        return snapshot;
    }

    void say(String words) {
        status.setText(words == null ? "" : words);
    }

    // ---- reading ----

    /** Reads this PC again, off the UI thread; a refresh asked for meanwhile runs once this one ends. */
    void refresh() {
        if (reading) {
            readAgain = true;
            return;
        }
        reading = true;
        refresh.setDisable(true);
        EmulatorsModel.Snapshot last = snapshot;
        Async.load("emulators-read", () -> EmulatorsModel.read(EmulatorsModel.Source.live(services), last, Instant.now()),
                this::show, failed -> {
                    readEnded();
                    say("This PC's emulators could not be read: " + failed);
                });
    }

    private void show(EmulatorsModel.Snapshot read) {
        snapshot = read;
        readAt.setText("Read at " + TIME.format(read.readAt()));
        emulatorsTab.setText("Emulators (" + read.emulators().stream()
                .filter(EmulatorsModel.EmulatorRow::installed).count() + ")");
        instancesTab.setText("Instances (" + read.instances().size() + ", " + read.running() + " running)");
        runningTab.setText("Running (" + read.running() + ")");
        emulators.show(read);
        instances.show(read);
        accounts.show(read);
        configurations.show(read);
        running.show(read);
        if (open != null) open.show(read);
        LiveScreenDialog.redrawAll();
        readEnded();
    }

    // ---- a configuration's view ----

    /** Opens a layer's view over its tab: an instance's over Instances, an emulator's over Emulators. */
    void configure(ConfigViews.Target target) {
        back();
        Tab tab = target instanceof ConfigViews.Target.Instance ? instancesTab : emulatorsTab;
        open = new ConfigView(this, target, this::back);
        tab.setContent(open.node());
        tabs.getSelectionModel().select(tab);
        open.show(snapshot);
    }

    /** Closes the view open, its tab showing its table again. */
    private void back() {
        if (open == null) return;
        if (open.target() instanceof ConfigViews.Target.Instance) instancesTab.setContent(instances.node());
        else emulatorsTab.setContent(emulators.node());
        open = null;
    }

    private void readEnded() {
        reading = false;
        refresh.setDisable(false);
        if (readAgain) {
            readAgain = false;
            refresh();
        }
    }

    // ---- the configuration ----

    /** Changes the configuration off the UI thread, then reads again; a refusal is said in the header. */
    void change(UnaryOperator<EmulatorConfig> change) {
        change(change, () -> {});
    }

    /** {@link #change(UnaryOperator)}, then {@code written} on the UI thread once the host wrote it. */
    void change(UnaryOperator<EmulatorConfig> change, Runnable written) {
        Async.load("emulators-write", () -> HostConfig.update(services, change), refused -> {
            say(refused.orElse(null));
            if (refused.isEmpty()) written.run();
            refresh();
        }, failed -> say("The configuration was not written: " + failed));
    }

    // ---- operations ----

    /** Starts the stopped rows, asking first when Windows would not have the memory for them all. */
    void start(List<EmulatorsModel.InstanceRow> rows) {
        List<EmulatorsModel.InstanceRow> stopped = rows.stream().filter(row -> !row.running()).toList();
        Optional<String> tight = RunningModel.Headroom.of(WindowsEmulatorChecks.availableMb(), snapshot)
                .warning(stopped);
        if (tight.isPresent()) {
            Alert ask = alert(Alert.AlertType.CONFIRMATION, tight.get() + "\n\nStart anyway?");
            ask.setHeaderText("Not enough memory");
            if (ask.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
        }
        for (EmulatorsModel.InstanceRow row : stopped) {
            submit("Start " + row.instance().name(), row.instance(),
                    context -> ApplyHost.live().start(row.instance(), context::step));
        }
    }

    void stop(List<EmulatorsModel.InstanceRow> rows) {
        for (EmulatorsModel.InstanceRow row : rows) {
            if (!row.running()) continue;
            submit("Stop " + row.instance().name(), row.instance(), context -> ApplyHost.live().stop(row.instance()));
        }
    }

    /** Takes back the last Apply's settings on the instance. */
    void undo(EmulatorsModel.InstanceRow row) {
        EmulatorInstance instance = row.instance();
        submit("Undo the last apply on " + instance.name(), instance, context -> {
            Applies log = applies();
            Optional<Applies.Entry> last = log.last(row.key());
            if (last.isEmpty()) return "Nothing was applied on " + instance.name() + " to undo.";
            InstanceFacts facts = row.facts() != null ? row.facts() : ApplyHost.live().read(instance, null);
            return Apply.undo(ApplyHost.live(), instance, facts, log, last.get(), context).failed();
        });
    }

    /**
     * Apply, for the rows that differ: shows every change, asks before any account is removed, then queues one
     * operation per instance. The batch's passwords are asked once each, in {@link #password}, and cleared when its
     * last operation ends.
     */
    void apply(List<EmulatorsModel.InstanceRow> rows) {
        applyItems(ApplyPlan.of(rows));
    }

    /** {@link #apply(List)} for items already chosen: the Accounts tab's, its sign-ins and sign-outs only. */
    void applyItems(List<ApplyPlan.Item> items) {
        if (items.isEmpty()) {
            say("Nothing to apply: the instances picked are as configured, or were not read.");
            return;
        }
        // the store can block (opening, or asking to be unlocked): asked off the FX thread, once per account
        Thread.ofVirtual().name("apply-passwords").start(() -> {
            Set<String> remembered = ApplyPlan.remembered(items, STORE);
            Platform.runLater(() -> apply(items, remembered));
        });
    }

    private void apply(List<ApplyPlan.Item> items, Set<String> remembered) {
        Optional<Boolean> removals = confirm(items, remembered);
        if (removals.isEmpty()) return;
        List<ApplyPlan.Item> agreed = ApplyPlan.agreed(items, removals.get());
        if (agreed.isEmpty()) return;

        Passwords passwords = new Passwords(STORE, this::password);
        Set<String> batch = ConcurrentHashMap.newKeySet();
        Consumer<Operation.Snapshot> ended = new Consumer<>() {
            @Override
            public void accept(Operation.Snapshot heard) {
                if (heard.state().ended() && SIGNING.remove(heard.id()) != null) {
                    Platform.runLater(LiveScreenDialog::redrawAll);
                }
                if (!heard.state().ended() || !batch.remove(heard.id()) || !batch.isEmpty()) return;
                passwords.close();
                OPERATIONS.removeListener(this);
            }
        };
        Applies log = applies();
        List<Operation> queued = new ArrayList<>();
        // every id is in the batch before any operation can end, so the passwords are cleared exactly once
        synchronized (batch) {
            OPERATIONS.addListener(ended);
            for (ApplyPlan.Item item : agreed) {
                Operation operation = OPERATIONS.submit("Apply to " + item.row().instance().name(),
                        item.row().instance(), context -> {
                            synchronized (batch) {
                                // waits until the whole batch is queued
                            }
                            return ApplyPlan.body(item, log, passwords, (typed, listed) -> change(
                                    c -> ConfigViews.renameAccount(c, item.row().key(), typed, listed))).run(context);
                        });
                batch.add(operation.id());
                queued.add(operation);
                List<String> signsIn = ApplyPlan.signInNames(item);
                if (!signsIn.isEmpty()) {
                    SIGNING.put(operation.id(), new Signing(operation.id(), item.row().key(), passwords, signsIn));
                }
            }
        }
        queued.forEach(operation -> RUNNING.put(operation.id(), operation));
        LiveScreenDialog.redrawAll();
        drawStrip();
    }

    void submit(String title, EmulatorInstance instance, Operation.Body body) {
        Operation operation = OPERATIONS.submit(title, instance, body);
        RUNNING.put(operation.id(), operation);
        drawStrip();
    }

    static Applies applies() {
        return new Applies(HostConfig.stateDir().resolve("applies.jsonl"));
    }

    /**
     * The confirmation: every change, per instance. Empty when the user cancelled; else whether the accounts listed
     * for removal are removed too, which is asked on its own and off until ticked.
     */
    private Optional<Boolean> confirm(List<ApplyPlan.Item> items, Set<String> remembered) {
        VBox body = new VBox(6);
        for (ApplyPlan.Item item : items) {
            body.getChildren().add(Styles.on(new Label(item.row().instance().name() + " ("
                    + item.row().key().platform().displayName() + ")"), StyleClasses.STRONG_TEXT));
            for (String line : ApplyPlan.lines(item, remembered)) {
                Label label = new Label("  " + line);
                label.setWrapText(true);
                body.getChildren().add(label);
            }
        }
        int asked = ApplyPlan.asked(items, remembered);
        if (asked > 0) {
            Label note = new Label(EmulatorsModel.count(asked, "password") + " will be asked, each once, when the"
                    + " first instance that signs its account in comes up.");
            note.setWrapText(true);
            body.getChildren().add(Styles.on(note, StyleClasses.DIALOG_HINT));
        }
        CheckBox removals = new CheckBox("Also remove the accounts listed above from their instances");
        boolean removes = items.stream().anyMatch(ApplyPlan.Item::removes);
        if (removes) body.getChildren().add(removals);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(520, 320);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Apply");
        dialog.setHeaderText("Apply these changes to " + EmulatorsModel.count(items.size(), "instance") + "?");
        dialog.getDialogPane().setContent(scroll);
        ButtonType apply = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(apply, ButtonType.CANCEL);
        services.theme().apply(dialog);
        Optional<ButtonType> pressed = dialog.showAndWait();
        return pressed.isPresent() && pressed.get() == apply
                ? Optional.of(removes && removals.isSelected()) : Optional.empty();
    }

    /**
     * Asks one account's password, from the operation's thread: typed here and nowhere else, handed to the batch as
     * characters, never kept as text, never logged. Null when the user skips the account.
     */
    private Passwords.Answer password(String email) throws InterruptedException {
        CompletableFuture<Passwords.Answer> answer = new CompletableFuture<>();
        boolean canRemember = STORE.available(); // here, on the operation's thread: the store can block
        Platform.runLater(() -> {
            try {
                answer.complete(askPassword(email, canRemember));
            } catch (RuntimeException failed) {
                answer.complete(null);
            }
        });
        try {
            return answer.get();
        } catch (ExecutionException failed) {
            return null;
        }
    }

    /**
     * What an instance's queued or running Apply signs in, for its live screen's Fill panel: the emails, and the batch
     * a password typed there is handed to. Held in memory while the operation lasts; never a password.
     */
    record Signing(String operation, InstanceKey instance, Passwords passwords, List<String> emails) {
        Signing {
            emails = List.copyOf(emails);
        }
    }

    /**
     * What the Applies on {@code key} sign in while they run, the earliest queued first: the one running is the first
     * not ended, as one instance runs one operation at a time.
     */
    List<Signing> signing(InstanceKey key) {
        List<Signing> signing = new ArrayList<>();
        for (Operation.Snapshot op : OPERATIONS.all()) {
            Signing one = SIGNING.get(op.id());
            if (one == null || !key.equals(one.instance())) continue;
            // an operation cancelled before its batch was queued ended before its entry was put: dropped here
            if (op.state().ended()) SIGNING.remove(op.id());
            else signing.add(one);
        }
        return signing;
    }

    /** This PC's credential store: it can block, so never asked on the FX thread. */
    PasswordStore store() {
        return STORE;
    }

    /** The password prompt, on the FX thread: what the user typed, or null when they skipped it. */
    Passwords.Answer askPassword(String email, boolean canRemember) {
        return askPassword(email, canRemember, canRemember
                ? "BotMaker types it on the instance, in Android's own sign-in. Remembered, it is kept in this PC's"
                + " credential store once the sign-in is done, and typed again without asking; no assistant ever sees"
                + " it. Skip it to leave this account out."
                : "BotMaker types it once on the instance, in Android's own sign-in, and keeps it only until this"
                + " Apply ends: this PC has no credential store to remember it in. Skip it to leave this account out.");
    }

    /** The password prompt with {@code words} under its header; *Remember on this PC* only when {@code canRemember}. */
    Passwords.Answer askPassword(String email, boolean canRemember, String words) {
        PasswordField field = new PasswordField();
        CheckBox remember = new CheckBox("Remember on this PC");
        remember.setSelected(true);
        Label why = new Label(words);
        why.setWrapText(true);
        why.setMaxWidth(420);
        VBox body = new VBox(8, Styles.on(why, StyleClasses.DIALOG_HINT), field);
        if (canRemember) body.getChildren().add(remember);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage.isShowing() ? stage : Modals.owner(services));
        dialog.setTitle("Google password");
        dialog.setHeaderText("Password for " + email);
        dialog.getDialogPane().setContent(body);
        ButtonType signIn = new ButtonType("Sign in", ButtonBar.ButtonData.OK_DONE);
        ButtonType skip = new ButtonType("Skip this account", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(signIn, skip);
        services.theme().apply(dialog);
        Platform.runLater(field::requestFocus);
        Optional<ButtonType> pressed = dialog.showAndWait();
        CharSequence typed = field.getCharacters();
        char[] password = null;
        if (pressed.isPresent() && pressed.get() == signIn && typed.length() > 0) {
            password = new char[typed.length()];
            for (int i = 0; i < password.length; i++) password[i] = typed.charAt(i);
        }
        field.clear();
        return password == null ? null : new Passwords.Answer(password, canRemember && remember.isSelected());
    }

    // ---- the operations strip ----

    private void heard(Operation.Snapshot heard) {
        drawStrip();
        // the cards show "Needs you" while an operation waits: drawn again as it starts and stops waiting
        boolean waits = heard.state() == Operation.State.NEEDS_YOU;
        if (waits ? waiting.add(heard.id()) : waiting.remove(heard.id())) running.draw();
        // the live screen opens by itself the first time an operation waits on the user
        if (waits && heard.instance() != null && shownNeedsYou.add(heard.id())) openScreen(heard);
        if (heard.state().ended()) {
            RUNNING.remove(heard.id());
            shownNeedsYou.remove(heard.id());
            refresh();
        }
    }

    private void drawStrip() {
        List<Operation.Snapshot> all = OPERATIONS.all();
        strip.getChildren().clear();
        if (all.isEmpty()) {
            strip.getChildren().add(Styles.on(new Label("Nothing is running. Starts, stops, applies and sign-ins show"
                    + " here."), StyleClasses.MUTED_TEXT));
            return;
        }
        for (Operation.Snapshot one : all) strip.getChildren().add(row(one));
        if (all.stream().anyMatch(one -> one.state().ended())) {
            Button clear = new Button("Clear finished");
            clear.setOnAction(e -> {
                OPERATIONS.clearEnded();
                drawStrip();
            });
            strip.getChildren().add(clear);
        }
    }

    private HBox row(Operation.Snapshot one) {
        Label title = Styles.on(new Label(one.title()), StyleClasses.STRONG_TEXT);
        title.setMinWidth(Region.USE_PREF_SIZE);
        Label state = new Label(one.state().displayName());
        state.setMinWidth(Region.USE_PREF_SIZE);
        Styles.on(state, switch (one.state()) {
            case DONE -> StyleClasses.OK_TEXT;
            case FAILED -> StyleClasses.ERROR_TEXT;
            case NEEDS_YOU -> StyleClasses.WARNING_TEXT;
            default -> StyleClasses.MUTED_TEXT;
        });
        String words = one.state() == Operation.State.NEEDS_YOU && one.needsYou() != null ? one.needsYou()
                : one.state().ended() && one.outcome() != null ? one.outcome() : one.step();
        Label step = new Label(words == null ? "" : words);
        step.setWrapText(true);
        HBox.setHgrow(step, Priority.ALWAYS);
        step.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(10, title, state, step);
        row.setAlignment(Pos.CENTER_LEFT);
        if (one.state() == Operation.State.NEEDS_YOU && one.instance() != null) {
            Button look = new Button("Open its screen");
            look.setMinWidth(Region.USE_PREF_SIZE);
            look.setOnAction(e -> openScreen(one));
            row.getChildren().add(look);
        }
        if (one.state() == Operation.State.RUNNING) {
            ProgressBar bar = new ProgressBar(one.progress() > 0 ? one.progress() : ProgressBar.INDETERMINATE_PROGRESS);
            bar.setPrefWidth(90);
            bar.setMinWidth(90);
            row.getChildren().add(bar);
        }
        if (!one.state().ended()) {
            Button cancel = new Button("Cancel");
            cancel.setMinWidth(Region.USE_PREF_SIZE);
            Operation operation = RUNNING.get(one.id());
            cancel.setDisable(operation == null);
            cancel.setOnAction(e -> {
                if (operation != null) operation.cancel();
            });
            row.getChildren().add(cancel);
        }
        return row;
    }

    /** A plain question owned by the window, themed as it is. */
    Alert alert(Alert.AlertType type, String words) {
        Alert alert = services.theme().alert(type, words);
        alert.initOwner(stage);
        return alert;
    }
}
