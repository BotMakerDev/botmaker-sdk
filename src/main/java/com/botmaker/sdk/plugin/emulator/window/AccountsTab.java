package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.accounts.AccountsWindow;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Accounts tab (board Grid): a row per Google account of the book, a column per instance, a tick per cell that
 * writes the instance's own layer at once ({@link AccountsGridModel#tick}). The book itself is the Accounts window's;
 * <i>Manage accounts…</i> opens it.
 */
final class AccountsTab {

    private static final String ANY_TAG = "Any tag";

    private final EmulatorsWindow window;
    private final ComboBox<Optional<PlatformId>> emulator = new ComboBox<>();
    private final ComboBox<String> tag = new ComboBox<>();
    private final Label accountFilter = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
    private final Button showAll = new Button("Show every account");
    private final Label changes = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
    private final Button undo = new Button("Undo ticks");
    private final Button apply = new Button("Apply…");
    private final GridPane grid = new GridPane();
    private final VBox root;
    /** Each ticked cell's instance layer before its first tick that was written, for <i>Undo ticks</i>. */
    private final Map<AccountsGridModel.Ticked, Layer> before = new LinkedHashMap<>();

    private List<AccountRecord> book = List.of();
    private AccountRecord.Key account;
    /** Counts the book's reads, so a slow one a newer one overtook is dropped. */
    private int reads;
    private boolean filling;
    private boolean visible;
    /** The book was not read since the last read of this PC: read when the tab shows. */
    private boolean stale = true;

    AccountsTab(EmulatorsWindow window) {
        this.window = window;
        emulator.setCellFactory(list -> emulatorCell());
        emulator.setButtonCell(emulatorCell());
        emulator.getItems().setAll(Optional.<PlatformId>empty());
        emulator.setValue(Optional.empty());
        tag.getItems().setAll(ANY_TAG);
        tag.setValue(ANY_TAG);
        emulator.valueProperty().addListener((o, was, now) -> draw());
        tag.valueProperty().addListener((o, was, now) -> draw());
        showAll.setOnAction(e -> {
            account = null;
            draw();
        });

        Button add = new Button("+ Account");
        add.setOnAction(e -> addAccount());
        Button manage = new Button("Manage accounts…");
        manage.setOnAction(e -> AccountsWindow.open(window.services()));
        undo.setOnAction(e -> undoTicks());
        apply.setOnAction(e -> window.applyItems(AccountsGridModel.toApply(columns(), rows())));

        FlowPane bar = new FlowPane(8, 6, emulator, tag, accountFilter, showAll, add, manage, changes, undo, apply);
        bar.setAlignment(Pos.CENTER_LEFT);
        for (Node node : bar.getChildren()) {
            if (node instanceof Region region) region.setMinWidth(Region.USE_PREF_SIZE);
        }

        grid.setHgap(14);
        grid.setVgap(6);
        grid.setPadding(new Insets(6, 2, 6, 2));
        ScrollPane scroll = new ScrollPane(grid);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Label hint = Styles.on(new Label("A tick changes the instance's own configuration; Apply signs in and out."
                + " Unticking an account its emulator lists removes it here only."), StyleClasses.MUTED_TEXT);
        hint.setWrapText(true);
        root = new VBox(8, bar, scroll, hint);
        root.setPadding(new Insets(8, 10, 8, 10));
        draw();
    }

    Node node() {
        return root;
    }

    /** Whether the tab is the one showing: the book is read only then, since it reads the credential store. */
    void visible(boolean now) {
        visible = now;
        if (visible && stale) readBook();
    }

    /** A new read of this PC: the filters' choices, the grid drawn again, and the book read again when showing. */
    void show(EmulatorsModel.Snapshot snapshot) {
        filling = true;
        List<Optional<PlatformId>> emulators = new ArrayList<>();
        emulators.add(Optional.empty());
        snapshot.emulators().stream().filter(row -> row.instances() > 0)
                .forEach(row -> emulators.add(Optional.of(row.platform())));
        Optional<PlatformId> picked = emulator.getValue();
        emulator.getItems().setAll(emulators);
        emulator.setValue(emulators.contains(picked) ? picked : Optional.empty());
        String tagPicked = tag.getValue() == null ? ANY_TAG : tag.getValue();
        List<String> tags = new ArrayList<>(List.of(ANY_TAG));
        tags.addAll(snapshot.tags());
        tag.getItems().setAll(tags);
        tag.setValue(tags.stream().filter(tagPicked::equalsIgnoreCase).findFirst().orElse(ANY_TAG));
        filling = false;
        draw();
        stale = true;
        if (visible) readBook();
    }

    /** The Accounts window's <i>N instances</i>: this account's row, over every instance. */
    void showAccount(AccountRecord.Key key) {
        filling = true;
        emulator.setValue(Optional.empty());
        tag.setValue(ANY_TAG);
        filling = false;
        account = key;
        draw();
    }

    private void readBook() {
        stale = false;
        int read = ++reads;
        EmulatorConfig config = window.snapshot().config();
        Async.load("accounts-grid", () -> AccountsWindow.known(config), known -> {
            if (read != reads) return;
            book = known;
            draw();
        }, failed -> {
            if (read != reads) return;
            window.say("The accounts could not be read: " + failed);
            draw();
        });
    }

    private AccountsGridModel.Filter filter() {
        String picked = tag.getValue();
        return new AccountsGridModel.Filter(emulator.getValue() == null ? null : emulator.getValue().orElse(null),
                picked == null || picked.equals(ANY_TAG) ? "" : picked, account);
    }

    private List<EmulatorsModel.InstanceRow> columns() {
        return AccountsGridModel.columns(window.snapshot().instances(), filter());
    }

