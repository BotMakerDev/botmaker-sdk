// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunCompanion;
import com.botmaker.plugin.api.run.RunContext;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.SdkLog;
import com.botmaker.sdk.plugin.run.SdkRuns;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Writes what <em>Keep exploring</em> or <em>Discover</em> settled off a screen's tree ({@link ExploreEvent.Settled})
 * into the bot, then resumes its stop, so the run goes on without Claude (web discovery phase 6b). It is the SDK's
 * companion of those runs (2026-11-20): the core starts one with each run and stops it at the run's end, whether or
 * not Studio or a session is open, and Studio's copy of the plugin never runs one, so two never race on one journal.
 */
public final class TreeAnswerWatch implements RunCompanion {

    /** How often the journal is read. */
    static final long POLL_MS = 500;

    private Thread thread;
    /** The project the run is of, once started: where its journal is when the run ends. */
    private Path project;

    /** A watch not started yet: the core builds one for each run. */
    public TreeAnswerWatch() {}

    @Override
    public List<RunEntry> serves() {
        return new SdkRuns().runs().stream().filter(entry -> entry.title().equals(SdkRuns.KEEP_EXPLORING)
                || entry.title().equals(SdkRuns.DISCOVER)).toList();
    }

    /** Watches the journal of the project the run is of, until {@link #stop()}. */
    @Override
    public synchronized void start(RunContext run) {
        StudioServices services = run.services();
        Path project = services == null ? null : services.projectDir();
        if (project == null || thread != null) return;
        this.project = project;
        // The latest run's journal, read again each round: the run begins its own once its process is up.
        thread = Thread.ofPlatform().daemon().name("explore-answers").start(() -> watch(() -> Journal.latest(project),
                (journal, id, answers) -> {
                    TreeAnswers.Written written = TreeAnswers.apply(services, "Explore: " + id, answers);
                    if (written.failed().isEmpty()) shots(services, journal, id, answers);
                    return written;
                }, pictured -> TreeAnswers.pictured(services, pictured)));
    }

    /** Writes what the run settled at a stop of {@code journal}'s run. */
    private interface Writer {
        TreeAnswers.Written write(Journal journal, String id, List<TreeAnswer> answers);
    }

    /**
     * Stops watching, and says the run ended when it could not: a run whose process was killed (the JVM, Studio quit, a
     * core restart) writes no end of its own, so its journal would say it still runs.
     */
    @Override
    public synchronized void stop() {
        if (thread != null) thread.interrupt();
        thread = null;
        if (project != null) Journal.latest(project).ifPresent(journal -> endIfKilled(journal, System.currentTimeMillis()));
    }

    /**
     * Writes {@link ExploreLoop.End#BROKE} at {@code nowMs} to a run's journal that began and never ended: the
     * host calls {@link #stop} once the run's process is gone, so a run that ended itself has said so already.
     *
     * @return whether an end was written
     */
    static boolean endIfKilled(Journal journal, long nowMs) {
        JournalView view = JournalView.of(journal);
        if (!view.began() || view.ended().isPresent()) return false;
        journal.append(new ExploreEvent.Ended(ExploreLoop.End.BROKE, nowMs));
        return true;
    }

