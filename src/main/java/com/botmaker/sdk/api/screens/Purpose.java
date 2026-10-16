package com.botmaker.sdk.api.screens;

/** What an {@link Input} is for, as Discovery names it: the activities are chosen from it after Discovery. */
public enum Purpose {
    /** Work that repeats: a claim, a train, a start. */
    TASK,
    /** A shortcut the game ships for a task: Auto, x10, Max, Claim all, Skip. */
    SHORTCUT,
    /** Spending the player decides on. */
    SPEND,
    /** Information only. */
    INFO
}
