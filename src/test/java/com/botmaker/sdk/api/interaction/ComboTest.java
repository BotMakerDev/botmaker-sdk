package com.botmaker.sdk.api.interaction;

import org.junit.jupiter.api.Test;

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
}
