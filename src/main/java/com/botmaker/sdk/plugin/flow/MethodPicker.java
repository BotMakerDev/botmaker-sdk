package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.toolkit.Modals;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A choice of one of the bot's methods — an activity's body, the flow's way home — beside a <i>Create method…</i>
 * button that writes one and picks it (2026-10-10).
 *
 * <p>The list is what the host answers ({@code PluginValues.methods}): the methods whose declaration fits, by
 * their return type, so a method is offered whether or not the flow names it yet. The references are compared,
 * never parsed; the one place a typed text becomes a name is the create prompt, checked by
 * {@link FlowNames#isMethodReference} before the host is asked.
 */
final class MethodPicker {

    /** The list's first entry: no method, written as the "none yet" the value holds then. */
    static final String NONE = "(none)";

    private MethodPicker() {}

    /**
     * @param methods  the references to offer, asked again each time the list opens
     * @param current  the reference held now, or blank
     * @param proposal what <i>Create method…</i> proposes, {@code Collect::body}
     * @param create   writes the method a class and a method name make, answering its reference or why not
     * @param blocked  why nothing can be created yet, or null when it can — asked at the click, so it sees a save
     *                 made since the row was drawn
     * @param picked   takes the reference chosen, blank for {@link #NONE}
     */
    static HBox of(StudioServices services, Supplier<List<String>> methods, String current, String proposal,
                   BiFunction<String, String, PluginValues.MethodWrite> create, Supplier<String> blocked,
                   Consumer<String> picked) {
        ComboBox<String> box = new ComboBox<>();
        box.setMaxWidth(Double.MAX_VALUE);
        box.getItems().setAll(items(methods.get(), current));
        box.setValue(current == null || current.isBlank() ? NONE : current);
        box.setOnShowing(e -> {
            String now = box.getValue();
            box.getItems().setAll(items(methods.get(), NONE.equals(now) ? "" : now));
        });
        box.valueProperty().addListener((o, was, now) -> {
            if (now == null || now.equals(was)) return;
            picked.accept(NONE.equals(now) ? "" : now);
        });

        Button add = new Button("Create method…");
        add.setTooltip(new Tooltip("Writes a new method in the bot's code and picks it here. Fill in its steps in "
                + "the editor."));
        add.setOnAction(e -> {
            String why = blocked.get();
            if (why != null) {
                refused(services, "No method", why);
                return;
            }
            prompt(services, proposal).ifPresent(named -> {
                PluginValues.MethodWrite written = create.apply(named.className(), named.method());
                if (!written.written()) {
                    refused(services, named.className() + "::" + named.method(), written.refusal());
                    return;
                }
                box.getItems().setAll(items(methods.get(), written.reference()));
                box.setValue(written.reference());
            });
        });

        HBox row = new HBox(6, box, add);
        HBox.setHgrow(box, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** {@link #NONE}, then {@code methods}, then {@code current} when the list no longer offers it. */
    static List<String> items(List<String> methods, String current) {
        List<String> items = new ArrayList<>();
        items.add(NONE);
        items.addAll(methods);
        if (current != null && !current.isBlank() && !items.contains(current)) items.add(current);
        return items;
    }

    /** The class a new method goes in and the method's name, as the create prompt took them. */
    record Named(String className, String method) {}

    /**
     * Asks for the class — one of the bot's, picked, or a new name typed — and the method's name, proposing
     * {@code proposal}'s two halves; empty when cancelled or either is not a Java name.
     */
    private static Optional<Named> prompt(StudioServices services, String proposal) {
        Named proposed = split(proposal);
        List<String> classes = services.pluginValues().classes();

        ComboBox<String> owner = new ComboBox<>();
        owner.setEditable(true);
        owner.getItems().setAll(classes);
        owner.setValue(proposed.className());   // a class of its own, Collect for Collect's body, until one is picked
        owner.setMaxWidth(Double.MAX_VALUE);
        TextField method = new TextField(proposed.method());

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Class"), owner);
        form.addRow(1, new Label("Method"), method);
        Label hint = new Label("Pick one of the bot's classes, or type a new name to create that class.");
        hint.setWrapText(true);
        form.add(hint, 0, 2, 2, 1);
        GridPane.setHgrow(owner, Priority.ALWAYS);

        Dialog<Named> dialog = new Dialog<>();
        services.theme().apply(dialog);
        dialog.initOwner(Modals.owner(services));
        dialog.setTitle("Create method");
        dialog.setHeaderText("Which class does the method go in, and what is it called?");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new Named(text(owner.getEditor().getText()), text(method.getText())) : null);
        javafx.application.Platform.runLater(method::requestFocus);
        Optional<Named> named = dialog.showAndWait().filter(n -> !n.className().isEmpty() || !n.method().isEmpty());
        if (named.isPresent() && !FlowNames.isMethodReference(named.get().className() + "::" + named.get().method())) {
            refused(services, "", "Both need a Java name, a letter first: a class like "
                    + proposed.className() + " and a method like " + proposed.method() + ".");
            return Optional.empty();
        }
        return named;
    }

    /** {@code Collect::body} as its class and method; a reference without {@code ::} is all method. */
    static Named split(String reference) {
        String whole = reference == null ? "" : reference.strip();
        int at = whole.indexOf("::");
        return at < 0 ? new Named("", whole) : new Named(whole.substring(0, at), whole.substring(at + 2));
    }

    private static String text(String typed) {
        return typed == null ? "" : typed.strip();
    }

    private static void refused(StudioServices services, String reference, String why) {
        Alert alert = services.theme().alert(Alert.AlertType.INFORMATION, why, ButtonType.OK);
        alert.initOwner(Modals.owner(services));
        alert.setTitle("Create method");
        alert.setHeaderText(reference.contains("::") ? reference + " was not written" : "No method was written");
        alert.showAndWait();
    }
}
