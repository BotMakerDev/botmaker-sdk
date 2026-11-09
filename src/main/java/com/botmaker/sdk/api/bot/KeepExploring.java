package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.internal.explore.Generation;
import com.botmaker.sdk.internal.explore.LivePlay;
import com.botmaker.sdk.internal.trace.Say;

import java.nio.file.Path;
import java.util.List;
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
        Generation first;
        try {
            first = Generation.first(List.of(args));
        } catch (RuntimeException | LinkageError e) {
            Debug.error("Keep exploring cannot load the bot's classes: " + e);
            return;
        }
        Bot.trial(() -> explore(first), first.holders());
    }

    /** Starts the game as a run's cold start does, then explores until stopped. */
    private static void explore(Generation first) {
        first.install();
        Bot.launchConfiguredTarget(Bot.StartMode.COLD);
        AtomicInteger restarts = new AtomicInteger();
        Runnable restart = () -> {
            // Restarts in a row wait longer each time, as a run's recoveries do.
            Wait.time(Bot.backOff(restarts.incrementAndGet()));
            Bot.launchConfiguredTarget(Bot.StartMode.RESTART);
        };
        LivePlay.explore(first, Path.of(System.getProperty("user.dir", ".")), restart);
        Say.info("Keep exploring ended.");
    }
}
