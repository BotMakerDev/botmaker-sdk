package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.emulator.SystemDialogs;
import com.botmaker.sdk.internal.trace.Say;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * Detects when the game no longer answers and throws {@link BotStuckException} so the {@link Bot#supervise
 * supervisor} can recover (go home / restart).
 *
 * <p>It tells a frozen game ({@link #frozen()}, 2026-11-03): every capture and every input counts into one
 * {@link Stillness}, and a picture that stays still for {@link BotSettings#frozenAfter()} after input was sent is
 * a game that no longer answers, whether the bot was clicking, waiting on a picture or walking the screen map. It also
 * carries a restart Android owes ({@link SystemDialogs}).
 *
 * <p>The throw happens deterministically at {@link #checkpoint()}, which a plan run calls once a round; the run
 * counts its own progress (2026-11-09: the match-repeat count went with flows). The supervisor calls {@link #reset()}
 * after a restart. The game's picture is one for the run, since a bot looks at one game (a run that captures two
 * surfaces in turn sees each move, and is never told frozen).
 *
 * <p>Internal: a bot never names it, and {@code Bot.run} turns it on.
 */
public final class Watchdog {

    private Watchdog() {}

    private static boolean enabled;

    /** Start watching (idempotent). */
    public static synchronized void enable() {
        if (!enabled) {
            // A run starts owing no restart: one an earlier run in this JVM left is not this run's.
            SystemDialogs.clear();
            restartNext = false;
            STILL.clear();
            enabled = true;
            Debug.log("enabled");
        }
    }

    /** Stop watching (idempotent). */
    public static synchronized void disable() {
        if (enabled) {
            enabled = false;
            Debug.log("disabled");
        }
    }

    /** True while the watchdog is on: a run is supervised. */
    public static synchronized boolean isEnabled() {
        return enabled;
    }

    /**
     * Throws {@link BotStuckException} when Android owes a restart of the game or its picture is frozen; otherwise
     * returns. Safe to call when disabled.
     */
    public static void checkpoint() {
        owedRestart();
        Optional<String> frozen = frozen();
        if (frozen.isPresent()) {
            STILL.clear();
            Say.warn(frozen.get());
            throw Say.told(new BotStuckException(frozen.get()));
        }
    }

    /**
     * Throws {@link BotStuckException} when a restart of the game is owed ({@link SystemDialogs}), and only then: what
     * a wait checks, where a still picture is no frozen game.
     */
    public static void owedRestart() {
        Optional<String> owed = SystemDialogs.takeRestart();
        if (owed.isPresent()) {
            restartNext = true;
            // The run's recovery restarts the game: a fallback rescuing the run, a warning.
            Say.warn(owed.get());
            throw Say.told(new BotStuckException(owed.get()));
        }
    }

    /**
     * Whether the recovery must restart the game rather than walk home, once: Android said it crashed or stopped
     * answering, or it no longer runs ({@link SystemDialogs}), and no screen of it is there to walk.
     */
    private static volatile boolean restartNext;

    /** {@link #restartNext}, cleared. */
    public static boolean takeRestartNext() {
        boolean owed = restartNext;
        restartNext = false;
        return owed;
    }

    /**
     * Forgets a restart owed while the game was being restarted: a look between its stop and its start saw it gone,
     * and the restart that just ended paid it.
     */
    public static void restarted() {
        SystemDialogs.clear();
    }

    /** Clears the game's picture (called by the supervisor after a restart). */
    public static void reset() {
        STILL.clear();
    }

    /** The game's picture across the run: every capture a source takes and every input sent to the game. */
    private static final Stillness STILL = new Stillness();

    /** Counts a frame a capture source took of the game, on whichever thread took it, watched or not; returns it. */
    public static BufferedImage saw(BufferedImage frame) {
        STILL.saw(frame, System.currentTimeMillis());
        return frame;
    }

    /** Counts a click, swipe, key or text sent to the game. */
    public static void acted() {
        STILL.acted(System.currentTimeMillis());
    }

    /**
     * Why the game looks frozen: its picture has not changed for {@link BotSettings#frozenAfter()} although input
     * was sent to it meanwhile. Empty while it answers, or while nothing was sent: a still game with no input is
     * one waiting, not a frozen one.
     */
    public static Optional<String> frozen() {
        long still = STILL.stillAfterInput();
        if (still < BotSettings.current().frozenAfter()) return Optional.empty();
        return Optional.of("The game looks frozen: its picture did not change for " + still / 1000
                + " s after the bot's input (BotSettings.frozenAfter)");
    }
}
