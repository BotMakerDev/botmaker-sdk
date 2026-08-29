package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.api.bot.Activity;
import com.botmaker.sdk.api.bot.Outcome;

/**
 * An {@link Activity} subclass, seen as the flow sees every activity.
 *
 * <p>One adapter, in one place, because there are two callers and they must not answer differently: an
 * {@code Activity}'s own constructor, which registers it, and the deprecated {@code FlowGraph.node}, which
 * takes one directly for a hand-built graph.
 *
 * <p>The outcome crosses by <b>name</b>, which loses nothing. The walk has only ever asked an outcome for
 * its {@linkplain Enum#name() name}; the type parameter did its real work at the {@code FlowGraph.route}
 * call site, where it made a route checkable against the activity it belongs to.
 */
public final class LegacyActivity implements ActivityRegistry.Runner {

    private final Activity<?> activity;

    private LegacyActivity(Activity<?> activity) {
        this.activity = activity;
    }

    /** {@code activity} as a runner, or {@code null} for a null activity. */
    public static ActivityRegistry.Runner of(Activity<?> activity) {
        return activity == null ? null : new LegacyActivity(activity);
    }

    @Override
    public String name() {
        return activity.name();
    }

    @Override
    public boolean active() {
        return activity.active();
    }

    @Override
    public void setEnabled(boolean enabled) {
        activity.setEnabled(enabled);
    }

    @Override
    public Outcome execute() {
        Enum<?> outcome = activity.execute();
        return Outcome.of(outcome == null ? null : outcome.name());
    }

    /** The instance behind it — what {@code FlowGraph.Node.activity()} still answers. */
    public Activity<?> activity() {
        return activity;
    }
}
