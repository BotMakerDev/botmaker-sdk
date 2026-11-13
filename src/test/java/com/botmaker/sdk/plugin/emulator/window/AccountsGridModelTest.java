package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.ConfigDiff;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
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

class AccountsGridModelTest {

    private static final AccountRecord MAIN = new AccountRecord(AccountRecord.Kind.GOOGLE, "Main@example.com");
    private static final AccountRecord FARM = new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com");
    private static final AccountRecord ALT = new AccountRecord(AccountRecord.Kind.GOOGLE, "alt@example.com");
    private static final AccountRecord FB = new AccountRecord(AccountRecord.Kind.FACEBOOK, "a player");
    private static final EmulatorInstance LD = new EmulatorInstance(PlatformId.LDPLAYER, "Farm", "127.0.0.1", 5555);
    private static final EmulatorInstance MUMU = new EmulatorInstance(PlatformId.MUMU, "MuMu-0", "127.0.0.1", 16384);
    private static final InstanceKey LD_KEY = InstanceKey.of(LD);
    private static final InstanceKey MUMU_KEY = InstanceKey.of(MUMU);

    /** LDPlayer's layer lists Main; the Farm instance adds Farm of its own. */
    private static final EmulatorConfig CONFIG = EmulatorConfig.EMPTY
            .withEmulator(PlatformId.LDPLAYER, Layer.EMPTY.withAccount(MAIN))
            .withInstance(LD_KEY, Layer.EMPTY.withAccount(FARM).withTags(List.of("farm")));

    private static EmulatorsModel.InstanceRow row(EmulatorConfig config, EmulatorInstance instance, Set<String> held) {
        InstanceKey key = InstanceKey.of(instance);
        InstanceFacts facts = held == null ? null : new InstanceFacts(key, Map.of(), Instant.EPOCH,
                Optional.of(new AndroidState(Map.of(), Optional.of(held), Optional.empty(), "14", false,
                        Instant.EPOCH)), true);
        return new EmulatorsModel.InstanceRow(instance, key, held != null, config.wanted(key), facts,
                facts == null ? null : ConfigDiff.of(config.wanted(key), facts), List.of());
    }

    @Test
    void eachCellSaysWhatTheConfigurationWantsAgainstWhatAndroidSaid() {
        EmulatorConfig config = CONFIG.withInstance(LD_KEY, CONFIG.instance(LD_KEY).removingAccount(ALT.key()));
        EmulatorsModel.InstanceRow ld = row(config, LD, Set.of("main@example.com", "alt@example.com",
                "other@example.com"));
        assertEquals(AccountsGridModel.Cell.SIGNED_IN, AccountsGridModel.cell(config, ld, MAIN));
        assertEquals(AccountsGridModel.Cell.TO_SIGN_IN, AccountsGridModel.cell(config, ld, FARM));
        assertEquals(AccountsGridModel.Cell.TO_SIGN_OUT, AccountsGridModel.cell(config, ld, ALT));
        assertEquals(AccountsGridModel.Cell.UNLISTED, AccountsGridModel.cell(config,
                ld, new AccountRecord(AccountRecord.Kind.GOOGLE, "other@example.com")));

        EmulatorsModel.InstanceRow stopped = row(config, LD, null);
        assertEquals(AccountsGridModel.Cell.NOT_READ, AccountsGridModel.cell(config, stopped, MAIN));
        assertEquals(AccountsGridModel.Cell.REMOVED_HERE, AccountsGridModel.cell(config, stopped, ALT));
        assertEquals(AccountsGridModel.Cell.NONE, AccountsGridModel.cell(config, row(config, MUMU, Set.of()), MAIN));
        assertTrue(AccountsGridModel.Cell.NOT_READ.ticked());
        assertFalse(AccountsGridModel.Cell.REMOVED_HERE.ticked());
    }

    @Test
    void unTickingAnInheritedAccountWritesRemovedHereAndNeverTouchesTheEmulatorsLayer() {
        EmulatorConfig off = AccountsGridModel.tick(CONFIG, LD_KEY, MAIN, false, Optional.of(Set.of()));
        assertEquals(Set.of(MAIN.key()), off.instance(LD_KEY).removedAccounts());
        assertEquals(CONFIG.emulator(PlatformId.LDPLAYER), off.emulator(PlatformId.LDPLAYER));
        assertEquals(AccountsGridModel.Cell.REMOVED_HERE, AccountsGridModel.cell(off, row(off, LD, Set.of()), MAIN));

        EmulatorConfig back = AccountsGridModel.tick(off, LD_KEY, MAIN, true, Optional.of(Set.of()));
        assertEquals(CONFIG.instance(LD_KEY), back.instance(LD_KEY), "ticked again, it is the emulator's once more");
    }

