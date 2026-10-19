package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunProperty;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.plugin.params.ParamSlot;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The games the bot supports — its {@code launchTargets} parameter ({@link SdkValues#LAUNCH_TARGETS}) in
 * {@code Sdk.java}, preferred first, which its author lists — and the one this computer launches: the runner's pick
 * ({@link SdkValues#LAUNCH_TARGET}) when it is one of them, else the first its system can launch.
 *
 * <p>The bot's Java, not this computer's: a game is the same game whichever system runs it, and its author lists
 * every launcher it works from (Steam, Heroic, an emulator app). Which of them a computer can use is deduced from its
 * system ({@code LaunchSpec.runsOn}), so nothing about a platform is written. A {@code -Dbotmaker.launch.target}
 * given to a bot started by hand still wins at run time — and so does the run property a computer kept before
 * (until 2026-10-12), which Studio passes as that {@code -D}: {@link #current} says so, and the first write clears it.
 *
 * <p>The list never holds one launcher twice, however it was spelled: every write collapses copies (2026-10-19,
 * a list that had grown a copy per pick).
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
        return ParamSlot.open(services, SdkValues.LAUNCH_TARGETS).flatMap(ctx -> ctx.value(List.class)).map(InstalledGame::specs).orElse(List.of());
    }

    /** The launcher the person running the bot picked, or {@code ""} for none: the first this computer can launch. */
    public static String chosen(StudioServices services) {
        if (services == null) return "";
        return ParamSlot.open(services, SdkValues.LAUNCH_TARGET).flatMap(ctx -> ctx.value(LaunchChoice.class))
                .map(LaunchChoice::spec).orElse("");
    }

    /**
     * The spec a run on this computer launches, or {@code null} for none: the run property a computer kept before,
     * while it is there, else the runner's pick when the bot lists it and this system can start it, else the first of
     * the bot's launchers this system can start.
     */
    public static String current(StudioServices services) {
        if (services == null) return null;
        Optional<String> old = services.runs().get(OLD_RUN_PROPERTY);
        if (old.isPresent()) return old.get();
        return InstalledGame.pick(all(services), chosen(services), Os.current());
    }

    /** The parsed spec, or {@code null} when there is none or it does not parse. */
    public static LaunchSpec spec(StudioServices services) {
        String spec = current(services);
        return spec == null ? null : LaunchSpec.parse(spec);
    }

    /**
     * The author's pick: makes {@code spec} the bot's preferred launcher — first in its list, held once — or, for
     * {@code null}, takes the one this computer launches out of the list. A runner's pick of another launcher is
     * dropped, so this computer launches what was just picked. A bot lacking the parameter has it written first.
     * Either way the run property a computer kept before is cleared.
     *
     * @return empty when written, else why nothing was
     */
    public static Optional<String> set(StudioServices services, String spec) {
        if (spec == null || spec.isBlank()) return remove(services, current(services));
        return write(services, InstalledGame.canonical(spec), true);
    }

    /**
     * The author's removal: takes every copy of {@code spec} out of the bot's list, and the runner's pick when it
     * named it. Nothing to remove writes nothing, and a bot lacking the list is not given one.
     *
     * @return empty when written or nothing was there, else why nothing was
     */
    public static Optional<String> remove(StudioServices services, String spec) {
        if (spec == null || spec.isBlank()) return Optional.empty();
        return write(services, InstalledGame.canonical(spec), false);
    }

    /**
     * The bot's list with {@code spec} first ({@code adding}) or gone, written collapsed when that changes it; then the
     * runner's pick is cleared when it names another launcher than the one just added, or the one just removed.
     */
    private static Optional<String> write(StudioServices services, String spec, boolean adding) {
        // A removal from a bot that lists nothing writes no list: there is nothing to take out.
        Optional<ValueContext> list;
        if (adding) {
            ParamSlot.Opened opened = ParamSlot.openOrCreate(services, SdkValues.LAUNCH_TARGETS, "Sdk.launchTargets");
            if (opened.ctx().isEmpty()) return Optional.of(opened.refused());
            list = opened.ctx();
        } else {
            list = ParamSlot.open(services, SdkValues.LAUNCH_TARGETS);
        }
        if (services.runs().get(OLD_RUN_PROPERTY).isPresent()) services.runs().set(OLD_RUN_PROPERTY, null);
        List<String> was = all(services);
        List<String> next = next(was, spec, adding);
        if (list.isPresent() && !next.equals(was)) {
            Optional<String> refused = list.get().write(next.stream().map(Launcher::of).toList());
            if (refused.isPresent()) return refused;
        }
        if (!staleChoice(chosen(services), spec, adding)) return Optional.empty();
        return ParamSlot.open(services, SdkValues.LAUNCH_TARGET).flatMap(choice -> choice.write(LaunchChoice.of("")));
    }

    /** The bot's list {@code was} with canonical {@code spec} first ({@code adding}) or gone, collapsed. Pure. */
    static List<String> next(List<String> was, String spec, boolean adding) {
        List<String> next = new ArrayList<>(was);
        next.removeIf(each -> InstalledGame.canonical(each).equals(spec));
        if (adding) next.addFirst(spec);
        return collapsed(next);
    }

    /**
     * Whether the runner's pick {@code chosen} goes once canonical {@code spec} is added — it names another, which
     * would hide what the author just picked — or removed — it names {@code spec}. Pure.
     */
    static boolean staleChoice(String chosen, String spec, boolean adding) {
        String picked = InstalledGame.canonical(chosen);
        return adding ? !picked.isEmpty() && !picked.equals(spec) : picked.equals(spec);
    }

    /** {@code specs} with every launcher once, in its first place, as {@link InstalledGame#canonical} writes it. */
    static List<String> collapsed(List<String> specs) {
        Set<String> seen = new LinkedHashSet<>();
        for (String each : specs) {
            String one = InstalledGame.canonical(each);
            if (!one.isEmpty()) seen.add(one);
        }
        return List.copyOf(seen);
    }
}
