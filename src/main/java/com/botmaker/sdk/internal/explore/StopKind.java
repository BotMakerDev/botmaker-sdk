package com.botmaker.sdk.internal.explore;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Why a run exploring on its own stopped and handed the game to Claude (spec §2). The list is open: a kind is added
 * as the run meets a new way to be stuck. {@link #UNKNOWN} is a kind this SDK does not know, read from a journal a
 * newer one wrote.
 */
public enum StopKind {
    /** No screen's markers match the frame. */
    UNKNOWN_SCREEN("unknown_screen", "Unknown screen"),
    /** Something on a known screen that no input covers. */
    UNMAPPED_INPUT("unmapped_input", "Unmapped input"),
    /** Inputs with no safety verdict: free, spends or never. */
    NO_VERDICT("no_verdict", "No verdict"),
    /** Text that reads as a value, with no readout covering it. */
    MISSING_FACT("missing_fact", "Missing fact"),
    /** A way that worked once and not again, with nothing in the bot saying when. */
    MISSING_CONDITION("missing_condition", "Missing condition"),
    /** A press landed elsewhere than the map says, nowhere new, or not in time. */
    WRONG_ARRIVAL("wrong_arrival", "Wrong arrival"),
    /** Nothing pressed for a long while: nothing left to try, no goal reachable. */
    STUCK("stuck", "Stuck"),
    /** A guard refused, or what is in front is not the game. */
    GUARD("guard", "Guard"),
    /** The bot's own code threw. */
    CRASH("crash", "Crash"),
    @JsonEnumDefaultValue
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    StopKind(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** The kind whose {@link #id()} is {@code id}; {@link #UNKNOWN} for any other. */
    public static StopKind fromId(String id) {
        for (StopKind kind : values()) {
            if (kind.id.equals(id)) return kind;
        }
        return UNKNOWN;
    }
}
