package samplebot;

import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Pictures;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageFinder;

/** Starts a fight and waits for it to end, saying whether it was won. */
public final class Battle {

    private Battle() {}

    public static boolean body() {
        if (!ImageClicker.click(Pictures.BATTLE)) {
            return false;
        }

        for (int attempt = 0; SampleBot.keepTrying(attempt); attempt++) {
            if (ImageFinder.find(Pictures.VICTORY)) return true;
            if (ImageFinder.find(Pictures.DEFEAT)) return false;
            Wait.seconds(1);
        }
        return false;
    }
}
