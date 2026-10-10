package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityClass;
import com.botmaker.sdk.internal.flow.FlowWalker;

/**
 * Turning the flow's activities on and off while a bot runs.
 *
 * <pre>{@code
 * public static Activities.DailyReward body() {
 *     claimDailyReward();
 *     ActivitySwitch.disable(Activities.DailyReward.class);   // once is enough
 *     return Activities.DailyReward.NEXT;
 * }
 * }</pre>
 *
 * <p>An activity's switch on the Activity Flow canvas is its default. {@link #enable}/{@link #disable} override
 * it for the rest of the run, from anywhere — one activity switching another off, or a body switching itself off
 * after doing its work once. A disabled activity takes its off-wire ({@code .whenOff(…)}).
 *
 * <p>Each call names the activity by its enum's class literal, the one the flow's step names.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "◎")
public final class ActivitySwitch {

    private ActivitySwitch() {}

    /** Whether the activity runs right now: its canvas switch, plus any override made this run. */
    public static boolean active(@ActivityClass Class<? extends Outcome> activity) {
        return FlowWalker.active(Activity.of(activity));
    }

    /** Switches the activity on for the rest of the run. */
    public static void enable(@ActivityClass Class<? extends Outcome> activity) {
        FlowWalker.setEnabled(Activity.of(activity), true);
    }

    /** Switches the activity off for the rest of the run; the flow takes its off-wire. */
    public static void disable(@ActivityClass Class<? extends Outcome> activity) {
        FlowWalker.setEnabled(Activity.of(activity), false);
    }

    /** Switches the activity on or off for the rest of the run. */
    @Hidden("the boolean picks between enable and disable, which the menu already offers by name")
    public static void setEnabled(@ActivityClass Class<? extends Outcome> activity, boolean enabled) {
        FlowWalker.setEnabled(Activity.of(activity), enabled);
    }
}
