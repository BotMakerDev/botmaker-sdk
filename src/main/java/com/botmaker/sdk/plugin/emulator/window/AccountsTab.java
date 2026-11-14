package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.accounts.AccountsWindow;
import com.botmaker.sdk.plugin.emulator.host.HostBook;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import com.botmaker.shared.emulator.google.PasswordStore;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.botmaker.sdk.plugin.emulator.window.Look.heading;
import static com.botmaker.sdk.plugin.emulator.window.Look.line;
import static com.botmaker.sdk.plugin.emulator.window.Look.muted;

/**
 * The Accounts tab (board Grid): a row per Google account of the book, a column per instance, a glyph per cell whose
 * click writes the instance's own layer at once ({@link AccountsGridModel#tick}), and beside the grid an inspector of
 * the account picked. The book itself is the Accounts window's; <i>Manage accounts…</i> opens it.
 */
final class AccountsTab {

    private static final String ANY_TAG = "Any tag";
    private static final double INSPECTOR = 280;
    /** The legend's cells, in the order it says them; a removed-here cell is drawn as a sign-out. */
    private static final List<AccountsGridModel.Cell> LEGEND = List.of(AccountsGridModel.Cell.SIGNED_IN,
            AccountsGridModel.Cell.TO_SIGN_IN, AccountsGridModel.Cell.NOT_READ, AccountsGridModel.Cell.TO_SIGN_OUT,
            AccountsGridModel.Cell.UNLISTED, AccountsGridModel.Cell.NONE);

    private final EmulatorsWindow window;
    private final ComboBox<Optional<PlatformId>> emulator = new ComboBox<>();
    private final ComboBox<String> tag = new ComboBox<>();
    private final Label accountFilter = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
    private final Button showAll = new Button("Show every account");
    private final Button spread = new Button("One account per instance");
    private final Button undo = new Button("Undo ticks");
    private final Button apply = Styles.on(new Button("Apply…"), StyleClasses.PRIMARY_BUTTON);
    private final GridPane grid = new GridPane();
    private final VBox inspector = new VBox(4);
    private final VBox root;
    /** Each ticked cell's instance layer before its first tick that was written, for <i>Undo ticks</i>. */
    private final Map<AccountsGridModel.Ticked, Layer> before = new LinkedHashMap<>();

    private Book book = Book.NONE;
    /** The book's accounts held through two of their addresses, over every instance; renewed by each draw. */
    private List<HostBook.Shared> shared = List.of();
    private AccountRecord.Key account;
    /** The account the inspector shows, picked by a click on its name or one of its cells. */
    private AccountRecord.Key picked;
    /** Counts the book's reads, so a slow one a newer one overtook is dropped. */
    private int reads;
    private boolean filling;
    private boolean visible;
    /** The book was not read since the last read of this PC: read when the tab shows. */
    private boolean stale = true;

    /** The store is asked again about every password at the next read: the tab was shown anew. */
    private boolean recheck = true;
    /** This draw's row headers and cells, so a pick or a focus needs no new draw. */
    private final Map<AccountRecord.Key, Label> headers = new LinkedHashMap<>();
    private final Map<AccountsGridModel.Ticked, Button> cells = new LinkedHashMap<>();

    /**
     * The book, and what this PC's store keeps of its passwords ({@link PasswordStore#key}).
     *
     * @param asked the keys the store was asked about, so a read after a tick asks only about new accounts
     */
    private record Book(List<AccountRecord> accounts, Set<String> asked, Set<String> remembered,
                        boolean storeAvailable) {
        static final Book NONE = new Book(List.of(), Set.of(), Set.of(), false);
    }

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

        Button manage = new Button("Manage accounts…");
        manage.setOnAction(e -> AccountsWindow.open(window.services()));
        spread.setTooltip(new Tooltip("Give each instance shown one account shown, no two the same. An instance "
                + "that has one keeps it; the rest of the shown accounts are unticked on it."));
        spread.setOnAction(e -> tickAll(AccountsGridModel.spread(window.snapshot().config(), columns(), rows()),
                "every instance shown already has one account of its own"));
        undo.setOnAction(e -> undoTicks());
        apply.setOnAction(e -> window.applyItems(AccountsGridModel.toApply(columns(), rows())));

