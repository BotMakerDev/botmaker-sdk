package com.botmaker.sdk.plugin.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The bot's {@code Screens.java} as a poll sees it: where it is, its size and when it was last written. 🗺 Screens
 * compares it before reading the whole map again (2026-10-17): every edit, the map's, the assistant's or an undo, is
 * written to the file, and a read opens every screen and input.
 */
record ScreensFile(Path file, long size, long modified) {

    static final String NAME = "Screens.java";

    /**
     * The file under {@code project}'s {@code src} now, looked for at {@code known} first (where it was last
     * time), else found; empty when the project has none or it cannot be read.
     */
    static Optional<ScreensFile> of(Path project, Path known) {
        Path file = known != null && Files.isRegularFile(known) ? known : find(project);
        if (file == null) return Optional.empty();
        try {
            return Optional.of(new ScreensFile(file, Files.size(file), Files.getLastModifiedTime(file).toMillis()));
        } catch (IOException gone) {
            return Optional.empty();
        }
    }

    private static Path find(Path project) {
        if (project == null) return null;
        // The bot's main sources first: a test copy of the file is not the one the map is read from.
        Path sources = Files.isDirectory(project.resolve("src/main/java")) ? project.resolve("src/main/java")
                : project.resolve("src");
        if (!Files.isDirectory(sources)) return null;
        try (Stream<Path> files = Files.walk(sources)) {
            return files.filter(path -> path.getFileName().toString().equals(NAME)).filter(Files::isRegularFile)
                    .findFirst().orElse(null);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }
}
