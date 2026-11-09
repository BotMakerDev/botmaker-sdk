package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.TypeHolder;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.Held;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Need;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Retry;
import com.botmaker.sdk.internal.params.ParamCheck;
import com.botmaker.sdk.internal.params.ParamCheck.Test;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * How Studio reads and writes what a plan is made of (2026-11-06): {@code Plan.of(Plan.tier(…), …)}, a goal
 * ({@code Goal.of(…)}, {@code Goal.done(() -> …).within(…)}), what a goal or an action needs of a fact
 * ({@code Need.atLeast(Progress::gold, 100)}) and an action of a screen ({@code Do.press(…)} and its links).
 *
 * <p>A goal named inside an action or a tier, {@code Goals.ENOUGH_GOLD}, is a reference to that constant
 * ({@code BotConstants.goal}); an action named inside another's {@code .over(() -> …)} or a {@code Goal.done(() -> …)}
 * is a reference to that field, as an input is ({@code BotConstants.input}). A condition crosses as its method
 * reference's text, as a screen's {@code .when(…)} does, or as the test of a fact it is.
 */
public final class PlanTypes implements TypeHolder {

    private PlanTypes() {}

    @Override
    public List<PluginType<?>> types() {
        return List.of();
    }

    @Override
    public List<ComponentType<?>> components() {
        return List.of(PLAN_SHAPE, TIER_SHAPE, GOAL_OF_SHAPE, GOAL_DONE_SHAPE, DO_SHAPE, RETRY_AGAIN_SHAPE,
                RETRY_LATER_SHAPE, RETRY_NEVER_SHAPE, NEED_AT_LEAST_SHAPE, NEED_AT_MOST_SHAPE, NEED_EQUAL_TO_SHAPE,
                NEED_IS_SHAPE, NEED_ABOVE_SHAPE, NEED_BELOW_SHAPE, NEED_NOT_SHAPE);
    }

    /** The parts held as something the factory or the link does not take: a condition as its text, a fact as its. */
    private static final class Parts {
        static final Held<Goal.Of, Object> CHECK = Held.as(Object.class, goal -> held(goal.check()));
        static final Held<Do, Object> UNTIL = Held.as(Object.class, action -> held(action.until()));
        static final Held<Do, Object> CHECKS = Held.as(Object.class, action -> held(action.checks()));
        static final Held<Do, List<Object>> NEEDS = Held.list(Object.class,
                action -> action.conditionNeeds().stream().map(PlanTypes::held).toList());

        static final Held<ParamCheck, String> FACT = Held.as(String.class, ScreenTypes::factOf);
        static final Held<ParamCheck, Object> LIMIT = Held.as(Object.class, ParamCheck::value);
        static final Held<Condition.Not, String> NOT_FACT = Held.as(String.class,
                not -> not.condition() instanceof ParamCheck check ? ScreenTypes.factOf(check) : null);
        static final Held<Condition.Not, Object> NOT_LIMIT = Held.as(Object.class,
                not -> not.condition() instanceof ParamCheck check ? check.value() : null);
    }

    // ---- a plan ----------------------------------------------------------------------------------------

    /** {@code Plan.of(Plan.tier(…), …)}: the tiers, most important first. */
    public static final DeclaredCall<Plan> PLAN_SHAPE = ComponentType.part(Plan.class)
            .writtenAsEach(Plan::of, Plan::tiers);

    /** {@code Plan.tier(Goals.A, Goals.B)}: goals with no order among them, each a reference to its constant. */
    public static final DeclaredCall<Plan.Tier> TIER_SHAPE = ComponentType.part(Plan.Tier.class)
            .writtenAsEach(Plan::tier, Plan.Tier::goals);

    /** {@code Goal.of(check)}: a method reference's text, or the test of a fact. */
    public static final DeclaredCall<Goal.Of> GOAL_OF_SHAPE = ComponentType.part(Goal.Of.class)
            .<BooleanSupplier, Goal>writtenAs(Goal::of, Parts.CHECK.part(PlanTypes::condition));

    /** {@code Goal.done(() -> Screens.ARENA.fight)} and {@code .within(Duration)} when its last time is bounded. */
    public static final DeclaredCall<Goal.Done> GOAL_DONE_SHAPE = ComponentType.part(Goal.Done.class)
            .writtenAs(Goal::done);

    // ---- an action -------------------------------------------------------------------------------------

    /**
     * {@code Do.press(input)} and each link it has; {@code .serves(…)}, {@code .over(() -> …)} and the flags are
     * {@code @Link}s, the two {@code .needs(…)}, {@code .until(…)} and {@code .checks(…)} hold conditions as text.
     */
    public static final DeclaredCall<Do> DO_SHAPE = ComponentType.part(Do.class)
            .writtenAs(Do::press)
            .<Goal>withEach(Do::needs, Do::goalNeeds)
            .<Condition>withEach(Do::needs, Parts.NEEDS.part(PlanTypes::conditions))
            .<Condition>with(Do::until, Parts.UNTIL.part(PlanTypes::condition))
            .<Condition>with(Do::checks, Parts.CHECKS.part(PlanTypes::condition));

    /** {@code Retry.again(times)}. */
    public static final DeclaredCall<Retry.Again> RETRY_AGAIN_SHAPE = ComponentType.part(Retry.Again.class)
            .writtenAs(Retry::again);

