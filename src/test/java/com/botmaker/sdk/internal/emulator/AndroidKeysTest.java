// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.emulator;

import com.botmaker.sdk.api.input.Key;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AndroidKeysTest {

    /** Hand walk 2026-11-27: Google sign-in has no close button, only Android's Back leaves it. */
    @Test
    void escapeIsBackAndHomeIsHome() {
        assertEquals(OptionalInt.of(4), AndroidKeys.of(Key.ESCAPE));
        assertEquals(OptionalInt.of(3), AndroidKeys.of(Key.HOME));
        assertEquals(OptionalInt.of(66), AndroidKeys.of(Key.ENTER));
    }

    @Test
    void lettersAndDigitsAreAndroidsOwn() {
        assertEquals(OptionalInt.of(29), AndroidKeys.of(Key.A));
        assertEquals(OptionalInt.of(54), AndroidKeys.of(Key.Z));
        assertEquals(OptionalInt.of(7), AndroidKeys.of(Key.NUM0));
        assertEquals(OptionalInt.of(16), AndroidKeys.of(Key.NUM9));
    }

    @Test
    void aKeyAndroidLacksHasNoCode() {
        assertEquals(OptionalInt.empty(), AndroidKeys.of(Key.F5));
        assertEquals(OptionalInt.empty(), AndroidKeys.of(Key.SHIFT));
        assertEquals(OptionalInt.empty(), AndroidKeys.of(null));
    }
}
