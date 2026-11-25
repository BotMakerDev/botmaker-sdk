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

    /** A refocused game with a settings dialog open: its biggest window, of the class its target names, is the pick. */
    @Test
    void theRefocusedWindowIsTheGamesBiggest() {
        GenericWindow settings = window(1, "Settings", 400, 7, "steam_app_440");
        GenericWindow game = window(2, "Team Fortress 2", 1280, 7, "steam_app_440");
        GenericWindow other = window(3, "Honkai: Star Rail", 1920, 9, "steam_app_1965");
        List<GenericWindow> open = List.of(settings, game, other);
        assertEquals("Team Fortress 2", NewWindows.refocused(NewWindows.handles(open), open, "steam_app_440")
                .window().getTitle());
        assertNull(NewWindows.refocused(NewWindows.handles(open), open, null));
        assertNull(NewWindows.refocused(Set.of(), open, "steam_app_440"), "a new window is no refocus");
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

    /** Two Wine windows of one size, neither the launch's: only size could decide, so it is a doubt; a launcher is not in it. */
    @Test
    void twoWineWindowsOfOneSizeAreADoubt() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Balatro", 1280, 8, "wine"),
                window(2, "Battle.net", 1280, 9, "wine"), window(3, "Steam", 1600, STEAM, "steam")),
                pid -> pid == STEAM, pid -> false);
        List<Candidate> doubt = NewWindows.doubt(ranked, null);
        assertEquals(2, doubt.size());
        assertTrue(doubt.getFirst().recommended(), "the guess first");
        assertTrue(doubt.stream().noneMatch(Candidate::launcher));
    }

    /** The window the launched process owns wins by its owner, bigger strangers or not: no doubt. */
    @Test
    void theLaunchsTreeLeavesNoDoubt() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Game", 1280, LAUNCHED, "wine"),
                window(2, "Battle.net", 1920, 9, "wine")), pid -> false, pid -> pid == LAUNCHED);
        assertTrue(NewWindows.doubt(ranked, null).isEmpty());
    }

    /** A Steam game's own class decides, even beside another window of that class: no doubt. */
    @Test
    void theGamesClassLeavesNoDoubt() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Balatro", 1280, 8, "steam_app_2379780"),
                window(2, "Balatro settings", 1280, 8, "steam_app_2379780"), window(3, "Other", 1280, 9, "wine")),
                pid -> false, pid -> false, "steam_app_2379780");
        assertTrue(NewWindows.doubt(ranked, "steam_app_2379780").isEmpty());
    }

    /** One window alone is no doubt. */
    @Test
    void oneWindowIsNoDoubt() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Balatro", 1280, 8, "wine")),
                pid -> false, pid -> false);
        assertTrue(NewWindows.doubt(ranked, null).isEmpty());
    }
}
