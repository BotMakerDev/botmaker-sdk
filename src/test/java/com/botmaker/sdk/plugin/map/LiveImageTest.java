package com.botmaker.sdk.plugin.map;

import com.botmaker.shared.capture.FrameGrab;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveImageTest {

    @Test
    void aFrameIsWrittenIntoTheSlotNotShownAndTheSlotsSwapOnAShow() {
        LiveImage frames = new LiveImage();
        int[] first = frames.pixels(2, 1);
        first[1] = 0xFF00FF80;
        assertEquals(0x00FF80, frames.taken().getRGB(1, 0) & 0xFFFFFF);
        LiveImage.Slot shown = frames.flip();
        assertEquals(2, shown.width());
        int[] second = frames.pixels(2, 1);
        assertNotSame(first, second);
        // The next show swaps back: the first slot's array is written again, never the one on screen.
        frames.flip();
        assertSame(first, frames.pixels(2, 1));
    }

    @Test
    void aFrameOfAnotherSizeGetsAnArrayOfItsSize() {
        LiveImage frames = new LiveImage();
        assertEquals(6, frames.pixels(3, 2).length);
        assertEquals(4, frames.pixels(2, 2).length);
        assertEquals(2, frames.taken().getWidth());
    }

    @Test
    void aGrabWritesThroughTheBoardsSlots() {
        LiveImage frames = new LiveImage();
        BufferedImage frame = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        frame.setRGB(1, 1, 0xABCDEF);
        assertTrue(FrameGrab.of(() -> frame).grab(frames));
        assertEquals(0xABCDEF, frames.taken().getRGB(1, 1) & 0xFFFFFF);
    }

    @Test
    void aWindowFoundAgainIsTheSamePlaceAndAFeedOfFramesNever() {
        LiveFeed window = new LiveFeed(List.of(7L, "0,0"), () -> FrameGrab.of(() -> null));
        assertTrue(window.samePlace(new LiveFeed(List.of(7L, "0,0"), () -> FrameGrab.of(() -> null))));
        assertFalse(window.samePlace(new LiveFeed(List.of(7L, "5,0"), () -> FrameGrab.of(() -> null))));
        LiveFeed frames = LiveFeed.of(Optional::empty);
        assertFalse(frames.samePlace(LiveFeed.of(Optional::empty)));
        assertFalse(frames.samePlace(null));
    }

    @Test
    void theRateSaysWhenTheCaptureHoldsItDown() {
        assertEquals("· 30 fps, 12 ms a read, 2 ms a draw", LiveBoard.rate(30, 30, 360, 60, 1_000));
        assertEquals("· 8 fps, 120 ms a read, 3 ms a draw: the capture is the limit",
                LiveBoard.rate(8, 8, 960, 24, 1_000));
        assertEquals("· 10 fps, 20 ms a read, 50 ms a draw: drawing is the limit",
                LiveBoard.rate(10, 10, 200, 500, 1_000));
        assertEquals("· 0 fps, 0 ms a read, 0 ms a draw", LiveBoard.rate(0, 0, 0, 0, 1_000));
    }
}
