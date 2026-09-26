package com.botmaker.sdk.plugin.types;

import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboTypeTest {

    private final SdkTypes.ComboType type = new SdkTypes.ComboType();

    @Test
    void a_combo_is_written_as_the_varargs_factory() {
        Method factory = (Method) type.factory();
        assertEquals("of", factory.getName());
        assertTrue(factory.isVarArgs());
        assertEquals(List.of(Key.class), type.componentTypes());
    }

    @Test
    void its_parts_build_it_back() {
        Combo combo = Combo.of(Key.CTRL, Key.SHIFT, Key.S);
        assertEquals(combo, type.build(type.components(combo)));
    }

    @Test
    void parts_that_are_not_keys_build_nothing() {
        assertNull(type.build(List.of(Key.CTRL, "S")));
        assertNull(type.build(List.of()));
    }

    @Test
    void a_fresh_combo_is_ctrl_s() {
        assertEquals(Combo.of(Key.CTRL, Key.S), type.fresh());
    }
}
