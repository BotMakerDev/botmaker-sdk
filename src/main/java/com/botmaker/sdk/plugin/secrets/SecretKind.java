// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.secrets;

import java.util.Arrays;
import java.util.List;

/** What a kept secret is, and so what the host types for it (2026-11-17). */
public enum SecretKind {
    /** Typed as kept: a password, a PIN. */
    PASSWORD("password", "Password"),
    /** Keeps an authenticator's setup key; the host types the current code made from it. */
    AUTHENTICATOR("authenticator", "Authenticator"),
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    SecretKind(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** The kinds a user picks from. */
    public static List<SecretKind> kinds() {
        return List.of(PASSWORD, AUTHENTICATOR);
    }

    public static SecretKind fromId(String id) {
        return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst().orElse(UNKNOWN);
    }
}
