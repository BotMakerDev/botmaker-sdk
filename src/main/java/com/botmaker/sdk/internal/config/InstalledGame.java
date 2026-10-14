package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.internal.capture.CurrentSource;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The game half of the bot's {@code Sdk} parameters, read off the classes {@code Bot.runFlow} is handed: the
 * launchers the bot can start its game from, the game VM it runs in and the capture source it reads — and the launch
 * target a run starts ({@link #launchTarget}). A {@code -D} given to a bot started by hand wins over the first two.
 *
 * <p>Read by mark, not by name: the field carrying {@code @SdkValue(SdkValue.Id.LAUNCH_TARGETS)} is the list,
 * whatever the bot calls it. A field of another type, or one that cannot be read, is no value: nothing is launched
 * and the VM is the one there is, which is what a bot with no such field gets too.
 */
public final class InstalledGame {

    /** The system property naming what a bot started by hand launches, in the launch spec grammar. */
    public static final String LAUNCH_TARGET = "botmaker.launch.target";

    private static volatile List<String> launchTargets = List.of();
    private static volatile String vm = "";

    private InstalledGame() {}

    /**
     * Takes the game parameters from the first of {@code values} that marks each, and points the bot at the capture
     * source one names ({@link Source#set}); with none, the source goes back to its default. A capture source of
     * {@code Source.current()} names none: it is what this sets.
     */
    public static void install(Class<?>... values) {
        List<String> targets = null;
        String named = null;
        CaptureSource capture = null;
        if (values != null) {
            for (Class<?> type : values) {
                if (type == null) continue;
                if (targets == null) targets = specs(field(type, SdkValue.Id.LAUNCH_TARGETS));
                if (named == null && field(type, SdkValue.Id.VM) instanceof String text) named = text;
                if (capture == null && field(type, SdkValue.Id.CAPTURE) instanceof CaptureSource source
                        && !(source instanceof CurrentSource)) {
                    capture = source;
                }
            }
        }
        launchTargets = targets == null ? List.of() : targets;
        vm = named == null ? "" : named.trim();
        Source.set(capture);
    }

    /** The launch specs the bot lists, preferred first; empty when it lists none. */
    public static List<String> launchTargets() {
        return launchTargets;
    }

    /** The game VM's name the bot gives, or {@code ""} for the one VM there is. */
    public static String vm() {
        return vm;
    }

    /**
     * The launch spec a run starts, or {@code null} for none — {@code internal.launch.Target} parses it: a
     * {@code -D}{@value #LAUNCH_TARGET} given by hand, else the first of the bot's launchers this computer's system can
     * launch.
     */
    public static String launchTarget() {
        String spec = System.getProperty(LAUNCH_TARGET);
        if (spec != null && !spec.isBlank()) return spec.trim();
        return firstRunnable(launchTargets, Os.current());
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
     * The value of the first {@code public static} field marked {@code id} in {@code values}, or null when none
     * marks it — how {@code Bot.runFlow} reads the run's own parameters ({@code flow}, {@code doSetup},
     * {@code skip}).
     */
    public static Object marked(SdkValue.Id id, Class<?>... values) {
        if (values == null) return null;
        for (Class<?> type : values) {
            Object value = type == null ? null : field(type, id);
            if (value != null) return value;
        }
        return null;
    }

    /** The value of the {@code public static} field of {@code type} marked {@code id}, or null. */
    private static Object field(Class<?> type, SdkValue.Id id) {
        Field[] declared;
        try {
            declared = type.getDeclaredFields();
        } catch (RuntimeException | LinkageError unreadable) {
            return null;
        }
        for (Field field : declared) {
            SdkValue mark = field.getAnnotation(SdkValue.class);
            int modifiers = field.getModifiers();
            if (mark == null || mark.value() != id || !Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
                continue;
            }
            try {
                return field.get(null);
            } catch (IllegalAccessException | RuntimeException unreadable) {
                return null;
            }
        }
        return null;
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
