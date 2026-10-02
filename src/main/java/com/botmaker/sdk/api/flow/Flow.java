package com.botmaker.sdk.api.flow;

import com.botmaker.sdk.api.bot.Outcome;

import java.util.List;

/**
 * A bot's activity flow, as a value: what its activities are, how they are wired, where a run starts, and
 * what stops it.
 *
 * <p><b>A flow is Java, so a renamed activity method is a compile error</b> rather than a silently empty
 * value three screens into a run. It is written in the file BotMaker gave your project —
 * {@code plugins/sdk/Sdk.java} — and the editor rewrites the one expression the {@code @Managed("flow")}
 * method returns, leaving everything around it exactly as you wrote it.
 *
 * <pre>{@code
 * @Managed("flow")
 * public static Flow flow() {
 *     return Flow.of(
 *             List.of(Flow.activity(Activities.COLLECT, Collect::body, "Click collect while there is one.",
 *                             true, false, true, List.of(Outcomes.NOTHING_LEFT)),
 *                     Flow.activity(Activities.REST, Rest::body, "Wait, then go round again.",
 *                             true, false, false, List.of())),
 *             List.of(Flow.edge(Activities.COLLECT, Activities.COLLECT, Outcome.NEXT),
 *                     Flow.edge(Activities.COLLECT, Activities.REST, Outcomes.NOTHING_LEFT),
 *                     Flow.edge(Activities.REST, Activities.COLLECT, Outcome.NEXT)),
 *             List.of(),
 *             Activities.COLLECT,
 *             Flow.limits(1000, 1000));
 * }
 * }</pre>
 *
 * <h2>Every name is a constant</h2>
 *
 * <p>An activity is an {@link Activity} constant of the bot's {@code Activities} class and an outcome an
 * {@link Outcome} constant of its {@code Outcomes} class (2026-10-02). A step, a wire, a preset and the start
 * name the same constants a body returns and an {@code ActivitySwitch} call switches, so the canvas renames them
 * by binding and javac catches a typo. They used to be strings matched by spelling, and a rename on the canvas
 * left every body still reporting the old one.
 *
 * <h2>Five parts, fixed, and no varargs</h2>
 *
 * <p>Each of the five has a type the editor knows, which is what lets it take the expression apart, change
 * one activity and put it back without re-writing the rest. Varargs would make the arity a property of the
 * call rather than of the type, and a heterogeneous part list would make it unreadable — both were tried in
 * earlier designs and both are why this one is a record with fixed components.
 *
 * <h2>What is here, and what is deliberately not</h2>
 *
 * <p>Everything a <em>run</em> depends on is here, {@link Step#enabled()} included: an activity switched off is
 * not missing from the flow, it is one the run walks through by its disabled wire, so the flag is part of what
 * the bot does and belongs in the bot's own source.
 *
 * <p>{@link Preset}s are here too although nothing at runtime reads one. A preset is a named set of enable
 * flags, so it is about the same fact this record already carries; keeping it beside them is what stops a
 * saved selection from being lost the moment a project is cloned.
 *
 * <p><b>Card positions are not here.</b> They change without the bot changing, so they are a value of their
 * own, the {@link FlowLayout} {@code Sdk.flowLayout()} returns, which nothing at runtime reads.
 *
 * @param steps   every activity in the flow with its work and its switches, in the order the editor lists them
 * @param edges   where each outcome leads; an edge naming an activity that is not here is ignored
 * @param presets named selections of enable flags, saved by whoever drew the flow
 * @param start   the activity a run begins at; {@link Activity#NONE} for a flow that runs nothing
 * @param limits  what stops a run that never finishes
 */
public record Flow(List<Step> steps, List<Edge> edges, List<Preset> presets, Activity start, Limits limits) {

    /** The empty flow: nothing to run, which is what a project with nothing drawn should do. */
    public static final Flow NONE = new Flow(List.of(), List.of(), List.of(), Activity.NONE, Limits.DEFAULT);

