package com.botmaker.sdk.internal.explore;

import com.botmaker.plugin.api.PluginState;
import com.botmaker.sdk.internal.plan.DoneRecords;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A run's explore journal, the files the run and the SDK plugin (the {@code explore} tool, 🗺 Screens' 🔭 Explore) talk
 * through, in the SDK's state folder of the bot under {@code explore/}: run data, never versioned.
 *
 * <ul>
 *   <li>{@code events.jsonl}: every {@link ExploreEvent}, one line each, appended. A line cut by a killed JVM is
 *       skipped on reading, so the journal reads back up to its last whole line.</li>
 *   <li>{@code queue.json}: the parked stops, oldest first, replaced whole.</li>
 *   <li>{@code command}: written by the plugin side ({@code resume <stopId>}, {@code stop}); read and deleted by the
 *       run.</li>
 * </ul>
 *
 * A file that cannot be written leaves the run going: the journal never ends a run.
 */
public final class Journal {

    static final String FOLDER = "explore";
    public static final String EVENTS = "events.jsonl";
    public static final String QUEUE = "queue.json";
    static final String COMMAND = "command";

    /** Reads what a newer SDK wrote: a field it added is skipped, a constant it added reads as UNKNOWN, else null. */
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

    /** What the plugin side asks of the run. */
    public sealed interface Command {
        /**
         * Reload the bot and go on from {@code stopId}; {@code ignore}: what it stopped on is fine as it is (a value
         * no fact needs), never raised again.
         */
        record Resume(String stopId, boolean ignore) implements Command {}

        /** End the run. */
        record End() implements Command {}

        /** {@code line} as a command ({@code resume <id> [ignore]}, {@code stop}); empty for one this run does not know. */
        static Optional<Command> parse(String line) {
            String[] said = (line == null ? "" : line.strip()).split("\\s+");
            if (said.length == 1 && said[0].equals("stop")) return Optional.of(new End());
            if (said[0].equals("resume") && (said.length == 2 || said.length == 3 && said[2].equals("ignore"))) {
                return Optional.of(new Resume(said[1], said.length == 3));
            }
            return Optional.empty();
        }
    }

    private final Path folder;

    public Journal(Path folder) {
        this.folder = folder;
    }

    /** The journal of the bot in {@code project}. */
    public static Journal of(Path project) {
        return new Journal(PluginState.folder(project, DoneRecords.GROUP, DoneRecords.ARTIFACT).resolve(FOLDER));
    }

    public Path folder() {
        return folder;
    }

    /** Appends {@code event} as one line. */
    public synchronized void append(ExploreEvent event) {
        try {
            ObjectNode line = JSON.createObjectNode().put("type", event.type());
            line.setAll((ObjectNode) JSON.valueToTree(event));
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(EVENTS), JSON.writeValueAsString(line) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException unwritten) {
            // the run goes on: its run log says the same
        }
    }

    /** Every event read back, oldest first; a line that does not read (cut, or of a newer SDK) is left out. */
    public List<ExploreEvent> events() {
        List<ExploreEvent> events = new ArrayList<>();
        for (String line : lines(folder.resolve(EVENTS))) {
            try {
                JsonNode node = JSON.readTree(line);
                Class<? extends ExploreEvent> type = node == null ? null : ExploreEvent.typed(node.path("type").asText());
                if (type != null) events.add(JSON.treeToValue(node, type));
            } catch (JsonProcessingException | RuntimeException unread) {
                // a cut last line, or one this SDK cannot read
            }
        }
        return events;
    }

    /** The parked stops, oldest first; none when the file is missing or does not read. */
    public List<Stop> queue() {
        try {
            return List.copyOf(JSON.readValue(folder.resolve(QUEUE).toFile(), new TypeReference<List<Stop>>() {}));
        } catch (IOException | RuntimeException unread) {
            return List.of();
        }
    }

    /** Replaces the queue with {@code stops}, whole or not at all. */
    public synchronized void queue(List<Stop> stops) {
        write(QUEUE, () -> JSON.writeValueAsString(stops));
    }

    /** Writes {@code command} for the run to take: the plugin side's half. */
    public void command(String command) {
        write(COMMAND, () -> command);
    }

    /** The command waiting, read and deleted; empty when there is none, or it does not read. */
    public Optional<Command> takeCommand() {
        Path file = folder.resolve(COMMAND);
        if (!Files.exists(file)) return Optional.empty();
        // Moved aside before it is read, so a command written meanwhile is a new file, never deleted unread.
        Path taken = folder.resolve(COMMAND + ".taken");
        try {
            Files.move(file, taken, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            String said = Files.readString(taken, StandardCharsets.UTF_8);
            Files.deleteIfExists(taken);
            return Command.parse(said);
        } catch (IOException unread) {
            return Optional.empty();
        }
    }

    private interface Text {
        String get() throws IOException;
    }

    /** Writes {@code name} through a file beside it, moved over it, so a reader never sees half of it. */
    private void write(String name, Text text) {
        try {
            Files.createDirectories(folder);
            Path temp = folder.resolve(name + ".tmp");
            Files.writeString(temp, text.get(), StandardCharsets.UTF_8);
            Files.move(temp, folder.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException unwritten) {
            // the run keeps its own memory of it
        }
    }

    /** {@code file}'s lines; bytes cut mid-character decode as a replacement, so the lines before them still read. */
    private static List<String> lines(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank()).toList();
        } catch (IOException | RuntimeException unread) {
            return List.of();
        }
    }
}
