package com.botmaker.sdk.plugin.screen;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.awt.Rectangle;

/**
 * A frame already grabbed, as the overlay's source (2026-09-26): the pick is made on these frozen pixels, placed
 * where they came from, so a point chosen on a game window is chosen on exactly what was captured. The overlay
 * reports picks inside the frame ({@code 0,0} its top-left); {@link PickSpace} adds the origin back when the
 * call wants desktop pixels.
 */
public record FrameShotSource(EditorFrame frame) implements ShotSource {

    @Override
    public Grab grab(Window owner) {
        return new Grab(new ScreenShot(frame.image(), logical(frame.bounds()), false, false), null);
    }

    @Override
    public String title() {
        return null;
    }

    /**
     * {@code bounds} in the logical pixels a stage is placed in. A grab is in device pixels; on a scaled
     * screen the two differ by the screen's output scale, and the overlay scales its picks back through the
     * image, so only the placement needs converting.
     */
    static Rectangle2D logical(Rectangle bounds) {
        double scale = Screen.getScreens().stream()
                .filter(s -> s.getBounds().contains(bounds.x / s.getOutputScaleX(), bounds.y / s.getOutputScaleY()))
                .findFirst().map(Screen::getOutputScaleX).orElse(1.0);
        return new Rectangle2D(bounds.x / scale, bounds.y / scale, bounds.width / scale, bounds.height / scale);
    }
}
