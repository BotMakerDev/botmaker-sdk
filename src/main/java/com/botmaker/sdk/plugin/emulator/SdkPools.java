package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.PoolHolder;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.PluginPool;
import com.botmaker.plugin.api.run.PoolOffer;
import com.botmaker.sdk.api.capture.Instance;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.RamBudget;

import java.util.List;
import java.util.TreeSet;

/**
 * The SDK's pool (spec {@code 2026-11-15-run-claims-design.md} §2, §5): several runs at once are each dealt one of
 * this PC's emulator instances into the bot's {@code instance()}, so no two drive one instance. The candidates are the
 * instances the Emulators window knows (its host values); the offer, when too few are free, opens that window to
 * create more, as many as this PC's memory holds.
 */
public final class SdkPools implements PoolHolder {

    /** The bot's {@code instance()}, dealt from this PC's instances. */
    public static final PluginPool<Instance> INSTANCES = PluginPool.fills(SdkValues.INSTANCE)
            .from(SdkPools::instances)
            .offering((shortfall, services) -> offers(shortfall.missing(), RamBudget.live().room(0)));

    @Override
    public List<PluginPool<?>> pools() {
        return List.of(INSTANCES);
    }

    /** This PC's instances, from the SDK's host values: the pool's candidates and the instance pill's list. */
    public static List<Instance> instances(StudioServices services) {
        return instances(HostConfig.load(services));
    }

    /**
     * Every instance {@code state} knows, its own settings or a bot seen launching it, by name in alphabetical order;
     * an instance named alike under two emulators is one, as a bot names it (by its name alone,
     * {@code CaptureSource.emulator}), and names that differ only in case are two, as an emulator tells them.
     */
    static List<Instance> instances(HostConfig.State state) {
        TreeSet<String> names = new TreeSet<>();
        state.config().instances().keySet().stream().map(InstanceKey::reference).forEach(names::add);
        state.usedBy().keySet().stream().map(InstanceKey::reference).forEach(names::add);
        return names.stream().map(Instance::named).toList();
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

    /** The offer's click: the window links JavaFX, so a headless host that only lists the offer never loads it. */
    private static void create(StudioServices services) {
        EmulatorsWindow.open(services);
    }
}
