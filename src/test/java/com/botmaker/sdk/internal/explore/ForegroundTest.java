// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForegroundTest {

    @Test
    void theTargetItselfIsNotForeign() {
        assertFalse(new Foreground("com.android.settings", "com.android.settings").foreign());
    }

    /** The Settings app's search runs in a package under its own: part of the same app, not a window in the way. */
    @Test
    void aSubPackageOfTheTargetIsTheSameApp() {
        assertFalse(new Foreground("com.android.settings.intelligence", "com.android.settings").foreign());
    }

    @Test
    void anotherAppIsForeignEvenWithAKindredName() {
        assertTrue(new Foreground("com.android.chrome", "com.android.settings").foreign());
        // A longer name that merely starts with the target's letters is another app.
        assertTrue(new Foreground("com.android.settingsx", "com.android.settings").foreign());
        // The parent of the target is not the target's app.
        assertTrue(new Foreground("com.android", "com.android.settings").foreign());
    }

    @Test
    void nothingReadIsForeignOnlyWithATarget() {
        assertTrue(new Foreground("", "com.game").foreign());
        assertFalse(Foreground.UNKNOWN.foreign());
    }

    /** A desktop window is named by its label, not a package: only the same label is the target. */
    @Test
    void aWindowLabelIsComparedWhole() {
        assertFalse(new Foreground("Game", "Game").foreign());
        assertTrue(new Foreground("Game launcher", "Game").foreign());
    }
}
