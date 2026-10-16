package com.botmaker.sdk.api.screens;

/**
 * How far automating a screen got, in order: the assistant's guide works out the stage the bot is at from each
 * screen's ({@link Setup#reached}); a screen with none is not surveyed yet.
 */
public enum Reached {
    /** Every input on it tried: Discovery is done with it. */
    SURVEYED,
    /** Its activity is written. */
    BUILT,
    /** Its activity passed its trial. */
    TRIED,
    /** Its activity is as fast as it gets. */
    TUNED,
    /** It cannot be opened on this account yet: it waits for its unlock. */
    LOCKED
}
