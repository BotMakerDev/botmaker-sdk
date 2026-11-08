package com.botmaker.sdk.plugin.plan;

import com.botmaker.sdk.internal.plan.Round.Why;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Card;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Row;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What the plan view shows, worked out with no window (2026-11-06): the plan's tiers, most important first, each a row
 * of goal chips. A chip is met (struck), waiting with when it comes due (⏳), open, or unknown until a run reads its
 * facts. Read from the Goals view's cards, so a chip says what the planner would make of its goal now.
 */
public final class PlanViewModel {

    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("HH:mm");

    /** How a goal stands. */
    public enum State { MET, WAITING, OPEN, UNKNOWN }

    /**
     * One goal of a tier.
     *
     * @param name  its constant
     * @param state how it stands
     * @param due   when it comes due, for a waiting one
     * @param said  {@code state} in a few words, as the chip shows it
     */
    public record Chip(String name, State state, Optional<Instant> due, String said) {}

    /** One tier: its place, from 0, and its goals. */
    public record Tier(int index, List<Chip> chips) {}

    private final List<Tier> tiers;

    private PlanViewModel(List<Tier> tiers) {
        this.tiers = tiers;
    }

    /** The tiers of {@code goals}'s plan, its cards' states read as the planner reads them. */
    public static PlanViewModel of(GoalsViewModel goals, ZoneId zone) {
        List<List<Chip>> rows = new ArrayList<>();
        for (Card card : goals.cards()) {
            while (rows.size() <= card.tier()) rows.add(new ArrayList<>());
            rows.get(card.tier()).add(chip(card, zone));
        }
        List<Tier> tiers = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) tiers.add(new Tier(i, List.copyOf(rows.get(i))));
        return new PlanViewModel(List.copyOf(tiers));
    }

    /** The tiers, most important first; a goal in two tiers shows in the first only. */
    public List<Tier> tiers() {
        return tiers;
    }

    /** The chip of {@code goal}, or empty. */
    public Optional<Chip> chip(String goal) {
        return tiers.stream().flatMap(t -> t.chips().stream()).filter(c -> c.name().equals(goal)).findFirst();
    }

    private static Chip chip(Card card, ZoneId zone) {
        if (card.met() == GoalsViewModel.Met.YES) return new Chip(card.name(), State.MET, Optional.empty(), "met");
        Optional<Instant> due = due(card.rows());
        if (due.isPresent()) {
            return new Chip(card.name(), State.WAITING, due, "⏳ " + AT.format(due.get().atZone(zone)));
        }
        if (card.met() == GoalsViewModel.Met.UNKNOWN) {
            return new Chip(card.name(), State.UNKNOWN, Optional.empty(), "known in a run");
        }
        return new Chip(card.name(), State.OPEN, Optional.empty(), "to do");
    }

    /**
     * When a goal comes due, as the planner passes it over: nothing can be done for it now and something only waits;
     * a last resort does not count while a better action only waits. Empty otherwise.
     */
    static Optional<Instant> due(List<Row> rows) {
        boolean betterWaits = rows.stream()
                .anyMatch(r -> r.why() instanceof Why.Waiting && !r.step().action().isLast());
        boolean doable = rows.stream().filter(Row::possible).findFirst()
                .filter(r -> !r.step().action().isLast() || !betterWaits).isPresent();
        if (doable) return Optional.empty();
        return rows.stream().map(Row::why).filter(Why.Waiting.class::isInstance).map(Why.Waiting.class::cast)
                .map(Why.Waiting::until).min(Comparator.naturalOrder());
    }
}
