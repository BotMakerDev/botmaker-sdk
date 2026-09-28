package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.interaction.Combo;
import com.botmaker.sdk.api.interaction.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What the key and combination pills say, and what the combination popup opens on. No JavaFX needed. */
class InputEditorTest {

    @Test
    void a_key_pill_shows_the_cap() {
        assertEquals("Page Up", InputEditors.keyPill(TestContexts.typedSlot(Key.class, "Key.PAGE_UP")
                .withValue(Key.PAGE_UP)));
        assertEquals("Choose a key…", InputEditors.keyPill(TestContexts.typedSlot(Key.class, "")));
        assertEquals("someKey", InputEditors.keyPill(TestContexts.typedSlot(Key.class, "someKey")));
    }

    /** A combo the host could not read shows its source and opens empty, never on a guessed chord. */
    @Test
    void a_combo_pill_shows_the_caps_or_the_source_as_written() {
        assertEquals("Ctrl+S", InputEditors.comboPill(TestContexts.typedSlot(Combo.class, "Combo.of(Key.CTRL, Key.S)")
                .withValue(Combo.of(Key.CTRL, Key.S))));
        assertEquals("Combo.of(keys)", InputEditors.comboPill(TestContexts.typedSlot(Combo.class, "Combo.of(keys)")));
        assertEquals(Chord.EMPTY, InputEditors.chordOf(TestContexts.typedSlot(Combo.class, "Combo.of(keys)")));
        assertEquals(Chord.of(Combo.of(Key.CTRL, Key.S)), InputEditors.chordOf(
                TestContexts.typedSlot(Combo.class, "x").withValue(Combo.of(Key.CTRL, Key.S))));
    }

    /**
     * Any combo opens as written since 2026-09-27 — two ordinary keys, modifiers after the key — and only one
     * that repeats a key opens empty, since holding it would make OK silently drop the repeat.
     */
    @Test
    void every_combo_opens_as_written_but_one_that_repeats_a_key() {
        assertEquals(Chord.of(Combo.of(Key.W, Key.D)), InputEditors.chordOf(
                TestContexts.typedSlot(Combo.class, "x").withValue(Combo.of(Key.W, Key.D))));
        assertEquals(java.util.List.of(Key.S, Key.CTRL), InputEditors.chordOf(
                TestContexts.typedSlot(Combo.class, "x").withValue(Combo.of(Key.S, Key.CTRL))).keys());
        assertEquals(Chord.EMPTY, InputEditors.chordOf(
                TestContexts.typedSlot(Combo.class, "x").withValue(Combo.of(Key.CTRL, Key.CTRL, Key.S))));
    }

    /** A held combo opens on its keys; the hold is a separate field, so it is no reason to open empty. */
    @Test
    void a_held_combo_opens_on_its_keys_and_its_pill_says_the_hold() {
        Combo held = Combo.of(Key.CTRL, Key.S).held(java.time.Duration.ofMillis(200));
        assertEquals(Chord.of(Combo.of(Key.CTRL, Key.S)), InputEditors.chordOf(
                TestContexts.typedSlot(Combo.class, "x").withValue(held)));
        assertEquals("Ctrl+S (hold 200 ms)", InputEditors.comboPill(
                TestContexts.typedSlot(Combo.class, "x").withValue(held)));
    }

    @Test
    void a_hold_is_read_as_whole_milliseconds() {
        assertEquals(java.time.Duration.ofMillis(200), InputEditors.holdOf("200"));
        assertEquals(java.time.Duration.ofMillis(200), InputEditors.holdOf(" 200 ms "));
        assertEquals(java.time.Duration.ZERO, InputEditors.holdOf(""));
        assertEquals(null, InputEditors.holdOf("-5"));
        assertEquals(null, InputEditors.holdOf("abc"));
    }

    @Test
    void a_step_dragged_onto_another_takes_its_place() {
        assertEquals(java.util.List.of("b", "a", "c"), InputEditors.moved(java.util.List.of("a", "b", "c"), 0, 1));
        assertEquals(java.util.List.of("c", "a", "b"), InputEditors.moved(java.util.List.of("a", "b", "c"), 2, 0));
        assertEquals(java.util.List.of("a", "b", "c"), InputEditors.moved(java.util.List.of("a", "b", "c"), 5, 0));
    }

    @Test
    void a_sequence_pill_shows_its_steps_or_the_source_as_written() {
        com.botmaker.sdk.api.interaction.KeySequence sequence = com.botmaker.sdk.api.interaction.KeySequence.of(
                com.botmaker.sdk.api.interaction.KeySequence.step(Combo.of(Key.CTRL, Key.A),
                        java.time.Duration.ofMillis(100)),
                com.botmaker.sdk.api.interaction.KeySequence.step(Combo.of(Key.CTRL, Key.C),
                        java.time.Duration.ZERO));
        assertEquals("Ctrl+A → 100 ms → Ctrl+C", InputEditors.sequencePill(TestContexts.typedSlot(
                com.botmaker.sdk.api.interaction.KeySequence.class, "x").withValue(sequence)));
        assertEquals("steps", InputEditors.sequencePill(TestContexts.typedSlot(
                com.botmaker.sdk.api.interaction.KeySequence.class, "steps")));
        assertEquals("Choose key steps…", InputEditors.sequencePill(TestContexts.typedSlot(
                com.botmaker.sdk.api.interaction.KeySequence.class, "")));
    }

    /** A pad or a mouse selects nothing for a value it cannot read, so the value is shown as written. */
    @Test
    void a_shape_that_selects_nothing_shows_the_value_as_written() {
        Class<?> direction = com.botmaker.sdk.api.geometry.Direction.class;
        assertEquals("heading", InputEditors.unreadSource(TestContexts.typedSlot(direction, "heading"), direction));
        assertEquals(null, InputEditors.unreadSource(TestContexts.typedSlot(direction, ""), direction));
        assertEquals(null, InputEditors.unreadSource(TestContexts.typedSlot(direction, "Direction.NORTH")
                .withValue(com.botmaker.sdk.api.geometry.Direction.NORTH), direction));
    }
}
