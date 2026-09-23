package com.botmaker.sdk.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The standard plugin package tree ({@code docs/refactor/34-plugin-package-tree.md}), held by a source scan in
 * the style of Studio's {@code StudioSourcesTest}.
 *
 * <ul>
 *   <li>{@code com.botmaker.sdk} has three children and no others: {@code api}, {@code internal} and
 *       {@code plugin}. A fourth is how {@code authoring} happened.</li>
 *   <li><b>Rule 1:</b> {@code api} and {@code internal} never name a {@code plugin} class. A bot loads both and
 *       must never link the Studio half.</li>
 *   <li><b>Rule 2:</b> only {@code plugin} names JavaFX or {@code botmaker-plugin-toolkit}. A bot has neither on
 *       its classpath at run time.</li>
 * </ul>
 *
 * <p>Only code is scanned: a comment line may name anything, so a javadoc explaining why {@code api} does not
 * touch JavaFX does not trip the rule it explains.
 */
class PluginLayersTest {

    private static final Path ROOT = Path.of("src/main/java/com/botmaker/sdk");

    /** What the bot half must never name, and why it matters at run time. */
    private static final List<String> STUDIO_ONLY = List.of(
            "com.botmaker.sdk.plugin.", "javafx.", "com.botmaker.plugin.toolkit.");

    @Test
    void theSdkHasExactlyTheThreeLayers() throws IOException {
        Set<String> children = new TreeSet<>();
        try (Stream<Path> list = Files.list(ROOT)) {
            list.filter(Files::isDirectory).forEach(p -> children.add(p.getFileName().toString()));
        }
        assertEquals(Set.of("api", "internal", "plugin"), children);
    }

    @Test
    void theBotHalfNamesNothingOfTheStudioHalf() {
        List<String> offences = new ArrayList<>();
        for (String layer : List.of("api", "internal")) {
            for (Path file : sources(ROOT.resolve(layer))) {
                List<String> lines = read(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (isComment(line)) continue;
                    for (String name : STUDIO_ONLY) {
                        if (line.contains(name)) {
                            offences.add(ROOT.relativize(file) + ":" + (i + 1) + " names " + name + "*");
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), offences, "api/ and internal/ run inside a bot; only plugin/ may name these");
    }

    private static boolean isComment(String line) {
        return line.startsWith("//") || line.startsWith("*") || line.startsWith("/*");
    }

    private static List<Path> sources(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
