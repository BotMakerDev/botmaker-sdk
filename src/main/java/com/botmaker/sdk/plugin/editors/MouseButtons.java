package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.MouseButton;

import java.util.List;
import java.util.Optional;

/**
 * The drawn mouse's parts, and the SDK button a real click is. Pure. Each part is a themed toggle whose outline
 * is an SVG path ({@code -fx-shape}), placed on a 120×160 drawing: the body is the buttons, so there is nothing
 * to theme but what a toggle already has.
 */
final class MouseButtons {

    record Part(MouseButton button, String label, String shape, double x, double y, double w, double h) {}

    private static final String SIDE = "M0 4 Q0 0 4 0 L10 0 L10 30 L4 30 Q0 30 0 26 Z";

    private static final List<Part> PARTS = List.of(
            new Part(MouseButton.LEFT, "Left", "M50 0 L50 70 L0 70 L0 40 Q0 0 50 0 Z", 10, 8, 48, 70),
            new Part(MouseButton.RIGHT, "Right", "M0 0 Q50 0 50 40 L50 70 L0 70 Z", 62, 8, 48, 70),
            new Part(MouseButton.MIDDLE, "Wheel",
                    "M0 6 Q0 0 6 0 L10 0 Q16 0 16 6 L16 30 Q16 36 10 36 L6 36 Q0 36 0 30 Z", 52, 22, 16, 36),
            new Part(MouseButton.FORWARD, "Forward", SIDE, 0, 88, 10, 30),
            new Part(MouseButton.BACK, "Back", SIDE, 0, 122, 10, 30));

    private MouseButtons() {}

    static List<Part> parts() {
        return PARTS;
    }

    /** The SDK button a real press is; {@code NONE} and {@code null} are no button. */
    static Optional<MouseButton> of(javafx.scene.input.MouseButton button) {
        if (button == null) return Optional.empty();
        return switch (button) {
            case PRIMARY -> Optional.of(MouseButton.LEFT);
            case SECONDARY -> Optional.of(MouseButton.RIGHT);
            case MIDDLE -> Optional.of(MouseButton.MIDDLE);
            case BACK -> Optional.of(MouseButton.BACK);
            case FORWARD -> Optional.of(MouseButton.FORWARD);
            case NONE -> Optional.empty();
        };
    }
}
