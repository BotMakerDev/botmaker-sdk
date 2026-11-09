package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.plan.DoneRecords.Done;
import com.botmaker.sdk.internal.plan.Round.Block;
import com.botmaker.sdk.internal.plan.Round.Considered;
import com.botmaker.sdk.internal.plan.Round.Step;
import com.botmaker.sdk.internal.plan.Round.Why;
import com.botmaker.sdk.internal.screens.Conditions;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
import com.botmaker.sdk.internal.screens.ScreenWalk;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * One round of a plan: a {@link PlanModel} in, a {@link Round} out, nothing walked and nothing pressed. It takes the
 * highest tier with a goal neither met nor waiting, ranks each goal's actions, and merges the tier's best into one route.
 * A goal's check and a way's conditions are asked once a round; an action's own, each time it is looked at.
 */
public final class Planner {

    /** How deep sub-goals nest: a goal's action may need a goal, up to this many in a row. */
    public static final int DEEPEST = 4;

    /**
     * When a goal blocked only for now is looked at again: a need or a {@code when} saying no, no way there now. It
     * waits this long, as one waiting for its cadence does, instead of ending the run.
     */
    public static final Duration RECHECK = Duration.ofMinutes(1);

    /**
     * Among actions of one bucket and in no {@code .over}: no sub-goal first, known first, then the least time in all,
     * the walk there and what each action {@link Do#takes}.
     */
    private static final Comparator<Option> CHEAPEST = Comparator.comparing((Option o) -> o.before() > 0)
            .thenComparing(o -> !o.known()).thenComparingLong(Option::cost);

    private final PlanModel model;
    private final Map<Goal, Answer> answers = new HashMap<>();
    private final Map<List<String>, Optional<List<Edge>>> routes = new HashMap<>();
    private final Map<Edge, Boolean> open = new HashMap<>();
    /** Each goal looked at, at the shallowest depth it was. */
    private final Map<Goal, Looked> looked = new LinkedHashMap<>();
    /** The goals being resolved, outermost first: one needed again inside its own is a loop. */
    private final Set<Goal> resolving = new LinkedHashSet<>();
    /** How many loops were met so far: a goal resolved while one was met answers only for the path it was on. */
    private int loops;
    /** When a goal blocked only for now is looked at again: one instant for the whole round. */
    private final Instant recheck;

    private Planner(PlanModel model) {
        this.model = model;
        this.recheck = model.clock().instant().plus(RECHECK);
    }

    /** What to do next under {@code model}'s plan, and why. */
    public static Round round(PlanModel model) {
        return new Planner(model).round();
    }

    /**
     * The way a walk under {@code model} takes from {@code from} to {@code to}, as a round would: what the Goals view
     * draws for one action. Empty when none leads there now.
     */
    public static Optional<List<Edge>> way(PlanModel model, String from, String to) {
        return new Planner(model).way(from, to);
    }

    /** Whether {@code screen} is one of {@code crossed} or one step off one of them and back, as on-the-way is. */
    static boolean near(PlanModel model, List<String> crossed, String screen) {
        return new Planner(model).nearCrossed(crossed, screen).isPresent();
    }

    /**
     * An action as looked at.
     *
     * @param chain  what to do for it, in order, itself last; empty when it is no choice
     * @param cost   what walking to them costs, each from where the game is, and what each {@link Do#takes}, in ms
     * @param known  whether everything it asks has answered
     * @param before how many actions come before it
     */
    private record Option(Step step, Why why, List<Step> chain, long cost, boolean known, int before) {

        static Option none(Step step, Why why) {
            return new Option(step, why, List.of(), 0, false, 0);
        }
    }

    /** A goal as looked at: {@code options} in the order they would be taken, those that are a choice first. */
    private record Resolved(boolean met, List<Option> options) {

        /** The choice; a last resort is none while something better only has to wait: it may spend what that would not. */
        Optional<Option> best() {
            boolean betterWaits = options.stream()
                    .anyMatch(o -> o.why() instanceof Why.Waiting && !o.step().action().isLast());
            return options.stream().filter(o -> Round.possible(o.why())).findFirst()
                    .filter(o -> !o.step().action().isLast() || !betterWaits);
        }

