// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What the Evaluator, or the Coordinator, found wrong with a run ({@link ExploreEvent.Finding}; spec
 * 2026-11-24-explore-agents-design § Findings and hand-off). Each kind has one owner: the Coordinator acts on
 * {@link #DRIFT} and {@link #STUCK}, the Reader takes {@link #BAD_MAP} again, the Mechanic {@link #SDK_FAULT}.
 */
public enum FindingKind {
    /** The run presses away from its goal. */
    DRIFT("drift", "Drifting from the goal"),
    /** The run cannot go on, and says why. */
    STUCK("stuck", "Stuck"),
    /** What the map holds is wrong: a screen misnamed, an element missing, a replay that does not match. */
    BAD_MAP("bad_map", "Map is wrong"),
    /** The SDK itself failed: the Mechanic's. */
    SDK_FAULT("sdk_fault", "SDK fault"),
    /** {@code done} was proposed with nothing code could check on its frame, so the run went on. */
    NO_WITNESS("no_witness", "Done without a witness"),
    /** A kind this SDK does not know, read from a journal a newer one wrote. */
    @JsonEnumDefaultValue
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    FindingKind(String id, String displayName) {
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

    /** The kind whose {@link #id()} is {@code id}; {@link #UNKNOWN} for any other. */
    public static FindingKind fromId(String id) {
        for (FindingKind kind : values()) {
            if (kind.id.equals(id)) return kind;
        }
        return UNKNOWN;
    }
}
