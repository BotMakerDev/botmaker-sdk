package com.botmaker.sdk.internal.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.launch.LaunchTarget;
import com.botmaker.shared.game.EpicLibraryScanner;
import com.botmaker.shared.game.FaugusLibraryScanner;
import com.botmaker.shared.game.GameLibraryProvider;
import com.botmaker.shared.game.HeroicLibraryScanner;
import com.botmaker.shared.game.SteamLibraryScanner;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Editor for a {@code LaunchTarget} argument (what the bot automates): a menu button offering the kinds a
 * launch target can be — a game in a launcher's library (Steam, Epic, Heroic, Faugus), a plain executable or
 * command line, or an app inside an Android emulator — each via the reusable picker dialog ({@link GameLibraryPickerDialog} / OS file chooser /
 * {@link com.botmaker.sdk.internal.plugin.emulator.EmulatorPicker}).
 *
 * <p>Replaces the plain {@code new …}/constructor pill the user otherwise gets for a {@code LaunchTarget} slot.
 */
public final class LaunchTargetArgPicker {

    private static final String PARSE = LaunchTarget.class.getName() + ".parse(\"%s\")";

    private LaunchTargetArgPicker() {}

    /** Creates a launch target picker node for the given context using host services. */
    public static Node create(StudioServices services, Window owner, String currentSpec) {
        MenuButton button = new MenuButton(label(currentSpec));
        button.getStyleClass().add("launch-target-picker");

        MenuItem steam = new MenuItem("Steam game…");
        steam.setOnAction(e -> pickGame(services, owner, button, new SteamLibraryScanner(), "steam", currentSpec));
        MenuItem epic = new MenuItem("Epic game…");
        epic.setOnAction(e -> pickGame(services, owner, button, new EpicLibraryScanner(), "epic", currentSpec));
        MenuItem heroic = new MenuItem("Heroic game (Epic/GOG on Linux)…");
        heroic.setOnAction(e -> pickGame(services, owner, button, new HeroicLibraryScanner(), "heroic", currentSpec));
        MenuItem faugus = new MenuItem("Faugus game (Proton/Wine on Linux)…");
        faugus.setOnAction(e -> pickGame(services, owner, button, new FaugusLibraryScanner(), "faugus", currentSpec));
        MenuItem exe = new MenuItem("Executable…");
        exe.setOnAction(e -> pickExecutable(services, owner, button));
        MenuItem cli = new MenuItem("CLI command…");
        cli.setOnAction(e -> pickCliCommand(services, owner, button));
        MenuItem emu = new MenuItem("Emulator app…");
        emu.setOnAction(e -> pickEmulatorApp(services, owner, button));

        button.getItems().addAll(steam, epic, heroic, faugus, new SeparatorMenuItem(), exe, cli, emu);
        return button;
    }

    private static void pickGame(StudioServices services, Window owner, MenuButton button,
                                 GameLibraryProvider provider, String kind, String currentSpec) {
        GameLibraryPickerDialog.show(services, owner, provider).ifPresent(game -> {
            if (game.id() == null || game.id().isBlank()) return;
            apply(button, kind + ":" + game.id());
        });
    }

    private static void pickExecutable(StudioServices services, Window owner, MenuButton button) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a program to launch");
        File chosen = chooser.showOpenDialog(owner);
        if (chosen != null) {
            apply(button, "exe:" + chosen.getAbsolutePath());
        }
    }

    private static void pickCliCommand(StudioServices services, Window owner, MenuButton button) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(cliCommandOf(null));
        dialog.initOwner(owner);
        dialog.setTitle("CLI command");
        dialog.setHeaderText("Command the bot runs to launch the game");
        dialog.setContentText("Command:");
        dialog.getEditor().setPrefColumnCount(40);
        dialog.showAndWait().ifPresent(cmd -> {
            String trimmed = cmd.trim();
            if (!trimmed.isEmpty()) apply(button, "cli:" + trimmed);
        });
    }

    /** The command line inside a current {@code cli:} spec (so re-editing pre-fills it), else empty. */
    private static String cliCommandOf(String spec) {
        return spec != null && spec.startsWith("cli:") ? spec.substring("cli:".length()) : "";
    }

    private static void pickEmulatorApp(StudioServices services, Window owner, MenuButton button) {
        com.botmaker.sdk.internal.plugin.emulator.EmulatorPicker.show(services, owner).ifPresent(sel -> {
            if (!sel.hasApp()) return; // a LaunchTarget needs the app package, not just the instance
            apply(button, "emu-app:" + sel.appPackage() + "@" + sel.instance().name());
        });
    }

    /** Commits {@code spec} as the new button label. */
    private static void apply(MenuButton button, String spec) {
        button.setText(labelFor(spec));
    }

    /**
     * The pill's text for a spec. Delegates to {@link LaunchSpec} rather than keeping a third private
     * copy of the kind→label switch.
     */
    private static String labelFor(String spec) {
        return LaunchSpec.describe(spec);
    }

    /** Shows an outcome on the status line — green when it worked, the usual red when it didn't. */
    private static String label(String spec) {
        return spec == null ? "Choose target…" : labelFor(spec);
    }
}
