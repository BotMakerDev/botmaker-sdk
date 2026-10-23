package samplebot.params;

import com.botmaker.plugin.api.params.Param;

import java.time.Duration;

/** The bot's parameters, one {@code @Param} field each: the rows of <b>Project ▸ Parameters</b>. */
public final class Parameters {

    /** How long {@link samplebot.Rest} waits before the loop starts again. */
    @Param(category = "Timing", visibility = Param.PUBLIC,
            description = "How long the bot rests before going round again")
    public static Duration restBetween = java.time.Duration.ofMillis(60000L);

    /** How many times an activity polls for a picture before it gives up. */
    @Param(category = "Limits", visibility = Param.PUBLIC, min = 1,
            description = "How many times an activity looks for a picture before giving up")
    public static int maxAttempts = 20;

    private Parameters() {}
}
