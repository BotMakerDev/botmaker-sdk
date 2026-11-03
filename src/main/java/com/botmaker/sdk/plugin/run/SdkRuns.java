package com.botmaker.sdk.plugin.run;

import com.botmaker.plugin.api.RunEntryHolder;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.api.bot.ExploreMap;

import java.util.List;

/** The SDK's runs beside the bot's own: <em>Explore map</em>, a walk over the bot's whole screen map ({@link ExploreMap}). */
public final class SdkRuns implements RunEntryHolder {

    /** The title Studio shows on the run's button, and the assistant's {@code run_bot entry} names. */
    public static final String EXPLORE_MAP = "Explore map";

    @Override
    public List<RunEntry> runs() {
        return List.of(RunEntry.of(EXPLORE_MAP, ExploreMap::main));
    }
}
