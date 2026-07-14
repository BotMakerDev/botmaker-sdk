package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.observe.BotObserver;
import com.botmaker.sdk.api.observe.Bots;
import com.botmaker.sdk.api.observe.MatchEvent;
import com.botmaker.sdk.api.vision.ClickConfig;
import com.botmaker.sdk.api.vision.MatchResult;

/**
 * Detects when a bot is <em>stuck</em> — no progress across many consecutive vision checks — and throws
 * {@link BotStuckException} so the {@link Bot#supervise supervisor} can recover (go home / restart).
 *
 * <p>It piggybacks on the match telemetry the SDK already emits ({@link Bots} / {@link MatchEvent}): while
 * enabled, an observer computes a <em>signature</em> for each match attempt (the template id + rounded
 * match location, or {@code "miss"}) and counts how many times in a row the signature is unchanged. The
 * observer only <em>counts</em> — it never throws, because the vision layer
 * ({@link com.botmaker.sdk.api.vision.ImageFinder}) swallows {@code Exception} and would eat it.
 *
 * <p>The throw happens deterministically at {@link #checkpoint()}, which the generated macro loop calls
 * once per iteration (and which you may call inside any custom loop). {@link #progress()} lets logic the
 * vision layer can't see reset the counter. All state is per-thread.
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
            Bots.addObserver(COUNTER);
            enabled = true;
        }
    }

    /** Stop watching (idempotent). Removes the counting observer. */
    public static synchronized void disable() {
        if (enabled) {
            Bots.removeObserver(COUNTER);
            enabled = false;
        }
    }

    /** True while the watchdog is counting match attempts. */
    public static synchronized boolean isEnabled() {
        return enabled;
    }

    /**
     * Throws {@link BotStuckException} if the bot has shown no progress for
     * {@link ClickConfig#MAX_RETRY_ATTEMPTS} consecutive match attempts; otherwise returns. Resets the
     * counter when it throws so recovery starts from a clean slate. Safe to call when disabled (never throws).
     */
    public static void checkpoint() {
        State s = STATE.get();
        if (s.repeats >= ClickConfig.MAX_RETRY_ATTEMPTS) {
            int repeats = s.repeats;
            String sig = s.signature;
            progress();
            throw new BotStuckException("Bot appears stuck: no progress for " + repeats
                    + " consecutive checks (repeating " + sig + ")");
        }
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
    }

    private static void record(String signature) {
        State s = STATE.get();
        if (signature.equals(s.signature)) {
            s.repeats++;
        } else {
            s.signature = signature;
            s.repeats = 0;
        }
    }

    /** Template id + coarse location, so tiny match jitter doesn't read as progress; {@code "miss"} when not found. */
    private static String signatureOf(MatchResult result) {
        if (result == null || !result.isFound()) {
            return "miss";
        }
        Point c = result.getCenter();
        long gx = Math.round(c.x / 4.0);
        long gy = Math.round(c.y / 4.0);
        return result.getTemplateId() + "@" + gx + "," + gy;
    }
}
