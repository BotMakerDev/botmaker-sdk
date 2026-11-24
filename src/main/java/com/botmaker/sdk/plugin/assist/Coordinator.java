// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopRole;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the explore crew's Coordinator does next (spec 2026-11-24-explore-agents-design § Coordinator): a pure function
 * of the run's journal and the clock, so a core restarted on the same journal decides the same. Its host
 * ({@link CoordinatorHost}) carries the actions out and writes them down; the journal is the only state.
 *
 * <p>A stop's role is <em>present</em> (its stops are leased to it, one try at a time, up to {@link Settings#attempts}),
 * <em>absent</em> (its open stop is parked at once, so the run goes on to what code can still do) or neither: left to
 * whoever takes it today, Claude's explore tool or the run's own park timeout.
 */
public final class Coordinator {

    /**
     * {@code lease}: how long a worker holds a stop; {@code attempts}: tries per stop before it is parked;
     * {@code heartbeat}: T, past which a silent Coordinator ends the run; {@code quiet}: how long a running journal may
     * say nothing before the run ends {@code STUCK}, whoever is at fault.
     */
    public record Settings(Duration lease, int attempts, Duration heartbeat, Duration quiet) {

        /**
         * The plan's start values. {@code quiet} is past the run's own {@code stuckAfter} (15 min): a run waiting on a
         * stop writes nothing for minutes, and one that idles ends itself first; this catches a run that hangs.
         */
        public static final Settings DEFAULT = new Settings(Duration.ofSeconds(120), 3, Duration.ofSeconds(60),
                Duration.ofMinutes(20));

        /** How often a heartbeat is written: a few per T, so one late round never reads as silence. */
        Duration beatEvery() {
            return heartbeat.dividedBy(4).compareTo(Duration.ofMillis(1)) < 0 ? Duration.ofMillis(1)
                    : heartbeat.dividedBy(4);
        }
    }

    /** Which roles take stops from the Coordinator, and which have nobody to. */
    public record Crew(Set<StopRole> present, Set<StopRole> absent) {
        public Crew {
            present = Set.copyOf(present);
            absent = Set.copyOf(absent);
        }

        /** Today's crew: the in-process Settler only; no worker is started yet, and nobody is parked for lack of one. */
        public static final Crew SETTLER_ONLY = new Crew(Set.of(StopRole.SETTLER), Set.of());
    }

    /** One thing the host does, and writes down. */
    public sealed interface Action {
        /** Hand {@code stopId} to {@code role}'s worker, its {@code attempt}th try, until {@code expiresAt}. */
        record Lease(String stopId, StopRole role, int attempt, long expiresAt) implements Action {}

        /** The {@code attempt}th try of {@code stopId} ran out: take it back, its worker killed; it is tried again. */
        record Requeue(String stopId, int attempt) implements Action {}

        /** Park the open stop {@code stopId}, {@code why} nobody takes it: the run goes on elsewhere. */
        record Park(String stopId, String why) implements Action {}

        /** End the run as {@code end}, {@code why} in plain words. */
        record End(ExploreLoop.End end, String why) implements Action {}

        /** Write a heartbeat. */
        record Beat() implements Action {}
    }

    private final Settings settings;
    private final Crew crew;

    public Coordinator(Settings settings, Crew crew) {
        this.settings = settings;
        this.crew = crew;
    }

