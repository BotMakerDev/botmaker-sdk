package com.botmaker.sdk.plugin.run;

import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.api.bot.ExploreMap;
import com.botmaker.sdk.api.bot.KeepExploring;
import com.botmaker.sdk.plugin.SdkPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
