package com.botmaker.sdk.plugin.screen;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a click on the sampler hands back. The pick is read off the frame alone, so it holds whatever the
 * window does on the way out: closing it fires a mouse-exit that forgets the hovered pixel, and a pick read
 * after that was a pick of (-1, -1) (feedback 3, 2026-09-27).
 */
class ColorSamplerTest {

    private static BufferedImage frame() {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) image.setRGB(x, y, 0x204060);
        }
        image.setRGB(7, 7, 0xFF0000);
        return image;
    }

    @Test
    void aPickCarriesThePixelAndItsNeighbourhoodSpread() {
        BufferedImage image = frame();
        EditorFrame frame = new EditorFrame(image, "game", new Rectangle(0, 0, 8, 8), true);
        ColorSampler.Sample sample = ColorSampler.sampleAt(frame, 1, 1);
        assertEquals(new java.awt.Color(0x204060), sample.color());
        assertEquals(0.0, sample.spread(), 1e-9, "a flat patch does not vary");
        assertTrue(ColorSampler.sampleAt(frame, 6, 6).spread() > 10, "the red corner is in the 5x5 around 6,6");
    }

    @Test
    void aCentreOutsideTheFrameHasNoSpreadRatherThanThrowing() {
        BufferedImage image = frame();
        assertEquals(0.0, ColorSampler.spreadAt(image, -1, -1));
        assertEquals(0.0, ColorSampler.spreadAt(image, 8, 0));
    }
}
