package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 🗺 Screens' hand tools: a ⋯ menu on each input row and one on the screen, setting by hand what the assistant's
 * tools record by trying (the user's call, 2026-10-15). Each edit is {@link ScreenEdits}; what it answered is handed
 * to {@code done}, which says it and reads the map again.
 */
final class ScreenHands {

    private final StudioServices services;
    private final Supplier<ScreenGraph> graph;
    private final Consumer<String> done;

    /** @param done what an edit answered, on the FX thread, once it wrote */
    ScreenHands(StudioServices services, Supplier<ScreenGraph> graph, Consumer<String> done) {
        this.services = services;
        this.graph = graph;
        this.done = done;
    }

    // --- an input ---

    /** The ⋯ menu of {@code row}, an input of {@code constant}. */
    MenuButton rowMenu(String constant, Row row) {
        Input input = row.input();
        Menu result = new Menu("Result");
        result.getItems().addAll(
                item("Untried", () -> set(constant, input, ScreenEdits.untried(input))),
                item("Changes this screen in place", () -> set(constant, input,
                        ScreenEdits.untried(input).did(Did.IN_PLACE))),
                item("No visible effect", () -> set(constant, input, ScreenEdits.untried(input).did(Did.NONE))),
                item("Leaves this screen", () -> set(constant, input, ScreenEdits.untried(input).did(Did.LEAVES))),
                item("Opens a new screen…", () -> newScreen(constant, input)));
        Menu kind = new Menu("Kind");
        kind.getItems().add(item("None", () -> set(constant, input, ScreenEdits.unkinded(input))));
        for (Kind k : Kind.values()) {
            kind.getItems().add(item(label(k.name()), () -> set(constant, input, input.kind(k))));
        }
        CheckMenuItem sometimes = new CheckMenuItem("Comes and goes");
        sometimes.setSelected(input.comesAndGoes());
        sometimes.setOnAction(e -> set(constant, input, ScreenEdits.sometimes(input, sometimes.isSelected())));
        MenuItem never = item(input.never() == null ? "Never…" : "Not never", () -> {
            if (input.never() != null) set(constant, input, ScreenEdits.untried(input).kind(input.kind()));
            else askText("Never clicked", "Why it is never clicked (it spends gems, real money)", "spends gems",
                    why -> set(constant, input, ScreenEdits.untried(input).never(why)));
        });
        MenuItem locked = item(input.lock() == null ? "Locked by…" : "Not locked", () -> {
            if (input.lock() != null) set(constant, input, ScreenEdits.untried(input));
            else askPicture("Locked by", "The picture proving it locked (a padlock on it)",
                    lock -> set(constant, input, ScreenEdits.untried(input)
                            .locked(ScreenEdits.picture(services, lock))));
        });
        MenuItem back = item("Use as the way back", () -> done.accept(ScreenEdits.useAsBack(services, constant,
                input)));
        MenuItem remove = item("Remove from this screen", () -> done.accept(ScreenEdits.setInput(services, constant,
                input, null)));
        MenuButton menu = new MenuButton("⋯");
        menu.setTooltip(new Tooltip("Set what " + row.label() + " does by hand"));
        menu.getItems().addAll(result, kind, sometimes, never, locked, new SeparatorMenuItem(), back, remove);
        return menu;
    }

    // --- a screen ---

    /** The ⋯ Screen menu of {@code constant}; {@code retake} takes its shot again, off the FX thread. */
    MenuButton screenMenu(String constant, Consumer<String> retake) {
        MenuButton menu = new MenuButton("⋯ Screen");
        menu.setTooltip(new Tooltip("Edit " + graph.get().shown(constant) + " by hand"));
        menu.getItems().addAll(screenItems(constant, retake));
        return menu;
    }

    /** The ⋯ Screen menu's items, for it and for the card's right-click menu. */
    List<MenuItem> screenItems(String constant, Consumer<String> retake) {
        GameScreen screen = graph.get().screens().get(constant);
        String shown = graph.get().shown(constant);
        MenuItem rename = item("Rename…", () -> askText("Rename " + shown, "The name the game shows", shown,
                name -> done.accept(ScreenEdits.rename(services, constant, name))));
        CheckMenuItem popup = new CheckMenuItem("Popup (opens on its own)");
        popup.setSelected(screen != null && screen.isPopup());
        popup.setOnAction(e -> done.accept(ScreenEdits.setPopup(services, constant, popup.isSelected())));
        MenuItem shot = item("Retake its shot from the game", () -> retake.accept(constant));
        MenuItem place = item("Add pictures the bot has…", () -> placePictures(constant));
        MenuItem child = item("New screen from here…", () -> newScreen(constant, null));
        MenuItem notScreen = item("Not a screen (its way in changes the one above)", () -> done.accept(
                ScreenEdits.notAScreen(new PanelContext(services, CaptureValue.current(services)), constant)));
        MenuItem delete = item("Delete…", () -> confirm("Delete " + shown, "Delete " + shown + " from the map? Its "
                + "pictures stay.", () -> done.accept(ScreenEdits.delete(services, constant))));
        return List.of(rename, popup, shot, new SeparatorMenuItem(), place, child, new SeparatorMenuItem(),
                notScreen, delete);
    }

    /** Adds pictures the bot has to {@code constant}: as inputs, its marker or its way back. */
    private void placePictures(String constant) {
        ListView<String> list = new ListView<>(FXCollections.observableArrayList(ScreenEdits.pictures(services)));
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setPrefHeight(320);
        ChoiceBox<String> as = new ChoiceBox<>(FXCollections.observableArrayList("Inputs", "Marker", "Way back"));
        as.setValue("Inputs");
        VBox body = new VBox(8, new Label("Pictures (Ctrl+click for several)"), list, new Label("Add them as"), as);
        Modals.form(services, "Add pictures to " + graph.get().shown(constant), body, () -> {
            List<String> picked = List.copyOf(list.getSelectionModel().getSelectedItems());
            if (picked.isEmpty()) return;
            PanelContext context = new PanelContext(services, null);
            done.accept(switch (as.getValue()) {
                case "Marker" -> ScreenEdits.place(context, constant, List.of(), picked.getFirst(), null);
                case "Way back" -> ScreenEdits.place(context, constant, List.of(), null, picked.getFirst());
                default -> ScreenEdits.place(context, constant, picked, null, null);
            });
        });
    }

    /** A screen under {@code parent}, opened by {@code via}, or by one of its inputs picked when null. */
    private void newScreen(String parent, Input via) {
        TextField name = new TextField();
        name.setPromptText("its name, as the game shows it");
        GameScreen on = graph.get().screens().get(parent);
        ComboBox<Input> input = new ComboBox<>(FXCollections.observableArrayList(on == null ? List.of()
                : on.inputs()));
        input.setValue(via);
        input.setDisable(via != null);
        input.setConverter(new StringConverter<>() {
            @Override
            public String toString(Input i) {
                return i == null ? "" : i.label();
            }

            @Override
            public Input fromString(String s) {
                return null;
            }
        });
        VBox body = new VBox(8, new Label("Name"), name, new Label("Opened from " + graph.get().shown(parent)
                + " by"), input);
        Modals.form(services, "New screen", body, () -> {
            if (input.getValue() == null) {
                done.accept("Pick the input that opens it.");
                return;
            }
            done.accept(ScreenEdits.newScreen(services, parent, name.getText(), input.getValue()));
        });
    }

    // --- pieces ---

    private void set(String constant, Input old, Input now) {
        done.accept(ScreenEdits.setInput(services, constant, old, now));
    }

    private void askText(String title, String prompt, String start, Consumer<String> answer) {
        TextField field = new TextField(start);
        Modals.form(services, title, new VBox(8, new Label(prompt), field), () -> {
            if (!field.getText().isBlank()) answer.accept(field.getText().strip());
        });
    }

    private void askPicture(String title, String prompt, Consumer<String> answer) {
        ComboBox<String> pick = new ComboBox<>(FXCollections.observableArrayList(ScreenEdits.pictures(services)));
        pick.setEditable(true);
        Modals.form(services, title, new VBox(8, new Label(prompt), pick), () -> {
            if (pick.getValue() != null && !pick.getValue().isBlank()) answer.accept(pick.getValue().strip());
        });
    }

    private void confirm(String title, String question, Runnable yes) {
        Label said = new Label(question);
        said.setWrapText(true);
        Modals.form(services, title, new VBox(said), yes);
    }

    private static MenuItem item(String text, Runnable onPress) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> onPress.run());
        return item;
    }

    private static String label(String constant) {
        String lower = constant.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
