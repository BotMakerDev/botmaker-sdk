// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.apps;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.ApkDownload;
import com.botmaker.shared.emulator.ApkFile;
import com.botmaker.shared.emulator.ApkLibrary;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorLauncher;
import com.botmaker.shared.emulator.EmulatorLiveness;
import com.botmaker.shared.emulator.GoogleAccount;
import com.botmaker.shared.emulator.PlayStoreSearch;
import com.botmaker.shared.launch.AndroidInstall;
import com.botmaker.shared.launch.GameInstall;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.UriLauncher;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Get an app, opened from the Emulators window's Instances tab ({@link EmulatorApps}): a game's name, which lists Google
 * Play's results to pick from ({@link PlayStoreSearch}), its package name or its Google Play link, then
 * Download it (Google Play's files, APKPure when the user allowed it, the Play app) or an APK file the user has, and
 * the Install on table ({@link InstallOn}). The installs run one device after another; a stopped device is started
 * for its install and stopped after, and one that fails doesn't stop the next. The library keeps one download per
 * processor kind, so a second device of the same kind installs the file already here.
 *
 * <p>The download account line is the one Google account Play's files are downloaded with for every emulator
 * ({@link GoogleAccount}): only the user signs it in, here.
 */
final class GetAppDialog {

    private static final ButtonType ALLOW = new ButtonType("Allow APKPure", ButtonBar.ButtonData.OK_DONE);

    private final StudioServices services;
    private final List<AndroidDevices.Device> devices;
    /** The references of the devices ticked at first: the instances picked in the Emulators window. */
    private final Set<String> picked;
    private final Runnable onDone;
    private final TextField app = new TextField();
    /** Google Play's results for a name typed in {@link #app}; picking one puts its package in the field. */
    private final ListView<PlayStoreSearch.StoreApp> found = new ListView<>();
    /** Waits for the typing to pause before Google Play is searched. */
    private final PauseTransition searching = new PauseTransition(Duration.millis(400));
    /** The name the latest search was for; only its results are shown. */
    private String searchedFor;
    private final RadioButton download = new RadioButton("Download it");
    private final RadioButton file = new RadioButton("An APK file I have…");
    private final Label fileName = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final CheckBox apkPure = new CheckBox("Also from APKPure");
    private final Label signedInAs = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.OK_TEXT);
    private final Hyperlink accountAction = new Hyperlink();
    private final Label accountNote = Styles.on(new Label("Used only when Play's anonymous downloads fail. Use a "
            + "secondary account."), Styles.SMALL_TEXT, Styles.WARNING_TEXT);
    private final VBox downloadOptions;
    private final VBox table = new VBox(4);
    private final Map<String, CheckBox> ticks = new HashMap<>();
    private final Label status = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final Button install = Styles.on(new Button("Install"), Styles.PRIMARY_BUTTON);
    private Stage stage;
    private Path chosenFile;
    /** The package the table's versions were read for; only the latest read fills it. */
    private String readFor;
    private Thread signingIn;
    private Thread installing;

    private GetAppDialog(StudioServices services, List<AndroidDevices.Device> devices, Set<String> picked,
                         Runnable onDone) {
        this.services = services;
        this.devices = List.copyOf(devices);
        this.picked = Set.copyOf(picked);
        this.onDone = onDone;
        ToggleGroup from = new ToggleGroup();
        download.setToggleGroup(from);
        file.setToggleGroup(from);
        download.setSelected(true);
        from.selectedToggleProperty().addListener((o, was, is) -> fromChanged());
        apkPure.setTooltip(new Tooltip("When Google Play's files can't be had and the device has no Google Play, "
                + "BotMaker downloads the app from APKPure with apkeep"));
        apkPure.setSelected(ApkDownload.acknowledged());
        apkPure.setOnAction(e -> apkPureChanged());
        accountAction.setOnAction(e -> accountClicked());
        accountNote.setWrapText(true);
        HBox account = new HBox(6, Styles.on(new Label("Download account:"), Styles.SMALL_TEXT), signedInAs,
                accountAction, accountNote);
        account.setAlignment(Pos.CENTER_LEFT);
        downloadOptions = new VBox(4, apkPure, account);
        downloadOptions.setPadding(new Insets(0, 0, 0, 24));
        app.setPromptText("A game's name, its package (com.example.game) or a Google Play link");
        app.textProperty().addListener((o, was, is) -> {
            readVersions();
            searchLater();
        });
        found.setVisible(false);
        found.setManaged(false);
        found.setPrefHeight(160);
        found.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PlayStoreSearch.StoreApp hit, boolean empty) {
                super.updateItem(hit, empty);
                setText(empty || hit == null ? null : hit.name() + "  ·  " + hit.packageName());
            }
        });
        found.setOnMouseClicked(e -> pickFound());
        found.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) pickFound();
        });
        searching.setOnFinished(e -> search());
        install.setOnAction(e -> install());
    }

    /** Opens the dialog over {@code owner}; {@code onDone} runs on the JavaFX thread after the installs. */
    static void show(StudioServices services, Window owner, List<AndroidDevices.Device> devices, Set<String> picked,
                     Runnable onDone) {
        new GetAppDialog(services, devices, picked, onDone).open(owner);
    }

    private void open(Window owner) {
        Label hint = Styles.on(new Label("Nothing starts until you press Install: a stopped device is started for its "
                + "install and stopped after."), Styles.DIALOG_HINT);
        hint.setWrapText(true);
        Button choose = new Button("Choose…");
        choose.setOnAction(e -> chooseFile());
        HBox fileRow = new HBox(8, file, choose, fileName);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        Label on = Styles.on(new Label("Install on"), Styles.DIALOG_SUBHEADING);
        status.setWrapText(true);
        Button close = new Button("Close");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, status, spacer, install, close);
        bottom.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(status, Priority.SOMETIMES);
        VBox root = new VBox(10, Styles.on(new Label("Get an app"), Styles.DIALOG_HEADING), hint, app, found, download,
                downloadOptions, fileRow, on, table, bottom);
        root.setPadding(new Insets(16));
        stage = Modals.window(services, owner, Modals.Frame.modeless("Get an app", 620, 520, 460, 380), root);
        stage.setOnHidden(e -> {
            if (signingIn != null) signingIn.interrupt();
        });
        showTable(Map.of());
        showAccount();
        fromChanged();
        stage.show();
    }

    private void fromChanged() {
        boolean downloading = download.isSelected();
        downloadOptions.setVisible(downloading);
        downloadOptions.setManaged(downloading);
        app.setDisable(!downloading);
        if (!downloading && chosenFile == null) chooseFile();
        readVersions();
        searchLater();
    }

    private void chooseFile() {
        services.dialogs().chooseFile("Choose an Android app", Path.of(System.getProperty("user.home", "")),
                "apk", "xapk", "apks").path().ifPresent(picked -> Async.load("apk-read", () -> {
            try (ApkFile apk = ApkFile.open(picked)) {
                return apk.packageName();
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }, pkg -> {
            chosenFile = picked;
            file.setSelected(true);
            fileName.setText(picked.getFileName().toString());
            app.setText(pkg);
        }, why -> say(false, why)));
    }

    /** The package the dialog installs, or empty while the field names none. */
    private Optional<String> pkg() {
        return PlayStoreSearch.packageIn(app.getText() == null ? "" : app.getText().strip());
    }

    /** The name to look for on Google Play in {@code text}, or empty when it names a package already or is too short. */
    static Optional<String> nameIn(String text) {
        String t = text == null ? "" : text.strip();
        return t.length() < 2 || PlayStoreSearch.packageIn(t).isPresent() ? Optional.empty() : Optional.of(t);
    }

    /** Searches Google Play once the typing pauses; a package or a link hides the results. */
    private void searchLater() {
        searching.stop();
        if (download.isSelected() && nameIn(app.getText()).isPresent()) {
            searching.playFromStart();
        } else {
            searchedFor = null;
            showFound(List.of());
        }
    }

    /** Google Play's results for the typed name, off the JavaFX thread; a result for older typing is dropped. */
    private void search() {
        String name = nameIn(app.getText()).orElse(null);
        searchedFor = name;
        if (name == null) return;
        say(true, "Looking for “" + name + "” on Google Play…");
        Async.load("play-search", () -> {
            try {
                return PlayStoreSearch.search(name);
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }, hits -> {
            if (!name.equals(searchedFor)) return;
            showFound(hits);
            say(true, hits.isEmpty() ? "Google Play lists nothing for “" + name + "”." : "Pick the app.");
        }, why -> {
            if (name.equals(searchedFor)) say(false, why);
        });
    }

    private void showFound(List<PlayStoreSearch.StoreApp> hits) {
        found.getItems().setAll(hits);
        found.setVisible(!hits.isEmpty());
        found.setManaged(!hits.isEmpty());
    }

    /** The picked result's package goes in the field, which reads its versions and hides the results. */
    private void pickFound() {
        PlayStoreSearch.StoreApp hit = found.getSelectionModel().getSelectedItem();
        if (hit != null) app.setText(hit.packageName());
    }

    /** Reads, off the JavaFX thread, what each running device has of the package; the table shows it. */
    private void readVersions() {
        String pkg = pkg().orElse(null);
        readFor = pkg;
        install.setDisable(pkg == null || (file.isSelected() && chosenFile == null));
        if (pkg == null) {
            showTable(Map.of());
            return;
        }
        Async.load("install-on-versions", () -> {
            Map<String, InstallOn.Facts> facts = new HashMap<>();
            for (AndroidDevices.Device device : devices) {
                if (!device.running()) continue;
                InstallOn.Facts read = InstallOn.read(device.instance(), pkg);
                if (read != null) facts.put(device.instance().reference(), read);
            }
            return facts;
        }, facts -> {
            if (pkg.equals(readFor)) showTable(facts);
        });
    }

    private void showTable(Map<String, InstallOn.Facts> facts) {
        Map<String, Boolean> was = new HashMap<>();
        ticks.forEach((reference, box) -> was.put(reference, box.isSelected()));
        ticks.clear();
        table.getChildren().clear();
        if (devices.isEmpty()) table.getChildren().add(Styles.on(new Label("No Android device is on this computer."),
                Styles.DIALOG_HINT));
        for (InstallOn.Row row : InstallOn.rows(devices, facts, picked)) {
            String reference = row.device().instance().reference();
            CheckBox box = new CheckBox((row.device().running() ? "● " : "○ ") + row.text());
            box.setSelected(was.getOrDefault(reference, row.ticked()));
            ticks.put(reference, box);
            table.getChildren().add(box);
        }
    }

    private void install() {
        if (installing != null && installing.isAlive()) return;
        String pkg = pkg().orElse(null);
        List<AndroidDevices.Device> on = devices.stream()
                .filter(d -> ticks.containsKey(d.instance().reference()) && ticks.get(d.instance().reference()).isSelected())
                .toList();
        if (pkg == null || on.isEmpty()) {
            say(false, pkg == null ? "Name the app first: its package or its Google Play link." : "Tick a device.");
            return;
        }
        Path fromFile = file.isSelected() ? chosenFile : null;
        boolean allowApkPure = ApkDownload.acknowledged();
        install.setDisable(true);
        say(true, "Installing " + pkg + "…");
        installing = Async.run("get-an-app", () -> {
            List<String> said = new ArrayList<>();
            boolean allOk = true;
            for (AndroidDevices.Device device : on) {
                EmulatorInstance instance = device.instance();
                boolean wasRunning = EmulatorLiveness.running(instance);
                AndroidInstall android = new AndroidInstall(LaunchSpec.emulatorApp(pkg, instance.reference(), null),
                        allowApkPure, ApkLibrary.shared());
                GameInstall.Result result = fromFile != null
                        ? android.fromFile(fromFile, line -> Platform.runLater(() -> say(true, line)))
                        : android.update(line -> Platform.runLater(() -> say(true, line)));
                if (!wasRunning && result.outcome() != GameInstall.Outcome.WAITING_FOR_USER) {
                    EmulatorLauncher.stop(instance);
                }
                boolean ok = result.outcome() != GameInstall.Outcome.FAILED
                        && result.outcome() != GameInstall.Outcome.NO_STORE;
                allOk &= ok;
                said.add(instance.caption() + ": " + result.message());
            }
            boolean ok = allOk;
            Platform.runLater(() -> {
                say(ok, String.join(" ", said));
                install.setDisable(false);
                readVersions();
            });
        }, onDone);
    }

    private void say(boolean ok, String message) {
        status.setText(message);
        Styles.pick(status, ok ? Styles.MUTED_TEXT : Styles.ERROR_TEXT, Styles.MUTED_TEXT, Styles.ERROR_TEXT);
    }

    /** The account line as it stands: signing in, signed in, or not. FX thread. */
    private void showAccount() {
        Optional<String> email = GoogleAccount.signedIn();
        // An assistant's sign-in (setup google_sign_in) waits in the same browser, and Cancel stops it too.
        boolean waiting = signingIn != null || GoogleAccount.signingIn();
        signedInAs.setText(waiting ? "Waiting for you to sign in in the browser…"
                : email.map(who -> "Signed in as " + who + " ·").orElse(""));
        Styles.pick(signedInAs, waiting ? Styles.MUTED_TEXT : Styles.OK_TEXT, Styles.MUTED_TEXT, Styles.OK_TEXT);
        accountAction.setText(waiting ? "Cancel" : email.isPresent() ? "Sign out" : "Sign in…");
        boolean note = !waiting && email.isEmpty();
        accountNote.setVisible(note);
        accountNote.setManaged(note);
    }

    private void accountClicked() {
        if (signingIn != null || GoogleAccount.signingIn()) {
            if (signingIn != null) signingIn.interrupt();
            GoogleAccount.cancelSignIn();
            showAccount();
            return;
        }
        if (GoogleAccount.signedIn().isPresent()) {
            GoogleAccount.signOut();
            say(true, GoogleAccount.SIGNED_OUT);
            showAccount();
            return;
        }
        if (!confirmSignIn()) return;
        signingIn = Async.load("google-sign-in", () -> GoogleAccount.signIn(line -> Platform.runLater(() ->
                say(true, line))), result -> {
            signingIn = null;
            say(result.ok(), result.message());
            showAccount();
        }, why -> {
            signingIn = null;
            say(false, why);
            showAccount();
        });
        showAccount();
    }

    private boolean confirmSignIn() {
        ButtonType open = new ButtonType("Open the sign-in", ButtonBar.ButtonData.OK_DONE);
        Alert alert = services.theme().alert(Alert.AlertType.CONFIRMATION, "", ButtonType.CANCEL, open);
        alert.initOwner(stage);
        alert.setTitle("Sign in to Google");
        alert.setHeaderText("Download Google Play's files with your account");
        Label text = new Label("BotMaker downloads a game's Google Play files with this account when Aurora's "
                + "anonymous accounts give none, for every emulator on this computer; no emulator signs in. Use a "
                + "secondary account: Google may flag an account that downloads for many devices. A Chrome or Edge "
                + "window opens on Google's sign-in; sign in there and select I agree. The key Google gives stays on "
                + "this computer.");
        text.setWrapText(true);
        text.setMaxWidth(460);
        alert.getDialogPane().setContent(text);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == open;
    }

    /** Ticked: apkeep's warning, then the user's answer kept. Unticked: the OK withdrawn. */
    private void apkPureChanged() {
        boolean yes = apkPure.isSelected() && confirmApkPure();
        apkPure.setSelected(yes);
        try {
            ApkDownload.acknowledge(yes);
        } catch (IOException e) {
            apkPure.setSelected(ApkDownload.acknowledged());
            say(false, "Couldn't keep your answer: " + e.getMessage());
        }
    }

    private boolean confirmApkPure() {
        Alert alert = services.theme().alert(Alert.AlertType.WARNING, "", ButtonType.CANCEL, ALLOW);
        alert.initOwner(stage);
        alert.setTitle("Download from APKPure?");
        alert.setHeaderText("APKPure is a third-party copy of Google Play");
        Label text = new Label("It has been found serving modified apps, which can carry malware. apkeep, the "
                + "downloader BotMaker uses, warns about it on its own page. Allow BotMaker to download Android apps "
                + "from APKPure on this computer when Google Play's files can't be had and a device has no Google "
                + "Play? You can untick this later.");
        text.setWrapText(true);
        text.setMaxWidth(460);
        Hyperlink link = new Hyperlink(ApkDownload.DANGERS);
        link.setOnAction(e -> UriLauncher.open(ApkDownload.DANGERS));
        alert.getDialogPane().setContent(new VBox(8, text, link));
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ALLOW;
    }
}
