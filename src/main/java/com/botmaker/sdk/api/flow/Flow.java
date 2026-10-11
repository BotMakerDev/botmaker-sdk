package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.api.bot.Outcome;

import java.util.List;

/**
 * One of a bot's activity flows, as a value: what its activities are, how they are wired, where a run starts
 * and what stops it.
 *
 * <p><b>A flow is Java, so a renamed activity method is a compile error</b> rather than a silently empty
 * value three screens into a run. A bot holds its flows as constants of its {@code Flows} class, one per way
 * of playing, and the editor rewrites one constant's expression, leaving everything around it exactly as you
 * wrote it. Which one runs when nobody picks is {@link Run}'s, in {@code Sdk.run()}, together with the game's
 * home and setup, which no flow owns.
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.FLOWS)
 * public final class Flows {
 *     public static final Flow MAIN = Flow.of(
 *             List.of(Flow.activity(Activities.Collect.class, Collect::body)
 *                             .described("Click collect while there is one.")
 *                             .checksPopups(),
 *                     Flow.activity(Activities.Rest.class, Rest::body).switchable()),
 *             List.of(Flow.edge(Activities.Collect.NEXT, Activities.Collect.class),
 *                     Flow.edge(Activities.Collect.NOTHING_LEFT, Activities.Rest.class),
 *                     Flow.edge(Activities.Rest.NEXT, Activities.Collect.class)),
 *             Activities.Collect.class,
 *             Flow.limits(1000, 1000));
 * }
 * }</pre>
 *
 * <h2>An activity is its outcome enum</h2>
 *
 * <p>Each activity is an enum nested in the bot's {@code Activities} class, and its constants are its exits
 * (2026-10-10). A step, the start and an {@code ActivitySwitch} call name the enum by its class literal; a wire
 * names the outcome it leaves on, whose enum is the activity it leaves. A body returns its activity's enum, and
 * {@link #activity} ties the two, so javac refuses a body reporting another activity's outcome. The canvas
 * renames them by binding. Two flows may run the same activity, each with its own step.
 *
 * <h2>A step names what it sets</h2>
 *
 * <p>{@link #activity} takes the two things every step has, the activity and its work, and each other part is a
 * named link after it, written only when it is not the default: {@code .described("…")}, {@code .off()},
 * {@code .goesHome()}, {@code .checksPopups()}, {@code .switchable()}, {@code .whenOff(…)}.
 *
 * <h2>Four parts, fixed, and no varargs</h2>
 *
 * <p>Each of the four has a type the editor knows, which is what lets it take the expression apart, change
 * one activity and put it back without re-writing the rest.
 *
 * <p><b>Card positions are not here.</b> They change without the bot changing, so they are a value of their
 * own, the {@link FlowLayout} {@code Sdk.flowLayout()} returns, which nothing at runtime reads.
 *
 * @param steps  every activity in the flow with its work and its switches, in the order the editor lists them
 * @param edges  where each outcome leads; an edge naming an activity that is not here is ignored
 * @param start  the activity a run begins at; {@link Activity#NONE} for a flow that runs nothing
 * @param limits what stops a run that never finishes
 */
public record Flow(List<Step> steps, List<Edge> edges, Activity start, Limits limits) {

    /** The empty flow: nothing to run, which is what a project with nothing drawn should do. */
    public static final Flow NONE = new Flow(List.of(), List.of(), Activity.NONE, Limits.DEFAULT);

    public Flow {
        steps = steps == null ? List.of() : List.copyOf(steps);
        edges = edges == null ? List.of() : List.copyOf(edges);
        start = start == null ? Activity.NONE : start;
        limits = limits == null ? Limits.DEFAULT : limits;
    }

    /** The flow, spelled the way the editor writes it. */
    public static Flow of(List<Step> steps, List<Edge> edges, Class<? extends Outcome> start, Limits limits) {
        return new Flow(steps, edges, Activity.of(start), limits);
    }

    /**
     * One activity's step, switched on, with no description, no go-home, no popup check, not switchable by the
     * runner and no off-wire; each of those is a link after it ({@link Step#described}, {@link Step#off},
     * {@link Step#goesHome}, {@link Step#checksPopups}, {@link Step#switchable}, {@link Step#whenOff}).
     *
     * @param activity which activity this is — {@code Activities.Collect.class}
     * @param body     the work, as a method reference returning that enum — {@code Collect::body}; or
     *                 {@link ActivityBody#none()} for a card not written yet
     * @param <O>      the activity's enum, which the body returns
     */
    public static <O extends Outcome> Step activity(Class<O> activity, ActivityBody<O> body) {
        return new Step(Activity.of(activity), body, "", true, false, false, false, Activity.NONE);
    }

