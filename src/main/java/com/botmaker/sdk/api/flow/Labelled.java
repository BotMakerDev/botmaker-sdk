package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.internal.flow.Labels;

/**
 * A constant of the bot's own that the canvas and the trace show by a label: an {@link Activity} or an
 * {@code Outcome}. The label is the name as words, {@code NOTHING_LEFT} as "Nothing left", so the constant holds
 * nothing else.
 *
 * <p><b>Why this is its own interface</b>: it holds the default method, and {@code Activity} and {@code Outcome}
 * hold the constants. Initialising a class initialises a superinterface only when that interface declares a
 * default method, so the SDK's own enums behind {@code Outcome.NEXT} and {@code Activity.NONE} initialise this
 * one, which has no fields, and never the interface whose fields read them.
 */
public interface Labelled {

    /** The constant's name, {@code NOTHING_LEFT} — what an enum answers already. */
    String name();

    /** What the canvas draws and the trace prints: the name as words, "Nothing left". */
    default String label() {
        return Labels.of(name());
    }
}
