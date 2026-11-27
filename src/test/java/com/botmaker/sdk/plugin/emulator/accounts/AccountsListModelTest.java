// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.sdk.plugin.emulator.AccountPool;
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
    void aLaunchTargetsAccountIsHeldAndWantedUnderItsTarget() {
        AccountRecord game = AccountRecord.ofLaunchTarget("emu-app:com.game", "main", "");
        AccountsListModel.Row row = AccountsListModel.rows(List.of(game), Map.of(game.key(), List.of(MEMU)),
                Set.of(game.key()), Set.of()).getFirst();
        assertEquals(1, row.instances());
        assertFalse(row.removable(), "a configuration lists it");
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
        assertEquals(List.of("All 3", "Google 2", "Facebook 1", "Mail 0", "Launch target 0"),
                counts.stream().map(AccountsListModel.KindCount::said).toList());
        assertEquals(List.of("All 0", "Google 0", "Facebook 0", "Mail 0", "Launch target 0"),
                AccountsListModel.counts(List.of()).stream().map(AccountsListModel.KindCount::said).toList());
        assertFalse(AccountsListModel.kinds().contains(AccountRecord.Kind.LAUNCH_TARGET),
                "+ Account adds none: its group's + Add does");
    }

    // ---- launch targets' accounts (run-places phase 7d) ----

    private static final String GAME = "emu-app:com.game";
    private static final AccountRecord AT_ONE = AccountRecord.ofLaunchTarget(GAME, "first", "")
            .withSignsInWith(Optional.of(MAIN.key())).withPlaysAt("this-pc/android/MEmu_1");
    private static final AccountRecord ANYWHERE = AccountRecord.ofLaunchTarget(GAME, "second", "");
    private static final AccountRecord STEAM = AccountRecord.ofLaunchTarget("steam:12345", "alt", "");
    private static final AccountPool.Pc PC = new AccountPool.Pc(Set.of("MEmu_1", "MEmu_2"), Set.of("Win11"),
            Map.of("MEmu_1", Set.of("main@example.com")));

    @Test
    void theLaunchTargetSegmentGroupsAccountsUnderTheBotsTargetsFirstThenTheOthers() {
        List<AccountsListModel.Row> rows = AccountsListModel.rows(List.of(MAIN, STEAM, ANYWHERE, AT_ONE), Map.of(),
                Set.of(), Set.of());
        List<AccountsListModel.Group> groups = AccountsListModel.groups(rows, List.of("emu-app:com.new", GAME));
        assertEquals(List.of("emu-app:com.new", GAME, "steam:12345"),
                groups.stream().map(AccountsListModel.Group::target).toList());
        assertEquals(List.of(), groups.getFirst().rows(), "a bot's target with none still offers + Add");
        assertEquals(List.of(AT_ONE, ANYWHERE), groups.get(1).rows().stream().map(AccountsListModel.Row::account)
                .toList(), "by name, and never the Google account");
        assertEquals(List.of("steam:12345"), AccountsListModel.groups(rows, List.of()).stream()
                .map(AccountsListModel.Group::target).filter(t -> t.startsWith("steam")).toList());
        assertEquals(List.of(STEAM), AccountsListModel.filtered(rows, "steam", AccountRecord.Kind.LAUNCH_TARGET)
                .stream().map(AccountsListModel.Row::account).toList(), "Find reads the launch target too");
    }

    @Test
    void aRowSaysWhatItSignsInWithAndWhereItPlays() {
        assertEquals("main@example.com", AccountsListModel.signsInWithText(AT_ONE));
        assertEquals("—", AccountsListModel.signsInWithText(ANYWHERE));
        assertEquals("MEmu_1", AccountsListModel.playsAtText(AT_ONE.playsAt()));
        assertEquals("Any free place", AccountsListModel.playsAtText(""));
        assertEquals("elsewhere/android/x", AccountsListModel.playsAtText("elsewhere/android/x"));
    }

    @Test
    void theWarningSaysWhyALinkIsBrokenOrDisagrees() {
        Set<AccountRecord.Key> book = Set.of(MAIN.key(), AT_ONE.key());
        assertEquals(Optional.empty(), AccountsListModel.warning(AT_ONE, book, PC), "MEmu_1 holds main");
        assertEquals(Optional.empty(), AccountsListModel.warning(MAIN, book, PC), "a Google account has no link");

        AccountRecord gone = ANYWHERE.withPlaysAt("this-pc/android/MEmu_9");
        assertEquals(Optional.of("second plays at MEmu_9, which is gone"), AccountsListModel.warning(gone, book, PC));
        AccountRecord odd = ANYWHERE.withPlaysAt("elsewhere/android/x");
        assertEquals(Optional.of("second plays at elsewhere/android/x, which this BotMaker does not read"),
                AccountsListModel.warning(odd, book, PC));
        AccountRecord lost = ANYWHERE.withSignsInWith(Optional.of(FARM.key()));
        assertEquals(Optional.of("second signs in with farm@example.com, which the book no longer has"),
                AccountsListModel.warning(lost, book, PC));
        AccountRecord other = AT_ONE.withSignsInWith(Optional.of(FARM.key()));
        assertEquals(Optional.of("MEmu_1 last read main@example.com; first signs in with farm@example.com"),
                AccountsListModel.warning(other, Set.of(MAIN.key(), FARM.key()), PC));

        assertEquals(Map.of(gone.key(), "second plays at MEmu_9, which is gone"),
                AccountsListModel.warnings(List.of(MAIN, AT_ONE, gone), PC));
    }

    @Test
    void thePicksListThisPcsPlacesAndTheBooksAccountsAndKeepALinkGone() {
        assertEquals(List.of("", "this-pc/android/MEmu_1", "this-pc/android/MEmu_2", "this-pc/desktop",
                "this-pc/vm/Win11"), AccountsListModel.placeChoices(PC, "this-pc/android/MEmu_1"));
        assertEquals("this-pc/android/MEmu_9",
                AccountsListModel.placeChoices(PC, "this-pc/android/MEmu_9").getLast(), "shown as it is");

        Optional<AccountRecord.Key> gone = Optional.of(new AccountRecord.Key(AccountRecord.Kind.GOOGLE, "x@y.z"));
        assertEquals(List.of(Optional.empty(), Optional.of(MAIN.key()), Optional.of(FB.key()), gone),
                AccountsListModel.signInChoices(List.of(MAIN, AT_ONE, FB), gone), "never a launch target's");
    }

    @Test
    void aNameIsRefusedBlankOrTakenWithinItsLaunchTarget() {
        List<AccountRecord> book = List.of(AT_ONE, ANYWHERE, STEAM);
        assertEquals(Optional.of("An account has a name."), AccountsListModel.nameRefused(GAME, " ", null, book));
        assertEquals(Optional.of(GAME + " has an account called Second already."),
                AccountsListModel.nameRefused(GAME, "Second", AT_ONE.key(), book));
        assertEquals(Optional.empty(), AccountsListModel.nameRefused(GAME, "First", AT_ONE.key(), book),
                "its own name, in another case");
        assertEquals(Optional.empty(), AccountsListModel.nameRefused(GAME, "alt", null, book), "steam's alt");
    }

    @Test
    void aLaunchTargetsPasswordIsTheSecretItsAccountNames() {
        assertEquals("launch-target:emu-app:com.game:second", AccountsListModel.secret(ANYWHERE).key());
        List<AccountsListModel.Row> rows = AccountsListModel.rows(List.of(ANYWHERE, AT_ONE), Map.of(), Set.of(),
                Set.of("launch-target:emu-app:com.game:second"));
        assertEquals(List.of(AccountsListModel.Password.NOT_KEPT, AccountsListModel.Password.KEPT),
                rows.stream().map(AccountsListModel.Row::password).toList());
        assertEquals(2, AccountsListModel.secrets(List.of(MAIN, ANYWHERE, AT_ONE)).size());
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
