package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A picture of several frames is one strip, its frames side by side, each the picture's width and height. */
class ImageTemplateFramesTest {

    private static BufferedImage strip(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) image.setRGB(x, y, (x * 37 + y * 11) & 0xFFFFFF);
        }
        return image;
    }

    @Test
    void frameSizeIsStripOverCount() {
        try (ImageTemplate plant = ImageTemplate.of("plant", strip(40, 10)).frames(4)) {
            assertEquals(4, plant.frames());
            assertEquals(10, plant.width());
            assertEquals(10, plant.height());
            assertEquals(4, plant.frameMats().size());
            assertEquals(10, plant.frameMats().get(3).cols());
        }
    }

    @Test
    void stillPictureHasOneFrame() {
        assertEquals(1, new ImageTemplate("images/a.png").frames());
    }

    @Test
    void stripOfWrongWidthIsRefused() {
        try (ImageTemplate plant = ImageTemplate.of("images/plant.png", strip(41, 10)).frames(4)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class, plant::width);
            assertTrue(refused.getMessage().contains("images/plant.png"), refused.getMessage());
            assertTrue(refused.getMessage().contains("41"), refused.getMessage());
            assertTrue(refused.getMessage().contains("4 frames"), refused.getMessage());
        }
    }

    @Test
    void framesBelowOneRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ImageTemplate("images/a.png").frames(0));
    }

    @Test
    void framesTakePartInEquality() {
        ImageTemplate plain = new ImageTemplate("images/a.png");
        assertNotEquals(plain.frames(3), plain.frames(4));
        assertEquals(plain, plain.frames(1));
        assertEquals(plain.frames(3), plain.frames(3));
        assertTrue(plain.frames(3).toString().contains("frames=3"));
    }
}
