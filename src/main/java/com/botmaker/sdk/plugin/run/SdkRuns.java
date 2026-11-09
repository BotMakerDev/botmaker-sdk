package com.botmaker.sdk.plugin.run;

import com.botmaker.plugin.api.RunEntryHolder;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.api.bot.ExploreMap;
import com.botmaker.sdk.api.bot.KeepExploring;

import java.util.List;

/**
 * The SDK's runs beside the bot's own: <em>Explore map</em>, a walk over the bot's whole screen map
 * ({@link ExploreMap}), and <em>Keep exploring</em>, a long session that stops for Claude ({@link KeepExploring}).
 */
public final class SdkRuns implements RunEntryHolder {

    /** The title Studio shows on the run's button, and the assistant's {@code run_bot entry} names. */
    public static final String EXPLORE_MAP = "Explore map";

    /** The title of the long exploring run, as {@link #EXPLORE_MAP}. */
    public static final String KEEP_EXPLORING = "Keep exploring";

    @Override
    public List<RunEntry> runs() {
        // Started from 🗺 Screens ▸ 🔭 Explore, not the toolbar (2026-11-09, spec screens-rail goal 9).
        return List.of(RunEntry.of(EXPLORE_MAP, ExploreMap::main).offBar(),
                RunEntry.of(KEEP_EXPLORING, KeepExploring::main).offBar());
    }
}
