// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.secrets;

import java.util.Locale;

/**
 * A secret this PC keeps, by its name ({@code Secret.of("farm password")}): a parameter's type, picked from the
 * secrets BotMaker keeps on this PC and dealt by a pool (2026-11-17, spec
 * {@code docs/superpowers/specs/2026-11-17-google-sign-in-map-design.md} §1). It names the secret only: the value
 * stays in this PC's credential store, the host types it, and it never reaches the run. {@code Secret.of("")} names
 * none.
 */
public final class Secret {

    private final String name;

    private Secret(String name) {
        this.name = name == null ? "" : name.strip();
    }

    /** The secret named {@code name}, stripped; {@code ""} for none. */
    public static Secret of(String name) {
        return new Secret(name);
    }

    /** The name as given, stripped; {@code ""} for none. */
    public String name() {
        return name;
    }

    /** What the credential store keys the secret by: the name lower-cased. */
    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Whether this names no secret. */
    public boolean isBlank() {
        return name.isEmpty();
    }

    /** Equal by {@link #key()}: one secret whatever the case it is written in. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Secret that && key().equals(that.key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }

    /** The name, never a value: a {@code Secret} holds none. */
    @Override
    public String toString() {
        return name;
    }
}
