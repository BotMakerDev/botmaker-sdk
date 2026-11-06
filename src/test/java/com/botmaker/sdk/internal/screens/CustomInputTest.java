package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Custom;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.plugin.assist.ScreenEdits;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bot's own Java as an input: a run presses it by running it, Discovery leaves it alone. */
class CustomInputTest {

    /** A game that was asked to do nothing: whatever it is asked is noted. */
    private static final class Still implements Reach {
        final List<String> done = new ArrayList<>();
        final BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(frame);
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage shown) {
            return Optional.empty();
        }

        @Override
        public void drag(Point from, Point to, Duration time) {
            done.add("drag");
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
            done.add("press");
        }

        @Override
        public void keys(Combo combo) {
            done.add("keys");
        }

        @Override
        public void type(String text, boolean clears, boolean submits) {
            done.add("type");
        }

        @Override
        public void scroll(Point at, int notches) {
            done.add("scroll");
        }

        @Override
        public long nowMs() {
            return 0;
        }

        @Override
        public void pause(long ms) {
            done.add("pause");
        }
    }

    @Test
    void pressRunsTheMethod() {
        AtomicReference<Thread> ranOn = new AtomicReference<>();
        Still game = new Still();

        assertTrue(game.perform(Custom.of(() -> ranOn.set(Thread.currentThread())), game.frame));

        assertEquals(Thread.currentThread(), ranOn.get(), "on the run's own thread");
        assertEquals(List.of(), game.done, "and nothing else is done to the game");
    }

    @Test
    void whatTheMethodThrowsReachesTheRun() {
        Still game = new Still();

        assertThrows(ArithmeticException.class, () -> game.perform(Custom.of(() -> {
            throw new ArithmeticException("no solution");
        }), game.frame));
    }

    /** Studio reads the method's name off the source, which nothing can run: not pressed, and no throw in a walk. */
    @Test
    void aNameIsNotPressed() {
        Still game = new Still();

        assertFalse(game.perform(Custom.of(ScreenTypes.method("Puzzles::solve")), game.frame));
    }

    @Test
    void discoveryLeavesItAlone() {
        Input solve = Custom.of(() -> { });
        MapScreen puzzle = MapFixtures.screen("Puzzle", solve);
        ScreenGraph graph = ScreenGraph.of(Map.of("PUZZLE", puzzle));
        String field = MapFixtures.fieldOf(solve);

        assertTrue(solve.isTried(), "tried once written");
        assertEquals(solve, solve.tried(), "so .tried() says nothing new");
        assertTrue(graph.untried("PUZZLE").isEmpty(), "nothing on the screen to try");
        assertTrue(graph.tried("PUZZLE", field));
        assertTrue(ScreenEdits.untried(solve).isTried(), "and no edit makes it Discovery's to try");
        assertTrue(graph.shape("PUZZLE").isEmpty(), "no one tried it: it says nothing of its screen's shape");
    }
}
