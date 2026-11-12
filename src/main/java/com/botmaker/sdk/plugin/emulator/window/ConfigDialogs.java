package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.GameRecord;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.function.Supplier;

/** The small dialogs of a configuration: a field's value, an account, a game. Each answers a value or nothing. */
final class ConfigDialogs {

    private ConfigDialogs() {}

    /** What the value dialog answers: a value to set, or the layer's own value dropped. */
    sealed interface Choice {
        record Set(ConfigValue value) implements Choice {
        }

        record Inherit() implements Choice {
        }
    }

    /** Asks {@code target}'s value of {@code field} and writes the answer: a value set, or its own one dropped. */
    static void editField(EmulatorsWindow window, ConfigViews.Target target, ConfigField field) {
        EmulatorConfig config = window.snapshot().config();
        value(window, target.title(), field, ConfigViews.current(target, config, field),
                ConfigViews.dropLabel(target, config, field).orElse(null)).ifPresent(choice -> {
            switch (choice) {
                case Choice.Set(var value) -> window.change(c -> ConfigViews.set(target, c, field, value));
                case Choice.Inherit() -> window.change(c -> ConfigViews.clear(target, c, field));
            }
        });
    }

    /**
     * Asks a field's value, starting from {@code current}. {@code inherit} names the button that drops the layer's own
     * value (<i>Use emulator's</i>, <i>Clear</i>), or is null when it has none.
     */
    static Optional<Choice> value(EmulatorsWindow window, String title, ConfigField field, Optional<ConfigValue> current,
                                  String inherit) {
        Dialog<ButtonType> dialog = dialog(window, field.displayName(), title);
        ButtonType set = new ButtonType("Set", ButtonBar.ButtonData.OK_DONE);
        ButtonType drop = inherit == null ? null : new ButtonType(inherit, ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().add(set);
        if (drop != null) dialog.getDialogPane().getButtonTypes().add(drop);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        Node input;
        Supplier<Optional<ConfigValue>> read;
        if (field.kind() == ConfigField.Kind.TOGGLE) {
            ChoiceBox<String> toggle = new ChoiceBox<>();
            toggle.getItems().setAll("on", "off");
            toggle.setValue(current.map(ConfigValue::written).orElse("on"));
            input = toggle;
            read = () -> ConfigViews.typed(field.kind(), toggle.getValue());
        } else {
            TextField text = new TextField(current.map(ConfigValue::said).orElse(""));
            text.setPromptText(ConfigViews.hint(field.kind()));
            text.setPrefColumnCount(22);
            input = text;
            read = () -> ConfigViews.typed(field.kind(), text.getText());
            Node ok = dialog.getDialogPane().lookupButton(set);
            ok.setDisable(read.get().isEmpty());
            text.textProperty().addListener((o, was, now) -> ok.setDisable(read.get().isEmpty()));
            Platform.runLater(text::requestFocus);
        }
        Label hint = Styles.on(new Label(ConfigViews.hint(field.kind())
                + (field.needsRestart() ? ". Apply restarts a running instance for it." : ".")), StyleClasses.DIALOG_HINT);
        hint.setWrapText(true);
        hint.setMaxWidth(380);
        dialog.getDialogPane().setContent(new VBox(8, input, hint));

        Optional<ButtonType> pressed = dialog.showAndWait();
        if (pressed.isEmpty()) return Optional.empty();
        if (pressed.get() == set) return read.get().map(Choice.Set::new);
        if (pressed.get() == drop) return Optional.of(new Choice.Inherit());
        return Optional.empty();
    }

    /**
     * Asks an account of {@code kind}: its name, and the user's note. Never a password: there is no field for one. With
     * {@code nameFixed} only the note changes, so editing a note never replaces the account.
     */
    static Optional<AccountRecord> account(EmulatorsWindow window, AccountRecord.Kind kind, String name, String note,
                                           boolean nameFixed) {
        Dialog<ButtonType> dialog = dialog(window, kind.displayName() + " account",
                kind.signedInByBotMaker() ? "Apply signs it in on the instances that should hold it. Its password is"
                        + " asked then, and never kept." : "A record only: BotMaker signs nothing in for it.");
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        TextField nameField = new TextField(name == null ? "" : name);
        nameField.setPromptText(kind == AccountRecord.Kind.FACEBOOK ? "Name" : "Email address");
        nameField.setPrefColumnCount(24);
        TextField noteField = new TextField(note == null ? "" : note);
        noteField.setPromptText("Optional, e.g. alt for server 12");
        GridPane form = form(new Label(kind == AccountRecord.Kind.FACEBOOK ? "Name" : "Email"), nameField,
                new Label("Note"), noteField);
        dialog.getDialogPane().setContent(form);
        nameField.setDisable(nameFixed);
        Node ok = dialog.getDialogPane().lookupButton(save);
        ok.setDisable(!named(kind, nameField.getText()));
        nameField.textProperty().addListener((o, was, now) -> ok.setDisable(!named(kind, now)));
        Platform.runLater(nameFixed ? noteField::requestFocus : nameField::requestFocus);
        Optional<ButtonType> pressed = dialog.showAndWait();
        if (pressed.isEmpty() || pressed.get() != save || !named(kind, nameField.getText())) return Optional.empty();
        return Optional.of(new AccountRecord(kind, nameField.getText(), noteField.getText()));
    }

    private static boolean named(AccountRecord.Kind kind, String name) {
        return ConfigViews.accountName(kind, name);
    }

    /** Asks a game: its Android package, and the name shown. */
    static Optional<GameRecord> game(EmulatorsWindow window) {
        Dialog<ButtonType> dialog = dialog(window, "Game", "Apply installs it where it is missing.");
        ButtonType save = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        TextField packageName = new TextField();
        packageName.setPromptText("e.g. com.zjcs.android.us");
        packageName.setPrefColumnCount(24);
        TextField label = new TextField();
        label.setPromptText("e.g. Sword x Staff");
        dialog.getDialogPane().setContent(form(new Label("Package"), packageName, new Label("Name"), label));
        Node ok = dialog.getDialogPane().lookupButton(save);
        ok.setDisable(true);
        packageName.textProperty().addListener((o, was, now) -> ok.setDisable(now.isBlank()));
        Platform.runLater(packageName::requestFocus);
        Optional<ButtonType> pressed = dialog.showAndWait();
        if (pressed.isEmpty() || pressed.get() != save || packageName.getText().isBlank()) return Optional.empty();
        return Optional.of(new GameRecord(packageName.getText(), label.getText()));
    }

    static Dialog<ButtonType> dialog(EmulatorsWindow window, String title, String header) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window.stage());
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        window.services().theme().apply(dialog);
        return dialog;
    }

    private static GridPane form(Node... cells) {
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        for (int i = 0; i < cells.length; i += 2) form.addRow(i / 2, cells[i], cells[i + 1]);
        return form;
    }
}
