package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.config.Applies;
import com.botmaker.shared.emulator.config.Apply;
import com.botmaker.shared.emulator.config.ApplyHost;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.EmulatorOperations;
import com.botmaker.shared.emulator.config.InstanceFacts;
import com.botmaker.shared.emulator.config.Operation;
import com.botmaker.shared.emulator.google.Passwords;
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

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private static EmulatorsWindow current;

    private final StudioServices services;
    private final Stage stage;
    private final Label readAt = Styles.on(new Label("Reading…"), StyleClasses.MUTED_TEXT);
    private final Label status = Styles.on(new Label(), StyleClasses.WARNING_TEXT);
    private final Button refresh = new Button("Refresh");
    private final Tab emulatorsTab = new Tab("Emulators");
    private final Tab instancesTab = new Tab("Instances");
    private final VBox strip = new VBox(4);
    private final EmulatorsTab emulators;
    private final InstancesTab instances;
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

    private EmulatorsWindow(StudioServices services) {
        this.services = services;
        this.emulators = new EmulatorsTab(this);
        this.instances = new InstancesTab(this);

        emulatorsTab.setContent(emulators.node());
        instancesTab.setContent(instances.node());
        TabPane tabs = new TabPane(emulatorsTab, instancesTab);
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
        stage.setOnShown(e -> {
            OPERATIONS.addListener(listener);
            drawStrip();
            refresh();
        });
        stage.setOnHidden(e -> {
            OPERATIONS.removeListener(listener);
            if (current == this) current = null;
        });
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
        Async.load("emulators-read", () -> EmulatorsModel.read(new Live(services), last, Instant.now()),
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
        emulators.show(read);
        instances.show(read);
        readEnded();
    }

    private void readEnded() {
        reading = false;
        refresh.setDisable(false);
        if (readAgain) {
            readAgain = false;
            refresh();
        }
    }

    /** This PC, read for real. */
    private record Live(StudioServices services) implements EmulatorsModel.Source {
        @Override
        public Platforms.DiscoveryReport discover() {
            return Platforms.discoverDetailed();
        }

        @Override
        public HostConfig.State configuration() {
            return HostConfig.load(services);
        }

        @Override
        public boolean running(EmulatorInstance instance) {
            return ApplyHost.live().running(instance);
        }

        @Override
        public InstanceFacts facts(EmulatorInstance instance, InstanceFacts last) {
            return ApplyHost.live().read(instance, last);
        }
    }

    // ---- the configuration ----

    /** Changes the configuration off the UI thread, then reads again; a refusal is said in the header. */
    void change(UnaryOperator<EmulatorConfig> change) {
        Async.load("emulators-write", () -> HostConfig.update(services, change), refused -> {
            say(refused.orElse(null));
            refresh();
        }, failed -> say("The configuration was not written: " + failed));
    }

    // ---- operations ----

    void start(List<EmulatorsModel.InstanceRow> rows) {
        for (EmulatorsModel.InstanceRow row : rows) {
            if (row.running()) continue;
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
        List<ApplyPlan.Item> items = ApplyPlan.of(rows);
        if (items.isEmpty()) {
            say("Nothing to apply: the instances picked are as configured, or were not read.");
            return;
        }
        Optional<Boolean> removals = confirm(items);
        if (removals.isEmpty()) return;
        List<ApplyPlan.Item> agreed = ApplyPlan.agreed(items, removals.get());
        if (agreed.isEmpty()) return;

        Passwords passwords = new Passwords(this::password);
        Set<String> batch = ConcurrentHashMap.newKeySet();
        Consumer<Operation.Snapshot> ended = new Consumer<>() {
            @Override
            public void accept(Operation.Snapshot heard) {
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
                            return ApplyPlan.body(item, log, passwords).run(context);
                        });
                batch.add(operation.id());
                queued.add(operation);
            }
        }
        queued.forEach(operation -> RUNNING.put(operation.id(), operation));
        drawStrip();
    }

    private void submit(String title, EmulatorInstance instance, Operation.Body body) {
        Operation operation = OPERATIONS.submit(title, instance, body);
        RUNNING.put(operation.id(), operation);
        drawStrip();
    }

    private static Applies applies() {
        return new Applies(HostConfig.stateDir().resolve("applies.jsonl"));
    }

    /**
     * The confirmation: every change, per instance. Empty when the user cancelled; else whether the accounts listed
     * for removal are removed too, which is asked on its own and off until ticked.
     */
    private Optional<Boolean> confirm(List<ApplyPlan.Item> items) {
        VBox body = new VBox(6);
        for (ApplyPlan.Item item : items) {
            body.getChildren().add(Styles.on(new Label(item.row().instance().name() + " ("
                    + item.row().key().platform().displayName() + ")"), StyleClasses.STRONG_TEXT));
            for (String line : ApplyPlan.lines(item)) {
                Label label = new Label("  " + line);
                label.setWrapText(true);
                body.getChildren().add(label);
            }
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
    private char[] password(String email) throws InterruptedException {
        CompletableFuture<char[]> answer = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                answer.complete(askPassword(email));
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

    private char[] askPassword(String email) {
        PasswordField field = new PasswordField();
        Label why = new Label("BotMaker types it once on the instance, in Android's own sign-in, and keeps it only"
                + " until this Apply ends. Skip it to leave this account out.");
        why.setWrapText(true);
        why.setMaxWidth(420);
        VBox body = new VBox(8, Styles.on(why, StyleClasses.DIALOG_HINT), field);

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
        return password;
    }

    // ---- the operations strip ----

    private void heard(Operation.Snapshot heard) {
        drawStrip();
        if (heard.state().ended()) {
            RUNNING.remove(heard.id());
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
