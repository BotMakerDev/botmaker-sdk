// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.apps;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.ApkLibrary;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Downloaded apps window (spec {@code 2026-11-25-game-window-design.md}, Downloaded apps): every file the
 * library keeps ({@link ApkLibrary}), with its version and processor kind, its size and the devices that have the app,
 * a Delete per row, and three bulk deletes. Deleting a file never uninstalls an app; a file an install is reading
 * is refused, with the library's sentence.
 */
final class DownloadedAppsWindow {

    private final StudioServices services;
    private final List<AndroidDevices.Device> devices;
    private final Runnable onChanged;
    private final GridPane grid = new GridPane();
    private final Label total = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final Label status = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final Button nowhere = new Button("Delete the ones installed nowhere");
    private final Button older = new Button("Keep only the newest");
    private final Button all = new Button("Delete all");
    private Stage stage;
    private List<DownloadedApps.Row> rows = List.of();

    private DownloadedAppsWindow(StudioServices services, List<AndroidDevices.Device> devices, Runnable onChanged) {
        this.services = services;
        this.devices = List.copyOf(devices);
        this.onChanged = onChanged;
        grid.setHgap(14);
        grid.setVgap(6);
        nowhere.setOnAction(e -> delete(DownloadedApps.installedNowhere(rows), null));
        older.setOnAction(e -> delete(DownloadedApps.allButNewest(rows), null));
        all.setOnAction(e -> delete(rows.stream().map(DownloadedApps.Row::entry).toList(),
                "Delete every downloaded app? The apps stay installed; a later install downloads them again."));
    }

    /** Opens the window over {@code owner}; {@code onChanged} runs on the JavaFX thread after each delete. */
    static void show(StudioServices services, Window owner, List<AndroidDevices.Device> devices, Runnable onChanged) {
        new DownloadedAppsWindow(services, devices, onChanged).open(owner);
    }

    private void open(Window owner) {
        Label hint = Styles.on(new Label("The files BotMaker downloaded or was given, one per version and processor "
                + "kind. Deleting one frees its space; the app stays installed where it is."), Styles.DIALOG_HINT);
        hint.setWrapText(true);
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        status.setWrapText(true);
        HBox bulk = new HBox(8, nowhere, older, all);
        bulk.setAlignment(Pos.CENTER_LEFT);
        Button close = new Button("Close");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, total, status, spacer, close);
        bottom.setAlignment(Pos.CENTER_LEFT);
        VBox root = new VBox(10, Styles.on(new Label("Downloaded apps"), Styles.DIALOG_HEADING), hint, scroll, bulk,
                bottom);
        root.setPadding(new Insets(16));
        stage = Modals.window(services, owner, Modals.Frame.modeless("Downloaded apps", 720, 460, 520, 320), root);
        reload();
        stage.show();
    }

    /** Reads the library off the JavaFX thread and shows it. */
    private void reload() {
        Map<String, List<String>> on = new HashMap<>();
        for (AndroidDevices.Device device : devices) {
            for (AndroidDevices.App app : device.apps()) {
                on.computeIfAbsent(app.packageName(), k -> new ArrayList<>()).add(device.instance().caption());
            }
        }
        Async.load("apk-library", () -> DownloadedApps.rows(ApkLibrary.shared(), on), this::showRows,
                why -> say(false, why));
    }

    private void showRows(List<DownloadedApps.Row> read) {
        rows = read;
        grid.getChildren().clear();
        String[] heads = {"App", "Version · processor", "Size", "Installed on", ""};
        for (int c = 0; c < heads.length; c++) grid.add(Styles.on(new Label(heads[c]), Styles.SMALL_TEXT), c, 0);
        int r = 1;
        long bytes = 0;
        for (DownloadedApps.Row row : read) {
            ApkLibrary.Entry entry = row.entry();
            bytes += entry.size();
            Button delete = new Button("Delete");
            delete.setOnAction(e -> delete(List.of(entry), null));
            grid.add(new Label(entry.packageName()), 0, r);
            grid.add(new Label(row.version()), 1, r);
            grid.add(new Label(DownloadedApps.size(entry.size())), 2, r);
            grid.add(Styles.on(new Label(row.on().isEmpty() ? "nowhere" : String.join(", ", row.on())),
                    Styles.SMALL_TEXT, Styles.MUTED_TEXT), 3, r);
            grid.add(delete, 4, r);
            r++;
        }
        if (read.isEmpty()) grid.add(Styles.on(new Label("No app was downloaded yet."), Styles.DIALOG_HINT), 0, 1, 5, 1);
        total.setText(read.size() + " files · " + DownloadedApps.size(bytes));
        nowhere.setDisable(DownloadedApps.installedNowhere(read).isEmpty());
        older.setDisable(DownloadedApps.allButNewest(read).isEmpty());
        all.setDisable(read.isEmpty());
    }

    /** Deletes {@code entries} off the JavaFX thread, after {@code confirm} when one is given; one refusal doesn't stop the rest. */
    private void delete(List<ApkLibrary.Entry> entries, String confirm) {
        if (entries.isEmpty()) return;
        if (confirm != null && !confirmed(confirm)) return;
        Async.load("apk-library-delete", () -> {
            List<String> refused = new ArrayList<>();
            for (ApkLibrary.Entry entry : entries) {
                try {
                    ApkLibrary.shared().delete(entry);
                } catch (IOException e) {
                    refused.add(e.getMessage());
                }
            }
            return refused;
        }, refused -> {
            say(refused.isEmpty(), refused.isEmpty() ? "Deleted " + entries.size() + (entries.size() == 1 ? " file."
                    : " files.") : String.join(" ", refused));
            reload();
            onChanged.run();
        });
    }

    private boolean confirmed(String text) {
        ButtonType yes = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        Alert alert = services.theme().alert(Alert.AlertType.CONFIRMATION, text, ButtonType.CANCEL, yes);
        alert.initOwner(stage);
        alert.setTitle("Delete downloaded apps");
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == yes;
    }

    private void say(boolean ok, String message) {
        status.setText(message);
        Styles.pick(status, ok ? Styles.MUTED_TEXT : Styles.ERROR_TEXT, Styles.MUTED_TEXT, Styles.ERROR_TEXT);
    }
}
