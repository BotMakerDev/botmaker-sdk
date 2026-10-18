package com.botmaker.sdk.api.screens;

import java.util.function.BooleanSupplier;

/**
 * A way into a screen: {@code via}, an input of another screen, opens it — {@code Way.of(TAVERN_CHOICE.tavern)}.
 * The screen it is on is the screen {@code via} is a field of. With a {@link #pick} it goes through that copy of a
 * repeated button. With a {@link #whenLooks state} it is taken only while the button looks so: a button leading to
 * one screen when ready and another when greyed has one way for each. With a {@link #when condition} it is taken
 * only while a method of the bot or of a plugin answers true: an input that leads to the event on event days and to
 * the shop otherwise (the user's call, 2026-10-15).
 *
 * <p>A way {@link #onItsOwn on its own} has no input: the screen follows {@code after} by itself, Home once the
 * loading screen is done (the user's call, 2026-10-18). A walk takes it by waiting for the screen's marker, up to the
 * time {@code after} {@link Setup#loads loads in}.
 *
 * @param via   the input that opens the screen, a field of the screen it is on; null for a way on its own
 * @param pick  the copy of a repeated button it goes through; null for the first
 * @param state the look of {@code via}'s button this way needs; null for any look
 * @param when  the method answering whether this way leads here now; null for always
 * @param after the screen this one follows by itself, with no input; null for a way through {@code via}
 */
public record Way(Input via, Pick pick, State state, BooleanSupplier when, Screen after) {

    public static Way of(Input via) {
        return new Way(via, null, null, null, null);
    }

    /** The way a screen follows {@code after} by itself, with no input: Home after the loading screen. */
    public static Way onItsOwn(Screen after) {
        return new Way(null, null, null, null, after);
    }

    /** Whether it has no input: the screen follows {@link #after} by itself. */
    public boolean isOnItsOwn() {
        return via == null && after != null;
    }

    /** This way, through the copy of {@code via}'s button that {@code pick} names. */
    public Way pick(Pick pick) {
        return new Way(via, pick, state, when, after);
    }

    /** This way, taken only while {@code via}'s button looks as {@code state} says. */
    public Way whenLooks(State state) {
        return new Way(via, pick, state, when, after);
    }

    /** This way, taken only while {@code condition} answers true: {@code Firestone::eventOn}. */
    public Way when(BooleanSupplier condition) {
        return new Way(via, pick, state, condition, after);
    }
}
