// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.run.PluginPool;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.api.signin.Account;
import com.botmaker.sdk.internal.config.PlaceKeys;
import com.botmaker.shared.emulator.config.AccountRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The SDK's account pool, dealt after the place (claimed accounts spec § Dealing, run-places phase 7c). */
class AccountPoolTest {

    private static final String GAME = "emu-app:com.game";
    private static final Place ONE = Place.android("MEmu_1");
    private static final Place TWO = Place.android("MEmu_2");
    /** Two instances, each holding one Google account when last read. */
    private static final AccountPool.Pc PC = new AccountPool.Pc(Set.of("MEmu_1", "MEmu_2"),
            Map.of("MEmu_1", Set.of("One@gmail.com"), "MEmu_2", Set.of("two@gmail.com")));

    private static final Account AT_ONE = Account.of(GAME, "first").playsAt(ONE);
    private static final Account WITH_TWO = Account.of(GAME, "second").signsInWith(Account.google("two@gmail.com"));
    private static final Account UNLINKED = Account.of(GAME, "third");

    /**
     * The dealer's walk over this one pool: each place in order, dealt the first account left for it that no row before
     * it holds, no account shared by all; a place with none left is no row.
     */
    private static Map<Place, Account> dealt(List<Place> places, List<Account> candidates, AccountPool.Pc pc) {
        Map<Place, Account> rows = new LinkedHashMap<>();
        List<Account> free = new ArrayList<>(candidates);
        for (Place place : places) {
            List<Account> left = AccountPool.left(free, Optional.of(place), pc);
            if (left.isEmpty()) continue;
            Account pick = left.getFirst();
            rows.put(place, pick);
            if (AccountPool.runsOf(pick) == 1) free.remove(pick);
        }
        return rows;
    }

    @Test
    void eachPlaceIsDealtTheAccountLinkedToItAndTheRowsAreThoseComplete() {
        // the unlinked account first in the book: still the linked ones go where they are linked
        List<Account> book = List.of(UNLINKED, WITH_TWO, AT_ONE);
        assertEquals(Map.of(ONE, AT_ONE, TWO, WITH_TWO), dealt(List.of(ONE, TWO), book, PC));
        assertEquals(Map.of(TWO, WITH_TWO, ONE, AT_ONE), dealt(List.of(TWO, ONE), book, PC));

        Place three = Place.android("MEmu_3");
        AccountPool.Pc more = new AccountPool.Pc(Set.of("MEmu_1", "MEmu_2", "MEmu_3"), PC.reads());
        Map<Place, Account> rows = dealt(List.of(ONE, TWO, three, Place.android("MEmu_4")), book, more);
        assertEquals(3, rows.size(), "three accounts, three rows: the fourth place has none left");
        assertEquals(UNLINKED, rows.get(three));
    }

    @Test
    void anAccountThatPlaysAtAnotherPlaceIsLeftOutAndOneOfNoPlaceGoesAnywhere() {
        assertEquals(List.of(WITH_TWO, UNLINKED), AccountPool.left(List.of(AT_ONE, WITH_TWO, UNLINKED),
                Optional.of(TWO), PC));
        assertEquals(List.of(AT_ONE, UNLINKED, WITH_TWO), AccountPool.left(List.of(AT_ONE, WITH_TWO, UNLINKED),
                Optional.of(ONE), PC), "the account another instance signed in is left for it, last");
        assertEquals(List.of(UNLINKED), AccountPool.left(List.of(AT_ONE, UNLINKED), Optional.of(Place.desktop()), PC));
        assertEquals(List.of(UNLINKED, AT_ONE), AccountPool.left(List.of(AT_ONE, UNLINKED), Optional.empty(), PC),
                "no place dealt: those of no place first");
    }

    @Test
    void linksThatDisagreeAreDealtAsPlaysAtSaysWithTheNoteWhy() {
        Account main = Account.of(GAME, "main").signsInWith(Account.google("main@gmail.com")).playsAt(ONE);
        AccountPool.Pc other = new AccountPool.Pc(Set.of("MEmu_1"), Map.of("MEmu_1", Set.of("other@gmail.com")));

        assertEquals(Map.of(ONE, main), dealt(List.of(ONE), List.of(UNLINKED, main), other));
        assertEquals(Optional.of("MEmu_1 last read other@gmail.com; main signs in with main@gmail.com"),
                AccountPool.note(main, Optional.of(ONE), other));
        AccountPool.Pc empty = new AccountPool.Pc(Set.of("MEmu_1"), Map.of("MEmu_1", Set.of()));
        assertEquals(Optional.of("MEmu_1 last read no Google account; main signs in with main@gmail.com"),
                AccountPool.note(main, Optional.of(ONE), empty));
        AccountPool.Pc agreed = new AccountPool.Pc(Set.of("MEmu_1"), Map.of("MEmu_1", Set.of("Main@gmail.com")));
        assertEquals(Optional.empty(), AccountPool.note(main, Optional.of(ONE), agreed));
        assertEquals(Optional.empty(), AccountPool.note(main, Optional.of(ONE),
                new AccountPool.Pc(Set.of("MEmu_1"), Map.of())), "never read: nothing to disagree with");
        assertEquals(Optional.empty(), AccountPool.note(UNLINKED, Optional.of(ONE), other));
    }

