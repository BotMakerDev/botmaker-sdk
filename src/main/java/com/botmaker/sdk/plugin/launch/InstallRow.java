package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.ApkDownload;
import com.botmaker.shared.emulator.EmulatorInstances;
import com.botmaker.shared.emulator.PlayStoreSetup;
import com.botmaker.shared.launch.GameInstall;
import com.botmaker.shared.launch.GameReadiness;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.UriLauncher;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The Game dialog's line under the current target: whether it can start ({@link GameReadiness}), and Install and
 * Update through its launcher ({@link GameInstall}). An Android target adds the user's OK to download from APKPure,
 * asked once with apkeep's warning and remembered ({@link ApkDownload#acknowledged()}), and the steps that give its
 * emulator Google Play ({@link PlayStoreSetup}).
 */
final class InstallRow {

    private static final ButtonType ALLOW = new ButtonType("Allow APKPure", ButtonBar.ButtonData.OK_DONE);

    private final StudioServices services;
    private final InstallGame.Report report;
    private final Runnable onDone;
    private final Label state = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final Button install = new Button("Install");
    private final Button update = new Button("Update");
    private final CheckBox apkPure = new CheckBox("Download from APKPure");
    private final Button googlePlay = new Button("Get Google Play…");
    private final HBox row;
    private LaunchSpec spec;
    private boolean busy;

    /** {@code onDone} runs on the JavaFX thread after an install or update, whatever it ended in. */
    InstallRow(StudioServices services, InstallGame.Report report, Runnable onDone) {
        this.services = services;
        this.report = report;
        this.onDone = onDone;
        state.setWrapText(true);
        HBox.setHgrow(state, Priority.ALWAYS);
        install.setOnAction(e -> run("Installing", GameInstall::install));
        update.setOnAction(e -> run("Updating", GameInstall::update));
        apkPure.setTooltip(new Tooltip("When the emulator has no Google Play, BotMaker downloads the game from APKPure "
                + "with apkeep"));
        apkPure.setOnAction(e -> apkPureChanged());
        googlePlay.setOnAction(e -> showGooglePlay());
        row = new HBox(8, state, apkPure, googlePlay, install, update);
        row.setAlignment(Pos.CENTER_LEFT);
    }

    Node node() {
        return row;
    }

    /** Shows {@code target}'s line, or hides it when nothing installs that kind. FX thread. */
    void show(String target) {
        spec = target == null ? null : LaunchSpec.parse(target);
        boolean installs = spec != null && !(GameInstall.of(spec, false) instanceof GameInstall.None);
        row.setVisible(installs);
        row.setManaged(installs);
        if (!installs) return;
        boolean android = spec.kind() == LaunchKind.EMULATOR_APP;
        for (Node own : new Node[]{apkPure, googlePlay}) {
            own.setVisible(android);
            own.setManaged(android);
        }
        apkPure.setSelected(ApkDownload.acknowledged());
        LaunchSpec asked = spec;
        state.setText("Checking " + spec.describe() + "…");
        Async.load("game-install-readiness", () -> GameReadiness.of(asked), readiness -> {
            if (asked != spec) return;
            state.setText(readiness.state() == GameReadiness.State.READY ? "Installed."
                    : readiness.state() == GameReadiness.State.UNKNOWN ? "From " + GameInstall.of(asked,
                    apkPure.isSelected()).source() + "."
                    : readiness.reason());
        }, why -> state.setText(""));
    }

    private void run(String doing, BiFunction<GameInstall, Consumer<String>, GameInstall.Result> work) {
        if (spec == null || busy) return;
        LaunchSpec target = spec;
        GameInstall game = GameInstall.of(target, ApkDownload.acknowledged());
        busy(true);
        report.say(true, doing + " " + target.describe() + " from " + game.source() + "…");
        Async.load("game-install", () -> work.apply(game, line -> Platform.runLater(() -> report.say(true, line))),
                result -> {
                    busy(false);
                    boolean ok = result.outcome() != GameInstall.Outcome.FAILED
                            && result.outcome() != GameInstall.Outcome.NO_STORE;
                    report.say(ok, result.message());
                    onDone.run();
                }, why -> {
                    busy(false);
                    report.say(false, why);
                });
    }

    private void busy(boolean on) {
        busy = on;
        install.setDisable(on);
        update.setDisable(on);
    }

    /** Ticked: apkeep's warning, then the user's answer kept. Unticked: the OK withdrawn. */
    private void apkPureChanged() {
        boolean yes = apkPure.isSelected() && confirmApkPure();
        apkPure.setSelected(yes);
        try {
            ApkDownload.acknowledge(yes);
        } catch (IOException e) {
            apkPure.setSelected(ApkDownload.acknowledged());
            report.say(false, "Couldn't keep your answer: " + e.getMessage());
        }
    }

    private boolean confirmApkPure() {
        Alert alert = services.theme().alert(Alert.AlertType.WARNING, "", ButtonType.CANCEL, ALLOW);
        Window owner = owner();
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Download from APKPure?");
        alert.setHeaderText("APKPure is a third-party copy of Google Play");
        Label text = new Label("It has been found serving modified apps, which can carry malware. apkeep, the "
                + "downloader BotMaker uses, warns about it on its own page. Allow BotMaker to download Android games "
                + "from APKPure on this computer when an emulator has no Google Play? You can untick this later.");
        text.setWrapText(true);
        text.setMaxWidth(460);
        Hyperlink link = new Hyperlink(ApkDownload.DANGERS);
        link.setOnAction(e -> UriLauncher.open(ApkDownload.DANGERS));
        alert.getDialogPane().setContent(new VBox(8, text, link));
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ALLOW;
    }

    /** The steps that give the target's emulator Google Play, each command copyable and each page a link. */
    private void showGooglePlay() {
        if (spec == null) return;
        String reference = spec.emulatorInstance();
        Async.load("google-play-setup", () -> EmulatorInstances.find(reference), instance -> {
            if (instance.isEmpty()) {
                report.say(false, "No emulator or phone named \"" + reference + "\" is on this computer.");
                return;
            }
            PlayStoreSetup setup = PlayStoreSetup.of(instance.get().platformId(), instance.get().name());
            VBox steps = new VBox(8);
            Label summary = new Label(setup.summary());
            summary.setWrapText(true);
            steps.getChildren().add(summary);
            int n = 0;
            for (PlayStoreSetup.Step step : setup.steps()) {
                Label text = new Label(++n + ". " + step.text());
                text.setWrapText(true);
                steps.getChildren().add(text);
                if (step.command() != null) steps.getChildren().add(command(step.command()));
                if (step.link() != null) {
                    Hyperlink link = new Hyperlink(step.link());
                    link.setOnAction(e -> UriLauncher.open(step.link()));
                    steps.getChildren().add(link);
                }
            }
            steps.setPrefWidth(520);
            Alert alert = services.theme().alert(Alert.AlertType.INFORMATION);
            Window owner = owner();
            if (owner != null) alert.initOwner(owner);
            alert.setTitle("Google Play on " + instance.get().name());
            alert.setHeaderText(null);
            alert.getDialogPane().setContent(steps);
            alert.show();
        }, why -> report.say(false, why));
    }

    /** A command line to select or copy. */
    private static Node command(String line) {
        TextField field = new TextField(line);
        field.setEditable(false);
        HBox.setHgrow(field, Priority.ALWAYS);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(line);
            Clipboard.getSystemClipboard().setContent(content);
        });
        return new HBox(6, field, copy);
    }

    private Window owner() {
        return row.getScene() == null ? null : row.getScene().getWindow();
    }
}
