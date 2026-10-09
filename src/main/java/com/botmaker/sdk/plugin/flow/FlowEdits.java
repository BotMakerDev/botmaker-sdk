package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.types.FlowTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Changes to a flow as values, and the one way a changed flow is saved — the operations 🔀 Activity Flow
 * performs, for a caller with no window: the assistant's activity tools.
 *
 * <p>Each change answers a new {@link Flow} or throws {@link IllegalArgumentException} with the sentence the
 * window would show; nothing is written until {@link #save}. Links no JavaFX.
 */
public final class FlowEdits {

    private FlowEdits() {}

    /**
     * {@code flow} with a new activity called {@code typed}, its body the method {@code bodySource} names
     * ({@code Collect::body}), or none yet when blank. The first activity of an empty flow is its start.
     */
    public static Flow addActivity(Flow flow, String typed, String bodySource) {
        String label = FlowNames.label(typed);
        String problem = FlowNames.activityNameProblem(label, labels(flow));
        if (problem != null) throw new IllegalArgumentException(problem);
        Flow.Step step = Flow.activity(FlowNames.activity(label), body(bodySource));
        List<Flow.Step> steps = new ArrayList<>(flow.steps());
        steps.add(step);
        Activity start = flow.start() == Activity.NONE || flow.start().label().isEmpty() ? step.activity() : flow.start();
        return Flow.of(steps, flow.edges(), flow.presets(), start, flow.limits());
    }

    /** {@code flow} with the activity {@code from} called {@code typed}, in every step, wire, preset and the start. */
    public static Flow renameActivity(Flow flow, String from, String typed) {
        Flow.Step step = step(flow, from);
        String label = FlowNames.label(typed);
        Set<String> others = labels(flow);
        others.remove(step.label());
        String problem = FlowNames.activityNameProblem(label, others);
        if (problem != null) throw new IllegalArgumentException(problem);
        Activity was = step.activity();
        Activity now = FlowNames.activity(label);
        List<Flow.Step> steps = flow.steps().stream().map(s -> s.activity().equals(was)
                ? new Flow.Step(now, s.body(), s.description(), s.enabled(), s.goHome(), s.popupCheck(), s.outcomes())
                : s).toList();
        List<Flow.Edge> edges = flow.edges().stream().map(e -> Flow.edge(swap(e.from(), was, now), swap(e.to(), was, now),
                e.outcome())).toList();
        List<Flow.Preset> presets = flow.presets().stream().map(p -> Flow.preset(p.name(),
                p.activities().stream().map(a -> swap(a, was, now)).toList())).toList();
        return Flow.of(steps, edges, presets, swap(flow.start(), was, now), flow.limits());
    }

    /** {@code flow} without the activity {@code name}, its wires, its place in presets, and the start if it was. */
    public static Flow removeActivity(Flow flow, String name) {
        Activity gone = step(flow, name).activity();
        List<Flow.Step> steps = flow.steps().stream().filter(s -> !s.activity().equals(gone)).toList();
        List<Flow.Edge> edges = flow.edges().stream()
                .filter(e -> !e.from().equals(gone) && !e.to().equals(gone)).toList();
        List<Flow.Preset> presets = flow.presets().stream().map(p -> Flow.preset(p.name(),
                p.activities().stream().filter(a -> !a.equals(gone)).toList())).toList();
        Activity start = flow.start().equals(gone) ? (steps.isEmpty() ? Activity.NONE : steps.getFirst().activity())
                : flow.start();
        return Flow.of(steps, edges, presets, start, flow.limits());
    }

    /**
     * {@code flow} with the wire from {@code from} on {@code outcome} going to {@code to}, replacing the one that
     * outcome had. An outcome {@code from} does not declare yet is added to it; blank is {@code NEXT}.
     */
    public static Flow connect(Flow flow, String from, String outcome, String to) {
        Flow.Step source = step(flow, from);
        Activity target = step(flow, to).activity();
        // An outcome is its constant: "bag full" is the declared BAG_FULL, not a second outcome beside it, and
        // "next" or "disabled" is the port every activity has, never one it declares.
        String typed = outcome == null ? "" : outcome.trim();
        if (!typed.isEmpty() && FlowNames.constantFor(typed) == null) {
            throw new IllegalArgumentException("'" + typed + "' is not an outcome's name.");
        }
        Outcome wired = FlowNames.outcome(typed);
        boolean builtIn = wired.equals(Outcome.NEXT) || wired.equals(Outcome.DISABLED);
        List<Flow.Step> steps = flow.steps();
        if (!builtIn && !source.outcomes().contains(wired)) {
            List<Outcome> outcomes = new ArrayList<>(source.outcomes());
            outcomes.add(wired);
            Flow.Step grown = source.reports(outcomes);
            steps = flow.steps().stream().map(s -> s == source ? grown : s).toList();
        }
        List<Flow.Edge> edges = new ArrayList<>(flow.edges().stream()
                .filter(e -> !(e.from().equals(source.activity()) && e.outcome().equals(wired))).toList());
        edges.add(Flow.edge(source.activity(), target, wired));
        Flow next = Flow.of(steps, edges, flow.presets(), flow.start(), flow.limits());
        String problem = validate(next);
        if (problem != null) throw new IllegalArgumentException(problem);
        return next;
    }

    /**
     * {@code flow} without the wire from {@code from} on {@code outcome}; blank is {@code NEXT}. The outcome stays
     * declared — a body may still return it, and the run then stops there — so only the arrow goes.
     */
    public static Flow disconnect(Flow flow, String from, String outcome) {
        Flow.Step source = step(flow, from);
        boolean next = outcome == null || outcome.isBlank();
        String constant = next ? null : FlowNames.constantFor(FlowNames.label(outcome));
        if (!next && constant == null) {
            throw new IllegalArgumentException("'" + outcome.trim() + "' is not an outcome's name.");
        }
        List<Flow.Edge> kept = new ArrayList<>();
        Flow.Edge gone = null;
        for (Flow.Edge edge : flow.edges()) {
            boolean match = edge.from().equals(source.activity()) && (constant == null
                    ? edge.outcome().equals(Outcome.NEXT)
                    : constant.equals(FlowNames.constantFor(edge.outcome().label())));
            if (match && gone == null) gone = edge;
            else kept.add(edge);
        }
        if (gone == null) {
            throw new IllegalArgumentException(source.label() + " has no wire on "
                    + (outcome == null || outcome.isBlank() ? Arrow.NEXT : outcome.trim()) + ".");
        }
        return Flow.of(flow.steps(), kept, flow.presets(), flow.start(), flow.limits());
    }

    /** {@code flow} starting at the activity {@code name}. */
    public static Flow setStart(Flow flow, String name) {
        return Flow.of(flow.steps(), flow.edges(), flow.presets(), step(flow, name).activity(), flow.limits());
    }

    /**
     * Why {@code flow} cannot be written — a label that makes no constant, two labels making one, an outcome
     * that clashes — or null. 🔀 Activity Flow's rule (its {@code validate} says why it is this short), here so a
     * caller with no window checks the same.
     */
    public static String validate(Flow flow) {
        Map<String, String> activityConstants = new HashMap<>();
        Map<String, String> outcomeConstants = new HashMap<>();
        for (Flow.Step step : flow.steps()) {
            String name = step.label();
            String constant = FlowNames.constantFor(name);
            if (constant == null) {
                return "'" + name + "' can't become a constant in Activities.java — start it with a letter.";
            }
            String clash = activityConstants.putIfAbsent(constant, name);
            if (clash != null) {
                return clash.equals(name) ? "Duplicate activity name: '" + name + "'."
                        : "'" + name + "' and '" + clash + "' would both be Activities." + constant + ".";
            }
            // Checked against the declared list, not allOutcomes(): that one de-duplicates defensively, so
            // validating it would report a clash as clean and leave the user with an outcome that silently
            // has no port.
            Set<String> own = new HashSet<>();
            for (Outcome declared : step.outcomes()) {
                String outcome = declared.label();
                String outcomeConstant = FlowNames.constantFor(outcome);
                if (outcomeConstant == null) {
                    return "Invalid outcome in " + name + ": '" + outcome + "' can't become a constant in "
                            + "Outcomes.java.";
                }
                if (Arrow.NEXT.equals(outcomeConstant)) {
                    return name + " already has a NEXT outcome — every activity does.";
                }
                if (Arrow.DISABLED.equals(outcomeConstant)) {
                    return name + " can't declare a DISABLED outcome — that port is always there, "
                            + "and an activity can't report it because it didn't run.";
                }
                if (!own.add(outcomeConstant)) return "Duplicate outcome '" + outcome + "' in " + name + ".";
                String other = outcomeConstants.putIfAbsent(outcomeConstant, outcome);
                if (other != null && !other.equals(outcome)) {
                    return "'" + outcome + "' and '" + other + "' would both be Outcomes." + outcomeConstant + ".";
                }
            }
        }
        return null;
    }

    /** The labels of {@code flow}'s activities, in order. */
    public static Set<String> labels(Flow flow) {
        Set<String> labels = new LinkedHashSet<>();
        for (Flow.Step step : flow.steps()) labels.add(step.label());
        return labels;
    }

    /**
     * Writes {@code flow} into {@code value}: the {@code Activities} and {@code Outcomes} constants first, then
     * the flow, then the constants it no longer names — the order 🔀 Activity Flow's save keeps (see
     * {@link FlowConstants}). A rename that lands is taken out of its map. On the FX thread.
     *
     * @param saved    the activity and outcome labels the project held before
     * @param notes    collects what was left undone without stopping the save
     * @return null when written, else why not
     */
    public static String save(PluginValues values, ValueContext value, Flow flow, Saved saved,
                              Map<String, String> activityRenames, Map<String, String> outcomeRenames,
                              List<String> notes) {
        String problem = validate(flow);
        if (problem != null) return problem;
        Set<String> activities = FlowConstants.activityLabels(flow);
        Set<String> outcomes = FlowConstants.outcomeLabels(flow);
        String refused = FlowConstants.prepare(values, FlowConstants.Kind.ACTIVITIES, activities, activityRenames);
        if (refused == null) {
            refused = FlowConstants.prepare(values, FlowConstants.Kind.OUTCOMES, outcomes, outcomeRenames);
        }
        if (refused == null) refused = FlowConstants.unheld(values, FlowConstants.Kind.ACTIVITIES, flow);
        if (refused == null) refused = FlowConstants.unheld(values, FlowConstants.Kind.OUTCOMES, flow);
        if (refused == null) refused = FlowValue.write(value, flow);
        if (refused == null) {
            FlowConstants.forget(values, FlowConstants.Kind.ACTIVITIES, without(saved.activities(), activities), notes);
            FlowConstants.forget(values, FlowConstants.Kind.OUTCOMES, without(saved.outcomes(), outcomes), notes);
        }
        return refused;
    }

    /** The activity and outcome labels a project held when a flow was last read or written. */
    public record Saved(Set<String> activities, Set<String> outcomes) {
        public Saved {
            activities = Set.copyOf(activities);
            outcomes = Set.copyOf(outcomes);
        }

        public static Saved of(Flow flow) {
            return new Saved(FlowConstants.activityLabels(flow), FlowConstants.outcomeLabels(flow));
        }
    }

    private static Set<String> without(Set<String> before, Set<String> now) {
        Set<String> gone = new LinkedHashSet<>(before);
        gone.removeAll(now);
        return gone;
    }

    /** The label {@code flow} holds for the activity {@code name} names, matched as every change here matches it. */
    public static Optional<String> labelOf(Flow flow, String name) {
        try {
            return Optional.of(step(flow, name).label());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Flow.Step step(Flow flow, String name) {
        for (Flow.Step step : flow.steps()) {
            if (step.label().equalsIgnoreCase(name == null ? "" : name.trim())) return step;
        }
        throw new IllegalArgumentException("No activity called '" + name + "'; there are " + labels(flow));
    }

    private static Activity swap(Activity activity, Activity was, Activity now) {
        return activity.equals(was) ? now : activity;
    }

    private static ActivityBody body(String source) {
        if (source == null || source.isBlank()) return ActivityBody.NONE;
        String trimmed = source.trim();
        if (!FlowNames.isMethodReference(trimmed)) {
            throw new IllegalArgumentException("'" + trimmed + "' is not a method reference like Collect::body");
        }
        return FlowTypes.body(trimmed);
    }
}
