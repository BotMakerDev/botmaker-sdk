package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Rect;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

import java.util.Map;

/**
 * Boxes drawn over a game's frame, in the frame's pixels times {@code scale}: what the live board draws on a screen
 * tab, and the Explore view on its <em>Now</em> frame (2026-11-09), one drawing for both.
 */
final class FrameBoxes {

    private FrameBoxes() {
    }

    /** {@code box} at {@code scale}, styled {@code style}. */
    static Rectangle box(Rect box, double scale, String style) {
        Rectangle drawn = new Rectangle(box.x() * scale, box.y() * scale, box.width() * scale, box.height() * scale);
        drawn.getStyleClass().add(style);
        return drawn;
    }

    /** Each readout's area, by name, boxed into {@code onto} with its name above it (under it at the frame's top). */
    static void areas(Pane onto, Map<String, Rect> areas, double scale) {
        areas.forEach((name, box) -> {
            onto.getChildren().add(box(box, scale, "screen-live-area"));
            Label named = new Label(name);
            named.getStyleClass().add("screen-live-area-name");
            named.setManaged(false);
            onto.getChildren().add(named);
            named.applyCss();
            named.autosize();
            double above = box.y() * scale - named.getHeight();
            named.relocate(box.x() * scale, above >= 0 ? above : (box.y() + box.height()) * scale);
        });
    }
}
