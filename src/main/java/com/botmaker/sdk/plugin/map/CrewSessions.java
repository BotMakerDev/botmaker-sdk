// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.internal.explore.Agent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.StopRole;
import com.botmaker.sdk.plugin.assist.WorkerLog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One agent's sessions, read off its workers' logs as they grow (Explore page, Crew tab): each stop leased to the
 * agent's role has its log ({@link Journal#workerLog}), which keeps every session of that stop one after the other,
 * each opened by a {@link WorkerLog.Kind#START} line. Each log is read on from where the last read left it.
 *
 * <p>Read by one thread at a time; what it hands out is a copy.
 */
final class CrewSessions {

    /** How many lines a session keeps: its newest, the older dropped. */
    static final int KEPT = 2_000;

    /** One session of a stop's worker: {@code number} counts from 1 in its log. */
    record Session(String stopId, int number, List<WorkerLog.Line> lines) {

        /** How the session picker names it: "unknown-1", or "unknown-1 · try 2" for a stop's later session. */
        String title() {
            return number == 1 ? stopId : stopId + " · try " + number;
        }

        @Override
        public String toString() {
            return title();
        }
    }

    /** A stop's log as read so far. */
    private static final class Log {
        long next;
        final List<List<WorkerLog.Line>> sessions = new ArrayList<>();
    }

    private Path folder;
    private Agent agent;
    private List<String> stops = List.of();
    private final Map<String, Log> logs = new HashMap<>();

    /** The role whose workers' logs are {@code agent}'s; empty for the agents with no worker, the run's own code. */
    static Optional<StopRole> roleOf(Agent agent) {
        return switch (agent == null ? Agent.UNKNOWN : agent) {
            case READER -> Optional.of(StopRole.CARTOGRAPHER);
            case EVALUATOR -> Optional.of(StopRole.EVALUATOR);
            case MECHANIC -> Optional.of(StopRole.MECHANIC);
            case EXPLORER, COORDINATOR, UNKNOWN -> Optional.empty();
        };
    }

    /**
     * {@code agent}'s sessions in {@code journal}'s run as they are now, oldest first; empty when nothing changed since
     * the last read for that same agent and run, unless {@code whole} (the tab shows none of them now). A run or agent
     * other than the last read's is read from its start.
     */
    synchronized Optional<List<Session>> read(Journal journal, JournalView view, Agent agent, boolean whole) {
        boolean other = whole || !journal.folder().equals(folder) || agent != this.agent;
        if (!journal.folder().equals(folder)) logs.clear();
        folder = journal.folder();
        this.agent = agent;
        List<String> now = roleOf(agent).map(view::leased).orElse(List.of());
        boolean changed = other || !now.equals(stops);
        stops = now;
        for (String stopId : now) {
            Log log = logs.computeIfAbsent(stopId, id -> new Log());
            WorkerLog.Read read = WorkerLog.read(journal.workerLog(stopId), log.next);
            log.next = read.next();
            for (WorkerLog.Line line : read.lines()) {
                if (line.kind() == WorkerLog.Kind.START || log.sessions.isEmpty()) log.sessions.add(new ArrayList<>());
                List<WorkerLog.Line> session = log.sessions.getLast();
                session.add(line);
                if (session.size() > KEPT) session.removeFirst();
                changed = true;
            }
        }
        return changed ? Optional.of(current()) : Optional.empty();
    }

    /** The sessions as last read, oldest first. */
    synchronized List<Session> current() {
        List<Session> all = new ArrayList<>();
        for (String stopId : stops) {
            List<List<WorkerLog.Line>> kept = logs.get(stopId).sessions;
            for (int i = 0; i < kept.size(); i++) all.add(new Session(stopId, i + 1, List.copyOf(kept.get(i))));
        }
        return all;
    }
}
