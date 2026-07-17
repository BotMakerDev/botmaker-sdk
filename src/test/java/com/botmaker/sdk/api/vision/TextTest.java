package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the SDK {@link Text} facade end to end against a fixed-origin {@link CaptureSource} stub:
 * recognition works, results land in {@link VisionContext}, and boxes are shifted into absolute screen
 * coordinates by the source origin.
 */
class TextTest {

    private static final int ORIGIN_X = 300;
    private static final int ORIGIN_Y = 200;

    /** A CaptureSource that serves a rendered image whose pixel (0,0) sits at (ORIGIN_X, ORIGIN_Y). */
    private static CaptureSource stub(String text) {
        BufferedImage img = new BufferedImage(600, 140, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
        g.drawString(text, 20, 95);
        g.dispose();
        return new CaptureSource() {
            @Override public BufferedImage capture() { return img; }
            @Override public Point origin() { return new Point(ORIGIN_X, ORIGIN_Y); }
        };
    }

    @Test
    void findStoresAbsoluteMatchInContext() {
        CaptureSource source = stub("START");

        assertTrue(Text.find("START", source), "should find START");

        TextMatch last = VisionContext.getLastTextMatch();
        assertTrue(last.isFound(), "context has the match");
        assertTrue(last.getText().toUpperCase().contains("START"), "text is START: " + last.getText());

        // Box must be shifted into absolute coordinates by the origin.
        Rect bounds = last.getBounds();
        assertTrue(bounds.x >= ORIGIN_X, "x is absolute: " + bounds.x);
        assertTrue(bounds.y >= ORIGIN_Y, "y is absolute: " + bounds.y);

        Point center = last.getCenter();
        assertTrue(center.x > ORIGIN_X && center.y > ORIGIN_Y, "center is absolute");
    }

    @Test
    void findReturnsFalseForAbsentText() {
        assertFalse(Text.find("GAMEOVER", stub("VICTORY")), "absent text not found");
        assertFalse(VisionContext.getLastTextMatch().isFound(), "context reflects the miss");
    }

    @Test
    void readReturnsFullText() {
        String read = Text.read(stub("HELLO")).toUpperCase();
        assertTrue(read.contains("HELLO"), "read returns text: " + read);
    }
}
