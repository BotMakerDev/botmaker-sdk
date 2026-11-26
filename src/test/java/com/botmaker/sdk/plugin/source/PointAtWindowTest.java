// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.sdk.plugin.source.PointAtWindow.Guard;
import com.botmaker.sdk.plugin.source.PointAtWindow.Level;
import com.botmaker.shared.capture.GenericWindow;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the confirm step says about the window the user pointed at. */
class PointAtWindowTest {

    private static final long STUDIO = 4242;

    private static GenericWindow window(String title, long pid, String windowClass) {
        return new GenericWindow(title, title, new Rectangle(0, 0, 1280, 720), pid, windowClass);
    }

    @Test
    void studiosOwnWindowIsRefused() {
        Optional<Guard> guard = PointAtWindow.guard(window("BotMaker Studio", STUDIO, "java"), STUDIO);
        assertEquals(Level.REFUSED, guard.orElseThrow().level());
        assertEquals("That's BotMaker Studio — point at the game", guard.get().text());
    }

    @Test
    void steamsWindowIsWarnedAsALauncher() {
        Optional<Guard> guard = PointAtWindow.guard(window("Steam", 10, "steam"), STUDIO);
        assertEquals(Level.WARNED, guard.orElseThrow().level());
        assertTrue(guard.get().text().startsWith("This looks like steam, a launcher"), guard.get().text());
    }

    @Test
    void anUntitledProtonWindowIsRefusedSinceNothingNamesIt() {
        Optional<Guard> guard = PointAtWindow.guard(window("", 11, "steam_proton"), STUDIO);
        assertEquals(Level.REFUSED, guard.orElseThrow().level());
        assertTrue(guard.get().text().startsWith("BotMaker can't name this window"), guard.get().text());
    }

    @Test
    void anUntitledWindowOfItsOwnClassIsWarned() {
        Optional<Guard> guard = PointAtWindow.guard(window("", 13, "hades2.exe"), STUDIO);
        assertEquals(new Guard(Level.WARNED, "This window has no name yet; the next run may not find it again"),
                guard.orElseThrow());
    }

    @Test
    void aTitledGameWindowHasNoGuard() {
        assertEquals(Optional.empty(), PointAtWindow.guard(window("Hades II", 12, "steam_app_1145350"), STUDIO));
    }

    @Test
    void aWindowWithNoKnownPidIsNotTakenForStudio() {
        assertEquals(Optional.empty(), PointAtWindow.guard(window("Hades II", GenericWindow.NO_PID,
                "steam_app_1145350"), GenericWindow.NO_PID));
    }
}
