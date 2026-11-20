// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ElementSaves;
import com.botmaker.sdk.plugin.pictures.ResourceManagerDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 🧩 Elements, the view 🗺 Screens' rail shows (2026-11-20, the user's pick: one tab in place of 🖼 Pictures): all the
 * bot finds things by. Its pictures, matched on the screen, are the picture manager as it was; its tree elements, the
 * {@code Elements} class found in a page's or an app's tree, are listed with their shot, what finds them and the
 * screens whose inputs press them. Filter chips show either or both. Read on the FX thread each time it is shown.
 */
final class ElementsView {

    /** What the chips show. */
    enum Show {
        ALL("all", "All"),
        PICTURES("pictures", "Pictures"),
        TREE("tree", "Tree elements");

        private final String id;
        private final String displayName;

        Show(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        String id() {
            return id;
        }

        String displayName() {
            return displayName;
        }
    }

    /** One tree element as its row says it: its constant, what finds it, its shot's path or null, where it is pressed. */
    record Row(String name, String found, String shot, List<String> on) {

        /** Whether {@code query} (lower case) names it, its words or a screen it is on. */
        boolean matches(String query) {
            if (query.isEmpty()) return true;
            return (name + " " + found + " " + String.join(" ", on)).toLowerCase(Locale.ROOT).contains(query);
        }
    }

    private static final double THUMB = 44;

    private ElementsView() {}

    static Node view(StudioServices services, Supplier<ScreenGraph> graph, Supplier<Window> window) {
        Node pictures = ResourceManagerDialog.view(services, window);
        VBox treeRows = new VBox(6);
        TextField search = new TextField();
        search.setPromptText("🔍 filter tree elements");
        List<Row> rows = new ArrayList<>();
        Runnable[] read = new Runnable[1];
        Runnable draw = () -> draw(services, treeRows, rows, search.getText(), () -> read[0].run());
        read[0] = () -> {
            rows.clear();
            rows.addAll(rows(ElementSaves.elements(services.pluginValues()), graph.get()));
            draw.run();
        };
        search.textProperty().addListener((o, was, is) -> draw.run());

        Label treeHead = heading("Tree elements", "found in the page's or the app's tree, never by a picture");
        ScrollPane treeScroll = new ScrollPane(treeRows);
        treeScroll.setFitToWidth(true);
        VBox tree = new VBox(8, treeHead, search, treeScroll);
        tree.setPadding(new Insets(8));
        VBox.setVgrow(treeScroll, Priority.ALWAYS);
        Label picturesHead = heading("Pictures", "matched on the screen's frame");
        VBox picturesBox = new VBox(8, picturesHead, pictures);
        picturesBox.setPadding(new Insets(8));
        VBox.setVgrow(pictures, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        ToggleGroup chips = new ToggleGroup();
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 8, 0, 8));
        for (Show show : Show.values()) {
            ToggleButton chip = new ToggleButton(show.displayName());
            chip.setToggleGroup(chips);
            chip.setUserData(show);
            bar.getChildren().add(chip);
        }
        chips.selectedToggleProperty().addListener((o, was, is) -> {
            // A chip clicked again stays picked: one of the three is always shown.
            if (is == null) {
                chips.selectToggle(was);
                return;
            }
            root.setCenter(switch ((Show) is.getUserData()) {
                case ALL -> both(tree, picturesBox);
                case PICTURES -> picturesBox;
                case TREE -> tree;
            });
        });
        root.setTop(bar);
        chips.selectToggle(chips.getToggles().getFirst());
        boolean[] shownOnce = {false};
        root.sceneProperty().addListener((o, was, is) -> {
            if (is == null) return;
            if (shownOnce[0]) read[0].run();
            shownOnce[0] = true;
        });
        read[0].run();
        return root;
    }

    /** Both lists, the tree elements above, each taking half the height. */
    private static Node both(VBox tree, VBox pictures) {
        VBox both = new VBox(tree, pictures);
        VBox.setVgrow(tree, Priority.ALWAYS);
        VBox.setVgrow(pictures, Priority.ALWAYS);
        tree.setPrefHeight(1);
        pictures.setPrefHeight(1);
        return both;
    }

    /** {@code elements} as rows, each with the screens of {@code graph} whose inputs press it. */
    static List<Row> rows(Map<String, Element> elements, ScreenGraph graph) {
        List<Row> rows = new ArrayList<>();
        elements.forEach((name, element) -> {
            List<String> on = new ArrayList<>();
            if (graph != null) {
                graph.screens().forEach((constant, screen) -> {
                    if (screen instanceof MapScreen map && presses(map, element)) on.add(graph.shown(constant));
                });
            }
            rows.add(new Row(name, found(element), element.shot(), List.copyOf(on)));
        });
        return List.copyOf(rows);
    }

    /** Whether an input of {@code screen} presses {@code element}, its shot aside. */
    private static boolean presses(MapScreen screen, Element element) {
        Element bare = element.shot(null);
        for (Input input : screen.inputs().values()) {
            if (input instanceof com.botmaker.sdk.api.screens.Button button && button.element() != null
                    && button.element().shot(null).equals(bare)) {
                return true;
            }
        }
        return false;
    }

