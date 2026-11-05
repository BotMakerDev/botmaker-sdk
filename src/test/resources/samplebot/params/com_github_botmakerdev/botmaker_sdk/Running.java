package samplebot.params.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.flow.Flow;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Flows;

/** The BotMaker SDK's run parameters on this computer: each method is one, its body the value. */
@Param
public class Running {

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
}
