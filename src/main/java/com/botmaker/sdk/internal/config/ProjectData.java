package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.authoring.FlowEdgeModel;
import com.botmaker.sdk.authoring.FlowModel;
import com.botmaker.plugin.basics.store.ProjectValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A running bot's own {@code activities.json}: the <b>flow</b>, plus a view onto the untyped store.
 *
 * <p><b>The store itself is {@link ProjectValues}, in {@code botmaker-plugin-toolkit}.</b> What
 * moved is the untyped key lookup — given a name, what text is stored — which has no schema in it and has to
 * be answerable by every plugin rather than by the one that owns the file. What stayed is everything below
 * the {@code flow} heading: what a node, an edge, a start and a step delay <em>mean</em> is
 * {@code com.botmaker.sdk.authoring}'s business, and reading it through a second set of rules anywhere else
 * is how the editor and a running bot would come to disagree about which activity runs first.
 *
 * <p>The two halves are told apart by one question: <i>does answering it require knowing what the file
 * says?</i> {@code one("wait")} does not. {@code start()} does — it falls back to the first placed activity
 * when the stored start names one that was deleted, which is {@code FlowModel.resolvedStart}'s rule and
 * nobody else's.
 *
 * <p>Everything else about the old class survives unchanged and is documented on {@link ProjectValues}: a
 * missing file, a missing name and a value that will not parse are ordinary states with an answer, and
 * {@link #current()} parses once for the life of the process.
 */
public final class ProjectData {

    /** Where a project's model lives on a bot's classpath — {@code src/main/resources/activities.json}. */
    public static final String RESOURCE = ProjectValues.RESOURCE;

    private final ProjectValues values;

    private ProjectData(ProjectValues values) {
        this.values = values;
    }

    // ---- loading ----------------------------------------------------------------------------------------

    /** This bot's own model, parsed once. Never {@code null}, and empty when there is nothing to read. */
    public static ProjectData current() {
        return new ProjectData(ProjectValues.current());
    }

    /** Test seam: forget the cached model so the next {@link #current()} reads again. */
    static void forget() {
        use(null);
    }

    /**
     * Test seam: make {@code data} the model {@link #current()} answers, or {@code null} to forget it and
     * read the classpath again.
     *
     * <p>Public because the things that read {@code current()} are spread across packages — {@code Wire}, an
     * {@code ActivityContext} checking an outcome name, a defined activity asking whether it is switched on
     * — and each of those wants to be tested against a model written in the test rather than against a
     * resource file per case. It sets {@link ProjectValues}' own seam, so a test that stubs the model here
     * also stubs what {@code Settings} reads.
     */
    public static void use(ProjectData data) {
        ProjectValues.use(data == null ? null : data.values);
    }

    /** The model at {@code resource} on the classpath, or an empty one. */
    public static ProjectData load(String resource) {
        return new ProjectData(ProjectValues.load(resource));
    }

    /** The model in {@code json}, or an empty one — the seam a test and the flow loader read through. */
    public static ProjectData of(String json) {
        return new ProjectData(ProjectValues.of(json));
    }

    /** A model with nothing in it — every lookup below answers its own fallback. */
    public static ProjectData empty() {
        return new ProjectData(ProjectValues.empty());
    }

    /** The untyped store behind this model, for a caller that wants it directly. */
    public ProjectValues values() {
        return values;
    }

    // ---- the untyped store, delegated -------------------------------------------------------------------

    /** Whether the named activity is switched on, defaulting to {@code false}. */
    public boolean enabled(String activity) {
        return values.enabled(activity);
    }

    /** The outcomes the named activity declares, without the implicit one. Empty when it has none. */
    public List<String> outcomes(String activity) {
        return values.outcomes(activity);
    }

    /** Every activity's name, in the order the file lists them. */
    public List<String> activities() {
        return values.activities();
    }

    /** Whether the named activity goes home before running. */
    public boolean goHome(String activity) {
        return values.goHome(activity);
    }

    /** Whether the named activity checks for popups before running. */
    public boolean popupCheck(String activity) {
        return values.popupCheck(activity);
    }

    /** The stored text of the named variable's first value, or {@code ""}. */
    public String value(String variable) {
        return values.one(variable);
    }

    /** Every stored value of the named variable — one for a plain value, several for a list. */
    public List<String> values(String variable) {
        return values.many(variable);
    }

    /** Whether the file declares a variable by this name at all — the question {@code ""} cannot answer. */
    public boolean declares(String variable) {
        return values.declares(variable);
    }

    /** Every variable's name, in the order the file lists them. */
    public List<String> variables() {
        return values.variables();
    }

    /** Whether this model holds nothing at all. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    // ---- flow: the half that knows what the file means --------------------------------------------------

    /** The drawn flow, as the file holds it — read by the flow loader, which owns what it means. */
    public JsonNode flow() {
        return values.section("flow");
    }

    /**
     * The activities placed on the canvas, in canvas order.
     *
     * <p>Only these are nodes of the graph. An activity declared but never placed is still constructed — it
     * can be enabled by name from another activity's body — it simply has nowhere to be reached from.
     */
    public List<String> placed() {
        List<String> out = new java.util.ArrayList<>();
        for (JsonNode node : flow().path("nodes")) {
            String activity = node.path("activity").asText("");
            // A node naming no activity is the legacy stop card; it is dropped here exactly as the editor's
            // own walk drops it, rather than becoming a node with nothing to run.
            if (!activity.isEmpty() && !out.contains(activity)) out.add(activity);
        }
        return List.copyOf(out);
    }

    /**
     * The node a run begins at, resolved against what is placed: the stored start when it names a placed
     * activity, else the first placed one, else {@code ""}.
     *
     * <p>The fallback is what lets a flow whose start activity was deleted or renamed still run —
     * {@code FlowModel.resolvedStart} answers the same question for the editor and this is the same rule.
     */
    public String start() {
        List<String> placed = placed();
        String stored = flow().path("start").asText("");
        if (placed.contains(stored)) return stored;
        return placed.isEmpty() ? "" : placed.get(0);
    }

    /** The budget of hand-offs one run may make; the model's default when unset or nonsensical. */
    public int maxSteps() {
        int stored = flow().path("maxSteps").asInt(0);
        return stored <= 0 ? FlowModel.DEFAULT_MAX_STEPS : stored;
    }

    /**
     * The pause between two activities, in milliseconds.
     *
     * <p>Absent and {@code 0} are different answers and must stay so: a flow written before the field existed
     * has no key and wants the default, while an explicit {@code 0} is a user asking for no pause. That is
     * the same distinction {@code FlowModel}'s boxed JSON creator exists to preserve.
     */
    public int stepDelayMs() {
        JsonNode stored = flow().path("stepDelayMs");
        if (!stored.isNumber()) return FlowModel.DEFAULT_STEP_DELAY_MS;
        return Math.max(0, stored.asInt());
    }

    /**
     * Where each of one activity's outcomes leads, keyed by outcome name.
     *
     * <p>Includes {@link FlowEdgeModel#DISABLED_OUTCOME}, which is not an outcome an activity can report and
     * is read out separately by the loader. A blank stored outcome is {@link FlowEdgeModel#NEXT_OUTCOME} —
     * blank-means-implicit is how that constant survived being renamed once already, and reading it any other
     * way here would undo that.
     */
    public Map<String, String> routes(String from) {
        if (from == null) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode edge : flow().path("edges")) {
            if (!from.equals(edge.path("from").asText(null))) continue;
            String to = edge.path("to").asText("");
            if (to.isEmpty()) continue;
            String outcome = edge.path("outcome").asText("");
            // First wire wins, matching the editor's rule that (from, outcome) is unique: a second one is a
            // file somebody hand-edited, and silently preferring the later would move the flow.
            out.putIfAbsent(outcome.isBlank() ? FlowEdgeModel.NEXT_OUTCOME : outcome, to);
        }
        return Map.copyOf(out);
    }
}
