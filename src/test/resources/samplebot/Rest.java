package samplebot;

import samplebot.params.Parameters;
import samplebot.params.bot.Progress;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.time.Wait;

/** Does nothing, for as long as {@link Parameters#restBetween} says, and remembers it rested. */
public final class Rest {

    private Rest() {}

    public static void body() {
        Wait.time(Parameters.restBetween);
        Params.set(Progress::rested, true);
    }
}
