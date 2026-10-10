package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.ActivityBody;

/**
 * {@link ActivityBody#none()}, the work of an activity drawn but not written: one object for every activity's
 * enum, so the walk asks {@code body == ActivityBody.none()}. Calling it reports nothing.
 */
public final class NoBody {

    private static final ActivityBody<Outcome> NONE = () -> null;

    private NoBody() {}

    /** The one body that does nothing, typed for whichever activity asks. */
    @SuppressWarnings("unchecked")
    public static <O extends Outcome> ActivityBody<O> none() {
        return (ActivityBody<O>) (ActivityBody<?>) NONE;
    }
}
