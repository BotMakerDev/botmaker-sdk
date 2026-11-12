package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.AccountRow;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.AccountState;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.GameRow;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.GameState;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.Grid;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.GridFilter;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.GridRow;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.GridSection;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.SettingRow;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.Target;
import com.botmaker.sdk.plugin.emulator.window.ConfigViews.ViewFilter;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.ConfigDiff;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigViewsTest {

    private static final EmulatorInstance FARM = new EmulatorInstance(PlatformId.LDPLAYER, "Farm", "127.0.0.1", 5555);
    private static final EmulatorInstance ALT = new EmulatorInstance(PlatformId.LDPLAYER, "Alt", "127.0.0.1", 5557);
    private static final InstanceKey FARM_KEY = InstanceKey.of(FARM);
    private static final InstanceKey ALT_KEY = InstanceKey.of(ALT);
    private static final InstanceKey GONE_KEY = new InstanceKey(PlatformId.MUMU, "MuMu-3");
    private static final Target LD = new Target.Emulator(PlatformId.LDPLAYER);
    private static final Target FARM_T = new Target.Instance(FARM_KEY);
    private static final ConfigValue.Screen TALL = new ConfigValue.Screen(1080, 1920, 320);
    private static final ConfigValue.Screen WIDE = new ConfigValue.Screen(1600, 900, 240);
    private static final AccountRecord PC = new AccountRecord(AccountRecord.Kind.GOOGLE, "pc@example.com");
    private static final AccountRecord SECOND = new AccountRecord(AccountRecord.Kind.GOOGLE, "second@example.com");
    private static final AccountRecord FB = new AccountRecord(AccountRecord.Kind.FACEBOOK, "Name", "alt for server 12");
    private static final GameRecord SXS = new GameRecord("com.zjcs.android.us", "Sword x Staff");
    private static final GameRecord OTHER = new GameRecord("com.other.game", "Other");

    /**
     * LDPlayer wants a tall screen, 30 FPS, both Google accounts and Sword x Staff; Farm sets 4 GB and 15 FPS of its
     * own, removes the second account and the game, adds Facebook, and is tagged; MuMu-3 is only in the configuration.
     */
    private static EmulatorConfig config() {
        return EmulatorConfig.EMPTY
                .withEmulator(PlatformId.LDPLAYER, Layer.EMPTY.with(ConfigField.SCREEN, TALL)
                        .with(ConfigField.FPS, new ConfigValue.Count(30))
                        .with(ConfigField.PRODUCT_AUTO_UPDATE_OFF, new ConfigValue.Toggle(true))
                        .withAccount(PC).withAccount(SECOND).withGame(SXS))
                .withInstance(FARM_KEY, Layer.EMPTY.with(ConfigField.MEMORY, new ConfigValue.Megabytes(4096))
                        .with(ConfigField.FPS, new ConfigValue.Count(15))
                        .removingAccount(SECOND.key()).withAccount(FB).removingGame(SXS.packageName())
                        .withTags(List.of("farm")))
                .withInstance(GONE_KEY, Layer.EMPTY.withTags(List.of("test")));
    }

    /** Farm runs with a wide screen, holds both Google accounts and has the game; Alt is stopped and was never read. */
    private static final class Pc implements EmulatorsModel.Source {
        @Override
        public Platforms.DiscoveryReport discover() {
            return new Platforms.DiscoveryReport(List.of(FARM, ALT), List.of(
                    new Platforms.PlatformStatus(PlatformId.LDPLAYER, true, 2, null),
                    new Platforms.PlatformStatus(PlatformId.MEMU, false, 0, null)));
        }

        @Override
        public HostConfig.State configuration() {
            return new HostConfig.State(config(), Map.of());
        }

        @Override
        public boolean running(EmulatorInstance instance) {
            return instance.equals(FARM);
        }

        @Override
        public InstanceFacts facts(EmulatorInstance instance, InstanceFacts last) {
            if (instance.equals(ALT)) throw new IllegalStateException("not read");
            AndroidState android = new AndroidState(Map.of(), Optional.of(Set.of("pc@example.com", "second@example.com")),
                    Optional.of(Set.of(SXS.packageName())), "14", false, Instant.EPOCH);
            return new InstanceFacts(FARM_KEY, Map.of(ConfigField.SCREEN, WIDE, ConfigField.MEMORY,
                    new ConfigValue.Megabytes(4096), ConfigField.FPS, new ConfigValue.Count(60)), Instant.EPOCH,
                    Optional.of(android), false);
        }
    }

    private static EmulatorsModel.Snapshot read() {
        return EmulatorsModel.read(new Pc(), EmulatorsModel.Snapshot.EMPTY, Instant.EPOCH);
    }

    private static EmulatorsModel.InstanceRow farm() {
        return read().instance(FARM_KEY).orElseThrow();
    }

    private static SettingRow setting(List<SettingRow> rows, ConfigField field) {
        return rows.stream().filter(r -> r.field() == field).findFirst().orElseThrow();
    }

    // ---- an instance's view ----

    @Test
    void anInstancesSettingsSayWhereEachComesFromAndWhatItHas() {
        List<SettingRow> rows = ConfigViews.settings(FARM_T, config(), farm());
        SettingRow screen = setting(rows, ConfigField.SCREEN);
        assertEquals(Optional.of(TALL), screen.wanted());
        assertFalse(screen.own());
        assertEquals("LDPlayer (emulator)", screen.fromText(FARM_T));
        assertEquals(Optional.of(WIDE), screen.now());
        assertTrue(screen.differs());

        SettingRow memory = setting(rows, ConfigField.MEMORY);
        assertTrue(memory.own());
        assertEquals("this instance", memory.fromText(FARM_T));
        assertEquals(ConfigDiff.Status.SAME, memory.status());
        assertFalse(memory.differs());

        SettingRow root = setting(rows, ConfigField.ROOT);
        assertEquals("not set", root.fromText(FARM_T));
        assertNull(root.status());
        assertEquals("—", root.wantedText());
        assertTrue(rows.stream().noneMatch(r -> r.field().emulatorOnly()), "an instance's view lists no emulator-only field");
    }

    @Test
    void onlyDifferencesAndTheFieldFilterNarrowTheRows() {
        List<SettingRow> rows = ConfigViews.settings(FARM_T, config(), farm());
        List<ConfigField> differing = rows.stream().filter(new ViewFilter("", true)::keeps).map(SettingRow::field).toList();
        assertEquals(List.of(ConfigField.SCREEN, ConfigField.FPS), differing);
        assertEquals(List.of(ConfigField.MEMORY), rows.stream().filter(new ViewFilter("mem", false)::keeps)
                .map(SettingRow::field).toList());
    }

    @Test
    void anEmulatorsViewListsItsOwnFieldsOnly() {
        List<SettingRow> rows = ConfigViews.settings(LD, config(), null);
        assertTrue(setting(rows, ConfigField.PRODUCT_AUTO_UPDATE_OFF).own());
        assertEquals("this emulator", setting(rows, ConfigField.FPS).fromText(LD));
        assertFalse(setting(rows, ConfigField.MEMORY).own());
        assertTrue(rows.stream().allMatch(r -> r.status() == null && r.now().isEmpty()));
    }

    @Test
    void accountsShowTheDefaultTheRecordsAndARemovalToApply() {
        List<AccountRow> rows = ConfigViews.accounts(FARM_T, config(), farm());
        assertEquals(3, rows.size());
        AccountRow pc = rows.get(0);
        assertTrue(pc.isDefault());
        assertFalse(pc.own());
        assertEquals(AccountState.HELD, pc.state());
        AccountRow fb = rows.get(1);
        assertEquals(AccountState.RECORD, fb.state());
        assertTrue(fb.own());
        AccountRow second = rows.get(2);
        assertTrue(second.removal());
        assertEquals(AccountState.TO_REMOVE, second.state());
        assertTrue(second.differs());
    }

    @Test
    void anAccountNotReadSaysSoAndOneMissingIsToSignIn() {
        EmulatorsModel.InstanceRow alt = read().instance(ALT_KEY).orElseThrow();
        assertEquals(AccountState.UNREAD, ConfigViews.accounts(new Target.Instance(ALT_KEY), config(), alt)
                .getFirst().state());
        EmulatorConfig more = ConfigViews.addAccount(FARM_T, config(),
                new AccountRecord(AccountRecord.Kind.GOOGLE, "third@example.com"));
        AccountRow third = ConfigViews.accounts(FARM_T, more, farm()).stream()
                .filter(r -> r.account().name().equals("third@example.com")).findFirst().orElseThrow();
        assertEquals(AccountState.TO_SIGN_IN, third.state());
    }

    @Test
    void gamesSayWhatIsInstalledAndWhatTheInstanceRemoved() {
        List<GameRow> rows = ConfigViews.games(FARM_T, config(), farm());
        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().removal());
        assertEquals(GameState.REMOVED, rows.getFirst().state());
        EmulatorConfig kept = ConfigViews.keepGame(FARM_T, config(), SXS.packageName());
        GameRow back = ConfigViews.games(FARM_T, kept, farm()).getFirst();
        assertEquals(GameState.INSTALLED, back.state());
        assertFalse(back.own());
        EmulatorConfig other = ConfigViews.addGame(FARM_T, config(), OTHER);
        assertEquals(GameState.MISSING, ConfigViews.games(FARM_T, other, farm()).getFirst().state());
    }

    // ---- a view's changes ----

    @Test
    void setHereAndUseEmulatorsMoveAFieldBetweenLayers() {
        EmulatorConfig set = ConfigViews.set(FARM_T, config(), ConfigField.SCREEN, WIDE);
        assertEquals(WIDE, set.instance(FARM_KEY).fields().get(ConfigField.SCREEN));
        assertEquals(TALL, set.emulator(PlatformId.LDPLAYER).fields().get(ConfigField.SCREEN));
        EmulatorConfig back = ConfigViews.clear(FARM_T, set, ConfigField.SCREEN);
        assertEquals(config(), back);
        EmulatorConfig cleared = ConfigViews.clear(LD, config(), ConfigField.FPS);
        assertFalse(cleared.emulator(PlatformId.LDPLAYER).fields().containsKey(ConfigField.FPS));
    }

    @Test
    void removingAnInheritedAccountMarksItAndKeepTakesItBack() {
        EmulatorConfig removed = ConfigViews.removeAccount(FARM_T, config(), PC.key());
        assertTrue(removed.instance(FARM_KEY).removedAccounts().contains(PC.key()));
        EmulatorConfig kept = ConfigViews.keepAccount(FARM_T, removed, PC.key());
        assertFalse(kept.instance(FARM_KEY).removedAccounts().contains(PC.key()));
        // an account only the instance listed is dropped, not marked removed
        EmulatorConfig noFacebook = ConfigViews.removeAccount(FARM_T, config(), FB.key());
        assertTrue(noFacebook.instance(FARM_KEY).accounts().isEmpty());
        assertFalse(noFacebook.instance(FARM_KEY).removedAccounts().contains(FB.key()));
        EmulatorConfig first = ConfigViews.makeDefault(LD, config(), SECOND.name());
        assertEquals(SECOND.name(), first.emulator(PlatformId.LDPLAYER).defaultGoogle());
    }

    @Test
    void aValueIsTypedAsShownOrAsWritten() {
        assertEquals(Optional.of(TALL), ConfigViews.typed(ConfigField.Kind.SCREEN, "1080x1920@320"));
        assertEquals(Optional.of(TALL), ConfigViews.typed(ConfigField.Kind.SCREEN, "1080×1920, 320 dpi"));
        assertEquals(Optional.of(new ConfigValue.Megabytes(4096)), ConfigViews.typed(ConfigField.Kind.MEGABYTES, "4 GB"));
        assertEquals(Optional.of(new ConfigValue.Megabytes(3072)), ConfigViews.typed(ConfigField.Kind.MEGABYTES, "3072"));
        assertEquals(Optional.of(new ConfigValue.Toggle(false)), ConfigViews.typed(ConfigField.Kind.TOGGLE, "Off"));
        assertEquals(Optional.empty(), ConfigViews.typed(ConfigField.Kind.COUNT, "0"));
        assertEquals(Optional.empty(), ConfigViews.typed(ConfigField.Kind.SCREEN, "big"));
        assertEquals(Optional.empty(), ConfigViews.typed(ConfigField.Kind.TEXT, " "));
    }

    // ---- the grid ----

    @Test
    void theGridListsEachEmulatorThenItsInstancesAndOnesOnlyConfigured() {
        Grid grid = ConfigViews.grid(read(), GridFilter.NONE);
        assertEquals(List.of("LDPlayer (emulator)", "Alt", "Farm", "MuMu Player (emulator)", "MuMu-3 (not found)"),
                grid.columns().stream().map(ConfigViews.GridColumn::title).toList());
        GridRow fps = row(grid, "FPS");
        assertEquals("30", fps.cells().get(0).text());
        assertEquals("inherits", fps.cells().get(1).text());
        assertTrue(fps.cells().get(1).placeholder());
        assertFalse(fps.cells().get(0).placeholder());
        assertEquals(new ConfigViews.Cell("15", true), fps.cells().get(2));
        assertEquals("—", fps.cells().get(3).text());
        GridRow google = row(grid, "Google");
        assertEquals("pc@example.com, second@example.com", google.cells().get(0).text());
        assertEquals(new ConfigViews.Cell("− second@example.com", true), google.cells().get(2));
        assertEquals("− Sword x Staff", row(grid, "Games").cells().get(2).text());
        assertEquals("farm", row(grid, "Tags").cells().get(2).text());
    }

    @Test
    void theGridsFiltersPickRowsAndColumns() {
        EmulatorsModel.Snapshot snapshot = read();
        Grid overridden = ConfigViews.grid(snapshot, new GridFilter("", null, null, "", true));
        assertEquals(List.of("Memory", "FPS", "Google", "Facebook", "Games", "Tags"),
                overridden.rows().stream().map(GridRow::name).toList());
        Grid accounts = ConfigViews.grid(snapshot, new GridFilter("", GridSection.ACCOUNTS, null, "", false));
        assertEquals(List.of("Google", "Facebook", "Mail"), accounts.rows().stream().map(GridRow::name).toList());
        Grid ld = ConfigViews.grid(snapshot, new GridFilter("", null, PlatformId.LDPLAYER, "farm", false));
        assertEquals(List.of("LDPlayer (emulator)", "Farm"),
                ld.columns().stream().map(ConfigViews.GridColumn::title).toList());
        Grid text = ConfigViews.grid(snapshot, new GridFilter("1080", null, null, "", false));
        assertEquals(List.of("Screen"), text.rows().stream().map(GridRow::name).toList());
    }

    private static GridRow row(Grid grid, String name) {
        return grid.rows().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    // ---- compare and copy ----

    @Test
    void compareSaysOnlyWhereTwoInstancesDiffer() {
        List<ConfigViews.Difference> found = ConfigViews.compare(config(), FARM_KEY, ALT_KEY);
        assertEquals(List.of("Memory", "FPS", "Google accounts", "Facebook accounts", "Games", "Tags"),
                found.stream().map(ConfigViews.Difference::what).toList());
        ConfigViews.Difference fps = found.get(1);
        assertEquals("15", fps.first());
        assertEquals("30", fps.second());
        assertTrue(ConfigViews.compare(config(), ALT_KEY, ALT_KEY).isEmpty());
    }

    @Test
    void copyingAColumnReplacesTheLayerAndKeepsTheInstancesTags() {
        Target alt = new Target.Instance(ALT_KEY);
        EmulatorConfig withTag = config().withInstance(ALT_KEY, Layer.EMPTY.withTags(List.of("alt")));
        EmulatorConfig copied = ConfigViews.copy(withTag, FARM_T, alt);
        assertEquals(List.of("alt"), copied.instance(ALT_KEY).tags());
        assertEquals(config().instance(FARM_KEY).fields(), copied.instance(ALT_KEY).fields());
        assertTrue(ConfigViews.compare(copied, FARM_KEY, ALT_KEY).stream().allMatch(d -> d.what().equals("Tags")));

        // an emulator's copy onto an instance leaves out what only an emulator sets
        EmulatorConfig fromEmulator = ConfigViews.copy(config(), LD, alt);
        assertFalse(fromEmulator.instance(ALT_KEY).fields().containsKey(ConfigField.PRODUCT_AUTO_UPDATE_OFF));
        // an instance's copy onto an emulator carries what it should be in full, without its tags and removals
        EmulatorConfig toMuMu = ConfigViews.copy(config(), FARM_T, new Target.Emulator(PlatformId.MUMU));
        Layer mumu = toMuMu.emulator(PlatformId.MUMU);
        assertTrue(mumu.tags().isEmpty());
        assertTrue(mumu.removedAccounts().isEmpty());
        assertEquals(Set.of(PC, FB), Set.copyOf(mumu.accounts()));
        assertEquals(TALL, mumu.fields().get(ConfigField.SCREEN), "inherited from LDPlayer");
        assertEquals(new ConfigValue.Count(15), mumu.fields().get(ConfigField.FPS));
        assertTrue(mumu.games().isEmpty(), "Farm removed the game");
        assertThrows(IllegalArgumentException.class, () -> ConfigViews.copy(config(), LD, LD));
    }

    @Test
    void anInstanceCopiedOntoItsOwnEmulatorKeepsTheEmulatorsProductSettings() {
        EmulatorConfig copied = ConfigViews.copy(config(), FARM_T, LD);
        Layer ld = copied.emulator(PlatformId.LDPLAYER);
        assertEquals(new ConfigValue.Toggle(true), ld.fields().get(ConfigField.PRODUCT_AUTO_UPDATE_OFF));
        assertEquals(TALL, ld.fields().get(ConfigField.SCREEN));
        assertEquals(new ConfigValue.Count(15), ld.fields().get(ConfigField.FPS));
    }

    @Test
    void anInstanceCopiedOntoAnotherEmulatorsInstanceCarriesNoRemoval() {
        EmulatorConfig copied = ConfigViews.copy(config(), FARM_T, new Target.Instance(GONE_KEY));
        Layer gone = copied.instance(GONE_KEY);
        assertTrue(gone.removedAccounts().isEmpty());
        assertTrue(gone.removedGames().isEmpty());
        assertEquals(List.of("test"), gone.tags());
        assertEquals(TALL, gone.fields().get(ConfigField.SCREEN));
    }

    @Test
    void aFieldIsDroppedByUseEmulatorsOrClearAndStartsFromWhatItShouldBe() {
        assertEquals(Optional.of("Use emulator's"), ConfigViews.dropLabel(FARM_T, config(), ConfigField.FPS));
        assertEquals(Optional.of("Clear"), ConfigViews.dropLabel(FARM_T, config(), ConfigField.MEMORY));
        assertEquals(Optional.empty(), ConfigViews.dropLabel(FARM_T, config(), ConfigField.SCREEN));
        assertEquals(Optional.of("Clear"), ConfigViews.dropLabel(LD, config(), ConfigField.SCREEN));
        assertEquals(Optional.of(TALL), ConfigViews.current(FARM_T, config(), ConfigField.SCREEN));
        assertEquals(Optional.empty(), ConfigViews.current(FARM_T, config(), ConfigField.PRODUCT_AUTO_UPDATE_OFF));
        assertEquals(Optional.empty(), ConfigViews.current(LD, config(), ConfigField.MEMORY));
    }

    @Test
    void aGoogleOrMailAccountIsAnAddress() {
        assertTrue(ConfigViews.accountName(AccountRecord.Kind.GOOGLE, " user@example.com "));
        assertFalse(ConfigViews.accountName(AccountRecord.Kind.GOOGLE, "main alt"));
        assertFalse(ConfigViews.accountName(AccountRecord.Kind.MAIL, "user@example"));
        assertTrue(ConfigViews.accountName(AccountRecord.Kind.FACEBOOK, "main alt"));
        assertFalse(ConfigViews.accountName(AccountRecord.Kind.FACEBOOK, " "));
    }
}
