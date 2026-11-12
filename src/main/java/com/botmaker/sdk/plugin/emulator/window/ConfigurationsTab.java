package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.InstanceKey;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The Configurations tab: every layer on one grid, rows the fields and columns each emulator's configuration and
 * each instance's own values. A cell edits its field; a column's menu opens its view or copies it to another.
 */
final class ConfigurationsTab {

    private static final String ANY_TAG = "Any tag";

    private final EmulatorsWindow window;
    private final TextField text = new TextField();
    private final ComboBox<Optional<ConfigViews.GridSection>> section = new ComboBox<>();
    private final ComboBox<Optional<PlatformId>> emulator = new ComboBox<>();
    private final ComboBox<String> tag = new ComboBox<>();
    private final CheckBox overridden = new CheckBox("Overridden only");
    private final TableView<ConfigViews.GridRow> table = new TableView<>();
    private final VBox root;

    private ConfigViews.Grid grid = new ConfigViews.Grid(List.of(), List.of());
    private boolean filling;

    ConfigurationsTab(EmulatorsWindow window) {
        this.window = window;
        text.setPromptText("Field or value");
        text.setPrefColumnCount(12);
        List<Optional<ConfigViews.GridSection>> sections = new ArrayList<>(List.of(Optional.empty()));
        for (ConfigViews.GridSection one : ConfigViews.GridSection.values()) {
            if (one != ConfigViews.GridSection.UNKNOWN) sections.add(Optional.of(one));
        }
        section.getItems().setAll(sections);
        section.setValue(Optional.empty());
        section.setCellFactory(list -> named(o -> o.map(ConfigViews.GridSection::displayName).orElse("Any section")));
        section.setButtonCell(named(o -> o.map(ConfigViews.GridSection::displayName).orElse("Any section")));
        emulator.getItems().setAll(Optional.<PlatformId>empty());
        emulator.setValue(Optional.empty());
        emulator.setCellFactory(list -> named(o -> o.map(PlatformId::displayName).orElse("Any emulator")));
        emulator.setButtonCell(named(o -> o.map(PlatformId::displayName).orElse("Any emulator")));
        tag.getItems().setAll(ANY_TAG);
        tag.setValue(ANY_TAG);

        text.textProperty().addListener((o, was, now) -> draw());
        section.valueProperty().addListener((o, was, now) -> draw());
        emulator.valueProperty().addListener((o, was, now) -> draw());
        tag.valueProperty().addListener((o, was, now) -> draw());
        overridden.selectedProperty().addListener((o, was, now) -> draw());

        Button compare = new Button("Compare two instances…");
        compare.setOnAction(e -> compare());
        Button copy = new Button("Copy column to…");
        copy.setOnAction(e -> copy(pickedColumn()));
        FlowPane bar = new FlowPane(8, 6, text, section, emulator, tag, overridden, compare, copy);
        bar.setAlignment(Pos.CENTER_LEFT);
        for (Node node : bar.getChildren()) {
            if (node instanceof Region region) region.setMinWidth(Region.USE_PREF_SIZE);
        }

        table.getSelectionModel().setCellSelectionEnabled(true);
        table.setPlaceholder(new Label("No field matches. Clear the filters."));
        table.setRowFactory(t -> {
            TableRow<ConfigViews.GridRow> one = new TableRow<>();
            one.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY && !one.isEmpty()) edit();
            });
            return one;
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        Label note = Styles.on(new Label("Emulator columns are what their instances start from; strong is an"
                + " instance's own value. Double-click a cell to change it; a column header's menu opens its view or"
                + " copies it to another instance or emulator."), StyleClasses.MUTED_TEXT);
        note.setWrapText(true);

