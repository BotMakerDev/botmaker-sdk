package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.InstanceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountsListModelTest {

    private static final AccountRecord MAIN = new AccountRecord(AccountRecord.Kind.GOOGLE, "Main@example.com", "main");
    private static final AccountRecord FARM = new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com");
    private static final AccountRecord FB = new AccountRecord(AccountRecord.Kind.FACEBOOK, "a player", "farm alt");
    private static final InstanceKey MUMU = new InstanceKey(PlatformId.MUMU, "MuMu-0");
    private static final InstanceKey MEMU = new InstanceKey(PlatformId.MEMU, "MEmu");

    private static List<AccountsListModel.Row> rows() {
        return AccountsListModel.rows(List.of(MAIN, FARM, FB), Map.of(MAIN.key(), List.of(MUMU, MEMU)),
                Set.of(MAIN.key(), FB.key()), Set.of("main@example.com"));
    }

    @Test
    void theRowsGoByNameAndCountTheInstancesThatWantEachAccount() {
        List<AccountsListModel.Row> rows = rows();
        assertEquals(List.of(FB, FARM, MAIN), rows.stream().map(AccountsListModel.Row::account).toList());
        assertEquals(List.of(0, 0, 2), rows.stream().map(AccountsListModel.Row::instances).toList());
        assertEquals("2 instances", rows.get(2).instancesText());
        assertEquals("None", rows.get(1).instancesText());
    }

    @Test
    void thePasswordSaysRememberedAskedOrNoneForARecord() {
        List<AccountsListModel.Row> rows = rows();
        assertEquals(List.of(AccountsListModel.Password.NONE, AccountsListModel.Password.ASKED,
                AccountsListModel.Password.REMEMBERED), rows.stream().map(AccountsListModel.Row::password).toList());
        assertTrue(rows.get(2).canForget());
        assertFalse(rows.get(2).canRemember(true));
        assertTrue(rows.get(1).canRemember(true));
        assertFalse(rows.get(1).canRemember(false), "no store: nothing to remember it in");
        assertFalse(rows.get(0).canRemember(true), "a Facebook record has no password");
        assertEquals(Set.of("main@example.com", "farm@example.com"), AccountsListModel.keys(List.of(MAIN, FARM, FB)));
    }

    @Test
    void removeIsOfferedOnlyForAnAccountNoConfigurationLists() {
        assertEquals(List.of(false, true, false), rows().stream().map(AccountsListModel.Row::removable).toList());
    }

    @Test
    void anImportCountsTheAccountsAddedAndTheNotesFilledAgainstTheBookReadBeforeIt() {
        AccountRecord farmNoted = new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com", "farm");
        AccountsListModel.Imported imported = AccountsListModel.imported(List.of(MAIN, FARM),
                List.of(MAIN, farmNoted, FB));
        assertEquals(new AccountsListModel.Imported(1, 1), imported);
        assertEquals("1 account imported, 1 note filled from a.json.", imported.said("a.json"));
        assertEquals("Nothing new in a.json.", AccountsListModel.imported(List.of(MAIN), List.of(MAIN)).said("a.json"));
        assertEquals("2 accounts imported from a.json.",
                AccountsListModel.imported(List.of(), List.of(MAIN, FB)).said("a.json"));
    }

    @Test
    void theFilterReadsTheNameAndTheNoteAndTheKind() {
        assertEquals(List.of(FB, FARM), AccountsListModel.filtered(rows(), " FARM ", null).stream()
                .map(AccountsListModel.Row::account).toList());
        assertEquals(List.of(FARM), AccountsListModel.filtered(rows(), "farm", AccountRecord.Kind.GOOGLE).stream()
                .map(AccountsListModel.Row::account).toList());
        assertEquals(3, AccountsListModel.filtered(rows(), "", null).size());
    }
}
