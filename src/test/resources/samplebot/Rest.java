package samplebot;

import samplebot.params.Parameters;
import samplebot.plugins.sdk.Activities;
import com.botmaker.sdk.api.time.Wait;

/** Does nothing, for as long as {@link Parameters#restBetween} says. */
public final class Rest {

    private Rest() {}

    public static Activities.Rest body() {
        Wait.time(Parameters.restBetween);
        return Activities.Rest.NEXT;
    }
}
