// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.signin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A book account named by its email: one account whatever its case, keyed as the credential store keys it. */
class GoogleAccountTest {

    @Test
    void keptAsGivenStrippedAndKeyedLowerCase() {
        GoogleAccount account = GoogleAccount.of(" A.Farm@Gmail.com ");
        assertEquals("A.Farm@Gmail.com", account.email());
        assertEquals("a.farm@gmail.com", account.key());
        assertEquals("A.Farm@Gmail.com", account.toString());
    }

    @Test
    void oneAccountWhateverItsCase() {
        assertEquals(GoogleAccount.of("A@gmail.com"), GoogleAccount.of("a@GMAIL.com"));
        assertEquals(GoogleAccount.of("A@gmail.com").hashCode(), GoogleAccount.of("a@gmail.com").hashCode());
    }

    @Test
    void blankNamesNone() {
        assertTrue(GoogleAccount.of("").isBlank());
        assertTrue(GoogleAccount.of(null).isBlank());
        assertTrue(GoogleAccount.of("  ").isBlank());
        assertFalse(GoogleAccount.of("a@gmail.com").isBlank());
    }
}
