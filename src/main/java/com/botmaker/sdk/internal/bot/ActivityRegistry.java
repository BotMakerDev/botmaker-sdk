package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.util.Debug;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every activity this bot has, by name — the one map, and the reason it is one.
 *
 * <p>There are two ways to write an activity. {@code Activities.define("Mining", ctx -> …)} is the one a
 * project uses now; subclassing {@code Activity} is the one bots written before 2026-08-29 use, and it keeps
 * working. Both end up here, because {@code Activity.disable("Mining")} has to find either — a second map
 * would make that call silently miss every activity written the other way, which is the kind of bug that
 * looks like the flow being wrong.
 *
 * <p>Insertion-ordered, and last registration wins: a project that defines one name twice gets the later
 * body, matching what constructing the same {@code Activity} subclass twice always did.
 *
 * <p>Not thread-safe, deliberately. Definitions are made from a bot's {@code main} before the flow starts;
 * enabling and disabling happen on the one thread the flow walks. Adding a lock here would suggest the
 * activities themselves may be driven concurrently, which nothing in the SDK supports.
 */
public final class ActivityRegistry {

    /**
     * What the flow needs from an activity, however it was written.
     *
     * <p>Four members and no more: the name it is wired under, whether it should run at all, a way for a
     * running bot to change that answer, and the work. Notably <b>not</b> the outcome <em>type</em> — a
     * lambda answers an {@link Outcome} and a legacy subclass answers its own enum, and the walk has only
     * ever asked an outcome for its name.
     */
    public interface Runner {

        String name();

        /** Whether the flow should run it this pass — the configured default, plus any runtime override. */
        boolean active();

        /** Overrides {@link #active()} for the rest of the run. */
        void setEnabled(boolean enabled);

        /** Do it, and report what happened. Never {@code null}: an activity that reports nothing is done. */
        Outcome execute();
    }

    private static final Map<String, Runner> REGISTRY = new LinkedHashMap<>();

    private ActivityRegistry() {}

    /** Registers, or replaces, the activity of that name. */
    public static void register(Runner runner) {
        if (runner == null || runner.name() == null || runner.name().isBlank()) return;
        REGISTRY.put(runner.name(), runner);
    }

    /** The activity of that name, or {@code null} when nothing has been written for it. */
    public static Runner get(String name) {
        return name == null ? null : REGISTRY.get(name);
    }

    /**
     * Sets the named activity's runtime enablement. An unknown name is a warning and a no-op, so a typo
     * never stops a running bot.
     */
    public static void setEnabled(String name, boolean enabled) {
        Runner runner = get(name);
        if (runner == null) {
            Debug.error("[Activity] setEnabled: no activity named '" + name + "' — known: "
                    + REGISTRY.keySet() + ". Ignoring.");
            return;
        }
        runner.setEnabled(enabled);
    }

    /** Every registered name, in registration order. */
    public static List<String> names() {
        return List.copyOf(REGISTRY.keySet());
    }

    /** Test-only: clear the process-global registry between tests. */
    public static void clear() {
        REGISTRY.clear();
    }
}
