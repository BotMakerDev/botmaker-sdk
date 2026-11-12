package com.botmaker.sdk.internal.emulator;

import com.botmaker.sdk.internal.bot.BotStuckException;
import com.botmaker.sdk.internal.bot.Watchdog;
import com.botmaker.sdk.internal.emulator.SystemDialogs.Act;
import com.botmaker.sdk.internal.emulator.SystemDialogs.Count;
import com.botmaker.shared.emulator.SystemDialog;
import com.botmaker.shared.emulator.SystemDialog.Shown;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemDialogsTest {

    private static final Optional<Shown> HUNG = Optional.of(new Shown(SystemDialog.NOT_RESPONDING, true));

    @Test
    void anOwedRestartIsSaidOnceAndARestartForgetsOneOwedMeanwhile() {
        SystemDialogs.clear();
        SystemDialogs.owe("The game no longer runs, com.ldmnq.launcher3 is in front: restarting it");
        SystemDialogs.owe("a second reason");
        BotStuckException stuck = assertThrows(BotStuckException.class, Watchdog::owedRestart);
        assertEquals("The game no longer runs, com.ldmnq.launcher3 is in front: restarting it", stuck.getMessage());
        assertTrue(Watchdog.takeRestartNext(), "the recovery restarts rather than walking home");
        assertDoesNotThrow(Watchdog::owedRestart, "said once");

        SystemDialogs.owe("seen gone between the restart's stop and start");
        Watchdog.restarted();
        assertDoesNotThrow(Watchdog::owedRestart);
    }

    @Test
    void aGameThatStopsAnsweringIsWaitedForTwiceThenRestarted() {
        Count count = new Count();
        assertEquals(Act.BACK, count.seen(HUNG, 0));
        assertEquals(Act.NOTHING, count.seen(Optional.empty(), 5_000));
        assertEquals(Act.BACK, count.seen(HUNG, 10_000));
        assertEquals(Act.NOTHING, count.seen(Optional.empty(), 15_000));
        assertEquals(Act.BACK_AND_RESTART, count.seen(HUNG, 20_000));
        assertEquals(Act.BACK, count.seen(HUNG, 25_000), "a restart counts again from none");
    }

    /** A Wait that missed leaves one dialog up across looks: it counts once, and a minute of it restarts. */
    @Test
    void oneDialogStillUpCountsOnce() {
        Count count = new Count();
        for (long at = 0; at < SystemDialogs.HUNG_MS; at += 2_000) assertEquals(Act.BACK, count.seen(HUNG, at));
        assertEquals(Act.BACK_AND_RESTART, count.seen(HUNG, SystemDialogs.HUNG_MS));
    }

    @Test
    void aMinuteOfNotAnsweringRestartsItWhateverTheCount() {
        Count count = new Count();
        assertEquals(Act.BACK, count.seen(HUNG, 0));
        assertEquals(Act.BACK_AND_RESTART, count.seen(HUNG, SystemDialogs.HUNG_MS));
    }

    @Test
    void aCalmSpellForgetsTheEarlierOnes() {
        Count count = new Count();
        count.seen(HUNG, 0);
        count.seen(HUNG, 1_000);
        count.seen(Optional.empty(), 1_000 + SystemDialogs.CALM_MS + 1);
        assertEquals(Act.BACK, count.seen(HUNG, 1_000 + SystemDialogs.CALM_MS + 2));
    }

    @Test
    void aCrashRestartsAtOnceAndAPermissionIsDenied() {
        Count count = new Count();
        assertEquals(Act.BACK_AND_RESTART, count.seen(Optional.of(new Shown(SystemDialog.CRASHED, true)), 0));
        assertEquals(Act.BACK, count.seen(Optional.of(new Shown(SystemDialog.PERMISSION, true)), 0));
    }

    @Test
    void anotherAppThatStopsAnsweringIsClosedAndNeverRestartsTheGame() {
        Count count = new Count();
        Optional<Shown> other = Optional.of(new Shown(SystemDialog.NOT_RESPONDING, false));
        for (int i = 0; i < 5; i++) assertEquals(Act.BACK, count.seen(other, i * 1_000L));
        assertEquals(Act.BACK, count.seen(Optional.of(new Shown(SystemDialog.CRASHED, false)), 6_000));
    }
}
