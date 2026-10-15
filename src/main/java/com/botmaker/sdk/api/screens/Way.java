package com.botmaker.sdk.api.screens;

import java.util.function.BooleanSupplier;

/**
 * A way into a screen: {@code via} done on {@code from} opens it — {@code Way.of(Screens.HOME,
 * Input.click(Pictures.CAMPAIGN_HOME))}. With a {@link #whenLooks state} it is taken only while the button looks so:
 * a button leading to one screen when ready and another when greyed has one way for each. With a {@link #when
 * condition} it is taken only while a method of the bot or of a plugin answers true: an input that leads to the event
 * on event days and to the shop otherwise (the user's call, 2026-10-15).
 *
 * @param from  the screen the input is on
 * @param via   the input that opens the screen
 * @param state the look of {@code via}'s button this way needs; null for any look
 * @param when  the method answering whether this way leads here now; null for always
 */
public record Way(GameScreen from, Input via, State state, BooleanSupplier when) {

    public static Way of(GameScreen from, Input via) {
        return new Way(from, via, null, null);
    }

    /** This way, taken only while {@code via}'s button looks as {@code state} says. */
    public Way whenLooks(State state) {
        return new Way(from, via, state, when);
    }

    /** This way, taken only while {@code condition} answers true: {@code Firestone::eventOn}. */
    public Way when(BooleanSupplier condition) {
        return new Way(from, via, state, condition);
    }
}
