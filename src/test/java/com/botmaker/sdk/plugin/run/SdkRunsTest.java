package com.botmaker.sdk.plugin.run;

import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.api.bot.ExploreMap;
import com.botmaker.sdk.plugin.SdkPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SdkRunsTest {

    @Test
    void theSdkDeclaresExploreMap() {
        List<RunEntry> entries = new SdkPlugin().runEntries();

        assertEquals(1, entries.size());
        assertEquals("Explore map", entries.getFirst().title());
        assertEquals(ExploreMap.class.getName(), entries.getFirst().mainClass());
    }
}
