package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.GameVm;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Instance;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.bot.SettingParams;
import com.botmaker.sdk.internal.capture.CurrentSource;
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

    private InstalledGame() {}

    /**
     * Takes the game parameters from the first of {@code values} that marks each, and points the bot at the capture
     * source one names ({@link Source#set}); with none, the source goes back to its default. A capture source of
     * {@code Source.current()} names none: it is what this sets.
     */
    public static void install(Class<?>... values) {
        settings(values);
        String instance = System.getProperty(CAPTURE_EMULATOR);
        if (instance != null && !instance.isBlank()) {
            playsOn = "";
            Source.set(CaptureSource.emulator(instance.strip()));
            return;
        }
        CaptureSource capture = marked(SdkValue.Id.CAPTURE, values) instanceof CaptureSource source
                && !(source instanceof CurrentSource) ? source : null;
        Object dealt = marked(SdkValue.Id.INSTANCE, values);
        CaptureSource moved = onInstance(capture, dealt);
        // The game is launched and restarted where the run plays: the dealt instance, not the author's.
        playsOn = moved != capture && dealt instanceof Instance named ? named.name() : "";
        Source.set(moved);
    }

    /**
     * {@code capture} on the instance {@code instance} names, when the capture is an emulator's (a region of it kept);
     * else {@code capture} as it is. Several runs at once are each dealt an instance into the bot's {@code instance()},
     * so this is what keeps two of them off one emulator. Studio's own views look where the run plays through it too.
     */
    public static CaptureSource onInstance(CaptureSource capture, Object instance) {
        if (!(instance instanceof Instance named) || !named.isNamed() || !SdkValues.onEmulator(capture)) {
            return capture;
        }
        CaptureSource moved = CaptureSource.emulator(named.name());
        Rect region = capture.subRegion();
        return region == null ? moved : moved.region(region);
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
        return LaunchKind.EMULATOR_APP.id() + ":" + parsed.emulatorPackage() + "@" + instance;
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
