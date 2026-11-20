// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalPageTest {

    /** A website run's page reads back as written, replaced whole, and none is there before the first. */
    @Test
    void aPageReadsBackAsTheLastWritten(@TempDir Path dir) {
        Journal journal = new Journal(dir.resolve("explore"));
        assertTrue(journal.lastPage(0).isEmpty());

        journal.page(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB));
        journal.page(new BufferedImage(8, 5, BufferedImage.TYPE_INT_RGB));

        Journal.Page page = journal.lastPage(0).orElseThrow();
        assertEquals(8, page.image().getWidth());
        assertEquals(5, page.image().getHeight());
        assertTrue(page.writtenAt() > 0);
        assertFalse(Files.exists(dir.resolve("explore").resolve(Journal.PAGE + ".tmp")), "the temp file is moved");
        assertTrue(journal.lastPage(page.writtenAt()).isEmpty(), "the page its reader holds is not read again");

        journal.forgetPage();
        assertTrue(journal.lastPage(0).isEmpty(), "a run's start forgets the last run's page");
    }

    /** A run restarted on the rebuilt SDK is the same run: its history before stays, and its earlier end is no end. */
    @Test
    void aRestartedRunKeepsItsHistory(@TempDir Path dir) {
        Journal journal = new Journal(dir.resolve("explore"));
        Stop fault = new Stop("fault-1", StopKind.FAULT, "A", "A.d→D", "", List.of(), List.of(), Map.of(), List.of(),
                "judged wrong", 2);
        journal.append(new ExploreEvent.Began(true, 1));
        journal.append(new ExploreEvent.NewScreen("A", null, null, 1));
        journal.append(new ExploreEvent.Stopped(fault, false, 2));
        journal.append(new ExploreEvent.Ended(ExploreLoop.End.STOPPED, 3));
        assertTrue(JournalView.of(journal).heldWhenLeft("fault-1"));
        assertFalse(JournalView.of(journal).replaying(fault), "held, and no reload asked yet");
        journal.append(new ExploreEvent.Claude(JournalView.RELOAD_SDK + " fault-1", 3));
        assertTrue(JournalView.of(journal).replaying(fault), "a reload asked: the replay answers it");

        journal.append(new ExploreEvent.Restarted("fault-1", false, 4));
        journal.append(new ExploreEvent.NewScreen("D", "A", "d", 5));

        JournalView view = JournalView.of(journal);
        assertTrue(view.running(), "the end before the restart is no end");
        assertEquals(1, view.beganAt());
        assertEquals(2, view.count(ExploreEvent.NewScreen.class), "its history before the restart");
        assertEquals(Optional.of(fault), view.open(), "the fault waits on its replay");
        assertFalse(view.dev(), "the rebuilt SDK's word, not the first run's");
        assertTrue(view.replaying(fault), "a wait does not answer the fault again while it replays");
        journal.append(new ExploreEvent.Stopped(fault, true, 6));
        assertFalse(JournalView.of(journal).replaying(fault), "the replay stopped on it again: that is its answer");

        journal.append(new ExploreEvent.Ended(ExploreLoop.End.BUDGET, 9));
        assertEquals(Optional.of(ExploreLoop.End.BUDGET), JournalView.of(journal).ended());
        assertEquals(8, JournalView.of(journal).ranFor(100).toMillis());
    }
}
