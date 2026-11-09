package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.params.Params;

import java.util.Objects;

/**
 * Whether an {@link Input} may be pressed by a run that explores on its own (2026-11-09): {@link Free free}, it
 * {@link Spends spends} one of the bot's facts ({@code .spends(Progress::gems)}), {@link Never never} done, or
 * {@link None none} said yet. An input with none is never pressed by such a run: whoever cut it says which, once.
 */
public sealed interface Verdict permits Verdict.Free, Verdict.Spends, Verdict.Never, Verdict.None {

    /** No verdict yet: the default of every input until one is written. */
    Verdict NONE = new None();

    /** Pressing it costs nothing the bot counts. */
    Verdict FREE = new Free();

    /** It costs nothing the bot counts: {@code .free()}. */
    record Free() implements Verdict {}

    /**
     * It spends {@code fact}, a method of one of the bot's {@code @Facts} groups: {@code .spends(Progress::gems)}. A
     * drop of that fact after its press is what it is for.
     */
    record Spends(Params.Of<?, ?> fact) implements Verdict {
        public Spends {
            Objects.requireNonNull(fact, "fact");
        }
    }

    /** It is never done, because {@code why}: it spends premium currency or real money. */
    record Never(String why) implements Verdict {
        public Never {
            Objects.requireNonNull(why, "why");
        }
    }

    /** Nothing said yet. */
    record None() implements Verdict {}

    /** {@link Never} for {@code why}; {@link #NONE} when it is null or blank, as an input with no reason never done. */
    static Verdict never(String why) {
        return why == null || why.isBlank() ? NONE : new Never(why);
    }
}
