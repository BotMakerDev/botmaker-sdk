package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A picture of several frames is found where any of its frames shows, as one picture. */
class FramedMatchTest {

    private static final int SIZE = 24;
    private static final double CONFIDENCE = 0.9;

    private static BufferedImage pattern(IntBinaryOperator rgb) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) img.setRGB(x, y, rgb.applyAsInt(x, y) & 0xFFFFFF);
        }
        return img;
    }

    private static final BufferedImage ONE = pattern((x, y) -> (int) (127 + 120 * Math.sin(x / 3.0)) << 16
            | (int) (127 + 120 * Math.cos(y / 4.0)) << 8);
    private static final BufferedImage TWO = pattern((x, y) -> ((x / 6 + y / 6) % 2 == 0 ? 0xE0E0E0 : 0x202020));
    private static final BufferedImage THREE = pattern((x, y) -> (int) (127 + 120 * Math.sin((x + y) / 2.5)) << 8
            | (x * 10) & 0xFF);

    /** {@code ONE} with a little of its corner changed: matches wherever {@code ONE} does. */
    private static final BufferedImage ONE_NUDGED = pattern((x, y) -> x < 3 && y < 3 ? 0x101010 : ONE.getRGB(x, y));

    private static BufferedImage strip(BufferedImage... frames) {
        BufferedImage strip = new BufferedImage(SIZE * frames.length, SIZE, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = strip.createGraphics();
        for (int i = 0; i < frames.length; i++) g.drawImage(frames[i], i * SIZE, 0, null);
        g.dispose();
        return strip;
    }

    /** Noise of a seed, with each {@code (image, x, y)} drawn on it. */
    private static BufferedImage background(Object... drawn) {
        BufferedImage frame = new BufferedImage(400, 260, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(7);
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) frame.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
        Graphics2D g = frame.createGraphics();
        for (int i = 0; i < drawn.length; i += 3) g.drawImage((BufferedImage) drawn[i], (int) drawn[i + 1],
                (int) drawn[i + 2], null);
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

    @Test
    void foundOnAnyFrame() {
        try (ImageTemplate plant = ImageTemplate.of("plant", strip(ONE, TWO, THREE)).frames(3)) {
            MatchResult found = ImageFinder.bestMatch(plant, still(background(THREE, 100, 60)));
            assertTrue(found.isFound());
            assertTrue(found.confidence() >= CONFIDENCE, "scored " + found.confidence());
            assertEquals(new Point(100, 60), found.topLeft());
            assertEquals(SIZE, found.width());
        }
    }

    @Test
    void theBestFrameWinsNotTheFirstThatWouldDo() {
        try (ImageTemplate plant = ImageTemplate.of("plant", strip(ONE, THREE)).frames(2)) {
            CaptureSource screen = still(background(ONE_NUDGED, 20, 20, THREE, 200, 100));
            MatchResult found = ImageFinder.findInternal(plant, screen, CONFIDENCE);
            assertTrue(found.isFound());
            assertEquals(new Point(200, 100), found.topLeft(), "frame 2 shows whole there; frame 1 only nearly");
            MatchResult compared = ImageFinder.compare(List.of(plant), List.of(), screen, CONFIDENCE, 0.05);
            assertEquals(new Point(200, 100), compared.topLeft());
        }
    }

    @Test
    void notFoundWhenNoFrameShows() {
        try (ImageTemplate plant = ImageTemplate.of("plant", strip(ONE, TWO)).frames(2)) {
            MatchResult best = ImageFinder.bestMatch(plant, still(background(THREE, 100, 60)));
            assertFalse(best.isFound() && best.confidence() >= CONFIDENCE, "scored " + best.confidence());
        }
    }

    @Test
    void findAllMergesFramesAtOnePlace() {
        try (ImageTemplate plant = ImageTemplate.of("plant", strip(ONE, ONE_NUDGED, THREE)).frames(3)) {
            List<MatchResult> all = ImageFinder.allMatches(plant,
                    still(background(ONE, 40, 30, THREE, 300, 200)), CONFIDENCE);
            assertEquals(2, all.size(), all.toString());
        }
    }

    @Test
    void placeLimitsEveryFrame() {
        Rect place = new Rect(40, 30, SIZE, SIZE);
        try (ImageTemplate atPlace = ImageTemplate.of("plant", strip(ONE, TWO)).frames(2).at(place)) {
            MatchResult far = ImageFinder.bestMatchesNear(List.of(atPlace), 10,
                    still(background(TWO, 300, 200))).getFirst();
            assertFalse(far.isFound() && far.confidence() >= CONFIDENCE, "far frame scored " + far.confidence());
            MatchResult near = ImageFinder.bestMatchesNear(List.of(atPlace), 10,
                    still(background(TWO, 44, 32))).getFirst();
            assertTrue(near.isFound() && near.confidence() >= CONFIDENCE, "near frame scored " + near.confidence());
            assertEquals(new Point(44, 32), near.topLeft());
        }
    }
}
