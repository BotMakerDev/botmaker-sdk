package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChordTest {

    @Test
    void modifiers_toggle_and_one_other_key_is_kept() {
        Chord chord = Chord.EMPTY.click(Key.SHIFT).click(Key.CTRL).click(Key.S).click(Key.A);
        assertEquals(Optional.of(Combo.of(Key.CTRL, Key.SHIFT, Key.A)), chord.combo());
        assertEquals(Optional.of(Combo.of(Key.CTRL, Key.A)), chord.click(Key.SHIFT).combo());
        assertEquals(Optional.of(Combo.of(Key.CTRL)), chord.click(Key.SHIFT).click(Key.A).combo(),
                "clicking the main key again clears it; modifiers alone are a combo");
    }

    @Test
    void nothing_chosen_is_no_combo() {
        assertTrue(Chord.EMPTY.combo().isEmpty());
        assertTrue(Chord.EMPTY.click(Key.S).click(Key.S).combo().isEmpty());
    }

    @Test
    void a_pressed_chord_is_taken_whole_in_press_order() {
        assertEquals(Optional.of(Combo.of(Key.CTRL, Key.ALT, Key.SHIFT, Key.META, Key.DELETE)),
                Chord.pressed(true, true, true, true, Key.DELETE).combo());
        assertEquals(Optional.of(Combo.of(Key.CTRL)), Chord.pressed(true, false, false, false, Key.CTRL).combo(),
                "pressing Ctrl alone is Ctrl");
    }

    @Test
    void a_combo_opens_as_its_chord() {
        Chord chord = Chord.of(Combo.of(Key.S, Key.CTRL));
        assertEquals(Set.of(Key.CTRL), chord.modifiers());
        assertEquals(Key.S, chord.main());
        assertEquals(Optional.of(Combo.of(Key.CTRL, Key.S)), chord.combo());
    }
}
