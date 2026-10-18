package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 🗺 Screens' hand tools: a ⋯ menu on each input row and one on the screen, setting by hand what the assistant's
 * tools record by trying (the user's call, 2026-10-15). Each edit is {@link ScreenEdits}; what it answered is handed
 * to {@code done}, which says it and reads the map again.
 */
final class ScreenHands {

    private static final String SHOWS = "shows it (one more marker)";
    private static final String NEVER_SHOWS = "never shows it (where a look-alike does)";

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
        String field = row.field();
        Input input = graph.get().screen(constant).flatMap(s -> s.input(field)).orElse(row.input());
        boolean opens = graph.get().opens(constant, field).isPresent();
        // Each menu marks what the input is now.
        ToggleGroup did = new ToggleGroup();
        Menu result = new Menu("Result");
        result.getItems().addAll(
                pick("Untried", did, !opens && !input.isTried() && !input.isLeaving(), () -> set(constant, field,
                        ScreenEdits.untried(input))),
                pick("Tried, opens nothing", did, !opens && input.isTried(), () -> set(constant, field,
                        ScreenEdits.untried(input).tried())),
                pick("Leaves for a screen reached otherwise", did, !opens && input.isLeaving(), () -> set(constant,
                        field, ScreenEdits.untried(input).leaves())),
                pick("Opens a new screen…", did, opens, () -> newScreen(constant, field)),
                item("Leads to a screen on the map…", () -> addWay(constant, field)));
        CheckMenuItem info = new CheckMenuItem("Information only");
        info.setSelected(input.isInfo());
        info.setOnAction(e -> set(constant, field, ScreenEdits.info(input, info.isSelected())));
        CheckMenuItem sometimes = new CheckMenuItem("Comes and goes");
        sometimes.setSelected(input.comesAndGoes());
        sometimes.setOnAction(e -> set(constant, field, ScreenEdits.sometimes(input, sometimes.isSelected())));
        MenuItem never = item(input.never() == null ? "Never…" : "Not never", () -> {
            if (input.never() != null) set(constant, field, ScreenEdits.untried(input));
            else askText("Never clicked", "Why it is never clicked (it spends gems, real money)", "spends gems",
                    why -> set(constant, field, ScreenEdits.untried(input).never(why)));
        });
        MenuItem locked = item(input.lock() == null ? "Locked by…" : "Not locked", () -> {
            if (input.lock() != null) set(constant, field, ScreenEdits.untried(input));
            else askPicture("Locked by", "The picture proving it locked (a padlock on it)",
                    lock -> set(constant, field, ScreenEdits.untried(input)
                            .locked(ScreenEdits.picture(services, lock))));
        });
        MenuItem back = item("Use as the way back", () -> done.accept(ScreenEdits.useAsBack(services, constant,
                field)));
        MenuItem remove = item("Remove from this screen", () -> done.accept(ScreenEdits.setInput(services, constant,
                field, null)));
        MenuButton menu = new MenuButton("⋯");
        menu.setTooltip(new Tooltip("Set what " + row.label() + " does by hand"));
        Menu when = whileMenu("Only there while", ScreenTypes.sourceOf(input.when()),
                method -> done.accept(ScreenEdits.setInputWhen(services, constant, field, method)));
        menu.getItems().addAll(result, info, sometimes, when, never, locked, new SeparatorMenuItem(), back, remove);
        return menu;
    }

    /**
     * Asks which screen of the map {@code from}'s field {@code field} leads to, and records it as one more way into
     * that screen: what try_input writes for an input that led to a screen mapped already.
     */
    void addWay(String from, String field) {
        ScreenGraph map = graph.get();
        List<String> screens = map.screens().keySet().stream()
                .filter(c -> !c.equals(from) && !map.screens().get(c).isPopup()).toList();
        ComboBox<String> to = new ComboBox<>(FXCollections.observableArrayList(screens));
        to.setConverter(new StringConverter<>() {
            @Override
            public String toString(String constant) {
                return constant == null ? "" : map.shown(constant);
            }

            @Override
            public String fromString(String shown) {
                return screens.stream().filter(c -> map.shown(c).equals(shown)).findFirst().orElse(null);
            }
        });
        to.setVisibleRowCount(16);
        Modals.form(services, "Leads to…", new VBox(8, new Label(map.shown(from) + "'s " + field + " opens"), to),
                () -> {
                    if (to.getValue() == null) {
                        done.accept("Pick the screen it opens.");
                        return;
                    }
                    done.accept(ScreenEdits.addWay(services, to.getValue(), from, field));
                });
    }

    // --- a screen's ways in, looks and markers ---

    /**
     * The ⋯ menu of {@code to}'s way in from {@code from} by its field {@code field}, open only while {@code when}
     * (blank: always): when it is open, and taking it off the map.
     */
    MenuButton wayMenu(String to, String from, String field, String when) {
        MenuButton menu = new MenuButton("⋯");
        menu.setTooltip(new Tooltip("Edit this way in"));
        menu.getItems().addAll(whileMenu("Open only while", when, method -> done.accept(
                        ScreenEdits.setWayWhen(services, to, from, field, method))),
                item("Remove this way in", () -> done.accept(ScreenEdits.removeWay(services, to, from, field))));
        return menu;
    }

    /** The ⋯ menu of {@code constant}'s look {@code look}, shown only while {@code when} (blank: no method says). */
    MenuButton lookMenu(String constant, String look, String when) {
        MenuButton menu = new MenuButton("⋯");
        menu.setTooltip(new Tooltip("When " + look + " shows"));
        menu.getItems().add(whileMenu("Shows only while", when, method -> done.accept(
                ScreenEdits.setLookWhen(services, constant, look, method))));
        return menu;
    }

    /**
     * Asks for a picture the bot has and whether {@code constant} shows it or never does, then hands both to
     * {@code chosen}: the marker is matched over every other shot, off the FX thread, by its caller.
     */
    void askMarker(String constant, BiConsumer<String, Boolean> chosen) {
        // Only a picture the bot has: none is cut here.
        ComboBox<String> pick = new ComboBox<>(FXCollections.observableArrayList(ScreenEdits.pictures(services)));
        ChoiceBox<String> shows = new ChoiceBox<>(FXCollections.observableArrayList(SHOWS, NEVER_SHOWS));
        shows.setValue(SHOWS);
        Modals.form(services, "Add a marker to " + graph.get().shown(constant), new VBox(8,
                new Label("A picture the bot has"), pick, new Label("The screen"), shows), () -> {
            if (pick.getValue() != null && !pick.getValue().isBlank()) {
                chosen.accept(pick.getValue().strip(), NEVER_SHOWS.equals(shows.getValue()));
            }
        });
    }

    // --- a link ---

    /**
     * The right-click menu of {@code link} on the map: its picture shown ({@code show}), and for a way in, the
     * method it is open only while. A way back has no condition: it is the screen's own.
     */
    ContextMenu linkMenu(Link link, Runnable show) {
        ContextMenu menu = new ContextMenu(item(link.via() == null ? "About " + link.label() : "Show "
                + link.label(), show));
        if (!link.back() && link.field() != null) {
            menu.getItems().add(whileMenu("Open only while", link.when(), method -> done.accept(
                    ScreenEdits.setWayWhen(services, link.to(), link.from(), link.field(), method))));
        }
        return menu;
    }

    /**
     * A menu of the bot's {@code boolean} methods, {@code current} ticked: picking one hands {@code set} its
     * reference, Always hands it blank, and New method… writes one returning false for the author to fill in.
     */
    private Menu whileMenu(String title, String current, Consumer<String> set) {
        Menu menu = new Menu(title + (current.isEmpty() ? "…" : ": " + current));
        ToggleGroup group = new ToggleGroup();
        RadioMenuItem always = new RadioMenuItem("Always (no method)");
        always.setToggleGroup(group);
        always.setSelected(current.isEmpty());
        always.setOnAction(e -> set.accept(""));
        menu.getItems().add(always);
        List<String> methods = services.pluginValues() == null ? List.of() : services.pluginValues().booleanMethods();
        if (!methods.isEmpty()) menu.getItems().add(new SeparatorMenuItem());
        for (String method : methods) {
            RadioMenuItem one = new RadioMenuItem(method);
            one.setToggleGroup(group);
            one.setSelected(method.equals(current));
            one.setOnAction(e -> set.accept(method));
            menu.getItems().add(one);
        }
        if (!current.isEmpty() && !methods.contains(current)) {
            // Written by hand, or its method since renamed: shown, so the user sees what the Java says.
            RadioMenuItem kept = new RadioMenuItem(current + " (not found)");
            kept.setToggleGroup(group);
            kept.setSelected(true);
            kept.setDisable(true);
            menu.getItems().add(kept);
        }
        menu.getItems().addAll(new SeparatorMenuItem(), item("New method…", () -> newMethod(set)));
        return menu;
    }

    /** Asks for a class and a name, writes {@code public static boolean name() { return false; }} and sets it. */
    private void newMethod(Consumer<String> set) {
        if (services.pluginValues() == null) return;
        List<String> classes = services.pluginValues().classes();
        ComboBox<String> holder = new ComboBox<>(FXCollections.observableArrayList(classes));
        holder.setEditable(true);
        if (!classes.isEmpty()) holder.setValue(classes.getFirst());
        TextField name = new TextField();
        name.setPromptText("eventOn");
        Modals.form(services, "New method", new VBox(8, new Label("The class it goes in"), holder,
                new Label("Its name: it answers true while the way is open"), name), () -> {
            String className = holder.getValue() == null ? "" : holder.getValue().strip();
            PluginValues.MethodWrite wrote = services.pluginValues().createBooleanMethod(className,
                    name.getText().strip());
            if (!wrote.written()) {
                done.accept("Not written: " + wrote.refusal());
                return;
            }
            set.accept(wrote.reference());
        });
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
        MapScreen screen = graph.get().screens().get(constant);
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

    /** A screen under {@code parent}, opened by its field {@code via}, or by one of its inputs picked when null. */
    private void newScreen(String parent, String via) {
        TextField name = new TextField();
        name.setPromptText("its name, as the game shows it");
        // Its looks' inputs too: a button only one look of the screen has opens screens as well.
        ComboBox<String> input = new ComboBox<>(FXCollections.observableArrayList(graph.get().fields(parent)));
        input.setValue(via);
        input.setDisable(via != null);
        VBox body = new VBox(8, new Label("Name"), name, new Label("Opened from " + graph.get().shown(parent)
                + " by"), input);
        Modals.form(services, "New screen", body, () -> {
            if (input.getValue() == null) {
                done.accept("Pick the input that opens it.");
                return;
            }
            done.accept(ScreenEdits.newScreen(services, parent, name.getText(), input.getValue(), null));
        });
    }

    // --- pieces ---

    private void set(String constant, String field, Input now) {
        done.accept(ScreenEdits.setInput(services, constant, field, now));
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

    /** A choice of {@code group}, ticked when it is what the input is now. */
    private static RadioMenuItem pick(String text, ToggleGroup group, boolean now, Runnable onPress) {
        RadioMenuItem item = new RadioMenuItem(text);
        item.setToggleGroup(group);
        item.setSelected(now);
        item.setOnAction(e -> onPress.run());
        return item;
    }

    private static MenuItem item(String text, Runnable onPress) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> onPress.run());
        return item;
    }
}
