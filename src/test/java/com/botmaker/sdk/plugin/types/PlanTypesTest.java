package com.botmaker.sdk.plugin.types;

import com.botmaker.java.grammar.BotNames;
import com.botmaker.java.grammar.JavaValue;
import com.botmaker.java.grammar.SourceNode;
import com.botmaker.java.grammar.ValueGrammar;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Retry;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.params.ParamCheck;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A plan, its goals and a screen's actions read out of the bot's Java and written back the same. */
class PlanTypesTest {

    private static final String SCREENS = "com.botmaker.sdk.api.screens.";
    private static final String PLAN = "com.botmaker.sdk.api.plan.";

    private static final ValueGrammar GRAMMAR = ValueGrammar.of(List.of(), List.of(PlanTypes.PLAN_SHAPE,
            PlanTypes.TIER_SHAPE, PlanTypes.GOAL_OF_SHAPE, PlanTypes.GOAL_DONE_SHAPE, PlanTypes.DO_SHAPE,
            PlanTypes.RETRY_AGAIN_SHAPE, PlanTypes.RETRY_LATER_SHAPE, PlanTypes.RETRY_NEVER_SHAPE,
            PlanTypes.NEED_AT_LEAST_SHAPE, PlanTypes.NEED_AT_MOST_SHAPE, PlanTypes.NEED_EQUAL_TO_SHAPE,
            PlanTypes.NEED_IS_SHAPE, PlanTypes.NEED_ABOVE_SHAPE, PlanTypes.NEED_BELOW_SHAPE, PlanTypes.NEED_NOT_SHAPE,
            ScreenTypes.WHEN_SHAPE, ScreenTypes.AT_LEAST_SHAPE, ScreenTypes.AT_MOST_SHAPE, ScreenTypes.IS_SHAPE,
            ScreenTypes.IS_TRUE_SHAPE));

    /** The bot's goals and screens' fields, as the core's lookup names them: {@code Goals.X}, {@code Screens.S.f}. */
    private static final BotNames NAMES = new BotNames() {
        @Override
        public Optional<Object> named(QualifiedName written) {
            String[] parts = written.getFullyQualifiedName().split("\\.");
            if (parts.length == 2 && parts[0].equals("Goals")) return Optional.of(BotConstants.goal(parts[1]));
            if (parts.length == 3 && parts[0].equals("Screens")) {
                return Optional.of(BotConstants.input(parts[1], parts[2]));
            }
            return Optional.empty();
        }

        @Override
        public Optional<JavaValue> spell(Object value) {
            if (value instanceof Goal goal) {
                return BotConstants.goalName(goal).flatMap(name -> JavaValue.parse("Goals." + name));
            }
            BotConstants.InputName input = value instanceof Input in ? BotConstants.inputName(in) : null;
            return input == null ? Optional.empty() : JavaValue.parse("Screens." + input.screen() + "." + input.field());
        }
    };

    private static Object read(Class<?> type, String java) {
        return GRAMMAR.read(type, SourceNode.parse(java).orElseThrow(), NAMES)
                .orElseThrow(() -> new AssertionError("read nothing of " + java));
    }

    /** An interface nothing builds itself reads by the calls that build its kinds. */
    @Test
    void aRetryReadsAsTheKindItsCallBuilds() {
        assertEquals(Retry.never(), read(Retry.class, SCREENS + "Retry.never()"));
        assertEquals(Retry.again(3), read(Retry.class, SCREENS + "Retry.again(3)"));
    }

    /** {@code value} written, then read again: what the editor keeps of it. */
    private static Object again(Class<?> type, Object value) {
        String java = GRAMMAR.write(type, value, NAMES).orElseThrow(() -> new AssertionError("wrote nothing of " + value))
                .source();
        return read(type, qualified(java));
    }

