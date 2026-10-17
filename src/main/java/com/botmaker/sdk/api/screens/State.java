package com.botmaker.sdk.api.screens;

import java.util.Locale;

/**
 * How a button looks at a moment, one {@link Look} each: the same button with a badge, greyed out, or picked. A
 * {@link Way} can need one ({@link Way#whenLooks}), so a button leads where its look says.
 */
public enum State {
    /** As it usually looks: the picture the input was cut as. */
    NORMAL("Normal"),
    /** With a badge, a dot or a glow: something waits behind it. */
    BADGE("Badge"),
    /** Greyed out or dimmed: pressing it does nothing now, so a run never presses it. */
    DISABLED("Disabled"),
    /** Picked: the tab or toggle that is on. */
    SELECTED("Selected"),
    /** Not open yet: a node whose parents are not done, so a run never presses it. */
    LOCKED("Locked"),
    /** Finished for good: maxed, learnt, so a run never presses it. */
    DONE("Done"),
    /** In progress: upgrading or on its way, so a run never presses it now. */
    BUSY("Busy");

    private final String displayName;

    State(String displayName) {
        this.displayName = displayName;
    }

    /** Whether a button showing it is never pressed: disabled, locked, done or busy. */
    public boolean blocks() {
        return this == DISABLED || this == LOCKED || this == DONE || this == BUSY;
    }

    /** How a tool names it: {@code disabled}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** How a window shows it: {@code Disabled}. */
    public String displayName() {
        return displayName;
    }
}
