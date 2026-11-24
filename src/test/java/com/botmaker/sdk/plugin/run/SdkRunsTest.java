// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.run;

import com.botmaker.plugin.api.assist.ToolSchema;
import com.botmaker.plugin.api.run.RunArguments;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.api.bot.Discover;
import com.botmaker.sdk.api.bot.ExploreMap;
import com.botmaker.sdk.api.bot.KeepExploring;
import com.botmaker.sdk.internal.explore.DiscoverArgs;
import com.botmaker.sdk.internal.explore.ExploreBudget;
import com.botmaker.sdk.internal.screens.MapCheckBudget;
import com.botmaker.sdk.plugin.SdkPlugin;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SdkRunsTest {

    @Test
    void theSdkDeclaresExploreMapKeepExploringAndDiscover() {
        List<RunEntry> entries = new SdkPlugin().runEntries();

        assertEquals(List.of("Explore map", "Keep exploring", "Discover"), entries.stream().map(RunEntry::title).toList());
        assertEquals(List.of(ExploreMap.class.getName(), KeepExploring.class.getName(), Discover.class.getName()),
                entries.stream().map(RunEntry::mainClass).toList());
        assertEquals(List.of(false, false, true), entries.stream().map(RunEntry::onBar).toList(),
                "the first two start from 🗺 Screens ▸ 🔭 Explore, Discover from the toolbar: a blank bot has no map");
    }

    @Test
    void eachTakesItsBudgetAndNeedsNone() {
        List<RunEntry> entries = new SdkPlugin().runEntries();

        assertEquals(List.of(List.of("minutes"), List.of("minutes", "maxScreens", "maxPresses", "maxCalls", "goal"),
                List.of("url", "minutes", "maxPages", "show", "goal", "goalScreen", "maxPresses")), entries.stream()
                .map(e -> e.schema().fields().stream().map(ToolSchema.Field::name).toList()).toList());
        assertEquals(List.of(false, false, false), entries.stream().map(RunEntry::needsArguments).toList(),
                "the window starts them with none");

        String before = System.getProperty(RunArguments.PROPERTY);
        try {
            System.setProperty(RunArguments.PROPERTY, entries.get(1).arguments(Map.of("maxScreens", 4)));
            assertEquals(new ExploreBudget(null, 4), RunArguments.read(ExploreBudget.class));
            System.setProperty(RunArguments.PROPERTY, entries.get(1).arguments(Map.of("goal", " sign in ")));
            ExploreBudget toward = RunArguments.read(ExploreBudget.class);
            assertEquals("sign in", toward.goal());
            assertEquals("sign in", toward.toward(null).goal(), "a goal is carried as a Discover's, for the crew");
            assertNull(ExploreBudget.NONE.toward(null), "no goal, no Discover");
            DiscoverArgs site = new DiscoverArgs("https://site.example", 9, null, true, "old", "Old", null);
            assertEquals(site, ExploreBudget.NONE.toward(site), "no goal: the kept arguments as they are");
            assertEquals(new DiscoverArgs("https://site.example", 9, null, true, "sign in", null, null),
                    toward.toward(site), "a goal turns the kept ones toward it: the website kept, the old screen not");
            System.setProperty(RunArguments.PROPERTY, entries.get(0).arguments(Map.of("minutes", 5)));
            assertEquals(Duration.ofMinutes(5), RunArguments.read(MapCheckBudget.class).budgets().whole());
            System.clearProperty(RunArguments.PROPERTY);
            assertEquals(ExploreBudget.NONE, RunArguments.read(ExploreBudget.class));
        } finally {
            if (before == null) System.clearProperty(RunArguments.PROPERTY);
            else System.setProperty(RunArguments.PROPERTY, before);
        }
        assertThrows(IllegalArgumentException.class, () -> entries.get(0).arguments(Map.of("maxScreens", 4)),
                "Explore map walks its map: no count of screens");
    }
}
