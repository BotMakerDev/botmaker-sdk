package com.botmaker.sdk.plugin.plan;

import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.bot.BotConstants.InputName;
import com.botmaker.sdk.internal.params.ParamCheck;
import com.botmaker.sdk.internal.params.ParamCheck.Test;
import com.botmaker.sdk.internal.plan.PlanModel;
import com.botmaker.sdk.internal.plan.StudioPlan;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Mark;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What the 🎯 Goals window changes, worked out with no window (2026-11-06): an action's {@code .over}, ★ or ⤓, a goal
 * moved to another tier or to a tier of its own, and a new goal made from a {@code Need} shortcut over one of the
 * bot's facts. Each gives the value to write; {@link PlanWrites} writes it, through the contract, so Studio's grammar
 * writes the Java.
 */
public final class PlanEdits {

    private PlanEdits() {}

    /**
     * {@code plan} with the goal {@code goal} (its constant's name) moved: into tier {@code to}, or, when
     * {@code newTier}, into a tier of its own put at {@code to} — {@code tiers().size()} puts it last. The tiers it
     * leaves empty go; each goal is the reference {@code Goals.X}, as Studio reads and writes a tier.
     *
     * @throws IllegalArgumentException when {@code to} is no tier, or no place for a new one
     */
    public static Plan moved(Plan plan, String goal, int to, boolean newTier) {
        int size = plan.tiers().size();
        if (to < 0 || to > size || (!newTier && to == size)) {
            throw new IllegalArgumentException("The plan has no tier " + (to + 1) + ".");
        }
        Goal named = BotConstants.goal(goal);
        // Dropped on the tier it is in: a tier has no order, so nothing moves.
        if (!newTier && plan.tiers().get(to).goals().stream().anyMatch(g -> goal.equals(name(g)))) return plan;
        List<List<Goal>> tiers = new ArrayList<>();
        for (Plan.Tier tier : plan.tiers()) {
            tiers.add(new ArrayList<>(tier.goals().stream().filter(g -> !goal.equals(name(g))).toList()));
        }
        if (newTier) tiers.add(to, new ArrayList<>(List.of(named)));
        else tiers.get(to).add(named);
        return new Plan(tiers.stream().filter(t -> !t.isEmpty()).map(Plan.Tier::new).toList());
    }

    /** The first tier of {@code plan} holding the goal {@code goal}, from 0; -1 when none does. */
    public static int tierOf(Plan plan, String goal) {
        for (int i = 0; i < plan.tiers().size(); i++) {
            if (plan.tiers().get(i).goals().stream().anyMatch(g -> goal.equals(name(g)))) return i;
        }
        return -1;
    }

    /** The plan of one tier, {@code goal} alone: what {@code + Plan} writes. */
    public static Plan startedWith(String goal) {
        return Plan.of(Plan.tier(BotConstants.goal(goal)));
    }

    // ---- an action's order among those serving its goals: the cards' drags ----------------------------------

    /**
     * {@code action} with one {@code .over} more, naming {@code screen}.{@code field} as Studio's Java does, by
     * reference; itself when it names that one already.
     */
    static Do over(Do action, String screen, String field) {
        InputName named = new InputName(screen, field);
        // Read, a supplier gives the field's reference though typed as a Do.
        boolean already = action.over().stream().<Object>map(Supplier::get)
                .anyMatch(each -> each instanceof Input input && named.equals(BotConstants.inputName(input)));
        return already ? action : action.over(reference(screen, field));
    }

    /** {@code () -> Screens.<screen>.<field>}, as the grammar reads it: the field's reference wherever a Do is asked. */
    @SuppressWarnings("unchecked")
    static Supplier<Do> reference(String screen, String field) {
        Supplier<?> named = new Reference(screen, field);
        return (Supplier<Do>) named;
    }

    /** {@link #reference}: equal by the field it names, so two drags onto one row make equal actions. */
    private record Reference(String screen, String field) implements Supplier<Input> {
        @Override
        public Input get() {
            return BotConstants.input(screen, field);
        }
    }

    /** {@code action} put first, last, or neither. */
    static Do marked(Do action, Mark mark) {
        return new Do(action.input(), action.serves(), action.goalNeeds(), action.conditionNeeds(), mark == Mark.FIRST,
                mark == Mark.LAST, action.over(), action.isOnSight(), action.every(), action.daily(), action.isOnce(),
                action.until(), action.checks(), action.onFail(), action.takes());
    }

    /**
     * The planner's sentence when {@code map}, its action {@code constant}.{@code field} being {@code next}, has
     * actions each tried before the next in a circle; empty when it has none.
     */
    static Optional<String> refusedOver(ScreenGraph map, String constant, String field, Do next) {
        Map<String, MapScreen> screens = new LinkedHashMap<>(map.screens());
        MapScreen screen = screens.get(constant);
        if (screen == null) return Optional.empty();
        screens.put(constant, screen.withAction(field, next));
        return PlanModel.overCycle(StudioPlan.of(PlanModel.NO_PLAN, Map.of(), ScreenGraph.of(screens)).map());
    }