    /** {@code java} with the SDK's classes named in full, as a file names them by its imports. */
    private static String qualified(String java) {
        return java.replaceAll("(?<![.\\w])(Do|Retry)\\.", SCREENS + "$1.")
                .replaceAll("(?<![.\\w])(Goal|Need|Plan)\\.", PLAN + "$1.");
    }

    @Test
    void aGoalOfANeedIsReadAsTheTestWrittenByNeed() {
        Goal.Of goal = (Goal.Of) read(Goal.class, PLAN + "Goal.of(" + PLAN + "Need.atLeast(Progress::gold, 100))");
        ParamCheck check = assertInstanceOf(ParamCheck.class, goal.check());
        assertEquals(ParamCheck.Test.AT_LEAST, check.test());
        assertEquals(100, check.value());
        assertTrue(check.byNeed(), "written by Need, so written back by Need");
        assertEquals("Progress::gold", ScreenTypes.factOf(check));
        assertEquals(goal, again(Goal.class, goal));
        String java = GRAMMAR.write(Goal.class, goal, NAMES).orElseThrow().source();
        assertTrue(java.contains("Need.atLeast(Progress::gold, 100)"), java);
    }

    @Test
    void theNegatedNeedsReadAsWhatTheyNegate() {
        for (String need : List.of("above(Progress::gold, 5)", "below(Progress::gold, 5)", "not(Progress::full)")) {
            Goal.Of goal = (Goal.Of) read(Goal.class, PLAN + "Goal.of(" + PLAN + "Need." + need + ")");
            Condition.Not not = assertInstanceOf(Condition.Not.class, goal.check(), need);
            assertTrue(((ParamCheck) not.condition()).byNeed(), need);
            assertEquals(goal, again(Goal.class, goal), need);
            assertTrue(GRAMMAR.write(Goal.class, goal, NAMES).orElseThrow().source().contains("Need." + need), need);
        }
    }

    @Test
    void anActionReadsEachLinkAndIsWrittenBackTheSame() {
        String java = SCREENS + "Do.press(Screens.SHOP.chest).serves(Goals.ENOUGH_GOLD, Goals.RICH)"
                + ".needs(Goals.GUILD).needs(Firestone::eventOn, " + PLAN + "Need.is(Progress::open)).first()"
                + ".over(() -> Screens.ARENA.fight).until(Progress::full).onSight()";
        Do action = (Do) read(Do.class, java);
        assertEquals(BotConstants.input("SHOP", "chest"), action.input());
        assertEquals(List.of(BotConstants.goal("ENOUGH_GOLD"), BotConstants.goal("RICH")), action.serves());
        assertEquals(List.of(BotConstants.goal("GUILD")), action.goalNeeds());
        assertEquals(2, action.conditionNeeds().size());
        assertEquals("Firestone::eventOn", ScreenTypes.sourceOf(action.conditionNeeds().getFirst()));
        assertTrue(action.isFirst() && action.isOnSight());
        Supplier<Do> over = action.over().getFirst();
        assertEquals(BotConstants.input("ARENA", "fight"), over.get());
        assertEquals("Progress::full", ScreenTypes.sourceOf(action.until()));
        assertEquals(action, again(Do.class, action));
    }

    @Test
    void aPlanReadsItsTiersOfGoalReferences() {
        Plan plan = (Plan) read(Plan.class, PLAN + "Plan.of(" + PLAN + "Plan.tier(Goals.A, Goals.B), " + PLAN
                + "Plan.tier(Goals.C))");
        assertEquals(List.of(BotConstants.goal("A"), BotConstants.goal("B")), plan.tiers().getFirst().goals());
        assertEquals(plan, again(Plan.class, plan));
    }

    @Test
    void aDoneGoalReadsItsActionLazily() {
        Goal.Done goal = (Goal.Done) read(Goal.class, PLAN + "Goal.done(() -> Screens.ARENA.fight)");
        assertEquals(BotConstants.input("ARENA", "fight"), goal.action().get());
        assertEquals(goal, again(Goal.class, goal));
    }
}
