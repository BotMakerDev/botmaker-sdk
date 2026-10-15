package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Window;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * The map drawn off screen as 🗺 Screens draws it, for read_map's {@code show}: apart from {@link MapReport} so
 * declaring the tool links no JavaFX.
 */
final class MapDrawing {

    /** The widest the drawing is sent; a bigger map is shrunk to it. */
    private static final int MOST_WIDTH = 1800;

    private MapDrawing() {}

    /** {@code graph} as the window draws it, in Studio's theme, shrunk to {@link #MOST_WIDTH}. On the FX thread. */
    static BufferedImage of(StudioServices services, ScreenGraph graph, Map<String, String> stages) {
        ScreenCanvas canvas = new ScreenCanvas(services.resourcesDir());
        canvas.loadNow();
        canvas.show(ScreenCards.cards(graph, stages), ScreenCards.links(graph), ScreenCards.root(graph).orElse(null));
        Scene scene = new Scene(canvas.graph(), 1600, 1000);
        Window studio = Modals.owner(services);
        if (studio != null && studio.getScene() != null) {
            // Studio's tokens (-bm-*) live on its own scene's sheets and root classes: the drawing takes its theme.
            scene.getStylesheets().addAll(studio.getScene().getStylesheets());
            if (studio.getScene().getRoot() != null) {
                scene.getRoot().getStyleClass().addAll(studio.getScene().getRoot().getStyleClass());
            }
        }
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        WritableImage image = canvas.graph().image();
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] argb = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        BufferedImage whole = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        whole.setRGB(0, 0, width, height, argb, 0, width);
        if (width <= MOST_WIDTH) return whole;
        int shrunkHeight = Math.max(1, height * MOST_WIDTH / width);
        BufferedImage shrunk = new BufferedImage(MOST_WIDTH, shrunkHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = shrunk.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(whole, 0, 0, MOST_WIDTH, shrunkHeight, null);
        g.dispose();
        return shrunk;
    }
}
