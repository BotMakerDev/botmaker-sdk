// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.plugin.emulator.host.HostBook;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import com.botmaker.shared.emulator.google.AuthenticatorKeys;
import com.botmaker.shared.emulator.google.PasswordStore;
import com.botmaker.shared.emulator.google.Totp;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A {@code GoogleAccount} picker's <i>Add account…</i> (2026-11-17, spec {@code 2026-11-17-google-sign-in-map-design.md}
 * §1): the account into this PC's book with its second step, its password and, for the authenticator, its setup key
 * into the credential store. No instance is touched: signing it in is a bot's work. Blocks on the store, so never on
 * the FX thread. Every secret array it is handed is cleared, whatever it answers.
 */
public final class AddAccount {

    /** An address as Google takes one: something, an at sign, a domain with a dot, and no space. */
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** What an add did. */
    public enum Result {
        /** The book had no such account; it has it now. */
        ADDED("added"),
        /** The book had it: its second step and secrets are the form's now. */
        UPDATED("updated"),
        /** Not an address. Nothing was kept. */
        INVALID_EMAIL("invalid-email"),
        /** This PC has no credential store that answers. Nothing was kept. */
        NO_KEYRING("no-keyring"),
        /** The authenticator's setup key does not read as one, or none is given nor kept. Nothing was kept. */
        NOT_A_KEY("not-a-key"),
        /** The store refused the password or the key. Nothing was kept. */
        REFUSED("refused"),
        UNKNOWN("unknown");

        private final String id;

        Result(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** Whether the account is in the book with its secrets. */
        public boolean kept() {
            return this == ADDED || this == UPDATED;
        }

        public static Result fromId(String id) {
            return Arrays.stream(values()).filter(result -> result.id.equals(id)).findFirst().orElse(UNKNOWN);
        }
    }

    /**
     * What the user typed: {@code key} is the authenticator's setup key, read only when {@code verify} is
     * {@link Verification#AUTHENTICATOR}, and may be null otherwise.
     */
    public record Form(String email, char[] password, Verification verify, char[] key) {
        public Form {
            email = email == null ? "" : email.strip();
            verify = verify == null ? Verification.DEFAULT : verify;
        }

        /** Clears the secrets. */
        void clear() {
            if (password != null) Arrays.fill(password, '\0');
            if (key != null) Arrays.fill(key, '\0');
        }
    }

    /** The book an add writes: {@link HostBook}'s, or a test's. */
    public interface Book {
        List<AccountRecord> list() throws IOException;

        void add(AccountRecord account) throws IOException;

        void verify(AccountRecord.Key key, Verification method) throws IOException;
    }

    private AddAccount() {}

    /** This PC's book, as {@code services} holds it. */
    public static Book book(StudioServices services) {
        HostBook book = HostConfig.accounts(services);
        return new Book() {
            @Override
            public List<AccountRecord> list() throws IOException {
                return book.known(HostConfig.load(services).config());
            }

            @Override
            public void add(AccountRecord account) throws IOException {
                book.add(account);
            }

            @Override
            public void verify(AccountRecord.Key key, Verification method) throws IOException {
                book.verify(key, method);
            }
        };
    }

    /**
     * Checks the whole form first, keeps the password, then the key, then writes the book, the account found by any of
     * its addresses. A later step that fails puts the earlier ones back: the password as it was, a key the account had
     * not forgotten again. One loss stays: a key that replaced an older one is not restored, the store giving no key
     * back. An account no longer on the authenticator has its key forgotten.
     */
    public static Result add(Book book, PasswordStore passwords, AuthenticatorKeys keys, Form form) throws IOException {
        Optional<char[]> before = Optional.empty();
        try {
            if (!EMAIL.matcher(form.email()).matches()) return Result.INVALID_EMAIL;
            if (!passwords.available()) return Result.NO_KEYRING;
            if (form.password() == null || form.password().length == 0) return Result.REFUSED;
            boolean authenticator = form.verify() == Verification.AUTHENTICATOR;
            boolean withKey = authenticator && form.key() != null && form.key().length > 0;
            if (withKey) {
                Optional<char[]> canonical = Totp.canonical(form.key());
                if (canonical.isEmpty()) return Result.NOT_A_KEY;
                Arrays.fill(canonical.get(), '\0');
            } else if (authenticator && !keys.has(form.email())) {
                return Result.NOT_A_KEY;
            }
            Optional<AccountRecord> listed = book.list().stream()
                    .filter(account -> account.answersTo(form.email()))
                    .findFirst();
            String address = listed.map(AccountRecord::name).orElse(form.email());
            boolean hadKey = keys.has(address);

            before = passwords.read(address);
            if (!passwords.remember(address, form.password())) return Result.REFUSED;
            if (withKey && keys.put(address, form.key()) != AuthenticatorKeys.Put.KEPT) {
                restore(passwords, address, before);
                return Result.REFUSED;
            }
            try {
                if (listed.isPresent()) book.verify(listed.get().key(), form.verify());
                else book.add(new AccountRecord(AccountRecord.Kind.GOOGLE, address).withVerify(form.verify()));
            } catch (IOException | RuntimeException e) {
                restore(passwords, address, before);
                if (withKey && !hadKey) keys.forget(address);
                throw e;
            }
            if (!authenticator && hadKey) keys.forget(address);
            return listed.isPresent() ? Result.UPDATED : Result.ADDED;
        } finally {
            form.clear();
            before.ifPresent(password -> Arrays.fill(password, '\0'));
        }
    }

    /** Puts back the password {@code address} had, or none. */
    private static void restore(PasswordStore passwords, String address, Optional<char[]> before) {
        if (before.isPresent()) passwords.remember(address, before.get());
        else passwords.forget(address);
    }
}
