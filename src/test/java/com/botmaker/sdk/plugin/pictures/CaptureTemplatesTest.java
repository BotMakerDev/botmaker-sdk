package com.botmaker.sdk.plugin.pictures;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An oval picture: the inscribed oval of the crop, transparent outside, with a smooth rim. */
class CaptureTemplatesTest {

    private static BufferedImage solid(int w, int h, Color colour) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, y, colour.getRGB());
        return image;
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    @Test
    void theCentreIsKeptAndTheCornersAreTransparent() {
        BufferedImage oval = CaptureTemplates.oval(solid(40, 20, Color.RED));
        assertEquals(255, alpha(oval, 20, 10));
        assertEquals(Color.RED.getRGB() & 0xFFFFFF, oval.getRGB(20, 10) & 0xFFFFFF);
        assertEquals(0, alpha(oval, 0, 0));
        assertEquals(0, alpha(oval, 39, 19));
    }

    @Test
    void theRimIsAntiAliased() {
        BufferedImage oval = CaptureTemplates.oval(solid(64, 64, Color.BLUE));
        boolean partial = false;
        for (int x = 0; x < 64 && !partial; x++) {
            for (int y = 0; y < 64 && !partial; y++) {
                int a = alpha(oval, x, y);
                partial = a > 0 && a < 255;
            }
        }
        assertTrue(partial, "a clipped oval has only fully opaque and fully transparent pixels");
    }
}
