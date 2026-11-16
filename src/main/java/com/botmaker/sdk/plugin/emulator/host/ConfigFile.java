// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Configurations tab's <i>Export…</i> and <i>Import…</i> (2026-11-14): this PC's configuration as a file for another
 * PC, each emulator's layer and each instance's own, with their settings, games, tags and listed accounts (kind, name,
 * note). No password, no Android state and no device id is in it: a layer holds none.
 */
public final class ConfigFile {

    /** The extension the file dialogs offer. */
    public static final String EXTENSION = ".botmaker-emulators.json";

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final int FORMAT = 1;

    private ConfigFile() {}

    /** What an import read: the layers of the file, and what it could not read. */
    public record Read(Map<PlatformId, Layer> emulators, Map<InstanceKey, Layer> instances, int unread) {

        public boolean isEmpty() {
            return emulators.isEmpty() && instances.isEmpty();
        }

        /**
         * The file as this PC takes it: an instance's layer only for an instance of that name {@code here}, so no
         * layer is kept for an instance this PC lacks.
         */
        public Read on(Set<InstanceKey> here) {
            Map<InstanceKey, Layer> kept = new LinkedHashMap<>(instances);
            kept.keySet().retainAll(here);
            return new Read(emulators, Map.copyOf(kept), unread);
        }

        /** {@code config} with each layer of the file in place of its own; the layers the file lacks stay. */
        public EmulatorConfig over(EmulatorConfig config) {
            EmulatorConfig out = config;
            for (Map.Entry<PlatformId, Layer> one : emulators.entrySet()) out = out.withEmulator(one.getKey(), one.getValue());
            for (Map.Entry<InstanceKey, Layer> one : instances.entrySet()) out = out.withInstance(one.getKey(), one.getValue());
            return out;
        }

        /** "1 emulator and 2 instances". */
        public String holds() {
            return count(emulators.size(), "emulator") + " and " + count(instances.size(), "instance");
        }

        public String said(String file) {
            return holds() + " imported from " + file + (unread == 0 ? "." : "; " + unread + " not understood, left out.");
        }

        private static String count(int n, String thing) {
            return n + " " + thing + (n == 1 ? "" : "s");
        }
    }

    public static void write(EmulatorConfig config, Path target) throws IOException {
        Files.writeString(target, JSON.writeValueAsString(json(config)));
    }

    public static Read read(Path source) throws IOException {
        return read(JSON.readTree(Files.readString(source)));
    }

    static ObjectNode json(EmulatorConfig config) {
        ObjectNode root = JSON.createObjectNode();
        root.put("format", FORMAT);
        ObjectNode emulators = root.putObject("emulators");
        config.emulators().forEach((platform, layer) -> {
            if (!layer.isEmpty()) emulators.set(platform.id(), layer(layer));
        });
        ObjectNode instances = root.putObject("instances");
        config.instances().forEach((key, layer) -> {
            if (!layer.isEmpty()) instances.set(key.text(), layer(layer));
        });
        return root;
    }

    static Read read(JsonNode root) throws IOException {
        if (root == null || !root.isObject() || root.path("format").asInt() != FORMAT) {
            throw new IOException("not an emulators configuration BotMaker exported");
        }
        int[] unread = {0};
        Map<PlatformId, Layer> emulators = new LinkedHashMap<>();
        // a layer that is not an object is not understood: it never replaces this PC's with an empty one
        root.path("emulators").properties().forEach(one -> {
            PlatformId platform = PlatformId.fromId(one.getKey());
            if (platform == PlatformId.UNKNOWN || !one.getValue().isObject()) unread[0]++;
            else emulators.put(platform, layer(one.getValue(), unread));
        });
        Map<InstanceKey, Layer> instances = new LinkedHashMap<>();
        root.path("instances").properties().forEach(one -> {
            InstanceKey key = InstanceKey.parse(one.getKey());
            if (key == null || !one.getValue().isObject()) unread[0]++;
            else instances.put(key, layer(one.getValue(), unread));
        });
        return new Read(Map.copyOf(emulators), Map.copyOf(instances), unread[0]);
    }

    private static ObjectNode layer(Layer layer) {
        ObjectNode out = JSON.createObjectNode();
        ObjectNode settings = out.putObject("settings");
        layer.fields().forEach((field, value) -> settings.put(field.id(), value.written()));
        ArrayNode accounts = out.putArray("accounts");
        for (AccountRecord account : layer.accounts()) {
            accounts.addObject().put("kind", account.kind().id()).put("name", account.name()).put("note", account.note());
        }
        ArrayNode removed = out.putArray("removedAccounts");
        for (AccountRecord.Key key : layer.removedAccounts()) removed.addObject().put("kind", key.kind().id()).put("name", key.name());
        if (layer.defaultGoogle() != null) out.put("defaultGoogle", layer.defaultGoogle());
        ArrayNode games = out.putArray("games");
        for (GameRecord game : layer.games()) games.addObject().put("package", game.packageName()).put("label", game.label());
        ArrayNode removedGames = out.putArray("removedGames");
        layer.removedGames().forEach(removedGames::add);
        ArrayNode tags = out.putArray("tags");
        layer.tags().forEach(tags::add);
        return out;
    }

    private static Layer layer(JsonNode json, int[] unread) {
        Map<ConfigField, ConfigValue> fields = new EnumMap<>(ConfigField.class);
        json.path("settings").properties().forEach(one -> {
            ConfigField field = ConfigField.fromId(one.getKey());
            ConfigValue value = field == ConfigField.UNKNOWN ? null : ConfigValue.read(field.kind(), one.getValue().asText());
            if (value == null) unread[0]++;
            else fields.put(field, value);
        });
        List<AccountRecord> accounts = new ArrayList<>();
        for (JsonNode one : json.path("accounts")) {
            AccountRecord.Kind kind = AccountRecord.Kind.fromId(one.path("kind").asText());
            String name = one.path("name").asText("").strip();
            if (kind == AccountRecord.Kind.UNKNOWN || name.isEmpty()) unread[0]++;
            else accounts.add(new AccountRecord(kind, name, one.path("note").asText("")));
        }
        Set<AccountRecord.Key> removedAccounts = new LinkedHashSet<>();
        for (JsonNode one : json.path("removedAccounts")) {
            AccountRecord.Kind kind = AccountRecord.Kind.fromId(one.path("kind").asText());
            String name = one.path("name").asText("").strip();
            if (kind == AccountRecord.Kind.UNKNOWN || name.isEmpty()) unread[0]++;
            else removedAccounts.add(new AccountRecord.Key(kind, name));
        }
        List<GameRecord> games = new ArrayList<>();
        for (JsonNode one : json.path("games")) {
            String packageName = one.path("package").asText("").strip();
            if (packageName.isEmpty()) unread[0]++;
            else games.add(new GameRecord(packageName, one.path("label").asText("")));
        }
        Set<String> removedGames = new LinkedHashSet<>();
        for (JsonNode one : json.path("removedGames")) removedGames.add(one.asText());
        List<String> tags = new ArrayList<>();
        for (JsonNode one : json.path("tags")) tags.add(one.asText());
        String defaultGoogle = json.path("defaultGoogle").isTextual() ? json.path("defaultGoogle").asText() : null;
        return new Layer(fields, accounts, removedAccounts, defaultGoogle, games, removedGames, tags);
    }
}
