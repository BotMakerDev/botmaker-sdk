// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Way;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.picture;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static com.botmaker.sdk.internal.screens.ScreenGraph.Presence.ALWAYS;
import static com.botmaker.sdk.internal.screens.ScreenGraph.Presence.LOCKED;
import static com.botmaker.sdk.internal.screens.ScreenGraph.Presence.SOMETIMES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The map's graph algorithms on JGraphT: what {@link ScreenGraph} answers through {@link MapGraph}. */
class MapGraphTest {

    private static List<String> tos(Optional<List<ScreenGraph.Edge>> path) {
        return path.orElseThrow().stream().map(ScreenGraph.Edge::to).toList();
    }

    /** Home to Target in two steps through X, or three through Y and Z, costing the same. */
    @Test
    void ofEqualCostTheFewestEdgesWin() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("to_y"), click("to_x")));
        screens.put("Y", screen(Setup.named("Y").from(ref("HOME", "to_y")).takes(Duration.ofMillis(700)),
                click("to_z")));
        screens.put("Z", screen(Setup.named("Z").from(ref("Y", "to_z")).takes(Duration.ofMillis(700)),
                click("z_end")));
        screens.put("X", screen(Setup.named("X").from(ref("HOME", "to_x")).takes(Duration.ofMillis(1700)),
                click("x_end")));
        screens.put("TARGET", screen(Setup.named("Target").from(ref("Z", "z_end"))
                .alsoFrom(List.of(Way.of(ref("X", "x_end"))))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(List.of("X", "TARGET"), tos(graph.pathTo("HOME", "TARGET", e -> true)), "2.0 + 1.3 s either way");
    }

    @Test
    void ofEqualCostAndLengthTheEarliestEdgeWins() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("first"), click("second")));
        screens.put("SHOP", screen(Setup.named("Shop").from(ref("HOME", "second"))
                .alsoFrom(List.of(Way.of(ref("HOME", "first"))))));
        ScreenGraph graph = ScreenGraph.of(screens);

        String first = graph.edges().stream().filter(e -> e.to().equals("SHOP")).findFirst().orElseThrow().input();
        assertEquals(first, graph.pathTo("HOME", "SHOP", e -> true).orElseThrow().getFirst().input());
    }

    /** Hero's tabs Gear and Skills: each tab presses its sibling's button, Hero's own, from where the game is. */
    @Test
    void aTabTakesItsSiblingsButton() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("hero")));
        screens.put("HERO", screen(Setup.named("Hero").from(ref("HOME", "hero")), click("gear_tab"),
                click("skills_tab")));
        screens.put("GEAR", screen(Setup.named("Gear").from(ref("HERO", "gear_tab")).tab()));
        screens.put("SKILLS", screen(Setup.named("Skills").from(ref("HERO", "skills_tab")).tab()));
        ScreenGraph graph = ScreenGraph.of(screens);

        List<ScreenGraph.Edge> path = graph.pathTo("GEAR", "SKILLS", e -> true).orElseThrow();
        assertEquals(1, path.size());
        assertEquals("skills_tab", path.getFirst().input());
        assertEquals(List.of("HOME"), tos(graph.pathTo("GEAR", "HOME", e -> true)), "Hero's way back, from its tab");
    }

    @Test
    void eachCallTakesItsOwnEdges() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("first"), click("second")));
        screens.put("SHOP", screen(Setup.named("Shop").from(ref("HOME", "first"))
                .alsoFrom(List.of(Way.of(ref("HOME", "second"))))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals("first", graph.pathTo("HOME", "SHOP", e -> true).orElseThrow().getFirst().input());
        assertEquals("second", graph.pathTo("HOME", "SHOP", e -> !"first".equals(e.input())).orElseThrow()
                .getFirst().input());
        assertEquals("first", graph.pathTo("HOME", "SHOP", e -> true).orElseThrow().getFirst().input(), "no memory");
    }

    @Test
    void aScreenOfNoMapHasNoPath() {
        ScreenGraph graph = ScreenGraph.of(Map.of("HOME", screen("Home")));
        assertEquals(Optional.empty(), graph.pathTo("HOME", "NOWHERE", e -> true));
        assertEquals(Optional.empty(), graph.pathTo("NOWHERE", "HOME", e -> true));
        assertEquals(Optional.of(List.of()), graph.pathTo("HOME", "HOME", e -> true));
        assertEquals(Map.of(), graph.costsFrom("NOWHERE"));
    }

    /** What reaching each screen costs is what its quickest path costs, edge by edge. */
    @Test
    void costsFromAreThePathsCosts() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("ROOT", screen("Root", click("to_slow"), click("to_fast")));
        screens.put("SLOW", screen(Setup.named("Slow").from(ref("ROOT", "to_slow")).takes(Duration.ofSeconds(5)),
                click("slow_on")));
        screens.put("FAST", screen(Setup.named("Fast").from(ref("ROOT", "to_fast")).takes(Duration.ofMillis(200)),
                click("fast_on")));
        screens.put("TARGET", screen(Setup.named("Target").from(ref("SLOW", "slow_on"))
                .alsoFrom(List.of(Way.of(ref("FAST", "fast_on"))))));
        ScreenGraph graph = ScreenGraph.of(screens);

        Map<String, Long> costs = graph.costsFrom("FAST");
        for (String screen : screens.keySet()) {
            long sum = graph.pathTo("FAST", screen, e -> true).orElseThrow().stream().mapToLong(graph::cost).sum();
            assertEquals(sum, costs.get(screen), screen);
        }
        assertEquals(0L, costs.get("FAST"));
        assertTrue(costs.get("SLOW") > costs.get("TARGET"), "Slow lies back through Root");
    }

    /**
     * A screen is as present as the best of its ways in, each as bad as its worst step: Camp is reached at times by
     * Home's occasional button, and only behind a lock the other way, so it comes and goes.
     */
    @Test
    void presenceMatchesTheFixedPoint() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("event").occasional(), click("vault"), click("tower")));
        screens.put("EVENT", screen(Setup.named("Event").from(ref("HOME", "event")), click("camp")));
        screens.put("VAULT", screen(Setup.named("Vault").from(ref("HOME", "vault")),
                click("camp").locked(picture("padlock"))));
        screens.put("CAMP", screen(Setup.named("Camp").from(ref("VAULT", "camp"))
                .alsoFrom(List.of(Way.of(ref("EVENT", "camp"))))));
        screens.put("TOWER", screen(Setup.named("Tower").from(ref("HOME", "tower"))));
        screens.put("OFFER", screen(Setup.named("Offer").back(click("close_x")).popup()));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(ALWAYS, graph.presence("HOME"));
        assertEquals(ALWAYS, graph.presence("VAULT"));
        assertEquals(ALWAYS, graph.presence("TOWER"));
        assertEquals(SOMETIMES, graph.presence("EVENT"));
        assertEquals(SOMETIMES, graph.presence("CAMP"), "the occasional way beats the locked one");
        assertEquals(ALWAYS, graph.presence("OFFER"), "a popup is always there");
        assertEquals(LOCKED, graph.presence("NOWHERE"));
    }

    /** A and B open each other, and the only way into the pair is locked: the loop makes neither better. */
    @Test
    void loopOfWaysAddsNothing() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("to_a").locked(picture("padlock"))));
        screens.put("A", screen(Setup.named("A").from(ref("HOME", "to_a"))
                .alsoFrom(List.of(Way.of(ref("B", "to_a")))), click("to_b")));
        screens.put("B", screen(Setup.named("B").from(ref("A", "to_b")), click("to_a")));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(LOCKED, graph.presence("A"));
        assertEquals(LOCKED, graph.presence("B"));
    }

    /** Shop opens from Camp and from Home, Camp from Home and Vault: a cap keeps the nearest, both ways in first. */
    @Test
    void dependsOnTakesTheNearestOfEveryWayIn() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("ROOT", screen("Root", click("vault")));
        screens.put("VAULT", screen(Setup.named("Vault").from(ref("ROOT", "vault")), click("camp")));
        screens.put("CAMP", screen(Setup.named("Camp").from(ref("VAULT", "camp")), click("shop")));
        screens.put("HOME", screen("Home", click("shop")));
        screens.put("SHOP", screen(Setup.named("Shop").from(ref("CAMP", "shop"))
                .alsoFrom(List.of(Way.of(ref("HOME", "shop"))))));
        MapGraph graph = MapGraph.of(ScreenGraph.of(screens));

        assertEquals(List.of("CAMP", "HOME"), List.copyOf(graph.dependsOn("SHOP", 2)));
        assertEquals(List.of("CAMP", "HOME", "VAULT", "ROOT"), List.copyOf(graph.dependsOn("SHOP", 64)));
        assertEquals(Set.of(), graph.dependsOn("NOWHERE", 64));
    }

    @Test
    void lockedBehindLockedInput() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("guild").locked(picture("padlock"))));
        screens.put("GUILD", screen(Setup.named("Guild").from(ref("HOME", "guild")), click("hall")));
        screens.put("HALL", screen(Setup.named("Hall").from(ref("GUILD", "hall"))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(LOCKED, graph.presence("GUILD"));
        assertEquals(LOCKED, graph.presence("HALL"), "an open button behind a locked one is locked");
    }

    @Test
    void sometimesBehindOccasionalInput() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen("Home", click("event").occasional()));
        screens.put("EVENT", screen(Setup.named("Event").from(ref("HOME", "event")), click("shop"),
                click("boss").locked(picture("padlock"))));
        screens.put("SHOP", screen(Setup.named("Shop").from(ref("EVENT", "shop"))));
        screens.put("BOSS", screen(Setup.named("Boss").from(ref("EVENT", "boss"))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(SOMETIMES, graph.presence("SHOP"), "an open button behind an occasional one comes and goes");
        assertEquals(LOCKED, graph.presence("BOSS"), "a lock is worse than coming and going");
    }
}
