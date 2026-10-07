package com.botmaker.sdk.plugin.screen;

import com.botmaker.shared.capture.ScreenGeometry;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The monitor geometry both halves of capture need.
 *
 * <p>It crops a desktop grab to the monitor a user picked in the chooser; its callers are
 * {@link ScreenOverlay} and {@link DesktopSource}.
 *
 * <p>It stays its own class rather than folding into either, because it is the one piece of arithmetic in
 * this package that is easy to get subtly wrong and impossible to see wrong: an off-by-one in the scale
 * factor produces a crop that looks right and matches nothing.
 */
final class Screens {

    private Screens() {
    }

    /** The whole virtual-desktop bounds in JavaFX logical coordinates (union of every screen). */
    static Rectangle2D virtualScreenBounds(List<Screen> screens) {
        double minX = screens.stream().mapToDouble(s -> s.getBounds().getMinX()).min().orElse(0);
        double minY = screens.stream().mapToDouble(s -> s.getBounds().getMinY()).min().orElse(0);
        double maxX = screens.stream().mapToDouble(s -> s.getBounds().getMaxX()).max().orElse(0);
        double maxY = screens.stream().mapToDouble(s -> s.getBounds().getMaxY()).max().orElse(0);
        return new Rectangle2D(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    /**
     * Crops the full-desktop {@code desktop} image to the pixel region of {@code target}: each screen's JavaFX
     * logical bounds times its own output scale ({@link ScreenGeometry}), so a layout of mixed scales crops the
     * right monitor too. The offset used to be scaled by the target's scale alone.
     */
    static BufferedImage cropToScreen(BufferedImage desktop, List<Screen> screens, Screen target) {
        return ScreenGeometry.crop(desktop, screens.stream().map(Screens::geometry).toList(), geometry(target));
    }

    private static ScreenGeometry.Screen geometry(Screen screen) {
        Rectangle2D b = screen.getBounds();
        return new ScreenGeometry.Screen(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight(),
                screen.getOutputScaleX(), screen.getOutputScaleY());
    }
}
