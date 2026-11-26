// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.PoolHolder;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.PluginPool;
import com.botmaker.plugin.api.run.PoolOffer;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.InstanceMemory;
import com.botmaker.shared.emulator.config.LightProfile;
import com.botmaker.shared.host.MemoryBudget;

import java.util.List;
import java.util.TreeSet;

/**
 * The SDK's pool (spec {@code 2026-11-15-run-claims-design.md} §2, §5; {@code 2026-11-26-run-places-design.md} §1):
 * several runs at once are each dealt one of this PC's places into the bot's {@code place()}, so no two drive one
 * instance. The candidates are, until phase 3 of the run places plan widens them, the Android places of the instances
 * the Emulators window knows (its host values); the offer, when too few are free, opens that window to create more, as
 * many as this PC's memory holds.
 */
public final class SdkPools implements PoolHolder {

    /** The bot's {@code place()}, dealt from this PC's places. */
    public static final PluginPool<Place> PLACES = PluginPool.fills(SdkValues.PLACE)
            .from(SdkPools::places)
            .offering((shortfall, services) -> offers(shortfall.missing(), room()));

    @Override
    public List<PluginPool<?>> pools() {
        return List.of(PLACES);
    }

    /** This PC's places, from the SDK's host values: the pool's candidates and the place pill's list. */
    public static List<Place> places(StudioServices services) {
        return places(HostConfig.load(services));
    }

    /**
     * The Android place of every instance {@code state} knows, its own settings or a bot seen launching it, by name in
     * alphabetical order; an instance named alike under two emulators is one, as a bot names it (by its name alone,
     * {@code CaptureSource.emulator}), and names that differ only in case are two, as an emulator tells them.
     */
    static List<Place> places(HostConfig.State state) {
        TreeSet<String> names = new TreeSet<>();
        state.config().instances().keySet().stream().map(InstanceKey::reference).forEach(names::add);
        state.usedBy().keySet().stream().map(InstanceKey::reference).forEach(names::add);
        return names.stream().map(Place::android).toList();
    }

    /**
     * The offer for {@code missing} more instances when {@code room} more fit in this PC's memory: create as many as
     * both allow, in the Emulators window, where the user picks the emulator and starts it. None when none fits: the
     * host then says how many are missing.
     */
    static List<PoolOffer> offers(int missing, int room) {
        int creatable = Math.min(missing, room);
        if (creatable <= 0) return List.of();
        String title = "Create " + creatable + (creatable == 1 ? " instance" : " instances")
                + (creatable < missing ? " (this PC's memory holds no more)" : "");
        return List.of(PoolOffer.of(title, SdkPools::create));
    }

    /** How many more instances the offer makes, light ones, this PC can commit now. */
    private static int room() {
        MemoryBudget budget = MemoryBudget.live();
        return budget.room(InstanceMemory.need("A light instance", LightProfile.MEMORY_MB, budget));
    }

    /** The offer's click: the window links JavaFX, so a headless host that only lists the offer never loads it. */
    private static void create(StudioServices services) {
        EmulatorsWindow.open(services);
    }
}
