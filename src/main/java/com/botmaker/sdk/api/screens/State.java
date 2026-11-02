package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.meta.Migrate;

import java.util.Locale;

/**
 * The SDK's own meanings of a button's {@link Look}: as it usually looks, picked, or greyed out. Every other meaning
 * is the bot's, a constant of its {@code States} ({@link ButtonState}, 2026-11-02). A {@link Way} can need one
 * ({@link Way#whenLooks}), so a button leads where its look says.
 */
public enum State implements ButtonState {
    /** As it usually looks: the picture the input was cut as. */
    NORMAL("Normal"),
    /** With a badge, a dot or a glow: something waits behind it. */
    @Deprecated(forRemoval = true)
    @Migrate.Dropped("a bot names its own state in its States class: States.Presses.BADGE")
    BADGE("Badge"),
    /** Greyed out or dimmed: pressing it does nothing now, so a run never presses it. */
    DISABLED("Disabled"),
    /** Picked: the tab or toggle that is on. */
    SELECTED("Selected"),
    /** Not open yet: a node whose parents are not done, so a run never presses it. */
    @Deprecated(forRemoval = true)
    @Migrate.Dropped("a bot names its own state in its States class: States.Blocks.LOCKED")
    LOCKED("Locked"),
    /** Finished for good: maxed, learnt, so a run never presses it. */
    @Deprecated(forRemoval = true)
    @Migrate.Dropped("a bot names its own state in its States class: States.Blocks.DONE")
    DONE("Done"),
    /** In progress: upgrading or on its way, so a run never presses it now. */
    @Deprecated(forRemoval = true)
    @Migrate.Dropped("a bot names its own state in its States class: States.Blocks.BUSY")
    BUSY("Busy");

    private final String displayName;

    State(String displayName) {
        this.displayName = displayName;
    }

    /** Whether a button showing it is never pressed: disabled (and, until they go, locked, done or busy). */
    @Override
    @SuppressWarnings("removal")
    public boolean blocks() {
        return this == DISABLED || this == LOCKED || this == DONE || this == BUSY;
    }

    /** How a tool names it: {@code disabled}. */
    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** How a window shows it: {@code Disabled}. */
    @Override
    public String displayName() {
        return displayName;
    }
}
