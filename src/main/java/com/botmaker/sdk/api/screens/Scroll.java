package com.botmaker.sdk.api.screens;

/**
 * How a {@link Strip} is scrolled — {@code Scroll.wheel(-5)}.
 *
 * @param notches how far each scroll of the wheel goes: negative down or toward you, positive up or away
 */
public record Scroll(int notches) {

    /** The most scrolls each way looking for a member: a strip longer than that is a screen's own. */
    public static final int MOST = 8;

    /** The wheel turned {@code notches} over a member that shows. */
    public static Scroll wheel(int notches) {
        return new Scroll(notches);
    }
}
