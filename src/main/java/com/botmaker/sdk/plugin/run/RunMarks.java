package com.botmaker.sdk.plugin.run;

import com.botmaker.shared.ipc.TelemetryEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The marks drawn over the desktop while a bot runs: a box where it found a picture, a dot where it clicked,
 * each fading out over {@link #FADE_MILLIS}. Both are in absolute screen pixels on the wire, which is the
 * layer's own space, so nothing is offset here. Not thread-safe: the JavaFX thread only.
 */
final class RunMarks {

    /** How long a mark stays, fading from opaque to gone. */
    static final long FADE_MILLIS = 1500;
    /** The most marks kept at once: a bot clicking in a tight loop must not grow this without bound. */
    static final int MAX_MARKS = 64;

    enum Kind { FOUND, CLICK }

    /**
     * One mark at {@code now}: which one ({@code id}, the same for as long as it shows, so a drawer keeps one
     * node per mark), where, how opaque, and the label a found box carries.
     */
    record Mark(long id, Kind kind, int x, int y, int width, int height, double opacity, String label) {}

    private record Placed(long id, Kind kind, int x, int y, int width, int height, String label, long at) {}

    private final Deque<Placed> placed = new ArrayDeque<>();
    private long nextId;

    /** Adds the mark {@code event} leaves, if it leaves one. */
    void add(TelemetryEvent event, long now) {
        switch (event) {
            case TelemetryEvent.Match m when m.found() && m.rect() != null -> placed.addLast(new Placed(nextId++,
                    Kind.FOUND, m.rect().x(), m.rect().y(), m.rect().width(), m.rect().height(),
                    Math.round(m.confidence() * 100) + "%", now));
            case TelemetryEvent.Click c -> placed.addLast(new Placed(nextId++, Kind.CLICK, c.x(), c.y(), 0, 0, "",
                    now));
            default -> { }
        }
        while (placed.size() > MAX_MARKS) placed.removeFirst();
    }

    /** The marks still showing at {@code now}, oldest first; the ones that have faded out are dropped. */
    List<Mark> at(long now) {
        placed.removeIf(p -> now - p.at() >= FADE_MILLIS);
        List<Mark> marks = new ArrayList<>(placed.size());
        for (Placed p : placed) {
            double opacity = 1 - (double) Math.max(0, now - p.at()) / FADE_MILLIS;
            marks.add(new Mark(p.id(), p.kind(), p.x(), p.y(), p.width(), p.height(), opacity, p.label()));
        }
        return marks;
    }

    boolean isEmpty() {
        return placed.isEmpty();
    }
}
