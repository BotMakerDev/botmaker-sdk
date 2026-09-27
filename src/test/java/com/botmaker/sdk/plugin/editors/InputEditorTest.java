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
}