    /** The constant a tier's goal names, or null for one that names none. */
    static String name(Goal goal) {
        return BotConstants.goalName(goal).orElse(null);
    }

    /** A {@code Need} shortcut, as the {@code + Goal} picker offers it for a fact's type. */
    public enum Shortcut {
        AT_LEAST("at least", "AT_LEAST"),
        AT_MOST("at most", "AT_MOST"),
        ABOVE("above", "ABOVE"),
        BELOW("below", "BELOW"),
        EQUAL_TO("equal to", "IS"),
        IS("is true", ""),
        NOT("is false", "NOT");

        private final String label;
        private final String word;

        Shortcut(String label, String word) {
            this.label = label;
            this.word = word;
        }

        /** What the picker shows. */
        public String label() {
            return label;
        }

        /** Whether it takes a limit: all but {@link #IS} and {@link #NOT}. */
        public boolean takesLimit() {
            return this != IS && this != NOT;
        }
    }

    /** The shortcuts a fact of {@code type} takes: a whole number all four bounds, a flag is/not, a text equal-to. */
    public static List<Shortcut> shortcuts(String type) {
        return switch (bare(type)) {
            // Need's limits read back as an Integer or a String (2026-11-08): a long or a double is not offered.
            case "int", "Integer" -> List.of(Shortcut.AT_LEAST, Shortcut.AT_MOST, Shortcut.ABOVE, Shortcut.BELOW,
                    Shortcut.EQUAL_TO);
            case "boolean", "Boolean" -> List.of(Shortcut.IS, Shortcut.NOT);
            case "String" -> List.of(Shortcut.EQUAL_TO);
            default -> List.of();
        };
    }

    /** {@code text} as the limit a fact of {@code type} takes; empty when it is none. */
    public static Optional<Object> limit(String type, String text) {
        if (text == null) return Optional.empty();
        return switch (bare(type)) {
            case "int", "Integer" -> {
                try {
                    yield Optional.of(Integer.valueOf(text.strip()));
                } catch (NumberFormatException notOne) {
                    yield Optional.empty();
                }
            }
            case "String" -> Optional.of(text);
            default -> Optional.empty();
        };
    }

    /**
     * The goal {@code Goal.of(Need.<shortcut>(fact, limit))}, {@code fact} its method reference's text
     * ({@code Progress::gold}), written back as {@code Need} writes it.
     */
    public static Goal fromNeed(String fact, Shortcut shortcut, Object limit) {
        if (fact == null || !fact.contains("::")) throw new IllegalArgumentException("A fact is named Progress::gold.");
        if (shortcut.takesLimit() && limit == null) throw new IllegalArgumentException(shortcut.label() + " takes a limit.");
        Condition check = switch (shortcut) {
            case AT_LEAST -> need(fact, Test.AT_LEAST, limit);
            case AT_MOST -> need(fact, Test.AT_MOST, limit);
            case ABOVE -> new Condition.Not(need(fact, Test.AT_MOST, limit));
            case BELOW -> new Condition.Not(need(fact, Test.AT_LEAST, limit));
            case EQUAL_TO -> need(fact, Test.IS, limit);
            case IS -> need(fact, Test.IS_TRUE, null);
            case NOT -> new Condition.Not(need(fact, Test.IS_TRUE, null));
        };
        return Goal.of(check);
    }

    private static ParamCheck need(String fact, Test test, Object value) {
        ParamCheck check = ScreenTypes.test(fact, test, value);
        return new ParamCheck(check.when(), check.test(), check.value(), true);
    }

    /**
     * The constant a goal from {@link #fromNeed} is named: {@code GOLD_AT_LEAST_100}, {@code GUILD_JOINED},
     * {@code NOT_GUILD_JOINED}; never one of {@code taken}.
     */
    public static String goalName(String fact, Shortcut shortcut, Object limit, Set<String> taken) {
        String method = fact.substring(fact.indexOf("::") + 2);
        String base = snake(method);
        if (shortcut == Shortcut.NOT) base = "NOT_" + base;
        else if (shortcut.takesLimit()) {
            String value = snake(String.valueOf(limit).replaceFirst("^-", "minus ")).replaceAll("[^A-Z0-9_]", "");
            base = base + "_" + shortcut.word + (value.isEmpty() ? "" : "_" + value);
        }
        if (base.isEmpty() || !Character.isJavaIdentifierStart(base.charAt(0))) base = "GOAL_" + base;
        String name = base;
        for (int i = 2; taken.contains(name); i++) name = base + "_" + i;
        return name;
    }

    /** {@code guildJoined} as {@code GUILD_JOINED}; a run of anything else one underscore. */
    private static String snake(String text) {
        String words = text.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]+", "_");
        return words.replaceAll("^_+|_+$", "").toUpperCase(Locale.ROOT);
    }

    private static String bare(String type) {
        if (type == null) return "";
        String plain = type.strip();
        return plain.substring(plain.lastIndexOf('.') + 1);
    }
}
