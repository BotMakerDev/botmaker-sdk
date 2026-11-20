// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalTest {

    @Test
    void commandsAreTakenInOrder(@TempDir Path folder) {
        Journal journal = new Journal(folder);

        journal.command("resume settled-1");
        journal.command(Journal.Command.resume("guard-2", List.of("x: no screen GONE in the map.")));
        assertTrue(journal.commandWaiting());

        assertEquals(Optional.of(new Journal.Command.Resume("settled-1", false)), journal.takeCommand());
        assertTrue(journal.commandWaiting(), "the second is not lost");
        assertEquals(Optional.of(new Journal.Command.Resume("guard-2", false, List.of("x: no screen GONE in the map."))),
                journal.takeCommand());
        assertFalse(journal.commandWaiting());
        assertEquals(Optional.empty(), journal.takeCommand(), "each taken once");
    }

    /** A park names one stop; a stop's line carries who takes it, for the watcher, and reads back without it. */
    @Test
    void aParkIsACommandAndAStopSaysItsRole(@TempDir Path folder) throws IOException {
        assertEquals(Optional.of(new Journal.Command.Park("guard-1")), Journal.Command.parse("park guard-1"));
        assertEquals(Optional.empty(), Journal.Command.parse("park"));

        Journal journal = new Journal(folder);
        Stop stop = new Stop("fault-1", StopKind.FAULT, "A", "A", "", List.of(), List.of(), Map.of(),
                List.of(), "x", 1);
        journal.append(new ExploreEvent.Stopped(stop, false, 1));
        journal.queue(List.of(stop));

        assertTrue(journal.events().getFirst() instanceof ExploreEvent.Stopped(Stop read, boolean again, long at)
                && read.equals(stop), journal.events().toString());
        assertEquals(List.of(stop), journal.queue());
        assertTrue(Files.readString(folder.resolve(Journal.EVENTS)).contains("\"role\":\"mechanic\""));
        assertTrue(Files.readString(folder.resolve(Journal.QUEUE)).contains("\"role\":\"mechanic\""));
    }

    @Test
    void aLiveRunsScreenIsItsLastWhereAndNoneOnceItEnded(@TempDir Path project) {
        assertEquals(Optional.empty(), Journal.liveAt(project), "no run yet");
        Journal run = Journal.begin(project, 1_000);
        run.append(new ExploreEvent.Began(false, 1_000));
        assertEquals(Optional.empty(), Journal.liveAt(project), "before it reached a screen");
        run.append(new ExploreEvent.NewScreen("HOME", null, null, 1_100));
        run.append(new ExploreEvent.NewScreen("SHOP", "HOME", "shop", 1_200));
        assertEquals(Optional.of("SHOP"), Journal.liveAt(project));
        run.append(new ExploreEvent.Ended(ExploreLoop.End.STOPPED, 1_300));
        assertEquals(Optional.empty(), Journal.liveAt(project), "an ended run stands nowhere");
        assertEquals(Optional.empty(), Journal.liveAt(null));
    }

    @Test
    void eachRunWritesItsOwnFolder(@TempDir Path project) {
        Journal first = Journal.begin(project, 1_000);
        first.append(new ExploreEvent.Began(false, 1_000));
        Stop parked = new Stop("tap-7", StopKind.MISSING_CONDITION, "Home", "", "", List.of(), List.of(), Map.of(),
                List.of(), "no condition", 1_000);
        first.queue(List.of(parked));
        Journal second = Journal.begin(project, 1_000);
        second.append(new ExploreEvent.Began(false, 2_000));

        assertEquals(List.of(first.runId(), second.runId()), Journal.runs(project), "the index lists both, oldest first");
        assertFalse(first.folder().equals(second.folder()), "one folder a run, even begun in the same second");
        assertTrue(Files.exists(first.folder().resolve(Journal.EVENTS)));
        assertTrue(Files.exists(second.folder().resolve(Journal.EVENTS)));
        assertEquals(1, first.events().size(), "a run's events are its own");
        assertEquals(List.of(parked), second.queue(), "what a run parked is carried into the next");
        assertEquals(Optional.of(second.folder()), Journal.latest(project).map(Journal::folder));
        assertEquals(Optional.of(first.folder()), Journal.of(project, first.runId()).map(Journal::folder));
        assertEquals(Optional.empty(), Journal.of(project, "../elsewhere"));
    }

    @Test
    void aRunDropsWhatARunBeforeLeft(@TempDir Path folder) {
        Journal journal = new Journal(folder);
        journal.command("resume settled-1");
        journal.command("stop");

        journal.dropCommands();
        journal.command("resume settled-2");

        assertEquals(Optional.of(new Journal.Command.Resume("settled-2", false)), journal.takeCommand());
        assertFalse(journal.commandWaiting());
    }
}