    /** What finds {@code element}, as a tester reads it: {@code link "Home"}, {@code #email}, {@code a text field}. */
    static String found(Element element) {
        List<String> parts = new ArrayList<>();
        if (element.role() != null) parts.add(element.role().id());
        if (element.label() != null) parts.add("\"" + element.label().text() + "\"");
        else if (element.text() != null) parts.add("text \"" + element.text().text() + "\"");
        if (element.id() != null) parts.add("#" + element.id());
        if (parts.isEmpty()) parts.add(element.isPassword() ? "a password field" : element.isField() ? "a text field"
                : "by its place");
        if (element.index() != null) parts.add("[" + element.index() + "]");
        return String.join(" ", parts);
    }

    private static void draw(StudioServices services, VBox box, List<Row> rows, String query, Runnable reread) {
        Path resources = services.resourcesDir();
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        box.getChildren().clear();
        List<Row> shown = rows.stream().filter(r -> r.matches(q)).toList();
        if (shown.isEmpty()) {
            box.getChildren().add(new Label(rows.isEmpty() ? "The bot has no tree element yet: Discover adds the ones"
                    + " a page's inputs are found by." : "No tree element matches."));
        }
        for (Row row : shown) box.getChildren().add(row(services, row, resources, reread));
    }

    /**
     * The row's ⋯: Rename… renames the constant by binding, every use with it; Delete removes it, refused while an
     * input or a step still names it, and its shot with it. Either reads the list again after.
     */
    private static MenuButton more(StudioServices services, Row row, Runnable reread) {
        MenuItem rename = new MenuItem("Rename…");
        rename.setOnAction(e -> {
            TextInputDialog ask = new TextInputDialog(row.name());
            ask.setTitle("Rename " + row.name());
            ask.setHeaderText(null);
            ask.setContentText("New name (UPPER_SNAKE):");
            services.dialogs().ownerWindow().ifPresent(ask::initOwner);
            ask.showAndWait().map(String::strip).filter(n -> !n.isEmpty() && !n.equals(row.name())).ifPresent(n -> {
                Optional<String> refused = services.pluginValues().rename(SdkValues.ELEMENTS, Member.of(row.name()), n);
                services.status(refused.map(why -> "Not renamed: " + why).orElse("Renamed " + row.name() + " to " + n
                        + "."));
                reread.run();
            });
        });
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> {
            Optional<String> refused = services.pluginValues().remove(SdkValues.ELEMENTS, Member.of(row.name()));
            Path shot = shotFile(services.resourcesDir(), row.shot());
            if (refused.isEmpty() && shot != null) {
                try {
                    Files.deleteIfExists(shot);
                } catch (IOException ex) {
                    services.status("Deleted " + row.name() + "; its shot stays: " + ex.getMessage());
                    reread.run();
                    return;
                }
            }
            services.status(refused.map(why -> "Not deleted: " + why).orElse("Deleted " + row.name() + "."));
            reread.run();
        });
        MenuButton more = new MenuButton("⋯", null, rename, delete);
        more.getStyleClass().add("flat");
        return more;
    }

    /**
     * The shot's file under {@code resources}, or null: a shot written as an absolute path, or one that leads out of the
     * folder or through a link (a bot cloned from someone else names its own), is never touched.
     */
    static Path shotFile(Path resources, String shot) {
        if (resources == null || shot == null || shot.isBlank()) return null;
        Path relative = Path.of(shot);
        if (relative.isAbsolute()) return null;
        Path base = resources.toAbsolutePath().normalize();
        Path file = base.resolve(relative).normalize();
        if (!file.startsWith(base) || file.equals(base) || Files.isSymbolicLink(file)) return null;
        return file;
    }

    private static Node row(StudioServices services, Row row, Path resources, Runnable reread) {
        Label name = new Label(row.name());
        name.getStyleClass().add("screen-card-name");
        Label found = new Label("tree · " + row.found());
        found.getStyleClass().add("screen-panel-path");
        Label on = new Label(row.on().isEmpty() ? "pressed by no input" : "on " + String.join(", ", row.on()));
        on.getStyleClass().add("screen-panel-path");
        on.setWrapText(true);
        VBox text = new VBox(2, name, found, on);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox line = new HBox(10, thumb(row.shot(), resources), text, more(services, row, reread));
        line.setAlignment(Pos.CENTER_LEFT);
        line.setPadding(new Insets(6, 8, 6, 6));
        line.getStyleClass().add("screen-card");
        return line;
    }

    /** The element's shot, or a dash when it has none on disk. */
    private static Node thumb(String shot, Path resources) {
        StackPane box = new StackPane();
        box.getStyleClass().add("screen-card-thumb");
        box.setMinSize(THUMB, THUMB);
        box.setPrefSize(THUMB, THUMB);
        box.setMaxSize(THUMB, THUMB);
        Path file = shot == null || resources == null ? null : resources.resolve(shot);
        if (file != null && Files.isRegularFile(file)) {
            ImageView view = new ImageView(new Image(file.toUri().toString(), THUMB * 2, 0, true, true));
            view.setPreserveRatio(true);
            view.setFitWidth(THUMB - 4);
            view.setFitHeight(THUMB - 4);
            box.getChildren().add(view);
        } else {
            Label none = new Label("–");
            none.getStyleClass().add("screen-thumb-none");
            box.getChildren().add(none);
        }
        return box;
    }

    private static Label heading(String title, String what) {
        Label head = new Label(title + " — " + what);
        head.getStyleClass().add("screen-panel-path");
        return head;
    }
}
