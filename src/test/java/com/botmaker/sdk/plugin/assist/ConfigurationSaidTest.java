package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.InstanceFacts;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 11: the assistant reads an instance's configuration as the window shows it, and only reads. */
class ConfigurationSaidTest {

    private static final EmulatorInstance FARM = new EmulatorInstance(PlatformId.LDPLAYER, "Farm", "127.0.0.1", 5555);
    private static final EmulatorInstance TEST = new EmulatorInstance(PlatformId.LDPLAYER, "test", "127.0.0.1", 5557);
    private static final EmulatorInstance MUMU = new EmulatorInstance(PlatformId.MUMU, "Farm", "127.0.0.1", 16384);
    private static final ConfigValue.Screen TALL = new ConfigValue.Screen(1080, 1920, 320);
    private static final ConfigValue.Screen WIDE = new ConfigValue.Screen(1600, 900, 240);

    /**
     * LDPlayer wants a tall screen and Sword x Staff; Farm runs wide, holds another account than the one it wants and
     * lacks the game; test is stopped with nothing of its own; MuMu's Farm cannot be read.
     */
    private static final class Pc implements EmulatorsModel.Source {
        int read;

        @Override
        public Platforms.DiscoveryReport discover() {
            return new Platforms.DiscoveryReport(List.of(TEST, MUMU, FARM), List.of());
        }

        @Override
        public HostConfig.State configuration() {
            EmulatorConfig config = EmulatorConfig.EMPTY
                    .withEmulator(PlatformId.LDPLAYER, Layer.EMPTY.with(ConfigField.SCREEN, TALL)
                            .withGame(new GameRecord("com.zjcs.android.us", "Sword x Staff")))
                    .withInstance(InstanceKey.of(FARM), Layer.EMPTY.withTags(List.of("farm"))
                            .withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "User@example.com"))
                            .withAccount(new AccountRecord(AccountRecord.Kind.FACEBOOK, "farm one")));
            return new HostConfig.State(config, Map.of(InstanceKey.of(FARM), List.of("SwordXStaff")));
        }

        @Override
        public boolean running(EmulatorInstance instance) {
            return !instance.equals(TEST);
        }

        @Override
        public InstanceFacts facts(EmulatorInstance instance, InstanceFacts last) {
            read++;
            if (instance.equals(MUMU)) throw new IllegalStateException("its console did not answer");
            Optional<AndroidState> android = instance.equals(FARM) ? Optional.of(new AndroidState(Map.of(),
                    Optional.of(Set.of("other@example.com")), Optional.of(Set.of("com.other.app")), "14", false,
                    Instant.EPOCH)) : Optional.empty();
            return new InstanceFacts(InstanceKey.of(instance), Map.of(ConfigField.SCREEN,
                    instance.equals(FARM) ? WIDE : TALL), Instant.EPOCH, android, android.isPresent());
        }
    }

    private static String said(String reference) {
        return ConfigurationSaid.said(EmulatorsModel.named(new Pc(), reference), reference);
    }

    @Test
    void eachWantedValueIsSaidWithItsLayerAndWhatTheInstanceHas() {
        String said = said("Farm").split("\n\n")[1];
        assertTrue(said.startsWith("\"Farm\" — LDPlayer, running. It differs: Apply, in the Emulators window, would "
                + "change 3."), said);
        assertTrue(said.contains("• " + ConfigField.SCREEN.displayName() + ": " + TALL.said() + ", its emulator's, now "
                + WIDE.said() + " (its settings say) — Apply changes it"), said);
        assertTrue(said.contains("• Google User@example.com (offered first), its own — not signed in"), said);
        assertTrue(said.contains("• Facebook farm one, its own — a record only, nothing signs it in"), said);
        assertTrue(said.contains("Signed in to Google now: other@example.com."), said);
        assertTrue(said.contains("• Sword x Staff (com.zjcs.android.us), its emulator's — not installed"), said);
        assertTrue(said.contains("Tags: farm."), said);
        assertTrue(said.contains("Used by the bots: SwordXStaff."), said);
    }

    @Test
    void aNameTwoEmulatorsHaveSaysBothAndAnUnreadOneSaysSo() {
        String[] both = said("Farm").split("\n\n");
        assertEquals(2, both.length);
        assertTrue(both[0].startsWith("\"Farm\" — " + PlatformId.MUMU.displayName()
                + ". What it has now could not be read."), both[0]);
        assertTrue(both[0].contains("Signed in to Google now: not read."), both[0]);
    }

    @Test
    void aStoppedInstanceIsReadFromItsSettingsAndItsAccountsAreNotRead() {
        String said = said("test");
        assertTrue(said.startsWith("\"test\" — LDPlayer, stopped. It is as configured, as far as it was read."), said);
        assertTrue(said.contains("Signed in to Google now: not read, it is stopped."), said);
        assertTrue(said.contains("its emulator's — not read"), said);
        assertFalse(said.contains("Accounts wanted"), said);
    }

    @Test
    void onlyTheNamedInstanceIsReadAndAnUnknownNameIsSaid() {
        Pc pc = new Pc();
        assertEquals(1, EmulatorsModel.named(pc, "test").size());
        assertEquals(1, pc.read, "the other instances are not connected to");
        assertEquals("No emulator instance is called \"nope\"; list_emulators names them.", said("nope"));
    }
}
