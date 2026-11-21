// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.internal.explore.ExploreLoop.Paused;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Where a <em>Keep exploring</em> run waits on a stop, and the stops it parked (split from {@link ExploreLoop},
 * 2026-11-21): a stop waits for a command, the game back in front, or its parking time; a parked one waits in the
 * journal's queue and its place is avoided until it is fixed. What a command does to the run is the loop's, through
 * {@link Answers}.
 */
final class Pauses {

    /** What the loop does for a command taken while paused. */
    interface Answers {

        /**
         * A resume of {@code stopId} while paused on {@code open}: the bot reloaded and what it ends. Empty: refused, or
         * a parked stop resumed, and the run stays paused.
         */
        Optional<Paused> resume(Stop open, String stopId, boolean ignore);

        /** Claude calls {@code stop} a Discover fault, {@code why} its line: held from now on. */
        void held(Stop stop, String why);

        /** A resume without ignore of the fault {@code stopId} being held: refused, the fault stays held. */
        void refusedFault(String stopId);

        /** A resume of {@code stopId}, neither open nor parked: refused, so the tool waiting on it hears. */
        void stale(String stopId);

        /** Whether the run's budget is spent. */
        boolean spent();
    }

    private final Journal journal;
    private final ExploreLoop.Play play;
    private final ExploreLoop.Settings settings;
    private final LongSupplier nowMs;
    private final Consumer<ExploreEvent> emit;
    /** Whether Claude may call a stop a fault: the SDK is a {@code -SNAPSHOT} build. */
    private final boolean dev;

    /** The parked stops, by kind and place ({@link #key}): never pressed, never stopped on again until fixed. */
    private final Map<String, Stop> parked = new LinkedHashMap<>();
    /**
     * What the plugin side left out of each settled stop, by its id, kept until a resume of it takes: a refused reload
     * loses none of it, and a resume with ignore drops it.
     */
    private final Map<String, List<String>> leftOut = new LinkedHashMap<>();
    /** Whether the run ends because a stop that cannot park waited past {@link ExploreLoop.Settings#stuckAfter}. */
    private boolean stuck;

    Pauses(Journal journal, ExploreLoop.Play play, ExploreLoop.Settings settings, LongSupplier nowMs,
           Consumer<ExploreEvent> emit, boolean dev) {
        this.journal = journal;
        this.play = play;
        this.settings = settings;
        this.nowMs = nowMs;
        this.emit = emit;
        this.dev = dev;
        // A queue a run before left is avoided too.
        for (Stop stop : journal.queue()) parked.put(key(stop.kind(), stop.place()), stop);
    }

    /** A parked stop's key: its kind and place, so two kinds parked on one place are both kept. */
    static String key(StopKind kind, String place) {
        return kind.id() + " " + place;
    }

