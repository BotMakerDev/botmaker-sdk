// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
