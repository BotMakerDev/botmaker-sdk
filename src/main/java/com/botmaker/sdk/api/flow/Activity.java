package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.api.bot.Outcome;

/**
 * One activity of the bot's flow, as the flow holds it: the name of one of the enums nested in your bot's
 * {@code Activities} class (2026-10-10).
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.ACTIVITIES)
 * public final class Activities {
 *     public enum Collect implements Outcome { NEXT, NOTHING_LEFT }
 *     public enum Battle implements Outcome { NEXT, WON, LOST }
 * }
 * }</pre>
 *
 * <p><b>An activity is its outcome enum.</b> Its constants are its exits, and its body returns one of them, so
 * javac refuses a body reporting another activity's outcome. A bot names an activity by the enum's class
 * literal — {@code Flow.activity(Activities.Collect.class, Collect::body)}, {@code ActivitySwitch.disable(
 * Activities.Collect.class)} — and never builds one of these: the flow does, from that literal or from the
 * enum of an outcome ({@link Outcome#activity()}).
 *
 * <p><b>The name is the enum's simple name</b>, {@code "Collect"}, so two activities are the same when their
 * enums are named the same; the enums all live in one class, where javac keeps the names apart. What the canvas
 * draws on the card and the run's trace prints is the name as words ({@link Labelled#label()}): {@code
 * DailyReward} is "Daily reward".
 *
 * @param name the enum's simple name; blank for {@link #NONE}
 */
public record Activity(String name) implements Labelled {

    /** No activity: the start of a flow with nothing in it, and a step with no off-wire. Labelled blank. */
    public static final Activity NONE = new Activity("");

    public Activity {
        name = name == null ? "" : name;
    }

    /** The activity {@code activity} is: the enum's simple name. {@link #NONE} for {@code null}. */
    public static Activity of(Class<? extends Outcome> activity) {
        return activity == null ? NONE : new Activity(activity.getSimpleName());
    }

    /** Whether this is {@link #NONE}. */
    public boolean isNone() {
        return name.isEmpty();
    }

    @Override
    public String toString() {
        return name;
    }
}
