package com.botmaker.sdk.api.plan;

import java.util.List;

/**
 * What matters most, as an ordered list of tiers of goals. Between tiers the order is strict; inside a tier there is
 * none. It says nothing of how: a plan acts only through a screen's actions, a {@code Flow} only through its activities.
 *
 * <pre>{@code
 * public static final Plan DAILY = Plan.of(
 *         Plan.tier(Goals.KEEP_ENERGY),
 *         Plan.tier(Goals.DAILY_CLAIMED, Goals.ENOUGH_GOLD),
 *         Plan.tier(Goals.LEVEL_UP));
 * }</pre>
 *
 * @param tiers its tiers, what matters most first
 */
public record Plan(List<Tier> tiers) implements Routine {

    public Plan {
        tiers = List.copyOf(tiers);
        if (tiers.isEmpty()) throw new IllegalArgumentException("A plan holds at least one tier of goals.");
    }

    /** The plan of {@code tiers}, what matters most first. */
    public static Plan of(Tier... tiers) {
        return new Plan(List.of(tiers));
    }

    /** One tier of {@code goals}, in no order. */
    public static Tier tier(Goal... goals) {
        for (Goal goal : goals) {
            if (goal == null) {
                throw new IllegalArgumentException("A tier was given no goal: its constant is declared below the plan, "
                        + "or read while its own class still loads.");
            }
        }
        return new Tier(List.of(goals));
    }

    /**
     * Goals with no order among them.
     *
     * @param goals its goals
     */
    public record Tier(List<Goal> goals) {

        public Tier {
            goals = List.copyOf(goals);
            if (goals.isEmpty()) throw new IllegalArgumentException("A tier of a plan holds at least one goal.");
        }
    }
}
