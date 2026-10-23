package samplebot;

import samplebot.plugins.sdk.Activities;
import samplebot.plugins.sdk.Pictures;
import samplebot.plugins.sdk.Screens;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Place;
import com.botmaker.sdk.api.time.Wait;

import java.time.Duration;
import java.util.OptionalLong;

/**
 * Clicks the collect button while there is one, and says so when there is not; with enough gold, buys the item
 * right of the shop's first one.
 */
public final class Collect {

    private Collect() {}

    public static Activities.Collect body() {
        if (!Navigate.press(Screens.HOME.collect).done()) {
            return Activities.Collect.NOTHING_LEFT;
        }
        OptionalLong gold = Navigate.number(Screens.HOME.gold);
        if (gold.isPresent() && gold.getAsLong() > 100) {
            Navigate.press(Screens.SHOP.buy, Pick.at(Place.rightOf(Pictures.ITEM)));
        }
        Navigate.read(Screens.HOME.title).ifPresent(title -> Wait.milliseconds(title.length()));
        Wait.between(Duration.ofMillis(600), Duration.ofMillis(1200));
        return Activities.Collect.NEXT;
    }
}
