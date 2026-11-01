package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.checks.CheckResult;
import com.botmaker.shared.emulator.checks.WaydroidChecks;
import com.botmaker.plugin.api.StudioServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.awt.Desktop;
import java.net.URI;

/**
 * What is wrong with the local Waydroid setup: each failed check of shared's {@link WaydroidChecks}, what fixes
 * it, and its commands to copy. The Game dialog's Emulator checks run the fixes (2026-11-01); this window says
 * where.
 *
 * <p>Reached two ways, per the same principle as the rest of the picker — <em>offered</em>, never forced: the
 * "Diagnose…" button on the Waydroid row, and automatically when Waydroid is installed but discovery came back
 * with nothing, which is precisely the moment the user has a symptom and no explanation.
 */
public final class WaydroidReport {

    private WaydroidReport() {}

    /** Runs the probes off the FX thread and shows the results. Returns as soon as the dialog is dismissed. */
    public static void show(StudioServices services, Window owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        services.theme().apply(dialog);
        dialog.setTitle("Waydroid diagnostics");
        if (owner != null) dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        VBox body = new VBox(10);
        body.setPadding(new Insets(12));
        body.getChildren().add(new Label("Checking Waydroid…"));

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(560);
        scroll.setPrefViewportHeight(420);
        dialog.getDialogPane().setContent(scroll);

        // The probes shell out (systemctl, waydroid status, ip route) — never on the FX thread.
        Async.load("waydroid-diagnostics", () -> WaydroidChecks.run(null, null).stream()
                .filter(CheckResult::failed).toList(), findings -> {
            body.getChildren().clear();
            if (findings.isEmpty()) {
                body.getChildren().add(allClear());
                return;
            }
            for (CheckResult finding : findings) {
                body.getChildren().add(card(finding));
            }
        });

        dialog.showAndWait();
    }

    /**
     * The empty result, worded as a conclusion rather than a shrug: ruling the setup out is the answer that
     * makes running the check worthwhile, and "no problems found" alone reads as "the check did nothing".
     */
    private static VBox allClear() {
        Label title = new Label("No problems found.");
        Styles.on(title, Styles.STRONG_TEXT);
        Label detail = new Label("The container service, session, host networking, ARM translation layer and "
                + "display size all look right. If a bot still can't reach Waydroid, the cause is somewhere "
                + "else — start the session and check that the app is actually running.");
        detail.setWrapText(true);
        return new VBox(6, title, detail);
    }

    /** One failed check: what was found, what fixes it, the commands (read-only) and a Copy button. */
    private static VBox card(CheckResult finding) {
        Label symptom = new Label(finding.reason());
        symptom.setWrapText(true);
        Styles.on(symptom, Styles.STRONG_TEXT);

        Label remedy = new Label(finding.fixable()
                ? finding.remedy() + " Fix is in the Game dialog's Emulator checks." : finding.remedy());
        remedy.setWrapText(true);

        VBox card = new VBox(6, symptom, remedy);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: -fx-control-inner-background-alt; -fx-background-radius: 6;");

        if (!finding.commands().isEmpty()) {
            TextArea commands = new TextArea(finding.commandBlock());
            commands.setEditable(false);
            commands.setWrapText(false);
            commands.setPrefRowCount(Math.min(finding.commands().size(), 12));
            Styles.on(commands, Styles.MONO_TEXT);

            Button copy = new Button("Copy commands");
            copy.setOnAction(e -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(finding.commandBlock());
                Clipboard.getSystemClipboard().setContent(content);
                copy.setText("Copied");
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox actions = new HBox(8, spacer, copy);
            actions.setAlignment(Pos.CENTER_RIGHT);

            if (finding.link() != null) {
                Hyperlink docs = new Hyperlink("Upstream docs");
                docs.setOnAction(e -> browse(finding.link()));
                actions.getChildren().add(0, docs);
            }
            card.getChildren().addAll(commands, actions);
        }
        return card;
    }

    /**
     * Opens a URL in the user's browser; silently does nothing where that isn't supported. Off the FX thread:
     * AWT's {@code browse} spawns the desktop's opener and can block, and a frozen dialog over a help link is
     * worse than a link that took a second.
     */
    private static void browse(String url) {
        Async.run("waydroid-docs", () -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                }
            } catch (Exception ignored) {
                // A diagnostics panel that throws while opening a help page is worse than one that doesn't.
            }
        }, null);
    }
}