    public Flow {
        steps = steps == null ? List.of() : List.copyOf(steps);
        edges = edges == null ? List.of() : List.copyOf(edges);
        presets = presets == null ? List.of() : List.copyOf(presets);
        start = start == null ? Activity.NONE : start;
        limits = limits == null ? Limits.DEFAULT : limits;
    }

    /** The flow, spelled the way the editor writes it. */
    public static Flow of(List<Step> steps, List<Edge> edges, List<Preset> presets, Activity start,
                          Limits limits) {
        return new Flow(steps, edges, presets, start, limits);
    }

    /**
     * One activity's step.
     *
     * @param activity    which activity this is — an {@code Activities} constant
     * @param body        the work, as a method reference — {@code Collect::body}
     * @param description one line, shown on the canvas and nowhere else
     * @param enabled     whether it runs; a disabled activity takes its disabled wire rather than its work
     * @param goHome      whether the bot's "get back to a known screen" step runs before this activity
     * @param popupCheck  whether the popup guard runs while it does
     * @param outcomes    the outcomes this activity can report — {@code Outcomes} constants — which is what the
     *                    editor offers as wires
     */
    public static Step activity(Activity activity, ActivityBody body, String description, boolean enabled,
                                boolean goHome, boolean popupCheck, List<Outcome> outcomes) {
        return new Step(activity, body, description, enabled, goHome, popupCheck, outcomes);
    }

    /** One named selection of enable flags: the activities it lists are on, every other one is off. */
    public static Preset preset(String name, List<Activity> activities) {
        return new Preset(name, activities);
    }

    /** One wire: leaving {@code from} on {@code outcome}, arriving at {@code to}. */
    public static Edge edge(Activity from, Activity to, Outcome outcome) {
        return new Edge(from, to, outcome);
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
     * <p>A record and not a class, for the reason every value here is one: it is written into a user's file
     * as a call and read back out of it, and a value with identity would have nothing for the second half to
     * reconstruct. Called a step rather than an activity because {@link Activity} is the name, and this is
     * what the flow does under it.
     */
    public record Step(Activity activity, ActivityBody body, String description, boolean enabled,
                       boolean goHome, boolean popupCheck, List<Outcome> outcomes) {

        public Step {
            activity = activity == null ? Activity.NONE : activity;
            description = description == null ? "" : description;
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }

        /** The activity's label, which is what the canvas and the trace show. */
        public String label() {
            return activity.label();
        }
    }

    /**
     * A named selection of which activities are switched on.
     *
     * <p>Applying one sets {@link Step#enabled()} from this list and touches nothing else: the wiring, the
     * outcomes and the limits are the same flow either way. It is a way of saying "just the gathering ones
     * tonight" without redrawing anything.
     *
     * @param name       what the editor's preset menu calls it
     * @param activities the activities this preset switches on; every other one is switched off
     */
    public record Preset(String name, List<Activity> activities) {

        public Preset {
            name = name == null ? "" : name;
            activities = activities == null ? List.of() : List.copyOf(activities);
        }

        /** Whether {@code activity} is one this preset switches on. */
        public boolean enables(Activity activity) {
            return activities.contains(activity);
        }
    }

    /**
     * One wire between two activities. {@code outcome} is {@link Outcome#NEXT} for the wire an activity takes
     * when it reports nothing in particular, and {@link Outcome#DISABLED} for the one it takes switched off.
     */
    public record Edge(Activity from, Activity to, Outcome outcome) {

        public Edge {
            from = from == null ? Activity.NONE : from;
            to = to == null ? Activity.NONE : to;
            outcome = outcome == null ? Outcome.NEXT : outcome;
        }

        /** Whether this is the "switched off, go here instead" edge. */
        public boolean isDisabled() {
            return Outcome.DISABLED.equals(outcome);
        }
    }

    /**
     * What stops a run that never finishes: how many activities may run, and how long to wait between them.
     *
     * <p>Both were fields of the flow file and both are here rather than on the bot, because they are
     * properties of <em>this</em> flow: a bot with a tight loop and a bot with a slow one want different
     * numbers, and neither wants to recompile to change them.
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
