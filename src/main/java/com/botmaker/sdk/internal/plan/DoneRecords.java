package com.botmaker.sdk.internal.plan;

import com.botmaker.plugin.api.PluginState;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.screens.Conditions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * When each {@code Do} was last done and whether its check held, keyed {@code <Screen constant>.<field>}: run data, in
 * the SDK's state folder of the bot ({@link PluginState}), so never versioned or published. A missing or unreadable file
 * is no record yet, and a file that cannot be written leaves the run's own memory of it: neither ends a run.
 */
public final class DoneRecords {

    /** The SDK's coordinate, which names its state folder. */
    public static final String GROUP = "com.github.BotMakerDev";
    public static final String ARTIFACT = "botmaker-sdk";
    /** The file in that folder. */
    static final String FILE = "done.json";

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * An action's last time.
     *
     * @param at    when it was done
     * @param held  whether its check held
     * @param fails how many times in a row it has not, up to this one
     */
    public record Done(Instant at, boolean held, int fails) {}

    private static final class Run {
        // Read on a run's first walk; only a Do done writes it. Timed in the game server's zone, which a reset with
        // none of its own is read in.
        static final DoneRecords RECORDS = new DoneRecords(PluginState
                .folder(Path.of(System.getProperty("user.dir", ".")), GROUP, ARTIFACT).resolve(FILE), new GameClock());
    }

    /**
     * The system clock in the game server's zone as the bot's parameters say it now ({@link InstalledGame#gameZone}),
     * read on each use: records made before the parameters were installed, or kept across a second run in one JVM,
     * still read the zone that run set.
     */
    private static final class GameClock extends Clock {
        @Override
        public ZoneId getZone() {
            return InstalledGame.gameZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.system(zone);
        }

        @Override
        public Instant instant() {
            return Instant.now();
        }
    }

    private final Path file;
    private final Clock clock;
    private final Map<String, Done> done;
    /** What is being done now, which a walk under it leaves alone. */
    private final Set<Do> doing = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The records in {@code file}, each new one timed by {@code clock}. */
    public DoneRecords(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
        this.done = read(file);
    }

    /** The running bot's. */
    public static DoneRecords run() {
        return Run.RECORDS;
    }

    /** The records kept in the SDK's state folder {@code folder}, as Studio reads a bot's; timed by {@code clock}. */
    public static DoneRecords in(Path folder, Clock clock) {
        return new DoneRecords(folder.resolve(FILE), clock);
    }

    /** No records, kept nowhere: what a check that only reads the map plans with. */
    public static DoneRecords none() {
        return new DoneRecords(null, Clock.systemDefaultZone());
    }

    /** The key of the action {@code field} of the screen {@code constant}. */
    public static String key(String constant, String field) {
        return constant + "." + field;
    }

    /** {@code key}'s last time; empty when it was never done. */
    public synchronized Optional<Done> last(String key) {
        return Optional.ofNullable(done.get(key));
    }

    /** Records {@code key} done now, its check having {@code held} or not, and keeps it in the file. */
    public void done(String key, boolean held) {
        done(key, held, clock.instant());
    }

    /** Records {@code key} done {@code at}, its check having {@code held} or not, and keeps it in the file. */
    public synchronized void done(String key, boolean held, Instant at) {
        int fails = held ? 0 : last(key).map(Done::fails).orElse(0) + 1;
        done.put(key, new Done(at, held, fails));
        write();
    }

    /**
     * Records {@code key}, the field holding {@code action}, pressed: it held when its check does not say no; with no
     * check, when the game went {@code asTheMapSays}.
     */
    public void pressed(String key, Do action, boolean asTheMapSays) {
        pressed(key, action, asTheMapSays, clock.instant());
    }

    /** {@link #pressed(String, Do, boolean)}, the press made {@code at}: one proven after it is timed by the press. */
    public void pressed(String key, Do action, boolean asTheMapSays, Instant at) {
        done(key, action.checks() == null ? asTheMapSays : Conditions.holds(action.checks()), at);
    }

    /** Takes {@code action} as being done now, this very value; false when it is already. */
    public synchronized boolean start(Do action) {
        return doing.add(action);
    }

    /** {@code action} is no more being done. */
    public synchronized void finish(Do action) {
        doing.remove(action);
    }

    /** The clock these records are timed by, whose zone a time of day is read in. */
    public Clock clock() {
        return clock;
    }

    private static Map<String, Done> read(Path file) {
        Map<String, Done> read = new LinkedHashMap<>();
        if (file == null || !Files.isRegularFile(file)) return read;
        try {
            JSON.readTree(file.toFile()).properties().forEach(e -> done(e.getValue()).ifPresent(d -> read.put(e.getKey(), d)));
        } catch (IOException unreadable) {
            System.err.println("The done-records in " + file + " cannot be read, and start over: " + unreadable);
        }
        return read;
    }

    /** One record of the file; empty when it is no such thing. */
    private static Optional<Done> done(JsonNode node) {
        try {
            return Optional.of(new Done(Instant.parse(node.path("at").asText()), node.path("held").asBoolean(),
                    node.path("fails").asInt()));
        } catch (DateTimeException unreadable) {
            return Optional.empty();
        }
    }

    private void write() {
        if (file == null) return;
        ObjectNode all = JSON.createObjectNode();
        done.forEach((key, d) -> all.putObject(key).put("at", d.at().toString()).put("held", d.held())
                .put("fails", d.fails()));
        Path written = null;
        try {
            Files.createDirectories(file.getParent());
            written = Files.createTempFile(file.getParent(), FILE, ".tmp");
            JSON.writerWithDefaultPrettyPrinter().writeValue(written.toFile(), all);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            System.err.println("The done-records could not be written to " + file + ": " + unwritable);
            try {
                if (written != null) Files.deleteIfExists(written);
            } catch (IOException stays) {
                System.err.println("Left behind: " + written + ": " + stays);
            }
        }
    }
}
