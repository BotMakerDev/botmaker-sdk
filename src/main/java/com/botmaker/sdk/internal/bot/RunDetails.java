package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.internal.flow.InstalledRun;
import com.botmaker.sdk.internal.launch.LaunchTarget;
import com.botmaker.sdk.internal.launch.Target;
import com.botmaker.sdk.internal.observe.IpcObserver;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.shared.ipc.TelemetryEvent;
import com.botmaker.shared.runlog.RunFiles;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the SDK adds to a run's log, which shared's {@link RunFiles} keeps (spec {@code
 * docs/superpowers/specs/2026-11-03-run-reports-design.md} §2): the run in the SDK's own words, its facts'
 * {@code details}, and each line tied to the bot's own line that led to it, as the host's trace ties it.
 */
public final class RunDetails {

    private RunDetails() {}

    /** Starts this run's log in its miss frames' folder, read off {@code values}, the bot's values classes. */
    public static void start(Class<?>... values) {
        RunFiles.start(MissFrames.folder().orElse(null), of(values), RunDetails::attributed);
    }

    /** {@code line} tied to the bot's own line that led to it; as is when none. */
    static TelemetryEvent.Log attributed(TelemetryEvent.Log line) {
        return IpcObserver.botFrame().map(at -> line.at(at.getClassName(), at.getLineNumber())).orElse(line);
    }

    /**
     * The SDK's words for the run: {@code flow}, {@code launchTarget} (its kind), {@code platform} (an emulator's
     * instance, else the OS) and each {@code @Param} of {@code values} whose value is plain text, a number, a switch
     * or a choice, as {@code param.<name>}.
     */
    static Map<String, String> of(Class<?>... values) {
        Map<String, String> out = new LinkedHashMap<>();
        String flow = InstalledRun.flow() == null ? null : InstalledRun.nameOf(InstalledRun.flow());
        if (flow != null) out.put("flow", flow);
        LaunchTarget target = Target.current();
        out.put("launchTarget", target == null ? "none" : target.getClass().getSimpleName());
        out.put("platform", target instanceof LaunchTarget.EmulatorApp app && app.instance() != null
                && !app.instance().isBlank() ? app.instance() : System.getProperty("os.name", ""));
        for (Class<?> holder : values == null ? new Class<?>[0] : values) {
            for (Field field : holder.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !field.isAnnotationPresent(Param.class)) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (plain(value)) out.put("param." + field.getName(), String.valueOf(value));
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
