// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.internal.capture.NewWindows.Candidate;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.launch.LaunchSpec;
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

    /**
     * The live check of 2026-11-25: Balatro launched through Steam while Honkai: Star Rail ran under Proton. Proton's
     * untitled window was picked and matched Honkai's; now it is no candidate, and Balatro's own class wins over a
     * bigger window the launch's tree owns.
     */
    @Test
    void aSteamGamesOwnClassWinsAndProtonsPlaceholderIsNone() {
        GenericWindow honkai = window(1, "Honkai: Star Rail", 1920, 70, "steam_proton");
        GenericWindow placeholder = window(2, "steam_proton", 1920, LAUNCHED, "steam_proton");
        GenericWindow balatro = window(3, "Balatro", 1280, 400, "steam_app_2379780");
        List<Candidate> ranked = NewWindows.ranked(NewWindows.handles(List.of(honkai)),
                List.of(honkai, placeholder, balatro), pid -> false, pid -> pid == LAUNCHED,
                NewWindows.gameClass(LaunchSpec.parse("steam:2379780")));
        assertEquals(List.of("steam_app_2379780"), ranked.stream().map(c -> c.identity().spec()).toList());
    }

    /** A native Steam game's window has a class of its own: the launch's tree, then size, decide as before. */
    @Test
    void aNativeSteamGameFallsBackToTheLaunchsTree() {
        GenericWindow stranger = window(1, "Browser", 1900, 50, "firefox");
        GenericWindow game = window(2, "Hades II", 1280, LAUNCHED, "hades2.x86_64");
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(stranger, game), pid -> false,
                pid -> pid == LAUNCHED, NewWindows.gameClass(LaunchSpec.parse("steam:1145350")));
        assertEquals("hades2.x86_64", NewWindows.recommended(ranked).identity().spec());
    }

    @Test
    void onlyASteamTargetNamesItsClass() {
        assertEquals("steam_app_2379780", NewWindows.gameClass(LaunchSpec.parse("steam:2379780")));
        assertNull(NewWindows.gameClass(LaunchSpec.parse("lutris:rungameid/3")));
        assertNull(NewWindows.gameClass(null));
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
