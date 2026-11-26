// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.sdk.api.bot.BotSettings.Where;
import com.botmaker.sdk.plugin.source.LaunchAndPick.Place;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ▶ Launch and pick starts the game where the bot's settings say, or on this desktop with the reason. */
class LaunchPlaceTest {

    @Test
    void theSettingsPlaceIsUsedWhenNothingRefusesIt() {
        Place place = LaunchAndPick.place(Where.PRIVATE_DISPLAY, where -> Optional.empty());
        assertEquals(Where.PRIVATE_DISPLAY, place.where());
        assertNull(place.refused());
        assertTrue(place.describe().startsWith("It starts in a private display"));
    }

    @Test
    void aRefusedPlaceFallsBackToThisDesktopAndSaysWhy() {
        Place place = LaunchAndPick.place(Where.PRIVATE_DISPLAY, where -> Optional.of("Install gamescope first."));
        assertEquals(Where.MY_DESKTOP, place.where());
        assertEquals("It starts on this desktop for this pick only: Install gamescope first. The bot's runs still "
                + "start it where that setting says.", place.describe());
    }

    @Test
    void theGameVmIsUsedWhenOneIsReadyAndThisDesktopWhenNone() {
        Place ready = LaunchAndPick.place(Where.VM, where -> Optional.empty());
        assertEquals(Where.VM, ready.where());
        assertTrue(ready.describe().startsWith("It starts in the game VM, as"));

        Place none = LaunchAndPick.place(Where.VM, where -> Optional.of("There's no game VM."));
        assertEquals(Where.MY_DESKTOP, none.where());
        assertTrue(none.describe().contains("There's no game VM."));
    }

    @Test
    void thisDesktopIsNeverAskedAbout() {
        Place place = LaunchAndPick.place(Where.MY_DESKTOP, where -> {
            throw new AssertionError("asked about " + where);
        });
        assertEquals(Where.MY_DESKTOP, place.where());
        assertEquals("It starts on this desktop, as the bot's \"Run the game in\" setting says.", place.describe());
    }
}
