package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A picture that says where it was cut is looked for there, scaled to the frame, and nowhere else. */
class BestMatchesNearTest {

    private static final int SIZE = 48;

    /** A smooth, well-textured patch: it survives being scaled. */
    private static BufferedImage patch() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int r = (int) (127 + 120 * Math.sin(x / 4.0));
                int g = (int) (127 + 120 * Math.cos(y / 5.0));
                int b = (int) (127 + 120 * Math.sin((x + y) / 7.0));
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    /** Noise, with {@code patch} drawn {@code scale} times its size at {@code x}, {@code y}. */
    private static BufferedImage frame(int width, int height, BufferedImage patch, int x, int y, int scale) {
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(3);
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) frame.setRGB(i, j, rnd.nextInt(0xFFFFFF));
        }
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(patch, x, y, SIZE * scale, SIZE * scale, null);
        g.dispose();
        return frame;
    }

    private static CaptureSource still(BufferedImage image) {
        return new CaptureSource() {
            @Override public BufferedImage capture() {
                return image;
            }

            @Override public Point origin() {
                return new Point(0, 0);
            }

            @Override public void click(Point p) {
            }
        };
    }

    private static ImageTemplate saved(Path dir) throws IOException {
        Path file = dir.resolve("ore.png");
        ImageIO.write(patch(), "png", file.toFile());
        return new ImageTemplate(file.toString());
    }

    @Test
    void aPictureIsFoundAtItsPlace(@TempDir Path dir) throws IOException {
        Size cutFrom = new Size(640, 400);
        try (ImageTemplate atItsPlace = saved(dir).at(new Rect(300, 200, SIZE, SIZE)).in(cutFrom);
             ImageTemplate placeUnknown = saved(dir).in(cutFrom)) {
            List<MatchResult> near = ImageFinder.bestMatchesNear(List.of(atItsPlace, placeUnknown), 48,
                    still(frame(640, 400, patch(), 300, 200, 1)));

            assertEquals(new Point(300, 200), near.get(0).topLeft());
            assertEquals(new Point(300, 200), near.get(1).topLeft(), "no place: looked for everywhere");
            assertTrue(near.get(0).confidence() > 0.95, "scored " + near.get(0).confidence());
        }
    }

    @Test
    void aPictureAwayFromItsPlaceIsNotFoundNearButIsEverywhere(@TempDir Path dir) throws IOException {
        try (ImageTemplate elsewhere = saved(dir).at(new Rect(20, 20, SIZE, SIZE)).in(new Size(640, 400))) {
            CaptureSource source = still(frame(640, 400, patch(), 300, 200, 1));

            MatchResult near = ImageFinder.bestMatchesNear(List.of(elsewhere), 48, source).getFirst();
            MatchResult everywhere = ImageFinder.bestMatches(List.of(elsewhere), source).getFirst();

            assertTrue(near.confidence() < 0.5, "searched only near 20,20, it scored " + near.confidence());
            assertEquals(new Point(300, 200), everywhere.topLeft());
        }
    }

    @Test
    void itsPlaceIsScaledToTheFrame(@TempDir Path dir) throws IOException {
        try (ImageTemplate atItsPlace = saved(dir).at(new Rect(300, 200, SIZE, SIZE)).in(new Size(640, 400))) {
            MatchResult near = ImageFinder.bestMatchesNear(List.of(atItsPlace), 48,
                    still(frame(1280, 800, patch(), 600, 400, 2))).getFirst();

            assertTrue(Math.abs(near.topLeft().x() - 600) <= 2 && Math.abs(near.topLeft().y() - 400) <= 2,
                    "found at " + near.topLeft());
            assertTrue(near.confidence() > 0.8, "scored " + near.confidence());
        }
    }

    /** The margin is a share of the frame too: 40 cut pixels off is 80 on a frame twice as wide, still within 48. */
    @Test
    void itsMarginIsScaledToTheFrame(@TempDir Path dir) throws IOException {
        try (ImageTemplate atItsPlace = saved(dir).at(new Rect(300, 200, SIZE, SIZE)).in(new Size(640, 400))) {
            MatchResult near = ImageFinder.bestMatchesNear(List.of(atItsPlace), 48,
                    still(frame(1280, 800, patch(), 680, 400, 2))).getFirst();

            assertTrue(Math.abs(near.topLeft().x() - 680) <= 2, "found at " + near.topLeft());
            assertTrue(near.confidence() > 0.8, "scored " + near.confidence());
        }
    }

    @Test
    void aPictureHeldInMemoryMatchesAsItsSavedFileWould(@TempDir Path dir) throws IOException {
        CaptureSource source = still(frame(640, 400, patch(), 300, 200, 1));
        try (ImageTemplate held = ImageTemplate.of("ore", patch()).in(new Size(640, 400));
             ImageTemplate saved = saved(dir).in(new Size(640, 400))) {
            List<MatchResult> both = ImageFinder.bestMatches(List.of(held, saved), source);

            assertEquals("ore", held.id());
            assertEquals(both.get(1).topLeft(), both.get(0).topLeft());
            assertEquals(both.get(1).confidence(), both.get(0).confidence(), 1e-6);
        }
    }
}
