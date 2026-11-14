package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.host.HostBook;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.sdk.plugin.emulator.window.HostUndo;
import com.botmaker.sdk.plugin.emulator.window.Look;
import com.botmaker.sdk.plugin.emulator.window.WindowKeys;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.google.PasswordStore;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Accounts window: every account this PC knows, for every emulator and every bot, with no bot open (board
 * Accounts, spec {@code 2026-11-13-google-accounts-design.md}; its look, spec {@code 2026-11-14-emulators-look-design.md}).
 * It lists {@link HostBook} beside a detail panel for the account picked: its note, edited in place; its password,
 * forgotten or remembered in this PC's credential store; the instances that want it, each with its state; and Remove
 * for an account no configuration lists. It imports and exports the book (kind, name and note: never a password).
 * Which instance holds which account is the Emulators window's.
 *
 * <p>A password typed into <i>Remember…</i> goes from the field to the store as characters and is cleared; it is
 * never text, never logged. Emails are shown to the user only.
 */
public final class AccountsWindow {

    private static final String EXTENSION = ".json";

    private static AccountsWindow current;

    private final StudioServices services;
    private final Stage stage;
    private final TextField text = new TextField();
    /** The kind filter's segments, in {@link AccountsListModel#counts} order: All, then each kind. */
    private final List<ToggleButton> segments = new ArrayList<>();
    private final Label status = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
    private final TableView<AccountsListModel.Row> table = new TableView<>();
    private final VBox side = new VBox(6);
    private AccountRecord.Kind kind;
    private List<AccountsListModel.Row> all = List.of();
    private List<AccountRecord> book = List.of();
    private boolean storeAvailable;
    /** The instances discovered by the first read, kept for the reads after an action; null until then. */
    private Found found;
    /** Counts the reads started: a read that ends after a newer one started is dropped. */
    private int reads;
    /** The account picked, kept across a read that rebuilds the list. */
    private AccountRecord.Key picked;
    private boolean refilling;
    /** The row the panel shows, so a read that changed nothing of it leaves the panel, and a note being typed, alone. */
    private AccountsListModel.Row shown;
    private boolean shownStore;
    private Found shownFound;
    /** The panel's parts: the heading, the note (kept while the same account is shown), the rest. */
    private final VBox top = new VBox(6);
    private final VBox notes = new VBox(6);
    private final VBox rest = new VBox(6);
    /** The note's field and its account, kept across reads so one never undoes what is being typed. */
    private TextField noteField;
    private AccountRecord.Key noteKey;
    /** The note as last written or read for {@link #noteKey}: a commit writes only what differs from it. */
    private String[] noteWritten;
    /** The notes waiting to be written, one per account, written one at a time so the last typed is the one kept. */
    private final Map<AccountRecord.Key, AccountRecord> notesPending = new LinkedHashMap<>();
    private boolean noteWriting;

    /** The toolbar's press: shows the window, the one already open when there is one. */
    public static void open(ActionContext context) {
        open(context.services());
    }

    public static void open(StudioServices services) {
        if (current != null && current.services != services) current.stage.close();
        if (current == null) current = new AccountsWindow(services);
        current.stage.show();
        current.stage.toFront();
    }

    private AccountsWindow(StudioServices services) {
        this.services = services;

        text.setPromptText("Find");
        text.getStyleClass().add("em-find");
        text.textProperty().addListener((o, was, now) -> filter());

        Button add = new Button("+ Account");
        add.setOnAction(e -> addAccount());
        Button paste = new Button("Paste list…");
        paste.setTooltip(new Tooltip("Add many accounts at once from a list of emails, one per line or separated by"
                + " commas"));
        paste.setOnAction(e -> pasteList());
        Button importButton = new Button("Import…");
        importButton.setOnAction(e -> importBook());
        Button export = new Button("Export…");
        export.setOnAction(e -> exportBook());
        Button refresh = Styles.on(new Button("⟳"), "em-icon");
        refresh.setTooltip(new Tooltip("Read the accounts, the instances and their states again (F5)"));
        Runnable reread = () -> {
            found = null;
            load();
        };
        refresh.setOnAction(e -> reread.run());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HostUndo undo = new HostUndo(services, this::load);
        HBox bar = new HBox(8, text, kindBar(), gap, add, paste, importButton, export, undo.node(), refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("em-header");

        table.getColumns().setAll(List.of(accountColumn(), column("Note", 200, row -> row.account().note()),
                passwordColumn(), instancesColumn()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No account. + Account adds one, or an account set in an emulator's"
                + " configuration shows here."));
        table.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            // the list refilled drops and restores the selection itself: the panel follows only the end of it
            if (refilling) return;
            // null too when the user deselects, so a later read leaves it so
            picked = now == null ? null : now.account().key();
            detail(now);
        });

