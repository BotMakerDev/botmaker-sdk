package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the {@link Pixel} facade over a synthetic {@link CaptureSource} — in particular that results come
 * back in <b>absolute</b> screen coordinates (the source's origin applied), which is what makes them safe to
 * hand straight to {@code Mouse}.
 */
class PixelTest {

    private static final int W = 100, H = 80;

    /** A capture source serving a fixed image, reporting a non-zero origin (as a window at (500,300) would). */
    private record FakeSource(BufferedImage image, double ox, double oy) implements CaptureSource {
        @Override public BufferedImage capture() { return image; }
        @Override public Point origin() { return new Point(ox, oy); }
    }

    private static BufferedImage sceneWithRedPatch() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) img.setRGB(x, y, Color.WHITE.getRGB());
        for (int y = 20; y < 40; y++)
            for (int x = 10; x < 30; x++) img.setRGB(x, y, Color.RED.getRGB());
        return img;
    }

    @Test
    void findReportsAbsoluteCoordinates() {
        CaptureSource source = new FakeSource(sceneWithRedPatch(), 500, 300);

        assertTrue(Pixel.find(Color.RED, Pixel.TIGHT, source, 4));

        ColorMatch m = VisionContext.getLastColorMatch();
        assertTrue(m.isFound());
        // Patch is at (10,20)-(30,40) in image space; the source origin is (500,300).
        assertEquals(510, m.getTopLeft().x, 0.5);
        assertEquals(320, m.getTopLeft().y, 0.5);
        assertEquals(520, m.getCenter().x, 1.0, "centre must be absolute, not image-local");
        assertEquals(330, m.getCenter().y, 1.0);
        assertEquals(400, m.getPixelCount());

        Rect bounds = m.getBounds();
        assertEquals(510, bounds.x);
        assertEquals(320, bounds.y);
        assertEquals(20, bounds.width);
        assertEquals(20, bounds.height);
    }

    @Test
    void aMissLeavesANotFoundResultRatherThanStaleData() {
        CaptureSource source = new FakeSource(sceneWithRedPatch(), 0, 0);
        assertTrue(Pixel.find(Color.RED, Pixel.TIGHT, source, 4));
        assertTrue(VisionContext.lastColorMatchFound());

        assertFalse(Pixel.find(Color.MAGENTA, Pixel.EXACT, source, 4));
        ColorMatch m = VisionContext.getLastColorMatch();
        assertFalse(m.isFound(), "a miss must overwrite the previous hit");
        assertNull(m.getCenter());
        assertNull(m.getBounds());
    }

    @Test
    void minPixelsIsLocationPrecisionNotColourPrecision() {
        BufferedImage img = sceneWithRedPatch();
        CaptureSource source = new FakeSource(img, 0, 0);

        // The patch is 400px. Demanding more than that finds nothing, at the very same colour tolerance.
        assertTrue(Pixel.find(Color.RED, Pixel.TIGHT, source, 400));
        assertFalse(Pixel.find(Color.RED, Pixel.TIGHT, source, 401));
    }

    @Test
    void colorAtReadsThroughTheSourceOrigin() {
        CaptureSource source = new FakeSource(sceneWithRedPatch(), 500, 300);

        // Absolute (515,325) -> image-local (15,25), inside the red patch.
        assertEquals(Color.RED, Pixel.colorAt(515, 325, source));
        assertEquals(Color.WHITE, Pixel.colorAt(500, 300, source));
        // Outside the source entirely.
        assertNull(Pixel.colorAt(0, 0, source));
    }

    @Test
    void matchesAtUsesColourToleranceOnly() {
        CaptureSource source = new FakeSource(sceneWithRedPatch(), 0, 0);
        assertTrue(Pixel.matchesAt(15, 25, Color.RED, Pixel.EXACT, source));
        assertFalse(Pixel.matchesAt(15, 25, Color.GREEN, Pixel.LOOSE, source));
        assertFalse(Pixel.matchesAt(0, 0, Color.RED, Pixel.TIGHT, source), "white is not red");
    }

    @Test
    void findAllReturnsEveryClusterLargestFirst() {
        BufferedImage img = sceneWithRedPatch();
        for (int y = 60; y < 65; y++)
            for (int x = 60; x < 65; x++) img.setRGB(x, y, Color.RED.getRGB());   // a smaller 25px patch
        CaptureSource source = new FakeSource(img, 0, 0);

        assertEquals(2, Pixel.findAll(Color.RED, Pixel.TIGHT, source, 4));
        List<ColorMatch> all = VisionContext.getLastColorMatchList();
        assertEquals(400, all.get(0).getPixelCount());
        assertEquals(25, all.get(1).getPixelCount());
    }

    @Test
    void coverageIsTheMatchingFractionOfTheSource() {
        CaptureSource source = new FakeSource(sceneWithRedPatch(), 0, 0);
        // 400 red px out of 100*80 = 8000 -> 0.05
        assertEquals(0.05, Pixel.coverage(Color.RED, Pixel.TIGHT, source), 0.005);
    }

    @Test
    void distanceIsPerceptual() {
        assertEquals(0.0, Pixel.distance(Color.RED, Color.RED), 1e-9);
        assertTrue(Pixel.distance(Color.RED, Color.GREEN) > 50);
        assertTrue(Pixel.distance(Color.RED, new Color(250, 5, 5)) < Pixel.TIGHT);
    }

    @Test
    void aRegionOfASourceNarrowsTheSearch() {
        CaptureSource full = new FakeSource(sceneWithRedPatch(), 0, 0);
        // The red patch lives at (10,20)-(30,40); a region well away from it must not see it.
        CaptureSource elsewhere = full.region(new Rect(50, 50, 40, 25));
        assertFalse(Pixel.find(Color.RED, Pixel.TIGHT, elsewhere, 4));

        CaptureSource onIt = full.region(new Rect(5, 15, 40, 30));
        assertTrue(Pixel.find(Color.RED, Pixel.TIGHT, onIt, 4));
    }
}
