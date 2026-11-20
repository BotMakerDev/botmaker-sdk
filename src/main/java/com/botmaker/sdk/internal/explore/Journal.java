// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.plugin.api.PluginState;
import com.botmaker.sdk.internal.plan.DoneRecords;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A run's explore journal, the files the run and the SDK plugin (the {@code explore} tool, 🗺 Screens' 🔭 Explore) talk
 * through, in the SDK's state folder of the bot under {@code explore/<runId>/}, one folder a run, which
 * {@code explore/runs.json} lists oldest first (2026-11-20; one journal of the bot grew without end): run data,
 * never versioned.
 *
 * <ul>
 *   <li>{@code events.jsonl}: every {@link ExploreEvent}, one line each, appended. A line cut by a killed JVM is
 *       skipped on reading, so the journal reads back up to its last whole line.</li>
 *   <li>{@code queue.json}: the parked stops, oldest first, replaced whole.</li>
 *   <li>{@code page.png}: a website run's page as it last looked, replaced whole, for the window to show.</li>
 *   <li>{@code commands.jsonl}: appended by the plugin side, one command a line with its id ({@code resume <stopId>},
 *       {@code stop}); {@code commands.taken}: appended by the run, the id of each it took, so each is taken once and
 *       in order.</li>
 *   <li>{@code resume.json}: the run's memory while it holds a Discover fault ({@link ResumeFile}), for the run
 *       restarted on the rebuilt SDK.</li>
 * </ul>
 *
 * A file that cannot be written leaves the run going: the journal never ends a run.
 */
public final class Journal {

    static final String FOLDER = "explore";
    public static final String EVENTS = "events.jsonl";
    public static final String QUEUE = "queue.json";
    static final String PAGE = "page.png";
    static final String COMMANDS = "commands.jsonl";
    static final String TAKEN = "commands.taken";

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
        record Resume(String stopId, boolean ignore, List<String> skipped) implements Command {

            /**
             * {@code skipped}: what the plugin side left out of the answers the run settled at {@code stopId}, one
             * line each, why with it (2026-11-19); the run raises it again rather than let it be lost.
             */
            public Resume {
                skipped = List.copyOf(skipped);
            }

            public Resume(String stopId, boolean ignore) {
                this(stopId, ignore, List.of());
            }
        }

        /** End the run. */
        record End() implements Command {}

        /** Hold the open stop {@code stopId} as a Discover fault, {@code why} one line (2026-11-19): dev mode only. */
        record Fault(String stopId, String why) implements Command {}

        /** Park the open stop {@code stopId} now, for the user to take (2026-11-21): the run goes on elsewhere. */
        record Park(String stopId) implements Command {}

        /**
         * {@code text} as a command, read off its first line ({@code resume <id> [ignore]}, {@code fault <id> <why>},
         * {@code park <id>}, {@code stop}); a resume's further lines are what was skipped. Empty for one this run does
         * not know.
         */
        static Optional<Command> parse(String text) {
            List<String> lines = (text == null ? "" : text).lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
            String line = lines.isEmpty() ? "" : lines.getFirst();
            String[] said = line.split("\\s+");
            if (said.length == 1 && said[0].equals("stop")) return Optional.of(new End());
            if (said.length == 2 && said[0].equals("park")) return Optional.of(new Park(said[1]));
            if (said[0].equals("fault") && said.length >= 3) {
                String[] parts = line.split("\\s+", 3);
                return Optional.of(new Fault(parts[1], parts[2].strip()));
            }
            if (said[0].equals("resume") && (said.length == 2 || said.length == 3 && said[2].equals("ignore"))) {
                return Optional.of(new Resume(said[1], said.length == 3, lines.subList(1, lines.size())));
            }
            return Optional.empty();
        }

