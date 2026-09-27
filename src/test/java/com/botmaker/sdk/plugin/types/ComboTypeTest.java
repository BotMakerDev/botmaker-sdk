package com.botmaker.sdk.plugin.types;

import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;
import org.junit.jupiter.api.Test;

import com.botmaker.plugin.api.value.ComponentType;

import java.lang.reflect.Method;
import java.time.Duration;
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

    /** The static factory has no hold: a held combo does not survive it, which is what sends the host to the chain. */
    @Test
    void the_factory_drops_a_hold() {
        Combo held = Combo.of(Key.CTRL, Key.S).held(Duration.ofMillis(200));
        assertEquals(Combo.of(Key.CTRL, Key.S), type.build(type.components(held)));
    }

    @Test
    void a_held_combo_is_the_chain_on_the_combo_without_its_hold() {
        ComponentType<Combo> chain = SdkTypes.COMBO_HELD;
        Method factory = (Method) chain.factory();
        assertEquals("held", factory.getName());
        assertEquals(List.of(Combo.class, Duration.class), chain.componentTypes());
        Combo held = Combo.of(Key.CTRL, Key.S).held(Duration.ofMillis(200));
        assertEquals(List.of(Combo.of(Key.CTRL, Key.S), Duration.ofMillis(200)), chain.components(held));
        assertEquals(held, chain.build(chain.components(held)));
        assertNull(chain.build(List.of(Key.CTRL, Duration.ZERO)));
    }
}
