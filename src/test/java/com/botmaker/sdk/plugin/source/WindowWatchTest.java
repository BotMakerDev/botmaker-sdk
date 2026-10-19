package com.botmaker.sdk.plugin.source;

import com.botmaker.shared.capture.GenericWindow;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ▶ Launch and pick's guess: the windows a launch opened, the game's first and recommended. */
class WindowWatchTest {

    private static final long STEAM = 100;
    private static final long GAME = 200;

    private static GenericWindow window(long handle, String title, int width, long pid, String windowClass) {
        return new GenericWindow(handle, title, new Rectangle(0, 0, width, width), pid, windowClass);
    }

    @Test
    void theBiggestNewWindowNoLauncherOwnsIsRecommended() {
        GenericWindow editor = window(1, "notes.txt - Editor", 900, 50, "gedit");
        GenericWindow login = window(2, "Steam", 1200, STEAM, "steam");
        GenericWindow splash = window(3, "Loading", 300, GAME, "splash.exe");
        GenericWindow game = window(4, "Hades II", 1280, GAME, "steam_app_1145350");

        List<WindowWatch.Candidate> ranked = WindowWatch.ranked(WindowWatch.handles(List.of(editor)),
                List.of(editor, login, splash, game), pid -> pid == STEAM);

        assertEquals(List.of("steam_app_1145350", "splash.exe", "steam"),
                ranked.stream().map(c -> c.identity().spec()).toList());
        assertTrue(ranked.get(0).recommended());
        assertFalse(ranked.get(1).recommended());
        assertTrue(ranked.get(2).launcher());
    }

    /** A splash and the game under one class are one pick: the class is what is saved. */
    @Test
    void windowsOfOneClassAreOneRow() {
        List<WindowWatch.Candidate> ranked = WindowWatch.ranked(Set.of(), List.of(
                window(1, "Loading", 300, GAME, "game"), window(2, "Game", 1280, GAME, "game")), pid -> false);
        assertEquals(1, ranked.size());
        assertEquals("Game", ranked.get(0).window().getTitle());
    }

    /** A generic class is saved with its title; one with neither a class nor a title names nothing to save. */
    @Test
    void aWindowIsSavedByItsClassAndByItsTitleOnlyWhenTheClassIsGeneric() {
        List<WindowWatch.Candidate> ranked = WindowWatch.ranked(Set.of(), List.of(
                window(1, "Firestone", 1280, GAME, "steam_app_0"), window(2, "Untitled", 800, GAME, null)),
                pid -> false);
        assertEquals(List.of("steam_app_0|Firestone"), ranked.stream().map(c -> c.identity().spec()).toList());
    }

    /** Only the launcher opened a window yet: it is listed, and nothing is recommended. */
    @Test
    void aLauncherWindowIsNeverRecommended() {
        List<WindowWatch.Candidate> ranked = WindowWatch.ranked(Set.of(),
                List.of(window(2, "Steam", 1200, STEAM, "steam")), pid -> pid == STEAM);
        assertEquals(1, ranked.size());
        assertFalse(ranked.get(0).recommended());
    }
}