    /**
     * Waits on {@code stop} for a command, the game back in front, or its parking time, unless {@code neverParks}. A
     * fault is held instead: only the SDK's reload, a resume with ignore, or the run's end lets it go.
     */
    Paused pause(Stop stop, boolean neverParks, Answers answers) {
        String id = stop.id();
        boolean held = stop.kind() == StopKind.FAULT;
        long deadline = nowMs.getAsLong() + settings.parkAfter().toMillis();
        // A stop that cannot park (a window in front) ends the run once nothing could be pressed for the stuck time.
        long stuckAt = nowMs.getAsLong() + settings.stuckAfter().toMillis();
        while (true) {
            Optional<Journal.Command> command = journal.takeCommand();
            if (command.orElse(null) instanceof Journal.Command.End || answers.spent()) return Paused.END;
            // Claude calls this stop a fault: held from now on. Out of dev mode, or for another stop, it is dropped.
            if (command.orElse(null) instanceof Journal.Command.Fault(String stopId, String why)
                    && dev && !held && id.equals(stopId)) {
                held = true;
                answers.held(stop, why);
            }
            // A resume of a stop that is neither this one nor parked is stale: it changes nothing.
            if (command.orElse(null) instanceof Journal.Command.Resume(String stopId, boolean ignore,
                                                                        List<String> skipped)) {
                if (held && !ignore && id.equals(stopId)) {
                    answers.refusedFault(stopId);
                } else if (id.equals(stopId) || holds(stopId)) {
                    keepLeftOut(stopId, skipped);
                    Optional<Paused> resumedNow = answers.resume(stop, stopId, ignore);
                    if (resumedNow.isPresent()) {
                        // A fault dropped as a misreading: no restart goes on from it.
                        if (held) ResumeFile.delete(journal);
                        return resumedNow.get();
                    }
                    // Claude is on this stop: its parking time starts again. A parked one resumed leaves it be.
                    if (id.equals(stopId)) {
                        deadline = nowMs.getAsLong() + settings.parkAfter().toMillis();
                        stuckAt = nowMs.getAsLong() + settings.stuckAfter().toMillis();
                    }
                } else {
                    answers.stale(stopId);
                }
            }
            // A fault waits for its fix: never parked, never let go by the game coming back.
            if (held) {
                play.idle(settings.poll());
                continue;
            }
            if (stop.kind() == StopKind.GUARD && neverParks) {
                Foreground front = play.front();
                if (!front.foreign() && front.dialog().isEmpty()) {
                    emit.accept(new ExploreEvent.Fixed(id, "the game is in front again", nowMs.getAsLong()));
                    return Paused.BACK;
                }
                // Never parked, so no press goes to the window in front: the run ends instead (R3).
                if (nowMs.getAsLong() >= stuckAt) {
                    stuck = true;
                    return Paused.END;
                }
            } else if (!neverParks && (nowMs.getAsLong() >= deadline
                    || command.orElse(null) instanceof Journal.Command.Park(String stopId) && id.equals(stopId))) {
                // A stop Claude handed to the user waits in the queue for them at once: the run goes on.
                parked.put(key(stop.kind(), stop.place()), stop);
                journal.queue(List.copyOf(parked.values()));
                emit.accept(new ExploreEvent.Parked(id, nowMs.getAsLong()));
                return Paused.PARKED;
            }
            play.idle(settings.poll());
        }
    }

    /** Whether the run ends because a stop that cannot park waited past the stuck time. */
    boolean stuck() {
        return stuck;
    }

    /** Whether the queue holds the stop {@code stopId}. */
    boolean holds(String stopId) {
        return parked.values().stream().anyMatch(s -> s.id().equals(stopId));
    }

    /** The parked stop {@code stopId}; empty when the queue holds none of that id. */
    Optional<Stop> parked(String stopId) {
        return parked.values().stream().filter(s -> s.id().equals(stopId)).findFirst();
    }

    /** Whether a stop of {@code kind} on {@code place} is parked: it is not raised again. */
    boolean parked(StopKind kind, String place) {
        return parked.containsKey(key(kind, place));
    }

    /** Whether a stop of any kind is parked on {@code place}: it is not pressed until one is fixed. */
    boolean avoided(String place) {
        return parked.values().stream().anyMatch(stop -> stop.place().equals(place));
    }

    /** How many stops of {@code kind} on {@code screen} are parked: each one Claude has still to answer. */
    long on(String screen, StopKind kind) {
        return parked.values().stream().filter(stop -> stop.kind() == kind && screen.equals(stop.screen())).count();
    }

    /** How many stops of any kind on {@code screen} are parked. */
    long on(String screen) {
        return parked.values().stream().filter(stop -> screen.equals(stop.screen())).count();
    }

    /** Takes {@code stop} out of the queue, written at once. */
    void unpark(Stop stop) {
        parked.remove(key(stop.kind(), stop.place()));
        journal.queue(List.copyOf(parked.values()));
    }

    /** {@code screen} was built: a low-yield stop parked on it is answered. */
    void built(String screen) {
        parked.values().stream().filter(s -> s.kind() == StopKind.LOW_YIELD && screen.equals(s.screen()))
                .findFirst().ifPresent(s -> {
                    unpark(s);
                    emit.accept(new ExploreEvent.Fixed(s.id(), "its screen was built", nowMs.getAsLong()));
                });
    }

    /** Keeps {@code lines}, left out of the stop {@code stopId}, until a resume of it takes. */
    void keepLeftOut(String stopId, List<String> lines) {
        if (!lines.isEmpty()) leftOut.merge(stopId, lines, (kept, more) -> {
            List<String> all = new ArrayList<>(kept);
            all.addAll(more);
            return List.copyOf(all);
        });
    }

    /** What was left out of the stop {@code stopId}, no longer kept; null when nothing was. */
    List<String> takeLeftOut(String stopId) {
        return leftOut.remove(stopId);
    }
}
