package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.flow.Flows;

/**
 * What an activity's body is handed — the activity itself, from the inside.
 *
 * <p>It exists for one reason above the others: {@link #outcome(String)} is a call on a <em>receiver whose
 * type is known</em>, which is what lets BotMaker Studio draw a dropdown of this activity's own outcomes
 * where the name is typed. A body returning a bare {@code String} would look, to the editor, exactly like a
 * body returning any other string. Having built it for that, it is also the natural home for the three
 * things a body used to reach through {@code this} on a generated subclass — its own name, and turning
 * itself on or off.
 *
 * <pre>{@code
 * Activities.define("Mining", ctx -> {
 *     if (bagFull()) return ctx.outcome("BAG_FULL");
 *     mineOnce();
 *     return ctx.done();
 * });
 * }</pre>
 */
@Palette(category = "bot", categoryLabel = "Bot", order = 37)
@Hidden("handed to an activity body; a bot never builds one")
public final class ActivityContext {

    private final String activity;

    /**
     * A context for the activity called {@code activity}.
     *
     * <p><b>Public since 2026-09-21</b>, and the reason is worth stating because it reverses the
     * {@code @Hidden} note above. It was package-private while the only way to write a body was a lambda
     * passed to {@code Activities.define}, which nothing outside this package could call with a context of
     * its own. A body is now a {@code public static Outcome body(ActivityContext ctx)} in the user's own
     * file, and the flow builds one of these to call it with — so a constructor is needed outside this
     * package either way, and once it exists, refusing it to the user would be refusing them the one thing
     * the method-reference design buys: a body is an ordinary static method a plain JUnit test can call.
     *
     * <p>It is still not something a bot builds while it is <em>running</em>; the flow hands one in.
     */
    public ActivityContext(String activity) {
        this.activity = activity;
    }

    /** The name this activity is wired under on the canvas. */
    public String name() {
        return activity;
    }

    /**
     * The outcome to report — one of the outcomes this activity declares in Project ▸ Activity Flow.
     *
     * <p>A name the canvas does not declare is not refused: it becomes an outcome nothing is wired to, which
     * ends the run, and one line says so on the console. That is the same answer as an outcome the user
     * declared and never wired, and it is deliberately the same: a bot must not fail to start or die
     * mid-flow over a name.
     */
    public Outcome outcome(String name) {
        Flow.Activity declared = Flows.installed().activity(activity);
        if (name != null && !name.isBlank() && !Outcome.NEXT.equals(name)
                && declared != null && !declared.outcomes().contains(name)) {
            Debug.error("[Activity] " + activity + " reported '" + name + "', which it does not declare in "
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
    public Outcome done() {
        return Outcome.of(Outcome.NEXT);
    }

    /**
     * Switches this activity off for the rest of the run — the "do this once, then stop" pattern. It
     * outranks the value set in the editor, and the flow takes its {@code DISABLED} wire from the next pass
     * on.
     */
    public void disable() {
        Activity.setEnabled(activity, false);
    }

    /** Switches this activity back on for the rest of the run. */
    public void enable() {
        Activity.setEnabled(activity, true);
    }
}
