package com.botmaker.sdk.api.screens;

/** What an {@link Input} is for, as Discovery names it: the activities are chosen from it after Discovery. */
public enum Kind {
    /** Work that repeats: a claim, a train, a start. */
    CHORE,
    /** A shortcut the game ships for a chore: Auto, x10, Max, Claim all, Skip. */
    SHORTCUT,
    /** Spending the player decides on. */
    CHOICE,
    /** Information only. */
    INFO
}
