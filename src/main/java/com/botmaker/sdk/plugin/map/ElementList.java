package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ElementRows;
import com.botmaker.sdk.plugin.assist.ElementRows.Row;
import com.botmaker.sdk.plugin.assist.ElementSaves.As;
import com.botmaker.sdk.plugin.assist.ElementSaves.Pick;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The frozen frame's elements beside it (layout B, the user's call, 2026-10-31): a first line saying what was found,
 * one row per input, text and list, each its kind and words, never a selector. A row picked is outlined on the frame
 * and says how it is found; the rows ticked are saved together, each as a button, a marker or a readout of the screen
 * picked. With no tree, it says so: ✂ Cut is the way there.
 */
final class ElementList {

    private static final double WIDTH = 320;

    private final StudioServices services;
    private final Supplier<ScreenGraph> graph;
    private final BiConsumer<Row, Collection<Row>> outlined;
    private final BiConsumer<String, List<Pick>> save;
    private final Label found = new Label();
    private final ListView<Row> list = new ListView<>();
    private final Label details = new Label();
    private final Button saveButton = new Button("✓ Save ticked…");
    private final VBox root;
    /** The rows ticked, by identity: two rows of copies have the same words. */
    private final Set<Row> ticked = Collections.newSetFromMap(new IdentityHashMap<>());
    /** The screen the frame shows, the save's first choice; null for none of the map. */
    private String screen;
    /** Whether the screen moved while the tree was read. */
    private boolean moved;

    /**
     * @param outlined the row picked (null: none) and the rows ticked, to outline on the frame
     * @param save     the screen typed or picked (null: none) and the rows ticked with what each is saved as
     */
    ElementList(StudioServices services, Supplier<ScreenGraph> graph, BiConsumer<Row, Collection<Row>> outlined,
                BiConsumer<String, List<Pick>> save) {
        this.services = services;
        this.graph = graph;
        this.outlined = outlined;
        this.save = save;
        found.setWrapText(true);
        details.setWrapText(true);
        details.getStyleClass().add("screen-panel-path");
        list.setCellFactory(view -> new RowCell());
        list.getSelectionModel().selectedItemProperty().addListener((o, was, is) -> {
            details.setText(is == null ? "" : ElementRows.details(is));
            outlined.accept(is, ticked);
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        saveButton.setTooltip(new Tooltip(
                "Saves the ticked rows to the bot's Elements, each on the screen as you say"));
        saveButton.setOnAction(e -> ask());
        root = new VBox(found, list, details, saveButton);
        root.getStyleClass().add("screen-element-list");
        root.setPrefWidth(WIDTH);
        root.setMinWidth(WIDTH);
        clear();
    }

    Node node() {
        return root;
    }

    /** While the tree is read. */
    void reading() {
        show(List.of(), null, false);
        found.setText("Reading the app's tree…");
    }

    /** No tree to pick from: {@code why}, and nothing to tick. */
    void none(String why) {
        show(List.of(), null, false);
        found.setText(why);
    }

    /**
     * {@code rows}, read off the frame of {@code screen} (null: none of the map); {@code moved} when the screen moved
     * while the tree was read.
     */
    void show(List<Row> rows, String screen, boolean moved) {
        this.screen = screen;
        ticked.clear();
        list.getItems().setAll(rows);
        list.setVisible(!rows.isEmpty());
        list.setManaged(!rows.isEmpty());
        this.moved = moved;
        named(screen);
        details.setText("");
        changed();
    }

    /** The screen the frame shows, read since: the first line and the save's first choice say it. */
    void named(String screen) {
        this.screen = screen;
        if (list.getItems().isEmpty()) {
            found.setText(ElementRows.NOTHING);
            return;
        }
        ScreenGraph map = graph.get();
        String shown = screen == null || !map.screens().containsKey(screen) ? null : map.shown(screen);
        found.setText(ElementRows.found(shown, list.getItems()) + (moved ? "\n" + ElementRows.MOVED : ""));
    }

    void clear() {
        none("");
    }

    /** {@code row}, clicked on the frame, picked here and shown. */
    void pick(Row row) {
        list.getSelectionModel().select(row);
        list.scrollTo(row);
    }

    private void tick(Row row, boolean on) {
        if (on) ticked.add(row);
        else ticked.remove(row);
        changed();
    }

    private void changed() {
        saveButton.setDisable(ticked.isEmpty());
        saveButton.setText(ticked.size() <= 1 ? "✓ Save ticked…" : "✓ Save " + ticked.size() + "…");
        outlined.accept(list.getSelectionModel().getSelectedItem(), ticked);
    }

    /** Asks the screen and what each ticked row is, then saves them, in the list's order. */
    private void ask() {
        List<Row> rows = list.getItems().stream().filter(ticked::contains).toList();
        if (rows.isEmpty()) return;
        ComboBox<String> on = new ComboBox<>();
        on.setEditable(true);
        on.getItems().setAll(graph.get().screens().keySet().stream().sorted().toList());
        on.setConverter(new StringConverter<>() {
            @Override
            public String toString(String constant) {
                return constant == null ? "" : constant;
            }

            @Override
            public String fromString(String typed) {
                return typed == null || typed.isBlank() ? null : typed.strip();
            }
        });
        on.setPromptText("none: only saved to Elements");
        on.setValue(screen);
        GridPane grid = new GridPane(8, 8);
        List<ComboBox<As>> as = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ComboBox<As> one = new ComboBox<>(FXCollections.observableArrayList(As.offered(rows.get(i))));
            one.setButtonCell(asCell());
            one.setCellFactory(view -> asCell());
            one.setValue(one.getItems().getFirst());
            as.add(one);
            grid.addRow(i, new Label(rows.get(i).said()), one);
        }
        HBox top = new HBox(8, new Label("On the screen"), on);
        top.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label("Each is saved to the bot's Elements with a picture of it, then written on the screen: "
                + "pressed as a button, telling the screen, or read on it.");
        hint.setWrapText(true);
        hint.setMaxWidth(480);
        hint.getStyleClass().add("screen-panel-path");
        Modals.form(services, rows.size() == 1 ? "Save " + rows.getFirst().said() : "Save " + rows.size() + " elements",
                new VBox(10, top, grid, hint), () -> {
                    String typed = on.getEditor().getText();
                    List<Pick> picks = new ArrayList<>();
                    for (int i = 0; i < rows.size(); i++) picks.add(new Pick(rows.get(i), as.get(i).getValue()));
                    save.accept(typed == null || typed.isBlank() ? null : typed.strip(), picks);
                });
    }

    private static ListCell<As> asCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(As item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        };
    }

    /** A row: its tick and how it reads, {@code Button "Battery"}. */
    private final class RowCell extends ListCell<Row> {
        private final CheckBox tick = new CheckBox();

        RowCell() {
            tick.setOnAction(e -> {
                if (getItem() != null) tick(getItem(), tick.isSelected());
            });
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            tick.setSelected(ticked.contains(row));
            tick.setText(row.said());
            setGraphic(tick);
            setText(null);
        }
    }
}
