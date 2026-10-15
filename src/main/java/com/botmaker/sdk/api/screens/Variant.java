package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

/**
 * Another look of an {@link Input}'s button, and what that look means — {@code Variant.of(Pictures.BEER_BUY_OFF,
 * State.DISABLED)}. Any variant finds the button; it is no input of its own, so it is never tried on its own.
 *
 * @param picture the button as it looks then
 * @param state   what that look means
 */
public record Variant(ImageTemplate picture, State state) {

    public static Variant of(ImageTemplate picture, State state) {
        return new Variant(picture, state);
    }
}
