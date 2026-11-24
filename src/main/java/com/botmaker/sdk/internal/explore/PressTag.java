// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How dangerous a press is to a run nobody watches ({@link ExploreEvent.Ranked}; spec 2026-11-24-explore-agents-design
 * § Unattended safety). The Reader tags each press; code enforces it: only {@link #SAFE} is pressed unattended.
 */
public enum PressTag {
    /** Moves about the app and changes nothing that matters. */
    SAFE("safe", "Safe"),
    /** Changes something that can be undone: a setting, a permission asked, a link out of the app. */
    RISKY("risky", "Risky"),
    /** Cannot be undone: sign out, delete, reset, buy. */
    DESTRUCTIVE("destructive", "Destructive"),
    /** A tag this SDK does not know, read from a journal a newer one wrote: never pressed unattended. */
    @JsonEnumDefaultValue
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    PressTag(String id, String displayName) {
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

    /** Whether a run nobody watches may press it. */
    public boolean unattended() {
        return this == SAFE;
    }

    /** The tag whose {@link #id()} is {@code id}; {@link #UNKNOWN} for any other. */
    public static PressTag fromId(String id) {
        for (PressTag tag : values()) {
            if (tag.id.equals(id)) return tag;
        }
        return UNKNOWN;
    }
}
