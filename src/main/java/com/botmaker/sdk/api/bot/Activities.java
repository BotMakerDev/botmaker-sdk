package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Flows;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.internal.flow.FlowWalker;

/**
 * The flow's activities while a bot runs: what an activity body reports, and turning activities on and off.
 *
 * <pre>{@code
 * public static Outcome body() {
 *     if (bagFull()) return Activities.outcome("BAG_FULL");
 *     mineOnce();
 *     return Activities.next();
 * }
 * }</pre>
 *
 * <p><b>Which activity</b> is the flow's to know: it links {@code Collect::body} to the card "Collect" and
 * calls that body, so an outcome is checked against the activity running on this thread. In the editor, the
 * outcome picker lists the outcomes of the activity whose body the call sits in.
 *
 * <p>An activity's switch on the Activity Flow canvas is its default. {@link #enable}/{@link #disable}
 * override it for the rest of the run, from anywhere — one activity switching another off, or a body
 * switching itself off after doing its work once. A disabled activity takes its {@code DISABLED} wire.
 *
 * <p>A name the flow does not have is one line on the console and nothing else, so a typo never stops a
 * running bot.
 */
@Palette(category = "bot", categoryLabel = "Bot", icon = "◎")
public final class Activities {

    private Activities() {}

    /**
     * The outcome to report — one of the outcomes the running activity declares in Project ▸ Activity Flow.
     *
     * <p>A name the activity does not declare is not refused: it becomes an outcome nothing is wired to, which
     * ends the run, and one line says so on the console. That is the same answer as an outcome the user
     * declared and never wired, and it is deliberately the same: a bot must not fail to start or die
     * mid-flow over a name. Called outside a running activity (a JUnit test of a body) it checks nothing.
     */
    public static Outcome outcome(@OutcomeName String name) {
        String activity = FlowWalker.current();
        Flow.Activity declared = activity == null ? null : Flows.installed().activity(activity);
        if (name != null && !name.isBlank() && !Outcome.NEXT.equals(name)
                && declared != null && !declared.outcomes().contains(name)) {
            Debug.error(activity + " reported '" + name + "', which it does not declare in "
                    + "the Activity Flow — nothing is wired to it, so the run ends here.");
        }
        return Outcome.of(name);
    }

    /**
     * Nothing special to report — follow the activity's plain output wire.
     *
     * <p>The outcome every activity has without declaring one, so a flow drawn without ever thinking about
     * outcomes behaves like a plain linear one.
     */
    public static Outcome next() {
        return Outcome.of(Outcome.NEXT);
    }

    /** Whether the named activity runs right now: its canvas switch, plus any override made this run. */
    public static boolean active(@ActivityName String name) {
        return FlowWalker.active(name);
    }

    /** Switches the named activity on for the rest of the run. */
    public static void enable(@ActivityName String name) {
        FlowWalker.setEnabled(name, true);
    }

    /** Switches the named activity off for the rest of the run; the flow takes its {@code DISABLED} wire. */
    public static void disable(@ActivityName String name) {
        FlowWalker.setEnabled(name, false);
    }

    /** Switches the named activity on or off for the rest of the run. */
    @Hidden("the boolean picks between enable and disable, which the menu already offers by name")
    public static void setEnabled(@ActivityName String name, boolean enabled) {
        FlowWalker.setEnabled(name, enabled);
    }
}
