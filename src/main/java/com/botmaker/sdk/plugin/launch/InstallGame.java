// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.ApkLibrary;
import com.botmaker.shared.emulator.EmulatorAppCache;
import com.botmaker.shared.emulator.EmulatorInstall;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorLiveness;
import com.botmaker.shared.emulator.NewInstance;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Getting a game onto an emulator from the Games tab: an APK file, installed on the instance the user picks, a new
 * one if they like. The instance is started first when it is stopped; once the game is on it, it is what this
 * computer launches ({@code onInstalled}). The Android apps tab's Get an app ({@link GetAppDialog}) downloads one.
 */
final class InstallGame {

    private InstallGame() {}

    /**
     * An app file picked on this computer ({@code .apk}, {@code .xapk}, {@code .apks}), installed on the instance
     * the user picks next. {@code report} carries the progress, on the JavaFX thread.
     */
    static void fromFile(StudioServices services, Window owner, Report report, Consumer<GameCatalog.Item> onInstalled) {
        services.dialogs().chooseFile("Choose an Android app", Path.of(System.getProperty("user.home", "")),
                "apk", "xapk", "apks").path().ifPresent(file -> chooseInstance(services, owner, null)
                .ifPresent(instance -> {
                    report.say(true, "Installing " + file.getFileName() + " on " + instance.name() + "…");
                    // Kept in the APK library first, so an update or another instance finds it there.
                    Async.load("file-install", () -> {
                                Path kept;
                                try {
                                    kept = ApkLibrary.shared().add(file).file();
                                } catch (IOException e) {
                                    return new EmulatorInstall.Result(false, null, null, e.getMessage());
                                }
                                return EmulatorInstall.fromFile(instance, kept,
                                        line -> Platform.runLater(() -> report.say(true, line)));
                            },
                            result -> finished(result, instance, report, onInstalled),
                            why -> report.say(false, "Couldn't install " + file.getFileName() + ": " + why));
                }));
    }

    /** Where a step's sentence goes: the dialog's status line. */
    interface Report {
        void say(boolean ok, String message);
    }

    private static void finished(EmulatorInstall.Result result, EmulatorInstance instance, Report report,
                                 Consumer<GameCatalog.Item> onInstalled) {
        if (!result.ok()) {
            report.say(false, result.message());
            return;
        }
        onInstalled.accept(item(result, instance));
        report.say(true, result.message() + " This computer launches it now.");
    }

    /** The game's card: on {@code instance}, named and pictured as its APK says. */
    private static GameCatalog.Item item(EmulatorInstall.Result result, EmulatorInstance instance) {
        String spec = new LaunchSpec(LaunchKind.EMULATOR_APP, result.packageName() + "@" + instance.reference()).spec();
        return new GameCatalog.Item(spec, result.name(),
                EmulatorAppCache.shared().iconPath(instance, result.packageName()));
    }

    /**
     * Which instance to install on: every emulator instance and phone, each saying whether it runs and whether the
     * app is on it already ({@code packageName}, {@code null} for a file), and a way to create a new one.
     */
    static Optional<EmulatorInstance> chooseInstance(StudioServices services, Window owner, String packageName) {
        Dialog<EmulatorInstance> dialog = new Dialog<>();
        services.theme().apply(dialog);
        dialog.setTitle("Install on…");
        dialog.setHeaderText("Which emulator or phone gets the game?");
        if (owner != null) dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        VBox rows = new VBox(6);
        rows.setPadding(new Insets(4));
        rows.setPrefWidth(460);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(320);
        dialog.getDialogPane().setContent(scroll);
        fillInstances(rows, dialog, packageName);
        dialog.setResultConverter(button -> button == ButtonType.CANCEL ? null : dialog.getResult());
        return dialog.showAndWait();
    }

    /** One line per instance, checked off the JavaFX thread, then the new-instance menu. */
    private static void fillInstances(VBox rows, Dialog<EmulatorInstance> dialog, String packageName) {
        rows.getChildren().setAll(new Label("Looking for emulators and phones…"));
        record Line(EmulatorInstance instance, String state, boolean has) {}
        Async.load("install-targets", () -> {
            List<EmulatorInstance> all = Platforms.discoverAll();
            EmulatorAppCache cache = EmulatorAppCache.shared();
            return all.stream().map(instance -> new Line(instance, EmulatorLiveness.check(instance, all).label(),
                    packageName != null && cache.packages(instance).stream()
                            .anyMatch(app -> app.packageName().equals(packageName)))).toList();
        }, lines -> {
            rows.getChildren().clear();
            if (lines.isEmpty()) rows.getChildren().add(new Label("No emulator or phone was found. Create an instance:"));
            for (Line line : lines) {
                Button pick = new Button(line.instance().caption() + "  ·  " + line.state()
                        + (line.has() ? "  ·  installed" : ""));
                pick.setMaxWidth(Double.MAX_VALUE);
                pick.setAlignment(Pos.CENTER_LEFT);
                pick.setOnAction(e -> {
                    dialog.setResult(line.instance());
                    dialog.close();
                });
                rows.getChildren().add(pick);
            }
            rows.getChildren().add(newInstanceMenu(rows, dialog, packageName));
        }, why -> rows.getChildren().setAll(new Label("Couldn't look for emulators: " + why)));
    }

    /** ＋ New instance: one item per installed product that can make one. */
    private static Region newInstanceMenu(VBox rows, Dialog<EmulatorInstance> dialog, String packageName) {
        MenuButton menu = new MenuButton("＋ New instance");
        Label note = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
        note.setWrapText(true);
        Async.load("new-instance-ways", Platforms::newInstances, ways -> {
            if (ways.isEmpty()) {
                menu.setDisable(true);
                note.setText("No installed emulator can create an instance from here.");
            }
            for (NewInstance way : ways) {
                MenuItem item = new MenuItem(way.label());
                item.setOnAction(e -> {
                    menu.setDisable(true);
                    note.setText(way.opensManager() ? "Opening " + way.platformId().displayName() + "…"
                            : "Creating a " + way.platformId().displayName() + " instance…");
                    Async.load("new-instance", () -> {
                        try {
                            return way.run();
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex.getMessage(), ex);
                        }
                    }, said -> {
                        if (way.opensManager()) {
                            menu.setDisable(false);
                            note.setText(said + " Press Refresh when it's made.");
                        } else {
                            fillInstances(rows, dialog, packageName);
                        }
                    }, why -> {
                        menu.setDisable(false);
                        note.setText(why);
                    });
                });
                menu.getItems().add(item);
            }
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> fillInstances(rows, dialog, packageName));
        HBox buttons = new HBox(8, menu, refresh);
        return new VBox(4, buttons, note);
    }
}