        /**
         * When it comes due, while nothing can be done for it and something is only waiting; else empty. An action
         * blocked only for now waits until {@code recheck}, so the goal is looked at again rather than given up.
         */
        Optional<Instant> waitingUntil(Instant recheck) {
            if (met || best().isPresent()) return Optional.empty();
            return options.stream().map(Option::why).map(why -> switch (why) {
                case Why.Waiting waiting -> waiting.until();
                case Why.Blocked blocked when blocked.block().forNow() -> recheck;
                default -> null;
            }).filter(Objects::nonNull).min(Comparator.naturalOrder());
        }
    }

    /** A goal as resolved; {@code onItsPath} when a loop was met inside it, so it holds only for the path it was on. */
    private record Looked(int depth, Resolved resolved, boolean onItsPath) {}

    private Round round() {
        Plan plan = model.plan();
        Map<Goal, Resolved> goals = new LinkedHashMap<>();
        for (Plan.Tier tier : plan.tiers()) {
            for (Goal goal : tier.goals()) goals.put(goal, resolve(goal, 0));
        }

        OptionalInt taken = OptionalInt.empty();
        Instant due = null;
        List<List<Step>> chains = new ArrayList<>();
        for (int i = 0; i < plan.tiers().size() && taken.isEmpty(); i++) {
            for (Goal goal : plan.tiers().get(i).goals()) {
                Resolved resolved = goals.get(goal);
                if (resolved.met()) continue;
                Optional<Instant> until = resolved.waitingUntil(recheck);
                if (until.isPresent()) {
                    due = earlier(due, until.get());
                    continue;
                }
                taken = OptionalInt.of(i);
                resolved.best().ifPresent(best -> chains.add(best.chain()));
            }
        }
        List<Step> route = merge(chains);
        List<Considered> considered = new ArrayList<>();
        // The plan's goals in its order, then sub-goals of no tier.
        Set<Goal> order = new LinkedHashSet<>(goals.keySet());
        order.addAll(looked.keySet());
        for (Goal goal : order) {
            looked.get(goal).resolved().options()
                    .forEach(option -> considered.add(new Considered(goal, option.step(), option.why())));
        }
        List<String> crossed = route.isEmpty() ? List.of() : crossed(route);
        List<Near> near = onTheWay(route, goals, crossed);
        return new Round(taken, route, near.stream().map(Near::step).toList(), doing(route, near, crossed), crossed,
                considered, Optional.ofNullable(due));
    }

    /** An on-the-way action, and which of the crossed screens it is done from. */
    private record Near(int at, Step step) {}

    /** {@code route} with each of {@code near} before the first of its steps that is as far along {@code crossed}. */
    private static List<Step> doing(List<Step> route, List<Near> near, List<String> crossed) {
        List<Step> doing = new ArrayList<>();
        int next = 0;
        int along = 0;
        for (Step step : route) {
            along = Math.max(along, crossed.indexOf(step.screen()));
            while (next < near.size() && near.get(next).at() <= along) doing.add(near.get(next++).step());
            doing.add(step);
        }
        while (next < near.size()) doing.add(near.get(next++).step());
        return doing;
    }

    private Resolved resolve(Goal goal, int depth) {
        Looked prior = looked.get(goal);
        if (prior != null && prior.depth() == depth && !prior.onItsPath()) return prior.resolved();
        int loopsBefore = loops;
        Resolved resolved;
        if (answer(goal) == Answer.YES) {
            resolved = new Resolved(true, List.of());
        } else {
            resolving.add(goal);
            List<Option> options = new ArrayList<>();
            try {
                for (Step step : model.actions()) {
                    if (step.action().serves().contains(goal)) options.add(consider(step, depth));
                }
            } finally {
                resolving.remove(goal);
            }
            resolved = new Resolved(false, rank(options));
        }
        Looked now = looked.get(goal);
        if (now == null || depth < now.depth() || depth == now.depth() && now.onItsPath()) {
            looked.put(goal, new Looked(depth, resolved, loops != loopsBefore));
        }
        return resolved;
    }

