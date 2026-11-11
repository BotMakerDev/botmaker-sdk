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
 * stray tap. Any other app in front is left alone, and so is a game that no longer runs: its restart is the run's
 * recovery's ({@link SystemDialogs}, the watchdog), not this. Asked by {@link SystemDialogs} on its own thread, when no
 * Android dialog is up, at most once every {@value #AGAIN_MS} ms.
 */
final class GameInFront {

    static final long AGAIN_MS = 10_000;

    private static final AtomicLong ASKED = new AtomicLong();

    private GameInFront() {}

    /** Starts the run's game again on {@code emu} when an app of the emulator's own is in front of it. */
    static void keep(Emulator emu) {
        if (!(Target.current() instanceof LaunchTarget.EmulatorApp(String game, String instance)) || !emu.is(instance)) {
            return;
        }
        long now = System.currentTimeMillis();
        long asked = ASKED.get();
        if (now - asked < AGAIN_MS) return;
        Optional<String> cover = cover(emu.device().focusedPackage(), game, emu.device()::homePackage);
        if (cover.isEmpty() || !emu.runs(game) || !ASKED.compareAndSet(asked, now)) return;
        Debug.log(cover.get() + ", the emulator's own app, is in front of the game: bringing the game back");
        emu.device().startApp(game);
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
