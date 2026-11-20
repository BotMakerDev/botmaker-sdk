// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A run's memory, written when it holds a Discover fault (2026-11-19): what <em>Keep exploring</em>, restarted on the
 * rebuilt SDK, needs to go on where it was and replay the fault. It lies beside the journal, in {@code resume.json}:
 * run data, never the bot's, so never in {@code params/}, a version, a publish or a {@code .bmk}. Parked stops are not
 * in it: the journal's queue keeps them already.
 *
 * @param fault    the held stop, of kind {@link StopKind#FAULT} unless Claude called a plain stop one
 * @param replays  the kind of stop its place raises when the fault is still there: a fix that took passes it
 * @param source   what found the fault ({@code skipped}, {@code exception}, {@code claude}, …)
 * @param seen     the screens the run had reached
 * @param tried    the presses it had made, by place, each made once a run
 * @param worked   the ways it had pressed that arrived
 * @param answered the text places a missing fact was raised on
 * @param budget   the budget the run was started with, which a restart started with none takes (2026-11-20): a dev
 *                 reload or a crash restart hands the entry no arguments
 * @param discover what <em>Discover</em> was started with, taken the same way; null for a <em>Keep exploring</em> run
 */
public record ResumeFile(Stop fault, StopKind replays, String source, Set<String> seen, Set<String> tried,
                         Set<String> worked, Set<String> answered, ExploreBudget budget, DiscoverArgs discover) {

    /** The file's name in the journal's folder. */
    static final String FILE = "resume.json";

    public ResumeFile {
        budget = budget == null ? ExploreBudget.NONE : budget;
        replays = replays == null ? StopKind.UNKNOWN : replays;
        source = source == null ? "" : source;
        seen = seen == null ? Set.of() : Set.copyOf(seen);
        tried = tried == null ? Set.of() : Set.copyOf(tried);
        worked = worked == null ? Set.of() : Set.copyOf(worked);
        answered = answered == null ? Set.of() : Set.copyOf(answered);
    }

    /**
     * The file {@code journal} holds; empty when there is none, or it does not read (torn, or of no fault), said to
     * {@code warn}: a run then starts fresh.
     */
    public static Optional<ResumeFile> read(Journal journal, Consumer<String> warn) {
        try {
            Optional<ResumeFile> file = journal.readJson(FILE, ResumeFile.class);
            if (file.isPresent() && file.get().fault() == null) throw new IOException("it names no fault");
            return file;
        } catch (IOException | RuntimeException unread) {
            warn.accept("The explore run's resume file does not read, so the run starts fresh: " + unread.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The file a run starting now resumes from: the one {@code journal} holds, while the run that wrote it left its
     * fault held. One left by a run whose fault was let go since is stale: deleted, and the run starts fresh.
     */
    public static Optional<ResumeFile> pending(Journal journal, Consumer<String> warn) {
        Optional<ResumeFile> file = read(journal, warn);
        // One that does not read is said once, not at each start.
        if (file.isEmpty() || !JournalView.of(journal).heldWhenLeft(file.get().fault().id())) {
            delete(journal);
            return Optional.empty();
        }
        return file;
    }

    /**
     * The budget a run starting now takes: {@code given} when it gives an end, else the one {@code file} kept, so a
     * restart is the same run.
     */
    public static ExploreBudget budgetOf(ExploreBudget given, Optional<ResumeFile> file) {
        return given.given() ? given : file.map(ResumeFile::budget).orElse(given);
    }

    /** What a <em>Discover</em> starting now takes: {@code given} when it gives anything, else what {@code file} kept. */
    public static DiscoverArgs discoverOf(DiscoverArgs given, Optional<ResumeFile> file) {
        // A Keep exploring run's file, or an older SDK's, kept none.
        return given.given() ? given : file.map(ResumeFile::discover).filter(Objects::nonNull).orElse(given);
    }

    /** Replaces the file of {@code journal} with this one. */
    public void write(Journal journal) {
        journal.writeJson(FILE, this);
    }

    /** Deletes the file of {@code journal}, if it holds one. */
    public static void delete(Journal journal) {
        journal.delete(FILE);
    }
}
