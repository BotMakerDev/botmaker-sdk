package com.botmaker.sdk.internal.screens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * How long a run spent on each screen of its map, and how often it came to it: what the SDK's <em>Time on screens</em>
 * report sums up as the run ends ({@link ScreenMaps}). A visit starts when the game is first seen on a screen other
 * than the one before; popups are never one, as {@link ScreenMaps#seen} keeps none.
 */
public final class ScreenTime {

    /**
     * One screen's share of the run.
     *
     * @param screen the screen, as the map shows it
     * @param visits how often the run came to it
     * @param millis how long it stayed on it, in all
     */
    public record Spent(String screen, int visits, long millis) {}

    private final LongSupplier clock;
    private final Map<String, long[]> per = new LinkedHashMap<>();
    private String on;
    private long since;

    /** Times by {@code clock}, in milliseconds. */
    public ScreenTime(LongSupplier clock) {
        this.clock = clock;
    }

    /** The game is on {@code shown} now: a new visit unless it was there already; {@code null} changes nothing. */
    public synchronized void on(String shown) {
        if (shown == null || shown.equals(on)) return;
        long now = clock.getAsLong();
        close(now);
        on = shown;
        since = now;
        per.computeIfAbsent(shown, k -> new long[2])[0]++;
    }

    /**
     * The game is on no screen known now (a walk ended on none, a new try starts): the time so far is kept, none is
     * counted until the next {@link #on}, which is a visit even to the screen before.
     */
    public synchronized void off() {
        close(clock.getAsLong());
        on = null;
    }

    /** Each screen met so far with its visits and time, the one the game is on counted up to now; first met first. */
    public synchronized List<Spent> spent() {
        close(clock.getAsLong());
        List<Spent> spent = new ArrayList<>();
        per.forEach((screen, n) -> spent.add(new Spent(screen, (int) Math.min(Integer.MAX_VALUE, n[0]), n[1])));
        return spent;
    }

    /** Adds the time since the last mark to the screen the game is on. Guarded by this. */
    private void close(long now) {
        if (on != null) per.get(on)[1] += Math.max(0, now - since);
        since = now;
    }
}
