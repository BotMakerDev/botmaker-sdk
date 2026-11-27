// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.sdk.plugin.emulator.AccountPool;
import com.botmaker.sdk.plugin.emulator.window.Look;
import com.botmaker.shared.emulator.config.AccountRecord;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The Accounts window's side panel for a launch target's account (run-places phase 7d, layout B): its name, what it
 * signs in with (a pick of the book), where it plays (a pick of this PC's places), its note and its password. A new
 * account is the same panel with nothing filled and <i>Add</i> for <i>Save</i>. It writes nothing itself: the window
 * does what its actions ask.
 */
final class LaunchTargetPanel {

    private LaunchTargetPanel() {}

    /** What Add or Save asks: {@code next} in place of the account {@code was} keys, {@code null} for a new one. */
    record Edit(AccountRecord.Key was, AccountRecord next) {}

    /** What the panel's buttons ask of the window. */
    record Actions(Consumer<Edit> save, Runnable setPassword, Runnable forgetPassword, Runnable remove) {}

    /**
     * The panel for {@code row}'s account of {@code target}, or for a new one when {@code row} is null.
     *
     * @param warning what the list's ⚠ says of the row, shown above the links
     */
    static VBox of(String target, AccountsListModel.Row row, List<AccountRecord> book, AccountPool.Pc pc,
                   Optional<String> warning, Actions actions) {
        AccountRecord account = row == null ? null : row.account();
        AccountRecord.Key was = account == null ? null : account.key();
        VBox panel = new VBox(6);

        panel.getChildren().add(account == null ? Look.heading("New account", null)
                : Look.heading(account.name(), Look.avatar(account.name(), account.key().name())));
        panel.getChildren().add(Look.muted("An account of " + target));
        warning.ifPresent(words -> {
            Label warned = Look.line("⚠ " + words);
            warned.setWrapText(true);
            warned.getStyleClass().add("em-warning-text");
            panel.getChildren().add(warned);
        });

        TextField name = new TextField(account == null ? "" : account.name());
        name.setId("lt-name");
        name.setPromptText("The name it logs in as");
        Label refused = Styles.on(new Label(), StyleClasses.MUTED_TEXT);
        refused.setWrapText(true);

        ComboBox<Optional<AccountRecord.Key>> signsInWith = new ComboBox<>();
        signsInWith.setId("lt-signs-in-with");
        Optional<AccountRecord.Key> with = account == null ? Optional.empty() : account.signsInWith();
        signsInWith.getItems().setAll(AccountsListModel.signInChoices(book, with));
        signsInWith.setValue(with);
        cells(signsInWith, key -> key.map(one -> one.name() + " (" + one.kind().displayName() + ")")
                .orElse("No account"));
        signsInWith.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> playsAt = new ComboBox<>();
        playsAt.setId("lt-plays-at");
        String at = account == null ? "" : account.playsAt();
        playsAt.getItems().setAll(AccountsListModel.placeChoices(pc, at));
        playsAt.setValue(at);
        cells(playsAt, AccountsListModel::playsAtText);
        playsAt.setMaxWidth(Double.MAX_VALUE);

        TextField note = new TextField(account == null ? "" : account.note());
        note.setId("lt-note");
        note.setPromptText("Optional");

        Button save = new Button(account == null ? "Add" : "Save");
        save.setId("lt-save");
        save.setDefaultButton(true);
        Runnable check = () -> {
            Optional<String> why = AccountsListModel.nameRefused(target, name.getText(), was, book);
            refused.setText(why.orElse(""));
            save.setDisable(why.isPresent());
        };
        name.textProperty().addListener((o, before, now) -> check.run());
        check.run();
        save.setOnAction(e -> {
            if (AccountsListModel.nameRefused(target, name.getText(), was, book).isPresent()) return;
            actions.save().accept(new Edit(was, AccountRecord.ofLaunchTarget(target, name.getText(), note.getText())
                    .withSignsInWith(signsInWith.getValue() == null ? Optional.empty() : signsInWith.getValue())
                    .withPlaysAt(playsAt.getValue() == null ? "" : playsAt.getValue())));
        });

        panel.getChildren().addAll(Look.cap("Name"), name, refused,
                Look.cap("Signs in with"), signsInWith,
                Look.muted("The Google, Facebook or mail account of the book it signs in to the game with."),
                Look.cap("Plays at"), playsAt,
                Look.muted("Several runs at once deal it to a run at this place; any free place for none."),
                Look.cap("Note"), note);

        if (row != null) {
            HBox password = new HBox(6, Look.pill(row.password().pill()));
            password.setAlignment(Pos.CENTER_LEFT);
            Button set = new Button(row.password() == AccountsListModel.Password.KEPT ? "Replace…" : "Set password…");
            set.setId("lt-password");
            set.setOnAction(e -> actions.setPassword().run());
            password.getChildren().add(set);
            if (row.password() == AccountsListModel.Password.KEPT) {
                Button forget = new Button("Forget");
                forget.setOnAction(e -> actions.forgetPassword().run());
                password.getChildren().add(forget);
            }
            panel.getChildren().addAll(Look.cap("Password"), password, Look.muted(row.password().words(true)));
        }

        HBox buttons = new HBox(6, save);
        if (row != null && row.removable()) {
            Button remove = new Button("Remove");
            remove.setOnAction(e -> actions.remove().run());
            buttons.getChildren().add(remove);
        }
        panel.getChildren().add(buttons);
        return panel;
    }

    private static <T> void cells(ComboBox<T> pick, Function<T, String> said) {
        pick.setCellFactory(list -> cell(said));
        pick.setButtonCell(cell(said));
    }

    private static <T> ListCell<T> cell(Function<T, String> said) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : said.apply(item));
            }
        };
    }
}
