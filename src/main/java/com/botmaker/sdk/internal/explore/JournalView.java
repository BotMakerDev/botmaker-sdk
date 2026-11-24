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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Predicate;

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

    /**
     * The latest run's stops leased to {@code role}, each once, in the order first leased: the stops whose worker logs
     * ({@code Journal.workerLog}) that role's sessions wrote.
     */
    public List<String> leased(StopRole role) {
        return events.stream().filter(e -> e instanceof ExploreEvent.Leased leased && leased.role() == role)
                .map(e -> ((ExploreEvent.Leased) e).stopId()).distinct().toList();
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

    /** Whether the edit {@code editId} was carried out in the latest run, taken or refused. */
    public boolean applied(String editId) {
        return appliedOf(editId).isPresent();
    }

    /** How the edit {@code editId} was carried out in the latest run; empty while it was not. */
    public Optional<ExploreEvent.EditApplied> appliedOf(String editId) {
        return events.stream().filter(e -> e instanceof ExploreEvent.EditApplied edit && edit.editId().equals(editId))
                .map(ExploreEvent.EditApplied.class::cast).findFirst();
    }

    /** Whether the edit {@code editId} is queued in the latest run, carried out or not. */
    public boolean queued(String editId) {
        return queuedOf(editId).isPresent();
    }

    /** The edit {@code editId} as first queued in the latest run; empty while it was not. */
    public Optional<ExploreEvent.EditQueued> queuedOf(String editId) {
        return events.stream().filter(e -> e instanceof ExploreEvent.EditQueued edit && edit.editId().equals(editId))
                .map(ExploreEvent.EditQueued.class::cast).findFirst();
    }

    /** The edits queued in the latest run and not carried out yet, oldest first, each id once. */
    public List<ExploreEvent.EditQueued> unapplied() {
        Set<String> seen = new HashSet<>();
        events.stream().filter(ExploreEvent.EditApplied.class::isInstance)
                .forEach(e -> seen.add(((ExploreEvent.EditApplied) e).editId()));
        return events.stream().filter(ExploreEvent.EditQueued.class::isInstance).map(ExploreEvent.EditQueued.class::cast)
                .filter(edit -> seen.add(edit.editId())).toList();
    }

    /** What the latest run explores toward, in plain words or a screen's name; blank for none. */
    public String goal() {
        return events.stream().filter(ExploreEvent.Began.class::isInstance).map(ExploreEvent.Began.class::cast)
                .map(ExploreEvent.Began::goal).findFirst().orElse("");
    }

    /**
     * The presses the latest run ranked and has not made since, each by its latest rank, in the order first ranked: the
     * run's frontier as the journal says it (parking aside, which only the run knows).
     */
    public List<ExploreEvent.Ranked> openRanks() {
        Map<String, ExploreEvent.Ranked> open = new LinkedHashMap<>();
        for (ExploreEvent event : events) {
            switch (event) {
                case ExploreEvent.Ranked r -> open.put(r.screen() + "." + r.press(), r);
                case ExploreEvent.Tried t -> open.remove(t.screen() + "." + t.by());
                default -> {}
            }
        }
        return List.copyOf(open.values());
    }

    /**
     * {@code screen} and every screen the latest run first reached from one of them: the part of the map a press of
     * {@code screen} leads into, as this run found it.
     */
    public Set<String> under(String screen) {
        Set<String> under = new LinkedHashSet<>();
        if (screen == null || screen.isBlank()) return under;
        under.add(screen);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (ExploreEvent event : events) {
                if (event instanceof ExploreEvent.NewScreen found && found.from() != null
                        && under.contains(found.from())) {
                    grew |= under.add(found.screen());
                }
            }
        }
        return under;
    }

    /**
     * The latest {@link FindingKind#STUCK} finding of the run since it last restarted that an agent said through the
     * explore tool's {@code record_finding}: the call it wrote just before is the last of Claude's before the finding.
     * The run's own STUCK, which it ends on itself, is not one.
     */
    public Optional<ExploreEvent.Finding> stuckSaid() {
        ExploreEvent.Finding said = null;
        String lastCall = "";
        for (ExploreEvent event : sinceRestart()) {
            if (event instanceof ExploreEvent.Claude claude) lastCall = claude.call();
            if (event instanceof ExploreEvent.Finding f && f.kind() == FindingKind.STUCK) {
                if (lastCall.equals(STUCK_CALL)) said = f;
                lastCall = "";
            }
        }
        return Optional.ofNullable(said);
    }

    /** The call the explore tool writes before an agent's STUCK finding. */
    public static final String STUCK_CALL = "record_finding " + FindingKind.STUCK.id();

    /**
     * The latest run's findings nothing acted on, oldest first, each said once: all but a drift (its ranks were lowered
     * when it was said) and, once the goal was witnessed, a done proposed without a witness before it.
     */
    public List<ExploreEvent.Finding> unresolved() {
        boolean witnessed = events.stream().anyMatch(ExploreEvent.Witnessed.class::isInstance);
        Set<String> said = new HashSet<>();
        // A wrong map the Reader redrew: its stop, said at the finding's time and words, was released done.
        Set<String> redrawn = new HashSet<>();
        for (Stop flagged : foundBadMaps()) {
            if (events.stream().anyMatch(e -> e instanceof ExploreEvent.Released r && r.stopId().equals(flagged.id())
                    && r.ok())) {
                redrawn.add(flagged.at() + "\n" + flagged.missing());
            }
        }
        return findings().stream().filter(f -> f.kind() != FindingKind.DRIFT)
                .filter(f -> !(witnessed && f.kind() == FindingKind.NO_WITNESS))
                .filter(f -> !(f.kind() == FindingKind.BAD_MAP && redrawn.contains(f.at() + "\n" + f.why())))
                .filter(f -> said.add(f.kind().id() + "\n" + f.why())).toList();
    }

    /** Whether the latest run's summary is written: once it ended, after its end. */
    public boolean summed() {
        List<ExploreEvent> since = sinceRestart();
        for (int i = since.size() - 1; i >= 0; i--) {
            if (since.get(i) instanceof ExploreEvent.Summary) return true;
            if (since.get(i) instanceof ExploreEvent.Ended) return false;
        }
        return false;
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
    public record ForUser(Stop stop, String question) {

        /** Whether the run can resume it: a stop of the run's, not a fault found by the Evaluator. */
        public boolean resumable() {
            return !stop.id().startsWith(FOUND);
        }
    }

    /** What the id of a {@linkplain #foundFaults() found fault}'s stop begins with; no stop of the run's does. */
    private static final String FOUND = "finding-";

    /**
     * The latest run's {@link FindingKind#SDK_FAULT} findings as the Mechanic's stops, oldest first, each said once.
     * The run never stopped on one: it goes on meanwhile. Their ids count from 1 and never move, as the journal only
     * grows.
     */
    public List<Stop> foundFaults() {
        Set<String> said = new HashSet<>();
        List<Stop> faults = new ArrayList<>();
        for (ExploreEvent.Finding f : findings()) {
            if (f.kind() != FindingKind.SDK_FAULT || !said.add(f.why())) continue;
            String id = FOUND + (faults.size() + 1);
            faults.add(new Stop(id, StopKind.FAULT, null, id, "", List.of(), List.of(), Map.of(), List.of(),
                    f.why() + (f.evidence().isBlank() ? "" : " (evidence: " + f.evidence() + ")"), f.at()));
        }
        return List.copyOf(faults);
    }

    /** What the id of a {@linkplain #foundBadMaps() wrong map}'s stop begins with; no stop of the run's does. */
    private static final String FLAGGED = StopKind.BAD_MAP.id() + "-";

    /**
     * The latest run's wrong maps as the Reader's stops, oldest first (device drive 2026-11-24, F3): one per
     * {@link ExploreEvent.MapFlagged}, on the frame the Evaluator judged, unless one for the same screen was still
     * untried or under way when it was said. The run never stopped on one: it goes on meanwhile. Their ids count from
     * 1 and never move: each is decided by what was written before it, and the journal only grows.
     */
    public List<Stop> foundBadMaps() {
        Set<String> released = new HashSet<>();
        List<Stop> flagged = new ArrayList<>();
        for (ExploreEvent event : events) {
            if (event instanceof ExploreEvent.Released r) released.add(r.stopId());
            if (!(event instanceof ExploreEvent.MapFlagged f) || f.screen().isBlank()) continue;
            if (flagged.stream().anyMatch(s -> s.screen().equalsIgnoreCase(f.screen())
                    && !released.contains(s.id()))) {
                continue;
            }
            String id = FLAGGED + (flagged.size() + 1);
            flagged.add(new Stop(id, StopKind.BAD_MAP, f.screen(), f.screen(), f.frame(), List.of(), List.of(),
                    Map.of(), List.of(), f.why(), f.at()));
        }
        return List.copyOf(flagged);
    }

    /** The stops the run never stopped on, which its crew takes beside it: the faults and the wrong maps found. */
    public List<Stop> found() {
        List<Stop> found = new ArrayList<>(foundFaults());
        found.addAll(foundBadMaps());
        return List.copyOf(found);
    }

    /** What the Mechanic left of the stop {@code stopId} in the latest run; empty before it tried. */
    public Optional<ExploreEvent.Branched> branchOf(String stopId) {
        return events.stream().filter(e -> e instanceof ExploreEvent.Branched b && b.stopId().equals(stopId))
                .map(ExploreEvent.Branched.class::cast).reduce((first, last) -> last);
    }

    /** What the user's queue says of the found fault {@code fault}: how far the Mechanic is with it. */
    private String mechanicSaid(Stop fault) {
        Optional<ExploreEvent.Branched> left = branchOf(fault.id());
        if (left.isPresent()) {
            return fault.missing() + " — " + (left.get().branch().isBlank() ? "the Mechanic changed nothing: "
                    : "on branch " + left.get().branch() + ", yours to merge: ") + left.get().note();
        }
        if (leaseOf(fault.id()).isPresent()) return fault.missing() + " — the Mechanic is on it";
        return fault.missing() + (attempts(fault.id()) > 0 ? " — the Mechanic's try left nothing" : "");
    }

    /**
     * The user's queue: the parked stops that are the user's or that Claude handed them, oldest first, then the open
     * stop when it is the user's, then the SDK faults the Evaluator found, which the Mechanic may have left a branch
     * for; none once the run ended, as nothing can be resumed then.
     */
    public List<ForUser> forUser() {
        if (!running()) return List.of();
        List<ForUser> mine = new ArrayList<>();
        for (Stop stop : queue) {
            if (roleOf(stop) == StopRole.USER) mine.add(new ForUser(stop, asked(stop.id()).orElse(stop.missing())));
        }
        open().filter(s -> roleOf(s) == StopRole.USER && parked(s.id()).isEmpty())
                .ifPresent(s -> mine.add(new ForUser(s, asked(s.id()).orElse(s.missing()))));
        foundFaults().forEach(fault -> mine.add(new ForUser(fault, mechanicSaid(fault))));
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
            // A stop on a screen not on the map names none: the run is on no screen of it.
            if (screen != null) return screen.isBlank() ? Optional.empty() : Optional.of(screen);
        }
        return Optional.empty();
    }

    /** The latest screen of the map the latest run was on, even when it is off the map now; empty before any. */
    private Optional<String> lastScreen() {
        for (int i = events.size() - 1; i >= 0; i--) {
            String screen = switch (events.get(i)) {
                case ExploreEvent.NewScreen e -> e.screen();
                case ExploreEvent.Tried e -> e.screen();
                case ExploreEvent.Refused e -> e.screen();
                case ExploreEvent.Stopped e -> e.stop().screen();
                default -> null;
            };
            if (screen != null && !screen.isBlank()) return Optional.of(screen);
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

    /** When the latest run last pressed, epoch milliseconds; empty before it pressed. */
    public OptionalLong lastPress() {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Tried tried) return OptionalLong.of(tried.at());
        }
        return OptionalLong.empty();
    }

    /** The stop {@code stopId} as the latest run last stopped on it, or one {@linkplain #found() found}; else empty. */
    public Optional<Stop> stopOf(String stopId) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof ExploreEvent.Stopped(Stop stop, boolean again, long at)
                    && stop.id().equals(stopId)) {
                return Optional.of(stop);
            }
        }
        return found().stream().filter(f -> f.id().equals(stopId)).findFirst();
    }

    /** Whether the latest run has a crew: only a run given a goal does (the Coordinator and its workers). */
    public boolean crewed() {
        return !goal().isBlank();
    }

    /**
     * What {@code agent} is doing in the latest run at {@code nowMs}, for its card on the Explore page; the Coordinator
     * is silent once its heartbeat is older than {@code silentAfter}.
     */
    public AgentSummary agentSummary(Agent agent, long nowMs, Duration silentAfter) {
        return switch (agent == null ? Agent.UNKNOWN : agent) {
            case EXPLORER -> explorer(nowMs);
            case COORDINATOR -> coordinator(nowMs, silentAfter);
            case READER -> worker(Agent.READER, StopRole.CARTOGRAPHER, nowMs);
            case EVALUATOR -> worker(Agent.EVALUATOR, StopRole.EVALUATOR, nowMs);
            case MECHANIC -> worker(Agent.MECHANIC, StopRole.MECHANIC, nowMs);
            case UNKNOWN -> new AgentSummary(Agent.UNKNOWN, AgentSummary.State.UNKNOWN, "", "", Optional.empty(), 0,
                    Duration.ZERO);
        };
    }

    private AgentSummary explorer(long nowMs) {
        Optional<Stop> open = open();
        AgentSummary.State state = !running() ? AgentSummary.State.ENDED
                : open.isPresent() && !settled(open.get()) ? AgentSummary.State.WAITING : AgentSummary.State.WORKING;
        String frame = open.map(Stop::framePath).filter(f -> !f.isBlank()).orElseGet(this::lastFrame);
        return new AgentSummary(Agent.EXPLORER, state, open.map(Stop::id).orElse(""), frame,
                latest(e -> author(e) == Agent.EXPLORER), (int) count(ExploreEvent.Tried.class), ranFor(nowMs));
    }

    /**
     * Who wrote {@code event}, as the feed's chip says it: the run's own records the Explorer's, leases the
     * Coordinator's, findings the Evaluator's (a STUCK the run or the watchdog says too), branches the Mechanic's, and
     * ranks, edits and questions handed to the user the Reader's. {@link Agent#UNKNOWN} for Claude's calls and
     * heartbeats.
     */
    public static Agent author(ExploreEvent event) {
        return switch (event) {
            case ExploreEvent.Began e -> Agent.EXPLORER;
            case ExploreEvent.Ended e -> Agent.EXPLORER;
            case ExploreEvent.Pictured e -> Agent.EXPLORER;
            case ExploreEvent.Settled e -> Agent.EXPLORER;
            case ExploreEvent.NewScreen e -> Agent.EXPLORER;
            case ExploreEvent.Tried e -> Agent.EXPLORER;
            case ExploreEvent.Refused e -> Agent.EXPLORER;
            case ExploreEvent.GoalMet e -> Agent.EXPLORER;
            case ExploreEvent.Built e -> Agent.EXPLORER;
            case ExploreEvent.Building e -> Agent.EXPLORER;
            case ExploreEvent.SetAside e -> Agent.EXPLORER;
            case ExploreEvent.Stopped e -> Agent.EXPLORER;
            case ExploreEvent.Fixed e -> Agent.EXPLORER;
            case ExploreEvent.Parked e -> Agent.EXPLORER;
            case ExploreEvent.Faulted e -> Agent.EXPLORER;
            case ExploreEvent.ReplayLost e -> Agent.EXPLORER;
            case ExploreEvent.Restarted e -> Agent.EXPLORER;
            case ExploreEvent.Reloaded e -> Agent.EXPLORER;
            case ExploreEvent.Witnessed e -> Agent.EXPLORER;
            case ExploreEvent.Leased e -> Agent.COORDINATOR;
            case ExploreEvent.Released e -> Agent.COORDINATOR;
            case ExploreEvent.Finding e -> Agent.EVALUATOR;
            case ExploreEvent.MapFlagged e -> Agent.EVALUATOR;
            case ExploreEvent.Summary e -> Agent.EVALUATOR;
            case ExploreEvent.Branched e -> Agent.MECHANIC;
            case ExploreEvent.Ranked e -> Agent.READER;
            case ExploreEvent.EditQueued e -> Agent.READER;
            case ExploreEvent.EditApplied e -> Agent.READER;
            case ExploreEvent.Asked e -> Agent.READER;
            case ExploreEvent.Claude e -> Agent.UNKNOWN;
            case ExploreEvent.Heartbeat e -> Agent.UNKNOWN;
        };
    }

    /** The frame {@code event} names: a stop's, a page's shot, a witness's; {@code ""} for none. */
    public static String frameOf(ExploreEvent event) {
        return switch (event) {
            case ExploreEvent.Stopped e -> e.stop().framePath();
            case ExploreEvent.Pictured e -> e.frame();
            case ExploreEvent.Witnessed e -> e.frame();
            default -> "";
        };
    }

    /** The latest frame a record of the run's names; {@code ""} for none. */
    private String lastFrame() {
        return latest(e -> !frameOf(e).isBlank()).map(JournalView::frameOf).orElse("");
    }

    /**
     * The latest frame the run saved of {@code screen} (a stop on it or its page's shot), case aside; {@code ""} for
     * none. Never another screen's: a wrong map is redrawn on its own screen or not cut from at all.
     */
    public String lastFrameOf(String screen) {
        return latest(e -> !frameOf(e).isBlank() && switch (e) {
            case ExploreEvent.Stopped s -> screen.equalsIgnoreCase(s.stop().screen());
            case ExploreEvent.Pictured p -> screen.equalsIgnoreCase(p.screen());
            default -> false;
        }).map(JournalView::frameOf).orElse("");
    }

    /**
     * The way the latest run first came to the last screen of the map it was on, from where it began: each screen by
     * the one it was first found from, oldest first; empty before it reached one. Off the map now, {@link #where()}
     * is empty and this ends where it left the map.
     */
    public List<String> path() {
        Map<String, String> from = new LinkedHashMap<>();
        for (ExploreEvent event : events) {
            if (event instanceof ExploreEvent.NewScreen found) from.putIfAbsent(found.screen(), found.from());
        }
        List<String> path = new ArrayList<>();
        String screen = lastScreen().orElse(null);
        Set<String> seen = new HashSet<>();
        while (screen != null && seen.add(screen)) {
            path.add(screen);
            screen = from.get(screen);
        }
        return List.copyOf(path.reversed());
    }

    private AgentSummary coordinator(long nowMs, Duration silentAfter) {
        Optional<Instant> beat = lastHeartbeat();
        AgentSummary.State state = !running() ? AgentSummary.State.ENDED
                : beat.isEmpty() ? (crewed() ? AgentSummary.State.IDLE : AgentSummary.State.ABSENT)
                : nowMs - beat.get().toEpochMilli() > silentAfter.toMillis() ? AgentSummary.State.SILENT
                : AgentSummary.State.WORKING;
        return new AgentSummary(Agent.COORDINATOR, state, "", "",
                latest(e -> e instanceof ExploreEvent.Leased || e instanceof ExploreEvent.Released),
                (int) count(ExploreEvent.Leased.class), beat.isEmpty() ? Duration.ZERO : ranFor(nowMs));
    }

    /** A worker's card: the leases of {@code role}, the one open now, and what it last wrote. */
    private AgentSummary worker(Agent agent, StopRole role, long nowMs) {
        List<ExploreEvent.Leased> leases = events.stream().filter(e -> e instanceof ExploreEvent.Leased l
                && l.role() == role).map(ExploreEvent.Leased.class::cast).toList();
        Set<String> tries = new HashSet<>();
        leases.forEach(l -> tries.add(l.stopId() + "#" + l.attempt()));
        // A lease past its time is the Coordinator's to take back: its worker is gone, not working.
        Optional<ExploreEvent.Leased> open = leases.reversed().stream().filter(l -> !l.expired(nowMs))
                .filter(l -> leaseOf(l.stopId()).filter(l::equals).isPresent()).findFirst();
        long worked = 0;
        for (ExploreEvent.Leased lease : leases) {
            long until = events.stream().filter(e -> e instanceof ExploreEvent.Released r
                            && r.stopId().equals(lease.stopId()) && r.attempt() == lease.attempt())
                    .mapToLong(ExploreEvent::at).findFirst().orElse(Math.min(lease.expiresAt(), nowMs));
            worked += Math.max(0, until - lease.at());
        }
        AgentSummary.State state = open.isPresent() ? AgentSummary.State.WORKING
                : !crewed() && leases.isEmpty() ? AgentSummary.State.ABSENT
                : !running() ? AgentSummary.State.ENDED : AgentSummary.State.IDLE;
        String stopId = open.map(ExploreEvent.Leased::stopId).orElse("");
        String frame = open.flatMap(l -> stopOf(l.stopId())).map(Stop::framePath).orElse("");
        Optional<ExploreEvent> last = latest(e -> switch (e) {
            // A session that ended well said its piece in its own record; only a failed one's release says more.
            case ExploreEvent.Released r -> !r.ok() && tries.contains(r.stopId() + "#" + r.attempt());
            case ExploreEvent.Ranked r -> role == StopRole.CARTOGRAPHER;
            case ExploreEvent.EditQueued q -> role == StopRole.CARTOGRAPHER;
            case ExploreEvent.EditApplied a -> role == StopRole.CARTOGRAPHER;
            case ExploreEvent.Asked a -> role == StopRole.CARTOGRAPHER;
            case ExploreEvent.Finding f -> role == StopRole.EVALUATOR;
            case ExploreEvent.Summary s -> role == StopRole.EVALUATOR;
            case ExploreEvent.Branched b -> role == StopRole.MECHANIC;
            default -> false;
        });
        return new AgentSummary(agent, state, stopId, frame, last, leases.size(), Duration.ofMillis(worked));
    }

    /** The latest run's latest event {@code test} holds for; empty for none. */
    private Optional<ExploreEvent> latest(Predicate<ExploreEvent> test) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (test.test(events.get(i))) return Optional.of(events.get(i));
        }
        return Optional.empty();
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
