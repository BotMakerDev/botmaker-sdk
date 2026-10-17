package com.botmaker.sdk.plugin.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreensFileTest {

    @Test
    void theFileIsFoundUnderTheMainSourcesAndChangesWhenWritten(@TempDir Path project) throws IOException {
        Path main = Files.createDirectories(project.resolve("src/main/java/bot")).resolve(ScreensFile.NAME);
        Files.writeString(Files.createDirectories(project.resolve("src/test/java/bot")).resolve(ScreensFile.NAME), "x");
        Files.writeString(main, "class Screens {}");
        Files.setLastModifiedTime(main, FileTime.fromMillis(1_000_000));

        ScreensFile first = ScreensFile.of(project, null).orElseThrow();
        assertEquals(main, first.file(), "the bot's own, not a test's copy");
        assertEquals(first, ScreensFile.of(project, first.file()).orElseThrow(), "unchanged: the same key");

        Files.writeString(main, "class Screens { }");
        assertNotEquals(first, ScreensFile.of(project, first.file()).orElseThrow(), "written: another key");
    }

    @Test
    void aProjectWithoutTheFileHasNone(@TempDir Path project) throws IOException {
        Files.createDirectories(project.resolve("src/main/java"));
        assertEquals(Optional.empty(), ScreensFile.of(project, null));
        assertEquals(Optional.empty(), ScreensFile.of(null, null));
        assertTrue(ScreensFile.of(project, project.resolve("gone.java")).isEmpty(), "where it was is looked past");
    }
}
