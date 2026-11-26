// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.apps.EmulatorApps;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorState;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.SavedFilters;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.botmaker.sdk.plugin.emulator.window.Look.heading;
import static com.botmaker.sdk.plugin.emulator.window.Look.line;
import static com.botmaker.sdk.plugin.emulator.window.Look.muted;

/**
 * The Instances tab: every instance as a row folded under its emulator, with the columns the user chose (the table's
 * own ☰ button), the filters (the window's Find is their words), saved filters, and on the right the inspector of
 * the rows picked, which holds Start, Stop and Apply, and for Android ones Get an app and Downloaded apps. The order and the filtering are the model's
 * ({@link EmulatorsModel.Column#order}, {@link EmulatorsModel.Filter}); what the inspector says is
 * {@link InspectorModel}'s; this draws them.
 */
final class InstancesTab {

    private static final String ANY_TAG = "Any tag";
    /** The inspector's width (spec). */
    private static final double INSPECTOR = 280;
    /** How many accounts a row shows before "+N". */
    private static final int AVATARS = 2;

    /**
     * A line of the tree: an emulator's group, or one of its instances. Its {@code toString} is its words, which
     * Studio's {@code ui} tool reads off the model.
     */
    private sealed interface Line {
        String said();
    }

    private record GroupLine(EmulatorsModel.Group group) implements Line {
        @Override
        public String said() {
            return group.said();
        }

        @Override
        public String toString() {
            return said();
        }
    }

    private record RowLine(EmulatorsModel.InstanceRow row) implements Line {
        @Override
        public String said() {
            return row.said();
        }

        @Override
        public String toString() {
            return said();
        }
    }

    private final EmulatorsWindow window;
    private final ManageActions manage;
    private final TreeTableView<Line> table = new TreeTableView<>(new TreeItem<>());
    private final TextField text = new TextField();
    private final ComboBox<Optional<PlatformId>> emulator = new ComboBox<>();
    private final ComboBox<EmulatorsModel.Running> state = new ComboBox<>();
    private final ComboBox<String> tag = new ComboBox<>();
    private final CheckBox differs = new CheckBox("Differs from its configuration");
    private final MenuButton saved = new MenuButton("Saved filters");
    private final Label count = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
    private final ToggleButton toggle = new ToggleButton();
    private final VBox inspector = new VBox(6);
    private final ScrollPane inspectorPane = new ScrollPane(inspector);
    private final VBox root;
    /** The groups folded by the user, kept over each read. */
    private final Set<PlatformId> folded = new HashSet<>();

    /** Set while the controls are being filled, so filling them does not filter. */
    private boolean filling;
    /** The instance the picker's Manage… asked for, until its row is read. */
    private EmulatorInstance focusing;
    /**
     * The words found in an account only, and in a game only: set by a saved filter or the Accounts window, each
     * shown in {@link #parts} as a chip its ✕ removes. Find's words match everywhere instead.
     */
    private String accountPart = "";
    private String appPart = "";
    private final HBox parts = new HBox(6);

