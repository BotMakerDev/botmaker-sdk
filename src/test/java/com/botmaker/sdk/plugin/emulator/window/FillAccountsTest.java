package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.Sourced;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FillAccountsTest {

    private static Sourced<AccountRecord> configured(AccountRecord.Kind kind, String name) {
        return new Sourced<>(new AccountRecord(kind, name, ""), Sourced.Source.EMULATOR);
    }

    @Test
    void theAccountSigningInComesFirstThenTheInstancesThenTheConfigurations() {
        List<FillAccounts.Row> rows = FillAccounts.rows(
                List.of("signing@example.com"),
                Set.of("b.held@example.com", "a.held@example.com"),
                List.of(configured(AccountRecord.Kind.GOOGLE, "wanted@example.com"),
                        configured(AccountRecord.Kind.FACEBOOK, "facebook@example.com"),
                        configured(AccountRecord.Kind.MAIL, "mail@example.com")),
                Set.of());
        assertEquals(List.of("signing@example.com", "a.held@example.com", "b.held@example.com", "wanted@example.com"),
                rows.stream().map(FillAccounts.Row::email).toList(), "Google accounts only, in the panel's order");
        assertEquals(List.of(FillAccounts.Source.SIGNING_IN, FillAccounts.Source.ON_INSTANCE,
                FillAccounts.Source.ON_INSTANCE, FillAccounts.Source.CONFIGURED),
                rows.stream().map(FillAccounts.Row::source).toList());
        assertTrue(rows.get(0).highlighted());
        assertFalse(rows.get(1).highlighted());
    }

    @Test
    void anAccountIsListedOnceUnderItsFirstReason() {
        List<FillAccounts.Row> rows = FillAccounts.rows(
                List.of("User@Example.com"),
                Set.of("user@example.com", "held@example.com"),
                List.of(configured(AccountRecord.Kind.GOOGLE, " user@example.com "),
                        configured(AccountRecord.Kind.GOOGLE, "held@example.com")),
                Set.of());
        assertEquals(List.of("User@Example.com", "held@example.com"), rows.stream().map(FillAccounts.Row::email).toList());
        assertEquals(FillAccounts.Source.ON_INSTANCE, rows.get(1).source());
    }

    @Test
    void aRememberedPasswordIsFilledStraightAwayAndAnotherIsAsked() {
        List<FillAccounts.Row> rows = FillAccounts.rows(List.of("kept@example.com"), Set.of("asked@example.com"),
                List.of(), Set.of("kept@example.com"));
        assertEquals(FillAccounts.PasswordFill.REMEMBERED, rows.get(0).password());
        assertEquals("Fill password", rows.get(0).password().displayName());
        assertEquals(FillAccounts.PasswordFill.ASKED, rows.get(1).password());
        assertEquals("Fill password…", rows.get(1).password().displayName());
    }

    @Test
    void theStoreIsAskedAboutEveryAccountListedOnce() {
        assertEquals(Set.of("user@example.com", "held@example.com", "wanted@example.com"),
                FillAccounts.keys(List.of("User@example.com"), Set.of("held@example.com", "user@example.com"),
                        List.of(configured(AccountRecord.Kind.GOOGLE, "wanted@example.com"),
                                configured(AccountRecord.Kind.FACEBOOK, "facebook@example.com"))));
    }

    @Test
    void nothingToFillListsNothing() {
        assertTrue(FillAccounts.rows(List.of(), Set.of(), List.of(), Set.of()).isEmpty());
    }
}
