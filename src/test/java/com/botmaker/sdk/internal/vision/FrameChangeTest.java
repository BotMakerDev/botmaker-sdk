package com.botmaker.sdk.internal.vision;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What counts as the screen changing: a new screen or a fade does, noise and a caret do not. */
class FrameChangeTest {

    private static BufferedImage filled(Color color, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    @Test
    void theSameFrameHasNotChanged() {
        assertFalse(FrameChange.changed(filled(Color.GRAY, 640, 360), filled(Color.GRAY, 640, 360)));
    }

    @Test
    void aCaretBlinkingIsNotAChange() {
        BufferedImage after = filled(Color.GRAY, 640, 360);
        Graphics2D g = after.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(300, 170, 2, 16);
        g.dispose();
        assertFalse(FrameChange.changed(filled(Color.GRAY, 640, 360), after));
    }

    @Test
    void slightNoiseIsNotAChange() {
        assertFalse(FrameChange.changed(filled(new Color(128, 128, 128), 640, 360),
                filled(new Color(136, 136, 136), 640, 360)));
    }

    @Test
    void aNewScreenOrAPanelIsAChange() {
        assertTrue(FrameChange.changed(filled(Color.GRAY, 640, 360), filled(Color.BLACK, 640, 360)));
        BufferedImage panel = filled(Color.GRAY, 640, 360);
        Graphics2D g = panel.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(100, 100, 120, 60);
        g.dispose();
        assertTrue(FrameChange.changed(filled(Color.GRAY, 640, 360), panel));
    }

    @Test
    void anotherSizeIsAChangeAndAMissingFrameIsNot() {
        assertTrue(FrameChange.changed(filled(Color.GRAY, 640, 360), filled(Color.GRAY, 320, 180)));
        assertFalse(FrameChange.changed(null, filled(Color.GRAY, 640, 360)));
    }
}
