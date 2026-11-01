package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.StudioServices;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * What is wrong with the local Waydroid setup: the same rows as the Game dialog's Emulator checks, with their Fix
 * and Undo ({@link ChecksRow}).
 *
 * <p>Reached two ways, per the same principle as the rest of the picker — <em>offered</em>, never forced: the
 * "Diagnose…" button on the Waydroid row, and automatically when Waydroid is installed but discovery came back
 * with nothing, which is precisely the moment the user has a symptom and no explanation.
 */
public final class WaydroidReport {

    private WaydroidReport() {}

    /** Shows the rows, read off the FX thread. Returns as soon as the dialog is dismissed. */
    public static void show(StudioServices services, Window owner, ChecksRow.Starter starter) {
        Dialog<ButtonType> dialog = new Dialog<>();
        services.theme().apply(dialog);
        dialog.setTitle("Waydroid diagnostics");
        if (owner != null) dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        ChecksRow checks = new ChecksRow(services, starter);
        VBox body = new VBox(10, checks.node());
        body.setPadding(new Insets(12));

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(560);
        scroll.setPrefViewportHeight(420);
        dialog.getDialogPane().setContent(scroll);

        checks.showWaydroid();
        dialog.showAndWait();
    }
}
