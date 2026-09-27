package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChordTest {

    @Test
    void clicked_keys_are_kept_in_the_order_clicked_and_a_second_click_takes_one_out() {
        Chord chord = Chord.EMPTY.click(Key.SHIFT).click(Key.CTRL).click(Key.S).click(Key.A);
        assertEquals(Optional.of(Combo.of(Key.SHIFT, Key.CTRL, Key.S, Key.A)), chord.combo(),
                "any number of keys, modifiers anywhere");
        assertEquals(Optional.of(Combo.of(Key.SHIFT, Key.CTRL, Key.A)), chord.click(Key.S).combo());
    }

    @Test
    void nothing_chosen_is_no_combo() {
        assertTrue(Chord.EMPTY.combo().isEmpty());
        assertTrue(Chord.EMPTY.click(Key.S).click(Key.S).combo().isEmpty());
    }

    @Test
    void a_pressed_key_is_appended_with_the_modifiers_held_for_it() {
        Chord chord = Chord.EMPTY.press(true, false, false, false, Key.S);
        assertEquals(Optional.of(Combo.of(Key.CTRL, Key.S)), chord.combo());
        // Recording on: the next key joins the combination, and Ctrl is not written twice.
        assertEquals(Optional.of(Combo.of(Key.CTRL, Key.S, Key.A)),
                chord.press(true, false, false, false, Key.A).combo());
        assertEquals(Optional.of(Combo.of(Key.CTRL, Key.ALT, Key.SHIFT, Key.META, Key.DELETE)),
                Chord.EMPTY.press(true, true, true, true, Key.DELETE).combo(), "held modifiers in press order");
        assertEquals(Optional.of(Combo.of(Key.CTRL)), Chord.EMPTY.press(true, false, false, false, Key.CTRL).combo(),
                "pressing Ctrl alone is Ctrl");
    }

    @Test
    void a_combo_opens_as_its_keys_in_order() {
        Chord chord = Chord.of(Combo.of(Key.S, Key.CTRL, Key.A));
        assertEquals(List.of(Key.S, Key.CTRL, Key.A), chord.keys());
        assertEquals(Optional.of(Combo.of(Key.S, Key.CTRL, Key.A)), chord.combo());
    }

    /** A chip dragged to a new place: the order is the press order (feedback 3). */
    @Test
    void a_key_moves_to_where_it_is_dropped() {
        Chord chord = new Chord(List.of(Key.CTRL, Key.SHIFT, Key.S));
        assertEquals(List.of(Key.S, Key.CTRL, Key.SHIFT), chord.move(2, 0).keys());
        assertEquals(List.of(Key.SHIFT, Key.S, Key.CTRL), chord.move(0, 2).keys());
        assertEquals(chord, chord.move(1, 1));
        assertEquals(chord, chord.move(5, 0), "a place that is not there moves nothing");
        assertEquals(List.of(Key.SHIFT, Key.S, Key.CTRL), chord.move(0, 9).keys(), "past the end is the end");
    }

    @Test
    void a_repeated_key_is_held_once() {
        assertEquals(List.of(Key.A, Key.B), Chord.of(Combo.of(Key.A, Key.B, Key.A)).keys());
    }
}
