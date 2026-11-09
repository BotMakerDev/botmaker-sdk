package samplebot.params.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.plan.Plan;
import samplebot.plugins.com_github_botmakerdev.botmaker_sdk.Plans;

/** The BotMaker SDK's run parameters on this computer: each method is one, its body the value. */
@Param
public class Running {

    @SdkValue(SdkValue.Id.PLAN)
    @Param.Shown(description = "What the bot runs: one of its plans")
    public Plan plan() {
        return Plans.DAILY;
    }

    @SdkValue(SdkValue.Id.DO_SETUP)
    @Param.Shown(description = "Set the game up first: tick it on a new computer or after changing a game setting")
    public boolean doSetup() {
        return true;
    }
}