    /** What {@code step} can do for a goal at {@code depth}, from what is known, nothing walked. */
    private Option consider(Step step, int depth) {
        Do action = step.action();
        // As a press takes it: what its strip or tree says of it counts. A walk types no text only a body gives.
        ScreenGraph map = model.map();
        Optional<Input> input = map.nameOf(action.input()).filter(name -> name.screen().equals(step.screen()))
                .flatMap(name -> map.input(name.screen(), name.field()));
        if (input.isEmpty() || input.get() instanceof Button typed && typed.isTyped()) {
            return Option.none(step, new Why.Blocked(Block.NO_INPUT));
        }
        if (input.get().never() != null) return Option.none(step, new Why.Blocked(Block.NEVER));
        Answer press = Readiness.canPress(input.get());
        if (press == Answer.NO) return Option.none(step, new Why.Blocked(Block.INPUT));
        boolean known = press == Answer.YES;
        for (Condition need : action.conditionNeeds()) {
            Answer answer = Conditions.answer(need);
            if (answer == Answer.NO) return Option.none(step, new Why.Blocked(Block.CONDITION));
            known &= answer == Answer.YES;
        }
        if (action.until() != null) {
            Answer ended = Conditions.answer(action.until());
            if (ended == Answer.YES) return Option.none(step, new Why.Blocked(Block.ENDED));
            known &= ended == Answer.NO;
        }
        Optional<Done> last = model.last(step);
        Optional<Instant> due = Readiness.dueAt(action, last, model.clock());
        if (due.isEmpty()) {
            boolean held = last.map(Done::held).orElse(false);
            return Option.none(step, new Why.Blocked(held ? Block.DONE_FOR_GOOD : Block.GIVEN_UP));
        }
        if (due.get().isAfter(model.clock().instant())) return Option.none(step, new Why.Waiting(due.get()));
        Optional<Instant> heldOff = model.heldOff(step);
        if (heldOff.isPresent()) return Option.none(step, new Why.Waiting(heldOff.get()));

        List<Step> chain = new ArrayList<>();
        long cost = 0;
        Goal firstUnmet = null;
        Instant waiting = null;
        for (Goal need : action.goalNeeds()) {
            Answer answer = answer(need);
            if (answer == Answer.YES) continue;
            if (answer == Answer.UNKNOWN) {
                known = false;
                continue;
            }
            if (resolving.contains(need)) {
                loops++;
                return Option.none(step, new Why.Loop(need));
            }
            if (depth == DEEPEST) return Option.none(step, new Why.TooDeep(need));
            Resolved sub = resolve(need, depth + 1);
            Optional<Option> best = sub.best();
            Optional<Instant> until = sub.waitingUntil(recheck);
            if (best.isPresent()) {
                best.get().chain().forEach(s -> addOnce(chain, s));
                cost += best.get().cost();
                if (firstUnmet == null) firstUnmet = need;
            } else if (until.isPresent()) {
                waiting = waiting == null || until.get().isAfter(waiting) ? until.get() : waiting;
            } else {
                return Option.none(step, new Why.CannotMeet(need));
            }
        }
        if (waiting != null) return Option.none(step, new Why.Waiting(waiting));
        int before = chain.size();
        // What it needs may be what opens the way; once that is done, the next round looks again.
        Optional<Long> walk = cost(model.at(), step.screen());
        if (walk.isEmpty() && before == 0) return Option.none(step, new Why.Blocked(Block.NO_WAY_THERE));
        cost += walk.orElse(0L) + takes(action);
        addOnce(chain, step);
        Why why = before > 0 ? new Why.Needs(firstUnmet, before)
                : action.isLast() ? new Why.LastResort()
                : known ? new Why.Ready() : new Why.Unknown();
        return new Option(step, why, List.copyOf(chain), cost, known, before);
    }

    /** {@code .first}, plain, {@code .last}; within each, what {@code .over} says, then {@link #CHEAPEST}; the rest after. */
    private List<Option> rank(List<Option> options) {
        List<Option> ranked = new ArrayList<>();
        for (int bucket = 0; bucket < 3; bucket++) {
            int wanted = bucket;
            List<Option> left = new ArrayList<>(options.stream()
                    .filter(o -> Round.possible(o.why()) && bucket(o.step().action()) == wanted)
                    .sorted(CHEAPEST).toList());
            while (!left.isEmpty()) {
                // PlanModel refused a cycle, so one always has nothing tried before it.
                Option next = left.stream().filter(o -> left.stream()
                        .noneMatch(other -> model.over(other.step().action(), o.step().action()))).findFirst().orElseThrow();
                ranked.add(next);
                left.removeIf(o -> o == next);
            }
        }
        options.stream().filter(o -> !Round.possible(o.why())).forEach(ranked::add);
        return ranked;
    }

    private static int bucket(Do action) {
        return action.isFirst() ? 0 : action.isLast() ? 2 : 1;
    }

