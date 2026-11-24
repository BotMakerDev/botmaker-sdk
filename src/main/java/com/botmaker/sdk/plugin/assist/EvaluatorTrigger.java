// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.JournalView;

import java.util.List;
import java.util.Optional;

/**
 * When the explore crew's Evaluator is started (spec 2026-11-24-explore-agents-design § The crew): read off the journal
 * alone, so a restarted core decides the same. It is due after {@link Settings#k} presses that found no new screen and
 * ranked nothing better than before, at least every {@link Settings#every} presses whatever the progress, and once at
 * the run's end. An evaluation is leased like a stop, by an id of its own ({@code evaluation-1}), one at a time.
 */
final class EvaluatorTrigger {

    /** The prefix of an evaluation's lease id. */
    static final String PREFIX = "evaluation-";

    /** {@code k}: presses without progress before the run is judged; {@code every}: presses between two at most. */
    record Settings(int k, int every) {
        /** The plan's start values. */
        static final Settings DEFAULT = new Settings(8, 25);
    }

    /** Why an evaluation was started: said in its prompt. */
    enum Why {
        /** K presses found nothing new and ranked nothing better. */
        STALLED("stalled", "K presses found no new screen and no better press"),
        /** The safety net: many presses since the last evaluation, whatever they found. */
        SAFETY_NET("safety_net", "many presses since the last evaluation"),
        /** The run ended: judge it, and grade the map it leaves. */
        END("end", "the run ended"),
        UNKNOWN("unknown", "unknown");

        private final String id;
        private final String displayName;

        Why(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        String id() {
            return id;
        }

        String displayName() {
            return displayName;
        }

        /** The reason whose {@link #id()} is {@code id}, case aside; {@link #UNKNOWN} for any other. */
        static Why fromId(String id) {
            for (Why why : values()) {
                if (why.id.equalsIgnoreCase(id == null ? "" : id.strip())) return why;
            }
            return UNKNOWN;
        }
    }

    private EvaluatorTrigger() {}

    /** The lease id of the run's {@code n}th evaluation. */
    static String idOf(int n) {
        return PREFIX + n;
    }

    /** Whether {@code stopId} is an evaluation's lease id, not a stop's. */
    static boolean isEvaluation(String stopId) {
        return stopId != null && stopId.startsWith(PREFIX);
    }

    /**
     * Whether a mid-run evaluation is due on {@code view}, and why: counted from the run's latest evaluation lease (or
     * its start), a press each {@link ExploreEvent.Tried}. Whether one is held already is the caller's to check.
     */
    static Optional<Why> due(JournalView view, Settings settings) {
        int sinceProgress = 0;
        int sinceEvaluation = 0;
        int best = Integer.MIN_VALUE;
        for (ExploreEvent event : view.events()) {
            switch (event) {
                case ExploreEvent.Leased leased when isEvaluation(leased.stopId()) -> {
                    sinceProgress = 0;
                    sinceEvaluation = 0;
                }
                case ExploreEvent.NewScreen ignored -> sinceProgress = 0;
                case ExploreEvent.Ranked ranked -> {
                    if (ranked.rank() > best) {
                        best = ranked.rank();
                        sinceProgress = 0;
                    }
                }
                case ExploreEvent.Tried ignored -> {
                    sinceProgress++;
                    sinceEvaluation++;
                }
                default -> {}
            }
        }
        if (sinceEvaluation >= settings.every()) return Optional.of(Why.SAFETY_NET);
        if (sinceProgress >= settings.k() && sinceEvaluation >= settings.k()) return Optional.of(Why.STALLED);
        return Optional.empty();
    }

    /** The evaluation lease the run holds now, past its time or not; empty when none is out. */
    static Optional<ExploreEvent.Leased> held(JournalView view) {
        List<ExploreEvent> events = view.events();
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Leased leased && isEvaluation(leased.stopId())) {
                return view.leaseOf(leased.stopId());
            }
        }
        return Optional.empty();
    }

    /** The id the run's next evaluation is leased by. */
    static String nextId(JournalView view) {
        long leased = view.events().stream().filter(e -> e instanceof ExploreEvent.Leased l && isEvaluation(l.stopId())
                && l.attempt() == 1).count();
        return idOf((int) leased + 1);
    }

    /** Whether the run ended and its end evaluation has not been started yet. */
    static boolean atEnd(JournalView view) {
        if (view.ended().isEmpty()) return false;
        List<ExploreEvent> events = view.events();
        for (int i = events.size() - 1; i >= 0; i--) {
            ExploreEvent event = events.get(i);
            if (event instanceof ExploreEvent.Ended) return true;
            if (event instanceof ExploreEvent.Leased leased && isEvaluation(leased.stopId())) return false;
        }
        return false;
    }
}