    private List<AccountRecord> rows() {
        return AccountsGridModel.rows(book, filter());
    }

    private void draw() {
        if (filling) return;
        EmulatorConfig config = window.snapshot().config();
        List<EmulatorsModel.InstanceRow> columns = columns();
        List<AccountRecord> rows = rows();
        accountFilter.setText(account == null ? "" : "Only " + account.name());
        for (Node node : List.of(accountFilter, showAll)) {
            node.setVisible(account != null);
            node.setManaged(account != null);
        }
        int count = AccountsGridModel.changes(AccountsGridModel.toApply(columns, rows));
        changes.setText(count == 0 ? "Nothing to apply" : EmulatorsModel.count(count, "sign-in or sign-out")
                + " to apply");
        apply.setDisable(count == 0);
        undo.setDisable(before.isEmpty());

        grid.getChildren().clear();
        if (columns.isEmpty() || rows.isEmpty()) {
            String empty = columns.isEmpty() ? "No instance matches: pick another emulator or tag."
                    : account != null ? account.name() + " is not among this PC's Google accounts."
                    : "No Google account in the book: + Account adds one.";
            grid.add(Styles.on(new Label(empty), StyleClasses.MUTED_TEXT), 0, 0);
            return;
        }
        grid.add(Styles.on(new Label("Account"), StyleClasses.STRONG_TEXT), 0, 0);
        for (int c = 0; c < columns.size(); c++) {
            EmulatorsModel.InstanceRow column = columns.get(c);
            Label name = Styles.on(new Label(column.instance().name()), StyleClasses.STRONG_TEXT);
            Label product = Styles.on(new Label(column.key().platform().displayName()
                    + (column.running() ? " · running" : " · stopped")),
                    StyleClasses.MUTED_TEXT, StyleClasses.SMALL_TEXT);
            grid.add(new VBox(1, name, product), c + 1, 0);
        }
        for (int r = 0; r < rows.size(); r++) {
            AccountRecord one = rows.get(r);
            Label name = new Label(one.name());
            if (!one.note().isEmpty()) name.setTooltip(new Tooltip(one.note()));
            grid.add(name, 0, r + 1);
            for (int c = 0; c < columns.size(); c++) {
                grid.add(cell(config, columns.get(c), one), c + 1, r + 1);
            }
        }
        grid.add(Styles.on(new Label("Accounts"), StyleClasses.MUTED_TEXT), 0, rows.size() + 1);
        for (int c = 0; c < columns.size(); c++) {
            int held = AccountsGridModel.count(config, columns.get(c).key());
            grid.add(Styles.on(new Label(String.valueOf(held)), StyleClasses.MUTED_TEXT), c + 1, rows.size() + 1);
        }
    }

    private Node cell(EmulatorConfig config, EmulatorsModel.InstanceRow column, AccountRecord one) {
        AccountsGridModel.Cell cell = AccountsGridModel.cell(config, column, one);
        CheckBox box = new CheckBox(cell.displayName());
        box.setSelected(cell.ticked());
        switch (cell) {
            case TO_SIGN_IN -> Styles.on(box, StyleClasses.OK_TEXT);
            case TO_SIGN_OUT -> Styles.on(box, StyleClasses.ERROR_TEXT);
            case SIGNED_IN, NONE, UNKNOWN -> { }
            default -> Styles.on(box, StyleClasses.MUTED_TEXT);
        }
        if (AccountsGridModel.inherited(config, column.key(), one)) {
            box.setTooltip(new Tooltip(column.key().platform().displayName() + "'s configuration lists it;"
                    + " unticking removes it on this instance only."));
        }
        box.setOnAction(e -> tick(column, one, box.isSelected()));
        return box;
    }

    /** Writes the tick; the layer before it is kept for <i>Undo ticks</i> only once the write landed. */
    private void tick(EmulatorsModel.InstanceRow column, AccountRecord one, boolean on) {
        InstanceKey key = column.key();
        Optional<Set<String>> held = column.android().flatMap(AndroidState::googleAccounts);
        AtomicReference<Layer> was = new AtomicReference<>();
        window.change(config -> {
            was.set(config.instance(key));
            return AccountsGridModel.tick(config, key, one, on, held);
        }, () -> {
            before.putIfAbsent(new AccountsGridModel.Ticked(key, one.key()), was.get());
            undo.setDisable(false);
        });
    }

    private void undoTicks() {
        Map<AccountsGridModel.Ticked, Layer> undone = Map.copyOf(before);
        if (undone.isEmpty()) return;
        window.change(config -> AccountsGridModel.undo(config, undone), () -> {
            undone.keySet().forEach(before::remove);
            undo.setDisable(before.isEmpty());
        });
    }

    /** Adds a Google account to the book; the tab stays, its new row unticked. */
    private void addAccount() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(window.stage());
        dialog.setTitle("+ Account");
        dialog.setHeaderText("Add a Google account to this PC's accounts");
        dialog.getEditor().setPromptText("user@example.com");
        window.services().theme().apply(dialog);
        Optional<String> typed = dialog.showAndWait().map(String::strip).filter(text -> !text.isEmpty());
        if (typed.isEmpty()) return;
        AccountRecord added = new AccountRecord(AccountRecord.Kind.GOOGLE, typed.get());
        if (book.stream().anyMatch(known -> known.key().equals(added.key()))) {
            window.say(added.name() + " is in the accounts already.");
            return;
        }
        Async.load("accounts-add", () -> {
            try {
                return HostConfig.accounts().add(added);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, after -> readBook(), failed -> window.say("The account was not added: " + failed));
    }

    private static ListCell<Optional<PlatformId>> emulatorCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Optional<PlatformId> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.map(PlatformId::displayName).orElse("Any emulator"));
            }
        };
    }
}
