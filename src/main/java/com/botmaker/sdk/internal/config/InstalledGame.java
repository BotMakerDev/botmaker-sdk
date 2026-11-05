package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;

import java.util.ArrayList;
import java.util.List;

/**
 * The game half of the bot's {@code Sdk} values, read off the classes {@code Bot.runFlow} is handed and the parameter
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

    private static volatile List<String> launchTargets = List.of();
    private static volatile String launchChoice = "";
    private static volatile String vm = "";

    private InstalledGame() {}

    /**
     * Takes the game parameters from the first of {@code values} that marks each, and points the bot at the capture
     * source one names ({@link Source#set}); with none, the source goes back to its default. A capture source of
     * {@code Source.current()} names none: it is what this sets.
     */
    public static void install(Class<?>... values) {
        List<String> targets = specs(marked(SdkValue.Id.LAUNCH_TARGETS, values));
        launchTargets = targets == null ? List.of() : targets;
        launchChoice = marked(SdkValue.Id.LAUNCH_TARGET, values) instanceof LaunchChoice choice ? choice.spec() : "";
        vm = marked(SdkValue.Id.VM, values) instanceof String named ? named.trim() : "";
        Source.set(marked(SdkValue.Id.CAPTURE, values) instanceof CaptureSource source
                && !(source instanceof CurrentSource) ? source : null);
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
     * The launch spec a run starts, or {@code null} for none — {@code internal.launch.Target} parses it: a
     * {@code -D}{@value #LAUNCH_TARGET} given by hand, else {@link #pick}.
     */
    public static String launchTarget() {
        String spec = System.getProperty(LAUNCH_TARGET);
        if (spec != null && !spec.isBlank()) return spec.trim();
        return pick(launchTargets, launchChoice, Os.current());
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
     * when none marks it — how {@code Bot.runFlow} reads the run's own parameters ({@code flow}, {@code doSetup}).
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
