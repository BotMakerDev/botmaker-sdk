package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.plugin.map.MapGuide.Part;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * A part of 🗺's side panel or of the picked input's editors, folded by its title, its caption always shown under it
 * (the user's call, 2026-10-22: the panel's buttons each need their own visible explanation, and there were too many).
 * Folded or open as this computer last left that part, else as {@link Part#opensFor} says for the input's kind.
 */
final class PanelParts {

    /** Each part's fold, per viewer: not per project. */
    private static final Preferences PREFS = Preferences.userNodeForPackage(PanelParts.class);

    private PanelParts() {}

    /** {@code part} over {@code body}, for {@code input} (null: a part of the screen's panel). */
    static TitledPane of(Part part, Input input, List<Node> body) {
        Label caption = new Label(part.caption());
        caption.setWrapText(true);
        caption.setMinHeight(Region.USE_PREF_SIZE);
        caption.getStyleClass().add("screen-part-caption");
        VBox inside = new VBox(6, caption);
        inside.getChildren().addAll(body);
        TitledPane pane = new TitledPane(part.title(), inside);
        pane.setAnimated(false);
        pane.getStyleClass().add("screen-part");
        pane.setExpanded(open(part, input));
        pane.expandedProperty().addListener((o, was, is) -> remember(part, is));
        return pane;
    }

    static TitledPane of(Part part, Input input, Node... body) {
        return of(part, input, List.of(body));
    }

    /**
     * Whether {@code part} shows open: as it was last left, else as the input's kind needs. A part that opens by what
     * the button is (its field, its drag) always follows it: opened once for a field, it would open on every button.
     */
    private static boolean open(Part part, Input input) {
        if (part.followsKind()) return part.opensFor(input);
        try {
            return PREFS.getBoolean(key(part), part.opensFor(input));
        } catch (IllegalStateException | SecurityException unreadable) {
            return part.opensFor(input);
        }
    }

    private static void remember(Part part, boolean open) {
        if (part.followsKind()) return;
        try {
            PREFS.putBoolean(key(part), open);
        } catch (IllegalStateException | SecurityException unwritable) {
            // Not remembered: the next panel opens it as the kind needs.
        }
    }

    private static String key(Part part) {
        return "map.part." + part.name().toLowerCase(Locale.ROOT);
    }
}
