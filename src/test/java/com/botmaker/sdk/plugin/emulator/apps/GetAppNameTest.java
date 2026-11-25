// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.apps;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GetAppNameTest {

    @Test
    void aNameIsSearchedButAPackageOrALinkIsNot() {
        assertEquals(Optional.of("Sword x Staff"), GetAppDialog.nameIn("  Sword x Staff "));
        assertEquals(Optional.empty(), GetAppDialog.nameIn("com.example.game"));
        assertEquals(Optional.empty(),
                GetAppDialog.nameIn("https://play.google.com/store/apps/details?id=com.example.game"));
        assertEquals(Optional.empty(), GetAppDialog.nameIn("a"));
        assertEquals(Optional.empty(), GetAppDialog.nameIn(null));
    }
}
