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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Windows, Steam closed before: its login and library come up with the game, which Steam starts under its own
     * program, so neither the launch's tree nor the class of Proton's windows names it.
     */
    @Test
    void windowsSteamGameWinsOverSteam() {
        List<Candidate> ranked = onWindows("steam:2379780", window(1, "Steam", 1600, 10, "steamwebhelper.exe"),
                window(2, "Sign in to Steam", 700, 11, "steam.exe"), window(3, "Balatro", 1280, 12, "Balatro.exe"));
        assertGameFirst("Balatro.exe", ranked, 2);
    }

    @Test
    void windowsEpicGameWinsOverEpic() {
        List<Candidate> ranked = onWindows("epic:Fortnite",
                window(1, "Epic Games Launcher", 1600, 10, "EpicGamesLauncher.exe"),
                window(2, "Fortnite", 1280, 12, "FortniteClient-Win64-Shipping.exe"));
        assertGameFirst("FortniteClient-Win64-Shipping.exe", ranked, 1);
    }

    /** A link to a store: Ubisoft Connect and Battle.net are bigger than the game and are not it. */
    @Test
    void windowsStoreLinksGameWinsOverTheStore() {
        assertGameFirst("Trackmania.exe", onWindows("url:uplay://launch/5595/0",
                window(1, "Ubisoft Connect", 1600, 10, "upc.exe"),
                window(2, "Trackmania", 1280, 12, "Trackmania.exe")), 1);
        assertGameFirst("Hearthstone.exe", onWindows("url:battlenet://WTCG",
                window(1, "Battle.net", 1600, 10, "Battle.net.exe"),
                window(2, "Hearthstone", 1280, 12, "Hearthstone.exe")), 1);
    }

    /** Every Store app's window is ApplicationFrameHost's: its title says which, and an untitled frame is none. */
    @Test
    void windowsStoreAppIsSavedWithItsTitle() {
        List<Candidate> ranked = onWindows("desktop:Microsoft.MicrosoftSolitaireCollection_8wekyb3d8bbwe!App",
                window(1, "", 1600, 10, "ApplicationFrameHost.exe"),
                window(2, "Solitaire & Casual Games", 1280, 10, "ApplicationFrameHost.exe"));
        assertGameFirst("ApplicationFrameHost.exe|Solitaire & Casual Games", ranked, 0);
    }

    /** A plain program is the launched process itself: it wins over a bigger stranger, with no doubt. */
    @Test
    void windowsPlainProgramWinsByItsProcess() {
        List<Candidate> ranked = onWindows("exe:C:\\Games\\Celeste\\Celeste.exe",
                window(1, "Discord", 1900, 10, "Discord.exe"),
                window(2, "Celeste", 1280, LAUNCHED, "Celeste.exe"));
        assertEquals("Celeste.exe", NewWindows.recommended(ranked).identity().spec());
        assertTrue(NewWindows.doubt(ranked, null).isEmpty());
    }

    /** A target that is the store itself: its own window, which the launched process owns, is the pick. */
    @Test
    void windowsStoreItselfCanBeTheTarget() {
        List<Candidate> ranked = onWindows("exe:C:\\Battle.net\\Battle.net.exe",
                window(1, "Battle.net", 1280, LAUNCHED, "Battle.net.exe"));
        assertEquals("Battle.net.exe", NewWindows.recommended(ranked).identity().spec());
    }

    /** What a launch of {@code target} opened, its launchers told by class alone: no process of the fixture runs here. */
    private static List<Candidate> onWindows(String target, GenericWindow... opened) {
        return NewWindows.ranked(Set.of(), List.of(opened), pid -> false, pid -> pid == LAUNCHED,
                NewWindows.gameClass(LaunchSpec.parse(target)));
    }

    /** The game first and in no doubt, and the {@code launchers} windows after it. */
    private static void assertGameFirst(String identity, List<Candidate> ranked, int launchers) {
        assertEquals(1 + launchers, ranked.size());
        assertEquals(identity, NewWindows.recommended(ranked).identity().spec());
        assertTrue(ranked.getFirst().recommended());
        assertTrue(ranked.stream().skip(1).allMatch(Candidate::launcher));
        assertTrue(NewWindows.doubt(ranked, null).isEmpty());
    }

    /**
     * Lutris's Wine game, which the launch's tree does not own, beside a bigger Wine window: the process its launcher
     * named (2026-11-26) is the game's, and leaves no doubt.
     */
    @Test
    void theWindowItsProcessNamesWinsAndLeavesNoDoubt() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Battle.net", 1600, 9, "wine"),
                window(2, "Trackmania", 1280, 8, "wine")), pid -> false, pid -> false, null, pid -> pid == 8);
        assertEquals("wine|Trackmania", NewWindows.recommended(ranked).identity().spec());
        assertTrue(ranked.getFirst().marked());
        assertTrue(NewWindows.doubt(ranked, null).isEmpty());
    }

    /** The game's class still goes first: a marked window of another class is not preferred to it. */
    @Test
    void theGamesClassGoesBeforeAMarkedWindow() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Crash reporter", 1600, 8, "wine"),
                window(2, "Balatro", 1280, 8, "steam_app_2379780")), pid -> false, pid -> false,
                "steam_app_2379780", pid -> pid == 8);
        assertEquals("steam_app_2379780", NewWindows.recommended(ranked).identity().spec());
    }

    /** Lutris's Battle.net as the target: its window, a store's by class, is the game since its launcher named it. */
    @Test
    void aMarkedWindowOfAStoresClassIsTheGame() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Battle.net", 1280, 8, "Battle.net.exe")),
                pid -> false, pid -> false, null, pid -> pid == 8);
        assertFalse(ranked.getFirst().launcher());
        assertEquals("Battle.net.exe", NewWindows.recommended(ranked).identity().spec());
    }

    /** A pre-launcher and the game inherit one environment: two named windows only size tells apart are a doubt. */
    @Test
    void twoMarkedWindowsAreADoubt() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Launcher", 900, 8, "wine"),
                window(2, "Game", 1280, 9, "wine"), window(3, "Other", 1600, 7, "wine")),
                pid -> false, pid -> false, null, pid -> pid == 8 || pid == 9);
        List<Candidate> doubt = NewWindows.doubt(ranked, null);
        assertEquals(List.of("wine|Game", "wine|Launcher"),
                doubt.stream().map(c -> c.identity().spec()).toList());
    }

    /** A Lutris target names no class, since that would read its library: Lutris's CLI may take seconds. */
    @Test
    void aLutrisTargetReadsNoLibraryForItsClass() {
        assertNull(NewWindows.gameClass(LaunchSpec.parse("lutris:3")));
    }

    /** One window alone is no doubt. */
    @Test
    void oneWindowIsNoDoubt() {
        List<Candidate> ranked = NewWindows.ranked(Set.of(), List.of(window(1, "Balatro", 1280, 8, "wine")),
                pid -> false, pid -> false);
        assertTrue(NewWindows.doubt(ranked, null).isEmpty());
    }
}
