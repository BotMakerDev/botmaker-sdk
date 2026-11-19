// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.SdkLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Writes what <em>Keep exploring</em> settled off a screen's tree ({@link ExploreEvent.Settled}) into the bot while a
 * project is open, then resumes its stop, so the run goes on without Claude (web discovery phase 6b). The plugin is
 * opened in both the core and Studio: the process holding the journal's lock file writes, the other only watches for
 * the lock to come free.
 */
public final class TreeAnswerWatch {

    /** How often the journal is read. */
    static final long POLL_MS = 500;

    private static final String LOCK = "answers.lock";

    private static Thread thread;

    private TreeAnswerWatch() {}

    /** Watches the journal of the project {@code services} has open, until {@link #stop()}. */
    public static synchronized void start(StudioServices services) {
        stop();
        Path project = services.projectDir();
        if (project == null) return;
        Journal journal = Journal.of(project);
        thread = Thread.ofPlatform().daemon().name("explore-answers").start(() -> watch(journal, (id, answers) -> {
            Optional<String> failed = TreeAnswers.apply(services, "Explore: " + id, answers);
            if (failed.isEmpty()) shots(services, journal, id, answers);
            return failed;
        }, pictured -> TreeAnswers.pictured(services, pictured)));
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

    /** Stops watching. */
    public static synchronized void stop() {
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private static void watch(Journal journal, BiFunction<String, List<TreeAnswer>, Optional<String>> write,
                              Consumer<ExploreEvent.Pictured> picture) {
        Set<String> done = new HashSet<>();
        Set<ExploreEvent.Pictured> pictured = new HashSet<>();
        Path folder = journal.folder().toAbsolutePath().normalize();
        FileChannel channel = null;
        FileLock lock = null;
        String failedLast = null;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Another watcher of this JVM holding it is never asked through a channel of its own: closing that
                    // channel would drop the lock this JVM holds.
                    if (lock == null && Files.isDirectory(folder) && HELD.add(folder)) {
                        channel = FileChannel.open(folder.resolve(LOCK), StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE);
                        lock = tryLock(channel);
                        if (lock == null) {
                            channel.close();
                            channel = null;
                            HELD.remove(folder);
                        }
                    }
                    if (lock != null) {
                        round(journal, done, write);
                        pictures(journal, pictured, picture);
                    }
                    failedLast = null;
                } catch (IOException | RuntimeException failed) {
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
        } finally {
            try {
                if (lock != null) lock.release();
                if (channel != null) channel.close();
            } catch (IOException ignored) {
                // The lock goes with the process anyway.
            }
            if (lock != null) HELD.remove(folder);
        }
    }

    /** The latest run's pictures not yet in {@code done}, each given to {@code picture} once. */
    static void pictures(Journal journal, Set<ExploreEvent.Pictured> done, Consumer<ExploreEvent.Pictured> picture) {
        for (ExploreEvent event : JournalView.of(journal).events()) {
            if (event instanceof ExploreEvent.Pictured pictured && done.add(pictured)) picture.accept(pictured);
        }
    }

    /** The journal folders a watcher of this JVM holds the lock of. */
    private static final Set<Path> HELD = ConcurrentHashMap.newKeySet();

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException heldHere) {
            return null;
        }
    }

    /**
     * One look at the journal: the first stop the run settled and nobody resumed yet, open or parked, written by
     * {@code write} and resumed. One that does not write is resumed too, and comes back to the run as Claude's. One
     * resume at a time: none while a command waits.
     */
    static void round(Journal journal, Set<String> done,
                      BiFunction<String, List<TreeAnswer>, Optional<String>> write) {
        if (journal.commandWaiting()) return;
        JournalView view = JournalView.of(journal);
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
            write.apply(id, view.answers(id)).ifPresent(why -> SdkLog.warn("What the explore run settled at " + id
                    + " was not written: " + why));
            journal.command("resume " + id);
            return;
        }
    }
}
