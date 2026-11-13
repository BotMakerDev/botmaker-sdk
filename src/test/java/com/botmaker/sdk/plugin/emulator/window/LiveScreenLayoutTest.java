package com.botmaker.sdk.plugin.emulator.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveScreenLayoutTest {

    private static final double CHROME_W = 20;
    private static final double CHROME_H = 150;

    @Test
    void aDrawingKeepsTheFramesRatioInsideItsBox() {
        LiveScreenLayout.Size portrait = LiveScreenLayout.fitted(1080, 1920, 400, 400);
        assertEquals(225, portrait.width(), 0.001);
        assertEquals(400, portrait.height(), 0.001);
        LiveScreenLayout.Size landscape = LiveScreenLayout.fitted(1600, 900, 400, 400);
        assertEquals(400, landscape.width(), 0.001);
        assertEquals(225, landscape.height(), 0.001);
        assertEquals(new LiveScreenLayout.Size(0, 0), LiveScreenLayout.fitted(0, 0, 400, 400));
    }

    @Test
    void aPortraitScreenFitsAScaledLaptopScreen() {
        // a 1920×1080 screen at 150 %: 1280×672 free once the taskbar is off
        LiveScreenLayout.Size window = LiveScreenLayout.window(1080, 1920, CHROME_W, CHROME_H, 1280, 672, 200, 300);
        assertTrue(window.height() <= 672 * LiveScreenLayout.SHARE + 0.001, "the window fits: " + window);
        double drawingH = window.height() - CHROME_H;
        assertEquals(1080.0 / 1920, (window.width() - CHROME_W) / drawingH, 0.01);
    }

    @Test
    void aLandscapeScreenTakesTheWidth() {
        LiveScreenLayout.Size window = LiveScreenLayout.window(1600, 900, CHROME_W, CHROME_H, 1920, 1032, 320, 420);
        assertTrue(window.width() <= 1920 * LiveScreenLayout.SHARE + 0.001, "the window fits: " + window);
        assertTrue(window.height() <= 1032 * LiveScreenLayout.SHARE + 0.001, "the window fits: " + window);
        assertEquals(1600.0 / 900, (window.width() - CHROME_W) / (window.height() - CHROME_H), 0.01);
    }

    @Test
    void theMinimumHoldsButNeverPassesTheScreen() {
        LiveScreenLayout.Size tiny = LiveScreenLayout.window(1080, 1920, CHROME_W, CHROME_H, 300, 400, 320, 420);
        assertEquals(300, tiny.width(), 0.001);
        assertEquals(400, tiny.height(), 0.001);
    }

    @Test
    void aWindowIsMovedBackInsideTheScreen() {
        assertEquals(1000, LiveScreenLayout.inside(1500, 500, 0, 1500), 0.001);
        assertEquals(0, LiveScreenLayout.inside(-40, 500, 0, 1500), 0.001);
        assertEquals(200, LiveScreenLayout.inside(200, 500, 0, 1500), 0.001);
    }

    @Test
    void aResizedWindowKeepsItsCentreInsideTheScreen() {
        assertEquals(400, LiveScreenLayout.centred(500, 400, 600, 0, 1500), 0.001);
        assertEquals(0, LiveScreenLayout.centred(100, 400, 1200, 0, 1500), 0.001);
    }

    @Test
    void aTapLandsOnTheSamePixelAtAnySize() {
        for (double shownH : new double[] {320, 640, 960}) {
            double shownW = shownH * 1080 / 1920;
            RunningModel.Tap tap = RunningModel.tap(shownW / 2, shownH / 4, shownW, shownH, 1080, 1920).orElseThrow();
            assertEquals(540, tap.x(), 1);
            assertEquals(480, tap.y(), 1);
        }
    }
}
