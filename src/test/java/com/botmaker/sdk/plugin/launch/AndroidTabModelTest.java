// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.emulator.AndroidDoor;
import com.botmaker.shared.emulator.AndroidPage;
import com.botmaker.shared.emulator.AppPages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Android apps tab's Starts on model; the devices and their apps are {@code emulator.apps}'. */
class AndroidTabModelTest {

    private static final String GMS = "com.google.android.gms";

    @Test
    void aSystemAppHasNoFirstScreen() {
        assertNull(StartsOn.spec(GMS, "Waydroid", false, StartsOn.Choice.FIRST_SCREEN, null, null));
    }

    @Test
    void startsOnWritesThePageUseStarts() {
        AndroidPage signIn = new AndroidPage.Door(AndroidDoor.GOOGLE_SIGN_IN);
        assertEquals("emu-app:" + GMS + "@Waydroid#google-sign-in",
                StartsOn.spec(GMS, "Waydroid", false, StartsOn.Choice.PAGE, signIn, null));
        assertEquals("emu-app:com.a@Waydroid", StartsOn.spec("com.a", "Waydroid", true,
                StartsOn.Choice.FIRST_SCREEN, null, null));
        assertNull(StartsOn.spec(GMS, "Waydroid", false, StartsOn.Choice.PAGE, null, null));
        assertEquals("emu-app:" + GMS + "@Waydroid#google-sign-in", StartsOn.spec(GMS, "Waydroid", false,
                StartsOn.Choice.OPEN_NOW, null, new AppPages.Reached(signIn)));
    }

    @Test
    void anUnreachablePageOpenNowDisablesUse() {
        AppPages.Now unreachable = new AppPages.Unreachable(GMS + "/.auth.Hidden");
        assertNull(StartsOn.spec(GMS, "Waydroid", true, StartsOn.Choice.OPEN_NOW, null, unreachable));
        StartsOn.Line line = StartsOn.line(GMS, "Waydroid", unreachable);
        assertFalse(line.ok());
        assertTrue(line.text().contains("the bot's work"), line.text());
        assertNull(StartsOn.spec(GMS, "Waydroid", true, StartsOn.Choice.OPEN_NOW, null, new AppPages.NoPage()));
        // Another app's page is not this app's start.
        AppPages.Now settings = new AppPages.Reached(new AndroidPage.Door(AndroidDoor.WIFI));
        assertNull(StartsOn.spec(GMS, "Waydroid", true, StartsOn.Choice.OPEN_NOW, null, settings));
        assertFalse(StartsOn.line(GMS, "Waydroid", settings).ok());
        assertTrue(StartsOn.line(GMS, "Waydroid",
                new AppPages.Reached(new AndroidPage.Door(AndroidDoor.GOOGLE_SIGN_IN))).ok());
    }

    @Test
    void pagesAreListedByPlainName() {
        assertEquals("Google's sign-in page", StartsOn.label(new AndroidPage.Door(AndroidDoor.GOOGLE_SIGN_IN)));
        assertEquals(".wifi.WifiSettings", StartsOn.label(new AndroidPage.Activity("com.android.settings/.wifi.WifiSettings")));
    }
}
