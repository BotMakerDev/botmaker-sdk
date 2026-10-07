package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.EmulatorAppCache;
import com.botmaker.shared.emulator.EmulatorInstall;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorLiveness;
import com.botmaker.shared.emulator.NewInstance;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.PlayStoreSearch;
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
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Getting a game onto an emulator from the game dialog: found by name on Google Play — searched from this
 * computer, so nothing starts until the user installs — or from an APK file, then installed on the instance the
 * user picks, a new one if they like. The instance is started first when it is stopped; once the game is on it,
 * it is what this computer launches ({@code onInstalled}).
 */
final class InstallGame {

    private static final double ICON = 40;

    private InstallGame() {}

    /** The Google Play window for {@code query}: its results, each installable on an instance. */
    static void searchPlay(StudioServices services, Window owner, String query, Consumer<GameCatalog.Item> onInstalled) {
        Label heading = Styles.on(new Label("Google Play"), Styles.DIALOG_HEADING);
        Label hint = Styles.on(new Label("Searched from this computer: no emulator starts until you install. "
                + "Installing opens the game's page in Google Play on the instance; press Install there."),
                Styles.DIALOG_HINT);
        hint.setWrapText(true);
        TextField field = new TextField(query);
        field.setPromptText("A game's name, its package, or its Google Play address");
        HBox.setHgrow(field, Priority.ALWAYS);
        Button search = new Button("Search");
        HBox searchRow = new HBox(8, field, search);

        VBox results = new VBox(6);
        results.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(results);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label status = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
        status.setWrapText(true);
        Button stop = new Button("Stop waiting");
        stop.setVisible(false);
        stop.setManaged(false);
        Button close = new Button("Close");
        close.setCancelButton(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, status, spacer, stop, close);
        bottom.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(status, Priority.SOMETIMES);

        VBox root = new VBox(10, heading, hint, searchRow, scroll, bottom);
        root.setPadding(new Insets(16));
        Stage stage = Modals.window(services, owner, Modals.Frame.modeless("Google Play", 620, 560, 440, 360), root);
        Progress progress = new Progress(status, stop);
        close.setOnAction(e -> stage.close());
        stage.setOnHidden(e -> progress.cancel());

        // Only the latest search fills the list: an earlier one answering last would list another game's results.
        int[] latest = {0};
        Runnable run = () -> {
            String q = field.getText() == null ? "" : field.getText().strip();
            if (q.isEmpty()) return;
            int mine = ++latest[0];
            results.getChildren().setAll(new Label("Searching Google Play for “" + q + "”…"));
            Async.load("play-search", () -> {
                try {
                    return PlayStoreSearch.search(q);
                } catch (IOException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }, apps -> {
                if (mine != latest[0]) return;
                results.getChildren().clear();
                if (apps.isEmpty()) results.getChildren().add(new Label("Google Play lists nothing for “" + q + "”."));
                for (PlayStoreSearch.StoreApp app : apps) {
                    results.getChildren().add(row(app, () -> chooseInstance(services, stage, app.packageName())
                            .ifPresent(instance -> progress.start("play-install", () -> EmulatorInstall.fromStore(
                                    instance, app.packageName(), app.name(), progress::say), instance,
                                    onInstalled))));
                }
            }, why -> {
                if (mine == latest[0]) results.getChildren().setAll(Styles.on(new Label(why), Styles.ERROR_TEXT));
            });
        };
        search.setOnAction(e -> run.run());
        field.setOnAction(e -> run.run());
        stage.show();
        run.run();
    }

    /** One result: its icon, its name over its package, and Install on…. */
    private static HBox row(PlayStoreSearch.StoreApp app, Runnable install) {
        ImageView icon = new ImageView();
        icon.setFitWidth(ICON);
        icon.setFitHeight(ICON);
        icon.setPreserveRatio(true);
        // Loaded in the background by JavaFX itself; a result shows at once and its icon when it arrives.
        if (app.iconUrl() != null) icon.setImage(new Image(app.iconUrl(), 2 * ICON, 2 * ICON, true, true, true));
        Region iconBox = new Region();
        iconBox.setMinSize(ICON, ICON);
        Label name = Styles.on(new Label(app.name()), Styles.STRONG_TEXT);
        Label pkg = Styles.on(new Label(app.packageName()), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
        VBox text = new VBox(2, name, pkg);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button button = new Button("Install on…");
        button.setOnAction(e -> install.run());
        HBox row = new HBox(10, app.iconUrl() == null ? iconBox : icon, text, spacer, button);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * An app file picked on this computer ({@code .apk}, {@code .xapk}, {@code .apks}), installed on the instance
     * the user picks next. {@code report} carries the progress, on the JavaFX thread.
     */
    static void fromFile(StudioServices services, Window owner, Report report, Consumer<GameCatalog.Item> onInstalled) {
        services.dialogs().chooseFile("Choose an Android app", Path.of(System.getProperty("user.home", "")),
                "apk", "xapk", "apks").path().ifPresent(file -> chooseInstance(services, owner, null)
                .ifPresent(instance -> {
                    report.say(true, "Installing " + file.getFileName() + " on " + instance.name() + "…");
                    Async.load("file-install", () -> EmulatorInstall.fromFile(instance, file,
                                    line -> Platform.runLater(() -> report.say(true, line))),
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
        String spec = new LaunchSpec(LaunchKind.EMULATOR_APP, result.packageName() + "@" + instance.name()).spec();
        return new GameCatalog.Item(spec, result.name(),
                EmulatorAppCache.shared().iconPath(instance, result.packageName()));
    }

    /** The status line and Stop button of the Google Play window, and the install it is waiting on. */
    private static final class Progress {
        private final Label status;
        private final Button stop;
        private Thread running;

        Progress(Label status, Button stop) {
            this.status = status;
            this.stop = stop;
            stop.setOnAction(e -> cancel());
        }

        void start(String name, java.util.function.Supplier<EmulatorInstall.Result> work, EmulatorInstance instance,
                   Consumer<GameCatalog.Item> onInstalled) {
            if (running != null && running.isAlive()) {
                show(false, "An install is still running: wait for it, or press Stop waiting.");
                return;
            }
            show(true, "Starting…");
            waiting(true);
            running = Async.load(name, work, result -> {
                waiting(false);
                finished(result, instance, this::show, onInstalled);
            }, why -> {
                waiting(false);
                show(false, why);
            });
        }

        /** A line from the install's own thread. */
        void say(String line) {
            Platform.runLater(() -> show(true, line));
        }

        void cancel() {
            if (running != null) running.interrupt();
        }

        private void show(boolean ok, String message) {
            status.setText(message);
            Styles.pick(status, ok ? Styles.MUTED_TEXT : Styles.ERROR_TEXT, Styles.MUTED_TEXT, Styles.ERROR_TEXT);
        }

        private void waiting(boolean on) {
            stop.setVisible(on);
            stop.setManaged(on);
        }
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
