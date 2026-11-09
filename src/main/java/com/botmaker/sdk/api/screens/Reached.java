package com.botmaker.sdk.api.screens;

/**
 * How far automating a screen got, in order: the assistant's guide works out the stage the bot is at from each
 * screen's ({@link Setup#reached}); a screen with none is not surveyed yet.
 */
public enum Reached {
    /** Every input on it tried: Discovery is done with it. */
    SURVEYED,
    /** Its actions are written. */
    BUILT,
    /** Its actions passed their trial. */
    TRIED,
    /** Its actions are as fast as they get. */
    TUNED,
    /** It cannot be opened on this account yet: it waits for its unlock. */
    LOCKED
}
