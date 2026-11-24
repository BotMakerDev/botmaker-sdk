// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.SdkLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The Settler's work (spec 2026-11-24-explore-agents-design): writes what <em>Keep exploring</em> or <em>Discover</em>
 * settled off a screen's tree ({@link ExploreEvent.Settled}) into the bot, then resumes its stop, so the run goes on
 * without Claude (web discovery phase 6b). The Coordinator's host ({@link CoordinatorHost}), the SDK's companion of
 * those runs, leases each such stop to it.
 */
final class TreeAnswerWatch {

    private TreeAnswerWatch() {}

    /** Writes what the run settled at a stop of {@code journal}'s run. */
    interface Writer {
        TreeAnswers.Written write(Journal journal, String id, List<TreeAnswer> answers);
    }

    /** The writer into the bot {@code services} has open, frames given to the screens it adds. */
    static Writer into(StudioServices services) {
        return (journal, id, answers) -> {
            TreeAnswers.Written written = TreeAnswers.apply(services, "Explore: " + id, answers);
            if (written.failed().isEmpty()) shots(services, journal, id, answers);
            return written;
        };
    }

    /**
     * Writes {@link ExploreLoop.End#BROKE} at {@code nowMs} to a run's journal that began and never ended: the
     * host calls it once the run's process is gone, so a run that ended itself has said so already.
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

    /** The latest run's pictures not yet in {@code done}, each given to {@code picture} once. */
    static void pictures(Journal journal, Set<ExploreEvent.Pictured> done, Consumer<ExploreEvent.Pictured> picture) {
        pictures(JournalView.of(journal), done, picture);
    }

    static void pictures(JournalView view, Set<ExploreEvent.Pictured> done, Consumer<ExploreEvent.Pictured> picture) {
        for (ExploreEvent event : view.events()) {
            if (event instanceof ExploreEvent.Pictured pictured && done.add(pictured)) picture.accept(pictured);
        }
    }

    /**
     * One look at the journal without a Coordinator (tests that drive a run by hand): the first stop the run settled
     * and nobody resumed yet, open or parked, written by {@code write} and resumed. One resume at a time: none while a
     * command waits.
     */
    static void round(Journal journal, Set<String> done,
                      BiFunction<String, List<TreeAnswer>, TreeAnswers.Written> write) {
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
            settle(journal, view, id, write);
            return;
        }
    }

    /**
     * Writes what the run settled at {@code id} by {@code write}, and resumes it. One that does not write is resumed
     * too, saying why: the run raises what was left out as a stop of its own ({@code UNWRITTEN}), Claude's to write.
     *
     * @return why nothing was written, empty when it was
     */
    static Optional<String> settle(Journal journal, JournalView view, String id,
                                   BiFunction<String, List<TreeAnswer>, TreeAnswers.Written> write) {
        TreeAnswers.Written written = write.apply(id, view.answers(id));
        written.failed().ifPresent(why -> SdkLog.warn("What the explore run settled at " + id
                + " was not written: " + why));
        // Nothing written at all is said too: the run raises it, rather than meet the place again unexplained.
        List<String> leftOut = written.failed().map(why -> List.of("nothing was written: " + why))
                .orElse(written.skipped());
        journal.command(Journal.Command.resume(id, leftOut));
        return written.failed();
    }
}
