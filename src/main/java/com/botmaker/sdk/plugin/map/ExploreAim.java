// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * The Explore page's goal field, kept per bot (plan 2026-11-24-explore-drive-fixes, phase 7): its last text in the
 * SDK's state folder of the bot, and what <em>Keep exploring</em> is started with for it.
 *
 * @param file where the text is kept; null where the host gives no state folder, so nothing is kept
 */
record ExploreAim(Path file) {

    /** The file in the SDK's state folder of the bot. */
    static final String FILE = "explore-goal.txt";
    /** The {@code ExploreBudget} component a goal is handed to the run as. */
    static final String GOAL = "goal";

    /** The aim kept in {@code stateDir}, the host's state folder of the bot (none: nothing kept). */
    static ExploreAim in(Optional<Path> stateDir) {
        return new ExploreAim(stateDir.map(dir -> dir.resolve(FILE)).orElse(null));
    }

    /** The text last kept; empty when none was, or it cannot be read. */
    String read() {
        if (file == null || !Files.isRegularFile(file)) return "";
        try {
            return Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            return "";
        }
    }

    /**
     * Keeps {@code text} for the next time the page opens; a blank text forgets the last. False when it could not be
     * written: the run starts all the same, the field just forgets it.
     */
    boolean keep(String text) {
        if (file == null) return true;
        String goal = text == null ? "" : text.strip();
        try {
            if (goal.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, goal, StandardCharsets.UTF_8);
            }
            return true;
        } catch (IOException unwritable) {
            return false;
        }
    }

    /** What <em>Keep exploring</em> is started with for {@code text}: its goal, or nothing for a blank one. */
    static Map<String, Object> arguments(String text) {
        String goal = text == null ? "" : text.strip();
        return goal.isEmpty() ? Map.of() : Map.of(GOAL, goal);
    }
}
