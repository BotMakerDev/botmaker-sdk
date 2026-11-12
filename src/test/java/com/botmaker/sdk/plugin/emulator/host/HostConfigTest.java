package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.java.grammar.ValueGrammar;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.plugin.SdkPlugin;
import com.botmaker.sdk.plugin.types.HostTypes;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostConfigTest {

    private static final InstanceKey FARM = new InstanceKey(PlatformId.LDPLAYER, "LDPlayer-1");
    private static final InstanceKey OTHER = new InstanceKey(PlatformId.MUMU, "MuMu-0");

    private static EmulatorConfig full() {
        Layer ld = Layer.EMPTY.with(ConfigField.SCREEN, new ConfigValue.Screen(1080, 1920, 320))
                .with(ConfigField.ROOT, new ConfigValue.Toggle(false))
                .with(ConfigField.PRODUCT_AUTO_UPDATE_OFF, new ConfigValue.Toggle(true))
                .withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "main@example.com", "the main one"))
                .withAccount(new AccountRecord(AccountRecord.Kind.FACEBOOK, "fb-user"))
                .withGame(new GameRecord("com.zjcs.android.us", "Sword x Staff"));
        Layer farm = Layer.EMPTY.with(ConfigField.MEMORY, new ConfigValue.Megabytes(4096))
                .with(ConfigField.CPU_CORES, new ConfigValue.Count(4))
                .with(ConfigField.LOCALE, new ConfigValue.Text("fr-FR"))
                .withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com"))
                .removingAccount(new AccountRecord.Key(AccountRecord.Kind.FACEBOOK, "fb-user"))
                .withDefaultGoogle("farm@example.com")
                .removingGame("com.zjcs.android.us")
                .withGame(new GameRecord("com.example.other", "Other"))
                .withTags(List.of("farm", "main"));
        return EmulatorConfig.EMPTY.withEmulator(PlatformId.LDPLAYER, ld).withInstance(FARM, farm);
    }

    @Test
    void theConfigurationRoundTripsThroughItsRows() {
        EmulatorConfig config = full();
        HostConfig.Rows rows = HostConfig.rows(config, HostConfig.Rows.NONE);
        assertEquals(config, HostConfig.config(rows));
        assertTrue(rows.emulatorSettings().list().contains(
                new HostSettings.Setting("ldplayer", "screen", "1080x1920@320")));
        assertTrue(rows.instanceSettings().list().contains(
                new HostSettings.Setting("ldplayer/LDPlayer-1", "memory", "4096")));
        assertTrue(rows.accounts().list().contains(
                new HostAccounts.Account("ldplayer/LDPlayer-1", "google", "farm@example.com", "", false, true)));
        assertTrue(rows.accounts().list().contains(
                new HostAccounts.Account("ldplayer/LDPlayer-1", "facebook", "fb-user", "", true, false)));
        assertEquals(List.of(new HostLabels.Label("ldplayer/LDPlayer-1", "farm"),
                new HostLabels.Label("ldplayer/LDPlayer-1", "main")), rows.tags().list());
        assertEquals(rows, HostConfig.rows(HostConfig.config(rows), rows), "written the same way each time");
    }

    @Test
    void theFirstGoogleAccountMayBeOneTheEmulatorLists() {
        EmulatorConfig config = EmulatorConfig.EMPTY
                .withEmulator(PlatformId.LDPLAYER, Layer.EMPTY
                        .withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "a@example.com"))
                        .withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "b@example.com")))
                .withInstance(FARM, Layer.EMPTY.withDefaultGoogle("b@example.com"));
        EmulatorConfig read = HostConfig.config(HostConfig.rows(config, HostConfig.Rows.NONE));
        assertEquals("b@example.com", read.wanted(FARM).defaultGoogle().orElseThrow());
        assertEquals(2, read.wanted(FARM).accounts().size());
    }

    /** Review item 1, on the rows: what a newer BotMaker wrote is read past and kept on rewrite. */
    @Test
    void rowsThisBuildDoesNotUnderstandAreKept() {
        HostSettings.Setting newerField = new HostSettings.Setting("ldplayer", "gpu_mode", "vulkan");
        HostSettings.Setting newerProduct = new HostSettings.Setting("nox", "screen", "1080x1920@320");
        HostSettings.Setting badValue = new HostSettings.Setting("ldplayer/LDPlayer-1", "memory", "lots");
        HostAccounts.Account newerKind = new HostAccounts.Account("ldplayer", "apple", "me@example.com", "", false,
                false);
        HostGames.Game newerGame = new HostGames.Game("nox/Nox-1", "com.example", "Example", false);
        HostLabels.Label newerTag = new HostLabels.Label("nox/Nox-1", "farm");
        HostLabels.Label usedBy = new HostLabels.Label("ldplayer/LDPlayer-1", "SwordXStaff");
        HostConfig.Rows before = new HostConfig.Rows(HostSettings.of(newerField, newerProduct),
                HostGames.none(), HostSettings.of(badValue), HostGames.of(newerGame), HostLabels.of(newerTag),
                HostLabels.of(usedBy), HostAccounts.of(newerKind));
        assertEquals(EmulatorConfig.EMPTY, HostConfig.config(before));

        HostConfig.Rows next = HostConfig.rows(full(), before);
        assertEquals(full(), HostConfig.config(next));
        assertTrue(next.emulatorSettings().list().containsAll(List.of(newerField, newerProduct)));
        assertTrue(next.instanceSettings().list().contains(badValue));
        assertTrue(next.accounts().list().contains(newerKind));
        assertTrue(next.instanceGames().list().contains(newerGame));
        assertTrue(next.tags().list().contains(newerTag));
        assertEquals(before.usedBy(), next.usedBy(), "a configuration change leaves the bots seen alone");
    }

    @Test
    void anEmulatorOnlyFieldOnAnInstanceIsNotRead() {
        HostConfig.Rows rows = new HostConfig.Rows(HostSettings.none(), HostGames.none(),
                HostSettings.of(new HostSettings.Setting("ldplayer/LDPlayer-1", "product_auto_update_off", "on")),
                HostGames.none(), HostLabels.none(), HostLabels.none(), HostAccounts.none());
        assertEquals(EmulatorConfig.EMPTY, HostConfig.config(rows));
    }

    @Test
    void aBotIsNotedBesideExactlyTheInstancesItLaunches() {
        List<InstanceKey> both = List.of(FARM, OTHER);
        HostLabels none = HostLabels.none();
        HostLabels first = HostConfig.seen(none, "SwordXStaff", List.of(FARM, OTHER), both);
        assertEquals(Map.of(FARM, List.of("SwordXStaff"), OTHER, List.of("SwordXStaff")), HostConfig.usedBy(first));
        assertEquals(first, HostConfig.seen(first, "SwordXStaff", List.of(OTHER, FARM), both), "nothing to write");

        HostLabels two = HostConfig.seen(first, "Other bot", List.of(FARM), both);
        assertEquals(List.of("SwordXStaff", "Other bot"), HostConfig.usedBy(two).get(FARM));

        HostLabels moved = HostConfig.seen(two, "SwordXStaff", List.of(OTHER), both);
        assertEquals(List.of("Other bot"), HostConfig.usedBy(moved).get(FARM));
        assertEquals(List.of("SwordXStaff"), HostConfig.usedBy(moved).get(OTHER));
        assertFalse(HostConfig.usedBy(HostConfig.seen(moved, "SwordXStaff", List.of(), both)).containsKey(OTHER));
        assertEquals(moved, HostConfig.seen(moved, "SwordXStaff", List.of(), List.of(FARM)),
                "an emulator not found this time keeps the bot");
    }

    @Test
    void aRenamedInstanceTakesItsRowsWithIt() {
        InstanceKey renamed = new InstanceKey(PlatformId.LDPLAYER, "Farm 1");
        HostConfig.Rows before = HostConfig.rows(full(), new HostConfig.Rows(HostSettings.none(), HostGames.none(),
                HostSettings.of(new HostSettings.Setting("ldplayer/LDPlayer-1", "gpu_mode", "vulkan"),
                        new HostSettings.Setting("ldplayer/Farm 1", "memory", "1024")),
                HostGames.none(), HostLabels.none(), HostLabels.of(new HostLabels.Label("ldplayer/LDPlayer-1", "Bot")),
                HostAccounts.none()));
        HostConfig.Rows after = HostConfig.renamed(before, FARM, renamed);
        EmulatorConfig config = HostConfig.config(after);
        assertEquals(full().instance(FARM), config.instance(renamed));
        assertTrue(config.instance(FARM).isEmpty());
        assertEquals(full().emulator(PlatformId.LDPLAYER), config.emulator(PlatformId.LDPLAYER));
        assertEquals(List.of("Bot"), HostConfig.usedBy(after.usedBy()).get(renamed));
        assertTrue(after.instanceSettings().list().contains(
                new HostSettings.Setting("ldplayer/Farm 1", "gpu_mode", "vulkan")), "what it does not read moves too");
        assertEquals(before, HostConfig.renamed(before, FARM, FARM));
    }

    @Test
    void aDeletedInstanceLeavesNoRows() {
        HostConfig.Rows before = HostConfig.rows(full(), new HostConfig.Rows(HostSettings.none(), HostGames.none(),
                HostSettings.of(new HostSettings.Setting("ldplayer/LDPlayer-1", "gpu_mode", "vulkan")),
                HostGames.none(), HostLabels.none(), HostLabels.of(new HostLabels.Label("ldplayer/LDPlayer-1", "Bot"),
                        new HostLabels.Label("mumu/MuMu-0", "Bot")), HostAccounts.none()));
        HostConfig.Rows after = HostConfig.dropped(before, FARM);
        assertTrue(HostConfig.config(after).instance(FARM).isEmpty());
        assertEquals(full().emulator(PlatformId.LDPLAYER), HostConfig.config(after).emulator(PlatformId.LDPLAYER));
        assertTrue(after.instanceSettings().list().isEmpty(), "what it does not read goes too");
        assertEquals(Map.of(OTHER, List.of("Bot")), HostConfig.usedBy(after.usedBy()));
        assertEquals(before, HostConfig.dropped(before, new InstanceKey(PlatformId.MEMU, "Nobody")));
    }

    @Test
    void launchTargetsNameInstancesByReference() {
        EmulatorInstance ld = new EmulatorInstance(PlatformId.LDPLAYER, "LDPlayer-1", "127.0.0.1", 5555);
        EmulatorInstance mumu = new EmulatorInstance(PlatformId.MUMU, "MuMu-0", "127.0.0.1", 16384);
        assertEquals(Set.of(FARM), UsedBy.targets(List.of("emu-app:com.zjcs.android.us@LDPlayer-1", "steam:1145350",
                "emu-app:com.zjcs.android.us@Gone", "not a spec"), List.of(ld, mumu)));
        assertEquals(Set.of(), UsedBy.targets(List.of(), List.of(ld)));
    }

    /** The host writes these by the declarations: each takes its value apart and builds it back equal. */
    @Test
    void theHostCanWriteEveryValue() {
        HostConfig.Rows rows = HostConfig.rows(full(), HostConfig.Rows.NONE);
        assertEquals(rows.emulatorSettings(), roundTrip(HostTypes.SETTINGS, rows.emulatorSettings()));
        assertEquals(rows.instanceGames(), roundTrip(HostTypes.GAMES, rows.instanceGames()));
        assertEquals(rows.accounts(), roundTrip(HostTypes.ACCOUNTS, rows.accounts()));
        assertEquals(rows.tags(), roundTrip(HostTypes.LABELS, rows.tags()));
        HostAccounts.Account account = rows.accounts().list().getFirst();
        assertEquals(account, roundTrip(HostTypes.ACCOUNT, account));
        assertEquals(rows.instanceGames().list().getFirst(),
                roundTrip(HostTypes.GAME, rows.instanceGames().list().getFirst()));
        assertEquals(HostSettings.none(), roundTrip(HostTypes.SETTINGS, HostSettings.none()));
        SdkPlugin plugin = new SdkPlugin();
        assertTrue(plugin.componentTypes().containsAll(List.of(HostTypes.SETTINGS, HostTypes.SETTING, HostTypes.GAMES,
                HostTypes.GAME, HostTypes.ACCOUNTS, HostTypes.ACCOUNT, HostTypes.LABELS, HostTypes.LABEL)));
        List<ManagedValue<?>> host = List.of(SdkHostValues.EMULATOR_SETTINGS, SdkHostValues.EMULATOR_GAMES,
                SdkHostValues.INSTANCE_SETTINGS, SdkHostValues.INSTANCE_GAMES, SdkHostValues.INSTANCE_TAGS,
                SdkHostValues.INSTANCE_USED_BY, SdkHostValues.ACCOUNTS);
        assertTrue(plugin.managedValues().containsAll(host));
        assertTrue(host.stream().allMatch(ManagedValue::onHost));
    }

    /** The Java the host keeps in its folder of this PC, written and read back by the real grammar. */
    @Test
    void theValuesAreJavaTheGrammarWritesAndReads() {
        ValueGrammar grammar = ValueGrammar.of(List.of(), List.of(HostTypes.SETTINGS, HostTypes.SETTING,
                HostTypes.GAMES, HostTypes.GAME, HostTypes.ACCOUNTS, HostTypes.ACCOUNT, HostTypes.LABELS,
                HostTypes.LABEL));
        HostSettings settings = HostSettings.of(new HostSettings.Setting("ldplayer", "screen", "1080x1920@320"),
                new HostSettings.Setting("ldplayer/LDPlayer-1", "locale", "fr-FR"));
        String java = grammar.write(HostSettings.class, settings).orElseThrow().source();
        assertEquals("HostSettings.of(new HostSettings.Setting(\"ldplayer\", \"screen\", \"1080x1920@320\"), "
                + "new HostSettings.Setting(\"ldplayer/LDPlayer-1\", \"locale\", \"fr-FR\"))", java);
        assertEquals(settings, grammar.read(HostSettings.class, qualified(java)).orElseThrow());

        HostConfig.Rows rows = HostConfig.rows(full(), HostConfig.Rows.NONE);
        assertEquals(rows.accounts(), grammar.read(HostAccounts.class,
                qualified(grammar.write(HostAccounts.class, rows.accounts()).orElseThrow().source())).orElseThrow());
        assertEquals(rows.instanceGames(), grammar.read(HostGames.class,
                qualified(grammar.write(HostGames.class, rows.instanceGames()).orElseThrow().source())).orElseThrow());
        assertEquals(rows.tags(), grammar.read(HostLabels.class,
                qualified(grammar.write(HostLabels.class, rows.tags()).orElseThrow().source())).orElseThrow());
        assertEquals("HostLabels.of()", grammar.write(HostLabels.class, HostLabels.none()).orElseThrow().source());
    }

    /** {@code java} with the host types named in full, as a file names them by its imports. */
    private static String qualified(String java) {
        return java.replaceAll("\\bHost(Settings|Games|Accounts|Labels)\\b",
                HostSettings.class.getPackageName() + ".Host$1");
    }

    private static <T> T roundTrip(ComponentType<T> shape, T value) {
        return shape.build(shape.components(value));
    }
}
