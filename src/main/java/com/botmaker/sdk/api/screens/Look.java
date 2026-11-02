package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

/**
 * Another look of a {@link Button}, and what that look means — {@code Look.of(Pictures.BEER_BUY_OFF,
 * State.DISABLED)}, or one of the bot's own states, {@code Look.of(Pictures.HEALTH_MAX, States.Blocks.MAXED)}. Any
 * look finds the button; it is no input of its own, so it is never tried on its own.
 *
 * @param picture the button as it looks then
 * @param state   what that look means
 */
public record Look(ImageTemplate picture, ButtonState state) {

    public Look {
        // A look saying nothing is the usual one.
        state = state == null ? State.NORMAL : state;
    }

    public static Look of(ImageTemplate picture, ButtonState state) {
        return new Look(picture, state);
    }
}
