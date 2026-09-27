package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.sdk.internal.capture.RegionSource;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.source.SourcePicker;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What each pick in the capture-source picker writes, and how the pill names a value. */
class CaptureSourceEditorsTest {

    @Test
    void theProjectDefaultTileWritesSourceCurrent() {
        assertInstanceOf(CurrentSource.class,
                CaptureSourceEditors.valueOf(new SourcePicker.Selection.ProjectDefault()));
    }

    @Test
    void aWholeSourceIsWrittenAsItself() {
        CaptureSource window = CaptureSource.window("Game");
        assertSame(window, CaptureSourceEditors.valueOf(new SourcePicker.Selection.Concrete(window)));
    }

    @Test
    void aRegionSourceIsTheParentPlusItsRectangle() {
        CaptureSource window = CaptureSource.window("Game");
        CaptureSource value = CaptureSourceEditors.valueOf(
                new SourcePicker.Selection.Concrete(window, new Rectangle(10, 20, 300, 40)));
        RegionSource region = assertInstanceOf(RegionSource.class, value);
        assertSame(window, region.parent());
        assertEquals(new Rect(10, 20, 300, 40), region.sub());
    }

    @Test
    void theProjectDefaultIsLabelledAsSuch() {
        assertEquals("Project default", CaptureSourceEditors.label(new CurrentSource()));
    }

    @Test
    void aRegionIsLabelledByWhatItNarrows() {
        assertEquals("Game (region)",
                CaptureSourceEditors.label(CaptureSource.window("Game").region(new Rect(0, 0, 5, 5))));
        assertEquals("Screen 2", CaptureSourceEditors.label(CaptureSource.monitor(1)));
    }

    @Test
    void twoSourcesAreTheSameWhenTheyNameTheSameThing() {
        assertTrue(CaptureLabels.same(CaptureSource.window("Game"), CaptureSource.window("Game")));
        assertFalse(CaptureLabels.same(CaptureSource.window("Game"), CaptureSource.window("Other")));
        assertFalse(CaptureLabels.same(CaptureSource.monitor(0), CaptureSource.desktop()));
        assertFalse(CaptureLabels.same(null, CaptureSource.desktop()));
    }
}
