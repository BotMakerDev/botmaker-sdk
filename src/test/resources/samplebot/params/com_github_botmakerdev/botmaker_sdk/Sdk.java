package samplebot.params.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.Flow;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Flows;

import java.util.Set;

/** The BotMaker SDK's parameters on this computer: each method is one, its body the value. */
@Param
public class Sdk {

    @SdkValue(SdkValue.Id.FLOW)
    @Param.Shown(category = "Run", description = "Which flow the bot runs")
    public Flow flow() {
        return Flows.MAIN;
    }

    @SdkValue(SdkValue.Id.DO_SETUP)
    @Param.Shown(category = "Run",
            description = "Set the game up first: tick it on a new computer or after changing a game setting")
    public boolean doSetup() {
        return true;
    }

    @SdkValue(SdkValue.Id.SKIP)
    @Param.Shown(category = "Run", description = "Activities this run skips")
    public Set<Class<? extends Outcome>> skip() {
        return Set.of();
    }

    @SdkValue(SdkValue.Id.LAUNCH_TARGET)
    @Param.Shown(category = "Game", description = "Which of the bot's games this computer launches")
    public LaunchChoice launchTarget() {
        return LaunchChoice.of("");
    }

    @SdkValue(SdkValue.Id.CAPTURE)
    @Param.Shown(category = "Game", description = "The game's window: where the bot reads pixels from")
    public CaptureSource captureSource() {
        return CaptureSource.desktop();
    }
}
