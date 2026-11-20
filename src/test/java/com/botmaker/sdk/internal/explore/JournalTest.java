// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
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
