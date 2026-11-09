package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.internal.vision.FrameChange;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The one wait of a walk: a probe asked on a fresh look every {@link #POLL_MS} until it answers or a deadline passes,
 * and the game let settle after a press. Every wait ends early on a stop (the thread's interrupt) or on a game the run
 * finds frozen ({@link Reach#frozen}); none is a sleep of fixed length taken as the game's answer.
 */
final class Wait {

    /** How often the game is looked at while waiting. */
    static final long POLL_MS = 250;
    /** The longest a press is let settle when nothing waits on a screen: a game that animates never holds still. */
    static final long SETTLE_MS = 2_000;

    private Wait() {
    }

    /**
     * What {@code probe} answers, asked after each {@link #POLL_MS} until it answers, {@code deadline} (on
     * {@link Reach#nowMs}) passes, the thread is interrupted or {@code reach} finds the game frozen. It is asked at
     * least once, though the deadline has passed already. Empty when it never answered.
     */
    static <T> Optional<T> until(Reach reach, long deadline, Supplier<Optional<T>> probe) {
        do {
            reach.pause(POLL_MS);
            Optional<T> answer = probe.get();
            if (answer.isPresent()) return answer;
        } while (reach.nowMs() < deadline && !stopped(reach));
        return Optional.empty();
    }

    /**
     * The look once the game answered a press made on {@code before} and holds still: a look that differs from
     * {@code before} ({@link FrameChange#changed}) followed by one alike to it (less than
     * {@link FrameChange#ANSWER_SHARE} moved), or, when nothing has changed, two alike once {@code quietMs} passed: a
     * press with nothing to show. A null {@code before} waits only for two looks alike.
     * By {@code deadline}, or on a stop, the last look taken; empty when none could be taken.
     */
    static Optional<BufferedImage> settled(Reach reach, BufferedImage before, long quietMs, long deadline) {
        Settling settling = new Settling(reach, before, reach.nowMs() + quietMs);
        return until(reach, deadline, settling).or(() -> Optional.ofNullable(settling.last));
    }

    /** Whether a wait ends now, whatever it waits on: a stop, or a game found frozen. */
    static boolean stopped(Reach reach) {
        return Thread.currentThread().isInterrupted() || reach.frozen().isPresent();
    }

    /** {@link #settled}'s probe: the looks so far, and whether one has moved from the frame before the press. */
    private static final class Settling implements Supplier<Optional<BufferedImage>> {
        private final Reach reach;
        private final BufferedImage before;
        private final long quiet;
        private BufferedImage last;
        private boolean moved;

        Settling(Reach reach, BufferedImage before, long quiet) {
            this.reach = reach;
            this.before = before;
            this.quiet = quiet;
            this.moved = before == null;
        }

        @Override
        public Optional<BufferedImage> get() {
            Optional<BufferedImage> now = reach.look();
            if (now.isEmpty()) return Optional.empty();
            BufferedImage was = last;
            last = now.get();
            if (!moved) moved = FrameChange.changed(before, last);
            // Held still when less than an answer's share moves between two looks: a game that sways on its own
            // (Firestone's leaves and heroes) moves more than a changed frame's share every look, never as much as a
            // screen or a popup drawing in.
            boolean still = was != null
                    && FrameChange.share(was, last, FrameChange.Motion.NONE) < FrameChange.ANSWER_SHARE;
            return still && (moved || reach.nowMs() >= quiet) ? now : Optional.empty();
        }
    }
}
