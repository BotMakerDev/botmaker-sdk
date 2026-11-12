package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.SavedFilters;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
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
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Instances tab: every instance as a row, with the columns the user chose (the table's own ☰ button), the filters,
 * saved filters, and Start, Stop and Apply for the rows picked. The order and the filtering are the model's
 * ({@link EmulatorsModel.Column#order}, {@link EmulatorsModel.Filter}); this draws them.
 */
final class InstancesTab {

    private static final String ANY_TAG = "Any tag";

    private final EmulatorsWindow window;
    private final TableView<EmulatorsModel.InstanceRow> table = new TableView<>();
    private final ObservableList<EmulatorsModel.InstanceRow> shown = FXCollections.observableArrayList();
    private final TextField text = new TextField();
    private final ComboBox<Optional<PlatformId>> emulator = new ComboBox<>();
    private final ComboBox<EmulatorsModel.Running> state = new ComboBox<>();
    private final ComboBox<String> tag = new ComboBox<>();
    private final TextField account = new TextField();
    private final TextField app = new TextField();
    private final CheckBox differs = new CheckBox("Differs from its configuration");
    private final MenuButton saved = new MenuButton("Saved filters");
    private final Label count = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
    private final Button start = new Button("Start");
    private final Button stop = new Button("Stop");
    private final Button apply = new Button("Apply");
    private final VBox root;

    /** Set while the controls are being filled, so filling them does not filter. */
    private boolean filling;

    InstancesTab(EmulatorsWindow window) {
        this.window = window;

        text.setPromptText("Name");
        account.setPromptText("Account");
        app.setPromptText("Game");
        text.setPrefColumnCount(10);
        account.setPrefColumnCount(10);
        app.setPrefColumnCount(10);
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
        account.textProperty().addListener((o, was, now) -> filter());
        app.textProperty().addListener((o, was, now) -> filter());
        emulator.valueProperty().addListener((o, was, now) -> filter());
        state.valueProperty().addListener((o, was, now) -> filter());
        tag.valueProperty().addListener((o, was, now) -> filter());
        differs.selectedProperty().addListener((o, was, now) -> filter());
        Button clear = new Button("Clear");
        clear.setOnAction(e -> use(EmulatorsModel.Filter.NONE));

        FlowPane filters = new FlowPane(8, 6, text, emulator, state, tag, account, app, differs, clear, saved);
        filters.setAlignment(Pos.CENTER_LEFT);
        for (Node node : filters.getChildren()) {
            if (node instanceof Region region) region.setMinWidth(Region.USE_PREF_SIZE);
        }

        for (EmulatorsModel.Column column : EmulatorsModel.Column.offered()) table.getColumns().add(column(column));
        table.setTableMenuButtonVisible(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label("No instance matches. Clear the filters, or create an instance in an"
                + " emulator's own manager."));
        SortedList<EmulatorsModel.InstanceRow> sorted = new SortedList<>(shown);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setContextMenu(rowMenu());
        table.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<EmulatorsModel.InstanceRow>) change -> buttons());
        VBox.setVgrow(table, Priority.ALWAYS);

        start.setOnAction(e -> window.start(picked()));
        stop.setOnAction(e -> window.stop(picked()));
        apply.setOnAction(e -> window.apply(picked()));
        Styles.on(apply, StyleClasses.PRIMARY_BUTTON);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox actions = new HBox(8, count, gap, start, stop, apply);
        actions.setAlignment(Pos.CENTER_LEFT);
        for (Button button : List.of(start, stop, apply)) button.setMinWidth(Region.USE_PREF_SIZE);

        root = new VBox(8, filters, table, actions);
        root.setPadding(new Insets(10));
        savedMenu();
        buttons();
    }

    Node node() {
        return root;
    }

    /** Draws a new read: the same filter, and the rows picked before picked again. */
    void show(EmulatorsModel.Snapshot snapshot) {
        Set<InstanceKey> picked = picked().stream().map(EmulatorsModel.InstanceRow::key).collect(Collectors.toSet());
        EmulatorsModel.Filter filter = filterNow();
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
        tag.setValue(tags.stream().filter(filter.tag()::equalsIgnoreCase).findFirst().orElse(ANY_TAG));
        filling = false;
        filter();
        table.getSelectionModel().clearSelection();
        for (EmulatorsModel.InstanceRow row : table.getItems()) {
            if (picked.contains(row.key())) table.getSelectionModel().select(row);
        }
        buttons();
    }

    // ---- filters ----

    private EmulatorsModel.Filter filterNow() {
        return new EmulatorsModel.Filter(text.getText(), emulator.getValue() == null ? null
                : emulator.getValue().orElse(null), state.getValue(), account.getText(), app.getText(),
                tag.getValue() == null || ANY_TAG.equals(tag.getValue()) ? "" : tag.getValue(), differs.isSelected());
    }

    private void filter() {
        if (filling) return;
        List<EmulatorsModel.InstanceRow> all = window.snapshot().instances();
        List<EmulatorsModel.InstanceRow> kept = EmulatorsModel.filtered(all, filterNow());
        shown.setAll(kept);
        count.setText(kept.size() == all.size() ? EmulatorsModel.count(all.size(), "instance")
                : kept.size() + " of " + EmulatorsModel.count(all.size(), "instance"));
    }

    private void use(EmulatorsModel.Filter filter) {
        filling = true;
        text.setText(filter.text());
        account.setText(filter.account());
        app.setText(filter.app());
        emulator.setValue(emulator.getItems().contains(Optional.ofNullable(filter.emulator()))
                ? Optional.ofNullable(filter.emulator()) : Optional.empty());
        state.setValue(filter.state());
        tag.setValue(tag.getItems().stream().filter(filter.tag()::equalsIgnoreCase).findFirst().orElse(ANY_TAG));
        differs.setSelected(filter.differs());
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

    private List<EmulatorsModel.InstanceRow> picked() {
        return List.copyOf(table.getSelectionModel().getSelectedItems());
    }

    private void buttons() {
        List<EmulatorsModel.InstanceRow> picked = picked();
        start.setDisable(picked.stream().allMatch(EmulatorsModel.InstanceRow::running));
        stop.setDisable(picked.stream().noneMatch(EmulatorsModel.InstanceRow::running));
        long applicable = picked.stream().filter(EmulatorsModel.InstanceRow::applicable).count();
        apply.setDisable(applicable == 0);
        apply.setText(applicable == 0 ? "Apply" : "Apply to " + applicable);
    }

    private ContextMenu rowMenu() {
        MenuItem startOne = new MenuItem("Start");
        startOne.setOnAction(e -> window.start(picked()));
        MenuItem stopOne = new MenuItem("Stop");
        stopOne.setOnAction(e -> window.stop(picked()));
        MenuItem applyOne = new MenuItem("Apply its configuration");
        applyOne.setOnAction(e -> window.apply(picked()));
        MenuItem undo = new MenuItem("Undo the last apply");
        undo.setOnAction(e -> picked().forEach(window::undo));
        MenuItem tags = new MenuItem("Tags…");
        tags.setOnAction(e -> editTags());
        ContextMenu menu = new ContextMenu(startOne, stopOne, new SeparatorMenuItem(), applyOne, undo,
                new SeparatorMenuItem(), tags);
        menu.setOnShowing(e -> {
            List<EmulatorsModel.InstanceRow> picked = picked();
            startOne.setDisable(picked.stream().allMatch(EmulatorsModel.InstanceRow::running));
            stopOne.setDisable(picked.stream().noneMatch(EmulatorsModel.InstanceRow::running));
            applyOne.setDisable(picked.stream().noneMatch(EmulatorsModel.InstanceRow::applicable));
            undo.setDisable(picked.isEmpty());
            tags.setDisable(picked.isEmpty());
        });
        return menu;
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

    private TableColumn<EmulatorsModel.InstanceRow, EmulatorsModel.InstanceRow> column(EmulatorsModel.Column column) {
        TableColumn<EmulatorsModel.InstanceRow, EmulatorsModel.InstanceRow> out =
                new TableColumn<>(column.displayName());
        out.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        out.setComparator(column.order());
        out.setVisible(column.shownAtFirst());
        out.setPrefWidth(column == EmulatorsModel.Column.DIFFERS || column == EmulatorsModel.Column.ACCOUNTS ? 170 : 120);
        out.setCellFactory(made -> new TableCell<>() {
            @Override
            protected void updateItem(EmulatorsModel.InstanceRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll(StyleClasses.OK_TEXT, StyleClasses.WARNING_TEXT, StyleClasses.MUTED_TEXT);
                if (empty || row == null) {
                    setText(null);
                    return;
                }
                setText(column.text(row));
                if (column == EmulatorsModel.Column.STATE) {
                    getStyleClass().add(row.running() ? StyleClasses.OK_TEXT : StyleClasses.MUTED_TEXT);
                } else if (column == EmulatorsModel.Column.DIFFERS && row.differs()) {
                    getStyleClass().add(StyleClasses.WARNING_TEXT);
                }
            }
        });
        return out;
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
