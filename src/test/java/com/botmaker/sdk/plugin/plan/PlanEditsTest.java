package com.botmaker.sdk.plugin.plan;

import com.botmaker.java.grammar.BotNames;
import com.botmaker.java.grammar.JavaValue;
import com.botmaker.java.grammar.ValueGrammar;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Mark;
import com.botmaker.sdk.plugin.plan.PlanEdits.Shortcut;
import com.botmaker.sdk.plugin.types.PlanTypes;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 🎯 Goals window's edits: an action's {@code .over}, ★ and ⤓, a goal moved between tiers or into one of its own,
 * a goal made from a {@code Need}.
 */
class PlanEditsTest {

    private static final ValueGrammar GRAMMAR = ValueGrammar.of(List.of(), List.of(PlanTypes.PLAN_SHAPE,
            PlanTypes.TIER_SHAPE, PlanTypes.GOAL_OF_SHAPE, PlanTypes.NEED_AT_LEAST_SHAPE, PlanTypes.NEED_AT_MOST_SHAPE,
            PlanTypes.NEED_EQUAL_TO_SHAPE, PlanTypes.NEED_IS_SHAPE, PlanTypes.NEED_ABOVE_SHAPE,
            PlanTypes.NEED_BELOW_SHAPE, PlanTypes.NEED_NOT_SHAPE, PlanTypes.DO_SHAPE, PlanTypes.RETRY_AGAIN_SHAPE,
            PlanTypes.RETRY_LATER_SHAPE, PlanTypes.RETRY_NEVER_SHAPE));

    /** The bot's goals and screens' fields as Studio spells them: {@code Goals.X}, {@code Screens.S.f}. */
    private static final BotNames NAMES = new BotNames() {
        @Override
        public Optional<Object> named(QualifiedName written) {
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

    private static <T> String java(Class<T> type, T value) {
        return GRAMMAR.write(type, value, NAMES).orElseThrow().source();
    }

    /** {@code Plan.of(Plan.tier(Goals.A, Goals.B), Plan.tier(Goals.C))}, as Studio reads it: references. */
    private static Plan plan(List<List<String>> tiers) {
        return new Plan(tiers.stream()
                .map(tier -> new Plan.Tier(tier.stream().map(BotConstants::goal).toList())).toList());
    }

    @Test
    void moveGoalBetweenTiers() {
        Plan plan = plan(List.of(List.of("ENERGY"), List.of("GOLD", "GUILD"), List.of("LEVEL")));

        Plan moved = PlanEdits.moved(plan, "GUILD", 0, false);

        assertEquals(plan(List.of(List.of("ENERGY", "GUILD"), List.of("GOLD"), List.of("LEVEL"))), moved);
        assertEquals("Plan.of(Plan.tier(Goals.ENERGY, Goals.GUILD), Plan.tier(Goals.GOLD), Plan.tier(Goals.LEVEL))",
                java(Plan.class, moved));
        assertEquals(plan(List.of(List.of("ENERGY", "GUILD", "LEVEL"))),
                PlanEdits.moved(plan(List.of(List.of("ENERGY", "GUILD"), List.of("LEVEL"))), "LEVEL", 0, false),
                "the tier it leaves empty goes");
        assertThrows(IllegalArgumentException.class, () -> PlanEdits.moved(plan, "GOLD", 3, false));
        assertEquals(plan, PlanEdits.moved(plan, "GOLD", 1, false), "dropped on its own tier, nothing moves");
        assertEquals(2, PlanEdits.tierOf(PlanEdits.moved(plan, "LEVEL", 3, true), "LEVEL"), "its tier, from 0");
        assertEquals(1, PlanEdits.tierOf(PlanEdits.moved(plan(List.of(List.of("A"), List.of("B"))), "A", 2, true),
                "A"), "counted after the tier it left went");
    }

    @Test
    void newTier() {
        Plan plan = plan(List.of(List.of("ENERGY"), List.of("GOLD", "GUILD")));

        assertEquals(plan(List.of(List.of("ENERGY"), List.of("GUILD"), List.of("GOLD"))),
                PlanEdits.moved(plan, "GUILD", 1, true), "a tier of its own above the one it was in");
        assertEquals(plan(List.of(List.of("ENERGY"), List.of("GOLD"), List.of("GUILD"))),
                PlanEdits.moved(plan, "GUILD", 2, true), "last");
        assertEquals(plan(List.of(List.of("GOLD", "GUILD"), List.of("ENERGY"))),
                PlanEdits.moved(plan, "ENERGY", 2, true), "its old tier, left empty, goes");
        assertEquals("Plan.of(Plan.tier(Goals.START))", java(Plan.class, PlanEdits.startedWith("START")));
    }

    @Test
    void writesOver() {
        Do opening = Do.press(BotConstants.input("HOME", "chest")).serves(BotConstants.goal("GOLD"));

        Do over = PlanEdits.over(opening, "SHOP", "selling");

        String java = java(Do.class, over);
        assertTrue(java.contains(".over(() -> Screens.SHOP.selling)"), java);
        assertTrue(java.contains(".serves(Goals.GOLD)"), "the rest is kept: " + java);
        assertSame(over, PlanEdits.over(over, "SHOP", "selling"), "named once");
        Do twice = PlanEdits.over(over, "ARENA", "fighting");
        assertEquals(2, twice.over().size());
        assertTrue(java(Do.class, twice).contains("Screens.ARENA.fighting"), java(Do.class, twice));
    }

    @Test
    void writesFirstAndLast() {
        Do action = Do.press(BotConstants.input("HOME", "chest"));

        Do first = PlanEdits.marked(action, Mark.FIRST);
        Do last = PlanEdits.marked(first, Mark.LAST);
        Do neither = PlanEdits.marked(last, Mark.NONE);

        assertTrue(first.isFirst() && !first.isLast());
        assertTrue(java(Do.class, first).endsWith(".first()"), java(Do.class, first));
        assertTrue(last.isLast() && !last.isFirst(), "last is no more first");
        assertTrue(java(Do.class, last).endsWith(".last()") && !java(Do.class, last).contains("first"),
                java(Do.class, last));
        assertFalse(neither.isFirst() || neither.isLast());
        assertEquals(java(Do.class, action), java(Do.class, neither));
    }

    @Test
    void refusesOverCycle() {
        Do selling = Do.press(BotConstants.input("SHOP", "sell"));
        Do opening = PlanEdits.over(Do.press(BotConstants.input("HOME", "chest")), "SHOP", "selling");
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", new MapScreen(Setup.named("HOME").start(), Map.of("shop", click("shop"),
                "chest", click("chest")), Map.of(), Map.of(), Map.of("opening", opening)));
        screens.put("SHOP", new MapScreen(Setup.named("SHOP").from(ref("HOME", "shop")), Map.of("sell", click("sell")),
                Map.of(), Map.of(), Map.of("selling", selling)));
        ScreenGraph map = ScreenGraph.of(screens);

