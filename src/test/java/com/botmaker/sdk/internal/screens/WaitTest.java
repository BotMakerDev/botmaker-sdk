package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one wait of a walk, on a counted clock and frames drawn by the time. */
class WaitTest {

    private static final BufferedImage BEFORE = filled(Color.BLACK);
    private static final BufferedImage AFTER = filled(Color.WHITE);

    /** A game whose frame at each time is {@code frames}' answer; time is counted, not waited. */
    private static final class Game implements Reach {
        final LongFunction<BufferedImage> frames;
        long now;
        int looks;
        String frozen;

        Game(LongFunction<BufferedImage> frames) {
            this.frames = frames;
        }

        @Override
        public Optional<BufferedImage> look() {
            looks++;
            return Optional.of(frames.apply(now));
        }

        @Override
        public Optional<String> frozen() {
            return Optional.ofNullable(frozen);
        }

        @Override
        public long nowMs() {
            return now;
        }

        @Override
        public void pause(long ms) {
            now += ms;
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            return Optional.empty();
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
        }

        @Override
        public void keys(Combo combo) {
        }

        @Override
        public void type(String text, boolean clears, boolean submits) {
        }

        @Override
        public void scroll(Point at, int notches) {
        }

        @Override
        public void drag(Point from, Point to, Duration time) {
        }
    }

    private static BufferedImage filled(Color color) {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 40, 30);
        g.dispose();
        return image;
    }

    @Test
    void untilAsksAfterEachPollUpToItsDeadline() {
        Game game = new Game(t -> BEFORE);
        List<Long> asked = new ArrayList<>();
        Optional<String> none = Wait.until(game, 1_000, () -> {
            asked.add(game.now);
            return Optional.empty();
        });
        assertTrue(none.isEmpty());
        assertEquals(List.of(250L, 500L, 750L, 1_000L), asked);

        assertEquals(Optional.of("there"), Wait.until(game, 2_000,
                () -> game.now >= 1_500 ? Optional.of("there") : Optional.empty()));
        assertEquals(1_500, game.now, "answered: no wait past the answer");
    }

    @Test
    void untilAsksOnceWhenItsDeadlineHasPassed() {
        Game game = new Game(t -> BEFORE);
        game.now = 5_000;
        List<Long> asked = new ArrayList<>();
        Wait.until(game, 1_000, () -> {
            asked.add(game.now);
            return Optional.empty();
        });
        assertEquals(List.of(5_250L), asked);
    }

    /** Every wait of a walk stops on a frozen game, which shapes, gone and next did not ask (audit #19). */
    @Test
    void untilStopsOnAFrozenGameOrAStop() {
        Game game = new Game(t -> BEFORE);
        game.frozen = "The game has not moved for 60 s.";
        Wait.until(game, 10_000, Optional::empty);
        assertEquals(250, game.now, "one look, then the frozen game ends the wait");

        Game stopped = new Game(t -> BEFORE);
        Thread.currentThread().interrupt();
        try {
            Wait.until(stopped, 10_000, Optional::empty);
        } finally {
            Thread.interrupted();
        }
        assertEquals(250, stopped.now);
    }

    @Test
    void aPressThatChangesTheGameSettlesOnceTwoLooksAgree() {
        // The answer draws at 400 ms: one look shows it (500), the next agrees (750), well before the cap.
        Game game = new Game(t -> t >= 400 ? AFTER : BEFORE);
        Optional<BufferedImage> settled = Wait.settled(game, BEFORE, 600, 2_000);
        assertSame(AFTER, settled.orElseThrow());
        assertEquals(750, game.now);
    }

    @Test
    void aPressWithNothingToShowSettlesAtItsQuietTime() {
        Game game = new Game(t -> BEFORE);
        Wait.settled(game, BEFORE, 600, 2_000);
        assertEquals(750, game.now, "the first two looks alike past 600 ms, never the cap");
    }

    @Test
    void aGameThatNeverHoldsStillIsWaitedOnToTheCapOnly() {
        Game game = new Game(t -> t / 250 % 2 == 0 ? BEFORE : AFTER);
        Optional<BufferedImage> settled = Wait.settled(game, null, 600, 2_000);
        assertEquals(2_000, game.now);
        assertTrue(settled.isPresent(), "the last look, though it never held still");
    }

    /** Leaves that sway on their own move a few pixels every look: the answer under them still settles early. */
    @Test
    void aGameThatSwaysOnItsOwnSettlesOnceItsAnswerHolds() {
        BufferedImage sway = filled(Color.WHITE);
        Graphics2D g = sway.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 6, 4);    // 6 of the 300 pixels compared: 2%, more than a changed frame, less than an answer
        g.dispose();
        Game game = new Game(t -> t < 400 ? BEFORE : t / 250 % 2 == 0 ? AFTER : sway);
        assertTrue(Wait.settled(game, BEFORE, 600, 2_000).isPresent());
        assertEquals(750, game.now, "the answer drew at 400 ms and held at 750, though the leaves never stopped");
    }

    @Test
    void aScreenWithNoBeforeWaitsOnlyForTwoLooksAlike() {
        Game game = new Game(t -> t >= 300 ? AFTER : BEFORE);
        Wait.settled(game, null, 600, 2_000);
        assertEquals(750, game.now, "250 before, 500 and 750 after, the last two alike");
    }
}
