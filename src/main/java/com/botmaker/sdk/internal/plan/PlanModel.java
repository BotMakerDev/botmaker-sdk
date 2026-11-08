package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.internal.plan.DoneRecords.Done;
import com.botmaker.sdk.internal.plan.Round.Step;
import com.botmaker.sdk.internal.screens.Conditions;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What {@link Planner} decides from, and nothing else: the plan, the map with every action on it, the done-records with
 * their clock, and where the game is. Facts are as the goals' and actions' own checks read them. Building one reads
 * every {@code .over}, and refuses a cycle with a sentence.
 */
public final class PlanModel {

    /** A plan of one goal nothing answers, for what reads only the map's actions: a plan holds a tier. */
    public static final Plan NO_PLAN = Plan.of(Plan.tier(Goal.of((Condition) () -> Answer.UNKNOWN)));

    private final Plan plan;
    private final ScreenGraph map;
    private final DoneRecords records;
    private final String at;
    private final List<Step> actions;
    /** Each action, by identity: two fields may hold equal values, and each is its own action. */
    private final Map<Do, Step> steps = new IdentityHashMap<>();
    /** What each action is tried before, directly or through another. */
    private final Map<Do, Set<Do>> over = new IdentityHashMap<>();
    /** What a run found it could not do on arrival, by identity, and until when it leaves it be. */
    private final Map<Do, Instant> heldOff;

    /**
     * {@code at} is the screen the game shows; one the map does not hold is taken as the map's home, and so is a popup,
     * which has no way on the map out of it: a walk closes it first, whatever lies under it.
     */
    public PlanModel(Plan plan, ScreenGraph map, DoneRecords records, String at) {
        this(plan, map, records, at, Map.of());
    }

    /** As {@link #PlanModel(Plan, ScreenGraph, DoneRecords, String)}, each of {@code heldOff} waiting until its time. */
    public PlanModel(Plan plan, ScreenGraph map, DoneRecords records, String at, Map<Do, Instant> heldOff) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.map = Objects.requireNonNull(map, "map");
        this.records = Objects.requireNonNull(records, "records");
        this.heldOff = new IdentityHashMap<>(heldOff);
        boolean aPlace = at != null && map.screen(at).filter(screen -> !screen.isPopup()).isPresent();
        this.at = aPlace ? at : map.home().orElse(at);
        List<Step> all = new ArrayList<>();
        map.screens().forEach((screen, held) -> held.actions().forEach((field, action) -> {
            Step step = new Step(action, screen, field);
            all.add(step);
            steps.put(action, step);
        }));
        this.actions = List.copyOf(all);
        for (Step step : actions) readOver(step.action(), new ArrayList<>());
    }

    /** The sentence a model over {@code map} would refuse its {@code .over}s with; empty when they make no circle. */
    public static Optional<String> overCycle(ScreenGraph map) {
        try {
            new PlanModel(NO_PLAN, map, DoneRecords.none(), null);
            return Optional.empty();
        } catch (IllegalArgumentException cycle) {
            return Optional.of(cycle.getMessage());
        }
    }

    /** The plan. */
    public Plan plan() {
        return plan;
    }

    /** The map. */
    public ScreenGraph map() {
        return map;
    }

    /** Where the game is. */
    public String at() {
        return at;
    }

    /** Every action on the map, in the map's order. */
    public List<Step> actions() {
        return actions;
    }

    /** The clock of the records: the time this model is read at. */
    public Clock clock() {
        return records.clock();
    }

    /** Until when the run leaves {@code step} be, having found it could not do it; empty when it does not, or no more. */
    public Optional<Instant> heldOff(Step step) {
        return Optional.ofNullable(heldOff.get(step.action())).filter(until -> until.isAfter(clock().instant()));
    }

    /** {@code step}'s last time. */
    public Optional<Done> last(Step step) {
        return records.last(step.key());
    }

    /** What {@code goal} says, a done-goal from this model's map and records rather than the run's. */
    public Answer answer(Goal goal) {
        return switch (goal) {
            case Goal.Done done -> Conditions.answer((Condition) () -> Readiness.met(done, map, records));
            case Goal.Of of -> of.answer();
        };
    }

    /** Whether {@code first} is tried before {@code then}, by {@code .over} directly or through other actions. */
    public boolean over(Do first, Do then) {
        return over.getOrDefault(first, Set.of()).contains(then);
    }

    /** Fills {@link #over} for {@code action}; {@code path} is the {@code .over} chain that led here. */
    private Set<Do> readOver(Do action, List<Do> path) {
        if (path.stream().anyMatch(on -> on == action)) throw cycle(path, action);
        Set<Do> read = over.get(action);
        if (read != null) return read;
        path.add(action);
        Set<Do> after = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Supplier<Do> named : action.over()) {
            Do then = named.get();
            if (then == null) continue;
            after.add(then);
            after.addAll(readOver(then, path));
        }
        path.removeLast();
        over.put(action, after);
        return after;
    }

    private IllegalArgumentException cycle(List<Do> path, Do again) {
        int from = 0;
        while (path.get(from) != again) from++;
        String around = path.subList(from, path.size()).stream().map(this::name).collect(Collectors.joining(" over "));
        return new IllegalArgumentException("These actions each say to be tried before the next, in a circle: " + around
                + " over " + name(again) + ". Take one .over out, so one of them comes first.");
    }

    private String name(Do action) {
        Step step = steps.get(action);
        return step == null ? "an action no screen holds" : step.key();
    }
}
