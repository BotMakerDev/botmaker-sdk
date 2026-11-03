package com.botmaker.sdk.api.state;

import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.internal.state.KeptFacts;

import java.util.Optional;

/**
 * What this machine last saw of the game, fact by fact ({@link Fact}): a cache of what the game showed, kept per
 * launch target (and per account, once {@link Fact#account} is read) in the SDK's state folder of the bot, never in
 * its Java. Losing it costs a reread, never wrong logic.
 *
 * <pre>{@code
 * if (GameState.get(Facts.LEVEL).orElse(0) >= 5) Navigate.press(Screens.HOME.arena);
 * GameState.set(Facts.TUTORIAL_DONE, true);
 * }</pre>
 */
public final class GameState {

    private GameState() {}

    /**
     * The value {@code fact} holds: its fresh reading; its {@link Fact#assume assumed} one while no reading is kept;
     * empty when its reading is stale, or nothing is known.
     */
    public static <T> Optional<T> get(Fact<T> fact) {
        return KeptFacts.get(fact);
    }

    /** Keeps {@code value} as {@code fact}'s reading, now: what a step learned. */
    public static <T> void set(Fact<T> fact, T value) {
        KeptFacts.set(fact, value);
    }

    /**
     * Goes to {@code fact}'s screen ({@link Navigate#to}) and reads it there, for a flow that needs it fresh; coming
     * back is the caller's. Empty when the screen was not reached or nothing was read; a step's fact is not read.
     */
    public static <T> Optional<T> refresh(Fact<T> fact) {
        return KeptFacts.refresh(fact);
    }

    /** Drops {@code fact}'s reading: unknown until it is read again. */
    public static void forget(Fact<?> fact) {
        KeptFacts.forget(fact);
    }

    /** Drops every reading of the account the run plays. */
    public static void forgetAll() {
        KeptFacts.forgetAll();
    }
}
