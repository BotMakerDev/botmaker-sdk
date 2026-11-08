package com.botmaker.sdk.internal.bot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Counts a run's restarts of the game: past {@code cap} within {@code within}, the run stops with the reason instead
 * of restarting once more, since a game that keeps needing a restart is not fixed by another (2026-11-09, the user's
 * call; the supervisor's back-off alone restarted it forever). A recovery that only got home is not counted.
 */
public final class RestartCap {

    private final int cap;
    private final Duration within;
    private final Deque<Instant> made = new ArrayDeque<>();

    /** At most {@code cap} restarts within {@code within}. */
    public RestartCap(int cap, Duration within) {
        this.cap = cap;
        this.within = within;
    }

    /**
     * A restart asked at {@code now}: empty when it may go ahead, and it is counted; else why the run stops instead.
     */
    public synchronized Optional<String> restart(Instant now) {
        while (!made.isEmpty() && !made.peekFirst().isAfter(now.minus(within))) made.removeFirst();
        if (made.size() >= cap) {
            return Optional.of("The game was restarted " + made.size() + " times in the last " + within.toMinutes()
                    + " min and still needs another: the bot stops (BotSettings.restartCap). Look at the run's"
                    + " report for what kept going wrong.");
        }
        made.addLast(now);
        return Optional.empty();
    }
}
