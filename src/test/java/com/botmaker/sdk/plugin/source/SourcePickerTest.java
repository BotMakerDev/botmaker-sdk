// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Choose capture source window's tabs, what its Automatic tab says, and its region row. */
class SourcePickerTest {

    private static final LaunchSpec WAYDROID = LaunchSpec.parse("emu-app:com.foo@Waydroid");

    @Test
    void opensOnTheTabOfTheCurrentValue() {
        assertEquals(SourceTab.AUTOMATIC, SourceTab.opening(CaptureSource.automatic(), WAYDROID, true, false));
        assertEquals(SourceTab.AUTOMATIC, SourceTab.opening(null, null, true, false));
        assertEquals(SourceTab.WINDOWS,
                SourceTab.opening(CaptureSource.window("steam_app_0", "Game"), WAYDROID, true, false));
        assertEquals(SourceTab.DEVICES, SourceTab.opening(CaptureSource.emulator("Waydroid"), WAYDROID, true, false));
        assertEquals(SourceTab.SCREENS, SourceTab.opening(CaptureSource.monitor(1), WAYDROID, true, false));
        assertEquals(SourceTab.WINDOWS, SourceTab.opening(
                CaptureSource.window("steam_app_0", "Game").region(new Rect(0, 0, 5, 5)), WAYDROID, true, false),
                "a region opens on the tab of what it narrows");
    }

    @Test
    void aSurfaceOnlyPickerHasNoAutomaticTab() {
        assertFalse(SourceTab.shown(false, false).contains(SourceTab.AUTOMATIC));
        assertEquals(SourceTab.SCREENS, SourceTab.opening(null, null, false, false));
        assertEquals(SourceTab.AUTOMATIC, SourceTab.shown(true, false).get(0));
    }

    @Test
    void theGameVmTabOnlyWithAVm() {
        assertEquals(List.of(SourceTab.AUTOMATIC, SourceTab.WINDOWS, SourceTab.DEVICES, SourceTab.SCREENS),
                SourceTab.shown(true, false));
        assertTrue(SourceTab.shown(true, true).contains(SourceTab.VM));
        assertEquals(SourceTab.VM,
                SourceTab.opening(CaptureSource.window("steam_app_0", "Game"), WAYDROID, true, true));
    }

    @Test
    void thePillSaysWhatAutomaticReads() {
        assertEquals("Waydroid (from the game)", AutomaticTab.reads(WAYDROID, Optional.empty()));
        assertEquals("whole desktop (no game set)", AutomaticTab.reads(null, Optional.empty()));
        assertEquals("Automatic", AutomaticTab.pillLabel(WAYDROID, Optional.empty()));
    }

    @Test
    void theRegionFieldsParseOnlyAWholeRectangle() {
        assertEquals(new Rectangle(1, 2, 3, 4), SourceStrip.parseRegion("1", " 2", "3 ", "4"));
        assertNull(SourceStrip.parseRegion("1", "2", "3", ""));
        assertNull(SourceStrip.parseRegion("1", "2", "0", "4"));
        assertNull(SourceStrip.parseRegion("-1", "2", "3", "4"));
        assertNull(SourceStrip.parseRegion("a", "2", "3", "4"));
    }
}
