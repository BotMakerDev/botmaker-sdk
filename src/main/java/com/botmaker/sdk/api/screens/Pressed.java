package com.botmaker.sdk.api.screens;

/**
 * What {@link Navigate#press} did — {@code switch (Navigate.press(Screens.MISSIONS.claim, Pick.each(Order.NORTH))
 * .result())}: the input pressed or why not, and the screen the game showed after.
 *
 * @param result what came of it
 * @param landed the screen of the map the game showed after it; null when it shows none
 * @param times  how many times it was pressed: more than one only for {@link Pick#untilNone}; a {@link Pick#each}
 *               counts once
 */
public record Pressed(Result result, Screen landed, int times) {

    /** What came of a press. */
    public enum Result {
        /** Pressed, and the game stayed on its screen or went where the map says it leads. */
        DONE,
        /** Its button showed only its {@link State#DISABLED disabled} look: nothing was pressed. */
        DISABLED,
        /** It was not on its screen, its strip scrolled both ways: nothing was pressed. */
        ABSENT,
        /** Its screen could not be reached now, or it is locked or its method says no: nothing was pressed. */
        NOT_NOW,
        /** Pressed, and the game showed another screen than its own or where the map says it leads, or none. */
        LEFT
    }

    /** Whether it was pressed and went as the map says. */
    public boolean done() {
        return result == Result.DONE;
    }
}
