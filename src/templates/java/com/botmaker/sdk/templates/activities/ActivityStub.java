package com.botmaker.sdk.templates.activities;

import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.sdk.templates.Activities;
import com.botmaker.sdk.templates.meta.Template;

/**
 * Activity: ActivityStub. Fill in {@link #run()} with how to do it — that method is the whole point of this
 * file, and this file is yours to edit (BotMaker Studio creates it once and never overwrites it).
 * {@link #isEnabled()} is wired to this activity's enable flag on {@code Activities} and is managed for you;
 * the bot's variables are on {@code Activities}, the ones for this activity tagged after its name.
 *
 * <p>SEED — Studio writes it once, when the activity is created, and never again. The one exception is
 * {@link Outcome}, which Project &rarr; Activity Flow keeps in step with what the canvas can route on.
 */
@Template(id = "ACTIVITY_STUB", kind = Template.Kind.SEED, target = "activities/${ACTIVITY}.java",
        holes = {"OUTCOMES:1", "ENABLED:1"})
public class ActivityStub extends Activity<ActivityStub.Outcome> {

    /**
     * What this activity can report having happened. Return one from {@link #run()} and the flow drawn in
     * the Studio decides where each one goes — so this says what happened here, never where to go next.
     * GENERATED from Project &rarr; Activity Flow; edit it there, not here.
     */
    public enum Outcome { /*<STUDIO:OUTCOMES:1>*/ NEXT /*</STUDIO:OUTCOMES:1>*/ }

    @Override
    public boolean isEnabled() {
        return /*<STUDIO:ENABLED:1>*/ Activities.EXAMPLE /*</STUDIO:ENABLED:1>*/;
    }

    @Override
    public Outcome run() {
        // TODO: how to do ActivityStub
        return Outcome.NEXT;
    }
}
