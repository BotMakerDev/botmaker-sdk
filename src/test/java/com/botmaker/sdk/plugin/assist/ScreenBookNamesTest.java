// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.screens.ScreenGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenBookNamesTest {

    private final ScreenGraph empty = ScreenGraph.of(Map.of());

    @Test
    void aScreenCalledScreenIsNotTheClassItExtends() {
        assertEquals("PAGE", ScreenBook.constantFor(empty, "Screen"));
        assertEquals("PAGE", ScreenBook.constantFor(empty, "screen"));
    }

    @Test
    void aNameNoConstantCanBeFallsBackToAClassThatIsNotTheBase() {
        // "123": no letter, so no constant: the fallback used to be SCREEN, the class Screen.
        assertEquals("PAGE", ScreenBook.constantFor(empty, "123"));
    }

    @Test
    void aNameTheScreensFileImportsKeepsASuffix() {
        assertEquals("MARK_SCREEN", ScreenBook.constantFor(empty, "Mark"));
        assertEquals("GUILD_HALL", ScreenBook.constantFor(empty, "Guild hall"));
    }
}
