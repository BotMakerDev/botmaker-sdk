// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.bot;

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
import java.util.function.BiFunction;

/**
 * The one main of {@link KeepExploring} and {@link Discover} (N4, 2026-11-21): reads the run's arguments, loads the bot
 * as its first generation, takes a restart's kept arguments, starts the game and explores. What differs is how the
 * arguments start the run.
 */
final class ExploreEntry {

    private ExploreEntry() {}

    /**
     * How a run starts: its budget, what a <em>Discover</em> run was started with (null: none), and what its start
     * settled already, handed on with the first stop it settles.
     */
    record Start(ExploreBudget budget, DiscoverArgs discover, List<TreeAnswer> settled) {}

    /**
     * The run's main, {@code name} its name in what it says: {@code args} are the binary names of the bot's value
     * holders, {@code type} its arguments' record, and {@code start} turns them and a restart's kept memory into the
     * run's start, or null, said, when the run cannot start.
     */
    static <A extends Record> void main(String name, String[] args, Class<A> type, BiFunction<A, Optional<ResumeFile>, Start> start) {
        A given;
        try {
            given = RunArguments.read(type);
        } catch (IllegalArgumentException e) {
            Debug.error(name + " cannot read its arguments: " + e.getMessage());
            return;
        }
        Generation first;
        try {
            first = Generation.first(List.of(args));
        } catch (RuntimeException | LinkageError e) {
            Debug.error(name + " cannot load the bot's classes: " + e);
            return;
        }
        Bot.trial(() -> {
            first.install();
            // A restart (a dev reload, a crash) is handed no arguments: it takes those the run kept.
            Optional<ResumeFile> pending = LivePlay.pending(project());
            Start started = start.apply(given, pending);
            if (started == null) return;
            explore(name, first, started, pending);
        }, first.holders());
    }

    /** The bot's folder, the run's working directory. */
    static Path project() {
        return Path.of(System.getProperty("user.dir", "."));
    }

    /**
     * Starts the game as a run's cold start does, then explores until stopped or past the start's budget, going on
     * from {@code pending}. {@code first} is installed already.
     */
    private static void explore(String name, Generation first, Start start, Optional<ResumeFile> pending) {
        Bot.launchConfiguredTarget(Bot.StartMode.COLD);
        AtomicInteger restarts = new AtomicInteger();
        Runnable restart = () -> {
            // Restarts in a row wait longer each time, as a run's recoveries do.
            Wait.time(Bot.backOff(restarts.incrementAndGet()));
            Bot.launchConfiguredTarget(Bot.StartMode.RESTART);
        };
        ExploreLoop.End end = LivePlay.explore(first, project(), restart, start.budget(), start.discover(),
                start.settled(), pending);
        Say.info(switch (end) {
            case BUDGET -> name + " ended: its budget is spent.";
            case DONE -> name + " ended: the map is complete, nothing is left to press.";
            case STUCK -> name + " ended: another window stayed in front of the game, nothing could be pressed.";
            default -> name + " ended.";
        });
    }
}
