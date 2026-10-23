package samplebot;

import samplebot.plugins.sdk.Activities;
import samplebot.plugins.sdk.Pictures;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageFinder;

/** Starts a fight and waits for it to end, reporting which way it went. */
public final class Battle {

    private Battle() {}

    public static Activities.Battle body() {
        if (!ImageClicker.click(Pictures.BATTLE)) {
            return Activities.Battle.LOST;
        }

        for (int attempt = 0; SampleBot.keepTrying(attempt); attempt++) {
            if (ImageFinder.find(Pictures.VICTORY)) return Activities.Battle.WON;
            if (ImageFinder.find(Pictures.DEFEAT)) return Activities.Battle.LOST;
            Wait.seconds(1);
        }
        return Activities.Battle.LOST;
    }
}
