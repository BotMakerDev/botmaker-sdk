// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Who takes a stop of a run exploring on its own (audit 2026-11-20 §2.3): each {@link StopKind} has one, so an agent
 * never sees a stop it cannot fix. {@code tools/explore-watch.sh} reads it off the journal's lines, where each stop
 * carries it as {@code role}. {@link #UNKNOWN} is a role this SDK does not know, read from a journal a newer one wrote;
 * no kind has it.
 */
public enum StopRole {
    /** The run's own side: what the page's tree answers, written into the bot by the run's companion. */
    SETTLER("settler", "Settler"),
    /** A session with the map tools only: stops about the bot's map, pictures, conditions and verdicts. */
    CARTOGRAPHER("cartographer", "Cartographer"),
    /** A session in a worktree lane with the SDK's code: Discover's own faults and the bot's code throwing. */
    MECHANIC("mechanic", "Mechanic"),
    /** The bot's user, in the Explore tab: what only they can decide. No agent's wait answers such a stop. */
    USER("user", "You"),
    @JsonEnumDefaultValue
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    StopRole(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Who takes a stop of {@code kind}. */
    public static StopRole of(StopKind kind) {
        return switch (kind == null ? StopKind.UNKNOWN : kind) {
            case UNKNOWN_SCREEN, UNMAPPED_INPUT, NO_VERDICT, MISSING_FACT, MISSING_CONDITION, WRONG_ARRIVAL, ERROR_PAGE,
                 FAILED_INPUT, LOW_YIELD, UNWRITTEN -> CARTOGRAPHER;
            case FAULT, CRASH -> MECHANIC;
            case SETTLED -> SETTLER;
            // A window not the game's in front, nothing left to press, or a kind a newer SDK raised: the user's call.
            case GUARD, STUCK, UNKNOWN -> USER;
        };
    }

    /** The role whose {@link #id()} is {@code id}, case aside; {@link #UNKNOWN} for any other. */
    public static StopRole fromId(String id) {
        for (StopRole role : values()) {
            if (role.id.equalsIgnoreCase(id == null ? "" : id.strip())) return role;
        }
        return UNKNOWN;
    }
}
