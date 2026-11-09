package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.screens.MapWalk.Arrival;
import com.botmaker.sdk.internal.screens.MapWalk.Budgets;
import com.botmaker.sdk.internal.screens.MapWalk.Finding;
import com.botmaker.sdk.internal.screens.MapWalk.Summary;
import com.botmaker.sdk.internal.screens.MapWalk.Took;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapWalkTest {

    /** A, the start, opens B and D; B opens C; each but A has its way back. */
    private static ScreenGraph map() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b"), click("d")));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b")), click("c")));
        screens.put("C", screen(Setup.named("C").from(ref("B", "c"))));
        screens.put("D", screen(Setup.named("D").from(ref("A", "d"))));
        return ScreenGraph.of(screens);
    }

    /** A game that does what {@code arrival} says for each edge, AT_TARGET by default; goes where {@code at} allows. */
    private static class Game implements MapWalk.Moves {
        Function<ScreenGraph.Edge, Took> arrival = e -> new Took(Arrival.AT_TARGET, e.to());
        Predicate<String> at = s -> true;
        Optional<String> where = Optional.empty();
        final List<ScreenGraph.Edge> taken = new ArrayList<>();
        final List<Long> given = new ArrayList<>();
        int restarts;
        long nowMs;

        @Override
        public boolean goTo(String screen) {
            return at.test(screen);
        }

        @Override
        public Took take(ScreenGraph.Edge edge, long withinMs) {
            taken.add(edge);
            given.add(withinMs);
            return arrival.apply(edge);
        }

        @Override
        public Optional<String> where() {
            return where;
        }

        @Override
        public boolean recover() {
            return true;
        }

        @Override
        public void restart() {
            restarts++;
        }
    }

    private static Summary walk(ScreenGraph graph, Game game) {
        return MapWalk.walk(graph, game, Budgets.DEFAULT, () -> game.nowMs);
    }

    private static Took when(ScreenGraph.Edge edge, String from, String to, Arrival arrival, String at) {
        return edge.from().equals(from) && edge.to().equals(to) ? new Took(arrival, at)
                : new Took(Arrival.AT_TARGET, edge.to());
    }

    @Test
    void everyEdgeOfAHealthyMapIsTakenOnce() {
        ScreenGraph graph = map();
        Game game = new Game();

        Summary summary = walk(graph, game);

        assertEquals(List.of(), summary.findings());
        assertEquals(graph.edges().size(), summary.edges());
        assertEquals(summary.edges(), summary.ok());
        assertEquals(0, summary.notTried());
        assertEquals(4, summary.reached());
        assertEquals(graph.edges().size(), game.taken.size());
        assertEquals(game.taken.size(), game.taken.stream().distinct().count(), "each edge once");
    }

    @Test
    void anEdgeArrivingElsewhereIsMissed() {
        Game game = new Game();
        game.arrival = e -> when(e, "A", "B", Arrival.ELSEWHERE, "D");

        Summary summary = walk(map(), game);

        assertEquals(List.of(new Finding(Finding.Kind.EDGE_MISSED, "A", "B", "arrived at D")), summary.findings(),
                "B, walked to by the map once A and D are done, is reached");
        assertEquals(0, summary.notTried(), "B's and C's edges were tried from B");
    }

    @Test
    void aFrozenGameIsSaidAsTheStartsReason() {
        // Sword x Staff, 2026-11-03: Waydroid froze; the walk said only that it could not get to the start.
        Game game = new Game() {
            @Override
            public String lost() {
                return "the game looks frozen";
            }
        };
        game.at = s -> false;

        Summary summary = walk(map(), game);

        assertEquals(List.of(new Finding(Finding.Kind.UNREACHED, "A", null, "the game looks frozen")),
                summary.findings());
    }

    @Test
    void aStartTheGameSkipsBeginsWhereTheGameIs() {
        // Sword x Staff, 2026-11-03: Consent once accepted is never shown again; the walk stayed on it.
        Game game = new Game();
        game.at = s -> !s.equals("A");
        game.where = Optional.of("B");

        Summary summary = walk(map(), game);

        assertEquals("B", game.taken.getFirst().from());
        assertTrue(game.taken.stream().anyMatch(e -> e.from().equals("D")), "D, behind A only, walked to");
        assertEquals(List.of(new Finding(Finding.Kind.UNREACHED, "A", null, MapWalk.NOT_GOT_TO)), summary.findings());
    }

    @Test
    void aScreenOnlyShowingNoPicturesIsPicturesChanged() {
        Game game = new Game();
        game.arrival = e -> when(e, "A", "B", Arrival.UNKNOWN, null);
        game.at = s -> !s.equals("B") && !s.equals("C");    // C is only behind B, whose pictures do not show

        Summary summary = walk(map(), game);

        assertEquals(List.of(new Finding(Finding.Kind.PICTURES_CHANGED, "A", "B",
                "its edge from A showed no screen of the map")), summary.findings());
    }

    @Test
    void anEdgeWhereNothingHappenedOrTooSlowIsMissed() {
        Game game = new Game();
        game.arrival = e -> {
            if (e.from().equals("A") && e.to().equals("D")) game.nowMs += Duration.ofSeconds(31).toMillis();
            return when(e, "A", "B", Arrival.UNCHANGED, "A");
        };

        Summary summary = walk(map(), game);

        assertTrue(summary.findings().contains(new Finding(Finding.Kind.EDGE_MISSED, "A", "B", "nothing happened")));
        assertTrue(summary.findings().contains(new Finding(Finding.Kind.EDGE_MISSED, "A", "D", "no screen after 30 s")));
    }

    @Test
    void aMapWithoutAStartEndsAtOnce() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A"), click("b")));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        Game game = new Game();

        Summary summary = walk(ScreenGraph.of(screens), game);

        assertEquals(List.of(new Finding(Finding.Kind.UNREACHED, null, null, "the map has no start screen")),
                summary.findings());
        assertEquals(List.of(), game.taken);
        assertEquals(1, walk(ScreenGraph.of(Map.of()), game).findings().size(), "an empty map too");
    }

    @Test
    void aStopEndsTheWalkAndReportsNothingUnreached() {
        Game game = new Game();
        game.arrival = e -> {
            Bot.stop();
            return null;
        };

        RuntimeException stop = assertThrows(RuntimeException.class, () -> walk(map(), game));

        assertEquals("com.botmaker.sdk.api.bot.Bot$BotStoppedException", stop.getClass().getName());
        assertEquals(1, game.taken.size());
    }

    @Test
    void pastTheDeadlineScreensNotTriedAreNotUnreached() {
        Game game = new Game();
        game.arrival = e -> {
            game.nowMs += Duration.ofMinutes(31).toMillis();
            return new Took(Arrival.AT_TARGET, e.to());
        };

        Summary summary = MapWalk.walk(map(), game,
                new Budgets(Duration.ofHours(1), Duration.ofMinutes(30), 5), () -> game.nowMs);

        assertEquals(1, game.taken.size());
        assertEquals(summary.edges() - 1, summary.notTried());
        assertEquals(List.of(), summary.findings());
    }

    @Test
    void restartsStopAtTheCap() {
        // A opens six screens; only A can be gone back to, so each of the six costs a restart.
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b1"), click("b2"), click("b3"), click("b4"),
                click("b5"), click("b6")));
        for (int i = 1; i <= 6; i++) {
            screens.put("B" + i, screen(Setup.named("B" + i).from(ref("A", "b" + i)), click("c" + i)));
            screens.put("C" + i, screen(Setup.named("C" + i).from(ref("B" + i, "c" + i))));
        }
        Game game = new Game();
        game.at = "A"::equals;

        Summary summary = walk(ScreenGraph.of(screens), game);

        assertEquals(5, game.restarts);
        List<Finding> expected = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            expected.add(new Finding(Finding.Kind.UNREACHED, "B" + i, null, MapWalk.NOT_GOT_TO));
        }
        expected.add(new Finding(Finding.Kind.UNREACHED, "B5", null, "gave up after 5 restarts"));
        assertEquals(expected, summary.findings());
        assertTrue(summary.notTried() > 0);
    }

    @Test
    void aStartScreenTheGameNeverShowsIsSaid() {
        // Sword x Staff, 2026-11-03: the game opened on a screen that was not the start; the walk said nothing.
        Game game = new Game();
        game.at = s -> false;

        Summary summary = walk(map(), game);

        assertEquals(1, game.restarts);
        assertEquals(List.of(new Finding(Finding.Kind.UNREACHED, "A", null, MapWalk.NOT_GOT_TO)), summary.findings());
        assertEquals(summary.edges(), summary.notTried());
    }

    @Test
    void aCrashingTakeIsTimedOutAndTheWalkGoesOn() {
        Game game = new Game();
        Map<String, Integer> calls = new HashMap<>();
        game.arrival = e -> {
            calls.merge(e.from() + e.to(), 1, Integer::sum);
            if (e.from().equals("A") && e.to().equals("B")) throw new IllegalStateException("capture lost");
            return new Took(Arrival.AT_TARGET, e.to());
        };

        Summary summary = walk(map(), game);

        assertTrue(summary.findings().contains(new Finding(Finding.Kind.EDGE_MISSED, "A", "B",
                "failed: java.lang.IllegalStateException: capture lost")));
        assertEquals(1, calls.get("AD"), "the walk went on to A's next edge");
    }

    @Test
    void aWrappedStopStillEndsTheWalk() {
        Game game = new Game();
        game.arrival = e -> {
            try {
                Bot.stop();
            } catch (RuntimeException stop) {
                throw new IllegalStateException("on the capture thread", stop);
            }
            return null;
        };

        RuntimeException stop = assertThrows(RuntimeException.class, () -> walk(map(), game));

        assertEquals("com.botmaker.sdk.api.bot.Bot$BotStoppedException", stop.getClass().getName());
    }

    @Test
    void anEdgeWhoseButtonLooksOtherwiseIsNotTaken() {
        Game game = new Game() {
            @Override
            public boolean open(ScreenGraph.Edge edge) {
                return !edge.to().equals("B");
            }
        };

        Summary summary = walk(map(), game);

        assertTrue(game.taken.stream().noneMatch(e -> e.to().equals("B")));
        assertEquals(List.of(), summary.findings(), "B is behind an edge never tried");
    }

    @Test
    void aWayClosedNowIsNoFinding() {
        Game game = new Game();
        game.arrival = e -> when(e, "A", "B", Arrival.NOT_NOW, "A");

        Summary summary = walk(map(), game);

        assertEquals(List.of(), summary.findings(), "B, behind a way closed now, is no finding either");
        assertTrue(summary.notTried() > 0);
    }

    @Test
    void theFindingsBeforeAStopAreHandedOnAsFound() {
        Game game = new Game();
        game.arrival = e -> {
            if (e.to().equals("D")) Bot.stop();
            return when(e, "A", "B", Arrival.ELSEWHERE, "D");
        };
        List<Finding> found = new ArrayList<>();

        assertThrows(RuntimeException.class,
                () -> MapWalk.walk(map(), game, Budgets.DEFAULT, () -> game.nowMs, found::add));

        assertEquals(List.of(new Finding(Finding.Kind.EDGE_MISSED, "A", "B", "arrived at D")), found);
    }

    @Test
    void aFindingSaysItsScreensAndCarriesThem() {
        ScreenGraph graph = map();
        Finding missed = new Finding(Finding.Kind.EDGE_MISSED, "A", "B", "arrived at D");
        Finding unreached = new Finding(Finding.Kind.UNREACHED, null, "C", "no edge leads to it");

        assertEquals("Edge missed: A → B, arrived at D", missed.said(graph));
        assertEquals(Map.of("from", "A", "to", "B", "screen", "A"), missed.values(graph));
        assertEquals("Never reached: C, no edge leads to it", unreached.said(graph));
        assertEquals(Map.of("to", "C", "screen", "C"), unreached.values(graph));
        int edges = graph.edges().size();
        assertEquals("Map check: 4/4 screens, " + edges + "/" + edges + " edges, 0 not tried",
                walk(graph, new Game()).said());
    }

    @Test
    void anEdgeWhoseMethodSaysNoIsNotTaken() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b")));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b")).fromWhen(() -> false)));
        Game game = new Game();

        Summary summary = walk(ScreenGraph.of(screens), game);

        assertEquals(List.of(), game.taken);
        assertEquals(List.of(), summary.findings(), "B is behind an edge never tried");
        assertEquals(summary.edges(), summary.notTried());
    }
}
