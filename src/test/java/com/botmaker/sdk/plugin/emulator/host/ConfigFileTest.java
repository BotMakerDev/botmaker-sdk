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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileTest {

    private static final AccountRecord MAIN = new AccountRecord(AccountRecord.Kind.GOOGLE, "main@example.com", "main");
    private static final AccountRecord FARM = new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com");
    private static final InstanceKey LD = new InstanceKey(PlatformId.LDPLAYER, "LDPlayer-1");
    private static final InstanceKey MUMU = new InstanceKey(PlatformId.MUMU, "MuMu-0");

    private static final EmulatorConfig CONFIG = EmulatorConfig.EMPTY
            .withEmulator(PlatformId.LDPLAYER, Layer.EMPTY
                    .with(ConfigField.SCREEN, new ConfigValue.Screen(1080, 1920, 320))
                    .with(ConfigField.MEMORY, new ConfigValue.Megabytes(4096))
                    .withAccount(MAIN)
                    .withGame(new GameRecord("com.example.game", "A game")))
            .withInstance(LD, Layer.EMPTY.removingAccount(MAIN.key()).withAccount(FARM)
                    .withTags(List.of("farm", "test")).removingGame("com.example.game"))
            .withInstance(MUMU, Layer.EMPTY.with(ConfigField.CPU_CORES, new ConfigValue.Count(2)));

    @Test
    void anExportReadsBackAsTheSameLayers(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("emulators" + ConfigFile.EXTENSION);
        ConfigFile.write(CONFIG, file);
        ConfigFile.Read read = ConfigFile.read(file);

        assertEquals(0, read.unread());
        assertEquals(Map.of(PlatformId.LDPLAYER, CONFIG.emulator(PlatformId.LDPLAYER)), read.emulators());
        assertEquals(Map.of(LD, CONFIG.instance(LD), MUMU, CONFIG.instance(MUMU)), read.instances());
        assertEquals(CONFIG.wanted(LD), read.over(EmulatorConfig.EMPTY).wanted(LD));
    }

    @Test
    void theFileHoldsNoPasswordNoAndroidStateOnlyTheLayersKeys(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("emulators" + ConfigFile.EXTENSION);
        ConfigFile.write(CONFIG, file);
        String text = Files.readString(file);
        assertFalse(text.toLowerCase().contains("password"));
        ObjectNode account = (ObjectNode) new ObjectMapper().readTree(text)
                .path("emulators").path("ldplayer").path("accounts").get(0);
        assertEquals(List.of("kind", "name", "note"), List.copyOf(account.properties().stream()
                .map(Map.Entry::getKey).toList()));
    }

    @Test
    void anImportReplacesOnlyTheLayersTheFileHoldsAndSaysWhatItLeftOut() throws IOException {
        ObjectNode root = ConfigFile.json(EmulatorConfig.EMPTY.withInstance(MUMU, Layer.EMPTY.withTags(List.of("new"))));
        ((ObjectNode) root.path("instances")).putObject("NoSuchEmulator/one");
        ((ObjectNode) root.path("instances")).putNull("ldplayer/LDPlayer-1");
        ((ObjectNode) root.path("instances")).putObject("ldplayer/elsewhere").putArray("tags").add("x");
        ((ObjectNode) root.path("instances").path(MUMU.text()).path("settings")).put("cpu_cores_typo", "3");
        ConfigFile.Read read = ConfigFile.read(root).on(Set.of(LD, MUMU));

        EmulatorConfig after = read.over(CONFIG);
        assertEquals(List.of("new"), after.instance(MUMU).tags());
        assertTrue(after.instance(MUMU).fields().isEmpty(), "the file's layer replaces MuMu's own");
        assertEquals(CONFIG.instance(LD), after.instance(LD), "a null layer is not read as an empty one");
        assertEquals(CONFIG.emulator(PlatformId.LDPLAYER), after.emulator(PlatformId.LDPLAYER));
        assertFalse(after.instances().containsKey(new InstanceKey(PlatformId.LDPLAYER, "elsewhere")),
                "an instance this PC lacks gets no layer");
        assertEquals(3, read.unread());
        assertEquals("0 emulators and 1 instance imported from f.json; 3 not understood, left out.", read.said("f.json"));
    }

    @Test
    void aFileOfAnotherShapeIsRefused() {
        assertThrows(IOException.class, () -> ConfigFile.read(new ObjectMapper().createObjectNode().put("format", 9)));
    }
}
