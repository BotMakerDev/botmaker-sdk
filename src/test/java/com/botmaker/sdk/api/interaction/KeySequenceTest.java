package com.botmaker.sdk.api.interaction;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeySequenceTest {

    private static final Combo SELECT_ALL = Combo.of(Key.CTRL, Key.A);
    private static final Combo COPY = Combo.of(Key.CTRL, Key.C);

    @Test
    void the_steps_are_kept_in_the_order_given() {
        KeySequence sequence = KeySequence.of(
                KeySequence.step(SELECT_ALL, Duration.ofMillis(100)),
                KeySequence.step(COPY, Duration.ZERO));
        assertEquals(List.of(new KeySequence.Step(SELECT_ALL, Duration.ofMillis(100)),
                new KeySequence.Step(COPY, Duration.ZERO)), sequence.steps());
    }

    @Test
    void a_sequence_reads_as_its_combos_and_the_waits_between() {
        assertEquals("Ctrl+A → 100 ms → Ctrl+C", KeySequence.of(
                KeySequence.step(SELECT_ALL, Duration.ofMillis(100)),
                KeySequence.step(COPY, Duration.ZERO)).toString());
        assertEquals("Ctrl+A → Ctrl+C → 50 ms", KeySequence.of(
                KeySequence.step(SELECT_ALL, Duration.ZERO),
                KeySequence.step(COPY, Duration.ofMillis(50))).toString());
    }

    @Test
    void an_empty_sequence_or_a_bad_step_is_refused() {
        assertThrows(IllegalArgumentException.class, KeySequence::of);
        assertThrows(NullPointerException.class, () -> KeySequence.of((KeySequence.Step[]) null));
        assertThrows(NullPointerException.class, () -> KeySequence.step(null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> KeySequence.step(COPY, null));
        assertThrows(IllegalArgumentException.class, () -> KeySequence.step(COPY, Duration.ofMillis(-1)));
    }

    @Test
    void the_list_is_a_copy() {
        List<KeySequence.Step> steps = new ArrayList<>(List.of(KeySequence.step(COPY, Duration.ZERO)));
        KeySequence sequence = new KeySequence(steps);
        steps.add(KeySequence.step(SELECT_ALL, Duration.ZERO));
        assertEquals(1, sequence.steps().size());
    }
}
