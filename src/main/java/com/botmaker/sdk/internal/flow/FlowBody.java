package com.botmaker.sdk.internal.flow;

import com.botmaker.sdk.api.bot.ActivityBody;
import com.botmaker.sdk.api.bot.ActivityContext;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.sdk.internal.trace.Trace;
import com.botmaker.sdk.internal.bot.ActivityRegistry;

/**
 * One activity of the installed {@code Flow}, as something the walk can run.
 *
 * <p>It is the twin of {@code Activities.Defined} and of {@link com.botmaker.sdk.internal.bot.LegacyActivity},
 * and the third and last way an activity's work reaches {@link ActivityRegistry}. The difference is where
 * the name comes from: a defined activity registers itself under a string, a legacy one under its class's
 * own {@code name()}, and this one under the name on the card the body was written beside — with the body
 * itself being a method reference javac resolved, so there is no name in the middle at all.
 *
 * <h2>Where {@code active()} gets its answer</h2>
 *
 * <p>From the flow, which is the value the bot installed. It used to come from {@code Settings.enabled},
 * which read {@code activities.json} on every call so that a flag flipped in the editor was picked up on the
 * next run without the definition knowing anything about files. The flow is read once, at install, and is a
 * value; flipping a flag in the editor rewrites the bot's own Java, so the next run picks it up by being
 * compiled. The re-read had nothing left to re-read.
 *
 * <p>The override still outranks it, because {@code ctx.disable()} is a body switching its own activity off
 * mid-run and that has never been a question about the file.
 */
public final class FlowBody implements ActivityRegistry.Runner {

    private final String name;
    private final ActivityBody body;
    private final boolean enabledByDefault;

    /** What a running bot has since said, or {@code null} when it has said nothing. */
    private Boolean override;

    public FlowBody(String name, ActivityBody body, boolean enabledByDefault) {
        this.name = name;
        this.body = body;
        this.enabledByDefault = enabledByDefault;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean active() {
        return override != null ? override : enabledByDefault;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.override = enabled;
    }

    /**
     * Runs the body and reports what it said.
     *
     * <p>One line on the console per activity, for the reason {@code Activities.Defined} logs one: the
     * activity and its outcome are the coarsest unit of "what is the bot doing". A body that answers
     * {@code null} is {@code ctx.done()} — a method whose last statement fell through has nothing special to
     * report.
     */
    @Override
    public Outcome execute() {
        long startedAt = System.currentTimeMillis();
        Outcome outcome = body.run(new ActivityContext(name));
        if (outcome == null) outcome = Outcome.of(null);
        Debug.log("[Activity] " + name + " → " + outcome
                + " (" + Trace.elapsed(System.currentTimeMillis() - startedAt) + ")");
        return outcome;
    }
}
