// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.sdk.plugin.emulator.host.HostBook;
import com.botmaker.shared.emulator.AdbEndpoint;
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
        assertEquals(2, AccountsGridModel.applyCount(all), "Main signed out, Farm signed in");
        assertEquals(new AccountsGridModel.Pending(1, 1, 0, 1), AccountsGridModel.pending(all));
        assertEquals("1 sign-in, 1 sign-out across 1 instance", AccountsGridModel.pending(all).said());
        assertEquals("0 sign-ins, 1 sign-out if it is on it across 1 instance",
                new AccountsGridModel.Pending(0, 1, 1, 1).said());
        assertEquals("1 sign-in, 3 sign-outs (2 if it is on it) across 2 instances",
                new AccountsGridModel.Pending(1, 3, 2, 2).said());

        List<ApplyPlan.Item> mainOnly = AccountsGridModel.toApply(rows, List.of(MAIN));
        assertEquals(1, AccountsGridModel.applyCount(mainOnly), "Farm's row is not shown: its sign-in waits");
        assertEquals("Nothing to apply", AccountsGridModel.pending(List.of()).said());
    }

    @Test
    void aPhoneColumnTicksLikeAnInstanceAndItsSignInIsPending() {
        EmulatorInstance pixel = new EmulatorInstance(PlatformId.PHYSICAL, "Pixel 10",
                new AdbEndpoint.Server("4A1B2C3D"));
        InstanceKey pixelKey = InstanceKey.of(pixel);
        List<EmulatorsModel.InstanceRow> shown = List.of(row(CONFIG, LD, null), row(CONFIG, pixel, Set.of()));
        assertEquals(List.of(LD_KEY, pixelKey), AccountsGridModel.columns(shown, AccountsGridModel.Filter.NONE)
                .stream().map(EmulatorsModel.InstanceRow::key).toList());
        assertEquals(List.of(pixelKey), AccountsGridModel.columns(shown,
                new AccountsGridModel.Filter(PlatformId.PHYSICAL, "", null)).stream()
                .map(EmulatorsModel.InstanceRow::key).toList(), "Accounts… on a phone shows the phones");

        EmulatorConfig ticked = AccountsGridModel.tick(CONFIG, pixelKey, ALT, true, Optional.of(Set.of()));
        EmulatorsModel.InstanceRow phone = row(ticked, pixel, Set.of());
        assertEquals(AccountsGridModel.Cell.TO_SIGN_IN, AccountsGridModel.cell(ticked, phone, ALT));
        List<ApplyPlan.Item> items = AccountsGridModel.toApply(List.of(phone), List.of(ALT));
        assertEquals(new AccountsGridModel.Pending(1, 0, 0, 1), AccountsGridModel.pending(items));

        EmulatorsModel.InstanceRow unplugged = row(ticked, pixel, null);
        assertEquals(AccountsGridModel.Cell.NOT_READ, AccountsGridModel.cell(ticked, unplugged, ALT),
                "a phone not connected keeps its tick, unread");
    }

    @Test
    void eachCellHasAGlyphAndWordsAndTheInspectorSaysWhereAnAccountIsTicked() {
        for (AccountsGridModel.Cell cell : AccountsGridModel.Cell.values()) {
            assertFalse(AccountsGridModel.glyph(cell).words().isEmpty(), cell + " says something");
            assertEquals(cell == AccountsGridModel.Cell.NONE || cell == AccountsGridModel.Cell.UNKNOWN,
                    AccountsGridModel.glyph(cell).mark().isEmpty(), cell + "'s mark");
        }
        assertEquals("em-glyph-to-sign-in", AccountsGridModel.styleClass(AccountsGridModel.Cell.TO_SIGN_IN));

        EmulatorsModel.InstanceRow ld = row(CONFIG, LD, Set.of("main@example.com"));
        EmulatorsModel.InstanceRow mumu = row(CONFIG, MUMU, Set.of());
        assertEquals(List.of(new AccountsGridModel.On(ld, AccountsGridModel.Cell.SIGNED_IN)),
                AccountsGridModel.tickedOn(CONFIG, List.of(ld, mumu), MAIN), "LDPlayer's layer lists Main");
        List<AccountsGridModel.On> farm = AccountsGridModel.tickedOn(CONFIG, List.of(ld, mumu), FARM);
        assertEquals(List.of(new AccountsGridModel.On(ld, AccountsGridModel.Cell.TO_SIGN_IN)), farm);
        assertEquals(" · at Apply", farm.getFirst().mark(), "Farm is still to sign in on Farm");
        assertEquals(" · not read", AccountsGridModel.tickedOn(CONFIG, List.of(row(CONFIG, LD, null)), MAIN)
                .getFirst().mark());
        assertEquals("", AccountsGridModel.tickedOn(CONFIG, List.of(ld), MAIN).getFirst().mark());
        assertEquals(List.of(), AccountsGridModel.tickedOn(CONFIG, List.of(ld, mumu), ALT));
    }

    @Test
    void aRowOrAColumnTicksOnlyTheCellsNotAlreadySo() {
        List<EmulatorsModel.InstanceRow> columns = List.of(row(CONFIG, LD, Set.of()), row(CONFIG, MUMU, Set.of()));
        List<AccountsGridModel.Change> main = AccountsGridModel.row(CONFIG, columns, MAIN, true);
        assertEquals(List.of(MUMU_KEY), main.stream().map(c -> c.column().key()).toList(), "LDPlayer lists it");
        EmulatorConfig everywhere = AccountsGridModel.tick(CONFIG, main);
        assertEquals(List.of(MAIN), everywhere.instance(MUMU_KEY).accounts());

        List<AccountsGridModel.Change> off = AccountsGridModel.column(CONFIG, columns.getFirst(),
                List.of(ALT, FARM, MAIN), false);
        assertEquals(List.of(FARM, MAIN), off.stream().map(AccountsGridModel.Change::account).toList());
        EmulatorConfig none = AccountsGridModel.tick(CONFIG, off);
        assertEquals(0, AccountsGridModel.count(none, LD_KEY));
    }

    @Test
    void oneAccountPerInstanceKeepsWhatAColumnHasAndGivesTheOthersTheNextFreeOne() {
        EmulatorInstance third = new EmulatorInstance(PlatformId.MUMU, "MuMu-1", "127.0.0.1", 16416);
        EmulatorInstance fourth = new EmulatorInstance(PlatformId.MUMU, "MuMu-2", "127.0.0.1", 16448);
        // Farm holds Main (its emulator's) and Farm: it keeps Farm, the first in row order, and Main goes free.
        List<EmulatorsModel.InstanceRow> columns = List.of(row(CONFIG, LD, Set.of()), row(CONFIG, MUMU, Set.of()),
                row(CONFIG, third, Set.of()), row(CONFIG, fourth, Set.of()));
        List<AccountRecord> rows = List.of(ALT, FARM, MAIN);
        EmulatorConfig spread = AccountsGridModel.tick(CONFIG, AccountsGridModel.spread(CONFIG, columns, rows));

        assertEquals(1, AccountsGridModel.count(spread, LD_KEY));
        assertEquals(List.of(FARM), spread.instance(LD_KEY).accounts());
        assertEquals(Set.of(MAIN.key()), spread.instance(LD_KEY).removedAccounts(), "its emulator's, removed here");
        assertEquals(List.of(ALT), spread.instance(MUMU_KEY).accounts());
        assertEquals(List.of(MAIN), spread.instance(InstanceKey.of(third)).accounts());
        assertTrue(spread.instance(InstanceKey.of(fourth)).isEmpty(), "no account left: left as it was");
        assertTrue(AccountsGridModel.spread(spread, List.of(row(spread, LD, Set.of()), row(spread, MUMU, Set.of()),
                row(spread, third, Set.of())), rows).isEmpty(), "spread already: nothing to change");
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

    @Test
    void anAccountIsItsCellThroughAnyOfItsAddressesAndATickWritesTheOneNamed() {
        AccountRecord main = MAIN.withAlias("m.two@example.com");
        AccountRecord.Key alias = new AccountRecord.Key(AccountRecord.Kind.GOOGLE, "m.two@example.com");
        EmulatorConfig config = CONFIG.withInstance(MUMU_KEY,
                Layer.EMPTY.withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "M.Two@example.com")));
        EmulatorsModel.InstanceRow mumu = row(config, MUMU, Set.of("m.two@example.com"));
        assertEquals(AccountsGridModel.Cell.SIGNED_IN, AccountsGridModel.cell(config, mumu, main));
        assertEquals(alias, AccountsGridModel.named(config, MUMU_KEY, main, Optional.of(Set.of())));

        EmulatorConfig off = AccountsGridModel.tick(config, MUMU_KEY, main, false, Optional.of(Set.of("m.two@example.com")));
        assertEquals(Set.of(alias), off.instance(MUMU_KEY).removedAccounts(), "signed out by the address it holds");

        // signed in by its alias though no layer lists it: a tick lists the alias, so Apply signs nothing in again
        EmulatorConfig unlisted = CONFIG;
        assertEquals(AccountsGridModel.Cell.UNLISTED,
                AccountsGridModel.cell(unlisted, row(unlisted, MUMU, Set.of("m.two@example.com")), main));
        EmulatorConfig on = AccountsGridModel.tick(unlisted, MUMU_KEY, main, true, Optional.of(Set.of("m.two@example.com")));
        assertEquals(List.of(alias), on.instance(MUMU_KEY).accounts().stream().map(AccountRecord::key).toList());

        EmulatorsModel.InstanceRow toSign = row(config, MUMU, Set.of());
        assertEquals(1, AccountsGridModel.applyCount(AccountsGridModel.toApply(List.of(toSign), List.of(main))),
                "the alias's sign-in is the shown account's");
    }

    @Test
    void anAccountHeldThroughTwoOfItsAddressesIsSaidShared() {
        AccountRecord main = MAIN.withAlias("m.two@example.com");
        EmulatorConfig config = CONFIG.withInstance(MUMU_KEY,
                Layer.EMPTY.withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "m.two@example.com")));
        List<HostBook.Shared> shared = HostBook.shared(config, List.of(LD_KEY, MUMU_KEY), List.of(main, FARM));
        assertTrue(AccountsGridModel.shared(shared, main, MUMU_KEY).isPresent());
        assertTrue(AccountsGridModel.shared(shared, main, null).isPresent());
        assertTrue(AccountsGridModel.shared(shared, FARM, LD_KEY).isEmpty());
        assertEquals("Shared: 2 instances hold it through 2 of its addresses, and Google sees one account signed in"
                + " on each", AccountsGridModel.sharedWords(shared.getFirst()));
        assertTrue(HostBook.shared(CONFIG, List.of(LD_KEY, MUMU_KEY), List.of(main)).isEmpty(),
                "one address: nothing hidden");
    }
}
