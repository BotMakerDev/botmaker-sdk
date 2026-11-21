// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.bot;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.sdk.internal.explore.ExploreBudget;
import com.botmaker.sdk.internal.explore.ResumeFile;

import java.util.List;

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
        // A Discover's arguments a restart through this entry keeps on for the next.
        ExploreEntry.main("Keep exploring", args, ExploreBudget.class, (budget, pending) -> new ExploreEntry.Start(
                ResumeFile.budgetOf(budget, pending), pending.map(ResumeFile::discover).orElse(null), List.of()));
    }
}
