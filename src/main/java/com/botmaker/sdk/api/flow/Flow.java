package com.botmaker.sdk.api.flow;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.bot.ActivityBody;

import java.util.List;

/**
 * A bot's activity flow, as a value: what its activities are, how they are wired, where a run starts, and
 * what stops it.
 *
 * <p><b>This is where {@code activities.json} went</b> (2026-09-20). A flow lived in a JSON file the editor
 * wrote and the bot read back by name, which is the arrangement {@code docs/refactor/33-plugin-java.md}
 * exists to remove: a name renamed in Java was a silently empty value three screens into a run, while the
 * same rename here is a compile error. It is written in the file BotMaker gave your project —
 * {@code plugins/sdk/Sdk.java} — and the editor rewrites the one expression the {@code @Managed("flow")}
 * method returns, leaving everything around it exactly as you wrote it.
 *
 * <pre>{@code
 * @Managed("flow")
 * public static Flow flow() {
 *     return Flow.of(
 *             List.of(Flow.activity(Collect::body, "Collect", "Click collect while there is one.",
 *                             true, false, true, List.of("NOTHING_LEFT")),
 *                     Flow.activity(Rest::body, "Rest", "Wait, then go round again.",
 *                             true, false, false, List.of())),
 *             List.of(Flow.edge("Collect", "Collect", ""),
 *                     Flow.edge("Collect", "Rest", "NOTHING_LEFT"),
 *                     Flow.edge("Rest", "Collect", "")),
 *             List.of(),
 *             "Collect",
 *             Flow.limits(1000, 1000));
 * }
 * }</pre>
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
 * <p>Everything a <em>run</em> depends on is here, {@link Activity#enabled()} included: an activity switched
 * off is not missing from the flow, it is one the run walks through by its disabled wire, so the flag is
 * part of what the bot does and belongs in the bot's own source.
 *
 * <p>{@link Preset}s are here too although nothing at runtime reads one. A preset is a named set of enable
 * flags, so it is about the same fact this record already carries; keeping it beside them is what stops a
 * saved selection from being lost the moment a project is cloned.
 *
 * <p><b>Card positions are not here.</b> They are the one thing that changes without the bot changing, and a
 * node dragged two pixels must not show up in {@code git diff}. The editor keeps them in a gitignored
 * sidecar beside the plugin's other project files and lays the flow out itself when there is none.
 *
 * <p>Nesting is unbounded and deliberately so: a list of activities each holding a list of outcomes is three
 * levels, which is ordinary, and nothing in the editor caps what it may read out of your file.
 *
 * <h2>An activity is named twice, on purpose</h2>
 *
 * <p>{@link Activity#body()} is a method reference javac resolves, and {@link Activity#name()} is the label
 * the canvas draws and the edges route on. They are separate so that renaming the class does not rename the
 * activity on the canvas, and renaming it on the canvas does not touch your code. The one that must not
 * break silently — the link to the work — is the compiled one.
 *
 * @param activities every activity in the flow, in the order the editor lists them
 * @param edges      where each outcome leads; an edge naming an activity that is not here is ignored
 * @param presets    named selections of enable flags, saved by whoever drew the flow
 * @param start      the activity a run begins at; {@code ""} for a flow that runs nothing
 * @param limits     what stops a run that never finishes
 */
