package com.botmaker.sdk.api.authoring;

import com.botmaker.sdk.api.meta.Since;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * The wiring the generated {@code FlowDriver} is emitted from: which activities are placed, how they lead to
 * one another, where a run starts, and the two limits that stop it running away.
 *
 * @param nodes       the placed activities and their canvas positions
 * @param edges       the wires between them
 * @param start       the activity a run begins at; blank ⇒ the first placed one
 * @param maxSteps    the budget of node transitions per run; {@code <= 0} ⇒ {@link #DEFAULT_MAX_STEPS}
 * @param stepDelayMs the pause between two activities; {@code < 0} ⇒ the default, {@code 0} ⇒ no pause
 */
@Since("1.2.0")
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowModel(List<FlowNodeModel> nodes, List<FlowEdgeModel> edges, String start, int maxSteps,
                        int stepDelayMs) {

    /**
     * The default budget of node transitions per run. A branching flow may legitimately cycle forever, so the
     * generated driver counts steps and stops when this is exceeded — the difference between "this bot farms
     * all night" and "this bot is spinning between two activities and will never stop".
     */
    public static final int DEFAULT_MAX_STEPS = 1000;

    /**
     * The default pause between two activities, in milliseconds. A flow may loop, so an activity that
     * finishes in milliseconds can hand straight back to itself and leave the user with no gap in which to
     * hit Stop — the bot holds the mouse and the UI never gets a turn. A second between activities is that
     * gap. It is deliberately a floor on the <em>flow</em> and not on anything inside an activity.
     */
    public static final int DEFAULT_STEP_DELAY_MS = 1000;

    public FlowModel {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        if (start == null) start = "";
        if (maxSteps <= 0) maxSteps = DEFAULT_MAX_STEPS;
        // Only a negative value means "unset". 0 is a legitimate "no pause" the user can ask for, so unlike
        // maxSteps it must survive the constructor — see the JSON creator below for how absent stays distinct.
        if (stepDelayMs < 0) stepDelayMs = DEFAULT_STEP_DELAY_MS;
    }

    /** Nothing wired yet. */
    public static FlowModel empty() {
        return new FlowModel(List.of(), List.of(), "", DEFAULT_MAX_STEPS, DEFAULT_STEP_DELAY_MS);
    }

    /**
     * Deserialises the {@code flow} object. {@code stepDelayMs} is boxed for one reason: a file written
     * before the field existed has no such key, and Jackson would bind a missing {@code int} to 0 — silently
     * turning every pre-existing flow into a zero-delay one, which is exactly the runaway the field exists to
     * prevent. Absent means "take the default"; an explicit {@code 0} means the user asked for no pause.
     */
    @JsonCreator
    static FlowModel fromWire(@JsonProperty("nodes") List<FlowNodeModel> nodes,
                              @JsonProperty("edges") List<FlowEdgeModel> edges,
                              @JsonProperty("start") String start,
                              @JsonProperty("maxSteps") int maxSteps,
                              @JsonProperty("stepDelayMs") Integer stepDelayMs) {
        return new FlowModel(nodes, edges, start, maxSteps,
                stepDelayMs == null ? DEFAULT_STEP_DELAY_MS : stepDelayMs);
    }

    /** True when nothing has been wired — a caller should fall back to plain declaration order. */
    @JsonIgnore
    public boolean isEmpty() {
        return edges.isEmpty();
    }

    /** The saved placement for {@code activity}, if it is on the canvas. */
    public Optional<FlowNodeModel> node(String activity) {
        return nodes.stream().filter(n -> n.activity().equals(activity)).findFirst();
    }

    public FlowModel withStart(String newStart) {
        return new FlowModel(nodes, edges, newStart, maxSteps, stepDelayMs);
    }

    public FlowModel withMaxSteps(int newMaxSteps) {
        return new FlowModel(nodes, edges, start, newMaxSteps, stepDelayMs);
    }

    public FlowModel withStepDelayMs(int newStepDelayMs) {
        return new FlowModel(nodes, edges, start, maxSteps, newStepDelayMs);
    }

    // ---- reachability -----------------------------------------------------------------------------------
    //
    // This walk lives here, and not in an editor, because two things have to agree about it and disagreeing
    // is a bot that does not compile: the canvas decides which cards to flag as orphans, and the generator
    // decides which activities to instantiate in the registry. One walk, one answer.

    /**
     * The entry point resolved against what is actually placed: {@link #start()} when it names a placed
     * activity, else the first placed one. The fallback is what lets a flow whose start activity was deleted
     * or renamed still generate something that runs.
     */
    public String resolvedStart(List<String> allActivityNames) {
        List<String> placed = placedActivities(allActivityNames);
        if (placed.contains(start)) return start;
        return placed.isEmpty() ? "" : placed.getFirst();
    }

    /**
     * The activities a run can actually reach, breadth-first from {@link #resolvedStart}. Anything left out
     * is an <em>orphan</em>: placed but unreachable, so it never runs. Wires naming something that no longer
     * exists are ignored.
     *
     * <p>Breadth-first order is <em>presentational only</em> — with branches there is no single run order,
     * and the driver decides what runs next from the outcome an activity reports. What this list decides is
     * which activities are instantiated and which are flagged as orphans.
     *
     * <p>When the flow {@link #isEmpty() has no wires}, nothing is wired yet, so this returns
     * {@code allActivityNames} unchanged — declaration order, all of them.
     */
    public List<String> reachable(List<String> allActivityNames) {
        if (isEmpty()) return List.copyOf(allActivityNames);
        return reachableFrom(placedActivities(allActivityNames), edges, resolvedStart(allActivityNames));
    }

    /**
     * Breadth-first walk of {@code edges} from {@code start} over {@code placed}. Wires naming anything
     * outside {@code placed} are ignored as stale, and revisiting is skipped — which is what makes a cyclic
     * flow terminate here rather than spin.
     */
    public static List<String> reachableFrom(List<String> placed, List<FlowEdgeModel> edges, String start) {
        java.util.Set<String> known = new java.util.HashSet<>(placed);
        if (!known.contains(start)) return List.of();

        java.util.Map<String, List<String>> successors = new java.util.LinkedHashMap<>();
        for (FlowEdgeModel e : edges) {
            if (!known.contains(e.from()) || !known.contains(e.to())) continue; // stale wire
            successors.computeIfAbsent(e.from(), k -> new java.util.ArrayList<>()).add(e.to());
        }

        java.util.Set<String> visited = new java.util.LinkedHashSet<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String node = queue.removeFirst();
            if (!visited.add(node)) continue; // already reached; this is also the cycle guard
            queue.addAll(successors.getOrDefault(node, List.of()));
        }
        return List.copyOf(visited);
    }

    /**
     * The placed activity names, in canvas order, restricted to ones that still exist. Filtering on "still
     * exists" is also what quietly drops a legacy stop node: it names no activity.
     */
    private List<String> placedActivities(List<String> allActivityNames) {
        java.util.Set<String> known = new java.util.HashSet<>(allActivityNames);
        List<String> placed = new java.util.ArrayList<>();
        for (FlowNodeModel n : nodes) {
            if (known.contains(n.activity()) && !placed.contains(n.activity())) placed.add(n.activity());
        }
        return placed;
    }
}