        assertEquals(Optional.empty(), PlanEdits.refusedOver(map, "SHOP", "selling", selling), "no circle yet");
        Optional<String> refused = PlanEdits.refusedOver(map, "SHOP", "selling",
                PlanEdits.over(selling, "HOME", "opening"));

        assertTrue(refused.isPresent());
        assertTrue(refused.get().startsWith("These actions each say to be tried before the next, in a circle: "),
                refused.get());
        assertTrue(refused.get().contains("HOME.opening") && refused.get().contains("SHOP.selling"), refused.get());
    }

    @Test
    void newGoalFromNeed() {
        Goal gold = PlanEdits.fromNeed("Progress::gold", Shortcut.AT_LEAST, 100);
        assertEquals("Goal.of(Need.atLeast(Progress::gold, 100))", java(Goal.class, gold));
        assertEquals("Goal.of(Need.below(Progress::energy, 5))",
                java(Goal.class, PlanEdits.fromNeed("Progress::energy", Shortcut.BELOW, 5)));
        assertEquals("Goal.of(Need.is(Progress::guildJoined))",
                java(Goal.class, PlanEdits.fromNeed("Progress::guildJoined", Shortcut.IS, null)));
        assertEquals("Goal.of(Need.not(Progress::guildJoined))",
                java(Goal.class, PlanEdits.fromNeed("Progress::guildJoined", Shortcut.NOT, null)));
        assertEquals("Goal.of(Need.equalTo(Progress::rank, \"gold\"))",
                java(Goal.class, PlanEdits.fromNeed("Progress::rank", Shortcut.EQUAL_TO, "gold")));

        assertEquals("GOLD_AT_LEAST_100", PlanEdits.goalName("Progress::gold", Shortcut.AT_LEAST, 100, Set.of()));
        assertEquals("GOLD_AT_LEAST_MINUS_5", PlanEdits.goalName("Progress::gold", Shortcut.AT_LEAST, -5, Set.of()));
        assertEquals("GUILD_JOINED",PlanEdits.goalName("Progress::guildJoined", Shortcut.IS, null, Set.of()));
        assertEquals("NOT_GUILD_JOINED_2", PlanEdits.goalName("Progress::guildJoined", Shortcut.NOT, null,
                Set.of("NOT_GUILD_JOINED")));

        assertEquals(List.of(Shortcut.IS, Shortcut.NOT), PlanEdits.shortcuts("boolean"));
        assertTrue(PlanEdits.shortcuts("java.lang.Integer").contains(Shortcut.AT_LEAST));
        assertEquals(List.of(), PlanEdits.shortcuts("long"), "a long's limit does not read back yet");
        assertEquals(Optional.of(100), PlanEdits.limit("int", " 100 "));
        assertEquals(Optional.empty(), PlanEdits.limit("int", "lots"));
        assertThrows(IllegalArgumentException.class, () -> PlanEdits.fromNeed("gold", Shortcut.IS, null));
        assertThrows(IllegalArgumentException.class, () -> PlanEdits.fromNeed("Progress::gold", Shortcut.AT_LEAST,
                null));
    }
}
