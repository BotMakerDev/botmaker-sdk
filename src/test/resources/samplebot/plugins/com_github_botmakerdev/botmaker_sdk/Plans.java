package samplebot.plugins.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.plan.Plan;

/** What matters most, as tiers of this bot's goals: one constant per plan, which the Runner offers beside the flows. */
@SdkValue(SdkValue.Id.PLANS)
public final class Plans {

    public static final Plan DAILY = Plan.of(
            Plan.tier(Goals.RESTED, Goals.SOUND_HIDDEN),
            Plan.tier(Goals.CLAIMED_TODAY, Goals.ENOUGH_GOLD, Goals.SOME_GOLD),
            Plan.tier(Goals.NOT_TOO_RICH, Goals.POOR, Goals.NAMED, Goals.SHOP_OPEN, Goals.SOLVED));

    private Plans() {}
}
