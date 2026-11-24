// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.internal.screens.MapWalk.Arrival;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The frontier of a goal-directed run: best first, never pruned, parked past its cap or when it needs the user. */
class FrontierTest {

    private static Frontier depths(Map<String, Integer> depths) {
        return new Frontier(screen -> depths.getOrDefault(screen, 0));
    }

    private static String next(Frontier frontier, int cap) {
        return frontier.next(cap).map(Frontier.Entry::key).orElse("");
    }

    @Test
    void theBestRankComesFirstAndEqualsInTheOrderRanked() {
        Frontier frontier = depths(Map.of());
        frontier.add("A", "low", 1, PressTag.SAFE);
        frontier.add("A", "first", 5, PressTag.SAFE);
        frontier.add("B", "second", 5, PressTag.SAFE);

        assertEquals("A.first", next(frontier, 6));
        frontier.pressed("A", "first");
        assertEquals("B.second", next(frontier, 6));
    }

    @Test
    void anOffGoalPressStaysAndComesOnceTheBetterOnesAreGone() {
        Frontier frontier = depths(Map.of());
        frontier.add("A", "off", 0, null);
        frontier.add("A", "on", 9, null);

        frontier.pressed("A", "on");

        assertEquals("A.off", next(frontier, 6));
    }

    @Test
    void aRankAgainReplacesTheOlderOne() {
        Frontier frontier = depths(Map.of());
        frontier.add("A", "x", 1, PressTag.SAFE);
        frontier.add("A", "y", 5, PressTag.SAFE);
        frontier.add("A", "x", 9, PressTag.SAFE);

        assertEquals("A.x", next(frontier, 6));
    }

    @Test
    void pastTheDepthCapIsParkedAndResumeTakesItBack() {
        Frontier frontier = depths(Map.of("Deep", 7));
        frontier.add("Deep", "x", 9, PressTag.SAFE);
        frontier.add("A", "y", 1, PressTag.SAFE);

        assertEquals("A.y", next(frontier, 6));
        assertEquals(List.of("Deep.x"), frontier.parked().stream().map(p -> p.entry().key()).toList());
        assertEquals(Frontier.Hold.DEPTH, frontier.parked().getFirst().hold());

        assertEquals(1, frontier.resume().size());
        assertEquals("Deep.x", next(frontier, 7));
    }

    @Test
    void riskyAndDestructivePressesWaitOnTheUserAndAreNeverReturned() {
        Frontier frontier = depths(Map.of());
        frontier.add("A", "risky", 9, PressTag.RISKY);
        frontier.add("A", "gone", 8, PressTag.DESTRUCTIVE);
        frontier.add("A", "odd", 7, PressTag.UNKNOWN);

        assertEquals(Optional.empty(), frontier.next(6));
        assertEquals(3, frontier.needsYou().size());
        assertTrue(frontier.resume().isEmpty(), "resume never takes back what needs the user");
        assertTrue(frontier.held("A", "risky"));
        assertFalse(frontier.held("A", "other"));
        assertTrue(frontier.isEmpty());
    }

    @Test
    void theDenylistTagsAnUntaggedOrSafePressDestructive() {
        assertEquals(PressTag.DESTRUCTIVE, Frontier.tagOf("Sign out", null));
        assertEquals(PressTag.DESTRUCTIVE, Frontier.tagOf("delete_account", PressTag.SAFE));
        assertEquals(PressTag.DESTRUCTIVE, Frontier.tagOf("Buy now", PressTag.SAFE));
        assertEquals(PressTag.DESTRUCTIVE, Frontier.tagOf("deleteAccount", null));
        assertEquals(PressTag.DESTRUCTIVE, Frontier.tagOf("buyGems", null));
        assertEquals(PressTag.DESTRUCTIVE, Frontier.tagOf("signOutButton", null));
        assertEquals(PressTag.SAFE, Frontier.tagOf("Buyers guide", null));
        assertEquals(PressTag.SAFE, Frontier.tagOf("Docs", null));
        assertFalse(Frontier.denied(null));
    }

    @Test
    void theJournalRebuildsItRanksMinusTries() {
        List<ExploreEvent> events = List.of(
                new ExploreEvent.Ranked("A", "b", 3, PressTag.SAFE, 1),
                new ExploreEvent.Ranked("A", "d", 2, PressTag.SAFE, 2),
                new ExploreEvent.Tried("A", "b", ExploreLoop.Mode.BEST, Arrival.AT_TARGET, 3));

        Frontier frontier = Frontier.of(events, screen -> 0);

        assertEquals("A.d", next(frontier, 6));
        assertEquals(List.of("A.d"), frontier.saved(9).stream().map(r -> r.screen() + "." + r.press()).toList());
    }
}
