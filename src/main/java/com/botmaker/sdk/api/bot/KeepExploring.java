// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.run.RunArguments;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.explore.DiscoverArgs;
import com.botmaker.sdk.internal.explore.ExploreBudget;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.Generation;
import com.botmaker.sdk.internal.explore.LivePlay;
import com.botmaker.sdk.internal.explore.ResumeFile;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.trace.Say;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Studio's <em>Keep exploring</em> run (spec 2026-11-09): plays and explores the game for hours on its own, and stops
 * and hands the game to Claude whenever it meets what the bot does not know. A resume compiles the bot again and loads
 * it in this JVM, the game and the display staying; the run goes on from where it is. It never presses an input with
 * no verdict, nor anything the guards refuse. The journal of what it did is in the SDK's state folder of the bot.
 */
public final class KeepExploring {

    private KeepExploring() {}

    /**
     * The run's main: {@code args} are the binary names of the bot's value holders, as Studio hands a declared run.
     * The bot's classes are loaded as the run's first generation, so a resume can load the next one beside them.
     */
    @Hidden("Studio's Keep exploring run")
    public static void main(String[] args) {
        ExploreBudget budget;
        try {
            budget = RunArguments.read(ExploreBudget.class);
        } catch (IllegalArgumentException e) {
            Debug.error("Keep exploring cannot read its arguments: " + e.getMessage());
            return;
        }
        Generation first;
        try {
            first = Generation.first(List.of(args));
        } catch (RuntimeException | LinkageError e) {
            Debug.error("Keep exploring cannot load the bot's classes: " + e);
            return;
        }
        Bot.trial(() -> {
            first.install();
            Optional<ResumeFile> pending = LivePlay.pending(project());
            // A restart (a dev reload, a crash) is handed no arguments: it takes those the run kept.
            // A Discover's arguments a restart through this entry keeps on for the next.
            explore(first, ResumeFile.budgetOf(budget, pending), pending.map(ResumeFile::discover).orElse(null),
                    List.of(), pending);
        }, first.holders());
    }

    /** The bot's folder, the run's working directory. */
    static Path project() {
        return Path.of(System.getProperty("user.dir", "."));
    }

    /**
     * Starts the game as a run's cold start does, then explores until stopped or past {@code budget}, {@code settled}
     * handed on with the first stop it settles, going on from {@code pending}; {@code discover} is what a
     * <em>Discover</em> run was started with (null: none). {@code first} is installed already.
     */
    static void explore(Generation first, ExploreBudget budget, DiscoverArgs discover, List<TreeAnswer> settled,
                        Optional<ResumeFile> pending) {
        Bot.launchConfiguredTarget(Bot.StartMode.COLD);
        AtomicInteger restarts = new AtomicInteger();
        Runnable restart = () -> {
            // Restarts in a row wait longer each time, as a run's recoveries do.
            Wait.time(Bot.backOff(restarts.incrementAndGet()));
            Bot.launchConfiguredTarget(Bot.StartMode.RESTART);
        };
        ExploreLoop.End end = LivePlay.explore(first, project(), restart, budget, discover, settled, pending);
        Say.info(end == ExploreLoop.End.BUDGET ? "Keep exploring ended: its budget is spent."
                : "Keep exploring ended.");
    }
}