        /** The text of a resume of {@code stopId} that says {@code skipped}, each on a line of its own. */
        public static String resume(String stopId, List<String> skipped) {
            StringBuilder text = new StringBuilder("resume ").append(stopId);
            for (String line : skipped) text.append('\n').append(line.replaceAll("\\s+", " ").strip());
            return text.toString();
        }
    }

    /** The index of the runs in the {@code explore/} folder, their ids oldest first. */
    static final String RUNS = "runs.json";
    private static final DateTimeFormatter RUN_ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private final Path folder;

    /** The journal kept in {@code folder}: one run's. */
    public Journal(Path folder) {
        this.folder = folder;
    }

    /**
     * Begins the journal of a new run of the bot in {@code project} (2026-11-20): its own folder {@code explore/<id>/},
     * its id added to the index, the latest run's parked stops carried into it, since what a run left for later is the
     * bot's, not the run's. A run restarted from its {@link ResumeFile} goes on in its own journal ({@link #latest}).
     */
    public static synchronized Journal begin(Path project, long nowMs) {
        Path root = root(project);
        Optional<Journal> before = latest(project);
        List<String> runs = new ArrayList<>(runs(project));
        String base = RUN_ID.format(Instant.ofEpochMilli(nowMs));
        String id = base;
        for (int n = 2; runs.contains(id) || Files.exists(root.resolve(id)); n++) id = base + "-" + n;
        Journal journal = new Journal(root.resolve(id));
        before.map(Journal::queue).filter(q -> !q.isEmpty()).ifPresent(journal::queue);
        runs.add(id);
        new Journal(root).write(RUNS, () -> JSON.writeValueAsString(runs));
        return journal;
    }

    /** The journal of the latest run of the bot in {@code project}; empty before any. */
    public static Optional<Journal> latest(Path project) {
        List<String> runs = runs(project);
        return runs.isEmpty() ? Optional.empty() : Optional.of(new Journal(root(project).resolve(runs.getLast())));
    }

    /**
     * The screen the latest run of the bot in {@code project} stands on while it runs, as its journal last said it;
     * empty when no run is live, before it reached a screen, or with no project. The assistant's tools and 🗺 Screens
     * name the next screen to explore from it, as the run does.
     */
    public static Optional<String> liveAt(Path project) {
        if (project == null) return Optional.empty();
        return latest(project).map(JournalView::of).filter(JournalView::running).flatMap(JournalView::where);
    }

    /** The journal of the run {@code runId} of the bot in {@code project}; empty when the index has none of that id. */
    public static Optional<Journal> of(Path project, String runId) {
        return runs(project).contains(runId) ? Optional.of(new Journal(root(project).resolve(runId))) : Optional.empty();
    }

    /** The ids of the runs of the bot in {@code project}, oldest first; none when the index is missing or unread. */
    public static List<String> runs(Path project) {
        try {
            Path index = root(project).resolve(RUNS);
            if (!Files.exists(index)) return List.of();
            List<String> runs = JSON.readValue(index.toFile(), new TypeReference<List<String>>() {});
            return runs == null ? List.of() : runs.stream().filter(id -> id != null && !id.isBlank()
                    && !id.contains("/") && !id.contains("\\") && !id.contains("..")).toList();
        } catch (IOException | RuntimeException unread) {
            return List.of();
        }
    }

    private static Path root(Path project) {
        return PluginState.folder(project, DoneRecords.GROUP, DoneRecords.ARTIFACT).resolve(FOLDER);
    }

    public Path folder() {
        return folder;
    }

    /** The run's id: its folder's name. */
    public String runId() {
        return folder.getFileName().toString();
    }

    /** Appends {@code event} as one line. */
    public synchronized void append(ExploreEvent event) {
        try {
            ObjectNode line = JSON.createObjectNode().put("type", event.type());
            line.setAll((ObjectNode) JSON.valueToTree(event));
            Files.createDirectories(folder);
            Path file = folder.resolve(EVENTS);
            // A last line a killed run cut is ended first, so this one is not glued to it and lost with it.
            String cut = endsCut(file) ? "\n" : "";
            Files.writeString(file, cut + JSON.writeValueAsString(line) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException unwritten) {
            // the run goes on: its run log says the same
        }
    }

    /** Whether {@code file} ends inside a line: its last byte is no line's end. */
    private static boolean endsCut(Path file) {
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            long size = channel.size();
            if (size == 0) return false;
            ByteBuffer last = ByteBuffer.allocate(1);
            channel.position(size - 1);
            channel.read(last);
            return last.get(0) != '\n';
        } catch (IOException | RuntimeException none) {
            return false;
        }
    }

    /** Every event read back, oldest first; a line that does not read (cut, or of a newer SDK) is left out. */
    public List<ExploreEvent> events() {
        List<ExploreEvent> events = new ArrayList<>();
        for (String line : lines(folder.resolve(EVENTS))) event(line).ifPresent(events::add);
        // A last line with no end yet is read here too: the run that wrote it was killed, or writes it now.
        return events;
    }

    /** The events written from byte {@code from} of {@link #EVENTS} on, and where the next read starts. */
    public record Read(List<ExploreEvent> events, long next) {
        public Read {
            events = List.copyOf(events);
        }
    }

    /**
     * The events appended since byte {@code from}: only whole lines, so a line still being written is read by the
     * next call, which starts at {@link Read#next}. Nothing new, or a file that does not read, reads none from
     * {@code from}.
     */
    public Read events(long from) {
        Path file = folder.resolve(EVENTS);
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            long size = channel.size();
            if (size <= from) return new Read(List.of(), from);
            ByteBuffer bytes = ByteBuffer.allocate(Math.toIntExact(size - from));
            channel.position(from);
            while (bytes.hasRemaining() && channel.read(bytes) >= 0) {
                // read to the size it had
            }
            byte[] read = Arrays.copyOf(bytes.array(), bytes.position());
            int end = read.length;
            while (end > 0 && read[end - 1] != '\n') end--;
            List<ExploreEvent> events = new ArrayList<>();
            new String(read, 0, end, StandardCharsets.UTF_8).lines().filter(line -> !line.isBlank())
                    .forEach(line -> event(line).ifPresent(events::add));
            return new Read(events, from + end);
        } catch (IOException | RuntimeException unread) {
            return new Read(List.of(), from);
        }
    }

    private static Optional<ExploreEvent> event(String line) {
        try {
            JsonNode node = JSON.readTree(line);
            Class<? extends ExploreEvent> type = node == null ? null : ExploreEvent.typed(node.path("type").asText());
            return type == null ? Optional.empty() : Optional.ofNullable(JSON.treeToValue(node, type));
        } catch (JsonProcessingException | RuntimeException unread) {
            // a cut last line, or one this SDK cannot read
            return Optional.empty();
        }
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

    /**
     * Replaces {@link #PAGE} with {@code page}, whole or not at all: a website run's page as it last looked, which the
     * Explore window shows, since no capture of this PC sees the run's browser.
     */
    public synchronized void page(BufferedImage page) {
        try {
            Files.createDirectories(folder);
            Path temp = folder.resolve(PAGE + ".tmp");
            ImageIO.write(page, "png", temp.toFile());
            Files.move(temp, folder.resolve(PAGE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException unwritten) {
            // the window shows the frame it last read
        }
    }

    /** Deletes the page a run before wrote, so no window shows it as this run's. */
    public synchronized void forgetPage() {
        try {
            Files.deleteIfExists(folder.resolve(PAGE));
        } catch (IOException unread) {
            // the window still shows only a page written since this run began
        }
    }

    /**
     * The page {@link #page} last wrote, with when it wrote it; empty when there is none, it does not read, or it was
     * written at {@code knownAt} still: the page its reader holds already, not decoded again.
     */
    public Optional<Page> lastPage(long knownAt) {
        Path file = folder.resolve(PAGE);
        try {
            long at = Files.getLastModifiedTime(file).toMillis();
            if (at == knownAt) return Optional.empty();
            BufferedImage image = ImageIO.read(file.toFile());
            return image == null ? Optional.empty() : Optional.of(new Page(image, at));
        } catch (IOException | RuntimeException unread) {
            return Optional.empty();
        }
    }

    /** A page {@link #page} wrote, and when, in epoch milliseconds. */
    public record Page(BufferedImage image, long writtenAt) {}

    /** A command as {@link #COMMANDS} holds it: its id and its text. */
    record Written(String id, String text) {}

    /**
     * Appends {@code command} for the run to take: the plugin side's half. Two written before the run takes either are
     * both taken, in the order written (2026-11-20; a single file let the second replace the first).
     */
    public synchronized void command(String command) {
        try {
            Files.createDirectories(folder);
            String line = JSON.writeValueAsString(new Written(UUID.randomUUID().toString(), command)) + "\n";
            Files.writeString(folder.resolve(COMMANDS), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException unwritten) {
            // the plugin side's tool says it could not reach the run when the run never answers
        }
    }

    /** Whether a command is written and the run has not taken it yet. */
    public boolean commandWaiting() {
        return !waiting().isEmpty();
    }

    /** The first command waiting, read and marked taken; empty when there is none, or it does not read. */
    public synchronized Optional<Command> takeCommand() {
        List<Written> waiting = waiting();
        if (waiting.isEmpty()) return Optional.empty();
        Written first = waiting.getFirst();
        taken(List.of(first.id()));
        return Command.parse(first.text());
    }

    /** Marks every command waiting taken, unread: what a run before left is not this run's. */
    public synchronized void dropCommands() {
        taken(waiting().stream().map(Written::id).toList());
    }

    /** The commands written and not taken, oldest first; a line cut by a writer still writing waits for its end. */
    private List<Written> waiting() {
        Set<String> taken = new HashSet<>(lines(folder.resolve(TAKEN)));
        List<Written> waiting = new ArrayList<>();
        for (String line : lines(folder.resolve(COMMANDS))) {
            try {
                Written command = JSON.readValue(line, Written.class);
                if (command != null && command.id() != null && !taken.contains(command.id())) waiting.add(command);
            } catch (JsonProcessingException | RuntimeException unread) {
                // a line still being written
            }
        }
        return waiting;
    }

    private void taken(List<String> ids) {
        if (ids.isEmpty()) return;
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(TAKEN), String.join("\n", ids) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException unwritten) {
            // taken again next time: a resume of a stop already resumed is stale and changes nothing
        }
    }

    /** Writes {@code value} as JSON in the file {@code name} of the journal's folder, whole or not at all. */
    synchronized void writeJson(String name, Object value) {
        write(name, () -> JSON.writeValueAsString(value));
    }

    /**
     * The file {@code name} of the journal's folder read as {@code type}, leniently as the events are; empty when it is
     * not there.
     *
     * @throws IOException when it is there and does not read
     */
    <T> Optional<T> readJson(String name, Class<T> type) throws IOException {
        Path file = folder.resolve(name);
        if (!Files.exists(file)) return Optional.empty();
        return Optional.ofNullable(JSON.readValue(file.toFile(), type));
    }

    /** Deletes the file {@code name} of the journal's folder, if it is there. */
    synchronized void delete(String name) {
        try {
            Files.deleteIfExists(folder.resolve(name));
        } catch (IOException undeleted) {
            // read again by the next run, which checks it against the journal
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
