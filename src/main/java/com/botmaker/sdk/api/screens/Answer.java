package com.botmaker.sdk.api.screens;

import java.util.Arrays;

/**
 * What a {@link Condition} says: yes, no, or that it cannot tell yet (a fact the game never showed, a method that
 * threw). Unknown never closes a way and is never a finding.
 */
public enum Answer {
    YES("yes", "Yes"),
    NO("no", "No"),
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    Answer(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The stable id, as a log or a state file keeps it. */
    public String id() {
        return id;
    }

    /** The answer as a person reads it. */
    public String displayName() {
        return displayName;
    }

    /** The answer of {@code id}; {@link #UNKNOWN} for one this version does not know. */
    public static Answer fromId(String id) {
        return Arrays.stream(values()).filter(a -> a.id.equals(id)).findFirst().orElse(UNKNOWN);
    }

    /** {@link #YES} for true, {@link #NO} for false. */
    public static Answer of(boolean holds) {
        return holds ? YES : NO;
    }
}
