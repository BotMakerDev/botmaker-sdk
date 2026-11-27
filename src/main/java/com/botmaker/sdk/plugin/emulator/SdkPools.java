// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.PoolHolder;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.PluginPool;
import com.botmaker.plugin.api.run.PoolOffer;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.config.PointerClaims;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.sdk.plugin.params.LaunchTargets;
import com.botmaker.sdk.plugin.settings.SettingsValue;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.InstanceMemory;
import com.botmaker.shared.emulator.config.LightProfile;
import com.botmaker.shared.host.MemoryBudget;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The SDK's pool (spec {@code 2026-11-15-run-claims-design.md} §2, §5; {@code 2026-11-26-run-places-design.md} §1,
 * §2): several runs at once are each dealt one of this PC's places into the bot's {@code place()}, so no two drive one
 * place. The candidates are the places where one of the bot's launch targets can play: the Emulators window's
 * instances for an Android app, and this desktop, private displays and game VMs for a game of this PC, which a run
 * dealt one plays on. The offer, when too few are free for an Android bot, opens that window to create more
 * instances, as many as this PC's memory holds.
 */
public final class SdkPools implements PoolHolder {

    /**
     * The bot's {@code place()}, dealt from this PC's places that can play its game: one run each, but this desktop,
     * which any number of games play on (spec §3); its pointer is a claim of its own, held by the one run that takes
     * it over (spec §3, phase 5).
     */
    public static final PluginPool<Place> PLACES = PluginPool.fills(SdkValues.PLACE)
            .from(SdkPools::places)
            .offering((shortfall, services) -> offers(shortfall.missing(), room(), candidates(services)))
            .sharing(SdkPools::runsAt)
            .claiming(SdkPools::heldWith);

    /**
     * What a run dealt {@code place} holds alone: this desktop's pointer when the bot takes over the mouse and keyboard
     * ({@code RunIn.takeOver}, read from its settings group), so a second such run is no row, and nothing else. A run
     * whose code turns take-over on later claims the pointer then, in {@code BotSettings.use}, and stops when it is
     * held: the deal reads what the bot declares, not what its code may do.
     */
    static List<String> heldWith(Place place, StudioServices services) {
        return PointerClaims.heldWith(place, SettingsValue.current(services).takeOver());
    }

    /** How many runs may play at {@code place} at once: any number on this desktop, one anywhere else. */
    static int runsAt(Place place) {
        return place.kind() == Place.Kind.DESKTOP ? PluginPool.Sharing.ANY : 1;
    }

    /**
     * What a private display and the game on it are taken to commit (2026-11-26, my call): a desktop game's process
     * and the display's compositor. The count of displays offered is as many as this PC's memory holds of it.
     */
    static final MemoryBudget.Need DISPLAY = new MemoryBudget.Need("A private display", 2048, 1536);

    /** At most this many private displays are candidates, however much memory there is: past it a run waits. */
    static final int MOST_DISPLAYS = 8;

    @Override
    public List<PluginPool<?>> pools() {
        return List.of(PLACES);
    }

    /** This PC's places that can play the bot's game: the pool's candidates and the place pill's list. */
    public static List<Place> places(StudioServices services) {
        return candidates(services).places();
    }

    /**
     * The places that can play the game, and why each other place was left out.
     *
     * @param places  in the order to deal: Android places by name, then this PC's places, {@code RunIn.where}'s kind
     *                first ({@link #candidates(HostConfig.State, List, Pc)})
     * @param leftOut a sentence a person reads for each place that cannot play ("Pixel 9: com.game is not installed")
     * @param android whether the bot plays on Android, so more instances would be more places
     */
    record Candidates(List<Place> places, List<String> leftOut, boolean android) {
        Candidates {
            places = List.copyOf(places);
            leftOut = List.copyOf(leftOut);
        }
    }

