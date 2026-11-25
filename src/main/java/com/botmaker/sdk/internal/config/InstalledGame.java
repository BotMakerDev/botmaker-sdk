// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.GameVm;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.bot.SettingParams;
import com.botmaker.sdk.internal.capture.AutoCapture;
import com.botmaker.sdk.internal.capture.AutoCapture.Decision.WatchLaunch;
import com.botmaker.sdk.internal.capture.AutoPicks;
import com.botmaker.sdk.internal.emulator.EmulatorRef;
import com.botmaker.sdk.internal.emulator.Emulators;
import com.botmaker.sdk.internal.trace.Say;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The game half of the bot's {@code Sdk} values, read off the classes {@code Bot.run} is handed and the parameter
 * group beside them ({@link ParamGroups}): the launchers the bot supports and the game VM it runs in (the author's,
 * in {@code Sdk.java}), the one launcher its runner picked and the capture source it reads (the runner's, in
 * {@code params/}) — and the launch target a run starts ({@link #launchTarget}). A {@code -D} given to a bot started
 * by hand wins over the launchers.
 *
 * <p>Read by mark, not by name: the method carrying {@code @SdkValue(SdkValue.Id.LAUNCH_TARGETS)} is the list,
 * whatever the bot calls it. A value of another type, or one that cannot be read, is no value: nothing is launched
 * and the VM is the one there is, which is what a bot with no such method gets too.
 */
public final class InstalledGame {

    /** The system property naming what a bot started by hand launches, in the launch spec grammar. */
    public static final String LAUNCH_TARGET = "botmaker.launch.target";

    /**
     * The system property naming the emulator instance a run reads and acts on ({@code EmulatorInstance.reference()}),
     * over the bot's capture source: a host that starts a bot for one instance (the Google sign-in) gives it.
     */
    public static final String CAPTURE_EMULATOR = "botmaker.capture.emulator";

    private static volatile List<String> launchTargets = List.of();
    private static volatile String launchChoice = "";
    private static volatile String vm = "";
    private static volatile ZoneId gameZone = ZoneId.systemDefault();
    /** The instance {@code instance()} moved the capture onto, or {@code ""}: where an emulator app is launched. */
    private static volatile String playsOn = "";
    /** The desktop game whose launch's window the run reads, or {@code null}: {@link #pendingWatch}. */
    private static volatile WatchLaunch pendingWatch;
    /** The watch a window picked on an earlier run stands in for, or {@code null}: {@link #rewatch}. */
    private static volatile WatchLaunch rewatch;

    private InstalledGame() {}

    /**
     * Takes the game parameters from the first of {@code values} that marks each, and points the bot at the capture
     * source one names ({@link Source#set}); with none, the source goes back to its default. A capture source of
     * {@code Source.current()} names none: it is what this sets.
     */
    public static void install(Class<?>... values) {
        settings(values);
        playsOn = "";
        pendingWatch = null;
        rewatch = null;
        LaunchSpec target = LaunchSpec.parse(launchTarget());
        CaptureSource capture = marked(SdkValue.Id.CAPTURE, values) instanceof CaptureSource source ? source : null;
        Optional<CaptureSource> saved = AutoCapture.wantsSavedWindow(capture, target)
                ? AutoPicks.run().saved(target) : Optional.empty();
        AutoCapture.Inputs inputs = new AutoCapture.Inputs(System.getProperty(CAPTURE_EMULATOR), target, capture,
                marked(SdkValue.Id.INSTANCE, values), saved, InstalledGame::runningInstances);
        switch (AutoCapture.decide(inputs)) {
            case AutoCapture.Decision.Fixed fixed -> {
                // The game is launched and restarted where the run plays: the dealt instance, not the author's.
                playsOn = fixed.playsOn();
                Source.set(fixed.source());
                if (fixed.why() != null) Say.info(fixed.why());
                // Read from the pick of an earlier run: what is watched again when that window no longer comes up.
                if (saved.isPresent() && AutoCapture.decide(new AutoCapture.Inputs(inputs.emulatorProperty(), target,
                        capture, inputs.dealt(), Optional.empty(), inputs.runningInstances())) instanceof WatchLaunch w) {
                    rewatch = w;
                }
            }
            case WatchLaunch watch -> {
                // The whole desktop until the launch's window is up (Bot's start-up step watches for it).
                pendingWatch = watch;
                Source.set(null);
            }
            case AutoCapture.Decision.Fail fail -> throw new IllegalStateException(fail.message());
        }
    }

    /**
     * The launch whose window the run reads once it opens, when the capture is automatic and no window was picked for
     * this desktop game yet; empty otherwise.
     */
    public static Optional<WatchLaunch> pendingWatch() {
        return Optional.ofNullable(pendingWatch);
    }

    /**
     * The pending watch dropped, for a run that has no start-up step to watch it (▶ Try): what it reads stays the
     * whole desktop, which the caller says; empty when none was pending.
     */
    public static Optional<WatchLaunch> dropPendingWatch() {
        WatchLaunch dropped = pendingWatch;
        pendingWatch = null;
        return Optional.ofNullable(dropped);
    }

    /** {@code watch} pending, as {@link #install} leaves it: for tests. */
    static void pend(WatchLaunch watch) {
        pendingWatch = watch;
    }

    /** The launch's window is picked and read: nothing is pending, and a restart reads that window. */
    public static void watched() {
        pendingWatch = null;
        rewatch = null;
    }

    /**
     * The launch to watch when the window an earlier run picked for this game does not come up — the game opens
     * another now — once that pick is forgotten; empty when the capture was not read from such a pick.
     */
    public static Optional<WatchLaunch> rewatch() {
        return Optional.ofNullable(rewatch);
    }

    /** The names of the emulator instances and phones running now. */
    public static List<String> runningInstances() {
        return Emulators.listAll().stream().filter(EmulatorRef::running).map(EmulatorRef::name).toList();
    }

    /**
     * {@code capture} on the instance {@code instance} names, when the capture is an emulator's (a region of it kept);
     * else {@code capture} as it is. Several runs at once are each dealt an instance into the bot's {@code instance()},
     * so this is what keeps two of them off one emulator. Studio's own views look where the run plays through it too.
     */
    public static CaptureSource onInstance(CaptureSource capture, Object instance) {
        return AutoCapture.onInstance(capture, instance);
    }

    /**
     * Takes the game parameters as {@link #install} does, leaving the capture source as it is: a reloaded bot keeps
     * the game and the connection it has. The Settings group's parameters become the settings in force
     * ({@link BotSettings#use}), before anything clicks.
     */
    public static void settings(Class<?>... values) {
        List<String> targets = specs(marked(SdkValue.Id.LAUNCH_TARGETS, values));
        launchTargets = targets == null ? List.of() : targets;
        launchChoice = marked(SdkValue.Id.LAUNCH_TARGET, values) instanceof LaunchChoice choice ? choice.spec() : "";
        vm = marked(SdkValue.Id.VM, values) instanceof GameVm named ? named.name() : "";
        gameZone = zone(marked(SdkValue.Id.GAME_ZONE, values));
        List<Object> groups = values == null ? List.of() : ParamGroups.groups(values);
        BotSettings.use(SettingParams.from(field -> Optional.ofNullable(ParamGroups.marked(field.id(), groups, values))));
    }

    /** The launch specs the bot lists, preferred first; empty when it lists none. */
    public static List<String> launchTargets() {
        return launchTargets;
    }

    /** The launch spec the person running the bot picked, or {@code ""} for none. */
    public static String launchChoice() {
        return launchChoice;
    }

    /** The game VM's name the bot gives, or {@code ""} for the one VM there is. */
    public static String vm() {
        return vm;
    }

    /**
     * The game server's zone, the bot's {@code ZoneId gameZone()} parameter, or this computer's when the bot marks none
     * (or one that cannot be read, such as the {@code String} it was before 2026-11-09).
     */
    public static ZoneId gameZone() {
        return gameZone;
    }

    /**
     * A {@code gameZone} parameter's value as the zone a daily is read in: {@code value} when it is a {@code ZoneId},
     * else this computer's. The one reading the run and 🎯 Goals share, so both time a daily alike.
     */
    public static ZoneId zone(Object value) {
        return value instanceof ZoneId zone ? zone : ZoneId.systemDefault();
    }

    /**
     * The launch spec a run starts, or {@code null} for none — {@code internal.launch.Target} parses it: a
     * {@code -D}{@value #LAUNCH_TARGET} given by hand, else {@link #pick}, an emulator app's on the instance the run
     * plays on ({@link #launchedOn}).
     */
    public static String launchTarget() {
        String spec = System.getProperty(LAUNCH_TARGET);
        if (spec != null && !spec.isBlank()) return spec.trim();
        return launchedOn(pick(launchTargets, launchChoice, Os.current()), playsOn);
    }

    /**
     * {@code spec} launching its emulator app on {@code instance} when it is an emulator app's and {@code instance}
     * names one; else {@code spec} as it is. Several runs are each dealt an instance, and each launches the game there.
     */
    public static String launchedOn(String spec, String instance) {
        if (spec == null || instance == null || instance.isBlank()) return spec;
        LaunchSpec parsed = LaunchSpec.parse(spec);
        if (parsed == null || parsed.kind() != LaunchKind.EMULATOR_APP || instance.equals(parsed.emulatorInstance())) {
            return spec;
        }
        return parsed.onInstance(instance).spec();
    }

    /**
     * What a computer running {@code os} launches from the bot's {@code specs}, or {@code null}: {@code chosen} when
     * it is one of them and runs there, else the first that runs there. A pick the author no longer lists is none.
     */
    public static String pick(List<String> specs, String chosen, Os os) {
        String wanted = canonical(chosen);
        if (!wanted.isEmpty()) {
            for (String each : specs) {
                LaunchSpec spec = LaunchSpec.parse(each);
                if (spec != null && spec.runsOn(os) && spec.spec().equals(wanted)) return each;
            }
        }
        return firstRunnable(specs, os);
    }

    /** The first of {@code specs} a computer running {@code os} can launch, or {@code null}. */
    public static String firstRunnable(List<String> specs, Os os) {
        for (String each : specs) {
            LaunchSpec spec = LaunchSpec.parse(each);
            if (spec != null && spec.runsOn(os)) return each;
        }
        return null;
    }

    /**
     * {@code spec} as one launcher is written whatever its spacing — {@code steam:570} for {@code " steam: 570"} — so
     * two spellings of it are one launcher; trimmed when it does not parse, {@code ""} for none.
     */
    public static String canonical(String spec) {
        if (spec == null) return "";
        LaunchSpec parsed = LaunchSpec.parse(spec);
        return parsed == null ? spec.trim() : parsed.spec();
    }

    /**
     * The value marked {@code id} in {@code values} or the parameter group beside them ({@link ParamGroups}), or null
     * when none marks it — how {@code Bot.run} reads the run's own parameters ({@code plan}).
     */
    public static Object marked(SdkValue.Id id, Class<?>... values) {
        return ParamGroups.marked(id, values);
    }

    /**
     * A {@code launchTargets} value as specs: its launchers' non-blank specs, or null when it is not a list. The one
     * reading the run and Studio's game dialog share, so both agree on which launcher is first.
     */
    public static List<String> specs(Object value) {
        if (!(value instanceof List<?> list)) return null;
        List<String> out = new ArrayList<>(list.size());
        for (Object each : list) {
            if (each instanceof Launcher launcher && !launcher.spec().isEmpty()) out.add(launcher.spec());
        }
        return List.copyOf(out);
    }
}
