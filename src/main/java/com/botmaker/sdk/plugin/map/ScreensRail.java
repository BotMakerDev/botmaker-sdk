package com.botmaker.sdk.plugin.map;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 🗺 Screens' left rail (2026-11-09, spec screens-rail goal 1, layout B): one button a view, one view shown beside it.
 * A view is built the first time it is shown and kept, so a view shown again is where it was left.
 */
final class ScreensRail {

    /** What the rail switches between, in its order top to bottom. */
    enum View {
        MAP("🗺", "Map", "The game's screens as a graph, and a tab per screen opened"),
        GOALS("🎯", "Goals", "The goals of a plan and the actions serving each, in the order a run would try them"),
        EXPLORE("🔭", "Explore", "A run that explores the game, and what it found"),
        PICTURES("🖼", "Pictures", "Rename, retag, replace, delete, import and export the pictures the bot looks for");

        private final String icon;
        private final String displayName;
        private final String tooltip;

        View(String icon, String displayName, String tooltip) {
            this.icon = icon;
            this.displayName = displayName;
            this.tooltip = tooltip;
        }

        String displayName() {
            return displayName;
        }
    }

    private final BorderPane root = new BorderPane();
    private final ToggleGroup group = new ToggleGroup();
    private final Map<View, ToggleButton> buttons = new EnumMap<>(View.class);
    private final Map<View, Node> built = new EnumMap<>(View.class);
    private final Function<View, Node> build;
    private View showing;

    /** A rail whose views {@code build} makes, each once; the Map view shows first. */
    ScreensRail(Function<View, Node> build) {
        this.build = build;
        VBox rail = new VBox();
        rail.getStyleClass().add("screens-rail");
        rail.setAlignment(Pos.TOP_CENTER);
        for (View view : View.values()) {
            ToggleButton button = new ToggleButton(view.icon + "\n" + view.displayName);
            button.getStyleClass().add("screens-rail-button");
            button.setTextAlignment(TextAlignment.CENTER);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setTooltip(new Tooltip(view.displayName + " — " + view.tooltip));
            button.setToggleGroup(group);
            button.setUserData(view);
            buttons.put(view, button);
            rail.getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((o, was, is) -> {
            // A press on the view shown leaves it shown: one is always.
            if (is == null) {
                if (was != null) group.selectToggle(was);
                return;
            }
            show((View) is.getUserData());
        });
        root.setLeft(rail);
        show(View.MAP);
    }

    Node node() {
        return root;
    }

    View showing() {
        return showing;
    }

    /**
     * Shows {@code view}, built now if it never was, and presses its button. Built before anything moves, so a
     * build that throws leaves the rail on the view it was showing, and the next press tries again.
     */
    void show(View view) {
        if (view == showing) return;
        Node node = built.computeIfAbsent(view, build);
        showing = view;
        buttons.get(view).setSelected(true);
        root.setCenter(node);
    }
}