    /**
     * The chains as one route: of what comes next in each, the quickest from where the last step left the game, its
     * walk there and what it {@link Do#takes}; a tier's quick goals are met before its slow ones.
     */
    private List<Step> merge(List<List<Step>> chains) {
        List<List<Step>> left = new ArrayList<>();
        for (List<Step> chain : chains) left.add(new ArrayList<>(chain));
        List<Step> route = new ArrayList<>();
        String at = model.at();
        while (true) {
            left.removeIf(List::isEmpty);
            if (left.isEmpty()) return route;
            String from = at;
            Step next = left.stream().map(List::getFirst)
                    .min(Comparator.comparingLong(s -> cost(from, s.screen()).map(walk -> walk + takes(s.action()))
                            .orElse(Long.MAX_VALUE))).orElseThrow();
            route.add(next);
            at = next.screen();
            for (List<Step> chain : left) chain.removeIf(s -> s.action() == next.action());
        }
    }

    /**
     * For each goal of the plan neither met nor served by the route, its first ready action on a screen the route
     * crosses or one screen off it. A last resort waits for its turn, and an on-sight action is the walk's.
     */
    private List<Near> onTheWay(List<Step> route, Map<Goal, Resolved> goals, List<String> crossed) {
        if (route.isEmpty()) return List.of();
        List<Near> near = new ArrayList<>();
        goals.forEach((goal, resolved) -> {
            if (resolved.met() || route.stream().anyMatch(s -> s.action().serves().contains(goal))) return;
            for (Option option : resolved.options()) {
                Step step = option.step();
                if (!(option.why() instanceof Why.Ready) || step.action().isOnSight() || holds(route, step)) continue;
                OptionalInt at = nearCrossed(crossed, step.screen());
                if (at.isEmpty()) continue;
                if (near.stream().noneMatch(n -> n.step().action() == step.action())) near.add(new Near(at.getAsInt(), step));
                return;
            }
        });
        near.sort(Comparator.comparingInt(Near::at));
        return near;
    }

    /** The screens {@code route} shows, from where the game is, in order. */
    private List<String> crossed(List<Step> route) {
        Set<String> crossed = new LinkedHashSet<>(Set.of(model.at()));
        String at = model.at();
        for (Step step : route) {
            way(at, step.screen()).ifPresent(path -> path.forEach(edge -> crossed.add(edge.to())));
            at = step.screen();
        }
        return List.copyOf(crossed);
    }

    /** Which of {@code crossed} comes to {@code screen}: itself, else the first one step from it and back; else empty. */
    private OptionalInt nearCrossed(List<String> crossed, String screen) {
        int on = crossed.indexOf(screen);
        if (on >= 0) return OptionalInt.of(on);
        for (int i = 0; i < crossed.size(); i++) {
            String from = crossed.get(i);
            if (oneStep(from, screen) && oneStep(screen, from)) return OptionalInt.of(i);
        }
        return OptionalInt.empty();
    }

    private boolean oneStep(String from, String to) {
        return way(from, to).filter(path -> path.size() == 1).isPresent();
    }

    private Answer answer(Goal goal) {
        return answers.computeIfAbsent(goal, model::answer);
    }

    /** What walking from {@code from} to {@code to} costs, in ms; empty when no way leads there now. */
    private Optional<Long> cost(String from, String to) {
        ScreenGraph map = model.map();
        return way(from, to).map(path -> path.stream().mapToLong(map::cost).sum());
    }

    /** The way the map gives, by what a walk could take; a condition that says no closes one, an unknown does not. */
    private Optional<List<Edge>> way(String from, String to) {
        return routes.computeIfAbsent(List.of(Objects.toString(from), Objects.toString(to)),
                key -> model.map().pathTo(key.get(0), key.get(1), edge -> open.computeIfAbsent(edge, Planner::isOpen)));
    }

    /** Whether a walk could take {@code edge} now: its conditions do not say no, and its input can be walked. */
    static boolean isOpen(Edge edge) {
        if (!Conditions.holds(edge.when())) return false;
        return edge.via() == null || ScreenWalk.walkable(edge.via()) && Readiness.canPress(edge.via()) != Answer.NO;
    }

    /** What {@code action} {@link Do#takes} once pressed, in ms: 0 when it says none. */
    static long takes(Do action) {
        return action.takes() == null ? 0 : action.takes().toMillis();
    }

    private static boolean holds(List<Step> steps, Step step) {
        return steps.stream().anyMatch(s -> s.action() == step.action());
    }

    private static void addOnce(List<Step> steps, Step step) {
        if (!holds(steps, step)) steps.add(step);
    }

    private static Instant earlier(Instant a, Instant b) {
        return a == null || b.isBefore(a) ? b : a;
    }
}
