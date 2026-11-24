// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How code checked that a run's goal was reached on a frame ({@link ExploreEvent.Witnessed}; spec
 * 2026-11-24-explore-agents-design § Goal-directed exploration): never an agent's word alone.
 */
public enum WitnessCheck {
    /** The goal's screen, named when the run began, is on the map. */
    SCREEN_ON_MAP("screen_on_map", "Screen on the map"),
    /** The goal's mark, a picture, matches the frame again. */
    PICTURE("picture", "Picture matches"),
    /** The goal's mark, a text, is read on the frame again. */
    TEXT("text", "Text matches"),
    /** A check this SDK does not know, read from a journal a newer one wrote. */
    @JsonEnumDefaultValue
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    WitnessCheck(String id, String displayName) {
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

    /** The check whose {@link #id()} is {@code id}; {@link #UNKNOWN} for any other. */
    public static WitnessCheck fromId(String id) {
        for (WitnessCheck check : values()) {
            if (check.id.equals(id)) return check;
        }
        return UNKNOWN;
    }
}
