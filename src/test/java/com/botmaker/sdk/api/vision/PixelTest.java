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
    private record FakeSource(BufferedImage image, int ox, int oy) implements CaptureSource {
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

        assertTrue(Pixel.find(Color.RED, source, Precision.TIGHT));

        ColorMatch m = VisionContext.getLastColorMatch();
        assertTrue(m.isFound());
        // Patch is at (10,20)-(30,40) in image space; the source origin is (500,300).
        assertEquals(510, m.getTopLeft().x());
        assertEquals(320, m.getTopLeft().y());
        // The centre of mass is fractional and now rounded inside the SDK, so ±1 rather than exact.
        assertTrue(Math.abs(520 - m.getCenter().x()) <= 1, "centre must be absolute, not image-local");
        assertTrue(Math.abs(330 - m.getCenter().y()) <= 1);
        assertEquals(400, m.getPixelCount());

        Rect bounds = m.getBounds();
        assertEquals(510, bounds.x());
        assertEquals(320, bounds.y());
        assertEquals(20, bounds.width());
        assertEquals(20, bounds.height());
    }

    @Test
    void aMissLeavesANotFoundResultRatherThanStaleData() {
        CaptureSource source = new FakeSource(sceneWithRedPatch(), 0, 0);
        assertTrue(Pixel.find(Color.RED, source, Precision.TIGHT));
        assertTrue(VisionContext.lastColorMatchFound());

        assertFalse(Pixel.find(Color.MAGENTA, source, Precision.EXACT));
        ColorMatch m = VisionContext.getLastColorMatch();
        assertFalse(m.isFound(), "a miss must overwrite the previous hit");
        assertNull(m.getCenter());
        assertNull(m.getBounds());
    }

    @Test
    void theAreaThresholdIsLocationPrecisionNotColourPrecision() {
        BufferedImage img = sceneWithRedPatch();
        CaptureSource source = new FakeSource(img, 0, 0);

        // The patch is 400px. Demanding more than that finds nothing, at the very same colour tolerance.
        assertTrue(Pixel.find(Color.RED, source, Precision.TIGHT.minArea(400)));
        assertFalse(Pixel.find(Color.RED, source, Precision.TIGHT.minArea(401)));
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
        assertTrue(Pixel.matchesAt(15, 25, Color.RED, source, Precision.EXACT));
        assertFalse(Pixel.matchesAt(15, 25, Color.GREEN, source, Precision.LOOSE));
        assertFalse(Pixel.matchesAt(0, 0, Color.RED, source, Precision.TIGHT), "white is not red");
    }

    @Test
    void findAllReturnsEveryClusterLargestFirst() {
        BufferedImage img = sceneWithRedPatch();
        for (int y = 60; y < 65; y++)
            for (int x = 60; x < 65; x++) img.setRGB(x, y, Color.RED.getRGB());   // a smaller 25px patch
        CaptureSource source = new FakeSource(img, 0, 0);

        assertEquals(2, Pixel.findAll(Color.RED, source, Precision.TIGHT));
        List<ColorMatch> all = VisionContext.getLastColorMatchList();
        assertEquals(400, all.get(0).getPixelCount());
        assertEquals(25, all.get(1).getPixelCount());
    }

    @Test
    void theCountThresholdSeesColourTheAreaThresholdRejects() {
        // Two 25px patches: 50 red pixels present, but no blob anywhere near 400. The area test says no, the
        // count test says yes, and neither is wrong — they were asked different questions. This is the whole
        // reason Precision carries both, and it is behaviour the old single threshold could not express.
        BufferedImage img = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 80; y++)
            for (int x = 0; x < 100; x++) img.setRGB(x, y, Color.WHITE.getRGB());
        for (int y = 10; y < 15; y++)
            for (int x = 10; x < 15; x++) img.setRGB(x, y, Color.RED.getRGB());
        for (int y = 60; y < 65; y++)
            for (int x = 60; x < 65; x++) img.setRGB(x, y, Color.RED.getRGB());
        CaptureSource source = new FakeSource(img, 0, 0);

        assertFalse(Pixel.find(Color.RED, source, Precision.TIGHT.minArea(400)));
        assertTrue(Pixel.find(Color.RED, source, Precision.TIGHT.minArea(1).minCount(50)));
        assertFalse(Pixel.find(Color.RED, source, Precision.TIGHT.minArea(1).minCount(51)));
        // And the pair is an AND: a count it passes cannot rescue an area it fails.
        assertFalse(Pixel.find(Color.RED, source, Precision.TIGHT.minArea(400).minCount(50)));
    }

    @Test
    void anOperationThatCannotUseAKnobIgnoresItRatherThanChangingItsAnswer() {
        // One type for every search means some calls are handed fields they have no use for. That is the
        // accepted cost of not making callers assemble three arguments, and it is only safe while "no use
        // for" means exactly no effect — if a stray minArea could quietly turn a matchesAt into a miss, the
        // collapse would have reintroduced the silent wrongness these types exist to remove.
        CaptureSource source = new FakeSource(sceneWithRedPatch(), 0, 0);

        // matchesAt tests one pixel: there is no blob to measure and no total to reach.
        assertTrue(Pixel.matchesAt(15, 25, Color.RED, source,
                Precision.EXACT.minArea(100_000).minCount(100_000)));
        // coverage counts every matching pixel and never clusters.
        assertEquals(Pixel.coverage(Color.RED, source, Precision.TIGHT),
                Pixel.coverage(Color.RED, source, Precision.TIGHT.minArea(100_000).minCount(100_000)), 1e-9);
        // findInRange takes a band, so it has no target colour for a ΔE to measure from — but it does read
        // both quantity gates, which is the half of Precision it genuinely uses.
        assertTrue(Pixel.findInRange(new Color(150, 0, 0), new Color(255, 80, 80), source,
                Precision.EXACT.minArea(400)));
        assertFalse(Pixel.findInRange(new Color(150, 0, 0), new Color(255, 80, 80), source,
                Precision.LOOSE.minArea(401)));
    }

    @Test
    void coverageIsTheMatchingFractionOfTheSource() {
        CaptureSource source = new FakeSource(sceneWithRedPatch(), 0, 0);
        // 400 red px out of 100*80 = 8000 -> 0.05
        assertEquals(0.05, Pixel.coverage(Color.RED, source, Precision.TIGHT), 0.005);
    }

    @Test
    void distanceIsPerceptual() {
        assertEquals(0.0, Pixel.distance(Color.RED, Color.RED), 1e-9);
        assertTrue(Pixel.distance(Color.RED, Color.GREEN) > 50);
        assertTrue(Pixel.distance(Color.RED, new Color(250, 5, 5)) < Precision.TIGHT.deltaE());
    }

    @Test
    void aRegionOfASourceNarrowsTheSearch() {
        CaptureSource full = new FakeSource(sceneWithRedPatch(), 0, 0);
        // The red patch lives at (10,20)-(30,40); a region well away from it must not see it.
        CaptureSource elsewhere = full.region(new Rect(50, 50, 40, 25));
        assertFalse(Pixel.find(Color.RED, elsewhere, Precision.TIGHT));

        CaptureSource onIt = full.region(new Rect(5, 15, 40, 30));
        assertTrue(Pixel.find(Color.RED, onIt, Precision.TIGHT));
    }
}
