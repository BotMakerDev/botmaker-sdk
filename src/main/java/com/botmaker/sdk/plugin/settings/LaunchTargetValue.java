package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunProperty;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The launchers the bot can start its game from — its {@code launchTargets} parameter ({@link SdkValues#LAUNCH_TARGETS})
 * in {@code Sdk.java}, preferred first — and the one this computer uses: the first its system can launch.
 *
 * <p>The bot's Java, not this computer's: a game is the same game whichever system runs it, and its author lists
 * every launcher it works from (Steam, Heroic, an emulator app). Which of them a computer can use is deduced from its
 * system ({@code LaunchSpec.runsOn}), so nothing about a platform is written. A {@code -Dbotmaker.launch.target}
 * given to a bot started by hand still wins at run time — and so does the run property a computer kept before
 * (until 2026-10-12), which Studio passes as that {@code -D}: {@link #current} says so, and the first write clears it.
 *
 * <p>Beside {@link BotSettingsWindow} rather than in {@code launch}: the emulator picker writes it and the
 * launch button reads it, and neither package may name the other.
 */
public final class LaunchTargetValue {

    /** What a computer launched before the bot's Java said it: passed to every run as -D, so it still wins. */
    private static final RunProperty<String> OLD_RUN_PROPERTY = RunProperty.text(InstalledGame.LAUNCH_TARGET);

    private LaunchTargetValue() {}

    /** The bot's launchers, preferred first; empty when it lists none or the parameter does not read. */
    public static List<String> all(StudioServices services) {
        return open(services).flatMap(ctx -> ctx.value(List.class)).map(InstalledGame::specs).orElse(List.of());
    }

    /**
     * The spec a run on this computer launches, or {@code null} for none: the run property a computer kept before,
     * while it is there, else the first of the bot's launchers this computer's system can start.
     */
    public static String current(StudioServices services) {
        if (services == null) return null;
        Optional<String> old = services.runs().get(OLD_RUN_PROPERTY);
        if (old.isPresent()) return old.get();
        return InstalledGame.firstRunnable(all(services), Os.current());
    }

    /** The parsed spec, or {@code null} when there is none or it does not parse. */
    public static LaunchSpec spec(StudioServices services) {
        String spec = current(services);
        return spec == null ? null : LaunchSpec.parse(spec);
    }

    /**
     * Makes {@code spec} the bot's preferred launcher — first in its list, an earlier copy dropped — or, for
     * {@code null}, takes the one this computer launches out of the list. A bot lacking the parameter has it
     * written first. Either way the run property a computer kept before is cleared.
     *
     * @return empty when written, else why nothing was
     */
    public static Optional<String> set(StudioServices services, String spec) {
        if (services == null) return Optional.of("No project is open.");
        Optional<ValueContext> ctx = open(services);
        if (ctx.isEmpty()) {
            Optional<String> refused = services.pluginValues().create(SdkValues.LAUNCH_TARGETS);
            if (refused.isPresent()) return refused;
            ctx = open(services);
            if (ctx.isEmpty()) return Optional.of("Sdk.launchTargets is written by hand, so BotMaker leaves it alone.");
        }
        String clearing = spec == null || spec.isBlank() ? current(services) : null;
        services.runs().set(OLD_RUN_PROPERTY, null);
        List<String> next = new ArrayList<>(all(services));
        if (spec == null || spec.isBlank()) {
            next.remove(clearing);
        } else {
            next.remove(spec.trim());
            next.addFirst(spec.trim());
        }
        if (next.equals(all(services))) return Optional.empty();
        return ctx.get().write(List.copyOf(next));
    }

    private static Optional<ValueContext> open(StudioServices services) {
        try {
            return services == null ? Optional.empty() : services.pluginValues().open(SdkValues.LAUNCH_TARGETS);
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
