package com.botmaker.sdk.plugin.map;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveImageTest {

    private static int[] argb(BufferedImage frame) {
        int[] into = new int[frame.getWidth() * frame.getHeight()];
        LiveImage.argb(frame, into);
        return into;
    }

    @Test
    void anIntFrameIsCopiedWholeAndOpaque() {
        BufferedImage frame = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        frame.setRGB(0, 0, 0x123456);
        frame.setRGB(1, 1, 0xABCDEF);
        assertArrayEquals(new int[]{0xFF123456, 0xFF000000, 0xFF000000, 0xFFABCDEF}, argb(frame));
    }

    @Test
    void anyOtherFrameIsReadThroughItsColours() {
        BufferedImage frame = new BufferedImage(2, 1, BufferedImage.TYPE_3BYTE_BGR);
        frame.setRGB(1, 0, 0x00FF80);
        assertArrayEquals(new int[]{0xFF000000, 0xFF00FF80}, argb(frame));
    }

    @Test
    void aPartOfALargerFrameIsReadAsItsOwn() {
        BufferedImage whole = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        whole.setRGB(1, 1, 0x010203);
        assertArrayEquals(new int[]{0xFF010203, 0xFF000000}, argb(whole.getSubimage(1, 1, 2, 1)));
    }

    @Test
    void theRateSaysWhenTheCaptureHoldsItDown() {
        assertEquals("· 30 fps, 12 ms a read", LiveBoard.rate(30, 30, 360, 1_000));
        assertEquals("· 8 fps, 120 ms a read: the capture is the limit", LiveBoard.rate(8, 8, 960, 1_000));
        assertEquals("· 0 fps, 0 ms a read", LiveBoard.rate(0, 0, 0, 1_000));
    }
}
