package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The bot's map read out of its {@code Screens} class: the one read the assistant's tools, 🗺 Screens, the
 * {@code Navigate} screen picker, the {@code Screens} constant view and Manage Pictures' screen groups all make. Here,
 * not in {@code assist}, so the picker and the pictures window read it without naming the assistant's code.
 */
public final class ScreenMapRead {

    private static final ManagedSet<GameScreen> SCREENS = ManagedSet.of(SdkValues.SCREEN_MAP);

    private ScreenMapRead() {}

    /**
     * The map, in the order the bot declares its screens; a constant the grammar cannot read is left out. Throws
     * when the class will not read at all: 🗺 Screens keeps its last drawing then. On the FX thread.
     */
    public static ScreenGraph of(PluginValues values) {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        if (values != null) {
            for (Member member : SCREENS.members(values)) {
                SCREENS.read(values, member).ifPresent(screen -> screens.put(member.name(), screen));
            }
        }
        return ScreenGraph.of(screens);
    }

    /** {@link #of}, or no screens when the class will not read: what a view that must draw anyway shows. */
    public static ScreenGraph orEmpty(PluginValues values) {
        try {
            return of(values);
        } catch (RuntimeException unreadable) {
            return ScreenGraph.of(Map.of());
        }
    }
}
