package com.botmaker.sdk.plugin.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.internal.params.ParamCheck;
import com.botmaker.sdk.internal.plan.PlanModel;
import com.botmaker.sdk.internal.plan.Planner;
import com.botmaker.sdk.internal.plan.Round;
import com.botmaker.sdk.internal.plan.Round.Considered;
import com.botmaker.sdk.internal.plan.Round.Step;
import com.botmaker.sdk.internal.plan.Round.Why;
import com.botmaker.sdk.internal.plan.StudioPlan;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the Goals view shows, worked out with no window (2026-11-06, layout A): one card per goal of the plan, each with
 * the actions that serve it in the order {@link Planner#round} would take them, and for a row the way to its screen.
 * Built from a {@link PlanModel} over {@link StudioPlan}'s map, so what it says is what a run starting where the
 * model is would do.
 */
public final class GoalsViewModel {

    private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("HH:mm");

    private final PlanModel model;
    private final StudioPlan plan;
    private final Round round;
    private final List<Card> cards;

    /** A goal and what serves it. {@code fill} is what it asks, {@code met} whether it is met now. */
    public record Card(Goal goal, String name, int tier, String fill, Met met, List<Row> rows) {}

    /** Whether a goal is met, as far as Studio can tell: a fact is a run's, so most read unknown here. */
    public enum Met { YES, NO, UNKNOWN }

    /** Where an action is put first or last, or neither: the ★ and ⤓ of a row. */
    public enum Mark { FIRST, LAST, NONE }

    /**
     * One action looked at for a goal.
     *
     * @param step     the action, its screen and field
     * @param why      what the planner found
     * @param mark     its {@code .first()} or {@code .last()}
     * @param said     {@code why} in a few words
     * @param crossing how many screens a walk shows on its way there, the action's own included; -1 with no way
     * @param walk     what the walk there takes, in ms; -1 with no way
     */
    public record Row(Step step, Why why, Mark mark, String said, int crossing, long walk) {

        /** {@code SCREEN.field}: the action as {@link com.botmaker.sdk.internal.plan.PlanValues#ACTION} names it. */
        public String name() {
            return step.screen() + "." + step.field();
        }

        /** Whether the planner could take it now. */
        public boolean possible() {
            return !(why instanceof Why.Waiting || why instanceof Why.Blocked || why instanceof Why.CannotMeet
                    || why instanceof Why.TooDeep);
        }
    }

    /**
     * The way a row's walk takes, screen by screen from where the game is: only the screens crossed, in order, the
     * last one the action's. {@code edges} is how each leads to the next, one fewer than {@code screens}.
     */
    public record Route(List<String> screens, List<Edge> edges, String action) {}

    private GoalsViewModel(PlanModel model, StudioPlan plan) {
        this.model = model;
        this.plan = plan;
        this.round = Planner.round(model);
        this.cards = readCards();
    }

    /** The view of {@code model}, whose plan and map are {@code plan}'s. */
    public static GoalsViewModel of(PlanModel model, StudioPlan plan) {
        return new GoalsViewModel(model, plan);
    }

    /** The round the view shows. */
    public Round round() {
        return round;
    }

    /** One card per goal of the plan, tier by tier. */
    public List<Card> cards() {
        return cards;
    }

    /** The route of {@code row}: empty when no way leads there now. */
    public Optional<Route> route(Row row) {
        String at = model.at();
        String to = row.step().screen();
        if (at == null || at.equals(to)) {
            return Optional.of(new Route(List.of(to), List.of(), row.step().field()));
        }
        return Planner.way(model, at, to).map(edges -> {
            List<String> screens = new ArrayList<>(List.of(at));
            edges.forEach(edge -> screens.add(edge.to()));
            return new Route(List.copyOf(screens), List.copyOf(edges), row.step().field());
        });
    }

    /** The row of the action {@code SCREEN.field} under the first card that has one, or empty. */
    public Optional<Row> row(String action) {
        return cards.stream().flatMap(card -> card.rows().stream()).filter(row -> row.name().equals(action))
                .findFirst();
    }

    private List<Card> readCards() {
        Map<Goal, List<Row>> rows = new LinkedHashMap<>();
        for (Considered considered : round.considered()) {
            rows.computeIfAbsent(considered.goal(), g -> new ArrayList<>()).add(row(considered));
        }
        List<Card> out = new ArrayList<>();
        List<Goal> seen = new ArrayList<>();
        for (int tier = 0; tier < model.plan().tiers().size(); tier++) {
            for (Goal goal : model.plan().tiers().get(tier).goals()) {
                if (seen.contains(goal)) continue;
                seen.add(goal);
                out.add(new Card(goal, plan.name(goal).orElse("(a goal)"), tier, fill(goal), met(goal),
                        List.copyOf(rows.getOrDefault(goal, List.of()))));
            }
        }
        return List.copyOf(out);
    }

    private Row row(Considered considered) {
        Step step = considered.step();
        Mark mark = step.action().isFirst() ? Mark.FIRST : step.action().isLast() ? Mark.LAST : Mark.NONE;
        String at = model.at();
        Optional<List<Edge>> way = at == null || at.equals(step.screen()) ? Optional.of(List.of())
                : Planner.way(model, at, step.screen());
        ScreenGraph map = model.map();
        int crossing = way.map(edges -> edges.size() + 1).orElse(-1);
        long walk = way.map(edges -> edges.stream().mapToLong(map::cost).sum()).orElse(-1L);
        return new Row(step, considered.why(), mark, said(considered.why()), crossing, walk);
    }

    private Met met(Goal goal) {
        return switch (model.answer(goal)) {
            case YES -> Met.YES;
            case NO -> Met.NO;
            case UNKNOWN -> Met.UNKNOWN;
        };
    }

    /** {@code why} in a few words, as a row says it. */
    String said(Why why) {
        return switch (why) {
            case Why.Ready ignored -> "ready";
            case Why.LastResort ignored -> "last resort";
            case Why.Unknown ignored -> "known in a run";
            case Why.Needs needs -> "after " + plan.name(needs.goal()).orElse("a goal")
                    + (needs.actions() > 1 ? " (" + needs.actions() + " actions)" : "");
            case Why.Waiting waiting -> "waits until " + time(waiting.until());
            case Why.Blocked blocked -> switch (blocked.block()) {
                case INPUT -> "input not pressable";
                case CONDITION -> "a need says no";
                case ENDED -> "its until holds";
                case DONE_FOR_GOOD -> "done, once";
                case GIVEN_UP -> "given up";
                case NO_WAY_THERE -> "no way there";
            };
            case Why.CannotMeet cannot -> "needs " + plan.name(cannot.goal()).orElse("a goal") + ", unmet";
            case Why.TooDeep deep -> "needs " + plan.name(deep.goal()).orElse("a goal") + ", too deep";
        };
    }

    private String time(Instant at) {
        return AT.format(at.atZone(model.clock().getZone()));
    }

    /** What {@code goal} asks, as its card says it: {@code gold ≥ 100}, {@code ARENA.fight within 1h}. */
    String fill(Goal goal) {
        return switch (goal) {
            case Goal.Done done -> {
                String action = Optional.ofNullable(done.action().get())
                        .flatMap(each -> model.actions().stream().filter(step -> step.action() == each).findFirst())
                        .map(step -> step.screen() + "." + step.field()).orElse("an action");
                yield action + " done" + (done.within() == null ? "" : " within " + duration(done.within()));
            }
            case Goal.Of of -> condition(of.check());
        };
    }

    private static String condition(Condition check) {
        if (check instanceof Condition.Not not) return "not " + condition(not.condition());
        if (check instanceof ParamCheck param) {
            String fact = ScreenTypes.factOf(param);
            String name = fact.contains("::") ? fact.substring(fact.indexOf("::") + 2) : fact;
            return switch (param.test()) {
                case AT_LEAST -> name + " ≥ " + param.value();
                case AT_MOST -> name + " ≤ " + param.value();
                case IS -> name + " = " + param.value();
                case IS_TRUE -> name;
            };
        }
        String source = ScreenTypes.sourceOf(check);
        return source == null || source.isBlank() ? "its check" : source;
    }

    private static String duration(Duration d) {
        if (d.toDaysPart() > 0 && d.toHoursPart() == 0) return d.toDays() + "d";
        if (d.toHours() > 0 && d.toMinutesPart() == 0) return d.toHours() + "h";
        return d.toMinutes() + "min";
    }
}
