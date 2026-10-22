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
                shared.goHome(), shared.runnerSwitch(), Activity.NONE, shared.screen()), outcomes);
    }

    /**
     * {@code board} with a card running the flow {@code inner}, {@code ▸ DAILY}: no body, its exits the ends of
     * {@code inner}, read off that flow and so never held here. Refused when the flow already has one. Whether
     * {@code inner} may run here at all is the caller's to check ({@link FlowRefs#offerable}).
     */
    public static Board addSubflow(Board board, String inner) {
        String label = FlowNames.subflowLabel(inner);
        Flow flow = board.flow();
        if (flow.steps().stream().anyMatch(s -> s.label().equals(label))) {
            throw new IllegalArgumentException("The flow already has a card running Flows." + inner + ".");
        }
        List<Flow.Step> steps = new ArrayList<>(flow.steps());
        Flow.Step step = Flow.subflow(BotConstants.flow(inner));
        steps.add(step);
        Flow.Node start = flow.start().isNone() ? step.node() : flow.start();
        return board.with(with(flow, steps, flow.edges(), start));
    }

    /**
     * {@code board} with the wire from the card running {@code inner} on its exit {@code exit}
     * ({@code Claim · Done}: the inner activity's outcome) going to {@code to}, replacing the one that exit had.
     */
    public static Board connectExit(Board board, String inner, String exit, String to) {
        Flow flow = board.flow();
        Flow.Node card = step(flow, FlowNames.subflowLabel(inner)).node();
        Flow.Node target = step(flow, to).node();
        Outcome on = FlowNames.exitOutcome(exit);
        List<Flow.Edge> edges = new ArrayList<>(flow.edges().stream()
                .filter(e -> !(card.equals(e.from()) && on.equals(e.on()))).toList());
        edges.add(new Flow.Edge(BotConstants.flow(inner), on, target));
        return board.with(with(flow, flow.steps(), edges, flow.start()));
    }

    /** {@code board} without the wire from the card running {@code inner} on its exit {@code exit}. */
    public static Board disconnectExit(Board board, String inner, String exit) {
        Flow flow = board.flow();
        Flow.Step card = step(flow, FlowNames.subflowLabel(inner));
        Outcome on = FlowNames.exitOutcome(exit);
        List<Flow.Edge> kept = flow.edges().stream()
                .filter(e -> !(card.node().equals(e.from()) && on.equals(e.on()))).toList();
        if (kept.size() == flow.edges().size()) {
            throw new IllegalArgumentException(card.label() + " has no wire on " + exit + ".");
        }
        return board.with(with(flow, flow.steps(), kept, flow.start()));
    }

    private static Board addStep(Board board, Flow.Step step, List<String> outcomes) {
        Flow flow = board.flow();
        List<Flow.Step> steps = new ArrayList<>(flow.steps());
        steps.add(step);
        Flow.Node start = flow.start().isNone() ? step.node() : flow.start();
        Map<String, List<String>> exits = new LinkedHashMap<>(board.exits());
        exits.put(step.label(), outcomes == null || outcomes.isEmpty() ? List.of(Arrow.NEXT) : outcomes);
        return new Board(with(flow, steps, flow.edges(), start), exits);
    }

    /** {@code board} with the activity {@code from} called {@code typed}, in every step, wire and the start. */
    public static Board renameActivity(Board board, String from, String typed) {
        Flow flow = board.flow();
        Flow.Step step = step(flow, from);
        if (step.node() instanceof Flow.Sub) {
            throw new IllegalArgumentException(step.label() + " is a sub-flow card: it is named by its flow, so rename "
                    + "the flow instead.");
        }
        String label = FlowNames.activityLabel(typed);
        Set<String> others = labels(flow);
        others.remove(step.label());
        String problem = FlowNames.activityNameProblem(label, others);
        if (problem != null) throw new IllegalArgumentException(problem);
        Activity was = step.activity();
        Activity now = FlowNames.activity(label);
        List<Flow.Step> steps = flow.steps().stream().map(s -> new Flow.Step(swap(s.node(), was, now), s.body(),
                s.description(), s.enabled(), s.goHome(), s.runnerSwitch(),
                swap(s.whenOff(), was, now), s.screen())).toList();
        // An outcome names its enum, so a wire on one of was's renames with it, a sub-flow card's exit included.
        List<Flow.Edge> edges = flow.edges().stream().map(e -> new Flow.Edge(e.via(),
                e.on() != null && e.on().activity().equals(was) ? BotConstants.outcome(now.name(), e.on().name())
                        : e.on(),
                swap(e.to(), was, now))).toList();
        Map<String, List<String>> exits = new LinkedHashMap<>();
        board.exits().forEach((activity, outcomes) -> exits.put(activity.equals(step.label()) ? now.label() : activity,
                outcomes));
        return new Board(with(flow, steps, edges, swap(flow.start(), was, now)), exits);
    }

    /** {@code board} without the card {@code name} — an activity or a sub-flow card — its wires, and the start if it was. */
    public static Board removeActivity(Board board, String name) {
        Flow flow = board.flow();
        Flow.Step removed = step(flow, name);
        Flow.Node gone = removed.node();
        List<Flow.Step> steps = flow.steps().stream().filter(s -> !s.node().equals(gone))
                .map(s -> s.whenOff().equals(gone) ? FlowTypes.whenOff(s, Activity.NONE) : s).toList();
        List<Flow.Edge> edges = flow.edges().stream()
                .filter(e -> !e.from().equals(gone) && !e.to().equals(gone)).toList();
        Flow.Node start = flow.start().equals(gone) ? (steps.isEmpty() ? Activity.NONE : steps.getFirst().node())
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
        Flow.Step source = wireSource(flow, from);
        Flow.Node target = step(flow, to).node();
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
        List<Flow.Edge> edges = new ArrayList<>(flow.edges().stream()
                .filter(e -> e.via() != null || !wired.equals(e.on())).toList());
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
        Flow.Step source = wireSource(flow, from);
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

    /**
     * {@code board} with the card {@code name} as 🔀 Activity Flow's card leaves it: its body the method
     * {@code bodySource} names ({@code Collect::body}), its line {@code description} and its switches — each kept
     * as it is when null, the body also when blank; a blank line is none. A sub-flow card runs its flow and has no body to give.
     */
    public static Board setCard(Board board, String name, String bodySource, String description, Boolean goHome,
                                Boolean runnerSwitch) {
        Flow flow = board.flow();
        Flow.Step was = step(flow, name);
        boolean newBody = bodySource != null && !bodySource.isBlank();
        if (newBody && was.node() instanceof Flow.Sub) {
            throw new IllegalArgumentException(was.label() + " is a sub-flow card: it runs its flow and has no body.");
        }
        Flow.Step now = new Flow.Step(was.node(), newBody ? body(bodySource) : was.body(),
                description == null ? was.description() : description.strip(), was.enabled(),
                goHome == null ? was.goHome() : goHome, runnerSwitch == null ? was.runnerSwitch() : runnerSwitch, was.whenOff(),
                was.screen());
        List<Flow.Step> steps = flow.steps().stream().map(s -> s == was ? now : s).toList();
        return board.with(with(flow, steps, flow.edges(), flow.start()));
    }

    /** {@code board} starting at the card {@code name}. */
    public static Board setStart(Board board, String name) {
        Flow flow = board.flow();
        return board.with(with(flow, flow.steps(), flow.edges(), step(flow, name).node()));
    }

    /**
     * Why {@code board} cannot be written — a label that makes no name, two labels making one, an outcome that
     * clashes — or null. 🔀 Activity Flow's rule, here so a caller with no window checks the same.
     */
    public static String validate(Board board) {
        Map<String, String> activityTypes = new HashMap<>();
        for (Flow.Step step : board.flow().steps()) {
            if (step.node() instanceof Flow.Sub) continue;   // named by its flow; its exits are that flow's
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

    /** The labels of {@code flow}'s activities, in order; its sub-flow cards are not activities. */
    public static Set<String> labels(Flow flow) {
        Set<String> labels = new LinkedHashSet<>();
        for (Flow.Step step : flow.steps()) {
            if (step.node() instanceof Activity) labels.add(step.label());
        }
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
        throw new IllegalArgumentException("No activity called '" + name + "'; there are "
                + flow.steps().stream().map(Flow.Step::label).toList());
    }

    /** The activity a wire on one of its own outcomes leaves; a sub-flow card leaves on an exit instead. */
    private static Flow.Step wireSource(Flow flow, String name) {
        Flow.Step source = step(flow, name);
        if (source.node() instanceof Flow.Sub) {
            throw new IllegalArgumentException(source.label() + " is a sub-flow card: a wire leaves it on an exit, an "
                    + "activity of " + FlowRefs.flowOf(source.node()) + " and the outcome that activity ended on.");
        }
        return source;
    }

    /** {@code flow} with these parts, its limits kept. */
    static Flow with(Flow flow, List<Flow.Step> steps, List<Flow.Edge> edges, Flow.Node start) {
        return new Flow(steps, edges, start, flow.limits());
    }

    private static Flow withOff(Flow flow, Flow.Step source, Flow.Node to) {
        List<Flow.Step> steps = flow.steps().stream().map(s -> s == source ? FlowTypes.whenOff(s, to) : s).toList();
        return with(flow, steps, flow.edges(), flow.start());
    }

    private static Flow.Node swap(Flow.Node node, Flow.Node was, Flow.Node now) {
        return node.equals(was) ? now : node;
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
