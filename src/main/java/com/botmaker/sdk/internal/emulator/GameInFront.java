package com.botmaker.sdk.internal.emulator;

import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.launch.LaunchTarget;
import com.botmaker.sdk.internal.launch.Target;
import com.botmaker.shared.emulator.EmulatorOwnApps;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Brings the game back in front while a bot runs, when the emulator's own launcher, store, game center or ad covers
 * it ({@link EmulatorOwnApps}): LDPlayer's store panel, BlueStacks' game center, MuMu's ads, a Home pressed by a
 * stray tap. Any other app in front is left alone. A game that no longer runs (force-stopped, crashed with no dialog)
 * is not started here: it owes a restart ({@link SystemDialogs#owe}), which the run's recovery pays, counted against
 * its restart cap. Asked by {@link SystemDialogs} on its own thread, when no Android dialog is up, at most once every
 * {@value #AGAIN_MS} ms.
 */
final class GameInFront {

    static final long AGAIN_MS = 10_000;

    private static final AtomicLong ASKED = new AtomicLong();

    private GameInFront() {}

    /**
     * Brings the run's game back on {@code emu} when an app of the emulator's own is in front of it, or owes its restart
     * when it no longer runs.
     */
    static void keep(Emulator emu) {
        if (!(Target.current() instanceof LaunchTarget.EmulatorApp(String game, String instance)) || !emu.is(instance)) {
            return;
        }
        long now = System.currentTimeMillis();
        long asked = ASKED.get();
        if (now - asked < AGAIN_MS) return;
        Optional<String> cover = cover(emu.device().focusedPackage(), game, emu.device()::homePackage);
        if (cover.isEmpty() || !ASKED.compareAndSet(asked, now)) return;
        if (!emu.runs(game)) {
            SystemDialogs.owe("The game no longer runs, " + cover.get() + " is in front: restarting it");
            return;
        }
        Debug.log(cover.get() + ", the emulator's own app, is in front of the game: bringing the game back");
        if (!emu.device().bringToFront(game)) {
            Debug.error("Android started no activity of " + game + ": it stays behind " + cover.get());
        }
    }

    /**
     * The app of the emulator's own in front of {@code game}, or empty when the game, nothing known or another app is
     * in front. The home app is asked only when something other than the game is in front.
     */
    static Optional<String> cover(Optional<String> front, String game, Supplier<Optional<String>> home) {
        return front.filter(app -> !app.equals(game))
                .filter(app -> EmulatorOwnApps.covers(app, home.get()));
    }
}
