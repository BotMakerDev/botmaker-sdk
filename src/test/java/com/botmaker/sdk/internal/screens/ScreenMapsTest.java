package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Custom;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.screens.closing.Pictures;
import com.botmaker.sdk.internal.screens.closing.Screens;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The map a run reads off the bot's {@code Screens} class. */
class ScreenMapsTest {

    private static final AtomicInteger SOLVED = new AtomicInteger();

    /** A bot's own method, as {@code Puzzles::solve} is. */
    static void solve() {
        SOLVED.incrementAndGet();
    }

    /** A bot's {@code Screens} class. */
    public static final class Bot {

        public static final Puzzle PUZZLE = new Puzzle();

        public static final class Puzzle extends Screen {
            public final Custom solve = Custom.of(ScreenMapsTest::solve);
            public final Do solved = Do.press(solve).once();

            @Override
            public Setup setup() {
                return Setup.named("Puzzle");
            }
        }
    }

    @Test
    void readsACustomInput() {
        ScreenGraph map = ScreenMaps.read(Bot.class);

        Custom solve = assertInstanceOf(Custom.class, map.input("PUZZLE", "solve").orElseThrow());
        int before = SOLVED.get();
        solve.method().run();
        assertEquals(before + 1, SOLVED.get(), "a run holds the bot's own method, not its name");
    }

    /** A run's map clicks the bot's close_x for a way back with none, as the assistant's walks do; no picture, none. */
    @Test
    void theMapHoldsTheBotsCloseButton() {
        ScreenGraph map = ScreenMaps.read(Screens.class);

        assertSame(Pictures.CLOSE_X, map.close().picture());
        assertEquals(null, ScreenMaps.read(Bot.class).close(), "a bot with no close_x picture");
    }

    @Test
    void readsDoFields() {
        ScreenGraph map = ScreenMaps.read(Bot.class);

        MapScreen puzzle = map.screen("PUZZLE").orElseThrow();
        assertSame(Bot.PUZZLE.solved, puzzle.actions().get("solved"));
        assertSame(Bot.PUZZLE.solve, Bot.PUZZLE.solved.input());
        assertFalse(puzzle.parts().containsKey("solved"), "an action tells nothing of its screen");
        assertEquals("solved", map.nameOf(Bot.PUZZLE.solved).orElseThrow().field());
        assertEquals("PUZZLE", map.nameOf(Bot.PUZZLE.solved).orElseThrow().screen());
        assertTrue(map.nameOf(Do.press(Bot.PUZZLE.solve).once()).isEmpty(), "this very value, not one equal to it");
        assertEquals(puzzle.actions(), puzzle.setup(setup -> setup.popup()).actions(), "a change to its setup keeps them");
    }
}
