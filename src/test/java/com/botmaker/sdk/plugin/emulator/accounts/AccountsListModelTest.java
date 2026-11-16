// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.sdk.plugin.emulator.window.Look;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import com.botmaker.shared.emulator.config.InstanceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    void theKindFilterCountsEveryKindWithAllFirstAndAnEmptyKindAtZero() {
        List<AccountsListModel.KindCount> counts = AccountsListModel.counts(rows());
        assertEquals(List.of("All 3", "Google 2", "Facebook 1", "Mail 0"),
                counts.stream().map(AccountsListModel.KindCount::said).toList());
        assertEquals(List.of("All 0", "Google 0", "Facebook 0", "Mail 0"),
                AccountsListModel.counts(List.of()).stream().map(AccountsListModel.KindCount::said).toList());
    }

    @Test
    void eachRowKnowsTheInstancesThatWantItAndItsPasswordPillAndWords() {
        List<AccountsListModel.Row> rows = rows();
        assertEquals(List.of(MUMU, MEMU), rows.get(2).on());
        assertEquals(new Look.Pill(Look.Tone.OK, "Remembered"), rows.get(2).password().pill());
        assertEquals(new Look.Pill(Look.Tone.MUTED, "Asked"), rows.get(1).password().pill());
        assertEquals(new Look.Pill(Look.Tone.OUTLINED, "None"), rows.get(0).password().pill());
        assertTrue(rows.get(1).password().words(true).contains("first Apply"));
        assertTrue(rows.get(1).password().words(false).startsWith("No credential store"));
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

    @Test
    void aPastedListAddsEachNewEmailOnceAndSaysWhatItLeft() {
        AccountsListModel.Pasted pasted = AccountsListModel.pasted(
                "new@example.com, MAIN@example.com\n  other@example.com;new@example.com\nnot-an-email\n"
                        + "main@example.com\n\n",
                AccountRecord.Kind.GOOGLE, List.of(MAIN, FARM, FB));

        assertEquals(List.of("new@example.com", "other@example.com"),
                pasted.added().stream().map(AccountRecord::name).toList());
        assertEquals(1, pasted.known(), "Main, pasted twice, is one account listed already");
        assertEquals(List.of("not-an-email"), pasted.skipped());
        assertEquals("2 accounts added, 1 listed already, 1 not an email address.", pasted.said("added"));
        assertEquals("No account to add.", AccountsListModel.pasted("", AccountRecord.Kind.GOOGLE, List.of())
                .said("to add"));
    }

    @Test
    void aPastedListOfRecordsTakesAnyWordAndOnlyItsKindsBook() {
        AccountsListModel.Pasted pasted = AccountsListModel.pasted("A Player\n  another player \nfarm@example.com",
                AccountRecord.Kind.FACEBOOK, List.of(MAIN, FARM, FB));
        // a name keeps its spaces; FB's "a player" is listed already, and Google's farm is another kind
        assertEquals(List.of("another player", "farm@example.com"),
                pasted.added().stream().map(AccountRecord::name).toList());
        assertEquals(1, pasted.known());
        assertTrue(pasted.skipped().isEmpty());
    }

    @Test
    void theVerificationChoicesPutTheDefaultFirstAndNameWhatItIs() {
        assertEquals(List.of(Verification.DEFAULT, Verification.PHONE, Verification.CODE, Verification.AUTHENTICATOR,
                Verification.BACKUP_CODE, Verification.ASK), AccountsListModel.verificationChoices(Verification.CODE));
        assertEquals(Verification.UNKNOWN, AccountsListModel.verificationChoices(Verification.UNKNOWN).getLast(),
                "a newer BotMaker's shows as it is");
        assertEquals("Default (Phone prompt)", AccountsListModel.said(Verification.DEFAULT, Verification.PHONE));
        assertEquals("Default (Backup code)", AccountsListModel.said(Verification.DEFAULT, Verification.BACKUP_CODE));
        assertEquals("Ask me", AccountsListModel.said(Verification.ASK, Verification.PHONE));
    }

    @Test
    void anAccountIsCountedFoundAndKeptThroughAnyOfItsAliases() {
        AccountRecord farm = FARM.withAliases(List.of("f.one@example.com", "f.two@example.com"));
        AccountsListModel.Row row = AccountsListModel.rows(List.of(farm),
                Map.of(farm.key(), List.of(MUMU), new AccountRecord.Key(AccountRecord.Kind.GOOGLE, "F.One@example.com"),
                        List.of(MEMU, MUMU)),
                Set.of(new AccountRecord.Key(AccountRecord.Kind.GOOGLE, "f.two@example.com")),
                Set.of("f.one@example.com")).getFirst();
        assertEquals(List.of(MUMU, MEMU), row.on(), "MuMu-0 counted once, by either address");
        assertEquals(AccountsListModel.Password.REMEMBERED, row.password(), "remembered under an alias");
        assertFalse(row.removable(), "a configuration lists one of its aliases");
        assertEquals("+2", AccountsListModel.aliasTag(farm));
        assertEquals("", AccountsListModel.aliasTag(FARM));
        assertEquals(List.of(row), AccountsListModel.filtered(List.of(row), "F.TWO", null), "Find reads the aliases");
        assertEquals(Set.of("farm@example.com", "f.one@example.com", "f.two@example.com"),
                AccountsListModel.keys(List.of(farm)));
    }

    @Test
    void anEntryAnotherHasAsAnAliasIsOfferedTheMergeAndOnlyDifferentPasswordsAreAsked() {
        AccountRecord main = MAIN.withAlias("farm@example.com");
        assertEquals(Optional.of(main), AccountsListModel.mergeInto(FARM, List.of(main, FARM, FB)));
        assertTrue(AccountsListModel.mergeInto(main, List.of(main, FARM)).isEmpty());
        assertTrue(AccountsListModel.mergeInto(FARM, List.of(MAIN, FARM)).isEmpty());

        assertTrue(AccountsListModel.asksWhichPassword("one".toCharArray(), "two".toCharArray()));
        assertFalse(AccountsListModel.asksWhichPassword("same".toCharArray(), "same".toCharArray()));
        assertFalse(AccountsListModel.asksWhichPassword("one".toCharArray(), null), "only one remembered");
        assertFalse(AccountsListModel.asksWhichPassword(null, null));
    }

    @Test
    void anAliasIsANewEmailAddressOfTheAccount() {
        AccountRecord main = MAIN.withAlias("m.two@example.com");
        assertTrue(AccountsListModel.aliasRefused(main, " new@example.com ").isEmpty());
        assertEquals(Optional.of("An alias is an email address."), AccountsListModel.aliasRefused(main, "not one"));
        assertEquals(Optional.of("Main@example.com answers to it already."),
                AccountsListModel.aliasRefused(main, "M.TWO@example.com"));
        assertEquals("Also signs in as:\nm.two@example.com", AccountsListModel.aliasWords(main));
    }
}
