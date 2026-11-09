package samplebot.params.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.SdkValue;

import java.time.Duration;

/** How the bot clicks, looks and recovers on this computer: each method is one setting, its body the value. */
@Param
public class Settings {

    @SdkValue(SdkValue.Id.CONFIDENCE)
    @Param.Shown(description = "How sure a picture match has to be, 0 to 1")
    public double confidence() {
        return 0.8;
    }

    @SdkValue(SdkValue.Id.STUCK_AFTER)
    @Param.Shown(description = "How long a plan run may act with no progress")
    public Duration stuckAfter() {
        return Duration.ofMinutes(5);
    }

    @SdkValue(SdkValue.Id.GAME_RUNS_IN)
    @Param.Shown(description = "Where the game runs")
    public BotSettings.Where gameRunsIn() {
        return BotSettings.Where.PRIVATE_DISPLAY;
    }
}
