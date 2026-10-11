package com.botmaker.sdk.plugin.flow;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.bot.Outcome;
import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.flow.ActivityBody;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.plugin.types.FlowTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Changes to a flow as values, and the one way a changed flow is saved — the operations 🔀 Activity Flow
 * performs, for a caller with no window: the assistant's activity tools.
 *
 * <p>Each change answers a new {@link Board} — the flow and each activity's exits — or throws
 * {@link IllegalArgumentException} with the sentence the window would show; nothing is written until
 * {@link #save}. Links no JavaFX.
 */
public final class FlowEdits {

    private FlowEdits() {}

    /**
     * {@code board} with a new activity called {@code typed}, its body the method {@code bodySource} names
     * ({@code Collect::body}), or none yet when blank, and the one exit {@link Arrow#NEXT} Studio seeds every new
     * activity with — an ordinary outcome, renamed or removed like any other. The first activity of
     * an empty flow is its start.
     */
    public static Board addActivity(Board board, String typed, String bodySource) {
        return addActivity(board, typed, bodySource, List.of());
    }

    /**
     * {@code board} with a new activity as {@link #addActivity(Board, String, String)} adds one, its exits
     * {@code outcomes} — the outcome labels its enum already declares when another flow runs it — or
     * {@link Arrow#NEXT} when there are none.
     */
    public static Board addActivity(Board board, String typed, String bodySource, List<String> outcomes) {
        String label = FlowNames.activityLabel(typed);
        String problem = FlowNames.activityNameProblem(label, labels(board.flow()));
        if (problem != null) throw new IllegalArgumentException(problem);
        return addStep(board, FlowTypes.step(FlowNames.activity(label), body(bodySource)), outcomes);
    }

    /**
     * {@code board} with {@code shared} — the step another flow runs the activity as, its body, description and
     * switches — switched on and with no off-wire, its exits {@code outcomes}. Refused when the flow already runs it.
     */
    public static Board addShared(Board board, Flow.Step shared, List<String> outcomes) {
        String problem = FlowNames.activityNameProblem(shared.label(), labels(board.flow()));
        if (problem != null) throw new IllegalArgumentException(problem);
        return addStep(board, new Flow.Step(shared.activity(), shared.body(), shared.description(), true,
                shared.goHome(), shared.popupCheck(), shared.runnerSwitch(), Activity.NONE), outcomes);
    }

    private static Board addStep(Board board, Flow.Step step, List<String> outcomes) {
        Flow flow = board.flow();
        List<Flow.Step> steps = new ArrayList<>(flow.steps());
        steps.add(step);
        Activity start = flow.start().isNone() ? step.activity() : flow.start();
        Map<String, List<String>> exits = new LinkedHashMap<>(board.exits());
        exits.put(step.label(), outcomes == null || outcomes.isEmpty() ? List.of(Arrow.NEXT) : outcomes);
        return new Board(with(flow, steps, flow.edges(), start), exits);
    }

    /** {@code board} with the activity {@code from} called {@code typed}, in every step, wire and the start. */
    public static Board renameActivity(Board board, String from, String typed) {
        Flow flow = board.flow();
        Flow.Step step = step(flow, from);
        String label = FlowNames.activityLabel(typed);
        Set<String> others = labels(flow);
        others.remove(step.label());
        String problem = FlowNames.activityNameProblem(label, others);
        if (problem != null) throw new IllegalArgumentException(problem);
        Activity was = step.activity();
        Activity now = FlowNames.activity(label);
        List<Flow.Step> steps = flow.steps().stream().map(s -> new Flow.Step(swap(s.activity(), was, now), s.body(),
                s.description(), s.enabled(), s.goHome(), s.popupCheck(), s.runnerSwitch(),
                swap(s.whenOff(), was, now))).toList();
        List<Flow.Edge> edges = flow.edges().stream().map(e -> new Flow.Edge(
                e.from().equals(was) ? BotConstants.outcome(now.name(), e.on().name()) : e.on(),
                swap(e.to(), was, now))).toList();
        Map<String, List<String>> exits = new LinkedHashMap<>();
        board.exits().forEach((activity, outcomes) -> exits.put(activity.equals(step.label()) ? now.label() : activity,
                outcomes));
        return new Board(with(flow, steps, edges, swap(flow.start(), was, now)), exits);
    }

    /** {@code board} without the activity {@code name}, its wires, and the start if it was. */
    public static Board removeActivity(Board board, String name) {
        Flow flow = board.flow();
        Flow.Step removed = step(flow, name);
        Activity gone = removed.activity();
        List<Flow.Step> steps = flow.steps().stream().filter(s -> !s.activity().equals(gone))
                .map(s -> s.whenOff().equals(gone) ? FlowTypes.whenOff(s, Activity.NONE) : s).toList();
        List<Flow.Edge> edges = flow.edges().stream()
                .filter(e -> !e.from().equals(gone) && !e.to().equals(gone)).toList();
        Activity start = flow.start().equals(gone) ? (steps.isEmpty() ? Activity.NONE : steps.getFirst().activity())
                : flow.start();
        Map<String, List<String>> exits = new LinkedHashMap<>(board.exits());
        exits.remove(removed.label());
        return new Board(with(flow, steps, edges, start), exits);
    }

    /**
     * {@code board} with the wire from {@code from} on {@code outcome} going to {@code to}, replacing the one that
     * outcome had. An outcome {@code from} does not have yet is added to its exits; blank is its first outcome,
     * and "disabled" is where it goes switched off.
     */
    public static Board connect(Board board, String from, String outcome, String to) {
        Flow flow = board.flow();
        Flow.Step source = step(flow, from);
        Activity target = step(flow, to).activity();
        String typed = typed(board, source, outcome);
        String constant = FlowNames.constantFor(typed);
        if (constant == null) throw new IllegalArgumentException("'" + typed + "' is not an outcome's name.");
        if (Arrow.DISABLED.equals(constant)) {
            return board.with(withOff(flow, source, target));
        }
        Outcome wired = BotConstants.outcome(source.activity().name(), constant);
        Map<String, List<String>> exits = new LinkedHashMap<>(board.exits());
        List<String> own = new ArrayList<>(board.exitsOf(source.label()));
        if (own.stream().noneMatch(label -> constant.equals(FlowNames.constantFor(label)))) {
            own.add(wired.label());
            exits.put(source.label(), own);
        }
        List<Flow.Edge> edges = new ArrayList<>(flow.edges().stream().filter(e -> !e.on().equals(wired)).toList());
        edges.add(new Flow.Edge(wired, target));
        Board next = new Board(with(flow, flow.steps(), edges, flow.start()), exits);
        String problem = validate(next);
        if (problem != null) throw new IllegalArgumentException(problem);
        return next;
    }

    /**
     * {@code board} without the wire from {@code from} on {@code outcome}; blank is its first outcome. The outcome
     * stays — a body may still return it, and the run then stops there — so only the arrow goes.
     */
    public static Board disconnect(Board board, String from, String outcome) {
        Flow flow = board.flow();
        Flow.Step source = step(flow, from);
        String typed = typed(board, source, outcome);
        String constant = FlowNames.constantFor(FlowNames.label(typed));
        if (constant == null) throw new IllegalArgumentException("'" + typed + "' is not an outcome's name.");
        if (Arrow.DISABLED.equals(constant)) {
            if (source.whenOff().isNone()) throw new IllegalArgumentException(source.label() + " has no wire on " + typed + ".");
            return board.with(withOff(flow, source, Activity.NONE));
        }
        List<Flow.Edge> kept = new ArrayList<>();
        Flow.Edge gone = null;
        for (Flow.Edge edge : flow.edges()) {
            boolean match = edge.from().equals(source.activity()) && constant.equals(edge.on().name());
            if (match && gone == null) gone = edge;
            else kept.add(edge);
        }
        if (gone == null) throw new IllegalArgumentException(source.label() + " has no wire on " + typed + ".");
        return board.with(with(flow, flow.steps(), kept, flow.start()));
    }

    /** The outcome {@code outcome} names, trimmed: blank is {@code source}'s first, NEXT in a new activity. */
    private static String typed(Board board, Flow.Step source, String outcome) {
        if (outcome != null && !outcome.isBlank()) return outcome.trim();
        List<String> own = board.exitsOf(source.label());
        if (own.isEmpty()) throw new IllegalArgumentException(source.label() + " has no outcome to leave on.");
        return own.getFirst();
    }

    /** {@code board} starting at the activity {@code name}. */
    public static Board setStart(Board board, String name) {
        Flow flow = board.flow();
        return board.with(with(flow, flow.steps(), flow.edges(), step(flow, name).activity()));
    }

    /**
     * Why {@code board} cannot be written — a label that makes no name, two labels making one, an outcome that
     * clashes — or null. 🔀 Activity Flow's rule, here so a caller with no window checks the same.
     */
    public static String validate(Board board) {
        Map<String, String> activityTypes = new HashMap<>();
        for (Flow.Step step : board.flow().steps()) {
            String name = step.label();
            String type = FlowNames.typeFor(name);
            if (type == null) {
                return "'" + name + "' can't become a name in " + FlowNames.ACTIVITIES + ".java — start it with a letter.";
            }
            String clash = activityTypes.putIfAbsent(type, name);
            if (clash != null) {
                return clash.equals(name) ? "Duplicate activity name: '" + name + "'."
                        : "'" + name + "' and '" + clash + "' would both be " + FlowNames.ACTIVITIES + "." + type + ".";
            }
            if (board.exitsOf(name).isEmpty()) {
                return name + " needs an outcome: its body returns one of " + FlowNames.ACTIVITIES + "." + type
                        + "'s constants, and an enum with none has nothing to return.";
            }
            Set<String> own = new HashSet<>();
            for (String outcome : board.exitsOf(name)) {
                String constant = FlowNames.constantFor(outcome);
                if (constant == null) {
                    return "Invalid outcome in " + name + ": '" + outcome + "' can't become a constant in "
                            + FlowNames.ACTIVITIES + "." + type + ".";
                }
                if (Arrow.DISABLED.equals(constant)) {
                    return name + " can't have a DISABLED outcome — that port is always there, "
                            + "and an activity can't report it because it didn't run.";
                }
                if (!own.add(constant)) return "Duplicate outcome '" + outcome + "' in " + name + ".";
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
     * Writes {@code board} as {@code target}'s flow: the {@code Activities} enums first, then the flow, then what
     * it no longer names — the order 🔀 Activity Flow's save keeps (see {@link FlowConstants}). A rename that lands
     * is taken out of its map. On the FX thread.
     *
     * @param saved           the activity labels the project held before
     * @param activityRenames activity label at last save → label now
     * @param outcomeRenames  activity label now → outcome label at last save → label now
     * @param notes           collects what was left undone without stopping the save
     * @return null when written, else why not
     */
    public static String save(StudioServices services, FlowValue.Target target, Board board, Set<String> saved,
                              Map<String, String> activityRenames, Map<String, Map<String, String>> outcomeRenames,
                              List<String> notes) {
        String problem = validate(board);
        if (problem != null) return problem;
        if (target.problem() != null) return target.problem();
        PluginValues values = services.pluginValues();
        String refused = FlowConstants.prepare(values, board, activityRenames, outcomeRenames);
        if (refused == null) refused = FlowConstants.unheld(values, board.flow());
        if (refused == null) refused = FlowValue.write(services, target, board.flow());
        if (refused == null) {
            FlowConstants.forget(values, board, saved, () -> FlowValue.labels(values, target.name()), notes);
        }
        return refused;
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

    /** {@code flow} with these parts, its limits kept. */
    static Flow with(Flow flow, List<Flow.Step> steps, List<Flow.Edge> edges, Activity start) {
        return new Flow(steps, edges, start, flow.limits());
    }

    private static Flow withOff(Flow flow, Flow.Step source, Activity to) {
        List<Flow.Step> steps = flow.steps().stream().map(s -> s == source ? FlowTypes.whenOff(s, to) : s).toList();
        return with(flow, steps, flow.edges(), flow.start());
    }

    private static Activity swap(Activity activity, Activity was, Activity now) {
        return activity.equals(was) ? now : activity;
    }

    private static ActivityBody<?> body(String source) {
        // Blank is the card not written yet, held as the editor reads one back: written ActivityBody.none().
        if (source == null || source.isBlank()) return FlowTypes.body("");
        String trimmed = source.trim();
        if (!FlowNames.isMethodReference(trimmed)) {
            throw new IllegalArgumentException("'" + trimmed + "' is not a method reference like Collect::body");
        }
        return FlowTypes.body(trimmed);
    }
}
