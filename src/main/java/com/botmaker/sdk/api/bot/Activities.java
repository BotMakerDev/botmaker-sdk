package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.internal.flow.FlowWalker;

/**
 * Turning the flow's activities on and off while a bot runs.
 *
 * <p>An activity's switch on the Activity Flow canvas is its default. These calls override it for the rest of
 * the run, from anywhere — one activity switching another off, or a body switching itself off after doing its
 * work once ({@code ctx.disable()} is the same call). A disabled activity takes its {@code DISABLED} wire.
 *
 * <pre>{@code
 * public static Outcome body(ActivityContext ctx) {
 *     if (bagFull()) Activities.disable("Mining");
 *     return ctx.done();
 * }
 * }</pre>
 *
 * <p>A name the flow does not have is one line on the console and nothing else, so a typo never stops a
 * running bot.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "◎", order = 35)
public final class Activities {

    private Activities() {}

    /** Whether the named activity runs right now: its canvas switch, plus any override made this run. */
    public static boolean active(String name) {
        return FlowWalker.active(name);
    }

    /** Switches the named activity on for the rest of the run. */
    public static void enable(String name) {
        FlowWalker.setEnabled(name, true);
    }

    /** Switches the named activity off for the rest of the run; the flow takes its {@code DISABLED} wire. */
    public static void disable(String name) {
        FlowWalker.setEnabled(name, false);
    }

    /** Switches the named activity on or off for the rest of the run. */
    @Hidden("the boolean picks between enable and disable, which the menu already offers by name")
    public static void setEnabled(String name, boolean enabled) {
        FlowWalker.setEnabled(name, enabled);
    }
}
