package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Vision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
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
        BufferedImage frame = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(3);
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 200; x++) frame.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
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
        CaptureSource still = new CaptureSource() {
            @Override public BufferedImage capture() {
                return frame;
            }

            @Override public Point origin() {
                return new Point(0, 0);
            }
        };
        try (ImageTemplate template = new ImageTemplate(file.toString())) {
            ImageFinder.findAll(template, still);
            List<MatchResult> found = Vision.lastMatchList().stream().filter(m -> m.confidence() >= 0.8).toList();
            assertEquals(2, found.size(), found.toString());
        }
    }
}