    /** {@code Retry.later(Duration)}. */
    public static final DeclaredCall<Retry.Later> RETRY_LATER_SHAPE = ComponentType.part(Retry.Later.class)
            .writtenAs(Retry::later, Retry.Later::after);

    /** {@code Retry.never()}. */
    public static final DeclaredCall<Retry.Never> RETRY_NEVER_SHAPE = ComponentType.part(Retry.Never.class)
            .<Retry>writtenAs(Retry::never);

    // ---- what is needed of a fact ----------------------------------------------------------------------

    /*
     * Need.atLeast(Progress::gold, 100) is the same test as Params.when(Progress::gold).atLeast(100), marked as written
     * by Need (ParamCheck.byNeed) so each is written back as it was: the host writes a value by the call that rebuilds
     * it equal. Each is built here, not by calling Need, which refuses a fact that is no method of a parameter — and
     * Studio holds a fact as its method reference's text. Need.above, below and not are a Condition.Not of one.
     */

    /** {@code Need.atLeast(Progress::gold, 100)}. */
    public static final DeclaredCall<ParamCheck> NEED_AT_LEAST_SHAPE = ComponentType.part(ParamCheck.class)
            .<Params.Of<Object, Integer>, Integer, Condition>writtenAs(Need::atLeast, Parts.FACT.part(),
                    Parts.LIMIT.part())
            .builtFrom(parts -> need(parts.get(Parts.FACT), Test.AT_LEAST, parts.get(Parts.LIMIT)));

    /** {@code Need.atMost(Progress::energy, 0)}. */
    public static final DeclaredCall<ParamCheck> NEED_AT_MOST_SHAPE = ComponentType.part(ParamCheck.class)
            .<Params.Of<Object, Integer>, Integer, Condition>writtenAs(Need::atMost, Parts.FACT.part(),
                    Parts.LIMIT.part())
            .builtFrom(parts -> need(parts.get(Parts.FACT), Test.AT_MOST, parts.get(Parts.LIMIT)));

    /** {@code Need.equalTo(Progress::rank, "gold")}. */
    public static final DeclaredCall<ParamCheck> NEED_EQUAL_TO_SHAPE = ComponentType.part(ParamCheck.class)
            .<Params.Of<Object, String>, String, Condition>writtenAs(Need::equalTo, Parts.FACT.part(),
                    Parts.LIMIT.part())
            .builtFrom(parts -> need(parts.get(Parts.FACT), Test.IS, parts.get(Parts.LIMIT)));

    /** {@code Need.is(Progress::guildJoined)}. */
    public static final DeclaredCall<ParamCheck> NEED_IS_SHAPE = ComponentType.part(ParamCheck.class)
            .<Params.Of<Object, Boolean>, Condition>writtenAs(Need::is, Parts.FACT.part())
            .builtFrom(parts -> need(parts.get(Parts.FACT), Test.IS_TRUE, null));

    /** {@code Need.above(Progress::gold, 100)}: not at most. */
    public static final DeclaredCall<Condition.Not> NEED_ABOVE_SHAPE = ComponentType.part(Condition.Not.class)
            .<Params.Of<Object, Integer>, Integer, Condition>writtenAs(Need::above, Parts.NOT_FACT.part(),
                    Parts.NOT_LIMIT.part())
            .builtFrom(parts -> not(need(parts.get(Parts.NOT_FACT), Test.AT_MOST, parts.get(Parts.NOT_LIMIT))));

    /** {@code Need.below(Progress::gold, 100)}: not at least. */
    public static final DeclaredCall<Condition.Not> NEED_BELOW_SHAPE = ComponentType.part(Condition.Not.class)
            .<Params.Of<Object, Integer>, Integer, Condition>writtenAs(Need::below, Parts.NOT_FACT.part(),
                    Parts.NOT_LIMIT.part())
            .builtFrom(parts -> not(need(parts.get(Parts.NOT_FACT), Test.AT_LEAST, parts.get(Parts.NOT_LIMIT))));

    /** {@code Need.not(Progress::guildJoined)}: not true. */
    public static final DeclaredCall<Condition.Not> NEED_NOT_SHAPE = ComponentType.part(Condition.Not.class)
            .<Params.Of<Object, Boolean>, Condition>writtenAs(Need::not, Parts.NOT_FACT.part())
            .builtFrom(parts -> not(need(parts.get(Parts.NOT_FACT), Test.IS_TRUE, null)));

    /** {@code fact}'s test as {@code Need} writes it; null for a blank fact, which names no parameter. */
    private static ParamCheck need(String fact, Test test, Object value) {
        if (fact == null || fact.isBlank()) return null;
        ParamCheck check = ScreenTypes.test(fact, test, value);
        return new ParamCheck(check.when(), check.test(), check.value(), true);
    }

    private static Condition.Not not(ParamCheck check) {
        return check == null ? null : new Condition.Not(check);
    }

    // ---- conditions ------------------------------------------------------------------------------------

    /** What a condition part gives to be written, as {@link ScreenTypes#held} gives it. */
    static Object held(BooleanSupplier condition) {
        return ScreenTypes.held(condition);
    }

    /** The condition a part holds: a method reference's text as a name, a typed condition as itself, else none. */
    static Condition condition(Object held) {
        return switch (held) {
            case Condition typed -> typed;
            case String source when !source.isBlank() -> new ScreenTypes.NamedCondition(source.strip());
            case null, default -> null;
        };
    }

    private static List<Condition> conditions(List<Object> held) {
        return held == null ? null : held.stream().map(PlanTypes::condition).toList();
    }
}
