package com.botmaker.sdk.plugin.assist;

import com.botmaker.java.grammar.BotNames;
import com.botmaker.java.grammar.JavaValue;
import com.botmaker.java.grammar.ValueGrammar;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.plan.GoalsViewModel.Mark;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The names {@link ScreenEdits} gives what it adds to the bot, and the actions it rewrites. */
class ScreenEditsTest {

    private static final ValueGrammar GRAMMAR = ValueGrammar.of(List.of(), List.of(PlanTypes.DO_SHAPE,
            PlanTypes.RETRY_AGAIN_SHAPE, PlanTypes.RETRY_LATER_SHAPE, PlanTypes.RETRY_NEVER_SHAPE));

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

    private static String java(Do action) {
        return GRAMMAR.write(Do.class, action, NAMES).orElseThrow().source();
    }

    /** A field's text kept in {@code Texts} is named after it, as a constant, never a name taken already. */
    @Test
    void aTextsConstantIsNamedAfterItsText() {
        assertEquals("DRAGONS_GUILD", ScreenEdits.textName("  Dragons guild! ", Set.of()));
        assertEquals("DRAGONS_2", ScreenEdits.textName("dragons", Set.of("DRAGONS")));
        assertEquals("DRAGONS_3", ScreenEdits.textName("dragons", Set.of("DRAGONS", "DRAGONS_2")));
        assertEquals("TEXT_42", ScreenEdits.textName("42", Set.of()));
        assertEquals("TEXT", ScreenEdits.textName("…", Set.of()));
        assertEquals("A_VERY_LONG_SEARCH_THAT_GOES_ON", ScreenEdits.textName(
                "a very long search that goes on and on", Set.of()), "cut at a word's end");
    }

    @Test
    void writesOver() {
        Do opening = Do.press(BotConstants.input("HOME", "chest")).serves(BotConstants.goal("GOLD"));

        Do over = ScreenEdits.over(opening, "SHOP", "selling");

        String java = java(over);
        assertTrue(java.contains(".over(() -> Screens.SHOP.selling)"), java);
        assertTrue(java.contains(".serves(Goals.GOLD)"), "the rest is kept: " + java);
        assertSame(over, ScreenEdits.over(over, "SHOP", "selling"), "named once");
        Do twice = ScreenEdits.over(over, "ARENA", "fighting");
        assertEquals(2, twice.over().size());
        assertTrue(java(twice).contains("Screens.ARENA.fighting"), java(twice));
    }

    @Test
    void writesFirstAndLast() {
        Do action = Do.press(BotConstants.input("HOME", "chest"));

        Do first = ScreenEdits.marked(action, Mark.FIRST);
        Do last = ScreenEdits.marked(first, Mark.LAST);
        Do neither = ScreenEdits.marked(last, Mark.NONE);

        assertTrue(first.isFirst() && !first.isLast());
        assertTrue(java(first).endsWith(".first()"), java(first));
        assertTrue(last.isLast() && !last.isFirst(), "last is no more first");
        assertTrue(java(last).endsWith(".last()") && !java(last).contains("first"), java(last));
        assertFalse(neither.isFirst() || neither.isLast());
        assertEquals(java(action), java(neither));
    }

    @Test
    void refusesOverCycle() {
        Do selling = Do.press(BotConstants.input("SHOP", "sell"));
        Do opening = ScreenEdits.over(Do.press(BotConstants.input("HOME", "chest")), "SHOP", "selling");
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", new MapScreen(Setup.named("HOME").start(), Map.of("shop", click("shop"),
                "chest", click("chest")), Map.of(), Map.of(), Map.of("opening", opening)));
        screens.put("SHOP", new MapScreen(Setup.named("SHOP").from(ref("HOME", "shop")), Map.of("sell", click("sell")),
                Map.of(), Map.of(), Map.of("selling", selling)));
        ScreenGraph map = ScreenGraph.of(screens);

        assertEquals(Optional.empty(), ScreenEdits.refusedOver(map, "SHOP", "selling", selling), "no circle yet");
        Optional<String> refused = ScreenEdits.refusedOver(map, "SHOP", "selling",
                ScreenEdits.over(selling, "HOME", "opening"));

        assertTrue(refused.isPresent());
        assertTrue(refused.get().startsWith("These actions each say to be tried before the next, in a circle: "),
                refused.get());
        assertTrue(refused.get().contains("HOME.opening") && refused.get().contains("SHOP.selling"), refused.get());
    }
}
