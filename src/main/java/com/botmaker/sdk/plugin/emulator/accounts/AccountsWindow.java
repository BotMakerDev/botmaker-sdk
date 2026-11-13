package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.shared.emulator.GoogleAccount;
import com.botmaker.shared.emulator.config.AccountBook;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.google.PasswordStore;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The Accounts window: every account this PC knows, for every emulator and every bot, with no bot open (board
 * Accounts, spec {@code 2026-11-13-google-accounts-design.md}). It lists {@link AccountBook}, forgets or remembers a
 * Google password in this PC's credential store, removes an account no configuration lists, and imports and exports
 * the book (kind, name and note: never a password). Which instance holds which account is the Emulators window's.
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
    private final ComboBox<Optional<AccountRecord.Kind>> kind = new ComboBox<>();
    private final Label status = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
    private final TableView<AccountsListModel.Row> table = new TableView<>();
    private List<AccountsListModel.Row> all = List.of();
    private List<AccountRecord> book = List.of();
    private boolean storeAvailable;
    /** The instances discovered by the first read, kept for the reads after an action; null until then. */
    private List<InstanceKey> instances;
    /** Counts the reads started: a read that ends after a newer one started is dropped. */
    private int reads;

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

        text.setPromptText("Filter");
        text.setPrefColumnCount(16);
        text.textProperty().addListener((o, was, now) -> filter());
        List<Optional<AccountRecord.Kind>> kinds = new ArrayList<>();
        kinds.add(Optional.empty());
        for (AccountRecord.Kind one : kinds()) kinds.add(Optional.of(one));
        kind.getItems().setAll(kinds);
        kind.setValue(Optional.empty());
        kind.setButtonCell(kindCell());
        kind.setCellFactory(list -> kindCell());
        kind.valueProperty().addListener((o, was, now) -> filter());

        Button add = new Button("+ Account");
        add.setOnAction(e -> addAccount());
        Button importButton = new Button("Import…");
        importButton.setOnAction(e -> importBook());
        Button export = new Button("Export…");
        export.setOnAction(e -> exportBook());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> {
            instances = null;
            load();
        });
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox bar = new HBox(8, text, kind, gap, add, importButton, export, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);

        table.getColumns().setAll(List.of(
                column("Account", 240, row -> row.account().name()),
                column("Kind", 90, row -> row.account().kind().displayName()),
                column("Note", 180, row -> row.account().note()),
                instancesColumn(),
                column("Password", 120, row -> row.password().displayName()),
                actionsColumn()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No account. + Account adds one, or an account set in an emulator's"
                + " configuration shows here."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Label hint = Styles.on(new Label("The accounts this PC knows, for every emulator and every bot. Passwords stay"
                + " in this PC's credential store; an export holds the kind, the name and the note only."),
                StyleClasses.DIALOG_HINT);
        hint.setWrapText(true);
        status.setWrapText(true);
        VBox body = new VBox(8, hint, bar, table, status);
        body.setPadding(new Insets(10));

        stage = Modals.window(services, Modals.Frame.modeless("Accounts", 860, 520, 620, 340), new BorderPane(body));
        stage.setOnShown(e -> load());
        stage.setOnHidden(e -> {
            if (current == this) current = null;
        });
    }

    // ---- reading ----

    /**
     * Reads the book, the configurations and which passwords this PC keeps, off the FX thread: the store can block,
     * and discovery names the instances an emulator's layer reaches. Discovery runs on the first read and on Refresh;
     * a read that a newer one overtook is dropped.
     */
    private void load() {
        int read = ++reads;
        List<InstanceKey> discovered = instances;
        PasswordStore store = EmulatorsWindow.passwords();
        Async.load("accounts-read", () -> {
            List<InstanceKey> keys = discovered != null ? discovered
                    : EmulatorsModel.keys(EmulatorsModel.Source.live(services));
            EmulatorConfig config = HostConfig.load(services).config();
            List<AccountRecord> known = known(config);
            boolean available = store.available();
            Set<String> remembered = new HashSet<>();
            if (available) {
                for (String key : AccountsListModel.keys(known)) if (store.remembered(key)) remembered.add(key);
            }
            return new Read(keys, known, AccountsListModel.rows(known, AccountBook.onInstances(config, keys),
                    AccountBook.held(config), remembered), available);
        }, done -> {
            if (read != reads) return;
            instances = done.instances();
            book = done.book();
            all = done.rows();
            storeAvailable = done.storeAvailable();
            filter();
        }, failed -> say("The accounts were not read: " + failed));
    }

    private record Read(List<InstanceKey> instances, List<AccountRecord> book, List<AccountsListModel.Row> rows,
                        boolean storeAvailable) {
    }

    /** The book as it is now, with what {@code config} lists added; off the FX thread. */
    /**
     * The book as this PC knows it: its file, every configuration's accounts and the PC's own Google account. Reads
     * files and the credential store, so never on the FX thread.
     */
    public static List<AccountRecord> known(EmulatorConfig config) {
        return io(() -> HostConfig.accounts().known(config, GoogleAccount.signedIn()));
    }

    private void filter() {
        table.getItems().setAll(AccountsListModel.filtered(all, text.getText(), kind.getValue().orElse(null)));
    }

    private void say(String words) {
        status.setText(words == null ? "" : words);
    }

    // ---- the actions ----

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
            if (AccountBook.held(HostConfig.load(services).config()).contains(account.key())) return false;
            if (forget) {
                store.forget(account.name());
                if (store.remembered(account.name())) {
                    throw new IllegalStateException("this PC's credential store kept its password");
                }
            }
            io(() -> HostConfig.accounts().remove(account.key()));
            return true;
        }, removed -> {
            say(removed ? account.name() + " is removed."
                    : "A configuration lists " + account.name() + " now: it stays.");
            load();
        }, failed -> say("The account was not removed: " + failed));
    }

    private void addAccount() {
        newAccount().ifPresent(account -> Async.load("accounts-add",
                () -> io(() -> HostConfig.accounts().add(account)), after -> {
                    say(account.name() + " is added.");
                    load();
                }, failed -> say("The account was not added: " + failed)));
    }

    private void importBook() {
        Optional<Path> source = services.dialogs().chooseFile("Import accounts", null, EXTENSION).path();
        if (source.isEmpty()) return;
        String file = String.valueOf(source.get().getFileName());
        Async.load("accounts-import", () -> {
            List<AccountRecord> before = known(HostConfig.load(services).config());
            return AccountsListModel.imported(before, io(() -> HostConfig.accounts().importFrom(source.get())));
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
            List<AccountRecord> exported = known(HostConfig.load(services).config());
            if (exported.isEmpty()) return 0;
            io(() -> {
                AccountBook.export(exported, target.get());
                return null;
            });
            return exported.size();
        }, count -> say(count == 0 ? "There is no account to export."
                : count + (count == 1 ? " account" : " accounts") + " exported to " + file + ", with no password."),
                failed -> say("The accounts were not exported: " + failed));
    }

    // ---- the table ----

    private static TableColumn<AccountsListModel.Row, String> column(String title, double width,
                                                                    Function<AccountsListModel.Row, String> value) {
        TableColumn<AccountsListModel.Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    /** <i>N instances</i>: a link to the Emulators window on the instances that want the account. */
    private TableColumn<AccountsListModel.Row, AccountsListModel.Row> instancesColumn() {
        TableColumn<AccountsListModel.Row, AccountsListModel.Row> column = new TableColumn<>("On instances");
        column.setPrefWidth(110);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(AccountsListModel.Row row, boolean empty) {
                super.updateItem(row, empty);
                setText(null);
                if (empty || row == null) {
                    setGraphic(null);
                } else if (row.instances() == 0) {
                    setGraphic(Styles.on(new Label(row.instancesText()), StyleClasses.MUTED_TEXT));
                } else {
                    Hyperlink link = new Hyperlink(row.instancesText());
                    link.setOnAction(e -> EmulatorsWindow.openForAccount(services, row.account()));
                    setGraphic(link);
                }
            }
        });
        return column;
    }

    private TableColumn<AccountsListModel.Row, AccountsListModel.Row> actionsColumn() {
        TableColumn<AccountsListModel.Row, AccountsListModel.Row> column = new TableColumn<>("");
        column.setPrefWidth(220);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(AccountsListModel.Row row, boolean empty) {
                super.updateItem(row, empty);
                setText(null);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                HBox buttons = new HBox(6);
                buttons.setAlignment(Pos.CENTER_LEFT);
                if (row.canForget()) buttons.getChildren().add(button("Forget password", () -> forget(row)));
                if (row.canRemember(storeAvailable)) {
                    buttons.getChildren().add(button("Remember…", () -> remember(row)));
                }
                if (row.removable()) buttons.getChildren().add(button("Remove", () -> remove(row)));
                setGraphic(buttons);
            }
        });
        return column;
    }

    private static Button button(String label, Runnable action) {
        Button button = new Button(label);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static ListCell<Optional<AccountRecord.Kind>> kindCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Optional<AccountRecord.Kind> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.map(AccountRecord.Kind::displayName).orElse("Any kind"));
            }
        };
    }

    private static List<AccountRecord.Kind> kinds() {
        return Arrays.stream(AccountRecord.Kind.values()).filter(one -> one != AccountRecord.Kind.UNKNOWN).toList();
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
        kindPick.getItems().setAll(kinds());
        kindPick.setValue(AccountRecord.Kind.GOOGLE);
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