        side.setPadding(new Insets(10));
        side.getChildren().setAll(top, notes, rest);
        ScrollPane panel = new ScrollPane(side);
        panel.setFitToWidth(true);
        panel.setPrefWidth(300);
        panel.setMinWidth(260);
        panel.getStyleClass().add("em-inspector-pane");
        BorderPane list = new BorderPane(table);
        list.setRight(panel);
        VBox.setVgrow(list, Priority.ALWAYS);

        Label hint = Styles.on(new Label("The accounts this PC knows, for every emulator and every bot. Passwords stay"
                + " in this PC's credential store; an export holds the kind, the name and the note only."),
                StyleClasses.DIALOG_HINT);
        hint.setWrapText(true);
        status.setWrapText(true);
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        status.visibleProperty().bind(status.managedProperty());
        VBox body = new VBox(8, bar, hint, list, status);
        body.setPadding(new Insets(0, 10, 10, 10));
        detail(null);

        BorderPane root = new BorderPane(body);
        root.getStyleClass().add(Look.WINDOW);
        root.getStylesheets().add(Look.SHEET);
        stage = Modals.window(services, Modals.Frame.modeless("Accounts", 960, 540, 700, 360), root);
        WindowKeys.install(stage.getScene(), Map.of(
                WindowKeys.Action.REFRESH, reread,
                WindowKeys.Action.UNDO, undo::undo,
                WindowKeys.Action.REDO, undo::redo,
                WindowKeys.Action.FIND, () -> {
                    text.requestFocus();
                    text.selectAll();
                }));
        stage.setOnShown(e -> {
            undo.start();
            load();
        });
        stage.setOnHidden(e -> {
            undo.stop();
            if (current == this) current = null;
        });
    }

    /** All N, Google N, Facebook N, Mail N: one pressed at a time, its count renewed by each read. */
    private HBox kindBar() {
        ToggleGroup group = new ToggleGroup();
        HBox bar = new HBox();
        bar.getStyleClass().add("em-segmented");
        for (AccountsListModel.KindCount segment : AccountsListModel.counts(List.of())) {
            ToggleButton one = new ToggleButton(segment.said());
            one.setUserData(segment.kind());
            one.setToggleGroup(group);
            one.setMinWidth(Region.USE_PREF_SIZE);
            segments.add(one);
            bar.getChildren().add(one);
        }
        segments.getFirst().getStyleClass().add("em-first");
        segments.getLast().getStyleClass().add("em-last");
        segments.getFirst().setSelected(true);
        group.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) {
                // pressing the segment that is down leaves it down: one kind, or All, is always picked
                was.setSelected(true);
                return;
            }
            kind = (AccountRecord.Kind) now.getUserData();
            filter();
        });
        return bar;
    }

    // ---- reading ----

    /**
     * Reads the book, the configurations and which passwords this PC keeps, off the FX thread: the store can block,
     * and discovery names the instances an emulator's layer reaches. Discovery, and each wanted instance's state, run
     * on the first read and on Refresh; a read that a newer one overtook is dropped.
     */
    private void load() {
        int read = ++reads;
        Found discovered = found;
        PasswordStore store = EmulatorsWindow.passwords();
        Async.load("accounts-read", () -> {
            EmulatorConfig config = HostConfig.load(services).config();
            EmulatorsModel.Source source = EmulatorsModel.Source.live(services);
            Found now = states(discovered != null ? discovered : found(source), source, config);
            List<AccountRecord> known = known(services, config);
            boolean available = store.available();
            Set<String> remembered = new HashSet<>();
            if (available) {
                for (String key : AccountsListModel.keys(known)) if (store.remembered(key)) remembered.add(key);
            }
            return new Read(now, known, AccountsListModel.rows(known, HostBook.onInstances(config, now.keys()),
                    HostBook.held(config), remembered), available);
        }, done -> {
            if (read != reads) return;
            found = done.found();
            book = done.book();
            all = done.rows();
            storeAvailable = done.storeAvailable();
            filter();
        }, failed -> say("The accounts were not read: " + failed));
    }

    private record Read(Found found, List<AccountRecord> book, List<AccountsListModel.Row> rows,
                        boolean storeAvailable) {
    }

    /**
     * What discovery found: the instances' keys, each key's instance (to open it in the Emulators window), and the
     * state of each instance some account is wanted on, as of this read.
     */
    private record Found(List<InstanceKey> keys, Map<InstanceKey, EmulatorInstance> instances,
                         Map<InstanceKey, Look.Pill> states) {
    }

    /** Discovers this PC's instances, asking none of them anything yet. Off the FX thread. */
    private static Found found(EmulatorsModel.Source source) {
        Map<InstanceKey, EmulatorInstance> instances = new LinkedHashMap<>();
        for (EmulatorInstance instance : EmulatorsModel.instances(source)) {
            instances.putIfAbsent(InstanceKey.of(instance), instance);
        }
        return new Found(List.copyOf(instances.keySet()), Map.copyOf(instances), Map.of());
    }

    /**
     * {@code found} with a state for each instance an account is wanted on that has none yet: all of them after a
     * discovery, then only those an account came to be wanted on since. The instances are asked at once. Off FX.
     */
    private static Found states(Found found, EmulatorsModel.Source source, EmulatorConfig config) {
        Set<InstanceKey> wanted = new HashSet<>();
        HostBook.onInstances(config, found.keys()).values().forEach(wanted::addAll);
        List<InstanceKey> ask = found.keys().stream()
                .filter(key -> wanted.contains(key) && !found.states().containsKey(key)).toList();
        if (ask.isEmpty()) return found;
        Map<InstanceKey, Look.Pill> states = new HashMap<>(found.states());
        states.putAll(ask.parallelStream().collect(Collectors.toMap(key -> key,
                key -> state(source, found.instances().get(key)))));
        return new Found(found.keys(), found.instances(), Map.copyOf(states));
    }

    private static Look.Pill state(EmulatorsModel.Source source, EmulatorInstance instance) {
        try {
            return Look.pill(source.running(instance), true);
        } catch (RuntimeException unread) {
            // its chip says "not read"; one instance that fails to answer hides no other
            return Look.pill(false, false);
        }
    }

    /**
     * The book as this PC knows it, with every configuration's accounts added. Reads this PC's values, and on the
     * book's first use the PC's own Google account, so never on the FX thread.
     */
    public static List<AccountRecord> known(StudioServices services, EmulatorConfig config) {
        return io(() -> HostConfig.accounts(services).known(config));
    }

    /** Lists the rows the Find text and the kind keep, the account picked still picked, and renews the counts. */
    private void filter() {
        List<AccountsListModel.KindCount> counts = AccountsListModel.counts(all);
        for (int i = 0; i < segments.size(); i++) segments.get(i).setText(counts.get(i).said());
        AccountRecord.Key keep = picked;
        refilling = true;
        try {
            table.getItems().setAll(AccountsListModel.filtered(all, text.getText(), kind));
            table.getItems().stream().filter(row -> row.account().key().equals(keep)).findFirst()
                    .ifPresentOrElse(table.getSelectionModel()::select, table.getSelectionModel()::clearSelection);
        } finally {
            refilling = false;
        }
        // an account the Find text or the kind hides stays picked, and is shown again when they let it be
        detail(table.getSelectionModel().getSelectedItem());
    }

    private void say(String words) {
        status.setText(words == null ? "" : words);
    }

    // ---- the detail panel ----

    /**
     * The panel for {@code row}, or a hint for none; left as it is when the row, the store and the states are as it
     * shows them. The note's field stays while the account does, so a read that lands while the user types keeps
     * what they typed.
     */
    private void detail(AccountsListModel.Row row) {
        if (row != null && row.equals(shown) && storeAvailable == shownStore && found == shownFound) return;
        shown = row;
        shownStore = storeAvailable;
        shownFound = found;
        top.getChildren().clear();
        rest.getChildren().clear();
        if (row == null) {
            notes.getChildren().clear();
            noteField = null;
            noteKey = null;
            top.getChildren().add(Look.muted("Pick an account to see its note, its password and the instances that"
                    + " want it."));
            return;
        }
        AccountRecord account = row.account();
        top.getChildren().addAll(Look.heading(account.name(), Look.avatar(account.name(), account.key().name())),
                Look.muted(account.kind().displayName() + " account"));

        if (account.key().equals(noteKey)) {
            noteWritten[0] = account.note();
            if (!noteField.isFocused()) noteField.setText(account.note());
        } else {
            notes.getChildren().setAll(Look.cap("Note"), note(account));
        }

        rest.getChildren().add(Look.cap("Password"));
        Label pill = Look.pill(row.password().pill());
        HBox password = new HBox(6, pill);
        password.setAlignment(Pos.CENTER_LEFT);
        if (row.canForget()) password.getChildren().add(button("Forget", () -> forget(row)));
        if (row.canRemember(storeAvailable)) password.getChildren().add(button("Remember…", () -> remember(row)));
        rest.getChildren().addAll(password, Look.muted(row.password().words(storeAvailable)));

        rest.getChildren().add(Look.cap("On instances"));
        if (row.on().isEmpty()) {
            rest.getChildren().add(Look.muted("No configuration wants it on an instance."));
        }
        for (InstanceKey key : row.on()) rest.getChildren().add(instanceChip(key));

        VBox actions = new VBox(6);
        actions.setPadding(new Insets(8, 0, 0, 0));
        if (account.kind() == AccountRecord.Kind.GOOGLE) {
            Hyperlink grid = new Hyperlink("Show in grid →");
            grid.setTooltip(new Tooltip("The Emulators window's Accounts tab, on this account's row"));
            grid.setOnAction(e -> EmulatorsWindow.openForAccount(services, account));
            actions.getChildren().add(grid);
        } else if (!row.on().isEmpty()) {
            Hyperlink instances = new Hyperlink("Show its instances →");
            instances.setOnAction(e -> EmulatorsWindow.openForAccount(services, account));
            actions.getChildren().add(instances);
        }
        if (row.removable()) {
            Button remove = button("Remove", () -> remove(row));
            remove.setTooltip(new Tooltip("No configuration lists it, so it can leave the accounts"));
            actions.getChildren().add(remove);
        }
        rest.getChildren().add(actions);
    }

    /**
     * The note, edited in place: written when Enter is pressed or the focus moves to another control, if it changed;
     * the window alone losing the focus (another window, a dialog) writes nothing.
     */
    private TextField note(AccountRecord account) {
        TextField field = new TextField(account.note());
        field.setPromptText("No note");
        String[] written = {account.note()};
        Runnable commit = () -> {
            String typed = field.getText().strip();
            if (typed.equals(written[0])) return;
            written[0] = typed;
            writeNote(new AccountRecord(account.kind(), account.name(), typed));
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((o, was, now) -> {
            // still the scene's focus owner: only the window lost the focus
            if (!now && (field.getScene() == null || field.getScene().getFocusOwner() != field)) commit.run();
        });
        noteField = field;
        noteKey = account.key();
        noteWritten = written;
        return field;
    }

    /** {@code key}'s chip with its state pill; a link to it in the Emulators window when discovery found it. */
    private Node instanceChip(InstanceKey key) {
        EmulatorInstance instance = found == null ? null : found.instances().get(key);
        Look.Pill state = instance == null ? new Look.Pill(Look.Tone.OUTLINED, "Not found")
                : Objects.requireNonNullElse(found.states().get(key), Look.pill(false, false));
        HBox line = new HBox(6, Look.chip(Look.badge(key.platform()), key.reference()), Look.pill(state));
        line.setAlignment(Pos.CENTER_LEFT);
        if (instance == null) {
            Tooltip.install(line, new Tooltip("The last read found no instance " + key.reference() + " on this PC"));
            return line;
        }
        Hyperlink open = new Hyperlink(null, line);
        open.setTooltip(new Tooltip("Open " + key.reference() + " in the Emulators window"));
        open.setOnAction(e -> EmulatorsWindow.open(services, instance));
        return open;
    }

    // ---- the actions ----

    /** Queues the note as typed, empty or not; the notes are written one at a time, each account's last one. */
    private void writeNote(AccountRecord changed) {
        notesPending.remove(changed.key());
        notesPending.put(changed.key(), changed);
        if (!noteWriting) nextNote();
    }

    /**
     * Writes the oldest note waiting. An empty note is refused while a configuration lists the account with one:
     * reading the book fills an empty note from it, so the clear would not last.
     */
    private void nextNote() {
        if (notesPending.isEmpty()) {
            noteWriting = false;
            return;
        }
        noteWriting = true;
        AccountRecord changed = notesPending.remove(notesPending.keySet().iterator().next());
        String name = changed.name();
        Async.load("accounts-note", () -> {
            String listed = changed.note().isEmpty()
                    ? HostBook.listedNote(HostConfig.load(services).config(), changed.key()) : "";
            if (listed.isEmpty()) io(() -> HostConfig.accounts(services).put(changed));
            return listed;
        }, listed -> {
            say(!listed.isEmpty() ? "The note of " + name + " stays: an emulator's configuration lists it with the"
                    + " note \"" + listed + "\". Change it there to clear it."
                    : changed.note().isEmpty() ? "The note of " + name + " is cleared."
                    : "The note of " + name + " is saved.");
            nextNote();
            load();
        }, failed -> {
            say("The note was not saved: " + failed);
            nextNote();
            load();
        });
    }

    private void forget(AccountsListModel.Row row) {
        String name = row.account().name();
        if (!confirm("Forget the password of " + name + "?", "The next Apply that signs it in asks it again.",
                "Forget")) return;
        PasswordStore store = EmulatorsWindow.passwords();
        Async.load("accounts-forget", () -> {
            store.forget(name);
            return store.remembered(name);
        }, still -> {
            say(still ? "This PC's credential store kept the password of " + name + "."
                    : "The password of " + name + " is forgotten.");
            load();
        }, failed -> say("The password was not forgotten: " + failed));
    }

    /** Asks the password and keeps it in the store; nothing is signed in. */
    private void remember(AccountsListModel.Row row) {
        String name = row.account().name();
        char[] password = askPassword(name);
        if (password == null) return;
        PasswordStore store = EmulatorsWindow.passwords();
        Async.load("accounts-remember", () -> {
            try {
                return store.remember(name, password);
            } finally {
                Arrays.fill(password, '\0');
            }
        }, kept -> {
            say(kept ? "The password of " + name + " is remembered on this PC."
                    : "This PC's credential store refused the password of " + name + ".");
            load();
        }, failed -> say("The password was not remembered: " + failed));
    }

    /**
     * Takes the account out of the book once no configuration lists it, read again when the removal runs: its
     * remembered password is forgotten first, so a store that fails leaves the account listed rather than a password
     * no row offers to forget.
     */
    private void remove(AccountsListModel.Row row) {
        AccountRecord account = row.account();
        boolean forget = row.password() == AccountsListModel.Password.REMEMBERED;
        if (!confirm("Remove " + account.name() + " from the accounts?", "No configuration lists it."
                + (forget ? " Its remembered password is forgotten too." : ""), "Remove")) return;
        PasswordStore store = EmulatorsWindow.passwords();
        Async.load("accounts-remove", () -> {
            if (HostBook.held(HostConfig.load(services).config()).contains(account.key())) return false;
            if (forget) {
                store.forget(account.name());
                if (store.remembered(account.name())) {
                    throw new IllegalStateException("this PC's credential store kept its password");
                }
            }
            io(() -> HostConfig.accounts(services).remove(account.key()));
            return true;
        }, removed -> {
            say(removed ? account.name() + " is removed."
                    : "A configuration lists " + account.name() + " now: it stays.");
            load();
        }, failed -> say("The account was not removed: " + failed));
    }

    private void addAccount() {
        newAccount().ifPresent(account -> Async.load("accounts-add",
                () -> io(() -> HostConfig.accounts(services).add(account)), after -> {
                    say(account.name() + " is added.");
                    picked = account.key();
                    load();
                }, failed -> say("The account was not added: " + failed)));
    }

    /** Adds every new account of a pasted list, of the kind picked, as one change; no password is asked here. */
    private void pasteList() {
        ComboBox<AccountRecord.Kind> kindPick = new ComboBox<>();
        kindPick.getItems().setAll(AccountsListModel.kinds());
        kindPick.setValue(AccountRecord.Kind.GOOGLE);
        kindPick.setButtonCell(plainKindCell());
        kindPick.setCellFactory(list -> plainKindCell());
        TextArea list = new TextArea();
        list.setPromptText("user1@example.com\nuser2@example.com");
        list.setPrefRowCount(10);
        list.setPrefColumnCount(36);
        Label preview = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
        preview.setWrapText(true);
        Label hint = Styles.on(new Label("One per line, or separated by commas. Passwords are asked at the first Apply"
                + " that signs one in, or remembered with Remember… here."), StyleClasses.DIALOG_HINT);
        hint.setWrapText(true);
        hint.setMaxWidth(420);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Accounts");
        dialog.setHeaderText("Add accounts from a list");
        dialog.getDialogPane().setContent(new VBox(8, hint, new HBox(8, new Label("Kind"), kindPick), list, preview));
        ButtonType add = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);
        Runnable check = () -> {
            AccountsListModel.Pasted pasted = AccountsListModel.pasted(list.getText(), kindPick.getValue(), book);
            preview.setText(list.getText().isBlank() ? "" : pasted.said("to add"));
            dialog.getDialogPane().lookupButton(add).setDisable(pasted.added().isEmpty());
        };
        list.textProperty().addListener((o, was, now) -> check.run());
        kindPick.valueProperty().addListener((o, was, now) -> check.run());
        check.run();
        services.theme().apply(dialog);
        Platform.runLater(list::requestFocus);
        if (dialog.showAndWait().filter(add::equals).isEmpty()) return;
        AccountsListModel.Pasted pasted = AccountsListModel.pasted(list.getText(), kindPick.getValue(), book);
        Async.load("accounts-paste", () -> io(() -> HostConfig.accounts(services).addAll(pasted.added())), after -> {
            say(pasted.said("added"));
            load();
        }, failed -> say("The accounts were not added: " + failed));
    }

    private void importBook() {
        Optional<Path> source = services.dialogs().chooseFile("Import accounts", null, EXTENSION).path();
        if (source.isEmpty()) return;
        String file = String.valueOf(source.get().getFileName());
        Async.load("accounts-import", () -> {
            List<AccountRecord> before = known(services, HostConfig.load(services).config());
            return AccountsListModel.imported(before, io(() -> HostConfig.accounts(services).importFrom(source.get())));
        }, imported -> {
            say(imported.said(file));
            load();
        }, failed -> say("The accounts were not imported: " + failed));
    }

    /** Writes the book as it is when the export runs, not as this window last read it. */
    private void exportBook() {
        Optional<Path> target = services.dialogs()
                .saveFile("Export accounts", null, "accounts" + EXTENSION, EXTENSION).path();
        if (target.isEmpty()) return;
        String file = String.valueOf(target.get().getFileName());
        Async.load("accounts-export", () -> {
            List<AccountRecord> exported = known(services, HostConfig.load(services).config());
            if (exported.isEmpty()) return 0;
            io(() -> {
                HostBook.export(exported, target.get());
                return null;
            });
            return exported.size();
        }, count -> say(count == 0 ? "There is no account to export."
                : count + (count == 1 ? " account" : " accounts") + " exported to " + file + ", with no password."),
                failed -> say("The accounts were not exported: " + failed));
    }

    // ---- the list ----

    private static TableColumn<AccountsListModel.Row, String> column(String title, double width,
                                                                    Function<AccountsListModel.Row, String> value) {
        TableColumn<AccountsListModel.Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    /** A column whose cells draw the whole row, by {@code draw}; sorted by {@code order}. */
    private static TableColumn<AccountsListModel.Row, AccountsListModel.Row> drawn(
            String title, double width, Comparator<AccountsListModel.Row> order,
            Function<AccountsListModel.Row, Node> draw) {
        TableColumn<AccountsListModel.Row, AccountsListModel.Row> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setComparator(order);
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(AccountsListModel.Row row, boolean empty) {
                super.updateItem(row, empty);
                setText(null);
                setGraphic(empty || row == null ? null : draw.apply(row));
            }
        });
        return column;
    }

    /** The avatar and the name; a Facebook or mail record says its kind under the name. */
    private static TableColumn<AccountsListModel.Row, AccountsListModel.Row> accountColumn() {
        return drawn("Account", 260, Comparator.comparing(row -> row.account().name(),
                String.CASE_INSENSITIVE_ORDER), row -> {
            AccountRecord account = row.account();
            Label name = new Label(account.name());
            Node words = account.kind() == AccountRecord.Kind.GOOGLE ? name
                    : new VBox(0, name, Look.muted(account.kind().displayName()));
            HBox out = new HBox(8, Look.avatar(account.name(), account.key().name()), words);
            out.setAlignment(Pos.CENTER_LEFT);
            return out;
        });
    }

    /** The password's pill, its whole words in the tooltip. */
    private TableColumn<AccountsListModel.Row, AccountsListModel.Row> passwordColumn() {
        return drawn("Password", 110, Comparator.comparing(AccountsListModel.Row::password), row -> {
            Label pill = Look.pill(row.password().pill());
            pill.setTooltip(new Tooltip(row.password().words(storeAvailable)));
            return pill;
        });
    }

    private static TableColumn<AccountsListModel.Row, AccountsListModel.Row> instancesColumn() {
        return drawn("Instances", 100, Comparator.comparingInt(AccountsListModel.Row::instances), row ->
                row.instances() == 0 ? Look.muted(row.instancesText()) : new Label(row.instancesText()));
    }

    private static Button button(String label, Runnable action) {
        Button button = new Button(label);
        button.setOnAction(e -> action.run());
        return button;
    }

    // ---- dialogs ----

    private boolean confirm(String header, String words, String yes) {
        ButtonType ok = new ButtonType(yes, ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, words, ok, ButtonType.CANCEL);
        alert.initOwner(stage);
        alert.setTitle("Accounts");
        alert.setHeaderText(header);
        services.theme().apply(alert);
        return alert.showAndWait().filter(ok::equals).isPresent();
    }

    /** The password, typed here and nowhere else, as characters; null when the user cancelled or typed nothing. */
    private char[] askPassword(String email) {
        PasswordField field = new PasswordField();
        Label why = Styles.on(new Label("Kept in this PC's credential store, and typed on an instance by the next Apply"
                + " that signs this account in, without asking. Nothing is signed in now, and no assistant ever sees"
                + " it."),
                StyleClasses.DIALOG_HINT);
        why.setWrapText(true);
        why.setMaxWidth(420);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Google password");
        dialog.setHeaderText("Remember the password of " + email);
        dialog.getDialogPane().setContent(new VBox(8, why, field));
        ButtonType remember = new ButtonType("Remember", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(remember, ButtonType.CANCEL);
        services.theme().apply(dialog);
        Platform.runLater(field::requestFocus);
        Optional<ButtonType> pressed = dialog.showAndWait();
        CharSequence typed = field.getCharacters();
        char[] password = null;
        if (pressed.isPresent() && pressed.get() == remember && typed.length() > 0) {
            password = new char[typed.length()];
            for (int i = 0; i < password.length; i++) password[i] = typed.charAt(i);
        }
        field.clear();
        return password;
    }

    /** + Account: a kind, a name or an email, and a note. Empty when the user cancelled. */
    private Optional<AccountRecord> newAccount() {
        ComboBox<AccountRecord.Kind> kindPick = new ComboBox<>();
        kindPick.getItems().setAll(AccountsListModel.kinds());
        kindPick.setValue(kind == null ? AccountRecord.Kind.GOOGLE : kind);
        kindPick.setButtonCell(plainKindCell());
        kindPick.setCellFactory(list -> plainKindCell());
        TextField name = new TextField();
        name.setPromptText("user@example.com");
        TextField note = new TextField();
        note.setPromptText("Optional");
        Label taken = Styles.on(new Label("The accounts already list it."), StyleClasses.MUTED_TEXT);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Kind"), kindPick);
        grid.addRow(1, new Label("Account"), name);
        grid.addRow(2, new Label("Note"), note);
        grid.add(taken, 1, 3);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Accounts");
        dialog.setHeaderText("Add an account");
        dialog.getDialogPane().setContent(grid);
        ButtonType add = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);
        Runnable check = () -> {
            AccountRecord.Key typed = new AccountRecord.Key(kindPick.getValue(), name.getText());
            boolean listed = book.stream().anyMatch(account -> account.key().equals(typed));
            taken.setVisible(listed);
            dialog.getDialogPane().lookupButton(add).setDisable(name.getText().isBlank() || listed);
        };
        name.textProperty().addListener((o, was, now) -> check.run());
        kindPick.valueProperty().addListener((o, was, now) -> check.run());
        check.run();
        services.theme().apply(dialog);
        Platform.runLater(name::requestFocus);
        return dialog.showAndWait().filter(add::equals)
                .map(pressed -> new AccountRecord(kindPick.getValue(), name.getText().strip(), note.getText().strip()));
    }

    private static ListCell<AccountRecord.Kind> plainKindCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AccountRecord.Kind item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        };
    }

    // ---- helpers ----

    private interface Io<T> {
        T get() throws IOException;
    }

    private static <T> T io(Io<T> work) {
        try {
            return work.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
