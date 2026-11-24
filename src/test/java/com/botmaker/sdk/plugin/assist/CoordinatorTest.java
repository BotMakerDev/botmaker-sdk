// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.FindingKind;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.explore.StopRole;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.plugin.run.SdkRuns;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The explore crew's Coordinator (spec 2026-11-24-explore-agents-design): a fake clock, journals written by hand. */
class CoordinatorTest {

    /** Lease 100 ms, 3 tries, heartbeat T 40 ms (a beat every 10), quiet 1 s. */
    private static final Coordinator.Settings SETTINGS = new Coordinator.Settings(Duration.ofMillis(100), 3,
            Duration.ofMillis(40), Duration.ofSeconds(1));
    private static final Coordinator.Crew CARTOGRAPHERS = new Coordinator.Crew(Set.of(StopRole.CARTOGRAPHER), Set.of());

    @Test
    void aStopOfAPresentRoleIsLeased(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));

        List<Coordinator.Action> actions = coordinator(CARTOGRAPHERS).step(JournalView.of(journal), 50, false);

        assertEquals(List.of(new Coordinator.Action.Beat(),
                new Coordinator.Action.Lease("unknown-1", StopRole.CARTOGRAPHER, 1, 150)), actions);
    }

    @Test
    void anExpiredLeaseIsTakenBackAndTriedAgain(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        journal.append(new ExploreEvent.Leased("unknown-1", StopRole.CARTOGRAPHER, 1, 150, 50));
        Coordinator coordinator = coordinator(CARTOGRAPHERS);

        assertEquals(List.of(), leasesAndMore(coordinator.step(JournalView.of(journal), 149, false)), "still held");
        List<Coordinator.Action> late = coordinator.step(JournalView.of(journal), 150, false);
        assertEquals(List.of(new Coordinator.Action.Requeue("unknown-1", 1)), leasesAndMore(late));
        CoordinatorHost.carryOut(journal, JournalView.of(journal), late, () -> 150, refusingWriter(), Runnable::run);

        assertEquals(new ExploreEvent.Released("unknown-1", 1, false, "timeout", 150),
                journal.events().stream().filter(ExploreEvent.Released.class::isInstance).findFirst().orElseThrow());
        assertEquals(List.of(new Coordinator.Action.Lease("unknown-1", StopRole.CARTOGRAPHER, 2, 251)),
                leasesAndMore(coordinator.step(JournalView.of(journal), 151, false)));
    }

    @Test
    void aStopTriedTooOftenIsParked(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        for (int attempt = 1; attempt <= 3; attempt++) {
            journal.append(new ExploreEvent.Leased("unknown-1", StopRole.CARTOGRAPHER, attempt, 10L * attempt, 5));
            journal.append(new ExploreEvent.Released("unknown-1", attempt, false, "timeout", 10L * attempt));
        }

        assertEquals(List.of(new Coordinator.Action.Park("unknown-1", "tried 3 times")),
                leasesAndMore(coordinator(CARTOGRAPHERS).step(JournalView.of(journal), 50, false)));
    }

    /** Audit S2: a role with nobody to take its stops parks them at once, and the run goes on to what code can do. */
    @Test
    void anAbsentRoleParksItsOpenStopAtOnce(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        Coordinator coordinator = coordinator(new Coordinator.Crew(Set.of(), Set.of(StopRole.CARTOGRAPHER)));

        assertEquals(List.of(new Coordinator.Action.Park("unknown-1", "no Cartographer available")),
                leasesAndMore(coordinator.step(JournalView.of(journal), 50, false)));
        assertEquals(List.of(), leasesAndMore(coordinator.step(JournalView.of(journal), 50, true)),
                "one command at a time");
    }

    @Test
    void aStopOfARoleNobodyCoordinatesIsLeftAlone(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));

        assertEquals(List.of(), leasesAndMore(coordinator(Coordinator.Crew.SETTLER_ONLY)
                .step(JournalView.of(journal), 50, false)));
    }

    @Test
    void aJournalThatSaysNothingForTooLongEndsStuck(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        journal.append(new ExploreEvent.Heartbeat(900));
        Coordinator coordinator = coordinator(Coordinator.Crew.SETTLER_ONLY);

        assertEquals(List.of(), leasesAndMore(coordinator.step(JournalView.of(journal), 1001, false)));
        assertEquals(List.of(new Coordinator.Action.End(ExploreLoop.End.STUCK, "no progress for "
                        + JournalView.took(1000))),
                leasesAndMore(coordinator.step(JournalView.of(journal), 1002, false)), "heartbeats are no progress");
    }

    @Test
    void aLeaseBeingWorkedIsProgress(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        journal.append(new ExploreEvent.Leased("unknown-1", StopRole.CARTOGRAPHER, 1, 5_000, 2));

        assertEquals(List.of(), leasesAndMore(coordinator(CARTOGRAPHERS).step(JournalView.of(journal), 1500, false)));
    }

    @Test
    void aHeartbeatIsWrittenAFewTimesPerT(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        Coordinator coordinator = coordinator(Coordinator.Crew.SETTLER_ONLY);

        assertEquals(List.of(new Coordinator.Action.Beat()), coordinator.step(JournalView.of(journal), 3, false));
        journal.append(new ExploreEvent.Heartbeat(3));
        assertEquals(List.of(), coordinator.step(JournalView.of(journal), 12, false));
        assertEquals(List.of(new Coordinator.Action.Beat()), coordinator.step(JournalView.of(journal), 13, false));
    }

    @Test
    void aRunThatEndedIsNotCoordinated(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        journal.append(new ExploreEvent.Ended(ExploreLoop.End.DONE, 5));

        assertEquals(List.of(), coordinator(CARTOGRAPHERS).step(JournalView.of(journal), 5_000, false));
    }

    /** The watchdog: a running journal whose heartbeat is older than T ends {@code STUCK: coordinator silent}. */
    @Test
    void aSilentCoordinatorEndsTheRunStuck(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        assertFalse(CoordinatorHost.watch(journal, SETTINGS, 500), "never coordinated: not silent");
        journal.append(new ExploreEvent.Heartbeat(10));

        assertFalse(CoordinatorHost.watch(journal, SETTINGS, 50));
        assertTrue(CoordinatorHost.watch(journal, SETTINGS, 51));

        List<ExploreEvent> events = journal.events();
        assertEquals(new ExploreEvent.Finding(FindingKind.STUCK, "", "coordinator silent", 51),
                events.get(events.size() - 2));
        assertEquals(new ExploreEvent.Ended(ExploreLoop.End.STUCK, 51), events.getLast());
        assertTrue(journal.commandWaiting(), "asked to stop, in case it still listens");
        assertFalse(CoordinatorHost.watch(journal, SETTINGS, 99), "ended once");
    }

    /** The Settler's lease: written, resumed and released in one round, never written again (it is in the journal). */
    @Test
    void aSettledStopIsLeasedToTheSettlerWrittenOnceAndResumed(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Settled("settled-1", List.of(new TreeAnswer.InputNote("A", "x", "y")), 2));
        journal.append(new ExploreEvent.Stopped(stop("settled-1", StopKind.SETTLED), false, 2));
        Coordinator coordinator = coordinator(Coordinator.Crew.SETTLER_ONLY);
        List<String> written = new ArrayList<>();
        TreeAnswerWatch.Writer writer = (j, id, answers) -> {
            written.add(id);
            return new TreeAnswers.Written(Optional.empty(), List.of());
        };

        CoordinatorHost.round(journal, JournalView.of(journal), coordinator, () -> 20, writer, Runnable::run);
        assertEquals(Optional.of(new Journal.Command.Resume("settled-1", false)), journal.takeCommand());
        CoordinatorHost.round(journal, JournalView.of(journal), coordinator, () -> 30, writer, Runnable::run);

        assertEquals(List.of("settled-1"), written);
        assertTrue(journal.events().contains(new ExploreEvent.Leased("settled-1", StopRole.SETTLER, 1, 120, 20)));
        assertTrue(journal.events().contains(new ExploreEvent.Released("settled-1", 1, true, "", 20)));
        assertFalse(journal.commandWaiting());
    }

    /** A write that throws, or outlives its lease, may have partly landed: the stop is parked, never written twice. */
    @Test
    void aSettlerThatFailsIsNotTriedAgain(@TempDir Path folder) {
        Journal journal = settledAndOpen(folder);
        Coordinator coordinator = coordinator(Coordinator.Crew.SETTLER_ONLY);
        TreeAnswerWatch.Writer throwing = (j, id, answers) -> {
            throw new IllegalStateException("the core went away");
        };

        CoordinatorHost.round(journal, JournalView.of(journal), coordinator, () -> 20, throwing, Runnable::run);

        assertTrue(journal.events().contains(new ExploreEvent.Released("settled-1", 1, false, "the core went away", 20)));
        assertEquals(List.of(new Coordinator.Action.Park("settled-1", "tried 1 time")),
                leasesAndMore(coordinator.step(JournalView.of(journal), 30, false)));
    }

    @Test
    void anExpiredSettlerLeaseIsTakenBackThenParked(@TempDir Path folder) {
        Journal journal = settledAndOpen(folder);
        journal.append(new ExploreEvent.Leased("settled-1", StopRole.SETTLER, 1, 120, 20));
        Coordinator coordinator = coordinator(Coordinator.Crew.SETTLER_ONLY);

        List<Coordinator.Action> late = coordinator.step(JournalView.of(journal), 120, false);
        assertEquals(List.of(new Coordinator.Action.Requeue("settled-1", 1)), leasesAndMore(late));
        CoordinatorHost.carryOut(journal, JournalView.of(journal), late, () -> 120, refusingWriter(), Runnable::run);

        assertEquals(List.of(new Coordinator.Action.Park("settled-1", "tried 1 time")),
                leasesAndMore(coordinator.step(JournalView.of(journal), 121, false)));
    }

    /** While a Settler holds a lease its resume is not written yet: no second one is leased beside it. */
    @Test
    void oneSettlerLeaseAtATime(@TempDir Path folder) {
        Journal journal = settledAndOpen(folder);
        journal.append(new ExploreEvent.Leased("settled-1", StopRole.SETTLER, 1, 5_000, 20));
        journal.append(new ExploreEvent.Settled("settled-2", List.of(new TreeAnswer.InputNote("B", "x", "y")), 21));
        journal.append(new ExploreEvent.Parked("settled-1", 22));
        journal.append(new ExploreEvent.Stopped(stop("settled-2", StopKind.SETTLED), false, 23));

        assertEquals(List.of(), leasesAndMore(coordinator(Coordinator.Crew.SETTLER_ONLY)
                .step(JournalView.of(journal), 30, false)));
    }

    @Test
    void aRestartedRunHasNoHeartbeatYet(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        journal.append(new ExploreEvent.Heartbeat(10));
        journal.append(new ExploreEvent.Restarted("fault-1", true, 20));

        assertEquals(Optional.empty(), JournalView.of(journal).lastHeartbeat());
        assertFalse(CoordinatorHost.watch(journal, SETTINGS, 5_000));
    }

    @Test
    void theSettlerWaitsForTheRunToTakeTheLastCommand(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Settled("settled-1", List.of(new TreeAnswer.InputNote("A", "x", "y")), 2));
        journal.append(new ExploreEvent.Stopped(stop("settled-1", StopKind.SETTLED), false, 2));

        assertEquals(List.of(), leasesAndMore(coordinator(Coordinator.Crew.SETTLER_ONLY)
                .step(JournalView.of(journal), 5, true)));
    }

    @Test
    void itServesKeepExploringAndDiscover() {
        assertEquals(List.of(SdkRuns.KEEP_EXPLORING, SdkRuns.DISCOVER),
                new CoordinatorHost().serves().stream().map(RunEntry::title).toList());
    }

    /** A worker's lease starts it on the stop until the lease runs out, and its end releases the stop. */
    @Test
    void aWorkersLeaseRunsItAndItsEndReleasesTheStop(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        List<Object> ran = new ArrayList<>();
        CoordinatorHost.Worker worker = (on, role, stop, limit) -> {
            ran.addAll(List.of(role, stop.id(), limit));
            return new WorkerRunner.Result(true, "");
        };

        CoordinatorHost.round(journal, JournalView.of(journal), coordinator(CARTOGRAPHERS), () -> 50, refusingWriter(),
                Runnable::run, worker);

        assertEquals(List.of(StopRole.CARTOGRAPHER, "unknown-1", Duration.ofMillis(100)), ran);
        List<ExploreEvent.Released> released = JournalView.of(journal).events().stream()
                .filter(ExploreEvent.Released.class::isInstance).map(ExploreEvent.Released.class::cast).toList();
        assertEquals(1, released.size());
        assertTrue(released.getFirst().ok());
    }

    /** A worker killed at its lease's end writes nothing: the Coordinator's requeue releases it. */
    @Test
    void aWorkerThatRanOutIsReleasedOnlyByTheRequeue(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));

        CoordinatorHost.round(journal, JournalView.of(journal), coordinator(CARTOGRAPHERS), () -> 50, refusingWriter(),
                Runnable::run, (on, role, stop, limit) -> new WorkerRunner.Result(false, "timeout"));

        assertTrue(JournalView.of(journal).events().stream().noneMatch(ExploreEvent.Released.class::isInstance));
    }

    /** A worker that ends past its lease writes nothing: the requeue released that try already. */
    @Test
    void aWorkerEndingPastItsLeaseWritesNoSecondRelease(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        long[] now = {50};

        CoordinatorHost.round(journal, JournalView.of(journal), coordinator(CARTOGRAPHERS), () -> now[0],
                refusingWriter(), Runnable::run, (on, role, stop, limit) -> {
                    now[0] = 151;
                    return new WorkerRunner.Result(true, "");
                });

        assertTrue(JournalView.of(journal).events().stream().noneMatch(ExploreEvent.Released.class::isInstance));
    }

    /** A lease on a stop the view no longer holds is given back at once, not held with nobody on it. */
    @Test
    void aLeaseOnAStopGoneIsReleasedAtOnce(@TempDir Path folder) {
        Journal journal = running(folder, stop("unknown-1", StopKind.UNKNOWN_SCREEN));
        List<Coordinator.Action> lease = List.of(new Coordinator.Action.Lease("gone-1", StopRole.CARTOGRAPHER, 1, 150));

        CoordinatorHost.carryOut(journal, JournalView.of(journal), lease, () -> 50, refusingWriter(), Runnable::run,
                (on, role, stop, limit) -> {
                    throw new AssertionError("no worker for a stop that is gone");
                });

        assertTrue(JournalView.of(journal).events().stream().anyMatch(e -> e instanceof ExploreEvent.Released r
                && r.stopId().equals("gone-1") && !r.ok() && r.why().equals("stop not found")));
    }

    /** Signed in, the workers' roles are present; signed out, their stops are parked; no client, the Settler alone. */
    @Test
    void theCrewFollowsTheUsersSignIn() {
        assertEquals(Coordinator.Crew.SETTLER_ONLY, CoordinatorHost.crewOf(null));
        assertEquals(Set.of(StopRole.SETTLER, StopRole.CARTOGRAPHER, StopRole.MECHANIC, StopRole.EVALUATOR),
                CoordinatorHost.crewOf(client(true)).present());
        Coordinator.Crew out = CoordinatorHost.crewOf(client(false));
        assertEquals(Set.of(StopRole.SETTLER), out.present());
        assertEquals(Set.of(StopRole.CARTOGRAPHER, StopRole.MECHANIC, StopRole.EVALUATOR), out.absent());
    }

    private static AgentClient client(boolean signedIn) {
        return new AgentClient() {
            @Override
            public String id() {
                return "fake";
            }

            @Override
            public boolean available() {
                return signedIn;
            }

            @Override
            public List<String> command(String prompt, WorkerRole role, Path mcpConfig) {
                return List.of("true");
            }
        };
    }

    private static Coordinator coordinator(Coordinator.Crew crew) {
        return new Coordinator(SETTINGS, crew);
    }

    /** {@code actions} but its heartbeat, which most tests do not look at. */
    private static List<Coordinator.Action> leasesAndMore(List<Coordinator.Action> actions) {
        return actions.stream().filter(a -> !(a instanceof Coordinator.Action.Beat)).toList();
    }

    private static TreeAnswerWatch.Writer refusingWriter() {
        return (journal, id, answers) -> {
            throw new AssertionError("nothing is settled here");
        };
    }

    private static Journal settledAndOpen(Path folder) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Settled("settled-1", List.of(new TreeAnswer.InputNote("A", "x", "y")), 2));
        journal.append(new ExploreEvent.Stopped(stop("settled-1", StopKind.SETTLED), false, 2));
        return journal;
    }

    private static Journal running(Path folder, Stop open) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Stopped(open, false, 2));
        return journal;
    }

    private static Stop stop(String id, StopKind kind) {
        return new Stop(id, kind, "A", "A.x", null, List.of(), List.of(), Map.of(), List.of(), "notes", 2);
    }
}