        FlowPane bar = new FlowPane(8, 6, emulator, tag, accountFilter, showAll, manage, spread, undo, apply);
        bar.setAlignment(Pos.CENTER_LEFT);
        for (Node node : bar.getChildren()) {
            if (node instanceof Region region) region.setMinWidth(Region.USE_PREF_SIZE);
        }

        grid.setHgap(10);
        grid.setVgap(4);
        grid.setPadding(new Insets(6, 2, 6, 2));
        ScrollPane scroll = new ScrollPane(grid);
        HBox.setHgrow(scroll, Priority.ALWAYS);

        inspector.setPadding(new Insets(4, 4, 4, 12));
        ScrollPane inspectorPane = new ScrollPane(inspector);
        inspectorPane.getStyleClass().add("em-inspector-pane");
        inspectorPane.setFitToWidth(true);
        inspectorPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        inspectorPane.setPrefWidth(INSPECTOR);
        inspectorPane.setMinWidth(INSPECTOR);
        HBox body = new HBox(8, scroll, inspectorPane);
        VBox.setVgrow(body, Priority.ALWAYS);

        Label hint = muted("A click on a cell changes the instance's own configuration; Apply signs in and out."
                + " Unticking an account its emulator lists removes it here only.");
        root = new VBox(8, bar, legend(), body, hint);
        root.setPadding(new Insets(8, 10, 8, 10));
        draw();
    }

    Node node() {
        return root;
    }

    /** Whether the tab is the one showing: the book is read only then, since it reads the credential store. */
    void visible(boolean now) {
        if (now && !visible) recheck = true;
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
        Optional<PlatformId> chosen = emulator.getValue();
        emulator.getItems().setAll(emulators);
        emulator.setValue(emulators.contains(chosen) ? chosen : Optional.empty());
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

    /** The Accounts window's <i>N instances</i>: this account's row, over every instance, shown in the inspector. */
    void showAccount(AccountRecord.Key key) {
        filling = true;
        emulator.setValue(Optional.empty());
        tag.setValue(ANY_TAG);
        filling = false;
        account = key;
        picked = key;
        draw();
    }

    /**
     * Reads the book, off the FX thread, and asks the store, which can block, about the passwords it was not asked
     * about since the tab was shown: a read after each tick asks about no password it already knows.
     */
    private void readBook() {
        stale = false;
        int read = ++reads;
        EmulatorConfig config = window.snapshot().config();
        PasswordStore store = EmulatorsWindow.passwords();
        Book last = recheck ? Book.NONE : book;
        recheck = false;
        Async.load("accounts-grid", () -> {
            List<AccountRecord> known = AccountsWindow.known(window.services(), config);
            if (!store.available()) return new Book(known, Set.of(), Set.of(), false);
            Set<String> keys = new HashSet<>();
            known.stream().filter(one -> one.kind().signedInByBotMaker())
                    .forEach(one -> one.addresses().forEach(address -> keys.add(PasswordStore.key(address))));
            Set<String> ask = new HashSet<>(keys);
            ask.removeAll(last.asked());
            Set<String> remembered = new HashSet<>(last.remembered());
            remembered.retainAll(keys);
            remembered.addAll(ApplyPlan.remembered(ask, store));
            return new Book(known, keys, remembered, true);
        }, done -> {
            if (read != reads) return;
            book = done;
            draw();
        }, failed -> {
            if (read != reads) return;
            window.say("The accounts could not be read: " + failed);
            draw();
        });
    }

    private AccountsGridModel.Filter filter() {
        String chosen = tag.getValue();
        return new AccountsGridModel.Filter(emulator.getValue() == null ? null : emulator.getValue().orElse(null),
                chosen == null || chosen.equals(ANY_TAG) ? "" : chosen, account);
    }

    private List<EmulatorsModel.InstanceRow> columns() {
        return AccountsGridModel.columns(window.snapshot().instances(), filter());
    }

    private List<AccountRecord> rows() {
        return AccountsGridModel.rows(book.accounts(), filter());
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
        List<ApplyPlan.Item> items = AccountsGridModel.toApply(columns, rows);
        int count = AccountsGridModel.applyCount(items);
        apply.setText(count == 0 ? "Apply…" : "Apply " + EmulatorsModel.count(count, "change") + "…");
        apply.setDisable(count == 0);
        undo.setDisable(before.isEmpty());
        spread.setDisable(columns.isEmpty() || rows.isEmpty());
        if (picked != null && rows.stream().noneMatch(one -> one.key().equals(picked))) picked = null;
        shared = HostBook.shared(config, window.snapshot().instances().stream().map(EmulatorsModel.InstanceRow::key)
                .toList(), book.accounts());
        inspect(config, columns, rows, AccountsGridModel.pending(items));

        Node focused = root == null || root.getScene() == null ? null : root.getScene().getFocusOwner();
        AccountsGridModel.Ticked focusAfter = cells.entrySet().stream().filter(e -> e.getValue() == focused)
                .map(Map.Entry::getKey).findFirst().orElse(null);
        grid.getChildren().clear();
        headers.clear();
        cells.clear();
        if (columns.isEmpty() || rows.isEmpty()) {
            String empty = columns.isEmpty() ? "No instance matches: pick another emulator or tag."
                    : account != null ? account.name() + " is not among this PC's Google accounts."
                    : "No Google account in the book: Manage accounts… adds one.";
            grid.add(muted(empty), 0, 0);
            return;
        }
        grid.add(Look.cap("Account"), 0, 0);
        for (int c = 0; c < columns.size(); c++) {
            EmulatorsModel.InstanceRow column = columns.get(c);
            Label name = Styles.on(new Label(column.instance().name(), Look.badge(column.key().platform())),
                    StyleClasses.STRONG_TEXT);
            name.setGraphicTextGap(5);
            HBox header = new HBox(5, name, Look.dot(Look.pill(column.running(), column.facts() != null)));
            header.setAlignment(Pos.CENTER_LEFT);
            menu(header, () -> columnMenu(window.snapshot().config(), column, rows));
            grid.add(header, c + 1, 0);
        }
        for (int r = 0; r < rows.size(); r++) {
            AccountRecord one = rows.get(r);
            grid.add(rowHeader(config, columns, one), 0, r + 1);
            for (int c = 0; c < columns.size(); c++) {
                grid.add(cell(config, columns.get(c), one), c + 1, r + 1);
            }
        }
        grid.add(muted("Accounts"), 0, rows.size() + 1);
        for (int c = 0; c < columns.size(); c++) {
            int held = AccountsGridModel.count(config, columns.get(c).key());
            Label total = muted(String.valueOf(held));
            GridPane.setHalignment(total, HPos.CENTER);
            grid.add(total, c + 1, rows.size() + 1);
        }
        // a cell that had the focus keeps it in the new draw, so the keyboard can tick one cell after another
        if (focusAfter != null && cells.containsKey(focusAfter)) cells.get(focusAfter).requestFocus();
    }

    /** Shows {@code key}'s account in the inspector and marks its name, with no new draw of the grid. */
    private void pick(AccountRecord.Key key) {
        picked = key;
        headers.forEach((one, header) -> {
            header.getStyleClass().remove("em-picked");
            if (one.equals(key)) header.getStyleClass().add("em-picked");
        });
        List<EmulatorsModel.InstanceRow> columns = columns();
        List<AccountRecord> rows = rows();
        inspect(window.snapshot().config(), columns, rows,
                AccountsGridModel.pending(AccountsGridModel.toApply(columns, rows)));
    }

    /** The account's name with its avatar: a click shows it in the inspector, a right-click ticks its row. */
    private Node rowHeader(EmulatorConfig config, List<EmulatorsModel.InstanceRow> columns, AccountRecord one) {
        Label name = new Label(one.name(), Look.avatar(one.name(), one.key().name()));
        name.setGraphicTextGap(6);
        name.getStyleClass().add("em-account");
        if (one.key().equals(picked)) name.getStyleClass().add("em-picked");
        name.setTooltip(new Tooltip((one.note().isEmpty() ? "" : one.note() + "\n")
                + "Click: show it beside the grid. Right-click: tick or untick it on every instance shown"));
        name.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) pick(one.key());
        });
        menu(name, () -> rowMenu(window.snapshot().config(), columns, one));
        headers.put(one.key(), name);
        return name;
    }

    /** A cell's glyph, a button so the keyboard reaches it: a press ticks or unticks it and picks its account. */
    private Node cell(EmulatorConfig config, EmulatorsModel.InstanceRow column, AccountRecord one) {
        AccountsGridModel.Cell cell = AccountsGridModel.cell(config, column, one);
        AccountsGridModel.Glyph glyph = AccountsGridModel.glyph(cell);
        Button box = new Button(glyph.mark());
        box.getStyleClass().addAll("em-glyph", AccountsGridModel.styleClass(cell));
        String words = one.name() + " on " + column.instance().name() + ": " + glyph.words() + ".";
        if (AccountsGridModel.inherited(config, column.key(), one)) {
            words += "\n" + column.key().platform().displayName() + "'s configuration lists it;"
                    + " unticking removes it on this instance only.";
        }
        Optional<HostBook.Shared> twice = AccountsGridModel.shared(shared, one, column.key());
        if (twice.isPresent()) {
            box.getStyleClass().add("em-shared");
            words += "\n" + AccountsGridModel.sharedWords(twice.get()) + ".";
        }
        box.setTooltip(new Tooltip(words));
        box.setAccessibleText(words);
        AccountsGridModel.Ticked at = new AccountsGridModel.Ticked(column.key(), one.key());
        box.setOnAction(e -> {
            pick(one.key());
            tick(column, one, !cell.ticked());
        });
        cells.put(at, box);
        GridPane.setHalignment(box, HPos.CENTER);
        return box;
    }

    /** The glyphs in words, over the grid. */
    private static Node legend() {
        FlowPane legend = new FlowPane(14, 4);
        legend.getStyleClass().add("em-legend");
        for (AccountsGridModel.Cell cell : LEGEND) {
            AccountsGridModel.Glyph glyph = AccountsGridModel.glyph(cell);
            Label mark = new Label(glyph.mark());
            mark.getStyleClass().addAll("em-glyph", AccountsGridModel.styleClass(cell));
            Label words = Styles.on(new Label(glyph.words(), mark), StyleClasses.MUTED_TEXT, StyleClasses.SMALL_TEXT);
            words.setGraphicTextGap(5);
            legend.getChildren().add(words);
        }
        return legend;
    }

    /** The inspector: the picked account, or a hint, then what Apply would run over the whole grid. */
    private void inspect(EmulatorConfig config, List<EmulatorsModel.InstanceRow> columns, List<AccountRecord> rows,
                         AccountsGridModel.Pending pending) {
        List<Node> out = new ArrayList<>();
        Optional<AccountRecord> one = rows.stream().filter(row -> row.key().equals(picked)).findFirst();
        if (one.isPresent()) {
            AccountRecord shown = one.get();
            out.add(heading(shown.name(), Look.avatar(shown.name(), shown.key().name())));
            out.add(muted(password(shown)));
            out.add(shown.note().isEmpty() ? muted("No note") : line(shown.note()));
            if (!shown.aliases().isEmpty()) out.add(muted("Also " + String.join(", ", shown.aliases())));
            AccountsGridModel.shared(shared, shown, null).ifPresent(twice -> out.add(
                    Styles.on(line(AccountsGridModel.sharedWords(twice) + "."), "em-warning-text")));
            out.add(Look.cap("Ticked on"));
            List<AccountsGridModel.On> on = AccountsGridModel.tickedOn(config, columns, shown);
            if (on.isEmpty()) {
                out.add(muted("No instance shown"));
            } else {
                FlowPane chips = new FlowPane(4, 4);
                for (AccountsGridModel.On instance : on) {
                    chips.getChildren().add(Look.chip(Look.badge(instance.column().key().platform()),
                            instance.column().instance().name() + instance.mark()));
                }
                out.add(chips);
            }
        } else {
            out.add(muted(rows.isEmpty() ? "No account shown." : "Click an account's name or one of its cells to"
                    + " see it here."));
        }
        out.add(Look.cap("Pending"));
        out.add(line(pending.said()));
        inspector.getChildren().setAll(out);
    }

    /** What this PC keeps of {@code account}'s password, in words. */
    private String password(AccountRecord account) {
        if (!account.kind().signedInByBotMaker()) return "No password: a record only";
        if (!book.storeAvailable()) return "No credential store on this PC: its password is asked at each Apply";
        return PasswordStore.rememberedAny(account.addresses(), book.remembered()) ? "Password remembered on this PC"
                : "Password asked at the first Apply";
    }

    /** Writes the tick; the layer before it is kept for <i>Undo ticks</i> only once the write landed. */
    private void tick(EmulatorsModel.InstanceRow column, AccountRecord one, boolean on) {
        InstanceKey key = column.key();
        Optional<Set<String>> held = column.android().flatMap(AndroidState::googleAccounts);
        AtomicReference<Layer> was = new AtomicReference<>();
        AtomicReference<AccountRecord.Key> named = new AtomicReference<>();
        window.change(config -> {
            was.set(config.instance(key));
            named.set(AccountsGridModel.named(config, key, one, held));
            return AccountsGridModel.tick(config, key, one, on, held);
        }, () -> {
            before.putIfAbsent(new AccountsGridModel.Ticked(key, named.get()), was.get());
            undo.setDisable(false);
        });
    }

    private ContextMenu rowMenu(EmulatorConfig config, List<EmulatorsModel.InstanceRow> columns, AccountRecord one) {
        MenuItem on = new MenuItem("Tick on every instance shown");
        on.setOnAction(e -> tickAll(AccountsGridModel.row(config, columns, one, true),
                one.name() + " is ticked on every instance shown already"));
        MenuItem off = new MenuItem("Untick on every instance shown");
        off.setOnAction(e -> tickAll(AccountsGridModel.row(config, columns, one, false),
                one.name() + " is ticked on no instance shown"));
        return new ContextMenu(on, off);
    }

    private ContextMenu columnMenu(EmulatorConfig config, EmulatorsModel.InstanceRow column,
                                   List<AccountRecord> rows) {
        String name = column.instance().name();
        MenuItem on = new MenuItem("Tick every account shown");
        on.setOnAction(e -> tickAll(AccountsGridModel.column(config, column, rows, true),
                "every account shown is ticked on " + name + " already"));
        MenuItem off = new MenuItem("Untick every account shown");
        off.setOnAction(e -> tickAll(AccountsGridModel.column(config, column, rows, false),
                "no account shown is ticked on " + name));
        List<ApplyPlan.Item> items = AccountsGridModel.toApply(List.of(column), rows);
        MenuItem applyOne = new MenuItem("Apply " + name + "…");
        applyOne.setDisable(items.isEmpty());
        applyOne.setOnAction(e -> window.applyItems(items));
        return new ContextMenu(on, off, new SeparatorMenuItem(), applyOne);
    }

    /**
     * Right-click on {@code node} shows the menu {@code menu} builds then, from the configuration as it is: a label or a
     * box, which has no context menu of its own, and no menu built for a draw nobody right-clicks.
     */
    private static void menu(Node node, Supplier<ContextMenu> menu) {
        node.setOnContextMenuRequested(e -> {
            menu.get().show(node, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    /** Ctrl+F: the emulator filter has the focus, the tab's first filter. */
    void focusFilter() {
        emulator.requestFocus();
    }

    /**
     * Writes several ticks as one change of this PC's configuration, each cell's layer before its first tick kept for
     * <i>Undo ticks</i> once the write landed; {@code nothing} is said when there is nothing to tick.
     */
    private void tickAll(List<AccountsGridModel.Change> ticks, String nothing) {
        if (ticks.isEmpty()) {
            window.say("Nothing to change: " + nothing + ".");
            return;
        }
        Map<AccountsGridModel.Ticked, Layer> was = new LinkedHashMap<>();
        window.change(config -> {
            for (AccountsGridModel.Change one : ticks) {
                InstanceKey key = one.column().key();
                AccountRecord.Key named = AccountsGridModel.named(config, key, one.account(),
                        one.column().android().flatMap(AndroidState::googleAccounts));
                was.putIfAbsent(new AccountsGridModel.Ticked(key, named), config.instance(key));
            }
            return AccountsGridModel.tick(config, ticks);
        }, () -> {
            was.forEach(before::putIfAbsent);
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