        root = new VBox(8, bar, table, note);
        root.setPadding(new Insets(10));
    }

    Node node() {
        return root;
    }

    void show(EmulatorsModel.Snapshot snapshot) {
        filling = true;
        Optional<PlatformId> wasEmulator = emulator.getValue();
        List<Optional<PlatformId>> emulators = new ArrayList<>(List.of(Optional.empty()));
        ConfigViews.columns(snapshot).stream().filter(ConfigViews.GridColumn::emulator)
                .forEach(column -> emulators.add(Optional.of(column.target().platform())));
        emulator.getItems().setAll(emulators);
        emulator.setValue(emulators.contains(wasEmulator) ? wasEmulator : Optional.empty());
        String wasTag = tag.getValue();
        List<String> tags = new ArrayList<>(List.of(ANY_TAG));
        tags.addAll(snapshot.tags());
        tag.getItems().setAll(tags);
        tag.setValue(tags.contains(wasTag) ? wasTag : ANY_TAG);
        filling = false;
        draw();
    }

    private ConfigViews.GridFilter filter() {
        return new ConfigViews.GridFilter(text.getText(), section.getValue() == null ? null
                : section.getValue().orElse(null), emulator.getValue() == null ? null : emulator.getValue().orElse(null),
                tag.getValue() == null || ANY_TAG.equals(tag.getValue()) ? "" : tag.getValue(), overridden.isSelected());
    }

    /** Draws the grid again, keeping each column's width and the cell picked by the field and column they name. */
    private void draw() {
        if (filling) return;
        Map<String, Double> widths = new HashMap<>();
        table.getColumns().forEach(column -> widths.put(column.getText(), column.getWidth()));
        ConfigViews.GridRow pickedRow = table.getSelectionModel().getSelectedItem();
        String pickedColumn = pickedColumn().map(ConfigViews.GridColumn::title).orElse(null);

        grid = ConfigViews.grid(window.snapshot(), filter());
        table.getColumns().clear();
        TableColumn<ConfigViews.GridRow, ConfigViews.Cell> field = new TableColumn<>("Field");
        field.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(new ConfigViews.Cell(
                cell.getValue().section().displayName() + " · " + cell.getValue().name(), false)));
        field.setCellFactory(column -> cell(false));
        field.setPrefWidth(widths.getOrDefault(field.getText(), 200.0));
        field.setSortable(false);
        table.getColumns().add(field);
        for (int i = 0; i < grid.columns().size(); i++) {
            ConfigViews.GridColumn one = grid.columns().get(i);
            int at = i;
            TableColumn<ConfigViews.GridRow, ConfigViews.Cell> column = new TableColumn<>(one.title());
            column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().cells().get(at)));
            column.setCellFactory(c -> cell(one.emulator()));
            column.setPrefWidth(widths.getOrDefault(one.title(), 140.0));
            column.setSortable(false);
            column.setContextMenu(columnMenu(one));
            table.getColumns().add(column);
        }
        table.getItems().setAll(grid.rows());
        if (pickedRow == null || pickedColumn == null) return;
        int row = -1;
        for (int i = 0; i < grid.rows().size(); i++) {
            ConfigViews.GridRow one = grid.rows().get(i);
            if (one.section() == pickedRow.section() && one.name().equals(pickedRow.name())) row = i;
        }
        int at = row;
        table.getColumns().stream().filter(column -> column.getText().equals(pickedColumn)).findFirst()
                .filter(column -> at >= 0).ifPresent(column -> table.getSelectionModel().select(at, column));
    }

    private static TableCell<ConfigViews.GridRow, ConfigViews.Cell> cell(boolean emulatorColumn) {
        return new TableCell<>() {
            @Override
            protected void updateItem(ConfigViews.Cell item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(StyleClasses.STRONG_TEXT, StyleClasses.MUTED_TEXT);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.text());
                if (item.own() && !emulatorColumn) getStyleClass().add(StyleClasses.STRONG_TEXT);
                else if (item.placeholder()) getStyleClass().add(StyleClasses.MUTED_TEXT);
            }
        };
    }

    private ContextMenu columnMenu(ConfigViews.GridColumn column) {
        MenuItem open = new MenuItem("Open its configuration");
        open.setOnAction(e -> window.configure(column.target()));
        MenuItem copy = new MenuItem("Copy this column to…");
        copy.setOnAction(e -> copy(Optional.of(column)));
        return new ContextMenu(open, copy);
    }

    /** The column of the cell picked, when it is a layer's. */
    private Optional<ConfigViews.GridColumn> pickedColumn() {
        List<TablePosition> cells = table.getSelectionModel().getSelectedCells();
        if (cells.isEmpty()) return Optional.empty();
        int index = table.getColumns().indexOf(cells.getFirst().getTableColumn()) - 1;
        return index < 0 || index >= grid.columns().size() ? Optional.empty() : Optional.of(grid.columns().get(index));
    }

    /** A field's cell asks its value; an account, game or tag cell opens its column's view. */
    private void edit() {
        Optional<ConfigViews.GridColumn> picked = pickedColumn();
        ConfigViews.GridRow row = table.getSelectionModel().getSelectedItem();
        if (picked.isEmpty() || row == null) return;
        ConfigViews.Target target = picked.get().target();
        ConfigField field = row.field();
        if (field == null) {
            window.configure(target);
            return;
        }
        boolean instance = target instanceof ConfigViews.Target.Instance;
        if (instance && field.emulatorOnly()) {
            window.say(field.displayName() + " is set on the emulator, not on an instance.");
            return;
        }
        ConfigDialogs.editField(window, target, field);
    }

    /** Copies one column's layer over another's, after saying what is replaced. */
    private void copy(Optional<ConfigViews.GridColumn> from) {
        List<ConfigViews.GridColumn> columns = ConfigViews.columns(window.snapshot());
        if (columns.size() < 2) {
            window.say("There is no other column to copy to.");
            return;
        }
        ComboBox<ConfigViews.GridColumn> source = new ComboBox<>();
        ComboBox<ConfigViews.GridColumn> into = new ComboBox<>();
        for (ComboBox<ConfigViews.GridColumn> box : List.of(source, into)) {
            box.getItems().setAll(columns);
            box.setCellFactory(list -> named(ConfigViews.GridColumn::title));
            box.setButtonCell(named(ConfigViews.GridColumn::title));
        }
        source.setValue(from.orElse(columns.getFirst()));
        Label hint = Styles.on(new Label("The second column's settings, accounts and games are replaced by what the"
                + " first should be, what it inherits included (between two instances of one emulator, by the first's"
                + " own values). An instance keeps its tags, an emulator its own product settings."),
                StyleClasses.DIALOG_HINT);
        hint.setWrapText(true);
        hint.setMaxWidth(380);
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Copy"), source);
        form.addRow(1, new Label("To"), into);
        form.add(hint, 0, 2, 2, 1);

        Dialog<ButtonType> dialog = ConfigDialogs.dialog(window, "Copy a column", "Copy one configuration over another");
        ButtonType copy = new ButtonType("Copy", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(copy, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(form);
        Node ok = dialog.getDialogPane().lookupButton(copy);
        Runnable check = () -> ok.setDisable(source.getValue() == null || into.getValue() == null
                || source.getValue().target().equals(into.getValue().target()));
        source.valueProperty().addListener((o, was, now) -> check.run());
        into.valueProperty().addListener((o, was, now) -> check.run());
        check.run();
        Optional<ButtonType> pressed = dialog.showAndWait();
        if (pressed.isEmpty() || pressed.get() != copy || ok.isDisabled()) return;
        ConfigViews.Target a = source.getValue().target();
        ConfigViews.Target b = into.getValue().target();
        window.change(c -> ConfigViews.copy(c, a, b));
    }

    /** Two instances side by side: only where what they should be differs. */
    private void compare() {
        List<InstanceKey> keys = window.snapshot().instances().stream().map(EmulatorsModel.InstanceRow::key).toList();
        if (keys.size() < 2) {
            window.say("Comparing needs two instances.");
            return;
        }
        ComboBox<InstanceKey> first = new ComboBox<>();
        ComboBox<InstanceKey> second = new ComboBox<>();
        for (ComboBox<InstanceKey> box : List.of(first, second)) {
            box.getItems().setAll(keys);
            box.setCellFactory(list -> named(key -> key.reference() + " (" + key.platform().displayName() + ")"));
            box.setButtonCell(named(key -> key.reference() + " (" + key.platform().displayName() + ")"));
        }
        first.setValue(keys.get(0));
        second.setValue(keys.get(1));
        GridPane differences = new GridPane();
        differences.setHgap(14);
        differences.setVgap(6);
        Runnable draw = () -> {
            differences.getChildren().clear();
            if (first.getValue() == null || second.getValue() == null) return;
            List<ConfigViews.Difference> found = ConfigViews.compare(window.snapshot().config(), first.getValue(),
                    second.getValue());
            if (found.isEmpty()) {
                differences.add(Styles.on(new Label("They should be the same."), StyleClasses.MUTED_TEXT), 0, 0, 3, 1);
                return;
            }
            differences.addRow(0, Styles.on(new Label(""), StyleClasses.MUTED_TEXT),
                    Styles.on(new Label(first.getValue().reference()), StyleClasses.STRONG_TEXT),
                    Styles.on(new Label(second.getValue().reference()), StyleClasses.STRONG_TEXT));
            int at = 1;
            for (ConfigViews.Difference one : found) {
                differences.addRow(at++, Styles.on(new Label(one.what()), StyleClasses.MUTED_TEXT),
                        new Label(one.first()), new Label(one.second()));
            }
        };
        first.valueProperty().addListener((o, was, now) -> draw.run());
        second.valueProperty().addListener((o, was, now) -> draw.run());
        draw.run();
        FlowPane pick = new FlowPane(8, 6, first, new Label("and"), second);

        Dialog<ButtonType> dialog = ConfigDialogs.dialog(window, "Compare two instances",
                "Where what two instances should be differs");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(new VBox(12, pick, differences));
        dialog.showAndWait();
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
