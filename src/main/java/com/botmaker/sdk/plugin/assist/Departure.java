package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.vision.FrameChange.Clock;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Whether a try left its screen, told by what marked that screen, never by how much of the frame changed: a game
 * whose background moves changes much and leaves nothing, a tab whose title and rows change changes little and leaves
 * (2026-11-03, the user's call). Its markers gone while the screen holds still {@link Timing#holdMs} left it; gone and
 * back is the screen redrawn in place; still there once the screen holds still {@code holdMs} it stayed. At {@link
 * Timing#timeoutMs} a screen that never held still is judged as it is. A marker under a popup's dim still matches (the match ignores a uniform dim), so a popup leaves nothing.
 *
 * @param way   what the try did to the screen
 * @param frame the frame that tells it: the settled one when it stayed, the last read when it left, the one the
 *              markers came back on when it flashed
 */
record Departure(Way way, BufferedImage frame) {

    /** What a try did to the screen it was made on. */
    enum Way {
        /** Its markers went and stayed gone. */
        LEFT,
        /** Its markers stayed. */
        STAYED,
        /** Its markers went a moment and came back: the screen redrawn in place. */
        FLASHED
    }

    /**
     * How long gone is gone, how long a try is watched at most, how often the screen is read.
     *
     * @param holdMs    how long the screen holds still, its markers gone or there, to be judged
     * @param timeoutMs how long the screen is read on at most
     * @param pollMs    how long between reads
     */
    record Timing(long holdMs, long timeoutMs, long pollMs) {
        /** A try's: a second gone, ten at most, four reads a second. */
        static final Timing TRY = new Timing(1_000, 10_000, 250);
    }

    /**
     * What the try did, watched from {@code settled}, the frame its answer settled on.
     *
     * @param present whether a frame shows what marks the screen
     * @param moved   whether the screen changed between two reads, its own motion left out
     * @param look    a fresh read of the screen; empty when it failed
     */
    static Departure watch(BufferedImage settled, Predicate<BufferedImage> present,
                           BiPredicate<BufferedImage, BufferedImage> moved, Supplier<Optional<BufferedImage>> look,
                           Clock clock, Timing timing) {
        long start = clock.nowMs();
        boolean shown = present.test(settled);
        // Since the markers went or the screen last moved, whichever is later.
        long since = start;
        BufferedImage last = settled;
        while (true) {
            long now = clock.nowMs();
            if (now - since >= timing.holdMs() || now - start >= timing.timeoutMs()) break;
            try {
                clock.sleep(timing.pollMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Optional<BufferedImage> read = look.get();
            if (read.isEmpty()) continue;
            BufferedImage frame = read.get();
            boolean showsNow = present.test(frame);
            if (!shown && showsNow) return new Departure(Way.FLASHED, frame);
            // Gone, the next screen is still drawing while it moves (a loading page): the frame it ends on tells it.
            if (shown != showsNow || moved.test(last, frame)) since = clock.nowMs();
            shown = showsNow;
            last = frame;
        }
        return shown ? new Departure(Way.STAYED, settled) : new Departure(Way.LEFT, last);
    }
}
