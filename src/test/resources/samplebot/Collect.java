package samplebot;

import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Pictures;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Screens;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.States;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Place;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.api.time.Wait;

import java.time.Duration;
import java.util.OptionalLong;

/**
 * Clicks the collect button while there is one, and says so when there is not; with enough gold, buys the item
 * right of the shop's first one.
 */
public final class Collect {

    private Collect() {}

    /** Whether there was something to collect. */
    public static boolean body() {
        if (!Navigate.press(Screens.HOME.collect).done()) {
            return false;
        }
        OptionalLong gold = Navigate.number(Screens.HOME.gold);
        if (gold.isPresent() && gold.getAsLong() > 100) {
            Pressed bought = Navigate.press(Screens.SHOP.buy, Pick.at(Place.rightOf(Pictures.ITEM)));
            switch (bought.state()) {
                case States.Blocks.MAXED -> {
                    return false;
                }
                default -> {
                }
            }
        }
        Navigate.read(Screens.HOME.title).ifPresent(title -> Wait.milliseconds(title.length()));
        Wait.between(Duration.ofMillis(600), Duration.ofMillis(1200));
        return true;
    }
}
