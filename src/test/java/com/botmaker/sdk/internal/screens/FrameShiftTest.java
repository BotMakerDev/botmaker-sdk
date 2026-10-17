package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.shared.opencv.OpenCvNative;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** How far a view moved, read off its pixels. */
class FrameShiftTest {

    @BeforeAll
    static void loadOpenCv() {
        OpenCvNative.ensureLoaded();
    }

    /** A 400×300 frame of a plane of noise seen from {@code (x, y)}. */
    private static BufferedImage view(int[][] plane, int x, int y) {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < 300; j++) {
            for (int i = 0; i < 400; i++) {
                int v = plane[y + j][x + i];
                image.setRGB(i, j, v << 16 | v << 8 | v);
            }
        }
        return image;
    }

    @Test
    void contentThatMovedIsReadAsItsShift() {
        Random random = new Random(7);
        int[][] blocks = new int[50][63];
        for (int[] row : blocks) {
            for (int i = 0; i < row.length; i++) row[i] = random.nextInt(256);
        }
        // Blocks of 8 px, so the correlation has shapes to hold, not single pixels.
        int[][] plane = new int[400][500];
        for (int j = 0; j < 400; j++) {
            for (int i = 0; i < 500; i++) plane[j][i] = blocks[j / 8][i / 8];
        }
        // The view moves 37 left and 12 down: the content moves 37 right and 12 up.
        Optional<Point> shift = FrameShift.of(view(plane, 50, 40), view(plane, 13, 52));
        assertTrue(shift.isPresent(), "a textured pair is read");
        assertTrue(Math.abs(shift.get().x() - 37) <= 1 && Math.abs(shift.get().y() + 12) <= 1,
                "the content moved (37, -12), read " + shift.get());
    }

    @Test
    void aBlankPairTellsNothing() {
        BufferedImage blank = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        assertTrue(FrameShift.of(blank, blank).isEmpty());
    }
}
