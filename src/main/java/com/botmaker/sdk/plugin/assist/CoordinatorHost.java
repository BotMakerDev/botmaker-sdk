// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunCompanion;
import com.botmaker.plugin.api.run.RunContext;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.FindingKind;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopRole;
import com.botmaker.sdk.plugin.SdkLog;
import com.botmaker.sdk.plugin.run.SdkRuns;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The SDK's companion of <em>Keep exploring</em> and <em>Discover</em> runs (2026-11-20): the core starts one with
 * each run and stops it at the run's end, whether or not Studio or a session is open, and Studio's copy of the plugin
 * never runs one, so two never race on one journal. It hosts the explore crew's {@link Coordinator} (spec
 * 2026-11-24-explore-agents-design): every {@link #POLL_MS} it asks it what to do, does it and writes it down; a
 * watchdog ends the run {@code STUCK} when the Coordinator's heartbeat goes silent.
 */
public final class CoordinatorHost implements RunCompanion {

    /** How often the journal is read. */
    static final long POLL_MS = 500;

    /** One worker's try at a stop, by {@code limit}: a {@link WorkerRunner} on the user's own client. */
    @FunctionalInterface
    interface Worker {
        WorkerRunner.Result run(Journal journal, StopRole role, Stop stop, Duration limit);
    }

    private final Coordinator.Settings settings;
    /** The client the crew's workers run on; null while no worker is started (the Settler alone). */
    private final AgentClient client;
    /** The threads of the workers running now, each waiting on its process. */
    private final Set<Thread> workers = ConcurrentHashMap.newKeySet();
    private Thread thread;
    private ScheduledExecutorService watchdog;
    /** The project the run is of, once started: where its journal is when the run ends. */
    private Path project;

    /**
     * A host not started yet, at the plan's start values: the core builds one for each run. No worker is started yet:
     * none has its job until the Reader's (plan 2026-11-24-explore-agents, phase 5).
     */
    public CoordinatorHost() {
        this(Coordinator.Settings.DEFAULT, null);
    }

    CoordinatorHost(Coordinator.Settings settings, AgentClient client) {
        this.settings = settings;
        this.client = client;
    }

    /**
     * Who takes stops when the workers run on {@code client}: the Settler always; each worker's role while the user is
     * signed in to it, else none of them, their stops parked so the run goes on. Null: the Settler alone, nobody
     * parked.
     */
    static Coordinator.Crew crewOf(AgentClient client) {
        if (client == null) return Coordinator.Crew.SETTLER_ONLY;
        Set<StopRole> workers = Set.of(StopRole.CARTOGRAPHER, StopRole.MECHANIC);
        if (client.available()) {
            Set<StopRole> present = new HashSet<>(workers);
            present.add(StopRole.SETTLER);
            return new Coordinator.Crew(present, Set.of());
        }
        return new Coordinator.Crew(Set.of(StopRole.SETTLER), workers);
    }

    @Override
    public List<RunEntry> serves() {
        return new SdkRuns().runs().stream().filter(entry -> entry.title().equals(SdkRuns.KEEP_EXPLORING)
                || entry.title().equals(SdkRuns.DISCOVER)).toList();
    }

    /** Coordinates the latest run of the project the run is of, until {@link #stop()}. */
    @Override
    public synchronized void start(RunContext run) {
        StudioServices services = run.services();
        Path project = services == null ? null : services.projectDir();
        if (project == null || thread != null) return;
        this.project = project;
        Coordinator coordinator = new Coordinator(settings, crewOf(client));
        Worker worker = client == null ? null : (journal, role, stop, limit) -> {
            // Kept so that stop() ends it: a worker never outlives the run it works for.
            Thread own = Thread.currentThread();
            workers.add(own);
            try {
                return new WorkerRunner(journal.folder()).run(client, role, stop, limit);
            } finally {
                workers.remove(own);
            }
        };
        TreeAnswerWatch.Writer writer = TreeAnswerWatch.into(services);
        // The latest run's journal, read again each round: the run begins its own once its process is up.
        Supplier<Optional<Journal>> latest = () -> Journal.latest(project);
        watchdog = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon()
                .name("explore-watchdog").factory());
        long every = settings.beatEvery().toMillis();
        watchdog.scheduleWithFixedDelay(() -> {
            // A tick that throws would cancel the schedule for good: said, and tried again next tick.
            try {
                latest.get().ifPresent(journal -> watch(journal, settings, System.currentTimeMillis()));
            } catch (RuntimeException failed) {
                SdkLog.warn("The explore run's watchdog failed a look: " + failed.getMessage());
            }
        }, every, every, TimeUnit.MILLISECONDS);
        thread = Thread.ofPlatform().daemon().name("explore-coordinator").start(() -> coordinate(latest, coordinator,
                writer, worker, pictured -> TreeAnswers.pictured(services, pictured)));
    }

    /**
     * Stops coordinating, and says the run ended when it could not: a run whose process was killed (the JVM, Studio
     * quit, a core restart) writes no end of its own, so its journal would say it still runs.
     */
    @Override
    public synchronized void stop() {
        if (watchdog != null) watchdog.shutdownNow();
        if (thread != null) thread.interrupt();
        // A stopped worker kills its process and writes nothing.
        List<Thread> running = List.copyOf(workers);
        running.forEach(Thread::interrupt);
        // Their last writes land before the end is, never after it.
        try {
            if (watchdog != null) watchdog.awaitTermination(2, TimeUnit.SECONDS);
            if (thread != null) thread.join(Duration.ofSeconds(2));
            for (Thread worker : running) worker.join(Duration.ofSeconds(2));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        thread = null;
        watchdog = null;
        if (project != null) {
            Journal.latest(project).ifPresent(journal -> TreeAnswerWatch.endIfKilled(journal,
                    System.currentTimeMillis()));
        }
    }

    private void coordinate(Supplier<Optional<Journal>> latest, Coordinator coordinator, TreeAnswerWatch.Writer writer,
                            Worker worker, Consumer<ExploreEvent.Pictured> picture) {
        Set<ExploreEvent.Pictured> pictured = new HashSet<>();
        String failedLast = null;
        JournalView.Reader reader = null;
        // The Settler writes into the bot off this thread, and a worker runs off it, which goes on beating meanwhile.
        Executor settlers = command -> Thread.ofVirtual().name("explore-worker").start(command);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Optional<Journal> journal = latest.get();
                    if (journal.isPresent()) {
                        // Read on from where the last round left off, unless a newer run began its own journal.
                        if (reader == null || !reader.journal().folder().equals(journal.get().folder())) {
                            reader = new JournalView.Reader(journal.get());
                        }
                        Journal now = reader.journal();
                        round(now, reader.view(), coordinator, System::currentTimeMillis, writer, settlers, worker);
                        TreeAnswerWatch.pictures(reader.view(), pictured, picture);
                    }
                    failedLast = null;
                } catch (RuntimeException failed) {
                    // Said once, then tried again each round: one bad read or slow write stops nothing for good.
                    if (!String.valueOf(failed.getMessage()).equals(failedLast)) {
                        SdkLog.warn("The explore run's Coordinator failed a round: " + failed.getMessage());
                    }
                    failedLast = String.valueOf(failed.getMessage());
                }
                Thread.sleep(POLL_MS);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    /** One round: what {@code coordinator} decides on {@code view} at the clock's time, carried out on {@code journal}. */
    static void round(Journal journal, JournalView view, Coordinator coordinator, LongSupplier clock,
                      TreeAnswerWatch.Writer writer, Executor settlers) {
        round(journal, view, coordinator, clock, writer, settlers, null);
    }

    static void round(Journal journal, JournalView view, Coordinator coordinator, LongSupplier clock,
                      TreeAnswerWatch.Writer writer, Executor settlers, Worker worker) {
        long now = clock.getAsLong();
        carryOut(journal, view, coordinator.step(view, now, journal.commandWaiting()), clock, writer, settlers, worker);
    }

    static void carryOut(Journal journal, JournalView view, List<Coordinator.Action> actions, LongSupplier clock,
                         TreeAnswerWatch.Writer writer, Executor settlers) {
        carryOut(journal, view, actions, clock, writer, settlers, null);
    }

    /**
     * Does each of {@code actions} on {@code journal}, at the clock's time, and writes it down; the Settler's work and
     * each {@code worker}'s try (null: none is started) run on {@code settlers}, and say they finished when they did.
     */
    static void carryOut(Journal journal, JournalView view, List<Coordinator.Action> actions, LongSupplier clock,
                         TreeAnswerWatch.Writer writer, Executor settlers, Worker worker) {
        long nowMs = clock.getAsLong();
        for (Coordinator.Action action : actions) {
            switch (action) {
                case Coordinator.Action.Beat beat -> journal.append(new ExploreEvent.Heartbeat(nowMs));
                case Coordinator.Action.Lease lease -> {
                    journal.append(new ExploreEvent.Leased(lease.stopId(), lease.role(), lease.attempt(),
                            lease.expiresAt(), nowMs));
                    // The Settler works in this process; a worker of another role is a process of its own.
                    if (lease.role() == StopRole.SETTLER) {
                        settlers.execute(() -> settle(journal, view, lease, clock, writer));
                    } else if (worker != null) {
                        Optional<Stop> stop = stopOf(view, lease.stopId());
                        if (stop.isPresent()) {
                            settlers.execute(() -> work(journal, lease, stop.get(), worker,
                                    Duration.ofMillis(Math.max(0, lease.expiresAt() - nowMs)), clock));
                        } else {
                            // A stop gone since the step: the lease is given back now, not held with nobody on it.
                            journal.append(new ExploreEvent.Released(lease.stopId(), lease.attempt(), false,
                                    "stop not found", nowMs));
                        }
                    }
                }
                case Coordinator.Action.Requeue requeue -> journal.append(new ExploreEvent.Released(requeue.stopId(),
                        requeue.attempt(), false, "timeout", nowMs));
                case Coordinator.Action.Park park -> {
                    SdkLog.info("Explore: " + park.stopId() + " parked, " + park.why());
                    journal.command("park " + park.stopId());
                }
                case Coordinator.Action.End end -> end(journal, end.end(), end.why(), nowMs);
            }
        }
    }

    /** The stop {@code stopId} of {@code view}: the open one, or one queued. */
    private static Optional<Stop> stopOf(JournalView view, String stopId) {
        return view.open().filter(stop -> stop.id().equals(stopId))
                .or(() -> view.queue().stream().filter(stop -> stop.id().equals(stopId)).findFirst());
    }

    /**
     * {@code worker}'s try at {@code lease}, killed when the lease runs out at {@code limit}; released with how it
     * ended. What it found is what it wrote through the explore tools meanwhile.
     */
    private static void work(Journal journal, Coordinator.Action.Lease lease, Stop stop, Worker worker, Duration limit,
                             LongSupplier clock) {
        WorkerRunner.Result result;
        try {
            result = worker.run(journal, lease.role(), stop, limit);
        } catch (RuntimeException failed) {
            result = new WorkerRunner.Result(false, String.valueOf(failed.getMessage()));
        }
        // A try that ran out is released by the Coordinator's requeue already, even one that ended just past its lease
        // (the process started a little after the lease did); a stopped one belongs to a run that is ending.
        long now = clock.getAsLong();
        if (now >= lease.expiresAt() || result.why().equals("timeout") || result.why().equals("stopped")) return;
        journal.append(new ExploreEvent.Released(lease.stopId(), lease.attempt(), result.ok(), result.why(), now));
    }

    /**
     * The Settler's try at {@code lease}: writes what the run settled and resumes the stop, then releases the lease. A
     * write that throws releases it not done; the Settler tries once, so nothing is written twice.
     */
    private static void settle(Journal journal, JournalView view, Coordinator.Action.Lease lease, LongSupplier clock,
                               TreeAnswerWatch.Writer writer) {
        try {
            Optional<String> failed = TreeAnswerWatch.settle(journal, view, lease.stopId(),
                    (id, answers) -> writer.write(journal, id, answers));
            journal.append(new ExploreEvent.Released(lease.stopId(), lease.attempt(), true,
                    failed.map(why -> "nothing was written: " + why).orElse(""), clock.getAsLong()));
        } catch (RuntimeException failed) {
            SdkLog.warn("What the explore run settled at " + lease.stopId() + " was not written: " + failed.getMessage());
            journal.append(new ExploreEvent.Released(lease.stopId(), lease.attempt(), false,
                    String.valueOf(failed.getMessage()), clock.getAsLong()));
        }
    }

    /**
     * The watchdog's look at {@code journal} at {@code nowMs}: a running journal whose Coordinator went silent is ended
     * {@code STUCK: coordinator silent}.
     *
     * @return whether it ended the run
     */
    static boolean watch(Journal journal, Coordinator.Settings settings, long nowMs) {
        if (!Coordinator.silent(JournalView.of(journal), settings, nowMs)) return false;
        end(journal, ExploreLoop.End.STUCK, "coordinator silent", nowMs);
        return true;
    }

    /**
     * Ends the run as {@code end}: says why as a finding, writes the end (a run whose process hangs never would), and
     * asks the run to stop, in case it still reads its commands.
     */
    private static void end(Journal journal, ExploreLoop.End end, String why, long nowMs) {
        // The watchdog and the Coordinator may both end a run: the first does, the other finds it ended.
        synchronized (ENDING) {
            if (!JournalView.of(journal).running()) return;
            journal.append(new ExploreEvent.Finding(FindingKind.STUCK, "", why, nowMs));
            journal.append(new ExploreEvent.Ended(end, nowMs));
            journal.command("stop");
        }
    }

    private static final Object ENDING = new Object();
}
