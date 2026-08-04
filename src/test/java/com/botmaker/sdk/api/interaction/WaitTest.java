package com.botmaker.sdk.api.interaction;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@link Wait} has to get right now that the length is a {@code java.time.Duration} and the range is a
 * call rather than a value: nothing degenerate may sleep, and a range must actually vary while staying inside
 * its bounds. The bounds are asserted by sampling the elapsed time rather than a return value because
 * {@code between} deliberately keeps its roll private — that is the point of moving it out of an accessor.
 */
class WaitTest {

    /** How long {@code r} takes, in milliseconds. */
    private static long elapsed(Runnable r) {
        long before = System.nanoTime();
        r.run();
        return (System.nanoTime() - before) / 1_000_000;
    }

    @Test
    void nothingDegenerateSleeps() {
        long ms = elapsed(() -> {
            Wait.time(null);
            Wait.time(Duration.ZERO);
            Wait.time(Duration.ofSeconds(-5));
            Wait.between(null, null);
            Wait.between(Duration.ZERO, Duration.ZERO);
            Wait.between(Duration.ofSeconds(-3), Duration.ofSeconds(-1));
        });
        assertTrue(ms < 200, "one of these slept: " + ms + "ms");
    }

    @Test
    void aFixedWaitSleepsAtLeastItsLength() {
        assertTrue(elapsed(() -> Wait.time(Duration.ofMillis(40))) >= 35);
    }

    @Test
    void aRangeStaysInsideItsBoundsWhicheverWayRound() {
        // Inverted on purpose: the shorter end is the floor, so this must behave as 30–70ms and not throw.
        for (int i = 0; i < 5; i++) {
            long ms = elapsed(() -> Wait.between(Duration.ofMillis(70), Duration.ofMillis(30)));
            assertTrue(ms >= 25, "slept " + ms + "ms, under the floor");
            assertTrue(ms < 400, "slept " + ms + "ms, well past the ceiling");
        }
    }

    @Test
    void aRangeVariesFromCallToCall() {
        // A range rolled once (at construction, as the old range-valued Duration did) would sleep the same
        // amount every time and humanize nothing.
        boolean varied = false;
        long first = elapsed(() -> Wait.between(Duration.ofMillis(0), Duration.ofMillis(60)));
        for (int i = 0; i < 20 && !varied; i++) {
            varied = Math.abs(elapsed(() -> Wait.between(Duration.ofMillis(0), Duration.ofMillis(60))) - first) > 5;
        }
        assertTrue(varied, "every roll of a 0–60ms range came back the same length");
    }

    @Test
    void aBoundInAnyUnitIsTheSameWait() {
        assertTrue(elapsed(() -> Wait.between(Duration.ofMillis(30), Duration.ofMillis(40))) >= 25);
        assertTrue(elapsed(() -> Wait.minutes(0.0005)) >= 25, "0.0005min is 30ms, not 0");
    }
}
