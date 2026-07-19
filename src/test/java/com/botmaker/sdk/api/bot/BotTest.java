package com.botmaker.sdk.api.bot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the {@link Bot#supervise} recovery loop. Since {@code supervise} never returns under normal
 * operation, the recovery hook throws a test-only {@link Error} (which supervise deliberately does NOT
 * catch — it only catches {@link RuntimeException}/{@link BotStuckException}) to break out after a few cycles.
 */
class BotTest {

    /** Error (not RuntimeException) so it escapes supervise's catch and ends the loop. */
    private static final class StopLoop extends Error {}

    @AfterEach
    void tearDown() {
        Watchdog.disable();
        Watchdog.reset();
    }

    @Test
    void superviseCatchesStuckAndRunsRecoveryEachCycle() {
        AtomicInteger body = new AtomicInteger();
        AtomicInteger recovery = new AtomicInteger();
        assertThrows(StopLoop.class, () -> Bot.supervise(
                () -> { body.incrementAndGet(); throw new BotStuckException("stuck"); },
                () -> { if (recovery.incrementAndGet() >= 3) throw new StopLoop(); }));
        assertEquals(3, body.get(), "body re-runs after each recovery");
        assertEquals(3, recovery.get());
    }

    @Test
    void superviseCatchesGenericRuntimeCrash() {
        AtomicInteger recovery = new AtomicInteger();
        assertThrows(StopLoop.class, () -> Bot.supervise(
                () -> { throw new IllegalStateException("crash"); },
                () -> { if (recovery.incrementAndGet() >= 2) throw new StopLoop(); }));
        assertEquals(2, recovery.get());
    }

    @Test
    void superviseRunsStartGameThenGoHomeOnceAtColdStartBeforeTheLoop() {
        // The reported bug: Startup never ran on a normal launch. Cold start now runs startGame then goHome,
        // once, before the first body pass — so "launch the game in Startup" actually fires.
        StringBuilder order = new StringBuilder();
        java.util.List<StartMode> modes = new java.util.ArrayList<>();
        assertThrows(StopLoop.class, () -> Bot.supervise(
                () -> { order.append("B"); throw new StopLoop(); },   // body: end the loop on the first pass
                () -> order.append("H"),                              // goHome
                mode -> { modes.add(mode); order.append("S"); }));    // startGame
        assertEquals("SHB", order.toString(),
                "cold start = startGame then goHome, once, before the first body pass");
        assertEquals(java.util.List.of(StartMode.COLD), modes, "cold start hands startGame COLD");
    }

    @Test
    void superviseRecoversWithGoHomeThenStartGameAfterColdStart() {
        // Mid-run recovery keeps its original order (goHome then startGame), distinct from cold start.
        StringBuilder order = new StringBuilder();
        AtomicInteger starts = new AtomicInteger();
        java.util.List<StartMode> modes = new java.util.ArrayList<>();
        assertThrows(StopLoop.class, () -> Bot.supervise(
                () -> { order.append("B"); throw new BotStuckException("stuck"); },
                () -> order.append("H"),                              // goHome
                mode -> {                                             // startGame
                    modes.add(mode);
                    order.append("S");
                    if (starts.incrementAndGet() >= 2) throw new StopLoop();  // stop on the recovery restart
                }));
        // cold start S,H → body B (stuck) → recovery H,S(2nd → StopLoop)
        assertEquals("SHBHS", order.toString());
        assertEquals(java.util.List.of(StartMode.COLD, StartMode.RESTART), modes,
                "cold start is COLD; the recovery restart is RESTART");
    }

    @Test
    void stopBreaksTheLoopAndSuperviseReturnsWithoutRecovering() {
        // Bot.stop() is the clean exit: supervise returns normally (no StopLoop needed) and never recovers.
        AtomicInteger body = new AtomicInteger();
        AtomicInteger recovery = new AtomicInteger();
        Bot.supervise(
                () -> { if (body.incrementAndGet() >= 3) Bot.stop(); },
                recovery::incrementAndGet);
        assertEquals(3, body.get(), "body runs until it calls stop()");
        assertEquals(0, recovery.get(), "stop() is a clean exit, not a crash — recovery must not run");
    }

    @Test
    void stopDuringColdStartEndsTheBotBeforeTheLoopRuns() {
        AtomicInteger body = new AtomicInteger();
        AtomicInteger recovery = new AtomicInteger();
        Bot.supervise(
                body::incrementAndGet,                 // body: must never run
                recovery::incrementAndGet,             // goHome (recovery half)
                mode -> Bot.stop());                   // startGame stops during cold start
        assertEquals(0, body.get(), "a stop() during cold start ends the bot before the first body pass");
        assertEquals(0, recovery.get(), "and does not route through recovery");
    }

    @Test
    void aFailedColdStartRoutesThroughRecoveryInsteadOfAborting() {
        StringBuilder order = new StringBuilder();
        AtomicInteger starts = new AtomicInteger();
        assertThrows(StopLoop.class, () -> Bot.supervise(
                () -> { order.append("B"); throw new StopLoop(); },   // body ends the loop
                () -> order.append("H"),                              // goHome
                mode -> {                                             // startGame
                    order.append("S");
                    if (starts.incrementAndGet() == 1) throw new IllegalStateException("cold start boom");
                }));
        // cold start S (throws) → recovery H,S → loop body B (StopLoop)
        assertEquals("SHSB", order.toString());
    }
}
