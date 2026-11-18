// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.run;

import com.botmaker.plugin.api.assist.ToolSchema;
import com.botmaker.plugin.api.run.RunArguments;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.api.bot.ExploreMap;
import com.botmaker.sdk.api.bot.KeepExploring;
import com.botmaker.sdk.internal.explore.ExploreBudget;
import com.botmaker.sdk.internal.screens.MapCheckBudget;
import com.botmaker.sdk.plugin.SdkPlugin;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SdkRunsTest {

    @Test
    void theSdkDeclaresExploreMapAndKeepExploring() {
        List<RunEntry> entries = new SdkPlugin().runEntries();

        assertEquals(List.of("Explore map", "Keep exploring"), entries.stream().map(RunEntry::title).toList());
        assertEquals(List.of(ExploreMap.class.getName(), KeepExploring.class.getName()),
                entries.stream().map(RunEntry::mainClass).toList());
        assertEquals(List.of(false, false), entries.stream().map(RunEntry::onBar).toList(),
                "both start from 🗺 Screens ▸ 🔭 Explore");
    }

    @Test
    void eachTakesItsBudgetAndNeedsNone() {
        List<RunEntry> entries = new SdkPlugin().runEntries();

        assertEquals(List.of(List.of("minutes"), List.of("minutes", "maxScreens")), entries.stream()
                .map(e -> e.schema().fields().stream().map(ToolSchema.Field::name).toList()).toList());
        assertEquals(List.of(false, false), entries.stream().map(RunEntry::needsArguments).toList(),
                "the window starts them with none");

        String before = System.getProperty(RunArguments.PROPERTY);
        try {
            System.setProperty(RunArguments.PROPERTY, entries.get(1).arguments(Map.of("maxScreens", 4)));
            assertEquals(new ExploreBudget(null, 4), RunArguments.read(ExploreBudget.class));
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
