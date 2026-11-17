// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.sdk.plugin.emulator.accounts.AddAccount.Form;
import com.botmaker.sdk.plugin.emulator.accounts.AddAccount.Result;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import com.botmaker.shared.emulator.google.AuthenticatorKeys;
import com.botmaker.shared.emulator.google.PasswordStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** <i>Add account…</i>: secrets into the store, the account into the book, no instance, every array cleared. */
class AddAccountTest {

    /** A credential store in memory, keyed as the real one. */
    private static final class Store implements PasswordStore {
        final Map<String, char[]> kept = new HashMap<>();
        boolean available = true;
        boolean refuses;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public Optional<char[]> read(String email) {
            char[] password = kept.get(email.strip().toLowerCase(Locale.ROOT));
            return password == null ? Optional.empty() : Optional.of(password.clone());
        }

        @Override
        public boolean remember(String email, char[] password) {
            if (refuses) return false;
            kept.put(email.strip().toLowerCase(Locale.ROOT), password.clone());
            return true;
        }

        @Override
        public void forget(String email) {
            kept.remove(email.strip().toLowerCase(Locale.ROOT));
        }
    }

    /** The book in memory, each verification noted. */
    private static final class Book implements AddAccount.Book {
        final List<AccountRecord> accounts = new ArrayList<>();
        boolean fails;

        @Override
        public List<AccountRecord> list() {
            return List.copyOf(accounts);
        }

        @Override
        public void add(AccountRecord account) throws IOException {
            if (fails) throw new IOException("disk full");
            accounts.add(account);
        }

        @Override
        public void verify(AccountRecord.Key key, Verification method) {
            accounts.replaceAll(account -> account.key().equals(key) ? account.withVerify(method) : account);
        }
    }

    private final Store passwords = new Store();
    private final Store keyStore = new Store();
    private final AuthenticatorKeys keys = new AuthenticatorKeys(keyStore);
    private final Book book = new Book();

    @Test
    void addsThenUpdatesOneAccountWhateverItsCase() throws Exception {
        assertEquals(Result.ADDED, AddAccount.add(book, passwords, keys,
                new Form("farm@gmail.com", "first".toCharArray(), Verification.PHONE, null)));
        assertEquals(Result.UPDATED, AddAccount.add(book, passwords, keys,
                new Form(" Farm@Gmail.com ", "second".toCharArray(), Verification.ASK, null)));
        assertEquals(1, book.accounts.size());
        assertEquals(Verification.ASK, book.accounts.getFirst().verify());
        assertArrayEquals("second".toCharArray(), passwords.read("farm@gmail.com").orElseThrow());
    }

    @Test
    void clearsTheSecretsItIsHanded() throws Exception {
        char[] password = "secret".toCharArray();
        char[] key = "JBSWY3DPEHPK3PXP".toCharArray();
        AddAccount.add(book, passwords, keys, new Form("farm@gmail.com", password, Verification.AUTHENTICATOR, key));
        assertArrayEquals(new char[password.length], password);
        assertArrayEquals(new char[key.length], key);
        assertTrue(keys.has("farm@gmail.com"));
    }

    @Test
    void notAnAddressKeepsNothing() throws Exception {
        char[] password = "secret".toCharArray();
        assertEquals(Result.INVALID_EMAIL, AddAccount.add(book, passwords, keys,
                new Form("nobody", password, Verification.PHONE, null)));
        assertTrue(book.accounts.isEmpty());
        assertTrue(passwords.kept.isEmpty());
        assertArrayEquals(new char[password.length], password, "cleared even when refused");
    }

    @Test
    void noStoreKeepsNothing() throws Exception {
        passwords.available = false;
        assertEquals(Result.NO_KEYRING, AddAccount.add(book, passwords, keys,
                new Form("farm@gmail.com", "secret".toCharArray(), Verification.PHONE, null)));
        assertTrue(book.accounts.isEmpty());
    }

    @Test
    void aBadKeyKeepsNothing() throws Exception {
        assertEquals(Result.NOT_A_KEY, AddAccount.add(book, passwords, keys,
                new Form("farm@gmail.com", "secret".toCharArray(), Verification.AUTHENTICATOR, "nope".toCharArray())));
        assertTrue(book.accounts.isEmpty());
        assertTrue(passwords.kept.isEmpty());
    }

    @Test
    void aRefusedPasswordForgetsTheKeyAndLeavesTheBook() throws Exception {
        passwords.refuses = true;
        assertEquals(Result.REFUSED, AddAccount.add(book, passwords, keys,
                new Form("farm@gmail.com", "secret".toCharArray(), Verification.AUTHENTICATOR,
                        "JBSWY3DPEHPK3PXP".toCharArray())));
        assertFalse(keys.has("farm@gmail.com"));
        assertTrue(book.accounts.isEmpty());
    }

    @Test
    void anAliasIsTheAccountItBelongsTo() throws Exception {
        book.accounts.add(new AccountRecord(AccountRecord.Kind.GOOGLE, "main@gmail.com", "", List.of("alt@gmail.com"),
                Verification.DEFAULT));
        assertEquals(Result.UPDATED, AddAccount.add(book, passwords, keys,
                new Form("Alt@gmail.com", "secret".toCharArray(), Verification.CODE, null)));
        assertEquals(1, book.accounts.size());
        assertEquals(Verification.CODE, book.accounts.getFirst().verify());
        assertTrue(passwords.read("main@gmail.com").isPresent(), "kept under the main address");
    }

    @Test
    void aBookThatFailsPutsThePasswordBack() {
        book.fails = true;
        passwords.remember("farm@gmail.com", "old".toCharArray());
        assertThrows(IOException.class, () -> AddAccount.add(book, passwords, keys, new Form("farm@gmail.com",
                "new".toCharArray(), Verification.AUTHENTICATOR, "JBSWY3DPEHPK3PXP".toCharArray())));
        assertArrayEquals("old".toCharArray(), passwords.read("farm@gmail.com").orElseThrow());
        assertFalse(keys.has("farm@gmail.com"), "a key the account lacked is forgotten again");
    }

    @Test
    void theAuthenticatorWantsAKeyGivenOrKept() throws Exception {
        assertEquals(Result.NOT_A_KEY, AddAccount.add(book, passwords, keys,
                new Form("farm@gmail.com", "secret".toCharArray(), Verification.AUTHENTICATOR, null)));
        assertTrue(book.accounts.isEmpty());
        AddAccount.add(book, passwords, keys, new Form("farm@gmail.com", "secret".toCharArray(),
                Verification.AUTHENTICATOR, "JBSWY3DPEHPK3PXP".toCharArray()));
        assertEquals(Result.UPDATED, AddAccount.add(book, passwords, keys,
                new Form("farm@gmail.com", "again".toCharArray(), Verification.AUTHENTICATOR, null)));
    }

    @Test
    void leavingTheAuthenticatorForgetsItsKey() throws Exception {
        AddAccount.add(book, passwords, keys, new Form("farm@gmail.com", "secret".toCharArray(),
                Verification.AUTHENTICATOR, "JBSWY3DPEHPK3PXP".toCharArray()));
        AddAccount.add(book, passwords, keys,
                new Form("farm@gmail.com", "secret".toCharArray(), Verification.PHONE, null));
        assertFalse(keys.has("farm@gmail.com"));
    }

    @Test
    void resultsAreAClosedSet() {
        assertEquals(Result.ADDED, Result.fromId("added"));
        assertEquals(Result.UNKNOWN, Result.fromId("nope"));
        assertTrue(Arrays.stream(Result.values()).filter(Result::kept).count() == 2);
    }
}
