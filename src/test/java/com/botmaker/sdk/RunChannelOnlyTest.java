package com.botmaker.sdk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The bot half reaches its host through the contract's run channel only (spec {@code
 * docs/superpowers/specs/2026-11-03-run-reports-design.md} §7): no class under {@code api} or {@code internal} names
 * shared's log, run files or telemetry wire.
 */
class RunChannelOnlyTest {

    private static final List<String> FORBIDDEN =
            List.of("com.botmaker.shared.Diag", "com.botmaker.shared.runlog", "com.botmaker.shared.ipc");

    @Test
    void theBotHalfNamesNoneOfSharedsRunPlumbing() throws IOException {
        Path root = Path.of("src/main/java/com/botmaker/sdk");
        List<String> found;
        try (Stream<Path> files = Stream.concat(Files.walk(root.resolve("api")), Files.walk(root.resolve("internal")))) {
            found = files.filter(f -> f.toString().endsWith(".java"))
                    .flatMap(f -> {
                        try {
                            String text = Files.readString(f);
                            return FORBIDDEN.stream().filter(text::contains).map(name -> root.relativize(f) + ": " + name);
                        } catch (IOException unreadable) {
                            return Stream.of(f + ": unreadable");
                        }
                    })
                    .toList();
        }
        assertEquals(List.of(), found);
    }
}
