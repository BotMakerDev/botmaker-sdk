package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Custom;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Setup;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
}
