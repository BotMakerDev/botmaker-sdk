package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.plugin.api.StyleClasses;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.GameRecord;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.List;
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
                        + " asked then, unless this PC remembers it." : "A record only: BotMaker signs nothing in for it.");
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

    /**
     * + Google (board A): a radio row per account of the book the layer does not list, the first one picked, then
     * <i>Another account</i> with its email field, and the note. Answers the account, with the note as given.
     */
    static Optional<AccountRecord> google(EmulatorsWindow window, String layer, List<GooglePick.Choice> choices,
                                          Collection<AccountRecord.Key> here) {
        Dialog<ButtonType> dialog = dialog(window, "Google account", "Add a Google account to " + layer
                + ". BotMaker signs the instance in to it at Apply.");
        ButtonType add = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);

        ToggleGroup group = new ToggleGroup();
        TextField other = new TextField();
        other.setPromptText("name@gmail.com");
        other.setPrefColumnCount(24);
        TextField note = new TextField();
        note.setPromptText("Optional, e.g. alt for server 12");
        VBox body = new VBox(8);
        if (!choices.isEmpty()) body.getChildren().add(Styles.on(new Label("Known on this PC"), StyleClasses.STRONG_TEXT));
        for (GooglePick.Choice choice : choices) {
            Label email = Styles.on(new Label(choice.account().name()), StyleClasses.STRONG_TEXT);
            Label detail = Styles.on(new Label(choice.detail()), StyleClasses.MUTED_TEXT);
            Label pill = Styles.on(new Label(choice.pill().displayName()),
                    choice.pill() == GooglePick.Pill.REMEMBERED ? StyleClasses.OK_TEXT : StyleClasses.MUTED_TEXT);
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox graphic = new HBox(12, new VBox(2, email, detail), gap, pill);
            graphic.setAlignment(Pos.CENTER_LEFT);
            graphic.setMinWidth(380);
            RadioButton radio = new RadioButton();
            radio.setGraphic(graphic);
            radio.setToggleGroup(group);
            radio.setUserData(choice.account());
            radio.selectedProperty().addListener((o, was, now) -> {
                if (now) note.setText(choice.account().note());
            });
            body.getChildren().add(radio);
        }
        RadioButton another = new RadioButton("Another account");
        another.setToggleGroup(group);
        HBox anotherRow = new HBox(10, another, other);
        anotherRow.setAlignment(Pos.CENTER_LEFT);
        other.textProperty().addListener((o, was, now) -> {
            if (!now.isEmpty()) another.setSelected(true);
        });
        group.selectedToggleProperty().addListener((o, was, now) -> {
            // Leaving a known account for Another takes its note away, never one the user typed.
            if (now == another && was != null && was.getUserData() instanceof AccountRecord left
                    && note.getText().equals(left.note())) note.setText("");
        });
        Label listed = Styles.on(new Label("This configuration already lists it."), StyleClasses.MUTED_TEXT);
        listed.setVisible(false);
        listed.managedProperty().bind(listed.visibleProperty());
        body.getChildren().addAll(anotherRow, listed, new Label("Note"), note);
        dialog.getDialogPane().setContent(body);

        Supplier<Optional<String>> picked = () -> {
            if (group.getSelectedToggle() == null) return Optional.empty();
            if (group.getSelectedToggle() == another) {
                String typed = other.getText().strip();
                boolean already = here.contains(new AccountRecord(AccountRecord.Kind.GOOGLE, typed).key());
                listed.setVisible(already);
                return named(AccountRecord.Kind.GOOGLE, typed) && !already ? Optional.of(typed) : Optional.empty();
            }
            listed.setVisible(false);
            return Optional.of(((AccountRecord) group.getSelectedToggle().getUserData()).name());
        };
        Node ok = dialog.getDialogPane().lookupButton(add);
        Runnable check = () -> ok.setDisable(picked.get().isEmpty());
        group.selectedToggleProperty().addListener((o, was, now) -> check.run());
        other.textProperty().addListener((o, was, now) -> check.run());
        if (group.getToggles().size() > 1) {
            group.getToggles().getFirst().setSelected(true);
        } else {
            another.setSelected(true);
            Platform.runLater(other::requestFocus);
        }
        check.run();

        Optional<ButtonType> pressed = dialog.showAndWait();
        if (pressed.isEmpty() || pressed.get() != add) return Optional.empty();
        return picked.get().map(name -> new AccountRecord(AccountRecord.Kind.GOOGLE, name, note.getText()));
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
