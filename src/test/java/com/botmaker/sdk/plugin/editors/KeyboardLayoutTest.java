package com.botmaker.sdk.plugin.editors;

import com.botmaker.sdk.api.interaction.Key;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyboardLayoutTest {

    @Test
    void every_key_the_sdk_has_is_on_the_keyboard_exactly_once() {
        List<Key> caps = KeyboardLayout.rows().stream().flatMap(List::stream)
                .map(KeyboardLayout.Cap::key).filter(Objects::nonNull).toList();
        assertEquals(EnumSet.allOf(Key.class), EnumSet.copyOf(caps), "missing a key");
        assertEquals(caps.size(), new HashSet<>(caps).size(), "a key drawn twice");
    }

    @Test
    void a_search_matches_the_cap_or_the_constant_name() {
        assertEquals(Set.of(Key.PAGE_UP, Key.PAGE_DOWN), KeyboardLayout.matching("page"));
        assertTrue(KeyboardLayout.matching("num 5").contains(Key.NUMPAD_5));
        assertTrue(KeyboardLayout.matching("esc").contains(Key.ESCAPE));
        assertEquals(EnumSet.allOf(Key.class), KeyboardLayout.matching("  "));
    }
}