    /**
     * What this PC is, besides its instances, for {@link #candidates(HostConfig.State, List, Pc)}: read live by
     * {@link #candidates(StudioServices)}.
     *
     * @param os             this PC's system
     * @param displayRefusal why the game cannot play on a private display here, empty when it can
     * @param displayRoom    how many private displays this PC's memory holds at once, those taken counted in: a held
     *                       display stays a candidate while runs fill the memory
     * @param vms            the game VMs that are set up, by name
     * @param vmRefusal      why the game cannot play in a game VM here, empty when it can
     * @param packages       by instance name, the packages Android last said it has; an instance never read is absent
     * @param where          the runner's {@code RunIn.where}: which kind of this PC's places is dealt first
     */
    record Pc(Os os, Optional<String> displayRefusal, int displayRoom, List<String> vms, Optional<String> vmRefusal,
              Map<String, Set<String>> packages, BotSettings.Where where) {
        Pc {
            vms = List.copyOf(vms);
            Map<String, Set<String>> copy = new HashMap<>();
            packages.forEach((name, read) -> copy.put(name, Set.copyOf(read)));
            packages = Map.copyOf(copy);
            Objects.requireNonNull(where, "where");
        }

        /** A PC that prefers private displays, the settings' default. */
        Pc(Os os, Optional<String> displayRefusal, int displayRoom, List<String> vms, Optional<String> vmRefusal,
           Map<String, Set<String>> packages) {
            this(os, displayRefusal, displayRoom, vms, vmRefusal, packages, BotSettings.Where.PRIVATE_DISPLAY);
        }
    }

    private static Candidates candidates(StudioServices services) {
        HostConfig.State state = HostConfig.load(services);
        Os os = Os.current();
        // The game a run launches first, the runner's pick, so the places are asked about the game that will start.
        List<String> targets = new ArrayList<>(LaunchTargets.all(services));
        String current = LaunchTargets.current(services);
        if (current != null && targets.remove(current)) targets.addFirst(current);
        LaunchSpec pcGame = pcGame(specs(targets), os);
        Optional<String> refusal = pcGame == null ? Optional.empty()
                : SessionBootstrap.placeRefusal(pcGame, BotSettings.Where.PRIVATE_DISPLAY);
        int room = pcGame == null || refusal.isPresent() ? 0
                : (int) Math.min(MOST_DISPLAYS, MemoryBudget.live().mostMb() / DISPLAY.commitsMb());
        boolean vmsHere = pcGame != null && SessionBootstrap.hasGameVms(os);
        List<String> vms = vmsHere
                ? VmInventory.list().stream().filter(vm -> vm.stage() == VmRecord.Stage.READY).map(VmRecord::name)
                        .toList()
                : List.of();
        Optional<String> vmRefusal = vmsHere ? SessionBootstrap.placeRefusal(pcGame, BotSettings.Where.VM)
                : Optional.empty();
        // Android's packages are read only while the Emulators window shows an instance, and kept nowhere: every
        // instance counts as never read, a candidate whose launch installs the game or fails as it does today.
        return candidates(state, targets, new Pc(os, refusal, room, vms, vmRefusal, Map.of(),
                SettingsValue.current(services).where()));
    }

    /**
     * The places that can play one of {@code targets} (spec §2), pure:
     *
     * <ul>
     *   <li>the Android place of every instance {@code state} knows, when a target is an {@code emu-app}, its package
     *       installed as last read or never read; by name in alphabetical order, an instance named alike under two
     *       emulators being one, as a bot names it (by its name alone, {@code CaptureSource.emulator}), and names that
     *       differ only in case two, as an emulator tells them;</li>
     *   <li>private displays 1…N for a target that runs on this system and is not an {@code emu-app}, N as many as
     *       {@link Pc#displayRoom} and {@link #MOST_DISPLAYS} allow, when {@link Pc#displayRefusal} is empty;</li>
     *   <li>each VM that is set up, for that target, when {@link Pc#vmRefusal} is empty;</li>
     *   <li>this desktop, for that target: any number of runs share it ({@link #runsAt}).</li>
     * </ul>
     *
     * This PC's places are dealt in {@link Pc#where}'s order (spec §1): its kind first, then private displays, VMs and
     * this desktop as they come. The host deals a shared candidate in the pool's order, so this desktop, listed first
     * when {@code MY_DESKTOP} is preferred, takes every run; listed after the others, it takes those they leave.
     *
     * The PC's places are asked about the first such target, so a caller puts the one a run launches first. A bot that
     * lists no target, or none this system runs, is run against a game already open: its candidates are the Android
     * places, as before places.
     */
    static Candidates candidates(HostConfig.State state, List<String> targets, Pc pc) {
        List<LaunchSpec> specs = specs(targets);
        List<String> packages = specs.stream()
                .filter(spec -> spec.kind() == LaunchKind.EMULATOR_APP && spec.runsOn(pc.os()))
                .map(SdkPools::androidPackage).filter(pkg -> !pkg.isEmpty()).distinct().toList();
        boolean android = !packages.isEmpty() || pcGame(specs, pc.os()) == null;
        List<Place> places = new ArrayList<>();
        List<String> leftOut = new ArrayList<>();
        if (android) {
            for (String name : instances(state)) {
                Set<String> read = pc.packages().get(name);
                if (read == null || packages.isEmpty() || packages.stream().anyMatch(read::contains)) {
                    places.add(Place.android(name));
                } else {
                    leftOut.add(name + ": " + String.join(" or ", packages) + " is not installed");
                }
            }
        }
        if (pcGame(specs, pc.os()) != null) {
            List<Place> displays = new ArrayList<>();
            if (pc.displayRefusal().isEmpty()) {
                for (int n = 1; n <= Math.min(pc.displayRoom(), MOST_DISPLAYS); n++) displays.add(Place.privateDisplay(n));
            } else if (pc.os() == Os.LINUX) {
                leftOut.add("Private displays: " + pc.displayRefusal().get());
            }
            List<Place> vms = new ArrayList<>();
            if (pc.vmRefusal().isEmpty()) {
                pc.vms().stream().map(Place::vm).forEach(vms::add);
            } else if (!pc.vms().isEmpty()) {
                leftOut.add("Game VMs: " + pc.vmRefusal().get());
            }
            places.addAll(inOrder(pc.where(), displays, vms));
        }
        return new Candidates(places, leftOut, android);
    }

