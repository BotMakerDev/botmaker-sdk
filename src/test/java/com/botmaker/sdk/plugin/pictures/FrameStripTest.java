package com.botmaker.sdk.plugin.pictures;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A moving picture's file: frames side by side, frame 1 at the left. */
class FrameStripTest {

    static BufferedImage filled(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, 0xFF000000 | rgb);
        return image;
    }

    @Test
    void joinThenSplitGivesFramesBack() {
        List<BufferedImage> frames = List.of(filled(6, 4, 0xFF0000), filled(6, 4, 0x00FF00), filled(6, 4, 0x0000FF));
        BufferedImage strip = FrameStrip.join(frames);
        assertEquals(18, strip.getWidth());
        assertEquals(4, strip.getHeight());
        List<BufferedImage> split = FrameStrip.split(strip, 3);
        assertEquals(3, split.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(6, split.get(i).getWidth());
            assertEquals(frames.get(i).getRGB(2, 2), split.get(i).getRGB(2, 2), "frame " + (i + 1));
        }
    }

    @Test
    void joinRefusesUnequalSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> FrameStrip.join(List.of(filled(6, 4, 0), filled(7, 4, 0))));
        assertThrows(IllegalArgumentException.class, () -> FrameStrip.split(filled(7, 4, 0), 2),
                "7 px do not split into 2 frames");
    }

    @Test
    void firstIsLeftmostFrame() {
        BufferedImage strip = FrameStrip.join(List.of(filled(5, 5, 0x123456), filled(5, 5, 0x654321)));
        BufferedImage first = FrameStrip.first(strip, 2);
        assertEquals(5, first.getWidth());
        assertEquals(0xFF123456, first.getRGB(4, 4));
        assertEquals(strip, FrameStrip.first(strip, 1), "a still picture is its own first frame");
    }
}
