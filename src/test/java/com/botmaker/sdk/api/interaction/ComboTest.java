package com.botmaker.sdk.api.interaction;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComboTest {

    @Test
    void a_combo_reads_as_its_caps_joined() {
        assertEquals("Ctrl+Shift+S", Combo.of(Key.CTRL, Key.SHIFT, Key.S).toString());
        assertEquals("Ctrl", Combo.of(Key.CTRL).toString());
    }

    @Test
    void the_keys_are_kept_in_the_order_given() {
        assertEquals(List.of(Key.S, Key.CTRL), Combo.of(Key.S, Key.CTRL).keys());
    }

    @Test
    void an_empty_or_null_combo_is_refused() {
        assertThrows(IllegalArgumentException.class, Combo::of);
        assertThrows(NullPointerException.class, () -> Combo.of((Key[]) null));
        assertThrows(NullPointerException.class, () -> Combo.of(Key.CTRL, null));
        assertThrows(IllegalArgumentException.class, () -> new Combo(List.of()));
    }

    @Test
    void the_list_is_a_copy() {
        ArrayList<Key> keys = new ArrayList<>(List.of(Key.CTRL, Key.C));
        Combo combo = new Combo(keys);
        keys.add(Key.V);
        assertEquals(List.of(Key.CTRL, Key.C), combo.keys());
    }

    @Test
    void a_combo_without_a_hold_is_held_for_no_time() {
        assertEquals(Duration.ZERO, Combo.of(Key.CTRL, Key.S).hold());
        assertEquals(Duration.ZERO, new Combo(List.of(Key.CTRL, Key.S)).hold());
    }

    @Test
    void held_is_a_copy_with_the_hold() {
        Combo plain = Combo.of(Key.CTRL, Key.S);
        Combo held = plain.held(Duration.ofMillis(200));
        assertEquals(Duration.ofMillis(200), held.hold());
        assertEquals(plain.keys(), held.keys());
        assertEquals(Duration.ZERO, plain.hold());
        assertEquals(plain, held.held(Duration.ZERO));
    }

    @Test
    void a_hold_reads_after_the_caps() {
        assertEquals("Ctrl+S (hold 200 ms)", Combo.of(Key.CTRL, Key.S).held(Duration.ofMillis(200)).toString());
        assertEquals("Ctrl+S", Combo.of(Key.CTRL, Key.S).held(Duration.ZERO).toString());
    }

    @Test
    void a_null_or_negative_hold_is_refused() {
        assertThrows(NullPointerException.class, () -> Combo.of(Key.CTRL).held(null));
        assertThrows(IllegalArgumentException.class, () -> Combo.of(Key.CTRL).held(Duration.ofMillis(-1)));
    }
}
