package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.sdk.api.geometry.Rect;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PictureCutsTest {

    @Test
    void aBoxInDesktopPixelsIsCutFromWhereTheFrameSits() {
        BufferedImage frame = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        frame.setRGB(30, 20, 0xFF0000);
        PictureCuts.Piece piece = PictureCuts.crop(frame, new Area(1000, 500, 200, 100), new Area(1030, 520, 10, 5))
                .orElseThrow();
        assertEquals(new Rect(30, 20, 10, 5), piece.place(), "the box in the frame's own pixels");
        BufferedImage cut = piece.image();
        assertEquals(10, cut.getWidth());
        assertEquals(5, cut.getHeight());
        assertEquals(0xFF0000, cut.getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    void aBoxOverTheEdgeIsClippedAndOneOutsideIsNothing() {
        BufferedImage frame = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        assertEquals(new Rect(180, 0, 20, 10), PictureCuts.crop(frame, null, new Area(180, 0, 50, 10)).orElseThrow()
                .place());
        assertTrue(PictureCuts.crop(frame, null, new Area(300, 0, 10, 10)).isEmpty());
        assertTrue(PictureCuts.crop(frame, null, new Area(0, 0, 0, 10)).isEmpty());
        assertTrue(PictureCuts.crop(null, null, new Area(0, 0, 5, 5)).isEmpty());
    }
}
