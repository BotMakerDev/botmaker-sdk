package samplebot;

import samplebot.params.Parameters;
import samplebot.params.bot.Progress;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Activities;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.time.Wait;

/** Does nothing, for as long as {@link Parameters#restBetween} says, and remembers it rested. */
public final class Rest {

    private Rest() {}

    public static Activities.Rest body() {
        Wait.time(Parameters.restBetween);
        Params.set(Progress::rested, true);
        return Activities.Rest.NEXT;
    }
}
