package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.internal.config.ParamGroups;
import com.botmaker.sdk.internal.flow.InstalledRun;
import com.botmaker.sdk.internal.launch.LaunchTarget;
import com.botmaker.sdk.internal.launch.Target;
import com.botmaker.sdk.internal.trace.SdkRun;
import com.botmaker.shared.launch.GameVersion;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the SDK adds to a run's log (spec {@code docs/superpowers/specs/2026-11-03-run-reports-design.md} §2): the run
 * in the SDK's own words, its facts' {@code details}, handed to the host through the run channel (§7). The host keeps
 * the log, the facts it knows itself and which bot line each line belongs to.
 */
public final class RunDetails {

    private RunDetails() {}

    /** Tells the host this run's details, read off {@code values}, the bot's values classes. */
    public static void start(Class<?>... values) {
        SdkRun.channel().details(of(values));
    }

    /**
     * The SDK's words for the run: {@code flow}, {@code launchTarget} (its kind), {@code gameVersion} (best effort,
     * {@link GameVersion}: at most two seconds, else {@code unknown}), {@code platform} (an emulator's
     * instance, else the OS) and each parameter of the groups beside {@code values} ({@link ParamGroups}) whose value
     * is plain text, a number, a switch or a choice, as {@code param.<name>}.
     */
    static Map<String, String> of(Class<?>... values) {
        Map<String, String> out = new LinkedHashMap<>();
        String flow = InstalledRun.flow() == null ? null : InstalledRun.nameOf(InstalledRun.flow());
        if (flow != null) out.put("flow", flow);
        LaunchTarget target = Target.current();
        out.put("launchTarget", target == null ? "none" : target.getClass().getSimpleName());
        out.put("gameVersion", target == null ? GameVersion.UNKNOWN : GameVersion.of(target.launchSpec()));
        out.put("platform", target instanceof LaunchTarget.EmulatorApp app && app.instance() != null
                && !app.instance().isBlank() ? app.instance() : System.getProperty("os.name", ""));
        for (Object group : ParamGroups.groups(values)) {
            for (Method method : group.getClass().getDeclaredMethods()) {
                // A parameter is a public instance method with no argument, as Studio's Parameters window reads one.
                if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())
                        || method.getParameterCount() != 0 || method.getReturnType() == void.class
                        || method.isSynthetic()) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(group);
                    if (plain(value)) out.put("param." + method.getName(), String.valueOf(value));
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // a parameter that cannot be read is left out
                }
            }
        }
        return out;
    }

    /** Whether {@code value} says itself in a word: text, a number, a switch or an enum's constant. */
    private static boolean plain(Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?>;
    }
}
