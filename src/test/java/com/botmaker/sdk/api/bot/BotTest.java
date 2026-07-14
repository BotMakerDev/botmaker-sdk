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
    void superviseRunsGoHomeThenStartGameInThatOrder() {
        StringBuilder order = new StringBuilder();
        assertThrows(StopLoop.class, () -> Bot.supervise(
                () -> { throw new BotStuckException("stuck"); },
                () -> order.append("H"),          // goHome
                () -> {                             // startGame
                    order.append("S");
                    throw new StopLoop();
                }));
        assertEquals("HS", order.toString());
    }
}
