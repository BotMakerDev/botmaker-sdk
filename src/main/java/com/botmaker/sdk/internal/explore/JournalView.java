// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.internal.explore.tree.TreeAnswer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link Journal} read as the plugin side needs it: the latest run's events, its open stop, its parked stops, its
 * counts. The {@code explore} tool and the Explore tab both read the run through it.
 *
 * @param all    every event of the journal, oldest first
 * @param events the latest run's: from its {@link ExploreEvent.Began} on (all of them when none began), through every
 *               {@link ExploreEvent.Restarted} of it
 * @param queue  the parked stops, oldest first
 */
public record JournalView(List<ExploreEvent> all, List<ExploreEvent> events, List<Stop> queue) {

    public JournalView {
        all = List.copyOf(all);
        events = List.copyOf(events);
        queue = List.copyOf(queue);
    }

    /** {@code journal} as it is now. */
    public static JournalView of(Journal journal) {
        return of(journal.events(), journal.queue());
    }

    private static JournalView of(List<ExploreEvent> all, List<Stop> queue) {
        int began = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i) instanceof ExploreEvent.Began) {
                began = i;
                break;
            }
        }
        return new JournalView(all, all.subList(began, all.size()), queue);
    }

    /**
     * Reads one journal again and again, each read parsing only the lines appended since the last (2026-11-20: a poll
     * parsed the whole file each time). One reader's, one thread at a time.
     */
    public static final class Reader {

        private final Journal journal;
        private final List<ExploreEvent> all = new ArrayList<>();
        private long next;

        public Reader(Journal journal) {
            this.journal = journal;
        }

        public Journal journal() {
            return journal;
        }

        /** The journal as it is now: the events read before, the new ones after them. */
        public JournalView view() {
            Journal.Read read = journal.events(next);
            all.addAll(read.events());
            next = read.next();
            return of(all, journal.queue());
        }
    }

    /** Whether a run ever began on this journal. */
    public boolean began() {
        return events.stream().anyMatch(ExploreEvent.Began.class::isInstance);
    }

    /**
     * How the latest run ended; empty while it runs (or was killed before it could say). An end it went on from, by a
     * {@link ExploreEvent.Restarted}, is no end.
     */
    public Optional<ExploreLoop.End> ended() {
        return sinceRestart().stream().filter(ExploreEvent.Ended.class::isInstance)
                .map(e -> ((ExploreEvent.Ended) e).end())
                .map(end -> end == null ? ExploreLoop.End.UNKNOWN : end).reduce((first, last) -> last);
    }

    /** The latest run's events since it last restarted; all of them when it never did. */
    private List<ExploreEvent> sinceRestart() {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Restarted) return events.subList(i, events.size());
        }
        return events;
    }

    /**
     * Whether the latest run left the stop {@code stopId} held as a Discover fault: the last it stopped on, a fault,
     * nothing closing it since, however it ended. A run restarted from its {@link ResumeFile} only then.
     */
    public boolean heldWhenLeft(String stopId) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Stopped(Stop stop, boolean again, long at)) {
                return stop.id().equals(stopId) && !closedAfter(stopId, i)
                        && (stop.kind() == StopKind.FAULT || fault(stopId).isPresent());
            }
        }
        return false;
    }

    /** Whether the latest run began and has not ended. */
    public boolean running() {
        return began() && ended().isEmpty();
    }

    /**
     * The stop the run waits on: the latest it stopped on that was neither fixed, parked, nor reloaded for since. A
     * refused reload leaves it open, and so does a restart: the held fault stays open while its replay runs.
     */
    public Optional<Stop> open() {
        if (!running()) return Optional.empty();
        for (int i = events.size() - 1; i >= 0; i--) {
            if (!(events.get(i) instanceof ExploreEvent.Stopped(Stop stop, boolean again, long at))) continue;
            return closedAfter(stop.id(), i) ? Optional.empty() : Optional.of(stop);
        }
        return Optional.empty();
    }

    /**
     * Whether {@code stop} is a held fault the run is replaying, or about to: since the run last stopped on it, Claude
     * asked {@code reload_sdk} for it or the run {@link ExploreEvent.Restarted restarted}. Its answer is the replay's,
     * so a wait does not answer it again.
     */
    public boolean replaying(Stop stop) {
        for (int i = events.size() - 1; i >= 0; i--) {
            switch (events.get(i)) {
                case ExploreEvent.Stopped(Stop s, boolean again, long at) when s.id().equals(stop.id()) -> {
                    return false;
                }
                case ExploreEvent.Restarted restarted -> {
                    return true;
                }
                case ExploreEvent.Claude(String call, long at) when call.equals(RELOAD_SDK + " " + stop.id()) -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    /** The call a {@code reload_sdk} writes as its {@link ExploreEvent.Claude} event, before the stop's id. */
    public static final String RELOAD_SDK = "reload_sdk";

    private boolean closedAfter(String stopId, int index) {
        for (ExploreEvent event : events.subList(index + 1, events.size())) {
            boolean closes = switch (event) {
                case ExploreEvent.Fixed fixed -> fixed.stopId().equals(stopId);
                case ExploreEvent.ReplayLost lost -> lost.faultId().equals(stopId);
                case ExploreEvent.Parked parked -> parked.stopId().equals(stopId);
                case ExploreEvent.Reloaded reloaded -> reloaded.stopId().equals(stopId) && !reloaded.refused();
                default -> false;
            };
            if (closes) return true;
        }
        return false;
    }

    /**
     * What the run settled the stop {@code stopId} with itself ({@link ExploreEvent.Settled}): the plugin side's to
     * write, never Claude's to fix; empty for a stop Claude is waited on for. The latest run's only: ids start again
     * with each run.
     */
    public List<TreeAnswer> answers(String stopId) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Settled settled && settled.stopId().equals(stopId)) {
                return settled.answers();
            }
        }
        return List.of();
    }

    /** Whether {@code stop} is one the run settled itself, for the plugin side to write. */
    public boolean settled(Stop stop) {
        return !answers(stop.id()).isEmpty();
    }

    /** Whether the latest run plays on an SDK under development, so it raises and holds Discover faults. */
    public boolean dev() {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Began began) return began.dev();
            if (events.get(i) instanceof ExploreEvent.Restarted restarted) return restarted.dev();
        }
        return false;
    }

    /** What made the stop {@code stopId} a Discover fault in the latest run; empty for a plain stop. */
    public Optional<ExploreEvent.Faulted> fault(String stopId) {
        return events.stream().filter(ExploreEvent.Faulted.class::isInstance).map(ExploreEvent.Faulted.class::cast)
                .filter(f -> f.stopId().equals(stopId)).reduce((first, last) -> last);
    }

    /**
     * The latest run's unreleased lease of {@code stopId}: its last {@link ExploreEvent.Leased}, unless a release of
     * that same attempt follows it. A lease past its time is still returned: {@link ExploreEvent.Leased#expired} says
     * so, and the Coordinator takes it back.
     */
    public Optional<ExploreEvent.Leased> leaseOf(String stopId) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Leased leased && leased.stopId().equals(stopId)) {
                boolean released = events.subList(i + 1, events.size()).stream().anyMatch(e ->
                        e instanceof ExploreEvent.Released r && r.stopId().equals(stopId)
                                && r.attempt() == leased.attempt());
                return released ? Optional.empty() : Optional.of(leased);
            }
        }
        return Optional.empty();
    }

    /** How many times the latest run tried {@code stopId}: its highest leased attempt, 0 when never leased. */
    public int attempts(String stopId) {
        return events.stream().filter(e -> e instanceof ExploreEvent.Leased leased && leased.stopId().equals(stopId))
                .mapToInt(e -> ((ExploreEvent.Leased) e).attempt()).max().orElse(0);
    }

    /** The latest run's findings, oldest first. */
    public List<ExploreEvent.Finding> findings() {
        return events.stream().filter(ExploreEvent.Finding.class::isInstance).map(ExploreEvent.Finding.class::cast)
                .toList();
    }

    /**
     * When the Coordinator last said it was alive in the latest run, since it last restarted (a restarted run has a new
     * Coordinator); empty when it never did.
     */
    public Optional<Instant> lastHeartbeat() {
        List<ExploreEvent> since = sinceRestart();
        for (int i = since.size() - 1; i >= 0; i--) {
            if (since.get(i) instanceof ExploreEvent.Heartbeat beat) return Optional.of(Instant.ofEpochMilli(beat.at()));
        }
        return Optional.empty();
    }

    /** Whether the edit {@code editId} reached the bot in the latest run. */
    public boolean applied(String editId) {
        return events.stream().anyMatch(e -> e instanceof ExploreEvent.EditApplied edit && edit.editId().equals(editId));
    }

    /**
     * Whether {@code stop} is held as a Discover fault now: the stop the run waits on, raised as one or called one by
     * Claude. A fault dropped by a resume with ignore is held no more.
     */
    public boolean held(Stop stop) {
        if (open().filter(s -> s.id().equals(stop.id())).isEmpty()) return false;
        return stop.kind() == StopKind.FAULT || fault(stop.id()).isPresent();
    }

    /** A stop the user's queue lists (2026-11-21): the stop, and what the user is to decide on it. */
    public record ForUser(Stop stop, String question) {}

    /**
     * The user's queue: the parked stops that are the user's or that Claude handed them, oldest first, then the open
     * stop when it is the user's; none once the run ended, as nothing can be resumed then.
     */
    public List<ForUser> forUser() {
        if (!running()) return List.of();
        List<ForUser> mine = new ArrayList<>();
        for (Stop stop : queue) {
            if (roleOf(stop) == StopRole.USER) mine.add(new ForUser(stop, asked(stop.id()).orElse(stop.missing())));
        }
        open().filter(s -> roleOf(s) == StopRole.USER && parked(s.id()).isEmpty())
                .ifPresent(s -> mine.add(new ForUser(s, asked(s.id()).orElse(s.missing()))));
        return List.copyOf(mine);
    }

    /**
     * Who takes {@code stop} now: the user once Claude handed it to them, the Mechanic once Claude called it a Discover
     * fault, else its kind's role.
     */
    public StopRole roleOf(Stop stop) {
        if (asked(stop.id()).isPresent()) return StopRole.USER;
        return fault(stop.id()).isPresent() ? StopRole.MECHANIC : stop.role();
    }

    /** The question Claude handed the stop {@code stopId} to the user with, since the run last stopped on it. */
    public Optional<String> asked(String stopId) {
        for (int i = events.size() - 1; i >= 0; i--) {
            switch (events.get(i)) {
                case ExploreEvent.Asked asked when asked.stopId().equals(stopId) -> {
                    return Optional.of(asked.question());
                }
                case ExploreEvent.Stopped stopped when stopped.stop().id().equals(stopId) -> {
                    return Optional.empty();
                }
                default -> {
                }
            }
        }
        return Optional.empty();
    }

    /** The parked stop {@code stopId}, if the queue holds it. */
    public Optional<Stop> parked(String stopId) {
        return queue.stream().filter(s -> s.id().equals(stopId)).findFirst();
    }

    /** The latest reload answered for {@code stopId}, after the last time the run stopped on it. */
    public Optional<ExploreEvent.Reloaded> reloadOf(String stopId) {
        ExploreEvent.Reloaded last = null;
        for (ExploreEvent event : all) {
            if (event instanceof ExploreEvent.Stopped(Stop stop, boolean again, long at) && stop.id().equals(stopId)) {
                last = null;
            } else if (event instanceof ExploreEvent.Reloaded reloaded && reloaded.stopId().equals(stopId)) {
                last = reloaded;
            }
        }
        return Optional.ofNullable(last);
    }

    /** The screens the latest run found since Claude last waited on it, oldest first. */
    public List<String> newScreensSinceWait() {
        int since = 0;
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Claude(String call, long at) && call.startsWith(WAIT)) {
                since = i + 1;
                break;
            }
        }
        return events.subList(since, events.size()).stream().filter(ExploreEvent.NewScreen.class::isInstance)
                .map(e -> ((ExploreEvent.NewScreen) e).screen()).toList();
    }

    /** The call a {@code wait} writes as its {@link ExploreEvent.Claude} event. */
    public static final String WAIT = "wait";

    /** The screen the latest run was last on; empty before it reached one. */
    public Optional<String> where() {
        for (int i = events.size() - 1; i >= 0; i--) {
            String screen = switch (events.get(i)) {
                case ExploreEvent.NewScreen e -> e.screen();
                case ExploreEvent.Tried e -> e.screen();
                case ExploreEvent.Refused e -> e.screen();
                case ExploreEvent.Stopped e -> e.stop().screen();
                default -> null;
            };
            if (screen != null) return Optional.of(screen);
        }
        return Optional.empty();
    }

    /** The generation the latest run plays: its last reload that took; 1 before any. */
    public int generation() {
        return events.stream().filter(ExploreEvent.Reloaded.class::isInstance).map(ExploreEvent.Reloaded.class::cast)
                .filter(r -> !r.refused()).mapToInt(ExploreEvent.Reloaded::generation).reduce((a, b) -> b).orElse(1);
    }

    /** When the latest run began, epoch milliseconds; 0 when none did. */
    public long beganAt() {
        return events.stream().filter(ExploreEvent.Began.class::isInstance).mapToLong(ExploreEvent::at).findFirst()
                .orElse(0);
    }

    /**
     * When the latest run's own process began playing, epoch milliseconds: its last {@link ExploreEvent.Restarted}, a
     * new process on the rebuilt SDK, else {@link #beganAt}.
     */
    public long playingSince() {
        List<ExploreEvent> since = sinceRestart();
        return since != events && !since.isEmpty() ? since.getFirst().at() : beganAt();
    }

    /** How long the latest run has run: to its end when it ended, else to {@code nowMs}. */
    public Duration ranFor(long nowMs) {
        if (beganAt() == 0) return Duration.ZERO;
        long until = sinceRestart().stream().filter(ExploreEvent.Ended.class::isInstance).mapToLong(ExploreEvent::at)
                .reduce((a, b) -> b).orElse(nowMs);
        return Duration.ofMillis(Math.max(0, until - beganAt()));
    }

    /**
     * Whether the latest run parked a stop since Claude last waited on it as {@code role} (null: a wait on every
     * stop), so one role's wait never uses up the news of a stop parked for another.
     */
    public boolean parkedSinceWait(StopRole role) {
        String mine = waitCall(role);
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Parked) return true;
            if (events.get(i) instanceof ExploreEvent.Claude(String call, long at) && call.equals(mine)) return false;
        }
        return false;
    }

    /** The call a wait as {@code role} writes: {@link #WAIT}, then the role's id when it keeps to one. */
    public static String waitCall(StopRole role) {
        return role == null ? WAIT : WAIT + " " + role.id();
    }

    /** How many of the latest run's events are {@code type}. */
    public long count(Class<? extends ExploreEvent> type) {
        return events.stream().filter(type::isInstance).count();
    }

    /**
     * How far the latest run built the map: each screen built with its clock, and each screen it left open, as it
     * last left it, longest clock first, and the screens set aside over their budget and not built since.
     */
    public record Build(List<ExploreEvent.Built> built, List<ExploreEvent.Building> open, Set<String> aside) {

        public Build {
            built = List.copyOf(built);
            open = List.copyOf(open);
            aside = Set.copyOf(aside);
        }

        /** The median time a built screen took; zero when none is. */
        public Duration median() {
            if (built.isEmpty()) return Duration.ZERO;
            long[] ms = built.stream().mapToLong(ExploreEvent.Built::ms).sorted().toArray();
            return Duration.ofMillis(ms.length % 2 == 1 ? ms[ms.length / 2]
                    : (ms[ms.length / 2 - 1] + ms[ms.length / 2]) / 2);
        }
    }

    /** A screen's clock said short: {@code 1m40s}, {@code 2m}, or {@code 40s} under a minute. */
    public static String took(long ms) {
        long s = ms / 1000;
        return s < 60 ? s + "s" : s / 60 + "m" + (s % 60 == 0 ? "" : s % 60 + "s");
    }

    /** The latest run's {@link Build}. */
    public Build build() {
        Map<String, ExploreEvent.Built> built = new LinkedHashMap<>();
        Map<String, ExploreEvent.Building> open = new LinkedHashMap<>();
        Set<String> aside = new HashSet<>();
        for (ExploreEvent event : events) {
            switch (event) {
                // The latest says it: a built screen a parked stop opens again is building until built again.
                case ExploreEvent.Built b -> {
                    built.put(b.screen(), b);
                    open.remove(b.screen());
                    aside.remove(b.screen());
                }
                case ExploreEvent.SetAside a -> aside.add(a.screen());
                case ExploreEvent.Building b -> {
                    open.put(b.screen(), b);
                    built.remove(b.screen());
                }
                default -> {}
            }
        }
        return new Build(List.copyOf(built.values()), open.values().stream()
                .sorted(Comparator.comparingLong(ExploreEvent.Building::ms).reversed()).toList(), aside);
    }
}
