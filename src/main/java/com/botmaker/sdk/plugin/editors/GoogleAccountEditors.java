// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.sdk.api.signin.GoogleAccount;
import com.botmaker.sdk.plugin.emulator.accounts.AddAccount;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import com.botmaker.shared.emulator.google.PasswordStore;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * A {@link GoogleAccount}'s pill (2026-11-17, the user's pick A of the mockups): the book's Google accounts, each with its
 * second step and a warning when no password is kept on this PC, then <i>Add account…</i>, one form (email, password,
 * second step, the authenticator's key) that puts the account in the book only.
 */
public final class GoogleAccountEditors {

    private static final String NONE = "No account";
    private static final String ADD = "➕ Add account…";

    /** A book account as the menu says it. */
    record Choice(String email, Verification verify, boolean remembered) {
        String label() {
            String said = email + " — " + verify.displayName();
            return remembered ? said : said + " · no password kept";
        }
    }

    private GoogleAccountEditors() {}

    public static Node account(ValueContext ctx) {
        MenuButton pill = Pills.bare(label(ctx.value(GoogleAccount.class).orElse(null)));
        AtomicReference<List<Choice>> book = new AtomicReference<>(List.of());
        StudioServices services = ctx.services();
        if (services != null) Async.load("google-accounts", () -> choices(services), book::set);
        Pills.onOpen(pill, () -> {
            List<MenuItem> items = new ArrayList<>();
            items.add(Pills.item(NONE, () -> choose(ctx, pill, GoogleAccount.of(""))));
            for (Choice each : book.get()) {
                items.add(Pills.item(each.label(), () -> choose(ctx, pill, GoogleAccount.of(each.email()))));
            }
            items.add(Pills.separator());
            items.add(Pills.item(ADD, () -> addAccount(ctx, pill, book)));
            return items;
        });
        return pill;
    }

    /** The account's email beside a declared value. */
    public static Node preview(ValueContext ctx) {
        return ctx.value(GoogleAccount.class).map(account -> (Node) new Label(label(account))).orElse(null);
    }

    /** The book's Google accounts, in its order, each with whether a password is kept. Reads the store: never on FX. */
    static List<Choice> choices(StudioServices services) {
        try {
            PasswordStore store = EmulatorsWindow.passwords();
            List<AccountRecord> google = AddAccount.book(services).list().stream()
                    .filter(account -> account.kind() == AccountRecord.Kind.GOOGLE)
                    .toList();
            Set<String> remembered = google.stream()
                    .flatMap(account -> account.addresses().stream())
                    .filter(store::remembered)
                    .map(PasswordStore::key)
                    .collect(Collectors.toSet());
            return google.stream()
                    .map(account -> new Choice(account.name(), account.verify(),
                            PasswordStore.rememberedAny(account.addresses(), remembered)))
                    .toList();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static void addAccount(ValueContext ctx, MenuButton pill, AtomicReference<List<Choice>> book) {
        StudioServices services = ctx.services();
        if (services == null) return;
        form(services).ifPresent(form -> Async.load("google-account-add", () -> {
            try {
                return AddAccount.add(AddAccount.book(services), EmulatorsWindow.passwords(),
                        EmulatorsWindow.authenticatorKeys(), form);
            } catch (IOException e) {
                return AddAccount.Result.UNKNOWN;
            }
        }, result -> {
            services.status(said(form.email(), result));
            if (!result.kept()) return;
            choose(ctx, pill, GoogleAccount.of(form.email()));
            Async.load("google-accounts", () -> choices(services), book::set);
        }, why -> services.status("The account was not added: " + why)));
    }

    private static String said(String email, AddAccount.Result result) {
        return switch (result) {
            case ADDED -> email + " is in this PC's accounts; a bot signs it in.";
            case UPDATED -> email + "'s password and second step are kept anew.";
            case INVALID_EMAIL -> "\"" + email + "\" is not an address. Nothing was kept.";
            case NO_KEYRING -> "This PC has no credential store that answers (GNOME Keyring, KWallet, Credential"
                    + " Manager, Keychain). Nothing was kept.";
            case NOT_A_KEY -> "That is not a setup key: Google shows one as groups of letters (A–Z) and digits (2–7)."
                    + " Nothing was kept.";
            case REFUSED -> "This PC's credential store refused " + email + "'s secrets. Nothing was kept.";
            case UNKNOWN -> "The account was not added: the book could not be written.";
        };
    }

    /** The one form; the secrets leave it as arrays, never as kept strings. */
    private static Optional<AddAccount.Form> form(StudioServices services) {
        TextField email = new TextField();
        email.setPromptText("name@gmail.com");
        PasswordField password = new PasswordField();
        ComboBox<Verification> verify = new ComboBox<>();
        verify.getItems().setAll(Verification.methods());
        verify.setValue(Verification.PHONE);
        verify.setButtonCell(verificationCell());
        verify.setCellFactory(list -> verificationCell());
        PasswordField key = new PasswordField();
        key.setPromptText("JBSW Y3DP …");
        Label keyLabel = new Label("Setup key");
        Label hint = new Label("Kept in this PC's credential store. Signs in on no instance.");
        hint.setWrapText(true);
        hint.setMaxWidth(360);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("Email"), email);
        grid.addRow(1, new Label("Password"), password);
        grid.addRow(2, new Label("Second step"), verify);
        grid.addRow(3, keyLabel, key);
        grid.add(hint, 0, 4, 2, 1);
        Runnable shown = () -> {
            boolean authenticator = verify.getValue() == Verification.AUTHENTICATOR;
            key.setVisible(authenticator);
            keyLabel.setVisible(authenticator);
        };
        verify.valueProperty().addListener((o, was, now) -> shown.run());
        shown.run();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Accounts");
        dialog.setHeaderText("Add a Google account");
        dialog.getDialogPane().setContent(grid);
        ButtonType add = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);
        Runnable check = () -> dialog.getDialogPane().lookupButton(add)
                .setDisable(email.getText().isBlank() || password.getText().isEmpty()
                        || verify.getValue() == Verification.AUTHENTICATOR && key.getText().isBlank());
        email.textProperty().addListener((o, was, now) -> check.run());
        password.textProperty().addListener((o, was, now) -> check.run());
        key.textProperty().addListener((o, was, now) -> check.run());
        verify.valueProperty().addListener((o, was, now) -> check.run());
        check.run();
        if (services.theme() != null) services.theme().apply(dialog);
        Platform.runLater(email::requestFocus);
        Optional<AddAccount.Form> form = dialog.showAndWait().filter(add::equals).map(pressed -> new AddAccount.Form(
                email.getText(), password.getText().toCharArray(), verify.getValue(),
                verify.getValue() == Verification.AUTHENTICATOR ? key.getText().toCharArray() : null));
        password.clear();
        key.clear();
        return form;
    }

    private static ListCell<Verification> verificationCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Verification item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        };
    }

    private static void choose(ValueContext ctx, MenuButton pill, GoogleAccount picked) {
        Optional<String> refused = ctx.write(picked);
        if (refused.isPresent()) {
            if (ctx.services() != null) ctx.services().status(refused.get());
            return;
        }
        pill.setText(label(picked));
    }

    private static String label(GoogleAccount account) {
        return account == null || account.isBlank() ? NONE : account.email();
    }
}