    @Test
    void aPlaysAtNamingAnInstanceGoneIsDealtAsUnlinkedAndSaysSo() {
        Account lost = Account.of(GAME, "lost").playsAt(Place.android("MEmu_9"));

        assertEquals(List.of(lost), AccountPool.left(List.of(lost), Optional.of(ONE), PC));
        assertEquals(Optional.of("lost plays at MEmu_9, which is gone"), AccountPool.note(lost, Optional.of(ONE), PC));
    }

    @Test
    void theCandidatesAreTheBooksAccountsForTheBotsGamesElseNoAccountWhichEveryRunShares() {
        AccountRecord kept = AccountRecord.ofLaunchTarget(GAME, "main", "farm")
                .withSignsInWith(Optional.of(new AccountRecord.Key(AccountRecord.Kind.GOOGLE, "Main@gmail.com")))
                .withPlaysAt("this-pc/android/MEmu_1");
        List<AccountRecord> book = List.of(new AccountRecord(AccountRecord.Kind.GOOGLE, "main@gmail.com"), kept,
                AccountRecord.ofLaunchTarget("steam:1", "alt", ""));

        assertEquals(List.of(Account.of(GAME, "main").note("farm").signsInWith(Account.google("main@gmail.com"))
                .playsAt(ONE)), AccountPool.candidates(book, List.of(GAME)));
        assertEquals(List.of(Account.none()), AccountPool.candidates(book, List.of("emu-app:com.other")));
        assertEquals(PluginPool.Sharing.ANY, SdkPools.ACCOUNTS.sharedBy(Account.none()));
        assertEquals(1, SdkPools.ACCOUNTS.sharedBy(Account.of(GAME, "main")));
        assertTrue(AccountPool.left(List.of(Account.none()), Optional.of(ONE), PC).contains(Account.none()));
    }

    @Test
    void aPlaysAtThatDoesNotReadIsNone() {
        AccountRecord odd = AccountRecord.ofLaunchTarget(GAME, "main", "").withPlaysAt("elsewhere/android/x");
        assertEquals(Optional.empty(), AccountPool.account(odd).playsAt());
        AccountRecord blank = AccountRecord.ofLaunchTarget(GAME, "main", "").withPlaysAt("this-pc/android/");
        assertEquals(Optional.empty(), AccountPool.account(blank).playsAt());
    }

    @Test
    void aTargetOnAnInstanceIsTheSameGameAsWithout() {
        List<AccountRecord> book = List.of(AccountRecord.ofLaunchTarget(GAME + "@MEmu_1", "main", ""),
                AccountRecord.ofLaunchTarget(" " + GAME, "alt", ""));

        assertEquals(List.of(Account.of(GAME + "@MEmu_1", "main"), Account.of(" " + GAME, "alt")),
                AccountPool.candidates(book, List.of(GAME + "@MEmu_2")));
    }

    @Test
    void aPlaysAtNamingAGameVmGoneIsDealtAsUnlinkedAndSaysSo() {
        Account onVm = Account.of(GAME, "vm").playsAt(Place.vm("Win11"));
        AccountPool.Pc withVm = new AccountPool.Pc(Set.of("MEmu_1"), Set.of("Win11"), Map.of());
        AccountPool.Pc noVm = new AccountPool.Pc(Set.of("MEmu_1"), Map.of());

        assertEquals(List.of(onVm), AccountPool.left(List.of(onVm), Optional.of(Place.vm("Win11")), withVm));
        assertEquals(List.of(), AccountPool.left(List.of(onVm), Optional.of(ONE), withVm));
        assertEquals(List.of(onVm), AccountPool.left(List.of(onVm), Optional.of(ONE), noVm));
        assertEquals(Optional.of("vm plays at " + PlaceKeys.said(Place.vm("Win11")) + ", which is gone"),
                AccountPool.note(onVm, Optional.of(ONE), noVm));
    }
}
