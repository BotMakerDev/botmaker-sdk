// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JournalViewTest {

    @Test
    void readsFromItsOffset(@TempDir Path folder) throws IOException {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, 1));
        journal.append(new ExploreEvent.NewScreen("Home", null, null, 2));

        Journal.Read first = journal.events(0);
        assertEquals(2, first.events().size());

        journal.append(new ExploreEvent.NewScreen("Shop", "Home", "shop", 3));
        // A line still being written is left for the next read.
        Files.writeString(folder.resolve(Journal.EVENTS), "{\"type\":\"new_scr", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        Journal.Read second = journal.events(first.next());
        assertEquals(List.of(new ExploreEvent.NewScreen("Shop", "Home", "shop", 3)), second.events(),
                "only what was appended since the first read");
        assertEquals(second.next(), journal.events(second.next()).next(), "a cut line moves nothing on");
        journal.append(new ExploreEvent.Began(false, 5));
        assertEquals(List.of(new ExploreEvent.Began(false, 5)), journal.events(second.next()).events(),
                "an event appended after a cut line is not lost with it");

        Journal another = new Journal(folder.resolve("another"));
        another.append(new ExploreEvent.Began(false, 1));
        JournalView.Reader reader = new JournalView.Reader(another);
        assertEquals(1, reader.view().all().size());
        another.append(new ExploreEvent.NewScreen("Home", null, null, 2));
        assertEquals(2, reader.view().all().size(), "the reader keeps what it read and adds the rest");
        assertEquals(1, reader.view().count(ExploreEvent.NewScreen.class));
    }
}
