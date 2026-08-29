package com.botmaker.sdk.internal.plugin.seeds;

import com.botmaker.plugin.api.scaffold.ClassName;
import com.botmaker.plugin.api.scaffold.Editable;
import com.botmaker.plugin.api.scaffold.EnumValues;
import com.botmaker.plugin.api.scaffold.Scaffold;
import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.sdk.api.config.Wire;

/**
 * One thing the bot can do. Fill in {@link #run()} with how to do it — that method is the whole point of this
 * file, and this file is yours to edit (BotMaker creates it once and never overwrites it).
 * {@link #isEnabled()} is wired to this activity's tick in the editor and is managed for you.
 *
 * <p>SEED — written once, when the activity is created, and never again. The one exception is
 * {@link Outcome}, which Project &rarr; Activity Flow keeps in step with what the canvas can route on.
 */
@Scaffold(path = "src/main/java/{package}/activities/{name}.java",
        description = "One activity: what the bot does, and what it can report having happened.")
@ClassName
public class ActivityTemplate extends Activity<ActivityTemplate.Outcome> {

    /**
     * What this activity can report having happened. Return one from {@link #run()} and the flow drawn in the
     * editor decides where each one goes — so this says what happened here, never where to go next. GENERATED
     * from Project &rarr; Activity Flow; edit it there, not here.
     *
     * <p>{@code NEXT} alone is what a brand-new activity starts with, and it is a working default rather than
     * a placeholder: this class compiles as written, which is the whole argument for a seed being real source
     * instead of a template.
     */
    @EnumValues("outcomes")
    public enum Outcome { NEXT }

    @Override
    public boolean isEnabled() {
        return Wire.enabled(name());
    }

    @Editable("What this activity actually does.")
    @Override
    public Outcome run() {
        // TODO: how to do this activity.
        return Outcome.NEXT;
    }
}
