package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.GoogleAccount;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.Applies;
import com.botmaker.shared.emulator.config.Apply;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * One layer's configuration, drawn over the tab it was opened from: an instance's (each field's wanted value, where it
 * comes from, what the instance has now, and Apply) or an emulator's, which its instances start from. Every change is
 * written at once; Apply is what makes an instance so.
 */
final class ConfigView {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm")
            .withZone(ZoneId.systemDefault());

    private final EmulatorsWindow window;
    private final ConfigViews.Target target;
    private final Label state = new Label();
    private final TextField field = new TextField();
    private final CheckBox onlyDifferences = new CheckBox("Only differences");
    private final Button apply = Styles.on(new Button("Apply"), StyleClasses.PRIMARY_BUTTON);
    private final VBox content = new VBox(16);
    private final VBox side = new VBox(14);
    private final BorderPane root = new BorderPane();

    private EmulatorsModel.Snapshot snapshot = EmulatorsModel.Snapshot.EMPTY;

    ConfigView(EmulatorsWindow window, ConfigViews.Target target, Runnable back) {
        this.window = window;
        this.target = target;
        boolean instance = target instanceof ConfigViews.Target.Instance;

        Hyperlink backLink = new Hyperlink(instance ? "‹ Instances" : "‹ Emulators");
        backLink.setOnAction(e -> back.run());
        Label title = Styles.on(new Label(target.title()), StyleClasses.DIALOG_SUBHEADING);
        title.setMinWidth(Region.USE_PREF_SIZE);
        state.setMinWidth(Region.USE_PREF_SIZE);
        field.setPromptText("Field");
        field.setPrefColumnCount(10);
        field.textProperty().addListener((o, was, now) -> draw());
        onlyDifferences.selectedProperty().addListener((o, was, now) -> draw());
        onlyDifferences.setMinWidth(Region.USE_PREF_SIZE);
        apply.setMinWidth(Region.USE_PREF_SIZE);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox top = new HBox(10, backLink, title, state, gap, field);
        if (instance) top.getChildren().addAll(onlyDifferences, apply);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8, 10, 8, 10));

        content.setPadding(new Insets(10));
        ScrollPane main = new ScrollPane(content);
        main.setFitToWidth(true);
        side.setPadding(new Insets(10));
        ScrollPane panels = new ScrollPane(side);
        panels.setFitToWidth(true);
        panels.setMinWidth(220);
        SplitPane split = new SplitPane(main, panels);
        split.setDividerPositions(0.72);
        root.setTop(top);
        root.setCenter(split);
    }

    Node node() {
        return root;
    }

    ConfigViews.Target target() {
        return target;
    }

    /** Draws a new read; the side panels, which no filter changes, only here. */
    void show(EmulatorsModel.Snapshot read) {
        snapshot = read;
        draw();
        side.getChildren().setAll(panels(snapshot.config(), row()));
    }

    private EmulatorsModel.InstanceRow row() {
        return target instanceof ConfigViews.Target.Instance(var key) ? snapshot.instance(key).orElse(null) : null;
    }

    private void change(UnaryOperator<EmulatorConfig> change) {
        window.change(change);
    }

    private void draw() {
        EmulatorConfig config = snapshot.config();
        EmulatorsModel.InstanceRow row = row();
        ConfigViews.ViewFilter filter = new ConfigViews.ViewFilter(field.getText(), onlyDifferences.isSelected());
        boolean instance = target instanceof ConfigViews.Target.Instance;

        state.getStyleClass().removeAll(StyleClasses.OK_TEXT, StyleClasses.MUTED_TEXT);
        if (instance) {
            state.setText(row == null ? "not found on this PC" : row.running() ? "● running" : "stopped");
            Styles.on(state, row != null && row.running() ? StyleClasses.OK_TEXT : StyleClasses.MUTED_TEXT);
            int changes = row == null ? 0 : row.changes();
            apply.setText(changes == 0 ? "Apply" : "Apply " + EmulatorsModel.count(changes, "change"));
            apply.setDisable(row == null || !row.applicable());
            apply.setOnAction(e -> {
                if (row != null) window.apply(List.of(row));
            });
        } else {
            state.setText("what its instances start from");
            Styles.on(state, StyleClasses.MUTED_TEXT);
        }

        content.getChildren().setAll(settings(config, row, filter), accounts(config, row, filter),
                games(config, row, filter));
    }

    // ---- settings ----

    private Node settings(EmulatorConfig config, EmulatorsModel.InstanceRow row, ConfigViews.ViewFilter filter) {
        boolean instance = target instanceof ConfigViews.Target.Instance;
        GridPane grid = grid();
        List<String> heads = new ArrayList<>(List.of("Field", "Wanted", "From"));
        if (instance) heads.add("On the instance now");
        head(grid, heads);
        int at = 1;
        for (ConfigViews.SettingRow one : ConfigViews.settings(target, config, row)) {
            if (!filter.keeps(one)) continue;
            Label name = new Label(one.field().displayName() + (one.field().needsRestart() ? "  ↻" : ""));
            if (one.field().needsRestart()) name.setTooltip(new Tooltip(
                    "Applied with the instance stopped: Apply restarts a running one."));
            Label wanted = new Label(one.wantedText());
            if (one.own()) Styles.on(wanted, StyleClasses.STRONG_TEXT);
            Label from = Styles.on(new Label(one.fromText(target)), StyleClasses.MUTED_TEXT);
            List<Node> cells = new ArrayList<>(List.of(name, wanted, from));
            if (instance) {
                Label now = new Label(one.nowText() + (one.differs() && one.status() != null
                        ? "  · " + one.status().displayName() : ""));
                if (one.differs()) Styles.on(now, StyleClasses.WARNING_TEXT);
                cells.add(now);
            }
            cells.add(settingActions(config, one));
            grid.addRow(at++, cells.toArray(Node[]::new));
        }
        if (at == 1) grid.add(muted(filter.onlyDifferences() ? "No setting differs." : "No field matches."), 0, 1, 4, 1);
        return section("Settings", grid);
    }

    private Node settingActions(EmulatorConfig config, ConfigViews.SettingRow one) {
        boolean instance = target instanceof ConfigViews.Target.Instance;
        ConfigField f = one.field();
        Button edit = small(one.own() ? "Change…" : instance ? "Set here…" : "Set…");
        edit.setOnAction(e -> ConfigDialogs.editField(window, target, f));
        HBox actions = new HBox(6, edit);
        ConfigViews.dropLabel(target, config, f).ifPresent(drop -> {
            Button inherit = small(drop);
            inherit.setOnAction(e -> change(c -> ConfigViews.clear(target, c, f)));
            actions.getChildren().add(inherit);
        });
        return actions;
    }

    // ---- accounts ----

    private Node accounts(EmulatorConfig config, EmulatorsModel.InstanceRow row, ConfigViews.ViewFilter filter) {
        boolean instance = target instanceof ConfigViews.Target.Instance;
        GridPane grid = grid();
        List<String> heads = new ArrayList<>(List.of("Kind", "Account", "From"));
        if (instance) heads.add("On the instance now");
        head(grid, heads);
        List<ConfigViews.AccountRow> rows = ConfigViews.accounts(target, config, row);
        int at = 1;
        for (ConfigViews.AccountRow one : rows) {
            if (!filter.keeps(one)) continue;
            AccountRecord account = one.account();
            Label kind = new Label(account.kind().displayName() + (one.isDefault() ? " ★" : ""));
            String said = account.name() + (one.isDefault() ? " · default" : "")
                    + (account.note().isEmpty() ? "" : " · note: " + account.note());
            Label name = new Label(said);
            if (one.own() && !one.removal()) Styles.on(name, StyleClasses.STRONG_TEXT);
            String fromText = one.removal() ? "this instance removed it"
                    : one.own() ? (instance ? "this instance" : "this emulator")
                    : target.platform().displayName() + " (emulator)";
            List<Node> cells = new ArrayList<>(List.of(kind, name, Styles.on(new Label(fromText), StyleClasses.MUTED_TEXT)));
            if (instance) {
                Label now = new Label(one.state() == null ? "" : one.state().displayName());
                if (one.differs()) Styles.on(now, StyleClasses.WARNING_TEXT);
                cells.add(now);
            }
            cells.add(accountActions(one));
            grid.addRow(at++, cells.toArray(Node[]::new));
        }
        if (at == 1) grid.add(muted(rows.isEmpty() ? "No account. Google accounts are signed in by Apply; Facebook and"
                + " mail are records only." : "No account differs."), 0, 1, 4, 1);

        HBox adds = new HBox(6);
        for (AccountRecord.Kind kind : List.of(AccountRecord.Kind.GOOGLE, AccountRecord.Kind.FACEBOOK,
                AccountRecord.Kind.MAIL)) {
            Button add = small("+ " + kind.displayName());
            add.setOnAction(e -> {
                String offered = kind == AccountRecord.Kind.GOOGLE ? pcAccount(rows) : "";
                ConfigDialogs.account(window, kind, offered, "", false)
                        .ifPresent(account -> change(c -> ConfigViews.addAccount(target, c, account)));
            });
            adds.getChildren().add(add);
        }
        return section("Accounts", adds, grid);
    }

    /** The PC's own Google account, offered first when this layer does not list it yet. */
    private static String pcAccount(List<ConfigViews.AccountRow> rows) {
        Optional<String> pc = GoogleAccount.signedIn();
        return pc.filter(email -> rows.stream().noneMatch(r -> r.account().name().equalsIgnoreCase(email))).orElse("");
    }

    private Node accountActions(ConfigViews.AccountRow one) {
        AccountRecord account = one.account();
        HBox actions = new HBox(6);
        if (one.removal()) {
            Button keep = small("Keep");
            keep.setOnAction(e -> change(c -> ConfigViews.keepAccount(target, c, account.key())));
            actions.getChildren().add(keep);
            return actions;
        }
        if (one.own()) {
            Button note = small("Note…");
            note.setOnAction(e -> ConfigDialogs.account(window, account.kind(), account.name(), account.note(), true)
                    .ifPresent(edited -> change(c -> ConfigViews.addAccount(target, c, edited))));
            actions.getChildren().add(note);
        }
        if (account.kind() == AccountRecord.Kind.GOOGLE && !one.isDefault()) {
            Button first = small("Make default");
            first.setOnAction(e -> change(c -> ConfigViews.makeDefault(target, c, account.name())));
            actions.getChildren().add(first);
        }
        Button remove = small(one.own() ? "Remove" : "Remove here");
        remove.setOnAction(e -> change(c -> ConfigViews.removeAccount(target, c, account.key())));
        actions.getChildren().add(remove);
        return actions;
    }

    // ---- games ----

    private Node games(EmulatorConfig config, EmulatorsModel.InstanceRow row, ConfigViews.ViewFilter filter) {
        boolean instance = target instanceof ConfigViews.Target.Instance;
        GridPane grid = grid();
        List<String> heads = new ArrayList<>(List.of("Game", "From"));
        if (instance) heads.add("On the instance now");
        head(grid, heads);
        List<ConfigViews.GameRow> rows = ConfigViews.games(target, config, row);
        int at = 1;
        for (ConfigViews.GameRow one : rows) {
            if (!filter.keeps(one)) continue;
            Label name = new Label(one.game().label() + " · " + one.game().packageName());
            if (one.own() && !one.removal()) Styles.on(name, StyleClasses.STRONG_TEXT);
            String fromText = one.removal() ? "this instance removed it"
                    : one.own() ? (instance ? "this instance" : "this emulator")
                    : target.platform().displayName() + " (emulator)";
            List<Node> cells = new ArrayList<>(List.of(name, Styles.on(new Label(fromText), StyleClasses.MUTED_TEXT)));
            if (instance) {
                Label now = new Label(one.state() == null ? "" : one.state().displayName());
                if (one.differs()) Styles.on(now, StyleClasses.WARNING_TEXT);
                cells.add(now);
            }
            String packageName = one.game().packageName();
            Button action = small(one.removal() ? "Keep" : one.own() ? "Remove" : "Remove here");
            action.setOnAction(e -> change(c -> one.removal() ? ConfigViews.keepGame(target, c, packageName)
                    : ConfigViews.removeGame(target, c, packageName)));
            cells.add(action);
            grid.addRow(at++, cells.toArray(Node[]::new));
        }
        if (at == 1) grid.add(muted(rows.isEmpty() ? "No game. Apply installs a game listed here where it is missing."
                : "No game differs."), 0, 1, 3, 1);
        Button add = small("+ Game");
        add.setOnAction(e -> ConfigDialogs.game(window).ifPresent(game -> change(c -> ConfigViews.addGame(target, c, game))));
        return section("Games", new HBox(add), grid);
    }

    // ---- side panels ----

    private List<Node> panels(EmulatorConfig config, EmulatorsModel.InstanceRow row) {
        List<Node> out = new ArrayList<>();
        if (target instanceof ConfigViews.Target.Instance(var key)) {
            out.add(panel("Where values come from",
                    "1. The emulator's configuration (" + key.platform().displayName() + ").",
                    "2. This instance's own, over it.",
                    "Set here makes a value this instance's own; Use emulator's drops it."));
            List<String> bots = row == null ? List.of() : row.usedBy();
            out.add(panel("Used by bots", bots.isEmpty()
                    ? new String[]{"No bot opened on this PC names it in its launch targets."}
                    : bots.toArray(String[]::new)));
            out.add(applies(row));
        } else {
            long instances = snapshot.instances().stream().filter(r -> r.key().platform() == target.platform()).count();
            List<EmulatorsModel.InstanceRow> applicable = snapshot.applicable(target.platform());
            out.add(panel("Where values come from",
                    "Every instance of " + target.platform().displayName() + " starts from this.",
                    "An instance's own value, set in its view, wins over it.",
                    "A field marked by hand is set in the emulator itself."));
            VBox mine = panel("Its instances", EmulatorsModel.count((int) instances, "instance") + ", "
                    + applicable.size() + " to apply.");
            Button applyAll = Styles.on(small(applicable.isEmpty() ? "Apply" : "Apply to " + applicable.size()),
                    StyleClasses.PRIMARY_BUTTON);
            applyAll.setDisable(applicable.isEmpty());
            applyAll.setOnAction(e -> window.apply(applicable));
            mine.getChildren().add(applyAll);
            out.add(mine);
            if (config.emulator(target.platform()).isEmpty()) {
                out.add(panel("Nothing set", "Each instance keeps what it has, but for what is set on it."));
            }
        }
        return out;
    }

    /** The instance's applies, the newest first, an undone one said so, with Undo while one is left to undo. */
    private Node applies(EmulatorsModel.InstanceRow row) {
        VBox panel = panel("Applies");
        if (!(target instanceof ConfigViews.Target.Instance(var key))) return panel;
        List<Applies.Entry> entries;
        Set<String> undone;
        try {
            Applies log = EmulatorsWindow.applies();
            entries = log.all(key);
            undone = log.undoneIds();
        } catch (IOException unread) {
            panel.getChildren().add(muted("The applies could not be read: " + unread.getMessage()));
            return panel;
        }
        if (entries.isEmpty()) panel.getChildren().add(muted("Nothing applied yet."));
        List<Applies.Entry> newest = entries.reversed().stream().limit(6).toList();
        for (Applies.Entry entry : newest) {
            List<String> said = new ArrayList<>();
            for (Apply.Change change : entry.changes()) {
                said.add(change.field().displayName() + " " + (change.before() == null ? "—" : change.before().said())
                        + " → " + (change.after() == null ? "—" : change.after().said()));
            }
            boolean wasUndone = undone.contains(entry.id());
            Label line = new Label(WHEN.format(entry.at()) + " · " + String.join(", ", said)
                    + (wasUndone ? " · undone" : ""));
            line.setWrapText(true);
            if (wasUndone) Styles.on(line, StyleClasses.MUTED_TEXT);
            panel.getChildren().add(line);
        }
        if (row != null && entries.stream().anyMatch(entry -> !undone.contains(entry.id()))) {
            Button undo = small("Undo the last apply");
            undo.setOnAction(e -> window.undo(row));
            panel.getChildren().add(undo);
        }
        panel.getChildren().add(muted("Apply stops the instance for ↻ fields, then starts it again."));
        return panel;
    }

    // ---- pieces ----

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        return grid;
    }

    private static void head(GridPane grid, List<String> heads) {
        for (int i = 0; i < heads.size(); i++) {
            grid.add(Styles.on(new Label(heads.get(i)), StyleClasses.MUTED_TEXT), i, 0);
        }
    }

    private static VBox section(String title, Node... body) {
        VBox box = new VBox(8, Styles.on(new Label(title), StyleClasses.STRONG_TEXT));
        box.getChildren().addAll(body);
        return box;
    }

    private static VBox panel(String title, String... lines) {
        VBox box = new VBox(4, Styles.on(new Label(title), StyleClasses.STRONG_TEXT));
        for (String line : lines) {
            Label label = new Label(line);
            label.setWrapText(true);
            box.getChildren().add(label);
        }
        return box;
    }

    private static Label muted(String words) {
        Label label = Styles.on(new Label(words), StyleClasses.MUTED_TEXT);
        label.setWrapText(true);
        return label;
    }

    private static Button small(String text) {
        Button button = new Button(text);
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }
}
