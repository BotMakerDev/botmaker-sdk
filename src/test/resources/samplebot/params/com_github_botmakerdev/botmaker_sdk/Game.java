package samplebot.params.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;

/** The BotMaker SDK's game parameters on this computer: which game it launches and where it reads pixels. */
@Param
public class Game {

    @SdkValue(SdkValue.Id.LAUNCH_TARGET)
    @Param.Shown(description = "Which of the bot's games this computer launches")
    public LaunchChoice launchTarget() {
        return LaunchChoice.of("");
    }

    @SdkValue(SdkValue.Id.CAPTURE)
    @Param.Shown(description = "The game's window: where the bot reads pixels from")
    public CaptureSource captureSource() {
        return CaptureSource.desktop();
    }
}
