package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.toolkit.Types;
import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.KeySequence;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeySequenceTypeTest {

    private final Types.DeclaredCall<KeySequence> type = SdkTypes.KEY_SEQUENCE;
    private final KeySequence copyAll = KeySequence.of(
            KeySequence.step(Combo.of(Key.CTRL, Key.A), Duration.ofMillis(100)),
            KeySequence.step(Combo.of(Key.CTRL, Key.C), Duration.ZERO));

    @Test
    void a_sequence_is_written_as_the_varargs_factory_of_steps() {
        Method factory = (Method) type.factory();
        assertEquals("of", factory.getName());
        assertTrue(factory.isVarArgs());
        assertEquals(List.of(KeySequence.Step.class), type.componentTypes());
    }

    @Test
    void its_steps_build_it_back() {
        assertEquals(copyAll, type.build(type.components(copyAll)));
        assertNull(type.build(List.of()));
        assertNull(type.build(List.of("Ctrl+A")));
    }

    @Test
    void a_step_is_written_as_step_of_a_combo_and_a_wait() {
        Method factory = (Method) SdkTypes.STEP.factory();
        assertEquals("step", factory.getName());
        assertEquals(List.of(Combo.class, Duration.class), SdkTypes.STEP.componentTypes());
        KeySequence.Step step = copyAll.steps().getFirst();
        assertEquals(step, SdkTypes.STEP.build(SdkTypes.STEP.components(step)));
        assertNull(SdkTypes.STEP.build(List.of(Key.A, Duration.ZERO)));
    }

    @Test
    void a_fresh_sequence_is_select_all_then_copy() {
        assertEquals(copyAll, type.fresh());
    }
}
