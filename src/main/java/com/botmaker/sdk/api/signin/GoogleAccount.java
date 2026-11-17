// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.signin;

import java.util.Locale;

/**
 * A Google account of this PC's account book, by its email ({@code GoogleAccount.of("a@gmail.com")}): a parameter's
 * type, picked from the book and dealt by a pool (2026-11-17, spec
 * {@code docs/superpowers/specs/2026-11-17-google-sign-in-map-design.md} §1). It names the account only: the password
 * and the second step stay with the host, which types them, and never reach the run.
 * {@code GoogleAccount.of("")} names none.
 */
public final class GoogleAccount {

    private final String email;

    private GoogleAccount(String email) {
        this.email = email == null ? "" : email.strip();
    }

    /** The account of {@code email}, stripped; {@code ""} for none. */
    public static GoogleAccount of(String email) {
        return new GoogleAccount(email);
    }

    /** The email as given, stripped; {@code ""} for none. */
    public String email() {
        return email;
    }

    /** What the book and the credential store key the account by: the email lower-cased. */
    public String key() {
        return email.toLowerCase(Locale.ROOT);
    }

    /** Whether this names no account. */
    public boolean isBlank() {
        return email.isEmpty();
    }

    /** Equal by {@link #key()}: one account whatever the case it is written in. */
    @Override
    public boolean equals(Object other) {
        return other instanceof GoogleAccount that && key().equals(that.key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }

    @Override
    public String toString() {
        return email;
    }
}
