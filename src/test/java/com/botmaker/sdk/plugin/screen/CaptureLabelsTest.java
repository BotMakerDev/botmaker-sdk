package com.botmaker.sdk.plugin.screen;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.CurrentSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A region is its surface, narrowed: every "which surface" question is answered for the surface, and the
 * labels say it is a region. Until 2026-09-28 a region read as the whole desktop, so a narrowed project grabbed
 * the desktop in every editor.
 */
class CaptureLabelsTest {

    private static final CaptureSource GAME = CaptureSource.window("Game");

    @Test
    void aRegionOfAWindowReadsThatWindow() {
        CaptureSource narrowed = GAME.region(new Rect(10, 20, 300, 40));
        assertSame(GAME, CaptureLabels.whole(narrowed));
        assertEquals("Game", CaptureLabels.windowTitle(narrowed));
        assertFalse(CaptureLabels.isDesktop(narrowed));
    }

    @Test
    void aRegionOfAMonitorOrAnEmulatorReadsThatSurface() {
        assertEquals(2, CaptureLabels.monitorIndex(CaptureSource.monitor(2).region(new Rect(0, 0, 5, 5))));
        assertFalse(CaptureLabels.isDesktop(CaptureSource.monitor(2).region(new Rect(0, 0, 5, 5))));
    }

    @Test
    void aRegionOfARegionIsTheInnerMovedByTheOuter() {
        CaptureSource twice = GAME.region(new Rect(100, 50, 400, 300)).region(new Rect(10, 20, 30, 40));
        assertEquals(new Rect(110, 70, 30, 40), CaptureLabels.region(twice));
        assertNull(CaptureLabels.region(GAME));
    }

    @Test
    void theLabelsSayARegionIsOne() {
        CaptureSource narrowed = GAME.region(new Rect(10, 20, 300, 40));
        assertEquals("Game (region)", CaptureLabels.shortLabel(narrowed));
        assertEquals("Window: Game — region 300×40 at (10, 20)", CaptureLabels.longLabel(narrowed));
        assertEquals("Project default", CaptureLabels.shortLabel(new CurrentSource()));
        assertEquals("Whole desktop", CaptureLabels.shortLabel(null));
    }

    @Test
    void twoRegionsAreTheSameOnlyWithTheSameRectangle() {
        assertTrue(CaptureLabels.same(GAME.region(new Rect(1, 2, 3, 4)), CaptureSource.window("Game")
                .region(new Rect(1, 2, 3, 4))));
        assertFalse(CaptureLabels.same(GAME.region(new Rect(1, 2, 3, 4)), GAME.region(new Rect(1, 2, 3, 5))));
        assertFalse(CaptureLabels.same(GAME.region(new Rect(1, 2, 3, 4)), GAME));
    }
}
