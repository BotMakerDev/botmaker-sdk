package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.internal.flow.FlowWalker;

/**
 * Turning the flow's activities on and off while a bot runs.
 *
 * <pre>{@code
 * public static Outcome body() {
 *     claimDailyReward();
 *     ActivitySwitch.disable(Activities.DAILY_REWARD);   // once is enough
 *     return Outcome.NEXT;
 * }
 * }</pre>
 *
 * <p>An activity's switch on the Activity Flow canvas is its default. {@link #enable}/{@link #disable} override
 * it for the rest of the run, from anywhere — one activity switching another off, or a body switching itself off
 * after doing its work once. A disabled activity takes its {@code DISABLED} wire.
 *
 * <p>Named {@code ActivitySwitch} rather than {@code Activities} since 2026-10-02: {@code Activities} is your
 * bot's own class of {@link Activity} constants, which every call here takes.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "◎")
public final class ActivitySwitch {

    private ActivitySwitch() {}

    /** Whether the activity runs right now: its canvas switch, plus any override made this run. */
    public static boolean active(Activity activity) {
        return FlowWalker.active(activity);
    }

    /** Switches the activity on for the rest of the run. */
    public static void enable(Activity activity) {
        FlowWalker.setEnabled(activity, true);
    }

    /** Switches the activity off for the rest of the run; the flow takes its {@code DISABLED} wire. */
    public static void disable(Activity activity) {
        FlowWalker.setEnabled(activity, false);
    }

    /** Switches the activity on or off for the rest of the run. */
    @Hidden("the boolean picks between enable and disable, which the menu already offers by name")
    public static void setEnabled(Activity activity, boolean enabled) {
        FlowWalker.setEnabled(activity, enabled);
    }
}
