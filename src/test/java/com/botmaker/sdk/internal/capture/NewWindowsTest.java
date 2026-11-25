// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.internal.capture.NewWindows.Candidate;
import com.botmaker.shared.capture.GenericWindow;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which of the windows a launch opened is the game's. */
class NewWindowsTest {

    private static final long LAUNCHED = 300;
    private static final long STEAM = 100;

    private static GenericWindow window(long handle, String title, int width, long pid, String windowClass) {
        return new GenericWindow(handle, title, new Rectangle(0, 0, width, width), pid, windowClass);
    }

    @Test
    void windowOfLaunchedTreeWinsOverBiggerStranger() {
        GenericWindow stranger = window(1, "Browser", 1900, 50, "firefox");
        GenericWindow game = window(2, "Game", 800, LAUNCHED, "game.exe");
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(stranger, game), pid -> false,
                pid -> pid == LAUNCHED);
        assertEquals("game.exe", NewWindows.recommended(ranked).identity().spec());
        assertTrue(ranked.getFirst().ofLaunch());
    }

    /** Steam closed: the launch starts Steam itself, and its library is in the launch's tree — still not the game. */
    @Test
    void launcherInLaunchTreeStaysLast() {
        GenericWindow library = window(1, "Steam", 1600, LAUNCHED, "steam");
        GenericWindow game = window(2, "Game", 800, 400, "steam_app_1");
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(library, game), pid -> pid == LAUNCHED,
                pid -> pid == LAUNCHED);
        assertEquals("steam_app_1", NewWindows.recommended(ranked).identity().spec());
        assertTrue(ranked.getLast().launcher());
    }

    @Test
    void launcherOnlyIsNoPick() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Steam", 1200, STEAM, "steam")),
                pid -> pid == STEAM, pid -> false);
        assertEquals(1, ranked.size());
        assertNull(NewWindows.recommended(ranked));
    }

    @Test
    void splashAndGameSameClassOnce() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Loading", 300, LAUNCHED, "game"),
                window(2, "Game", 1280, LAUNCHED, "game")), pid -> false, pid -> pid == LAUNCHED);
        assertEquals(1, ranked.size());
        assertEquals("Game", ranked.getFirst().window().getTitle());
    }
}
