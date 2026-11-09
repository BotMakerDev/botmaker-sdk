package com.botmaker.sdk.internal.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A plan, its goals and the map's actions as Studio reads them out of the bot's Java, tied together as a run's are
 * (2026-11-06). Read, each names another by reference: an action's {@code .serves(Goals.X)} a goal reference, its
 * {@code .over(() -> Screens.ARENA.fight)} and a {@code Goal.done(() -> …)} a reference to an input field, its input
 * the screen's field. Here each reference is the value it names, so the planner reads them as it does in a run: a goal
 * is the bot's constant, an action the very {@code Do} its screen holds.
 *
 * @param plan  the plan, each goal of its tiers the bot's constant
 * @param map   the map, each action's references resolved
 * @param names each goal's constant, by identity
 */
public record StudioPlan(Plan plan, ScreenGraph map, Map<Goal, String> names) {

    /**
     * {@code plan} over {@code goals} (constant to value, as {@code Goals.java} declares them) and {@code map}. A goal
     * a reference names that {@code goals} lacks stays the reference, which answers unknown.
     */
    public static StudioPlan of(Plan plan, Map<String, Goal> goals, ScreenGraph map) {
        Map<String, Map<String, Do>> actions = new LinkedHashMap<>();
        Map<String, Goal> resolved = new LinkedHashMap<>();
        Map<Goal, String> names = new IdentityHashMap<>();
        goals.forEach((name, goal) -> {
            Goal each = switch (goal) {
                case Goal.Done done -> new Goal.Done(lazily(done.action(), actions), done.within(), done.since());
                case Goal.Of of -> of;
            };
            resolved.put(name, each);
            names.put(each, name);
        });
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        map.screens().forEach((constant, screen) -> {
            Map<String, Do> held = new LinkedHashMap<>();
            screen.actions().forEach((field, action) -> held.put(field, resolve(action, map, resolved, actions)));
            actions.put(constant, held);
            screens.put(constant, new MapScreen(screen.setup(), screen.inputs(), screen.readouts(), screen.marks(),
                    held));
        });
        Plan tiers = new Plan(plan.tiers().stream()
                .map(tier -> new Plan.Tier(tier.goals().stream().map(goal -> goal(goal, resolved, names)).toList()))
                .toList());
        return new StudioPlan(tiers, ScreenGraph.of(screens), names);
    }

    /** {@code goal}'s constant: its name, else what its reference names, else empty. */
    public Optional<String> name(Goal goal) {
        return Optional.ofNullable(names.get(goal)).or(() -> BotConstants.goalName(goal));
    }

    private static Do resolve(Do action, ScreenGraph map, Map<String, Goal> goals,
                              Map<String, Map<String, Do>> actions) {
        BotConstants.InputName pressed = BotConstants.inputName(action.input());
        Input input = pressed == null ? action.input()
                : map.input(pressed.screen(), pressed.field()).orElse(action.input());
        return new Do(input, action.serves().stream().map(goal -> goal(goal, goals, null)).toList(),
                action.goalNeeds().stream().map(goal -> goal(goal, goals, null)).toList(), action.conditionNeeds(),
                action.isFirst(), action.isLast(), action.over().stream().map(over -> lazily(over, actions)).toList(),
                action.isOnSight(), action.every(), action.daily(), action.isOnce(), action.until(), action.checks(),
                action.onFail());
    }

    /** The bot's goal a reference names, else the goal itself; noted by its name when {@code names} is given. */
    private static Goal goal(Goal goal, Map<String, Goal> goals, Map<Goal, String> names) {
        Optional<String> named = BotConstants.goalName(goal);
        Goal real = named.map(goals::get).orElse(goal);
        if (names != null && named.isPresent()) names.putIfAbsent(real, named.get());
        return real;
    }

    /**
     * {@code named}, an action read as a reference to its field, as the map's action there — read when asked, since
     * actions name each other in any order; one no screen holds answers null, which the planner skips.
     */
    private static Supplier<Do> lazily(Supplier<Do> named, Map<String, Map<String, Do>> actions) {
        return new Named(named, actions);
    }

    /** {@link #lazily}: equal by what it names, so an action rebuilt equal to another keeps its order. */
    private record Named(Supplier<?> named, Map<String, Map<String, Do>> actions) implements Supplier<Do> {
        @Override
        public Do get() {
            Object value = named.get();
            if (value instanceof Do action) return action;
            BotConstants.InputName name = value instanceof Input input ? BotConstants.inputName(input) : null;
            return name == null ? null : actions.getOrDefault(name.screen(), Map.of()).get(name.field());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Named that && named.equals(that.named);
        }

        @Override
        public int hashCode() {
            return named.hashCode();
        }
    }
}
