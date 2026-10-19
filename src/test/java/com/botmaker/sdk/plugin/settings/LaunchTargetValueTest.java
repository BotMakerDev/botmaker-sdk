package com.botmaker.sdk.plugin.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The author's list of games: held once each, a pick first, a removal every copy (2026-10-19). */
class LaunchTargetValueTest {

    @Test
    void aPickGoesFirstAndIsHeldOnceHoweverItWasSpelled() {
        List<String> was = List.of("lutris:3", " steam: 570 ", "steam:570", "lutris:3");
        assertEquals(List.of("steam:570", "lutris:3"), LaunchTargetValue.next(was, "steam:570", true));
        assertEquals(List.of("lutris:3", "steam:570"), LaunchTargetValue.next(was, "lutris:3", true),
                "picking the preferred one again only collapses the copies");
    }

    @Test
    void aRemovalTakesEveryCopy() {
        List<String> was = List.of("steam:570", "lutris:3", "steam: 570");
        assertEquals(List.of("lutris:3"), LaunchTargetValue.next(was, "steam:570", false));
        assertEquals(List.of("steam:570", "lutris:3"), LaunchTargetValue.next(was, "epic:x", false),
                "removing a game the bot lacks still collapses");
    }

    @Test
    void theRunnersPickGoesWhenItWouldHideTheAuthorsOrNamesTheOneRemoved() {
        assertTrue(LaunchTargetValue.staleChoice("lutris:3", "steam:570", true), "another pick hides the new one");
        assertFalse(LaunchTargetValue.staleChoice("steam: 570", "steam:570", true));
        assertFalse(LaunchTargetValue.staleChoice("", "steam:570", true), "no pick: the first available");
        assertTrue(LaunchTargetValue.staleChoice("steam:570", "steam:570", false));
        assertFalse(LaunchTargetValue.staleChoice("lutris:3", "steam:570", false));
    }
}