@Palette(category = "flow", categoryLabel = "Flow", order = 100)
@Hidden("a value type: the flow editor writes one into your project, it is not built from a menu")
public record Flow(List<Activity> activities, List<Edge> edges, List<Preset> presets, String start,
                   Limits limits) {

    /** The empty flow: nothing to run, which is what a project with nothing drawn should do. */
    public static final Flow NONE = new Flow(List.of(), List.of(), List.of(), "", Limits.DEFAULT);

    public Flow {
        activities = activities == null ? List.of() : List.copyOf(activities);
        edges = edges == null ? List.of() : List.copyOf(edges);
        presets = presets == null ? List.of() : List.copyOf(presets);
        start = start == null ? "" : start;
        limits = limits == null ? Limits.DEFAULT : limits;
    }

    /** The flow, spelled the way the editor writes it. */
    public static Flow of(List<Activity> activities, List<Edge> edges, List<Preset> presets, String start,
                          Limits limits) {
        return new Flow(activities, edges, presets, start, limits);
    }

    /**
     * One activity.
     *
     * @param body        the work, as a method reference — {@code Collect::body}
     * @param name        what the canvas calls it, and what an {@link Edge} routes to and from
     * @param description one line, shown on the canvas and nowhere else
     * @param enabled     whether it runs; a disabled activity takes its disabled wire rather than its work
     * @param goHome      whether the bot's "get back to a known screen" step runs before this activity
     * @param popupCheck  whether the popup guard runs while it does
     * @param outcomes    the outcomes this activity can report, which is what the editor offers as edges
     */
    public static Activity activity(ActivityBody body, String name, String description, boolean enabled,
                                    boolean goHome, boolean popupCheck, List<String> outcomes) {
        return new Activity(body, name, description, enabled, goHome, popupCheck, outcomes);
    }

    /** One named selection of enable flags: the activities it lists are on, every other one is off. */
    public static Preset preset(String name, List<String> activities) {
        return new Preset(name, activities);
    }

    /** One wire: leaving {@code from} on {@code outcome}, arriving at {@code to}. */
    public static Edge edge(String from, String to, String outcome) {
        return new Edge(from, to, outcome);
    }

    /** What stops a run that never finishes. */
    public static Limits limits(int maxSteps, int stepDelayMs) {
        return new Limits(maxSteps, stepDelayMs);
    }

    /** The activity called {@code name}, or {@code null}. */
    public Activity activity(String name) {
        for (Activity activity : activities) {
            if (activity.name().equals(name)) return activity;
        }
        return null;
    }

    /**
     * One activity of a flow: the work, and everything the canvas says about it.
     *
     * <p>A record and not a class, for the reason every value here is one: it is written into a user's file
     * as a call and read back out of it, and a value with identity would have nothing for the second half to
     * reconstruct.
     */
    public record Activity(ActivityBody body, String name, String description, boolean enabled,
                           boolean goHome, boolean popupCheck, List<String> outcomes) {

        public Activity {
            name = name == null ? "" : name;
            description = description == null ? "" : description;
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }
    }

    /**
     * A named selection of which activities are switched on.
     *
     * <p>Applying one sets {@link Activity#enabled()} from this list and touches nothing else: the wiring,
     * the outcomes and the limits are the same flow either way. It is a way of saying "just the gathering
     * ones tonight" without redrawing anything.
     *
     * @param name       what the editor's preset menu calls it
     * @param activities the activities this preset switches on; every other one is switched off
     */
    public record Preset(String name, List<String> activities) {

        public Preset {
            name = name == null ? "" : name;
            activities = activities == null ? List.of() : List.copyOf(activities);
        }

        /** Whether {@code activity} is one this preset switches on. */
        public boolean enables(String activity) {
            return activities.contains(activity);
        }
    }

    /**
     * One wire between two activities.
     *
     * <p>{@code outcome} is {@code ""} for the wire an activity takes when it reports nothing in particular
     * — {@code ctx.done()} — which is the common case and reads better blank than as a word the canvas
     * would then have to hide.
     */
    public record Edge(String from, String to, String outcome) {

        /**
         * The outcome every activity has without declaring it: "nothing special to report, carry on". An edge
         * stores it <b>blank</b>, never as this word, so renaming the word would cost no project anything.
         */
        public static final String NEXT = "NEXT";

        /**
         * The other outcome every activity has without declaring it: "this activity is switched off, go here
         * instead". An activity can never <em>report</em> it — it did not run — so the walk reads it as the
         * node's one disabled slot rather than as a route. An unwired {@code DISABLED} ends the run.
         */
        public static final String DISABLED = "DISABLED";

        public Edge {
            from = from == null ? "" : from;
            to = to == null ? "" : to;
            outcome = outcome == null ? "" : outcome;
        }

        /** The outcome this edge routes, with blank read as {@link #NEXT}. */
        public String outcomeOrNext() {
            return outcome.isBlank() ? NEXT : outcome;
        }

        /** Whether this is the "switched off, go here instead" edge. */
        public boolean isDisabled() {
            return DISABLED.equals(outcome);
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
