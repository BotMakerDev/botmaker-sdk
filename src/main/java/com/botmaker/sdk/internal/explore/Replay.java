// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The replay of a Discover fault by a run restarted on the rebuilt SDK, and the memory a held fault writes for that
 * restart (split from {@link ExploreLoop}, 2026-11-21). The replay waits until the run passes the fault's place, which
 * says it is fixed, or meets it again; a run started fresh replays nothing.
 */
final class Replay {

    private final Journal journal;
    private final LongSupplier nowMs;
    private final Consumer<ExploreEvent> emit;
    /** The memory the run goes on from, until the replay said; null for a run started fresh, or once it said. */
    private ResumeFile file;
    /** The arguments the run was started with, kept in its memory for a restart started with none. */
    private ExploreBudget startedBudget = ExploreBudget.NONE;
    private DiscoverArgs startedDiscover;

    Replay(Journal journal, LongSupplier nowMs, Consumer<ExploreEvent> emit) {
        this.journal = journal;
        this.nowMs = nowMs;
        this.emit = emit;
    }

    /** Goes on from {@code file}; none: the run replays nothing. */
    void from(Optional<ResumeFile> memory) {
        file = memory.orElse(null);
    }

    /** Keeps {@code budget} and {@code discover} (null for <em>Keep exploring</em>) for the memory a fault writes. */
    void startedWith(ExploreBudget budget, DiscoverArgs discover) {
        startedBudget = budget;
        startedDiscover = discover;
    }

    /** The memory being replayed; empty when nothing is. */
    Optional<ResumeFile> file() {
        return Optional.ofNullable(file);
    }

    /** Whether a fault is being replayed. */
    boolean on() {
        return file != null;
    }

    /** The fault being replayed; only while {@link #on}. */
    Stop fault() {
        return file.fault();
    }

    /** Whether {@code stopId} is the fault being replayed. */
    boolean of(String stopId) {
        return file != null && file.fault().id().equals(stopId);
    }

    /** Whether {@code kind} on {@code place} is the stop the fault being replayed was. */
    boolean replays(StopKind kind, String place) {
        return file != null && file.replays() == kind && file.fault().place().equals(place);
    }

    /** Whether the fault being replayed is on {@code place}. */
    boolean on(String place) {
        return file != null && file.fault().place().equals(place);
    }

    /** Whether the fault being replayed is on {@code screen}. */
    boolean onScreen(String screen) {
        return file != null && screen.equals(file.fault().screen());
    }

    /** Whether the fault being replayed was a crash, its place naming {@code press} (null: a round's, no press). */
    boolean crashedIn(String press) {
        return file != null && file.replays() == StopKind.CRASH
                && Objects.equals(ExploreLoop.pressOf(file.fault().place()), press);
    }

    /** Closes the fault being replayed, fixed for {@code why}: its memory is no longer needed. */
    void fixed(String why) {
        emit.accept(new ExploreEvent.Fixed(file.fault().id(), why, nowMs.getAsLong()));
        ResumeFile.delete(journal);
        file = null;
    }

    /**
     * The replay of the fault can no longer happen: a stop of another kind met its place first and set it aside. Said,
     * and its memory deleted, so the next start does not replay it.
     */
    void lost(String why) {
        emit.accept(new ExploreEvent.ReplayLost(file.fault().id(), why, nowMs.getAsLong()));
        ResumeFile.delete(journal);
        file = null;
    }

    /** The fault met again: held anew by the stop now raised, whose own memory takes over. */
    void met() {
        file = null;
    }

    /** The kind of stop the fault being replayed was; only while {@link #on}. */
    StopKind kind() {
        return file.replays();
    }

    /** What found the fault being replayed; only while {@link #on}. */
    String source() {
        return file.source();
    }

    /** The run's memory, for a restart on the rebuilt SDK to go on from the fault {@code fault}. */
    ResumeFile memory(Stop fault, StopKind replays, String source, Set<String> seen, Set<String> tried,
                      Set<String> worked, Set<String> answered) {
        return new ResumeFile(fault, replays, source, seen, tried, worked, answered, startedBudget, startedDiscover);
    }
}
