package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.Layer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.function.Function;

/**
 * The Emulators tab: every product BotMaker knows, installed or not, and beside the one picked what its instances
 * start from, with <i>Apply to N</i> for those that differ. The configuration is changed in an instance's view.
 */
final class EmulatorsTab {

    private final EmulatorsWindow window;
    private final TableView<EmulatorsModel.EmulatorRow> table = new TableView<>();
    private final VBox side = new VBox(6);
    private final SplitPane root;

    EmulatorsTab(EmulatorsWindow window) {
        this.window = window;
        table.getColumns().add(column("Emulator", 150, row -> row.platform().displayName()));
        table.getColumns().add(column("On this PC", 260, row -> row.installed()
                ? row.status().substring(row.status().indexOf(':') + 1).strip() : "Not installed"));
        table.getColumns().add(number("Instances", EmulatorsModel.EmulatorRow::instances));
        table.getColumns().add(number("Running", EmulatorsModel.EmulatorRow::running));
        table.getColumns().add(column("Its configuration", 170, EmulatorsModel.EmulatorRow::configured));
        table.getColumns().add(number("Differ", EmulatorsModel.EmulatorRow::differing));
        table.setPlaceholder(new Label("Reading this PC's emulators…"));
        table.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> side(now));

        side.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(side);
        scroll.setFitToWidth(true);
        scroll.setMinWidth(240);
        root = new SplitPane(table, scroll);
        root.setDividerPositions(0.68);
        side(null);
    }

    Node node() {
        return root;
    }

    void show(EmulatorsModel.Snapshot snapshot) {
        EmulatorsModel.EmulatorRow picked = table.getSelectionModel().getSelectedItem();
        PlatformId platform = picked == null ? null : picked.platform();
        table.getItems().setAll(snapshot.emulators());
        table.setPlaceholder(new Label("BotMaker found no emulator it knows on this PC."));
        snapshot.emulators().stream().filter(row -> row.platform() == platform).findFirst()
                .or(() -> snapshot.emulators().stream().filter(EmulatorsModel.EmulatorRow::installed).findFirst())
                .ifPresent(table.getSelectionModel()::select);
        side(table.getSelectionModel().getSelectedItem());
    }

    /** The picked emulator's configuration, and Apply for its instances that differ. */
    private void side(EmulatorsModel.EmulatorRow row) {
        side.getChildren().clear();
        if (row == null) {
            side.getChildren().add(muted("Pick an emulator to see what its instances start from."));
            return;
        }
        side.getChildren().add(Styles.on(new Label(row.platform().displayName()), StyleClasses.DIALOG_SUBHEADING));
        Layer layer = row.layer();
        if (layer.isEmpty()) {
            side.getChildren().add(muted("Nothing is set for this emulator: each instance keeps what it has, but"
                    + " for what is set on the instance itself."));
        }
        layer.fields().forEach((field, value) -> side.getChildren().add(line(field.displayName() + ": "
                + value.said())));
        for (AccountRecord account : layer.accounts()) {
            side.getChildren().add(line(account.kind().displayName() + " account: " + account.name()));
        }
        for (GameRecord game : layer.games()) side.getChildren().add(line("Game: " + game.label()));

        int applicable = window.snapshot().applicable(row.platform()).size();
        Button apply = Styles.on(new Button(applicable == 0 ? "Apply" : "Apply to " + applicable),
                StyleClasses.PRIMARY_BUTTON);
        apply.setMinWidth(Region.USE_PREF_SIZE);
        apply.setDisable(applicable == 0);
        apply.setOnAction(e -> window.apply(window.snapshot().applicable(row.platform())));
        side.getChildren().add(apply);
        side.getChildren().add(muted(applicable == 0
                ? (row.instances() == 0 ? "It has no instance." : "Its instances are as configured, or stopped and"
                        + " not read yet.")
                : EmulatorsModel.count(applicable, "instance") + " of this emulator "
                        + (applicable == 1 ? "differs" : "differ") + " from the configuration."));
    }

    private static Label line(String words) {
        Label label = new Label(words);
        label.setWrapText(true);
        return label;
    }

    private static Label muted(String words) {
        return Styles.on(line(words), StyleClasses.MUTED_TEXT);
    }

    private static TableColumn<EmulatorsModel.EmulatorRow, String> column(
            String title, double width, Function<EmulatorsModel.EmulatorRow, String> text) {
        TableColumn<EmulatorsModel.EmulatorRow, String> out = new TableColumn<>(title);
        out.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        out.setComparator(String.CASE_INSENSITIVE_ORDER);
        out.setPrefWidth(width);
        return out;
    }

    /** A count, sorted as a number. */
    private static TableColumn<EmulatorsModel.EmulatorRow, String> number(
            String title, Function<EmulatorsModel.EmulatorRow, Integer> count) {
        TableColumn<EmulatorsModel.EmulatorRow, String> out = column(title, 80, row -> count.apply(row).toString());
        out.setComparator(Comparator.comparingInt(Integer::parseInt));
        return out;
    }
}
