// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What code checks before a goal counts as reached. */
class WitnessTest {

    private static Witness.Frame frame(String screen, List<String> texts, Set<String> pictures) {
        return new Witness.Frame("f1", Optional.ofNullable(screen), Set.of("Home", "SignIn")::contains, texts,
                pictures::contains);
    }

    @Test
    void theGoalScreenHoldsOnlyWhenTheFrameIsOnItAndTheMapHasIt() {
        assertTrue(Witness.holds(new Witness.Check.ScreenOnMap("SignIn"), frame("SignIn", List.of(), Set.of())));
        assertFalse(Witness.holds(new Witness.Check.ScreenOnMap("SignIn"), frame("Home", List.of(), Set.of())));
        assertFalse(Witness.holds(new Witness.Check.ScreenOnMap("Shop"), frame("Shop", List.of(), Set.of())),
                "a screen the map lacks is no witness");
        assertFalse(Witness.holds(new Witness.Check.ScreenOnMap("SignIn"), frame(null, List.of(), Set.of())));
    }

    @Test
    void aMarkHoldsByPictureFirstThenByTextAnyCase() {
        assertEquals(Optional.of(WitnessCheck.PICTURE), Witness.how(new Witness.Check.PictureOrText("avatar"),
                frame("Home", List.of("avatar"), Set.of("avatar"))));
        assertEquals(Optional.of(WitnessCheck.TEXT), Witness.how(new Witness.Check.PictureOrText("signed  in"),
                frame("Home", List.of("You are Signed in as Ann"), Set.of())));
        assertEquals(Optional.empty(), Witness.how(new Witness.Check.PictureOrText("signed in"),
                frame("Home", List.of("Sign in"), Set.of())));
        assertEquals(Optional.empty(), Witness.how(new Witness.Check.PictureOrText(" "),
                frame("Home", List.of(" "), Set.of(" "))));
    }
}
