package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.emulator.SystemDialogs;
import com.botmaker.sdk.internal.observe.BotObserver;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.observe.MatchEvent;
import com.botmaker.sdk.internal.trace.Say;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * Detects when a bot is <em>stuck</em> — no progress across many consecutive vision checks — and throws
 * {@link BotStuckException} so the {@link Bot#supervise supervisor} can recover (go home / restart).
 *
 * <p>It piggybacks on the match telemetry the SDK already emits ({@link Bots} / {@link MatchEvent}): while
 * enabled, an observer computes a <em>signature</em> for each match that found its picture (the template id +
 * rounded match location) and counts how many times in a row the signature is unchanged. A miss ends the run
 * rather than adding to it: a bot that does not find a picture is waiting for one, polling within its own bound
 * (a fight running its course, a button not ready yet), and every miss used to read as the same {@code "miss"}
 * whatever the picture, so a minute's wait recovered — and a recovery then always restarted the game. Stuck is the same
 * picture found at the same place over and over: a click that changes nothing. The
 * observer only <em>counts</em> — it never throws, because the vision layer
 * ({@link com.botmaker.sdk.api.vision.ImageFinder}) swallows {@code Exception} and would eat it.
 *
 * <p>It also tells a frozen game ({@link #frozen()}, 2026-11-03): every capture and every input counts into one
 * {@link Stillness}, and a picture that stays still for {@link BotSettings#frozenAfter()} after input was sent is
 * a game that no longer answers, whether the bot was clicking, waiting on a picture or walking the screen map.
 *
 * <p>The throw happens deterministically at {@link #checkpoint()}, which the flow walker calls between
 * activities. {@link #progress()} lets logic the vision layer can't see reset the counter, and the supervisor
 * calls {@link #reset()} after a restart. The match count is per thread; the game's picture is one for the run,
 * since a bot looks at one game (a run that captures two surfaces in turn sees each move, and is never told frozen).
 *
 * <p>Internal: a bot never names it, and {@code Bot.runFlow} turns it on.
 */
public final class Watchdog {

    private Watchdog() {}

    private static final class State {
        String signature;
        int repeats;
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    /** Counts consecutive identical match signatures; installed by {@link #enable()}. */
    private static final BotObserver COUNTER = new BotObserver() {
        @Override
        public void onMatch(MatchEvent event) {
            record(signatureOf(event.result()));
        }
    };

    private static boolean enabled;

    /** Start watching (idempotent). Registers the counting observer. */
    public static synchronized void enable() {
        if (!enabled) {
            // A run starts owing no restart: one an earlier run in this JVM left is not this run's.
            SystemDialogs.clear();
            restartNext = false;
            STILL.clear();
            Bots.addObserver(COUNTER);
            enabled = true;
            Debug.log("enabled");
        }
    }

    /** Stop watching (idempotent). Removes the counting observer. */
    public static synchronized void disable() {
        if (enabled) {
            Bots.removeObserver(COUNTER);
            enabled = false;
            Debug.log("disabled");
        }
    }

    /** True while the watchdog is counting match attempts. */
    public static synchronized boolean isEnabled() {
        return enabled;
    }

    /**
     * Throws {@link BotStuckException} if the bot has shown no progress for
     * {@link BotSettings#maxRetryAttempts()} consecutive match attempts; otherwise returns. Resets the
     * counter when it throws so recovery starts from a clean slate. Safe to call when disabled (never throws).
     */
    public static void checkpoint() {
        Optional<String> dialog = SystemDialogs.takeRestart();
        if (dialog.isPresent()) {
            progress();
            restartNext = true;
            // The run's recovery restarts the game: a fallback rescuing the run, a warning.
            Say.warn(dialog.get());
            throw Say.told(new BotStuckException(dialog.get()));
        }
        Optional<String> frozen = frozen();
        if (frozen.isPresent()) {
            progress();
            STILL.clear();
            Say.warn(frozen.get());
            throw Say.told(new BotStuckException(frozen.get()));
        }
        State s = STATE.get();
        if (s.repeats >= BotSettings.current().maxRetryAttempts()) {
            int repeats = s.repeats;
            String sig = s.signature;
            progress();
            Say.warn("stuck after " + repeats + " checks repeating " + sig);
            throw Say.told(new BotStuckException("Bot appears stuck: no progress for " + repeats
                    + " consecutive checks (repeating " + sig + ")"));
        }
    }

    /**
     * Whether the recovery must restart the game rather than walk home, once: Android said it crashed or stopped
     * answering ({@link SystemDialogs}), and no screen of it is there to walk.
     */
    private static volatile boolean restartNext;

    /** {@link #restartNext}, cleared. */
    public static boolean takeRestartNext() {
        boolean owed = restartNext;
        restartNext = false;
        return owed;
    }

    /** Signal that the bot advanced; clears the no-progress counter. */
    public static void progress() {
        State s = STATE.get();
        s.signature = null;
        s.repeats = 0;
    }

    /** Clears all watchdog state on the current thread (called by the supervisor after a restart). */
    public static void reset() {
        progress();
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

    private static void record(String signature) {
        State s = STATE.get();
        if (signature == null) {
            progress();
        } else if (signature.equals(s.signature)) {
            s.repeats++;
        } else {
            s.signature = signature;
            s.repeats = 0;
        }
    }

    /** Template id + coarse location, so tiny match jitter doesn't read as progress; null when not found. */
    private static String signatureOf(MatchResult result) {
        if (result == null || !result.isFound()) {
            return null;
        }
        Point c = result.center();
        long gx = Math.round(c.x() / 4.0);
        long gy = Math.round(c.y() / 4.0);
        return result.templateId() + "@" + gx + "," + gy;
    }
}