    /**
     * Gives each screen {@code answers} added the frame its stop {@code id} saved as its shot, so 🗺 Screens shows the
     * page it was made from. A frame that cannot be read leaves the screen without one, as before.
     */
    private static void shots(StudioServices services, Journal journal, String id, List<TreeAnswer> answers) {
        if (answers.stream().noneMatch(TreeAnswer.NewScreen.class::isInstance)) return;
        JournalView view = JournalView.of(journal);
        Optional<Stop> stop = view.open().filter(s -> s.id().equals(id))
                .or(() -> view.queue().stream().filter(s -> s.id().equals(id)).findFirst());
        if (stop.isEmpty() || stop.get().framePath().isEmpty()) return;
        BufferedImage frame;
        try {
            frame = ImageIO.read(Path.of(stop.get().framePath()).toFile());
        } catch (IOException | RuntimeException unread) {
            return;
        }
        if (frame == null) return;
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(services));
        for (TreeAnswer answer : answers) {
            if (answer instanceof TreeAnswer.NewScreen screen) {
                ScreenBook.find(graph, screen.name())
                        .ifPresent(constant -> ScreenShots.saveIfMissing(services, graph, constant, frame));
            }
        }
    }

    private static void watch(Supplier<Optional<Journal>> latest, Writer write,
                              Consumer<ExploreEvent.Pictured> picture) {
        Set<String> done = new HashSet<>();
        Set<ExploreEvent.Pictured> pictured = new HashSet<>();
        String failedLast = null;
        JournalView.Reader reader = null;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Optional<Journal> journal = latest.get();
                    if (journal.isPresent()) {
                        // Read on from where the last round left off, unless a newer run began its own journal.
                        if (reader == null || !reader.journal().folder().equals(journal.get().folder())) {
                            reader = new JournalView.Reader(journal.get());
                        }
                        JournalView view = reader.view();
                        Journal now = reader.journal();
                        round(now, view, done, (id, answers) -> write.write(now, id, answers));
                        pictures(view, pictured, picture);
                    }
                    failedLast = null;
                } catch (RuntimeException failed) {
                    // Said once, then tried again each round: one bad read or slow write stops nothing for good.
                    if (!String.valueOf(failed.getMessage()).equals(failedLast)) {
                        SdkLog.warn("What the explore run settled could not be written: " + failed.getMessage());
                    }
                    failedLast = String.valueOf(failed.getMessage());
                }
                Thread.sleep(POLL_MS);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    /** The latest run's pictures not yet in {@code done}, each given to {@code picture} once. */
    static void pictures(Journal journal, Set<ExploreEvent.Pictured> done, Consumer<ExploreEvent.Pictured> picture) {
        pictures(JournalView.of(journal), done, picture);
    }

    private static void pictures(JournalView view, Set<ExploreEvent.Pictured> done,
                                 Consumer<ExploreEvent.Pictured> picture) {
        for (ExploreEvent event : view.events()) {
            if (event instanceof ExploreEvent.Pictured pictured && done.add(pictured)) picture.accept(pictured);
        }
    }

    /**
     * One look at the journal: the first stop the run settled and nobody resumed yet, open or parked, written by
     * {@code write} and resumed. One that does not write is resumed too, saying why: the run raises what was left out
     * as a stop of its own ({@code UNWRITTEN}), Claude's to write. One resume at a time: none while a command waits.
     */
    static void round(Journal journal, Set<String> done,
                      BiFunction<String, List<TreeAnswer>, TreeAnswers.Written> write) {
        round(journal, JournalView.of(journal), done, write);
    }

    private static void round(Journal journal, JournalView view, Set<String> done,
                              BiFunction<String, List<TreeAnswer>, TreeAnswers.Written> write) {
        if (journal.commandWaiting()) return;
        if (!view.running()) return;
        List<Stop> waiting = new ArrayList<>();
        view.open().ifPresent(waiting::add);
        waiting.addAll(view.queue());
        for (Stop stop : waiting) {
            String id = stop.id();
            // Ids start again with each run: one is done for the run that began then.
            String key = view.beganAt() + "/" + id;
            if (done.contains(key) || !view.settled(stop) || view.reloadOf(id).isPresent()) continue;
            done.add(key);
            TreeAnswers.Written written = write.apply(id, view.answers(id));
            written.failed().ifPresent(why -> SdkLog.warn("What the explore run settled at " + id
                    + " was not written: " + why));
            // Nothing written at all is said too: the run raises it, rather than meet the place again unexplained.
            List<String> leftOut = written.failed().map(why -> List.of("nothing was written: " + why))
                    .orElse(written.skipped());
            journal.command(Journal.Command.resume(id, leftOut));
            return;
        }
    }
}
