package com.botmaker.sdk.internal.state;

import com.botmaker.plugin.api.PluginState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The game facts of one launch target, or of one account on it: {@code targets/<target>/game.json}, or {@code
 * targets/<target>/<account>/game.json}, in the SDK's state folder of the bot ({@link PluginState#folder}). One part of
 * the SDK's state; other parts sit beside {@code targets/}. Studio's side finds the same folder through {@code
 * StudioServices.stateDir()}.
 *
 * <p>A cache of what the game showed: no format version, and an entry that does not read is dropped.
 */
public final class StateFile {

    /** The SDK's coordinate, which names its state folder. */
    public static final String GROUP = "com.github.BotMakerDev";
    public static final String ARTIFACT = "botmaker-sdk";
    /** The folder of each launch target's state, inside the SDK's. */
    public static final String TARGETS = "targets";
    /** The facts' file, inside a target's or an account's folder. */
    public static final String GAME = "game.json";
    /** The target folder of a run with no launch target. */
    public static final String NO_TARGET = "none";

    private static final ObjectMapper JSON = new ObjectMapper();

    private StateFile() {}

    /**
     * One fact's reading.
     *
     * @param value   what was read, as JSON: a number, a string or a boolean
     * @param at      when, epoch milliseconds
     * @param screen  the screen constant it was read on; null when a step set it
     * @param readout the readout field it was read from; null when a step set it
     * @param run     the run that read it, its folder's name; null when unknown
     */
    public record Entry(JsonNode value, long at, String screen, String readout, String run) {}

    /** The SDK's state folder of the bot at {@code botDir}. */
    public static Path folder(Path botDir) {
        return PluginState.folder(botDir, GROUP, ARTIFACT);
    }

    /**
     * The facts' file in {@code sdkState}, the SDK's state folder: {@code target}'s ({@code null} for none), or
     * {@code account}'s on it when one is given. Both are named as a folder can hold them ({@link PluginState#part}).
     */
    public static Path game(Path sdkState, String target, String account) {
        Path folder = sdkState.resolve(TARGETS).resolve(target == null || target.isBlank() ? NO_TARGET
                : PluginState.part(target));
        if (account != null && !account.isBlank()) folder = folder.resolve(PluginState.part(account));
        return folder.resolve(GAME);
    }

    /** The entries of {@code file}, by fact name: none when there is no file; empty when it does not read. */
    public static Optional<Map<String, Entry>> read(Path file) {
        Map<String, Entry> out = new LinkedHashMap<>();
        JsonNode root;
        try {
            root = JSON.readTree(Files.readString(file));
        } catch (NoSuchFileException none) {
            return Optional.of(out);
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
        if (root == null || root.isMissingNode()) return Optional.of(out);
        if (!root.isObject()) return Optional.empty();
        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            entry(field.getValue()).ifPresent(e -> out.put(field.getKey(), e));
        }
        return Optional.of(out);
    }

    private static Optional<Entry> entry(JsonNode node) {
        JsonNode value = node.get("value");
        if (value == null || !(value.isNumber() || value.isTextual() || value.isBoolean())) return Optional.empty();
        try {
            long at = Instant.parse(node.path("at").asText()).toEpochMilli();
            return Optional.of(new Entry(value, at, text(node, "screen"), text(node, "readout"), text(node, "run")));
        } catch (RuntimeException badTime) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Writes {@code entries} to {@code file}, replacing it whole: a reader sees the old file or the new one. */
    public static void write(Path file, Map<String, Entry> entries) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        entries.forEach((name, e) -> {
            ObjectNode node = root.putObject(name);
            node.set("value", e.value());
            node.put("at", Instant.ofEpochMilli(e.at()).toString());
            if (e.screen() != null) node.put("screen", e.screen());
            if (e.readout() != null) node.put("readout", e.readout());
            if (e.run() != null) node.put("run", e.run());
        });
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException noAtomic) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
