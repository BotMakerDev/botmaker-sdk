package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the Phase-3 click-routing seam: {@link com.botmaker.sdk.api.vision.ImageClicker} must
 * dispatch its click through {@link CaptureSource#click(Point)} — <em>not</em> straight to {@code Mouse} /
 * the native controller — so an emulator source can override it into an {@code adb input tap}.
 *
 * <p>The test uses a fake, capture-only source that records the click instead of touching the desktop, which
 * doubles as a headless guard: if routing regressed to {@code Mouse.click}, the native input path would run
 * (and there would be no recorded click to assert). It also checks that a {@link CaptureSource#region(Rect)}
 * of the source still delegates its click to the underlying surface. Everything is self-generated (no image
 * fixture), so it is deterministic and display-free — only OpenCV template matching runs.
 */
class ImageClickerRoutingTest {

    private static final int TEMPLATE_SIZE = 48;

    /** A CaptureSource that serves a fixed image and records where it was clicked (origin (0,0), like an emulator). */
    private static final class RecordingSource implements CaptureSource {
        private final BufferedImage frame;
        Point clicked;

        RecordingSource(BufferedImage frame) {
            this.frame = frame;
        }

        @Override public BufferedImage capture() {
            return frame;
        }

        @Override public Point origin() {
            return new Point(0, 0);
        }

        @Override public void click(Point p) {
            this.clicked = p;
        }
    }

    /** A small, deterministic, well-textured patch that OpenCV can locate uniquely inside noise. */
    private static BufferedImage templatePatch() {
        BufferedImage patch = new BufferedImage(TEMPLATE_SIZE, TEMPLATE_SIZE, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < TEMPLATE_SIZE; y++) {
            for (int x = 0; x < TEMPLATE_SIZE; x++) {
                int r = (x * 5) & 0xFF;
                int g = (y * 5) & 0xFF;
                int b = (x * 3 + y * 7) & 0xFF;
                patch.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return patch;
    }

    private static BufferedImage noiseWith(BufferedImage patch, int offsetX, int offsetY) {
        BufferedImage bg = new BufferedImage(400, 300, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(11);
        for (int y = 0; y < bg.getHeight(); y++) {
            for (int x = 0; x < bg.getWidth(); x++) {
                bg.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        bg.getGraphics().drawImage(patch, offsetX, offsetY, null);
        return bg;
    }

    @Test
    void imageClickerRoutesClickThroughTheSource(@TempDir Path tmp) throws Exception {
        BufferedImage patch = templatePatch();
        Path templateFile = tmp.resolve("marker.png");
        ImageIO.write(patch, "png", templateFile.toFile());

        int offsetX = 150, offsetY = 90;
        RecordingSource source = new RecordingSource(noiseWith(patch, offsetX, offsetY));

        boolean clicked = ImageClicker.click(new ImageTemplate(templateFile.toString()), source, 0.7);

        assertTrue(clicked, "template should be located and clicked");
        assertNotNull(source.clicked, "click must go through CaptureSource.click, not Mouse");
        // The recorded point is within the matched template rectangle (allowing for click randomization).
        double x = source.clicked.x, y = source.clicked.y;
        assertTrue(x >= offsetX && x <= offsetX + TEMPLATE_SIZE,
                "click x " + x + " within [" + offsetX + ", " + (offsetX + TEMPLATE_SIZE) + "]");
        assertTrue(y >= offsetY && y <= offsetY + TEMPLATE_SIZE,
                "click y " + y + " within [" + offsetY + ", " + (offsetY + TEMPLATE_SIZE) + "]");
    }

    @Test
    void regionDelegatesClickToUnderlyingSurface() {
        RecordingSource source = new RecordingSource(new BufferedImage(100, 100, BufferedImage.TYPE_3BYTE_BGR));
        CaptureSource region = source.region(new Rect(10, 20, 50, 50));

        region.click(new Point(33, 44));

        assertNotNull(source.clicked, "a region must delegate click() to its parent source");
        assertTrue(source.clicked.x == 33 && source.clicked.y == 44,
                "region must not mutate the click coordinate it forwards");
    }
}
