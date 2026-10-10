package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.internal.flow.Labels;

/**
 * A name of the bot's own that the canvas and the trace show by a label: an {@link Activity}, the name of an
 * activity's enum, or an {@code Outcome}, a constant of one. The label is the name as words — {@code NOTHING_LEFT}
 * is "Nothing left", {@code DailyReward} "Daily reward" — so the name holds nothing else.
 */
public interface Labelled {

    /** The name, {@code NOTHING_LEFT} — what an enum constant answers already. */
    String name();

    /** What the canvas draws and the trace prints: the name as words, "Nothing left". */
    default String label() {
        return Labels.of(name());
    }
}
