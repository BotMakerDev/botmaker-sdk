// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.secrets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A secret is its name: stripped, keyed lower-cased, never more. */
class SecretTest {

    @Test
    void oneSecretWhateverItsCaseOrSpaces() {
        Secret written = Secret.of("  Farm Password ");
        assertEquals("Farm Password", written.name());
        assertEquals("farm password", written.key());
        assertEquals(Secret.of("farm password"), written);
        assertEquals(Secret.of("farm password").hashCode(), written.hashCode());
        assertNotEquals(Secret.of("farm pin"), written);
    }

    @Test
    void blankNamesNone() {
        assertTrue(Secret.of("").isBlank());
        assertTrue(Secret.of(null).isBlank());
        assertTrue(Secret.of("   ").isBlank());
        assertFalse(Secret.of("pin").isBlank());
    }

    @Test
    void printsItsNameOnly() {
        assertEquals("farm password", Secret.of("farm password").toString());
    }
}
