// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeAnswerWatchTest {

    /**
     * The watch's half of a write the bot refused: the stop is resumed saying why, which the run raises as a stop of
     * kind {@code UNWRITTEN}, Claude's to write ({@code ExploreLoopTest.aSkippedAnswerIsATypedStopNotAFault}), never a
     * Discover fault.
     */
    @Test
    void anAnswerItCannotWriteIsAStop(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(true, 1));
        journal.append(new ExploreEvent.Settled("settled-1", List.of(new TreeAnswer.InputNote("A", "x", "y")), 2));
        journal.append(new ExploreEvent.Stopped(new Stop("settled-1", StopKind.SETTLED, "A", "A.x", null,
                List.of(), List.of(), Map.of(), List.of(), "notes", 2), false, 2));

        TreeAnswerWatch.round(journal, new HashSet<>(), (id, said) -> new TreeAnswers.Written(
                Optional.of("Home is the screen the game opens on: no way leads into it"), List.of()));

        assertEquals(Optional.of(new Journal.Command.Resume("settled-1", false, List.of("nothing was written: Home is "
                + "the screen the game opens on: no way leads into it"))), journal.takeCommand());
    }

    /** A run whose process is gone said nothing: the companion, told it ended, writes the end it could not (2026-11-24). */
    @Test
    void aRunThatDiedWithoutSayingSoIsEndedAsBroke(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Stopped(new Stop("settled-1", StopKind.SETTLED, "A", "A.x", null,
                List.of(), List.of(), Map.of(), List.of(), "notes", 2), false, 2));

        assertTrue(TreeAnswerWatch.endIfKilled(journal, 9));

        assertEquals(new ExploreEvent.Ended(ExploreLoop.End.BROKE, 9), journal.events().getLast());
        assertFalse(TreeAnswerWatch.endIfKilled(journal, 10), "said once");
    }

    @Test
    void aRunThatEndedItselfIsLeftAlone(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.Ended(ExploreLoop.End.DONE, 2));

        assertFalse(TreeAnswerWatch.endIfKilled(journal, 9));

        assertEquals(new ExploreEvent.Ended(ExploreLoop.End.DONE, 2), journal.events().getLast());
    }

    @Test
    void aJournalOfARunThatNeverBeganIsLeftAlone(@TempDir Path folder) {
        assertFalse(TreeAnswerWatch.endIfKilled(new Journal(folder), 9));
    }

}
