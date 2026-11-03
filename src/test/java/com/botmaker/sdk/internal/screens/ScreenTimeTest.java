package com.botmaker.sdk.internal.screens;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A run's time on each screen: a visit per change of screen, the screen it is on counted up to now. */
class ScreenTimeTest {

    @Test
    void eachChangeOfScreenIsAVisitAndTheTimeOnItSumsUp() {
        AtomicLong now = new AtomicLong(1_000);
        ScreenTime time = new ScreenTime(now::get);
        time.on("Home");
        now.addAndGet(500);
        time.on("Home");
        now.addAndGet(500);
        time.on("Shop");
        now.addAndGet(200);
        time.on("Home");
        now.addAndGet(300);

        assertEquals(List.of(new ScreenTime.Spent("Home", 2, 1_300), new ScreenTime.Spent("Shop", 1, 200)),
                time.spent());
        now.addAndGet(100);
        assertEquals(new ScreenTime.Spent("Home", 2, 1_400), time.spent().getFirst(), "counted again up to now");
    }

    @Test
    void offCountsNoTimeAndTheNextScreenIsAVisitEvenTheSameOne() {
        AtomicLong now = new AtomicLong(0);
        ScreenTime time = new ScreenTime(now::get);
        time.on("Home");
        now.addAndGet(100);
        time.off();
        now.addAndGet(5_000);
        time.on("Home");
        now.addAndGet(50);

        assertEquals(List.of(new ScreenTime.Spent("Home", 2, 150)), time.spent());
    }

    @Test
    void noScreenSeenIsNothingSpent() {
        ScreenTime time = new ScreenTime(() -> 0);
        time.on(null);
        assertEquals(List.of(), time.spent());
    }
}
