package samplebot;

import samplebot.params.Parameters;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Activities;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Facts;
import com.botmaker.sdk.api.state.GameState;
import com.botmaker.sdk.api.time.Wait;

/** Does nothing, for as long as {@link Parameters#restBetween} says, and remembers it rested. */
public final class Rest {

    private Rest() {}

    public static Activities.Rest body() {
        Wait.time(Parameters.restBetween);
        GameState.set(Facts.RESTED, true);
        return Activities.Rest.NEXT;
    }
}