    /**
     * What to do at {@code nowMs} on {@code view}, the latest run's journal. {@code commandWaiting}: the run has not
     * taken the last command yet, so none that needs one (a Settler's resume, a park, an end) is given; each comes
     * again next step. At most one command a step: the run takes one at a time.
     */
    public List<Action> step(JournalView view, long nowMs, boolean commandWaiting) {
        if (!view.running()) return List.of();
        List<Action> actions = new ArrayList<>();
        long lastBeat = view.lastHeartbeat().map(at -> at.toEpochMilli()).orElse(Long.MIN_VALUE);
        if (lastBeat == Long.MIN_VALUE || nowMs - lastBeat >= settings.beatEvery().toMillis()) {
            actions.add(new Action.Beat());
        }
        if (quiet(view, nowMs)) {
            actions.add(new Action.End(ExploreLoop.End.STUCK, "no progress for "
                    + JournalView.took(settings.quiet().toMillis())));
            return actions;
        }
        Optional<Stop> open = view.open();
        List<Stop> waiting = new ArrayList<>();
        open.ifPresent(waiting::add);
        waiting.addAll(view.queue());
        // The Settler's work ends in a resume, a command: one at a time, none while one of its leases is held.
        boolean commandGiven = commandWaiting || view.events().stream()
                .anyMatch(e -> e instanceof ExploreEvent.Leased leased && leased.role() == StopRole.SETTLER
                        && view.leaseOf(leased.stopId()).filter(leased::equals).isPresent());
        for (Stop stop : waiting) {
            String id = stop.id();
            StopRole role = view.roleOf(stop);
            boolean isOpen = open.map(o -> o.id().equals(id)).orElse(false);
            // A held lease is closed when it runs out, whatever became of its stop since.
            Optional<ExploreEvent.Leased> lease = view.leaseOf(id);
            if (lease.isPresent()) {
                if (lease.get().expired(nowMs)) actions.add(new Action.Requeue(id, lease.get().attempt()));
                continue;
            }
            if (role == StopRole.SETTLER && !settlerTakes(view, stop)) continue;
            if (done(view, id)) continue;
            if (crew.absent().contains(role)) {
                // A queued stop is parked already; only the open one holds the run.
                if (isOpen && !commandGiven) {
                    actions.add(new Action.Park(id, "no " + role.displayName() + " available"));
                    commandGiven = true;
                }
                continue;
            }
            if (!crew.present().contains(role)) continue;
            int tries = view.attempts(id);
            // The Settler tries once: a write that may have partly landed is never written again.
            int cap = role == StopRole.SETTLER ? 1 : settings.attempts();
            if (tries >= cap) {
                if (isOpen && !commandGiven) {
                    actions.add(new Action.Park(id, "tried " + tries + (tries == 1 ? " time" : " times")));
                    commandGiven = true;
                }
                continue;
            }
            if (role == StopRole.SETTLER) {
                if (commandGiven) continue;
                commandGiven = true;
            }
            actions.add(new Action.Lease(id, role, tries + 1, nowMs + settings.lease().toMillis()));
        }
        return actions;
    }

    /**
     * Whether the Coordinator has been silent too long at {@code nowMs}: a running journal whose last heartbeat is
     * older than T. The host's watchdog ends such a run {@code STUCK: coordinator silent}. A journal that never had one
     * is not silent: nobody coordinated it yet.
     */
    public static boolean silent(JournalView view, Settings settings, long nowMs) {
        return view.running() && view.lastHeartbeat()
                .map(at -> nowMs - at.toEpochMilli() > settings.heartbeat().toMillis()).orElse(false);
    }

    /** Whether the running journal said nothing but heartbeats for {@link Settings#quiet}, no lease being worked. */
    private boolean quiet(JournalView view, long nowMs) {
        List<ExploreEvent> events = view.events();
        for (int i = events.size() - 1; i >= 0; i--) {
            ExploreEvent event = events.get(i);
            if (event instanceof ExploreEvent.Heartbeat) continue;
            if (event instanceof ExploreEvent.Leased leased && !leased.expired(nowMs)
                    && view.leaseOf(leased.stopId()).isPresent()) {
                return false;
            }
            return nowMs - event.at() >= settings.quiet().toMillis();
        }
        return false;
    }

    /** Whether the Settler has work at {@code stop}: answers the run settled, and no reload for it yet. */
    private static boolean settlerTakes(JournalView view, Stop stop) {
        return view.settled(stop) && view.reloadOf(stop.id()).isEmpty();
    }

    /** Whether a try of {@code stopId} finished: nothing more is asked of its role. */
    private static boolean done(JournalView view, String stopId) {
        return view.events().stream().anyMatch(e -> e instanceof ExploreEvent.Released released
                && released.stopId().equals(stopId) && released.ok());
    }
}
