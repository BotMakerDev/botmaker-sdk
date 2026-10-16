package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Vision;
import com.botmaker.sdk.plugin.screen.CutShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PictureCutsTest {

    @Test
    void aBoxInDesktopPixelsIsCutFromWhereTheFrameSits() {
        BufferedImage frame = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        frame.setRGB(30, 20, 0xFF0000);
        PictureCuts.Piece piece = PictureCuts.crop(frame, new Area(1000, 500, 200, 100), new Area(1030, 520, 10, 5))
                .orElseThrow();
        assertEquals(new Rect(30, 20, 10, 5), piece.place(), "the box in the frame's own pixels");
        BufferedImage cut = piece.image();
        assertEquals(10, cut.getWidth());
        assertEquals(5, cut.getHeight());
        assertEquals(0xFF0000, cut.getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    void aBoxOverTheEdgeIsClippedAndOneOutsideIsNothing() {
        BufferedImage frame = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        assertEquals(new Rect(180, 0, 20, 10), PictureCuts.crop(frame, null, new Area(180, 0, 50, 10)).orElseThrow()
                .place());
        assertTrue(PictureCuts.crop(frame, null, new Area(300, 0, 10, 10)).isEmpty());
        assertTrue(PictureCuts.crop(frame, null, new Area(0, 0, 0, 10)).isEmpty());
        assertTrue(PictureCuts.crop(null, null, new Area(0, 0, 5, 5)).isEmpty());
    }

    /** Two map pins alike but for their timers' digits: with the digits left out, a run finds both. */
    @Test
    void aPartLeftOutLetsCopiesThatDifferThereAllMatch(@TempDir Path tmp) throws Exception {
        BufferedImage frame = noise(200, 100, 3);
        for (int pin : new int[]{20, 120}) {
            for (int y = 10; y < 40; y++) {
                for (int x = pin; x < pin + 30; x++) frame.setRGB(x, y, ((x - pin) * 8) << 16 | (y * 6) << 8 | 0x40);
            }
        }
        PictureCuts.Piece piece = PictureCuts.crop(frame, null, new Area(20, 10, 30, 40)).orElseThrow();
        PictureCuts.Piece pin = PictureCuts.masked(piece, new Rect(20, 40, 30, 10));
        assertEquals(0, pin.image().getRGB(5, 35) >>> 24, "the digits are transparent");
        assertEquals(0xFF, pin.image().getRGB(5, 5) >>> 24, "the icon is kept");
        assertEquals(piece, PictureCuts.masked(piece, new Rect(100, 0, 5, 5)), "a part outside leaves it as it is");

        Path file = tmp.resolve("pin.png");
        ImageIO.write(pin.image(), "png", file.toFile());
        try (ImageTemplate template = new ImageTemplate(file.toString())) {
            ImageFinder.findAll(template, still(frame));
            List<MatchResult> found = Vision.lastMatchList().stream().filter(m -> m.confidence() >= 0.8).toList();
            assertEquals(2, found.size(), found.toString());
        }
    }

    // ── round and free shapes ──────────────────────────────────────────────────────────────────────────

    @Test
    void anOvalKeepsItsCentreAndLeavesTheCornersTransparent() {
        BufferedImage oval = ellipse(solid(40, 20, Color.RED));
        assertEquals(255, alpha(oval, 20, 10));
        assertEquals(Color.RED.getRGB() & 0xFFFFFF, oval.getRGB(20, 10) & 0xFFFFFF);
        assertEquals(0, alpha(oval, 0, 0));
        assertEquals(0, alpha(oval, 39, 19));
    }

    @Test
    void anOvalsRimIsAntiAliased() {
        BufferedImage oval = ellipse(solid(64, 64, Color.BLUE));
        boolean partial = false;
        for (int x = 0; x < 64 && !partial; x++) {
            for (int y = 0; y < 64 && !partial; y++) {
                int a = alpha(oval, x, y);
                partial = a > 0 && a < 255;
            }
        }
        assertTrue(partial, "a clipped oval has only fully opaque and fully transparent pixels");
    }

    @Test
    void aPolygonIsCutToItsBoxAndLeavesItsOutsideTransparent() {
        BufferedImage frame = solid(200, 100, Color.GREEN);
        // A triangle: its right angle at (50,20), its legs 40 px long.
        PictureCuts.Piece piece = PictureCuts.polygon(frame,
                List.of(new Point(50, 20), new Point(89, 20), new Point(50, 59))).orElseThrow();
        assertEquals(new Rect(50, 20, 40, 40), piece.place(), "the box around the points");
        assertEquals(255, alpha(piece.image(), 3, 3), "inside, by the right angle");
        assertEquals(Color.GREEN.getRGB() & 0xFFFFFF, piece.image().getRGB(3, 3) & 0xFFFFFF);
        assertEquals(0, alpha(piece.image(), 37, 37), "outside, across the long side");
    }

    @Test
    void aPolygonNeedsThreePointsOnTheFrame() {
        BufferedImage frame = solid(100, 100, Color.GREEN);
        assertEquals(Optional.empty(), PictureCuts.polygon(frame, List.of(new Point(1, 1), new Point(9, 9))));
        assertEquals(Optional.empty(), PictureCuts.polygon(frame,
                List.of(new Point(500, 500), new Point(600, 500), new Point(500, 600))), "off the frame");
    }

    @Test
    void aCutTakesItsShape() {
        BufferedImage frame = solid(100, 100, Color.GREEN);
        Rect box = new Rect(10, 10, 30, 30);
        assertEquals(255, alpha(PictureCuts.cut(frame, box, CutShape.RECT, List.of()).orElseThrow().image(), 0, 0));
        assertEquals(0, alpha(PictureCuts.cut(frame, box, CutShape.ELLIPSE, List.of()).orElseThrow().image(), 0, 0));
    }

    @Test
    void aPreviewIsOpaqueAndCheckersWhatTheCutLeavesOut() {
        BufferedImage preview = PictureCuts.checkered(ellipse(solid(40, 40, Color.RED)));
        assertEquals(255, alpha(preview, 0, 0));
        assertTrue((preview.getRGB(0, 0) & 0xFFFFFF) != (Color.RED.getRGB() & 0xFFFFFF), "a corner shows the board");
        assertEquals(Color.RED.getRGB() & 0xFFFFFF, preview.getRGB(20, 20) & 0xFFFFFF);
    }

    /**
     * A round marker scores 1.00 on the frame it was cut from, and still matches where only what lies outside it
     * changed: a round button over a background that moves.
     */
    @Test
    void aMaskedMarkerScoresInFullOnItsFrameWhateverItsOutside(@TempDir Path tmp) throws Exception {
        BufferedImage frame = noise(200, 100, 5);
        PictureCuts.Piece round = PictureCuts.cut(frame, new Rect(60, 20, 40, 40), CutShape.ELLIPSE, List.of())
                .orElseThrow();
        Path file = tmp.resolve("round.png");
        ImageIO.write(round.image(), "png", file.toFile());
        try (ImageTemplate template = new ImageTemplate(file.toString())) {
            MatchResult own = ImageFinder.bestMatches(List.of(template), still(frame)).getFirst();
            assertTrue(own.confidence() >= 0.995, "on its own frame: " + own);
            assertEquals(new Point(60, 20), own.topLeft());

            BufferedImage moved = noise(200, 100, 6);
            moved.getGraphics().drawImage(round.image(), 60, 20, null);
            MatchResult over = ImageFinder.bestMatches(List.of(template), still(moved)).getFirst();
            assertTrue(over.confidence() >= 0.95, "over another background: " + over);
        }
    }

    private static BufferedImage ellipse(BufferedImage image) {
        return PictureCuts.ellipse(new PictureCuts.Piece(image, new Rect(0, 0, image.getWidth(), image.getHeight())))
                .image();
    }

    private static BufferedImage solid(int w, int h, Color colour) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, y, colour.getRGB());
        return image;
    }

    private static BufferedImage noise(int w, int h, long seed) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(seed);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        return image;
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static CaptureSource still(BufferedImage frame) {
        return new CaptureSource() {
            @Override public BufferedImage capture() {
                return frame;
            }

            @Override public Point origin() {
                return new Point(0, 0);
            }
        };
    }
}
