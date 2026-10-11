package com.botmaker.sdk.internal.flow.choices;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;

import java.util.List;

/** A bot's {@code Flows} class, for {@code RunChoicesTest}: found beside the values class it is given. */
@SdkValue(SdkValue.Id.FLOWS)
public final class Flows {

    public static final Flow MAIN = flow("Collect");

    public static final Flow DAILY = flow("Daily");

    /** Not a flow: a choice naming it is refused. */
    public static final String NOTE = "not a flow";

    private Flows() {}

    private static Flow flow(String activity) {
        Activity only = new Activity(activity);
        return new Flow(List.of(new Flow.Step(only, ActivityBody.none(), "", true, false, false, false, Activity.NONE)),
                List.of(), only, Flow.Limits.DEFAULT);
    }
}