    @Test
    void aTickListsTheAccountHereAndAnUntickOfOneNotHeldJustDropsIt() {
        EmulatorConfig on = AccountsGridModel.tick(CONFIG, MUMU_KEY, ALT, true, Optional.of(Set.of()));
        assertEquals(List.of(ALT), on.instance(MUMU_KEY).accounts());

        EmulatorConfig dropped = AccountsGridModel.tick(on, MUMU_KEY, ALT, false, Optional.of(Set.of()));
        assertTrue(dropped.instance(MUMU_KEY).isEmpty(), "never signed in: nothing to remove");

        EmulatorConfig held = AccountsGridModel.tick(on, MUMU_KEY, ALT, false, Optional.of(Set.of("alt@example.com")));
        assertEquals(Set.of(ALT.key()), held.instance(MUMU_KEY).removedAccounts(), "signed in: Apply signs it out");
        EmulatorConfig unread = AccountsGridModel.tick(on, MUMU_KEY, ALT, false, Optional.empty());
        assertEquals(Set.of(ALT.key()), unread.instance(MUMU_KEY).removedAccounts(), "not read: it may hold it");
    }

    @Test
    void theCountRowCountsTheGoogleAccountsEachInstanceShouldHold() {
        EmulatorConfig config = CONFIG.withInstance(LD_KEY, CONFIG.instance(LD_KEY).withAccount(FB));
        assertEquals(2, AccountsGridModel.count(config, LD_KEY));
        assertEquals(0, AccountsGridModel.count(config, MUMU_KEY));
    }

    @Test
    void theColumnsFollowTheEmulatorAndTagFiltersAndTheRowsAreTheBooksGoogleAccounts() {
        List<EmulatorsModel.InstanceRow> rows = List.of(row(CONFIG, MUMU, null), row(CONFIG, LD, null));
        assertEquals(List.of(LD_KEY, MUMU_KEY), AccountsGridModel.columns(rows, AccountsGridModel.Filter.NONE)
                .stream().map(EmulatorsModel.InstanceRow::key).toList());
        assertEquals(List.of(LD_KEY), AccountsGridModel.columns(rows, new AccountsGridModel.Filter(null, "FARM", null))
                .stream().map(EmulatorsModel.InstanceRow::key).toList());
        assertEquals(List.of(MUMU_KEY), AccountsGridModel.columns(rows,
                new AccountsGridModel.Filter(PlatformId.MUMU, "", null)).stream()
                .map(EmulatorsModel.InstanceRow::key).toList());

        List<AccountRecord> book = List.of(MAIN, FB, FARM, ALT);
        assertEquals(List.of(ALT, FARM, MAIN), AccountsGridModel.rows(book, AccountsGridModel.Filter.NONE));
        assertEquals(List.of(MAIN), AccountsGridModel.rows(book,
                AccountsGridModel.Filter.NONE.withAccount(new AccountRecord.Key(AccountRecord.Kind.GOOGLE,
                        "main@example.com"))), "exactly that account, not every name holding it");
    }

    @Test
    void applyRunsOnlyTheSignInsAndOutsOfTheAccountsShownAndNoSetting() {
        EmulatorConfig ticked = AccountsGridModel.tick(CONFIG, LD_KEY, MAIN, false,
                Optional.of(Set.of("main@example.com")))
                .withInstance(MUMU_KEY, Layer.EMPTY.with(ConfigField.FPS, new ConfigValue.Count(30)));
        List<EmulatorsModel.InstanceRow> rows = List.of(row(ticked, LD, Set.of("main@example.com")),
                row(ticked, MUMU, Set.of()));

        List<ApplyPlan.Item> all = AccountsGridModel.toApply(rows, List.of(MAIN, FARM));
        assertEquals(List.of(LD_KEY), all.stream().map(item -> item.row().key()).toList(), "MuMu differs in a"
                + " setting only, which this tab does not apply");
        assertTrue(all.getFirst().changes().isEmpty() && all.getFirst().games().isEmpty());
        assertEquals(2, AccountsGridModel.changes(all), "Main signed out, Farm signed in");

        List<ApplyPlan.Item> mainOnly = AccountsGridModel.toApply(rows, List.of(MAIN));
        assertEquals(1, AccountsGridModel.changes(mainOnly), "Farm's row is not shown: its sign-in waits");
    }

    @Test
    void undoPutsBackOnlyTheTickedCellsAccount() {
        EmulatorConfig ticked = AccountsGridModel.tick(CONFIG, LD_KEY, MAIN, false, Optional.of(Set.of()));
        EmulatorConfig meanwhile = ticked.withInstance(LD_KEY, ticked.instance(LD_KEY).withAccount(FB)
                .withTags(List.of("farm", "new")));
        EmulatorConfig undone = AccountsGridModel.undo(meanwhile,
                Map.of(new AccountsGridModel.Ticked(LD_KEY, MAIN.key()), CONFIG.instance(LD_KEY)));
        assertTrue(undone.instance(LD_KEY).removedAccounts().isEmpty(), "Main is the emulator's again");
        assertEquals(List.of(FARM, FB), undone.instance(LD_KEY).accounts(), "the record added meanwhile stays");
        assertEquals(List.of("farm", "new"), undone.instance(LD_KEY).tags());

        EmulatorConfig listed = AccountsGridModel.tick(CONFIG, LD_KEY, FARM, false, Optional.of(Set.of()));
        assertEquals(List.of(FARM), AccountsGridModel.undo(listed,
                Map.of(new AccountsGridModel.Ticked(LD_KEY, FARM.key()), CONFIG.instance(LD_KEY)))
                .instance(LD_KEY).accounts(), "an account the instance listed is listed again");
    }
}
