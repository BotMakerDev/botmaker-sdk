package com.botmaker.sdk.plugin.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * What a pick on the overlay reports, before any {@link PickSpace}: the frame's own pixels, scaled through the
 * frame when the overlay is laid out in logical pixels, and never outside the frame.
 */
class ScreenOverlayTest {

    @Test
    void aPointIsThePixelWhoseSquareThePointerIsIn() {
        // 2× HiDPI: a 200-wide overlay over a 400-pixel frame. 10.9 logical is 21.8 device: pixel 21, the one
        // the lens's crosshair boxes, not 22 as rounding said.
        assertArrayEquals(new int[]{21, 21}, ScreenOverlay.framePoint(400, 400, 200, 200, 10.9, 10.9));
    }

    @Test
    void aPointOffTheEdgeIsTheLastPixel() {
        assertArrayEquals(new int[]{99, 0}, ScreenOverlay.framePoint(100, 100, 100, 100, 250, -4));
    }

    @Test
    void aRegionIsScaledThroughTheFrame() {
        assertArrayEquals(new int[]{20, 40, 100, 60},
                ScreenOverlay.frameRect(400, 400, 200, 200, 10, 20, 50, 30));
    }

    @Test
    void aBandDraggedPastTheEdgeIsClampedToTheFrame() {
        // The band ran 40 pixels off the right and bottom: the region stops at the frame.
        assertArrayEquals(new int[]{80, 90, 20, 10},
                ScreenOverlay.frameRect(100, 100, 100, 100, 80, 90, 60, 50));
    }

    @Test
    void aRegionIsNeverEmpty() {
        assertArrayEquals(new int[]{99, 99, 1, 1},
                ScreenOverlay.frameRect(100, 100, 100, 100, 99.8, 99.8, 0.1, 0.1));
    }
}
