// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A place a run plays on: equal by kind, PC and name, its key naming its PC, its kind read back by id. */
class PlaceTest {

    @Test
    void aPlaceIsEqualByKindPcAndName() {
        assertEquals(Place.android(" Pixel 10 "), Place.android("Pixel 10"));
        assertEquals(Place.android("Pixel 10").hashCode(), Place.android("Pixel 10").hashCode());
        assertEquals(Place.desktop(), Place.desktop());
        assertEquals(Place.privateDisplay(2), Place.privateDisplay(2));
        assertNotEquals(Place.privateDisplay(1), Place.privateDisplay(2));
        assertNotEquals(Place.android("win11"), Place.vm("win11"), "the kind tells them apart");
        assertEquals(Place.THIS_PC, Place.vm("win11").pc());
        assertEquals(Place.android(""), Place.android(null));
    }

    @Test
    void theKeyNamesThePcTheKindAndTheNameOrNumber() {
        assertEquals("this-pc/android/Pixel 10", Place.android("Pixel 10").toString());
        assertEquals("this-pc/desktop", Place.desktop().toString());
        assertEquals("this-pc/private-display/3", Place.privateDisplay(3).toString());
        assertEquals("this-pc/vm/win11", Place.vm("win11").toString());
        assertEquals("this-pc/android", Place.android("").toString());
    }

    @Test
    void onlyAnAndroidPlaceNamingOneNamesAnInstance() {
        assertTrue(Place.android("Pixel 10").namesAndroid());
        assertFalse(Place.android("").namesAndroid());
        assertFalse(Place.vm("Pixel 10").namesAndroid());
        assertFalse(Place.desktop().namesAndroid());
    }

    @Test
    void aPrivateDisplayIsCountedFromOne() {
        assertEquals(1, Place.privateDisplay(1).display());
        assertEquals(0, Place.desktop().display());
        assertThrows(IllegalArgumentException.class, () -> Place.privateDisplay(0));
    }

    @Test
    void aKindIsReadBackByItsIdAndAnyOtherIsUnknown() {
        for (Place.Kind kind : Place.Kind.values()) assertEquals(kind, Place.Kind.fromId(kind.id()));
        assertEquals(Place.Kind.PRIVATE_DISPLAY, Place.Kind.fromId("private-display"));
        assertEquals(Place.Kind.UNKNOWN, Place.Kind.fromId("phone"));
        assertEquals(Place.Kind.UNKNOWN, Place.Kind.fromId(null));
    }
}