    InstancesTab(EmulatorsWindow window) {
        this.window = window;
        this.manage = new ManageActions(window);

        text.setPromptText("Name, account, game or tag");
        emulator.setCellFactory(list -> emulatorCell());
        emulator.setButtonCell(emulatorCell());
        state.getItems().setAll(EmulatorsModel.Running.values());
        state.setValue(EmulatorsModel.Running.ANY);
        state.setCellFactory(list -> named(EmulatorsModel.Running::displayName));
        state.setButtonCell(named(EmulatorsModel.Running::displayName));
        tag.getItems().setAll(ANY_TAG);
        tag.setValue(ANY_TAG);
        emulator.getItems().setAll(Optional.<PlatformId>empty());
        emulator.setValue(Optional.empty());

        text.textProperty().addListener((o, was, now) -> filter());
        emulator.valueProperty().addListener((o, was, now) -> filter());
        state.valueProperty().addListener((o, was, now) -> filter());
        tag.valueProperty().addListener((o, was, now) -> filter());
        differs.selectedProperty().addListener((o, was, now) -> filter());
        Button clear = new Button("Clear");
        clear.setOnAction(e -> use(EmulatorsModel.Filter.NONE));

        toggle.getStyleClass().add("em-icon");
        toggle.setSelected(!Files.exists(hiddenMark()));
        toggle.selectedProperty().addListener((o, was, now) -> {
            inspectorShown(now);
            remember(now);
        });
        parts.setAlignment(Pos.CENTER_LEFT);
        FlowPane filters = new FlowPane(8, 6, emulator, state, tag, differs, parts, clear, saved);
        filters.setAlignment(Pos.CENTER_LEFT);
        for (Node node : filters.getChildren()) {
            if (node instanceof Region region) region.setMinWidth(Region.USE_PREF_SIZE);
        }
        count.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(filters, Priority.ALWAYS);
        Button twoOfEach = new Button("Make two of each");
        twoOfEach.setMinWidth(Region.USE_PREF_SIZE);
        twoOfEach.setTooltip(new Tooltip("Makes the instances each emulator lacks up to two, one Google account each:"
                + " each is new, with its own device IDs, the light profile and its BotMaker ID, and is left stopped."));
        twoOfEach.setOnAction(e -> manage.twoOfEach());
        HBox bar = new HBox(8, filters, count, twoOfEach, toggle);
        bar.setAlignment(Pos.TOP_LEFT);

        for (EmulatorsModel.Column column : EmulatorsModel.Column.offered()) table.getColumns().add(column(column));
        table.setShowRoot(false);
        table.setTableMenuButtonVisible(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label("No instance matches. Clear the filters, or press Make two of each."));
        table.setContextMenu(rowMenu());
        table.setRowFactory(t -> {
            TreeTableRow<Line> one = new TreeTableRow<>() {
                @Override
                protected void updateItem(Line line, boolean empty) {
                    super.updateItem(line, empty);
                    getStyleClass().remove("em-group");
                    if (!empty && line instanceof GroupLine) getStyleClass().add("em-group");
                    setAccessibleText(empty || line == null ? null : line.said());
                }
            };
            one.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY
                        && one.getItem() instanceof RowLine) configure();
            });
            return one;
        });
        table.getSelectionModel().getSelectedItems().addListener((ListChangeListener<TreeItem<Line>>) change -> inspect());
        HBox.setHgrow(table, Priority.ALWAYS);

        inspector.getStyleClass().add("em-inspector");
        inspector.setPadding(new Insets(10));
        inspectorPane.setFitToWidth(true);
        inspectorPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        inspectorPane.setPrefWidth(INSPECTOR);
        inspectorPane.setMinWidth(INSPECTOR);
        inspectorPane.getStyleClass().add("em-inspector-pane");
        HBox body = new HBox(table, inspectorPane);
        VBox.setVgrow(body, Priority.ALWAYS);

        root = new VBox(8, bar, body);
        root.setPadding(new Insets(10));
        savedMenu();
        inspectorShown(toggle.isSelected());
        inspect();
    }

    Node node() {
        return root;
    }

    /** Draws a new read: the same filter, and the rows picked before picked again. */
    void show(EmulatorsModel.Snapshot snapshot) {
        EmulatorsModel.Filter filter = filterNow();
        EmulatorInstance focus = focusing;
        Set<InstanceKey> picked = null;
        if (focus != null) {
            // the picker's Manage… opened the window before its rows were read: its filter and row are set now
            filter = focusFilter(focus);
            picked = new HashSet<>();
            for (EmulatorsModel.InstanceRow row : snapshot.instances()) {
                if (same(row.instance(), focus)) {
                    picked.add(row.key());
                    focusing = null;
                }
            }
        }
        filling = true;
        List<Optional<PlatformId>> emulators = new ArrayList<>();
        emulators.add(Optional.empty());
        snapshot.emulators().stream().filter(row -> row.instances() > 0)
                .forEach(row -> emulators.add(Optional.of(row.platform())));
        emulator.getItems().setAll(emulators);
        emulator.setValue(emulators.contains(Optional.ofNullable(filter.emulator()))
                ? Optional.ofNullable(filter.emulator()) : Optional.empty());
        List<String> tags = new ArrayList<>(List.of(ANY_TAG));
        tags.addAll(snapshot.tags());
        tag.getItems().setAll(tags);
        String wantedTag = filter.tag();
        tag.setValue(tags.stream().filter(wantedTag::equalsIgnoreCase).findFirst().orElse(ANY_TAG));
        if (focus != null) text.setText(filter.text());
        filling = false;
        draw(picked);
    }

    // ---- filters ----

    private EmulatorsModel.Filter filterNow() {
        return new EmulatorsModel.Filter(text.getText(), emulator.getValue() == null ? null
                : emulator.getValue().orElse(null), state.getValue(), accountPart, appPart,
                tag.getValue() == null || ANY_TAG.equals(tag.getValue()) ? "" : tag.getValue(), differs.isSelected());
    }

    private void filter() {
        if (!filling) draw(null);
    }

    /**
     * Fills the tree with the rows the filter keeps, grouped by emulator, and picks {@code picked} again, or the rows
     * picked before when null.
     */
    private void draw(Set<InstanceKey> picked) {
        Set<InstanceKey> again = picked != null ? picked
                : picked().stream().map(EmulatorsModel.InstanceRow::key).collect(Collectors.toSet());
        Set<PlatformId> groupsPicked = pickedGroups().stream().map(EmulatorsModel.Group::platform)
                .collect(Collectors.toSet());
        List<EmulatorsModel.InstanceRow> all = window.snapshot().instances();
        List<EmulatorsModel.InstanceRow> kept = EmulatorsModel.filtered(all, filterNow());
        count.setText(kept.size() == all.size() ? EmulatorsModel.count(all.size(), "instance")
                : kept.size() + " of " + EmulatorsModel.count(all.size(), "instance"));

        filling = true;
        List<TreeItem<Line>> groups = new ArrayList<>();
        for (EmulatorsModel.Group group : EmulatorsModel.byEmulator(kept)) {
            TreeItem<Line> item = new TreeItem<>(new GroupLine(group));
            for (EmulatorsModel.InstanceRow row : group.rows()) item.getChildren().add(new TreeItem<>(new RowLine(row)));
            item.setExpanded(!folded.contains(group.platform()));
            item.expandedProperty().addListener((o, was, now) -> {
                if (now) folded.remove(group.platform());
                else folded.add(group.platform());
            });
            groups.add(item);
        }
        table.getSelectionModel().clearSelection();
        table.getRoot().getChildren().setAll(groups);
        table.sort();
        for (TreeItem<Line> group : groups) {
            if (group.getValue() instanceof GroupLine(EmulatorsModel.Group one)
                    && groupsPicked.contains(one.platform())) {
                table.getSelectionModel().select(group);
            }
            for (TreeItem<Line> item : group.getChildren()) {
                if (item.getValue() instanceof RowLine(EmulatorsModel.InstanceRow row) && again.contains(row.key())) {
                    group.setExpanded(true);
                    table.getSelectionModel().select(item);
                }
            }
        }
        filling = false;
        inspect();
    }

    /** The account and game parts as chips, each removed by its ✕. */
    private void drawParts() {
        parts.getChildren().clear();
        if (!accountPart.isEmpty()) parts.getChildren().add(part("Account: " + accountPart, () -> accountPart = ""));
        if (!appPart.isEmpty()) parts.getChildren().add(part("Game: " + appPart, () -> appPart = ""));
    }

    private Button part(String words, Runnable remove) {
        Button chip = new Button(words + "  ✕");
        chip.getStyleClass().add("em-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setTooltip(new Tooltip("Remove this part of the filter"));
        chip.setOnAction(e -> {
            remove.run();
            drawParts();
            filter();
        });
        return chip;
    }

    private void use(EmulatorsModel.Filter shown) {
        filling = true;
        accountPart = shown.account();
        appPart = shown.app();
        drawParts();
        text.setText(shown.text());
        emulator.setValue(emulator.getItems().contains(Optional.ofNullable(shown.emulator()))
                ? Optional.ofNullable(shown.emulator()) : Optional.empty());
        state.setValue(shown.state());
        tag.setValue(tag.getItems().stream().filter(shown.tag()::equalsIgnoreCase).findFirst().orElse(ANY_TAG));
        differs.setSelected(shown.differs());
        filling = false;
        filter();
    }

    private SavedFilters store() {
        return new SavedFilters(HostConfig.stateDir());
    }

    /** The saved filters of this tab: each used by a click, and removed from the Remove menu. */
    private void savedMenu() {
        List<SavedFilters.SavedFilter> filters;
        try {
            filters = store().load().stream().filter(one -> one.tab() == SavedFilters.Tab.INSTANCES).toList();
        } catch (IOException unread) {
            filters = List.of();
            window.say("The saved filters could not be read: " + unread.getMessage());
        }
        saved.getItems().clear();
        MenuItem save = new MenuItem("Save this filter…");
        save.setOnAction(e -> saveFilter());
        saved.getItems().add(save);
        if (filters.isEmpty()) return;
        saved.getItems().add(new SeparatorMenuItem());
        Menu remove = new Menu("Remove");
        for (SavedFilters.SavedFilter one : filters) {
            MenuItem use = new MenuItem(one.name());
            use.setOnAction(e -> use(EmulatorsModel.Filter.of(one.values())));
            saved.getItems().add(use);
            MenuItem gone = new MenuItem(one.name());
            gone.setOnAction(e -> {
                try {
                    store().remove(SavedFilters.Tab.INSTANCES, one.name());
                } catch (IOException failed) {
                    window.say("The filter was not removed: " + failed.getMessage());
                }
                savedMenu();
            });
            remove.getItems().add(gone);
        }
        saved.getItems().addAll(new SeparatorMenuItem(), remove);
    }

    private void saveFilter() {
        TextInputDialog ask = new TextInputDialog();
        ask.initOwner(window.stage());
        ask.setTitle("Save this filter");
        ask.setHeaderText("Name the filter. A filter of the same name is replaced.");
        window.services().theme().apply(ask);
        Optional<String> name = ask.showAndWait().map(String::strip).filter(typed -> !typed.isEmpty());
        if (name.isEmpty()) return;
        try {
            store().save(filterNow().saved(name.get()));
        } catch (IOException | IllegalArgumentException failed) {
            window.say("The filter was not saved: " + failed.getMessage());
        }
        savedMenu();
    }

    // ---- rows ----

    /** The instances picked; a group's line picked picks none. */
    private List<EmulatorsModel.InstanceRow> picked() {
        List<EmulatorsModel.InstanceRow> rows = new ArrayList<>();
        for (TreeItem<Line> item : table.getSelectionModel().getSelectedItems()) {
            if (item != null && item.getValue() instanceof RowLine(EmulatorsModel.InstanceRow row)) rows.add(row);
        }
        return rows;
    }

    /** The groups whose own line is picked. */
    private List<EmulatorsModel.Group> pickedGroups() {
        List<EmulatorsModel.Group> groups = new ArrayList<>();
        for (TreeItem<Line> item : table.getSelectionModel().getSelectedItems()) {
            if (item != null && item.getValue() instanceof GroupLine(EmulatorsModel.Group group)) groups.add(group);
        }
        return groups;
    }

    private ContextMenu rowMenu() {
        MenuItem startOne = new MenuItem("Start");
        startOne.setOnAction(e -> window.start(InspectorModel.startable(picked())));
        MenuItem stopOne = new MenuItem("Stop");
        stopOne.setOnAction(e -> window.stop(InspectorModel.startable(picked())));
        MenuItem applyOne = new MenuItem("Apply its configuration");
        applyOne.setOnAction(e -> window.apply(picked()));
        MenuItem undo = new MenuItem("Undo the last apply");
        undo.setOnAction(e -> picked().forEach(window::undo));
        MenuItem tags = new MenuItem("Tags…");
        tags.setOnAction(e -> editTags());
        MenuItem configure = new MenuItem("Configure…");
        configure.setOnAction(e -> configure());
        MenuItem cloneOne = new MenuItem("Clone…");
        cloneOne.setOnAction(e -> one().ifPresent(manage::cloneOf));
        MenuItem renameOne = new MenuItem("Rename…");
        renameOne.setOnAction(e -> one().ifPresent(manage::rename));
        MenuItem deleteOne = new MenuItem("Delete…");
        deleteOne.setOnAction(e -> one().ifPresent(manage::delete));
        MenuItem markOne = new MenuItem("Mark as itself");
        markOne.setOnAction(e -> one().ifPresent(manage::mark));
        ContextMenu menu = new ContextMenu(configure, new SeparatorMenuItem(), startOne, stopOne,
                new SeparatorMenuItem(), applyOne, undo, new SeparatorMenuItem(), tags,
                new SeparatorMenuItem(), cloneOne, renameOne, deleteOne, markOne);
        menu.setOnShowing(e -> {
            List<EmulatorsModel.InstanceRow> picked = picked();
            List<EmulatorsModel.InstanceRow> startable = InspectorModel.startable(picked);
            boolean phone = picked.size() == 1 && picked.getFirst().phone();
            configure.setDisable(picked.size() != 1 || phone);
            boolean managed = picked.size() == 1 && ManageModel.manageable(picked.getFirst().key().platform());
            cloneOne.setDisable(!managed || picked.getFirst().running());
            renameOne.setDisable(!managed);
            deleteOne.setDisable(!managed);
            // a clone answering with its original's ID shows a clash, not running: its product's word decides
            markOne.setDisable(picked.size() != 1 || phone
                    || picked.getFirst().instance().state() != EmulatorState.RUNNING);
            startOne.setDisable(startable.stream().allMatch(EmulatorsModel.InstanceRow::running));
            stopOne.setDisable(startable.stream().noneMatch(EmulatorsModel.InstanceRow::running));
            applyOne.setDisable(picked.stream().noneMatch(EmulatorsModel.InstanceRow::applicable));
            undo.setDisable(picked.isEmpty());
            tags.setDisable(picked.isEmpty());
        });
        return menu;
    }

    /** The one row picked, when one is. */
    private Optional<EmulatorsModel.InstanceRow> one() {
        List<EmulatorsModel.InstanceRow> picked = picked();
        return picked.size() == 1 ? Optional.of(picked.getFirst()) : Optional.empty();
    }

    /** The filter's words, which the window's Find shows and types into; the tab does not show them itself. */
    TextField findField() {
        return text;
    }

    /**
     * Shows {@code instance}: the words set to its name and the emulator to its own, the other filters cleared, its
     * row selected. A window not yet read keeps it until its rows are ({@link #show}).
     */
    void focus(EmulatorInstance instance) {
        if (instance == null) return;
        focusing = instance;
        use(focusFilter(instance));
        if (picked().stream().anyMatch(row -> same(row.instance(), instance))) {
            focusing = null;
            return;
        }
        for (TreeItem<Line> group : table.getRoot().getChildren()) {
            for (TreeItem<Line> item : group.getChildren()) {
                if (item.getValue() instanceof RowLine(EmulatorsModel.InstanceRow row) && same(row.instance(), instance)) {
                    focusing = null;
                    group.setExpanded(true);
                    table.getSelectionModel().clearSelection();
                    table.getSelectionModel().select(item);
                    return;
                }
            }
        }
    }

    /** The Accounts window's <i>N instances</i>: the instances that should hold {@code account}, the rest cleared. */
    void showAccount(String account) {
        focusing = null;
        use(new EmulatorsModel.Filter("", null, EmulatorsModel.Running.ANY, account, "", "", false));
    }

    private static EmulatorsModel.Filter focusFilter(EmulatorInstance instance) {
        return new EmulatorsModel.Filter(instance.name() == null ? "" : instance.name(), instance.platformId(),
                EmulatorsModel.Running.ANY, "", "", "", false);
    }

    private static boolean same(EmulatorInstance one, EmulatorInstance other) {
        return one.platformId() == other.platformId() && Objects.equals(one.reference(), other.reference());
    }

    /** Opens the one row picked's configuration. */
    private void configure() {
        List<EmulatorsModel.InstanceRow> picked = picked();
        if (picked.size() == 1) window.configure(new ConfigViews.Target.Instance(picked.getFirst().key()));
    }

    /** The tags of the rows picked, typed with commas between them; every row picked gets what was typed. */
    private void editTags() {
        List<EmulatorsModel.InstanceRow> picked = picked();
        if (picked.isEmpty()) return;
        TextInputDialog ask = new TextInputDialog(String.join(", ", picked.getFirst().tags()));
        ask.initOwner(window.stage());
        ask.setTitle("Tags");
        ask.setHeaderText(picked.size() == 1 ? "Tags of " + picked.getFirst().instance().name() + ", with commas between"
                : "Tags of the " + picked.size() + " instances picked, with commas between");
        window.services().theme().apply(ask);
        Optional<String> typed = ask.showAndWait();
        if (typed.isEmpty()) return;
        List<String> tags = EmulatorsModel.tags(typed.get());
        List<InstanceKey> keys = picked.stream().map(EmulatorsModel.InstanceRow::key).toList();
        window.change(config -> {
            var next = config;
            for (InstanceKey key : keys) next = next.withInstance(key, next.instance(key).withTags(tags));
            return next;
        });
    }

    // ---- the inspector ----

    private static Path hiddenMark() {
        return HostConfig.stateDir().resolve("instances-inspector-hidden");
    }

    /** Shows or hides the inspector. */
    private void inspectorShown(boolean shown) {
        inspectorPane.setVisible(shown);
        inspectorPane.setManaged(shown);
        toggle.setText(shown ? "⟩" : "⟨");
        toggle.setTooltip(new Tooltip(shown ? "Hide the inspector" : "Show the inspector"));
    }

    /** Remembers on this PC whether the inspector shows, as the user turns it. */
    private static void remember(boolean shown) {
        try {
            if (shown) Files.deleteIfExists(hiddenMark());
            else if (!Files.exists(hiddenMark())) {
                Files.createDirectories(hiddenMark().getParent());
                Files.createFile(hiddenMark());
            }
        } catch (IOException unsaved) {
            // the inspector still hides; only the next window forgets it
        }
    }

    /** Draws the inspector for what is picked: one instance, several, a group's line, or nothing. */
    void inspect() {
        if (filling) return;
        inspector.getChildren().clear();
        List<EmulatorsModel.InstanceRow> picked = picked();
        if (picked.size() == 1) {
            one(picked.getFirst());
        } else if (picked.size() > 1) {
            several(Styles.on(new Label(InspectorModel.several(picked).title()), StyleClasses.DIALOG_SUBHEADING),
                    picked);
        } else if (pickedGroups().size() == 1) {
            EmulatorsModel.Group group = pickedGroups().getFirst();
            several(heading(group.platform().displayName(), Look.badge(group.platform())), group.rows());
        } else if (pickedGroups().size() > 1) {
            List<EmulatorsModel.InstanceRow> rows = pickedGroups().stream()
                    .flatMap(group -> group.rows().stream()).toList();
            several(Styles.on(new Label(EmulatorsModel.count(pickedGroups().size(), "emulator")),
                    StyleClasses.DIALOG_SUBHEADING), rows);
        } else {
            for (TreeItem<Line> item : table.getRoot().getChildren()) {
                if (!(item.getValue() instanceof GroupLine(EmulatorsModel.Group group))) continue;
                InspectorModel.Several counted = InspectorModel.several(group.rows());
                inspector.getChildren().addAll(heading(group.platform().displayName(), Look.badge(group.platform())),
                        line(counted.title() + ": " + counted.states()));
            }
            inspector.getChildren().add(muted("Pick an instance to see it, or several to start, stop or apply them"
                    + " together."));
        }
    }

    private void one(EmulatorsModel.InstanceRow row) {
        Label title = heading(row.instance().name(), Look.badge(row.key().platform()));
        HBox pills = new HBox(6, Look.pill(InspectorModel.statePill(row)));
        if (row.differs()) pills.getChildren().add(Look.pill(new Look.Pill(Look.Tone.WARNING, "Differs")));
        inspector.getChildren().addAll(title, muted(EmulatorsModel.Column.EMULATOR.text(row)), pills);

        inspector.getChildren().add(Look.cap("Accounts"));
        if (row.accounts().isEmpty()) {
            inspector.getChildren().add(muted("None listed."));
        } else {
            FlowPane chips = new FlowPane(6, 6);
            for (String name : row.accounts()) chips.getChildren().add(Look.chip(Look.avatar(name, name), name));
            inspector.getChildren().add(chips);
        }

        inspector.getChildren().add(Look.cap("What Apply changes"));
        List<String> changes = InspectorModel.applyLines(row);
        if (row.diff() == null) inspector.getChildren().add(muted("Not read: refresh once it answers."));
        else if (changes.isEmpty()) inspector.getChildren().add(muted("Nothing: it is as configured."));
        else for (String change : changes) inspector.getChildren().add(line(change));

        inspector.getChildren().add(Look.cap("Device"));
        GridPane device = new GridPane();
        device.setHgap(10);
        device.setVgap(2);
        int at = 0;
        for (InspectorModel.Fact fact : InspectorModel.device(row)) {
            device.addRow(at++, Styles.on(new Label(fact.label()), StyleClasses.MUTED_TEXT), line(fact.value()));
        }
        inspector.getChildren().add(device);

        Button apply = Styles.on(new Button("Apply"), StyleClasses.PRIMARY_BUTTON);
        apply.setDisable(!row.applicable());
        apply.setOnAction(e -> window.apply(List.of(row)));
        FlowPane buttons;
        if (row.phone()) {
            // BotMaker neither starts, stops nor configures a phone: its accounts are what it changes
            Button accounts = new Button("Accounts…");
            accounts.setTooltip(new Tooltip("The Accounts tab, on the phones' columns"));
            accounts.setOnAction(e -> window.accountsOf(row.key().platform()));
            buttons = buttons(accounts, apply);
        } else {
            Button startStop = new Button(row.running() ? "Stop" : "Start");
            startStop.setOnAction(e -> {
                if (row.running()) window.stop(List.of(row));
                else window.start(List.of(row));
            });
            Button configure = new Button("Configure…");
            configure.setOnAction(e -> window.configure(new ConfigViews.Target.Instance(row.key())));
            buttons = buttons(startStop, configure, apply);
        }
        window.needsYou(row.key()).ifPresent(waiting -> {
            Button look = new Button("Open its screen");
            look.setMinWidth(Region.USE_PREF_SIZE);
            look.setOnAction(e -> window.openScreen(waiting));
            buttons.getChildren().add(look);
        });
        inspector.getChildren().add(buttons);
        apps(List.of(row));
    }

    /**
     * The Apps section, for the picked instances an app can be installed on: Get an app, with them ticked, and
     * Downloaded apps. Nothing when none of them takes an app.
     */
    private void apps(List<EmulatorsModel.InstanceRow> rows) {
        List<EmulatorInstance> android = rows.stream().filter(row -> EmulatorApps.takesApps(row.key().platform()))
                .map(EmulatorsModel.InstanceRow::instance).toList();
        if (android.isEmpty()) return;
        inspector.getChildren().add(Look.cap("Apps"));
        Label said = muted("");
        said.setVisible(false);
        said.setManaged(false);
        Consumer<String> failed = why -> {
            said.setText("Couldn't read the devices: " + why);
            said.setVisible(true);
            said.setManaged(true);
        };
        Button getApp = new Button(android.size() == 1 ? "Get an app…" : "Get an app on " + android.size() + "…");
        getApp.setTooltip(new Tooltip("Download an app or install an APK file you have"));
        getApp.setOnAction(e -> EmulatorApps.getApp(window.services(), window.stage(), android, window::refresh,
                failed));
        Button downloaded = new Button("Downloaded apps…");
        downloaded.setTooltip(new Tooltip("The app files BotMaker keeps, and deleting them"));
        downloaded.setOnAction(e -> EmulatorApps.downloaded(window.services(), window.stage(), failed));
        inspector.getChildren().addAll(buttons(getApp, downloaded), said);
    }

    /** Several instances, or a group's: their count and states, and Start, Stop and Apply to all of them. */
    private void several(Label title, List<EmulatorsModel.InstanceRow> rows) {
        InspectorModel.Several counted = InspectorModel.several(rows);
        inspector.getChildren().addAll(title, line(counted.title()), muted(counted.states()));
        List<EmulatorsModel.InstanceRow> startable = InspectorModel.startable(rows);
        Button start = new Button("Start");
        start.setDisable(startable.stream().allMatch(EmulatorsModel.InstanceRow::running));
        start.setOnAction(e -> window.start(startable));
        Button stop = new Button("Stop");
        stop.setDisable(startable.stream().noneMatch(EmulatorsModel.InstanceRow::running));
        stop.setOnAction(e -> window.stop(startable));
        Button apply = Styles.on(new Button(counted.applicable() == 0 ? "Apply" : "Apply to " + counted.applicable()),
                StyleClasses.PRIMARY_BUTTON);
        apply.setDisable(counted.applicable() == 0);
        apply.setOnAction(e -> window.apply(rows));
        inspector.getChildren().add(buttons(start, stop, apply));
        apps(rows);
    }

    private static FlowPane buttons(Button... buttons) {
        FlowPane row = new FlowPane(6, 6, buttons);
        row.setPadding(new Insets(8, 0, 0, 0));
        for (Button button : buttons) button.setMinWidth(Region.USE_PREF_SIZE);
        return row;
    }

    // ---- the columns ----

    private TreeTableColumn<Line, Line> column(EmulatorsModel.Column column) {
        TreeTableColumn<Line, Line> out = new TreeTableColumn<>(column.displayName());
        out.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getValue()));
        Comparator<EmulatorsModel.InstanceRow> rows = column.order();
        out.setComparator((a, b) -> a instanceof RowLine(EmulatorsModel.InstanceRow x)
                && b instanceof RowLine(EmulatorsModel.InstanceRow y) ? rows.compare(x, y)
                : String.CASE_INSENSITIVE_ORDER.compare(groupName(a), groupName(b)));
        out.setVisible(column.shownAtFirst());
        out.setPrefWidth(switch (column) {
            case NAME -> 200;
            case ACCOUNTS -> 220;
            case DIFFERS -> 140;
            case STATE -> 100;
            default -> 120;
        });
        out.setCellFactory(made -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Line line, boolean empty) {
                super.updateItem(line, empty);
                setText(null);
                setGraphic(null);
                setTooltip(null);
                if (empty || line == null) return;
                switch (line) {
                    case GroupLine(EmulatorsModel.Group group) -> {
                        if (column != EmulatorsModel.Column.NAME) return;
                        setText(group.platform().displayName() + " · " + group.rows().size() + " · "
                                + group.running() + (group.platform() == PlatformId.PHYSICAL ? " connected"
                                : " running"));
                        setGraphic(Look.badge(group.platform()));
                        setGraphicTextGap(8);
                    }
                    case RowLine(EmulatorsModel.InstanceRow row) -> cell(this, column, row);
                }
            }
        });
        return out;
    }

    private static String groupName(Line line) {
        return switch (line) {
            case GroupLine(EmulatorsModel.Group group) -> group.platform().displayName();
            case RowLine(EmulatorsModel.InstanceRow row) -> row.key().platform().displayName();
        };
    }

    /** What an instance's cell shows in {@code column}: a pill, avatars, or the column's words. */
    private static void cell(TreeTableCell<Line, Line> cell, EmulatorsModel.Column column,
                             EmulatorsModel.InstanceRow row) {
        switch (column) {
            case STATE -> cell.setGraphic(Look.pill(InspectorModel.statePill(row)));
            case DIFFERS -> {
                if (row.differs()) {
                    cell.setGraphic(Look.pill(new Look.Pill(Look.Tone.WARNING, "Differs")));
                    cell.setTooltip(new Tooltip(column.text(row)));
                } else if (row.applicable()) {
                    cell.setGraphic(Styles.on(new Label(column.text(row)), StyleClasses.MUTED_TEXT));
                }
            }
            case ACCOUNTS -> {
                List<String> names = row.accounts();
                if (names.isEmpty()) return;
                HBox shown = new HBox(8);
                shown.setAlignment(Pos.CENTER_LEFT);
                for (String name : names.subList(0, Math.min(AVATARS, names.size()))) {
                    Label one = new Label(name, Look.avatar(name, name));
                    one.setGraphicTextGap(4);
                    one.setMinWidth(0);
                    shown.getChildren().add(one);
                }
                if (names.size() > AVATARS) {
                    shown.getChildren().add(Styles.on(new Label("+" + (names.size() - AVATARS)),
                            StyleClasses.MUTED_TEXT));
                }
                cell.setGraphic(shown);
                cell.setTooltip(new Tooltip(String.join("\n", names)));
            }
            default -> cell.setText(column.text(row));
        }
    }

    private static ListCell<Optional<PlatformId>> emulatorCell() {
        return named(value -> value.map(PlatformId::displayName).orElse("Any emulator"));
    }

    private static <T> ListCell<T> named(Function<T, String> name) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : name.apply(item));
            }
        };
    }
}