    /** One wire: leaving {@code on}'s activity when it reports {@code on}, arriving at {@code to}. */
    public static Edge edge(Outcome on, Class<? extends Outcome> to) {
        return new Edge(on, Activity.of(to));
    }

    /** What stops a run that never finishes. */
    public static Limits limits(int maxSteps, int stepDelayMs) {
        return new Limits(maxSteps, stepDelayMs);
    }

    /** The step of {@code activity}, or {@code null}. */
    public Step step(Activity activity) {
        for (Step step : steps) {
            if (step.activity().equals(activity)) return step;
        }
        return null;
    }

    /**
     * One activity of a flow: the work, and everything the canvas says about it.
     *
     * @param activity    which activity this is
     * @param body        the work, as a method reference — {@code Collect::body}
     * @param description one line, shown on the canvas and nowhere else
     * @param enabled     whether it runs; a disabled activity takes its off-wire rather than its work
     * @param goHome      whether the bot's {@link Run#home} runs before this activity
     * @param popupCheck  whether the popup guard runs while it does
     * @param runnerSwitch whether the person running the bot may switch it off (or back on) for their machine,
     *                    without opening the project ({@link #switchable()})
     * @param whenOff     where the run goes when this activity is switched off or has no body yet;
     *                    {@link Activity#NONE} ends the run there
     */
    public record Step(Activity activity, ActivityBody<?> body, String description, boolean enabled,
                       boolean goHome, boolean popupCheck, boolean runnerSwitch, Activity whenOff) {

        public Step {
            activity = activity == null ? Activity.NONE : activity;
            description = description == null ? "" : description;
            whenOff = whenOff == null ? Activity.NONE : whenOff;
        }

        /** This step with {@code description}, the one line the canvas shows. */
        public Step described(String description) {
            return new Step(activity, body, description, enabled, goHome, popupCheck, runnerSwitch, whenOff);
        }

        /** This step switched off: the run takes its off-wire rather than its work. */
        public Step off() {
            return new Step(activity, body, description, false, goHome, popupCheck, runnerSwitch, whenOff);
        }

        /** This step with the bot's {@link Run#home} run before it. */
        public Step goesHome() {
            return new Step(activity, body, description, enabled, true, popupCheck, runnerSwitch, whenOff);
        }

        /** This step with the popup guard running while it does. */
        public Step checksPopups() {
            return new Step(activity, body, description, enabled, goHome, true, runnerSwitch, whenOff);
        }

        /** This step offered to the person running the bot, who may switch it off or back on for their machine. */
        public Step switchable() {
            return new Step(activity, body, description, enabled, goHome, popupCheck, true, whenOff);
        }

        /** This step going on to {@code to} when it is switched off or has no body yet. */
        public Step whenOff(Class<? extends Outcome> to) {
            return new Step(activity, body, description, enabled, goHome, popupCheck, runnerSwitch, Activity.of(to));
        }

        /** The activity's label, which is what the canvas and the trace show. */
        public String label() {
            return activity.label();
        }
    }

    /**
     * One wire: an activity reporting {@code on} goes on to {@code to}. The activity it leaves is
     * {@code on}'s enum ({@link #from()}).
     */
    public record Edge(Outcome on, Activity to) {

        public Edge {
            to = to == null ? Activity.NONE : to;
        }

        /** The activity this wire leaves: the enum that declares {@link #on()}. */
        public Activity from() {
            return on == null ? Activity.NONE : on.activity();
        }
    }

    /**
     * What stops a run that never finishes: how many activities may run, and how long to wait between them.
     *
     * @param maxSteps    how many activities may run before the bot gives up; {@code 0} for no limit
     * @param stepDelayMs how long to wait between activities, in milliseconds
     */
    public record Limits(int maxSteps, int stepDelayMs) {

        /** A thousand steps a second apart — the numbers a new project starts with. */
        public static final Limits DEFAULT = new Limits(1000, 1000);

        public Limits {
            maxSteps = Math.max(0, maxSteps);
            stepDelayMs = Math.max(0, stepDelayMs);
        }
    }
}
