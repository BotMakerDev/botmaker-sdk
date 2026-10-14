package com.botmaker.sdk.api.screens;

/**
 * What an {@link Input} tried during Discovery did, when it opened no screen. An input that opened one says so
 * by the screen it opened: that screen's {@link GameScreen#from} names it, so "opens" is never written here.
 */
public enum Did {
    /** It changed the screen it is on: a tab, a toggle, a list scrolled, a sub-panel. */
    IN_PLACE,
    /** Nothing visible changed. */
    NONE,
    /**
     * It went to a screen mapped already that this one cannot name, since that screen is declared first and this one
     * is reached through it: a Home button deep down.
     */
    LEAVES
}
