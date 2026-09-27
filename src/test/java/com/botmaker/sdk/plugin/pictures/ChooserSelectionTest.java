package com.botmaker.sdk.plugin.pictures;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which pictures the chooser selects after a capture, and which it offers at all. */
class ChooserSelectionTest {

    @Test
    void aSinglePictureSlotSelectsTheLastSaved() {
        assertEquals(List.of("gem"), ChooserSelection.afterCapture(List.of("coin", "gem"), false, null));
    }

    @Test
    void aGroupSelectsEverythingSaved() {
        assertEquals(List.of("coin", "gem"), ChooserSelection.afterCapture(List.of("coin", "gem"), true, null));
    }

    @Test
    void nothingSavedSelectsNothing() {
        assertEquals(List.of(), ChooserSelection.afterCapture(List.of(), false, null));
        assertEquals(List.of(), ChooserSelection.afterCapture(List.of(), true, null));
    }

    @Test
    void aCaptureOutsideTheAllowedSetIsNotSelected() {
        assertEquals(List.of("coin"), ChooserSelection.afterCapture(List.of("coin", "gem"), true, List.of("coin")));
        assertEquals(List.of("coin"), ChooserSelection.afterCapture(List.of("coin", "gem"), false, List.of("coin")));
    }

    @Test
    void theAllowedSetDecidesWhatIsPickable() {
        assertTrue(ChooserSelection.pickable("coin", null));
        assertTrue(ChooserSelection.pickable("coin", List.of("coin")));
        assertFalse(ChooserSelection.pickable("gem", List.of("coin")));
    }
}