    /** This PC's places in the order {@code where} deals them: its kind first, then displays, VMs and this desktop. */
    private static List<Place> inOrder(BotSettings.Where where, List<Place> displays, List<Place> vms) {
        List<Place> desktop = List.of(Place.desktop());
        List<List<Place>> kinds = switch (where) {
            case MY_DESKTOP -> List.of(desktop, displays, vms);
            case VM -> List.of(vms, displays, desktop);
            case PRIVATE_DISPLAY -> List.of(displays, vms, desktop);
        };
        return kinds.stream().flatMap(List::stream).toList();
    }

    /** The instances {@code state} knows, its own settings or a bot seen launching one, by name. */
    private static TreeSet<String> instances(HostConfig.State state) {
        TreeSet<String> names = new TreeSet<>();
        state.config().instances().keySet().stream().map(InstanceKey::reference).forEach(names::add);
        state.usedBy().keySet().stream().map(InstanceKey::reference).forEach(names::add);
        return names;
    }

    /** The first of {@code specs} that plays on this PC itself: one {@code os} runs that is no Android app. */
    private static LaunchSpec pcGame(List<LaunchSpec> specs, Os os) {
        return specs.stream().filter(spec -> spec.kind() != LaunchKind.EMULATOR_APP && spec.runsOn(os))
                .findFirst().orElse(null);
    }

    /** The package an {@code emu-app} target starts, with or without its {@code @<instance>}; {@code ""} for none. */
    private static String androidPackage(LaunchSpec spec) {
        String pkg = spec.emulatorPackage();
        return pkg == null ? "" : pkg;
    }

    private static List<LaunchSpec> specs(List<String> targets) {
        return targets.stream().map(LaunchSpec::parse).filter(Objects::nonNull).toList();
    }

    /**
     * The offer for {@code missing} more places when {@code room} more instances fit in this PC's memory: create as
     * many as both allow, in the Emulators window, where the user picks the emulator and starts it, for a bot that
     * plays on Android. None when none fits, or the game is no Android app: the host then says how many are missing.
     * A game of this PC is short only when it takes over the mouse and this desktop's pointer is held; it is offered
     * no private display (the candidates count as many as memory holds) and, for now, no game VM. The title says which
     * places {@code candidates} left out, so the Runs window says why they are not counted.
     */
    static List<PoolOffer> offers(int missing, int room, Candidates candidates) {
        int creatable = Math.min(missing, room);
        if (creatable <= 0 || !candidates.android()) return List.of();
        String title = "Create " + creatable + (creatable == 1 ? " instance" : " instances")
                + (creatable < missing ? " (this PC's memory holds no more)" : "")
                + (candidates.leftOut().isEmpty() ? "" : "; left out: " + String.join("; ", candidates.leftOut()));
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
    }}
