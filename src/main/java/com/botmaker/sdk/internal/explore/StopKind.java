// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

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
    /** Text that reads as a value, with no readout covering it: a plan run's, never raised by exploring (2026-11-20). */
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
    /** The server answered an error page (a 431, a 404) twice after one way or input (2026-11-20). */
    ERROR_PAGE("error_page", "Error page"),
    /**
     * An input that failed before failed again when the run pressed it once more (2026-11-20): the Cartographer gives it
     * a verdict or fixes its press; its screen stays not done.
     */
    FAILED_INPUT("failed_input", "Failed input"),
    /**
     * What presses of a screen's inputs did, read off its tree: ways to screens the bot has, and notes. The run
     * answers it itself ({@link ExploreEvent.Settled}); Claude never has to.
     */
    SETTLED("settled", "Settled by the tree"),
    /**
     * Discover itself got it wrong, not the bot (2026-11-19): raised only by a run on a {@code -SNAPSHOT} SDK
     * ({@link DevBuild}), held until the SDK is fixed and reloaded, never parked ({@link ExploreEvent.Faulted}).
     */
    FAULT("fault", "Discover fault"),
    /**
     * A screen set aside over its budget, back and still unbuilt after a second one (2026-11-20): it says what is
     * open and where its clock went. A resume gives it a fresh clock; a resume with ignore leaves it as it is.
     */
    LOW_YIELD("low_yield", "Low yield"),
    /**
     * What the run settled at a stop that its companion could not write into the bot, or wrote in part (2026-11-20):
     * it says each line left out and why, and what was settled. Claude writes it with the map tools, or fixes what
     * refused it, then resumes.
     */
    UNWRITTEN("unwritten", "Settled, not written"),
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
