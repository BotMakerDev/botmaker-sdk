package com.botmaker.sdk.api.screens;

/**
 * What {@link Navigate#press} did — {@code switch (Navigate.press(Screens.MISSIONS.claim, Pick.each(Order.NORTH))
 * .result())}: the input pressed or why not, the look it showed, and the screen the game showed after.
 *
 * @param result what came of it
 * @param landed the screen of the map the game showed after it; null when it shows none
 * @param times  how many times it was pressed: more than one only for {@link Pick#untilNone}; a {@link Pick#each}
 *               counts once
 * @param state  the look its button showed: the one that kept it from being pressed, or the one it was pressed in
 *               ({@code States.Presses.BADGE}); {@link State#NORMAL} when it had no other, or when the press read
 *               none: {@link Pick#each} and a pressed list member read no copy's look
 */
public record Pressed(Result result, Screen landed, int times, ButtonState state) {

    public Pressed {
        state = state == null ? State.NORMAL : state;
    }

    /** A press whose button showed its usual look. */
    public Pressed(Result result, Screen landed, int times) {
        this(result, landed, times, State.NORMAL);
    }

    /** What came of a press. */
    public enum Result {
        /** Pressed, and the game stayed on its screen or went where the map says it leads. */
        DONE,
        /** Its button showed only its {@link State#DISABLED disabled} look: nothing was pressed. */
        DISABLED,
        /**
         * Its button showed a look of the bot's that is never pressed, {@code States.Blocks.MAXED}, which
         * {@link #state()} carries: nothing was pressed.
         */
        BLOCKED,
        /** Its button showed its locked look: nothing was pressed. */
        @Deprecated(forRemoval = true)
        LOCKED,
        /** Its button showed its done look, finished for good: nothing was pressed. */
        @Deprecated(forRemoval = true)
        FINISHED,
        /** Its button showed its busy look, in progress: nothing was pressed. */
        @Deprecated(forRemoval = true)
        BUSY,
        /** It was not on its screen, its strip scrolled both ways: nothing was pressed. */
        ABSENT,
        /** Its screen could not be reached now, or it is locked or its method says no: nothing was pressed. */
        NOT_NOW,
        /**
         * Pressed, and the game showed another screen than its own or where the map says it leads, or none; or it
         * stayed on its own after a second press, though the map says it leads away.
         */
        LEFT
    }

    /**
     * What a press of a button showing {@code look} reports, nothing pressed: {@link Result#DISABLED} for the SDK's
     * greyed-out look, {@link Result#BLOCKED} for any other that blocks; null for a look that does not block.
     */
    public static Result blockedBy(ButtonState look) {
        if (look == null || !look.blocks()) return null;
        return look == State.DISABLED ? Result.DISABLED : Result.BLOCKED;
    }

    /** Whether it was pressed and went as the map says. */
    public boolean done() {
        return result == Result.DONE;
    }
}
