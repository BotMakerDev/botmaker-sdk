// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.sdk.api.secrets.Secret;
import com.botmaker.sdk.plugin.secrets.KeptSecrets;
import com.botmaker.sdk.plugin.secrets.SecretKind;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Secret}'s pill (2026-11-17; the layout the user picked for phase 1's account pill, kept): the secrets this PC
 * keeps, each with its kind and a warning when the credential store lacks its value, then <i>Add secret…</i>, one form
 * (name, kind, value) that keeps it on this PC only. The value never leaves the form but into the store.
 */
public final class SecretEditors {

    private static final String NONE = "No secret";
    private static final String ADD = "➕ Add secret…";

    private SecretEditors() {}

    public static Node secret(ValueContext ctx) {
        MenuButton pill = Pills.bare(label(ctx.value(Secret.class).orElse(null)));
        AtomicReference<List<KeptSecrets.Listed>> kept = new AtomicReference<>(List.of());
        StudioServices services = ctx.services();
        if (services != null) Async.load("secrets", () -> listed(services), kept::set);
        Pills.onOpen(pill, () -> {
            List<MenuItem> items = new ArrayList<>();
            items.add(Pills.item(NONE, () -> choose(ctx, pill, Secret.of(""))));
            for (KeptSecrets.Listed each : kept.get()) {
                items.add(Pills.item(said(each), () -> choose(ctx, pill, each.secret())));
            }
            items.add(Pills.separator());
            items.add(Pills.item(ADD, () -> addSecret(ctx, pill, kept)));
            return items;
        });
        return pill;
    }

    /** The secret's name beside a declared value. */
    public static Node preview(ValueContext ctx) {
        return ctx.value(Secret.class).map(secret -> (Node) new Label(label(secret))).orElse(null);
    }

    /** A kept secret as the menu says it. */
    static String said(KeptSecrets.Listed listed) {
        String said = listed.secret().name() + " — " + listed.kind().displayName();
        return listed.stored() ? said : said + " · not on this PC";
    }

    /** What this PC keeps. Reads the store: never on FX. */
    private static List<KeptSecrets.Listed> listed(StudioServices services) {
        try {
            return KeptSecrets.list(KeptSecrets.index(services), KeptSecrets.store());
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static void addSecret(ValueContext ctx, MenuButton pill, AtomicReference<List<KeptSecrets.Listed>> kept) {
        StudioServices services = ctx.services();
        if (services == null) return;
        form(services).ifPresent(form -> {
            String name = form.name();
            Async.load("secret-add", () -> {
                try {
                    return KeptSecrets.add(KeptSecrets.index(services), KeptSecrets.store(), form);
                } catch (IOException e) {
                    throw new UncheckedIOException(e.getMessage(), e);
                }
            }, result -> {
                services.status(said(name, result));
                if (!result.kept()) return;
                choose(ctx, pill, Secret.of(name));
                Async.load("secrets", () -> listed(services), kept::set);
            }, why -> services.status("The secret was not kept: " + why));
        });
    }

    static String said(String name, KeptSecrets.Result result) {
        return switch (result) {
            case ADDED -> "\"" + name + "\" is kept on this PC.";
            case UPDATED -> "\"" + name + "\" is kept anew.";
            case INVALID_NAME -> "A secret's name is one line of at most " + KeptSecrets.MAX_NAME
                    + " characters. Nothing was kept.";
            case NO_KEYRING -> "This PC has no credential store that answers (GNOME Keyring, KWallet, Credential"
                    + " Manager, Keychain). Nothing was kept.";
            case NOT_A_KEY -> "That is not a setup key: one reads as groups of letters (A–Z) and digits (2–7)."
                    + " Nothing was kept.";
            case REFUSED -> "This PC's credential store refused \"" + name + "\". Nothing was kept.";
            case UNKNOWN -> "The secret was not kept: this PC's list of secrets could not be written.";
        };
    }

    /** The one form; the value leaves it as an array, never as a kept string. */
    private static Optional<KeptSecrets.Form> form(StudioServices services) {
        TextField name = new TextField();
        name.setPromptText("farm password");
        ComboBox<SecretKind> kind = new ComboBox<>();
        kind.getItems().setAll(SecretKind.kinds());
        kind.setValue(SecretKind.PASSWORD);
        kind.setButtonCell(kindCell());
        kind.setCellFactory(list -> kindCell());
        PasswordField value = new PasswordField();
        Label valueLabel = new Label();
        Label hint = new Label("Kept in this PC's credential store. A bot names it; only BotMaker types it.");
        hint.setWrapText(true);
        hint.setMaxWidth(360);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Name"), name);
        grid.addRow(1, new Label("Kind"), kind);
        grid.addRow(2, valueLabel, value);
        grid.add(hint, 0, 3, 2, 1);
        Runnable shown = () -> {
            boolean authenticator = kind.getValue() == SecretKind.AUTHENTICATOR;
            valueLabel.setText(authenticator ? "Setup key" : "Value");
            value.setPromptText(authenticator ? "JBSW Y3DP …" : "");
        };
        kind.valueProperty().addListener((o, was, now) -> shown.run());
        shown.run();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Secrets");
        dialog.setHeaderText("Add a secret");
        dialog.getDialogPane().setContent(grid);
        ButtonType add = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);
        Runnable check = () -> dialog.getDialogPane().lookupButton(add)
                .setDisable(name.getText().isBlank() || value.getText().isEmpty());
        name.textProperty().addListener((o, was, now) -> check.run());
        value.textProperty().addListener((o, was, now) -> check.run());
        check.run();
        if (services.theme() != null) services.theme().apply(dialog);
        Platform.runLater(name::requestFocus);
        Optional<KeptSecrets.Form> form = dialog.showAndWait().filter(add::equals)
                .map(pressed -> new KeptSecrets.Form(name.getText(), kind.getValue(), value.getText().toCharArray()));
        value.clear();
        return form;
    }

    private static ListCell<SecretKind> kindCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SecretKind item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        };
    }

    private static void choose(ValueContext ctx, MenuButton pill, Secret picked) {
        Optional<String> refused = ctx.write(picked);
        if (refused.isPresent()) {
            if (ctx.services() != null) ctx.services().status(refused.get());
            return;
        }
        pill.setText(label(picked));
    }

    private static String label(Secret secret) {
        return secret == null || secret.isBlank() ? NONE : secret.name();
    }
}
