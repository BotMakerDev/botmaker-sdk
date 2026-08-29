package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

/**
 * What an activity reports when it finishes — {@code BAG_FULL}, {@code NO_ORE} — and what the flow drawn in
 * BotMaker Studio routes on.
 *
 * <p>Its identity is a <b>name</b>, and nothing else. That is deliberate and it is a trade. An activity used
 * to be a generated class with its own nested {@code Outcome} enum, so {@code return Outcome.BAG_FULL;} was
 * checked by javac; nothing is generated any more, because a project's structure belongs to the person who
 * owns it, so the name is a string and a typo is not a compile error. What replaces the check is a
 * <b>picker</b>: the editor knows which outcomes the activity on the canvas declares, and offers exactly
 * those where the name is typed. A misspelled outcome behaves like an outcome nothing was wired to, which
 * is an ordinary state — it ends the run.
 *
 * <p>You rarely name this type. An activity body is handed an {@link ActivityContext} and answers
 * {@code ctx.outcome("BAG_FULL")} or {@code ctx.done()}, both of which build one.
 *
 * <p>Two outcomes are equal when their names are. The name is compared exactly, including case, because it
 * is matched against the wire drawn on the canvas and the canvas stores what was typed.
 */
@Palette(category = "flow", categoryLabel = "Flow", order = 105)
@Hidden("a value type: an activity body builds one through its ActivityContext, never by naming this class")
public final class Outcome {

    /**
     * The outcome every activity has whether it declares one or not — "nothing special to report, carry on".
     *
     * <p>Kept as a constant rather than spelled out at each use because the name has been renamed once
     * already ({@code DEFAULT} before it was {@code NEXT}), and the canvas stores this wire as a
     * <em>blank</em> outcome for exactly that reason. Renaming it again must stay free.
     */
    static final String NEXT = "NEXT";

    private final String name;

    private Outcome(String name) {
        this.name = name;
    }

    /**
     * The outcome by that name.
     *
     * <p>A blank or {@code null} name is the implicit one — the plain output wire — rather than an error,
     * for the reason nothing else in a bot's own configuration throws either: an activity that answered
     * badly should follow the wire it would have followed with nothing to say, not stop the bot.
     */
    public static Outcome of(String name) {
        return new Outcome(name == null || name.isBlank() ? NEXT : name);
    }

    /** The name the flow's wires are matched against. */
    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Outcome other && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
