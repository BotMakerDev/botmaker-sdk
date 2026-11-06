package samplebot.plugins.com_github_botmakerdev.botmaker_sdk;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Need;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.plan.Routine;
import samplebot.SampleBot;
import samplebot.params.bot.Progress;

import java.time.Duration;

/** What the bot wants, and what matters most: each kind of check once. */
public final class Goals {

    public static final Goal ENOUGH_GOLD = Goal.of(Need.atLeast(Progress::gold, 100));
    public static final Goal SOME_GOLD = Goal.of(Need.above(Progress::gold, 0));
    public static final Goal NOT_TOO_RICH = Goal.of(Need.atMost(Progress::gold, 9000));
    public static final Goal POOR = Goal.of(Need.below(Progress::gold, 10));
    public static final Goal RESTED = Goal.of(Need.is(Progress::rested));
    public static final Goal SOUND_HIDDEN = Goal.of(Need.not(Progress::soundShown));
    public static final Goal NAMED = Goal.of(Need.equalTo(Progress::player, "Hero"));
    public static final Goal SHOP_OPEN = Goal.of(SampleBot::shopOpen);
    public static final Goal CLAIMED_TODAY = Goal.done(() -> Screens.HOME.claim).within(Duration.ofDays(1));
    public static final Goal SOLVED = Goal.done(() -> Screens.HOME.solved);

    public static final Plan DAILY = Plan.of(
            Plan.tier(RESTED, SOUND_HIDDEN),
            Plan.tier(CLAIMED_TODAY, ENOUGH_GOLD, SOME_GOLD),
            Plan.tier(NOT_TOO_RICH, POOR, NAMED, SHOP_OPEN, SOLVED));

    public static final Routine RUN_BY_PLAN = DAILY;

    private Goals() {}
}
