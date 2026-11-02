package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.Shades;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run's reach with a learned scale: a button shown bigger is found at its size and read as the scale's version, and
 * a map's poll of 40 buttons stays within 1.3× of a bot with no scale (the scaled copies are looked for near each
 * match only). OpenCV matching, headless.
 */
class ScaledMatchTest {

    private static final int SIDE = 32;
    private static final Shade GROWN = new Shade(Shade.Kind.SCALE, Shade.Channels.all(1), Shade.Channels.all(0), 0.04,
            Shade.Cause.GAME, State.SELECTED, new Shade.Geometry(0, 1.25, 1.25, 0, 0));

    @TempDir
    Path dir;

    @AfterEach
    void forget() {
        ShadeFit.use(Shades.none());
    }

    /** A smooth button, unlike the others by {@code seed}: smooth, so it still matches drawn at another size. */
    private static BufferedImage button(int seed) {
        BufferedImage image = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
        double a = 0.15 + (seed % 7) * 0.04;
        double b = 0.1 + (seed / 7) * 0.05;
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                int r = (int) (128 + 110 * Math.sin(x * a + seed));
                int g = (int) (128 + 110 * Math.cos(y * b + seed * 0.5));
                int bl = (int) (128 + 110 * Math.sin((x + y) * (a + b) / 2));
                image.setRGB(x, y, r << 16 | g << 8 | bl);
            }
        }
        return image;
    }

    private ImageTemplate saved(int seed, Rect place) throws IOException {
        Path file = dir.resolve("button" + seed + ".png");
        ImageIO.write(button(seed), "png", file.toFile());
        return new ImageTemplate(file.toString()).at(place);
    }

    private static Rect placeOf(int i) {
        return new Rect(20 + (i % 8) * 150, 20 + (i / 8) * 130, SIDE, SIDE);
    }

    /** The frame with each button at its place, button {@code grown} drawn 1.25× as big around its centre. */
    private static BufferedImage frame(int count, int grown) {
        BufferedImage frame = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int i = 0; i < count; i++) {
            Rect at = placeOf(i);
            if (i == grown) {
                g.drawImage(button(i), at.x() - 4, at.y() - 4, SIDE + 8, SIDE + 8, null);
            } else {
                g.drawImage(button(i), at.x(), at.y(), null);
            }
        }
        g.dispose();
        return frame;
    }

    @Test
    void aButtonShownBiggerIsFoundAtItsSizeAndReadAsTheScale() throws IOException {
        ImageTemplate picture = saved(3, placeOf(3));
        BufferedImage frame = frame(8, 3);
        ShadeFit.use(Shades.of(GROWN));
        Reach.Found found = new RunReach().findEachScored(List.of(picture), frame).getFirst().orElseThrow();
        assertEquals(40, found.place().width(), 1, "found by the scaled copy: " + found);
        assertEquals(Optional.of(GROWN),
                ShadeFit.shown(picture, found.place(), frame, List.of(GROWN), Set.of(Shade.Cause.GAME)));
    }

    @Test
    void aMapOfFortyButtonsPollsNearlyAsFastWithAScale() throws IOException {
        List<ImageTemplate> pictures = new ArrayList<>();
        for (int i = 0; i < 40; i++) pictures.add(saved(i, placeOf(i)));
        BufferedImage frame = frame(40, -1);
        RunReach reach = new RunReach();
        long none = fastest(reach, pictures, frame, Shades.none());
        long scaled = fastest(reach, pictures, frame, Shades.of(GROWN));
        assertTrue(scaled <= none * 1.3, "with a scale " + scaled / 1_000_000 + " ms, without "
                + none / 1_000_000 + " ms");
    }

    /** The fastest of several polls, after one to load the pictures: a busy machine's slow run is not the code's. */
    private static long fastest(RunReach reach, List<ImageTemplate> pictures, BufferedImage frame, Shades shades) {
        ShadeFit.use(shades);
        reach.findEachScored(pictures, frame);
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 7; run++) {
            long start = System.nanoTime();
            List<Optional<Reach.Found>> found = reach.findEachScored(pictures, frame);
            best = Math.min(best, System.nanoTime() - start);
            assertTrue(found.stream().allMatch(Optional::isPresent), "every button found");
        }
        return best;
    }
}
