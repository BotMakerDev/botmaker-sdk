package samplebot.plugins.sdk;

import samplebot.Battle;
import samplebot.Collect;
import samplebot.Rest;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.flow.Flow;

import java.util.List;

/** This bot's activity flows, one constant per way of playing. */
@SdkValue(SdkValue.Id.FLOWS)
public final class Flows {

    /** Collect until there is nothing left, then fight; a win goes back to collecting and a loss rests first. */
    public static final Flow MAIN = Flow.of(
            List.of(Flow.activity(Activities.Collect.class, Collect::body)
                            .described("Click the collect button while there is one.")
                            .on(Screens.HOME),
                    Flow.activity(Activities.Battle.class, Battle::body)
                            .described("Start a fight and wait for it to end.")
                            .goesHome(),
                    Flow.activity(Activities.Rest.class, Rest::body)
                            .described("Wait for Parameters.restBetween, then go round again.")
                            .switchable()
                            .whenOff(Activities.Collect.class)),
            List.of(Flow.edge(Activities.Collect.NEXT, Activities.Collect.class),
                    Flow.edge(Activities.Collect.NOTHING_LEFT, Activities.Battle.class),
                    Flow.edge(Activities.Battle.WON, Activities.Collect.class),
                    Flow.edge(Activities.Battle.LOST, Activities.Rest.class),
                    Flow.edge(Activities.Rest.NEXT, Activities.Collect.class)),
            Activities.Collect.class,
            Flow.limits(1000, 1000));

    private Flows() {}
}
