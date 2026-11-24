// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenClassNamesTest {

    @Test
    void aScreenNeverTakesTheNameOfTheClassItExtends() {
        assertTrue(ScreenClassNames.clashes("Screen"));
        assertEquals("Page", ScreenClassNames.className("Screen"));
        // The constant a screen called "Screen" gets is the one its class, Page, is written from.
        assertEquals("PAGE", ScreenClassNames.constant("SCREEN"));
    }

    @Test
    void aNameTheScreensFileImportsOrJavaLangHoldsKeepsAScreenSuffix() {
        assertEquals("MarkScreen", ScreenClassNames.className("Mark"));
        assertEquals("ObjectScreen", ScreenClassNames.className("Object"));
        assertEquals("MARK_SCREEN", ScreenClassNames.constant("MARK"));
        assertEquals("SDK_VALUE_SCREEN", ScreenClassNames.constant("SDK_VALUE"));
    }

    @Test
    void aFreeNameIsLeftAlone() {
        assertFalse(ScreenClassNames.clashes("Settings"));
        assertEquals("Settings", ScreenClassNames.className("Settings"));
        assertEquals("GUILD_HALL", ScreenClassNames.constant("GUILD_HALL"));
        // A numbered twin keeps its number: SCREEN_2 is the class Screen2, which clashes with nothing.
        assertEquals("SCREEN_2", ScreenClassNames.constant("SCREEN_2"));
    }
}
