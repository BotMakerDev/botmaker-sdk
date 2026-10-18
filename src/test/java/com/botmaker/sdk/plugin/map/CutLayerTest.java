package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Rect;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cut drawn on the board, in the frame's pixels. */
class CutLayerTest {

    @Test
    void aBoxDrawnAtHalfSizeIsTwiceAsBigOnTheFrame() {
        assertEquals(new Rect(20, 40, 60, 30), CutLayer.toFrame(10, 20, 30, 15, 0.5, 1280, 720));
    }

    @Test
    void aBoxStaysOnTheFrame() {
        assertEquals(new Rect(90, 0, 10, 50), CutLayer.toFrame(90, -5, 40, 55, 1, 100, 100));
        // Never empty, at the frame's last pixel either.
        assertEquals(new Rect(99, 99, 1, 1), CutLayer.toFrame(120, 120, 5, 5, 1, 100, 100));
    }

    @Test
    void theSkippedPartIsWhatLiesInTheCut() {
        Rect cut = new Rect(10, 10, 20, 20);
        assertEquals(Optional.of(new Rect(25, 10, 5, 5)), CutLayer.inside(new Rect(25, 5, 10, 10), cut));
        assertEquals(Optional.empty(), CutLayer.inside(new Rect(40, 40, 5, 5), cut));
        assertTrue(CutLayer.holds(cut, new Rect(12, 12, 4, 4)));
        assertFalse(CutLayer.holds(cut, new Rect(25, 5, 30, 30)));
    }
}
