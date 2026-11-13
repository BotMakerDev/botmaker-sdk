package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel.Column;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel.Filter;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel.InstanceRow;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel.Running;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.InstanceFacts;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import com.botmaker.shared.emulator.config.SavedFilters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorsModelTest {

    private static final EmulatorInstance FARM = new EmulatorInstance(PlatformId.LDPLAYER, "Farm", "127.0.0.1", 5555);
    private static final EmulatorInstance TEST = new EmulatorInstance(PlatformId.LDPLAYER, "test", "127.0.0.1", 5557);
    private static final EmulatorInstance MUMU = new EmulatorInstance(PlatformId.MUMU, "MuMu-0", "127.0.0.1", 16384);
    private static final ConfigValue.Screen TALL = new ConfigValue.Screen(1080, 1920, 320);
    private static final ConfigValue.Screen WIDE = new ConfigValue.Screen(1600, 900, 240);

    /**
     * LDPlayer wants a tall screen and Sword x Staff; Farm runs with a wide one, is tagged and used by a bot; test is
     * stopped, as configured, and wants its own account; MuMu has no configuration and cannot be read.
     */
    private static final class Pc implements EmulatorsModel.Source {
        final Set<EmulatorInstance> running = Set.of(FARM, MUMU);

        @Override
        public Platforms.DiscoveryReport discover() {
            return new Platforms.DiscoveryReport(List.of(TEST, MUMU, FARM), List.of(
                    new Platforms.PlatformStatus(PlatformId.LDPLAYER, true, 2, null),
                    new Platforms.PlatformStatus(PlatformId.MUMU, true, 1, null),
                    new Platforms.PlatformStatus(PlatformId.MEMU, false, 0, null)));
        }

        @Override
        public HostConfig.State configuration() {
            EmulatorConfig config = EmulatorConfig.EMPTY
                    .withEmulator(PlatformId.LDPLAYER, Layer.EMPTY.with(ConfigField.SCREEN, TALL)
                            .withGame(new GameRecord("com.zjcs.android.us", "Sword x Staff")))
                    .withInstance(InstanceKey.of(FARM), Layer.EMPTY.withTags(List.of("farm", "main")))
                    .withInstance(InstanceKey.of(TEST), Layer.EMPTY
                            .withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "user@example.com")));
            return new HostConfig.State(config, Map.of(InstanceKey.of(FARM), List.of("SwordXStaff")));
        }

        @Override
        public boolean running(EmulatorInstance instance) {
            return running.contains(instance);
        }

        @Override
        public InstanceFacts facts(EmulatorInstance instance, InstanceFacts last) {
            if (instance.equals(MUMU)) throw new IllegalStateException("its console did not answer");
            return new InstanceFacts(InstanceKey.of(instance),
                    Map.of(ConfigField.SCREEN, instance.equals(FARM) ? WIDE : TALL,
                            ConfigField.MEMORY, new ConfigValue.Megabytes(instance.equals(FARM) ? 4096 : 2048)),
                    Instant.EPOCH, Optional.empty(), false);
        }
    }

    private static EmulatorsModel.Snapshot read() {
        return EmulatorsModel.read(new Pc(), EmulatorsModel.Snapshot.EMPTY, Instant.EPOCH);
    }

    private static List<String> names(List<InstanceRow> rows) {
        return rows.stream().map(row -> row.instance().name()).toList();
    }

    @Test
    void readsEveryProductAndInstance() {
        EmulatorsModel.Snapshot snapshot = read();
        assertEquals(List.of(PlatformId.LDPLAYER, PlatformId.MUMU, PlatformId.MEMU),
                snapshot.emulators().stream().map(EmulatorsModel.EmulatorRow::platform).toList());
        EmulatorsModel.EmulatorRow ld = snapshot.emulators().getFirst();
        assertEquals(2, ld.instances());
        assertEquals(1, ld.running());
        assertEquals(1, ld.differing());
        assertEquals("1 setting, 1 game", ld.configured());
        assertFalse(snapshot.emulators().getLast().installed());
        assertEquals("Nothing set", snapshot.emulators().getLast().configured());
        assertEquals(2, snapshot.running());
        // Android read on neither: each installs the game unless it is there, and test signs its account in
        assertEquals(List.of("test", "Farm"), names(snapshot.applicable(PlatformId.LDPLAYER)));
        assertEquals(List.of("farm", "main"), snapshot.tags());
    }

    @Test
    void anInstanceThatCannotBeReadIsStillARow() {
        InstanceRow mumu = read().instance(InstanceKey.of(MUMU)).orElseThrow();
        assertNull(mumu.facts());
        assertNull(mumu.diff());
        assertFalse(mumu.differs());
        assertEquals("Not read", Column.DIFFERS.text(mumu));
    }

    @Test
    void columnsSayWhatARowHas() {
        InstanceRow farm = read().instance(InstanceKey.of(FARM)).orElseThrow();
        assertEquals("Farm", Column.NAME.text(farm));
        assertEquals("LDPlayer", Column.EMULATOR.text(farm));
        assertEquals("Running", Column.STATE.text(farm));
        assertEquals("2 changes to apply", Column.DIFFERS.text(farm), "the screen, and the game as Android was not read");
        assertEquals("Sword x Staff", Column.GAMES.text(farm));
        assertEquals("farm, main", Column.TAGS.text(farm));
        assertEquals("SwordXStaff", Column.USED_BY.text(farm));
        assertEquals(WIDE.said(), Column.SCREEN.text(farm));
        InstanceRow test = read().instance(InstanceKey.of(TEST)).orElseThrow();
        assertEquals("Stopped", Column.STATE.text(test));
        assertEquals("2 changes to apply", Column.DIFFERS.text(test), "stopped: its account and game, unread");
        assertFalse(test.differs(), "not read: not known to differ");
        assertEquals("user@example.com", Column.ACCOUNTS.text(test));
        for (Column column : Column.values()) assertEquals(column, Column.fromId(column.id()));
        assertEquals(Column.UNKNOWN, Column.fromId("nope"));
        assertFalse(Column.offered().contains(Column.UNKNOWN));
    }

    @Test
    void sortsByAnyColumnBothWays() {
        List<InstanceRow> rows = read().instances();
        assertEquals(List.of("Farm", "MuMu-0", "test"), names(EmulatorsModel.sorted(rows, Column.NAME, true)));
        assertEquals(List.of("test", "MuMu-0", "Farm"), names(EmulatorsModel.sorted(rows, Column.NAME, false)));
        assertEquals(List.of("Farm", "test", "MuMu-0"), names(EmulatorsModel.sorted(rows, Column.EMULATOR, true)));
        assertEquals(List.of("Farm", "MuMu-0", "test"), names(EmulatorsModel.sorted(rows, Column.STATE, true)),
                "running first, then by emulator and name");
        assertEquals("Farm", names(EmulatorsModel.sorted(rows, Column.DIFFERS, true)).getFirst(),
                "the most changes first");
        assertEquals(List.of("MuMu-0", "test", "Farm"), names(EmulatorsModel.sorted(rows, Column.MEMORY, true)),
                "as a number: unread, 2 GB, 4 GB");
    }

    @Test
    void everyFilterNarrows() {
        List<InstanceRow> rows = read().instances();
        assertEquals(3, EmulatorsModel.filtered(rows, Filter.NONE).size());
        assertEquals(List.of("Farm"), names(EmulatorsModel.filtered(rows, filter("text", "FAR"))));
        assertEquals(List.of("MuMu-0"), names(EmulatorsModel.filtered(rows, filter("emulator", "mumu"))));
        assertEquals(List.of("MuMu-0", "Farm"), names(EmulatorsModel.filtered(rows, filter("state", "running"))));
        assertEquals(List.of("test"), names(EmulatorsModel.filtered(rows, filter("state", "stopped"))));
        assertEquals(List.of("test"), names(EmulatorsModel.filtered(rows, filter("account", "USER@"))));
        assertEquals(List.of("test", "Farm"), names(EmulatorsModel.filtered(rows, filter("app", "sword"))));
        assertEquals(List.of("test", "Farm"), names(EmulatorsModel.filtered(rows, filter("app", "zjcs"))),
                "by package too");
        assertEquals(List.of("Farm"), names(EmulatorsModel.filtered(rows, filter("tag", "FARM"))));
        assertEquals(List.of(), names(EmulatorsModel.filtered(rows, filter("tag", "far"))), "a tag is whole");
        assertEquals(List.of("Farm"), names(EmulatorsModel.filtered(rows, filter("differs", "true"))));
        assertEquals(List.of(), names(EmulatorsModel.filtered(rows,
                Filter.of(Map.of("tag", "farm", "state", "stopped")))), "the parts narrow together");
    }

    private static Filter filter(String key, String value) {
        return Filter.of(Map.of(key, value));
    }

    @Test
    void aFilterRoundTripsThroughASavedFilter(@TempDir Path dir) throws Exception {
        Filter filter = new Filter(" far ", PlatformId.LDPLAYER, Running.RUNNING, "user", "sword", "farm", true);
        assertEquals("far", filter.text());
        SavedFilters store = new SavedFilters(dir);
        store.save(filter.saved("Farm needing work"));
        SavedFilters.SavedFilter saved = store.load().getFirst();
        assertEquals(SavedFilters.Tab.INSTANCES, saved.tab());
        assertEquals(filter, Filter.of(saved.values()));
        assertEquals(Map.of(), Filter.NONE.values());
        assertEquals(Filter.NONE, Filter.of(Map.of("emulator", "a-newer-one", "state", "nope", "later", "x")),
                "what this build does not know narrows nothing");
    }

    @Test
    void tagsAreTypedWithCommas() {
        assertEquals(List.of("farm", "Main"), EmulatorsModel.tags(" farm, Main ,, FARM,main"));
        assertEquals(List.of(), EmulatorsModel.tags(null));
        assertTrue(EmulatorsModel.tags(" , ").isEmpty());
    }

    @Test
    void applyShowsEveryChangeAndAsksForRemovals() {
        List<ApplyPlan.Item> items = ApplyPlan.of(read().instances());
        assertEquals(List.of("test", "Farm"), items.stream().map(i -> i.row().instance().name()).toList());
        ApplyPlan.Item farm = items.getLast();
        assertEquals(List.of("Screen: " + WIDE.said() + " → " + TALL.said() + " (restarts it)",
                "Install Sword x Staff"), ApplyPlan.lines(farm));
        assertFalse(farm.removes());
        assertEquals(items, ApplyPlan.agreed(items, false));
    }
}
