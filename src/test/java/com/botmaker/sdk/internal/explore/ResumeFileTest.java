// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A run's memory while it holds a fault: written and read back whole, and a file that does not read is no resume. */
class ResumeFileTest {

    @TempDir
    Path folder;

    private static Stop fault() {
        return new Stop("fault-3", StopKind.FAULT, "A", "A.d→D", "/run/fault-3.png",
                List.of(new Stop.Text("Docs", null)), List.of("link Docs"), Map.of("gold", "12"), List.of("A: d"),
                "judged a toggle as navigation", 7);
    }

    @Test
    void itReadsBackAsWritten() {
        Journal journal = new Journal(folder);
        ResumeFile file = new ResumeFile(fault(), StopKind.WRONG_ARRIVAL, "claude", Set.of("A", "D"),
                Set.of("A.d→D", "A.b→B"), Set.of("A.b→B"), Set.of("A#20,40"), null, null);

        file.write(journal);

        assertEquals(Optional.of(file), ResumeFile.read(journal, why -> {}));
        ResumeFile.delete(journal);
        assertEquals(Optional.empty(), ResumeFile.read(journal, why -> {}));
    }

    @Test
    void keepsTheRunsArguments() {
        Journal journal = new Journal(folder);
        ExploreBudget budget = new ExploreBudget(10, null);
        DiscoverArgs discover = new DiscoverArgs("https://example.com", 10, 40, true);
        ResumeFile file = new ResumeFile(fault(), StopKind.CRASH, "exception", Set.of("A"), Set.of(), Set.of(),
                Set.of(), budget, discover);

        file.write(journal);

        ResumeFile back = ResumeFile.read(journal, why -> {}).orElseThrow();
        assertEquals(file, back);
        assertEquals(budget, ResumeFile.budgetOf(ExploreBudget.NONE, Optional.of(back)), "a restart given none");
        assertEquals(new ExploreBudget(3, null), ResumeFile.budgetOf(new ExploreBudget(3, null), Optional.of(back)),
                "a run given its own keeps them");
        assertEquals(discover, ResumeFile.discoverOf(new DiscoverArgs(null, null, null, null), Optional.of(back)));
        DiscoverArgs none = new DiscoverArgs(null, null, null, null);
        ResumeFile kept = new ResumeFile(fault(), StopKind.CRASH, "exception", Set.of(), Set.of(), Set.of(), Set.of(),
                budget, null);
        assertEquals(none, ResumeFile.discoverOf(none, Optional.of(kept)), "a Keep exploring file kept none");
    }

    @Test
    void aResumeFileThatDoesNotReadIsIgnored() throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(ResumeFile.FILE), "{\"fault\":{\"id\":\"fault-3\",\"kind\":\"FA");
        List<String> warned = new ArrayList<>();

        Optional<ResumeFile> pending = ResumeFile.pending(new Journal(folder), warned::add);

        assertEquals(Optional.empty(), pending);
        assertEquals(1, warned.size(), warned.toString());
        assertTrue(warned.getFirst().contains("starts fresh"), warned.getFirst());
        assertFalse(Files.exists(folder.resolve(ResumeFile.FILE)), "said once, not at each start");
    }

    @Test
    void aNewerSdksFileReadsWhatItKnows() throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(ResumeFile.FILE), "{\"fault\":{\"id\":\"fault-3\",\"kind\":\"LATER\","
                + "\"place\":\"A\"},\"replays\":\"LATER\",\"later\":1,\"tried\":[\"A.b→B\"]}");

        ResumeFile file = ResumeFile.read(new Journal(folder), why -> {}).orElseThrow();

        assertEquals(StopKind.UNKNOWN, file.replays());
        assertEquals(Set.of("A.b→B"), file.tried());
        assertEquals(Set.of(), file.seen());
    }
}
