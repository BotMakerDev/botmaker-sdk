package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.vision.FrameChange.Clock;
import com.botmaker.sdk.plugin.assist.Departure.Timing;
import com.botmaker.sdk.plugin.assist.Departure.Way;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartureTest {

    private static final Timing TIMING = new Timing(1_000, 10_000, 250);

    /** A frame stands for itself: what shows on it is which of these it is. */
    private static BufferedImage frame() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    }

    private final BufferedImage marked = frame();
    private final BufferedImage markedMoved = frame();
    private final BufferedImage dimmed = frame();
    private final BufferedImage other = frame();
    private final BufferedImage loading = frame();
    private final BufferedImage spinning = frame();

    /** Time counted, never slept. */
    private static final class Counter implements Clock {
        long now;

        @Override
        public long nowMs() {
            return now;
        }

        @Override
        public void sleep(long ms) {
            now += ms;
        }
    }

    /** Reads {@code frames} one each, the last one again once they run out. */
    private Departure watch(BufferedImage settled, Predicate<BufferedImage> present, BufferedImage... frames) {
        Deque<BufferedImage> reads = new ArrayDeque<>(List.of(frames));
        BufferedImage[] last = {frames.length == 0 ? settled : frames[frames.length - 1]};
        return Departure.watch(settled, present, (a, b) -> a != b,
                () -> Optional.of(reads.isEmpty() ? last[0] : reads.poll()), new Counter(), TIMING);
    }

    private Predicate<BufferedImage> marks(BufferedImage... showing) {
        return Set.of(showing)::contains;
    }

    @Test
    void markersThatStayWhileTheBackgroundMovesStayedThoughMuchChanged() {
        // The background moves on its own: the screen never holds still, the markers never go.
        Counter clock = new Counter();
        BufferedImage[] alternate = {marked, markedMoved};
        int[] read = {0};
        Departure d = Departure.watch(marked, marks(marked, markedMoved), (a, b) -> a != b,
                () -> Optional.of(alternate[read[0]++ % 2]), clock, TIMING);
        assertEquals(Way.STAYED, d.way());
        assertSame(marked, d.frame());
        assertEquals(TIMING.timeoutMs(), clock.now, "watched up to the timeout, as the screen kept moving");
    }

    @Test
    void markersGoneAtOnceAndStayingGoneLeft() {
        Departure d = watch(other, marks(marked), other);
        assertEquals(Way.LEFT, d.way());
        assertSame(other, d.frame());
    }

    @Test
    void aScreenSlowToDrawLeavesWhenItsMarkersGoLater() {
        // Still marked a moment, then a spinner turning for a second and a half, then the next screen.
        Departure d = watch(marked, marks(marked), marked, loading, spinning, loading, spinning, loading, spinning,
                other);
        assertEquals(Way.LEFT, d.way());
        assertSame(other, d.frame());
    }

    @Test
    void markersHiddenAMomentAndBackFlashed() {
        Departure d = watch(other, marks(marked), other, marked);
        assertEquals(Way.FLASHED, d.way());
        assertSame(marked, d.frame());
    }

    @Test
    void markersThatGoAndComeBackFlashed() {
        Departure d = watch(marked, marks(marked), other, marked);
        assertEquals(Way.FLASHED, d.way());
    }

    @Test
    void aPopupThatDimsTheScreenLeavesItsMarkersPresent() {
        // The dimmed marker still matches.
        Departure d = watch(dimmed, marks(marked, dimmed), dimmed);
        assertEquals(Way.STAYED, d.way());
    }

    @Test
    void aStillScreenWithItsMarkersStaysAfterAHold() {
        Counter clock = new Counter();
        Departure d = Departure.watch(marked, marks(marked), (a, b) -> a != b, () -> Optional.of(marked), clock,
                TIMING);
        assertEquals(Way.STAYED, d.way());
        assertTrue(clock.now >= TIMING.holdMs() && clock.now < TIMING.timeoutMs(), "stopped once still: " + clock.now);
    }

    @Test
    void aFailedReadIsSkipped() {
        Counter clock = new Counter();
        Departure d = Departure.watch(other, marks(marked), (a, b) -> a != b, Optional::empty, clock, TIMING);
        assertEquals(Way.LEFT, d.way());
        assertSame(other, d.frame());
    }
}
