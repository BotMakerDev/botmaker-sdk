package com.botmaker.sdk.api.screens;

/**
 * A way into a screen: {@code via} done on {@code from} opens it — {@code Way.of(Screens.HOME,
 * Input.click(Pictures.CAMPAIGN_HOME))}.
 *
 * @param from the screen the input is on
 * @param via  the input that opens the screen
 */
public record Way(GameScreen from, Input via) {

    public static Way of(GameScreen from, Input via) {
        return new Way(from, via);
    }
}
