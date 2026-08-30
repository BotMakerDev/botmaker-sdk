package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The colour editor's two ends: what it writes into each of the two places a value lives, and what it reads
 * back out of them.
 *
 * <p>Both halves are worth pinning because both were previously done twice. Studio wrote a colour into a slot
 * from {@code ColorArgPicker} and into a Parameters row from {@code ValueEditors.ColorRow}, with two readers
 * to match; this editor is the single one that replaced them on 2026-08-30, and the round trip has to keep
 * meaning the same thing in both directions or a value picked in one window reads as something else in the
 * other.
 *
 * <p>No JavaFX toolkit is needed for any of it, which is what {@code commit}/{@code current} being separable
 * from the widget buys.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ColorEditorTest {

    @Test
    void a_slot_gets_the_constructor_call_and_asks_for_its_import() {
        TestContexts.Recording slot = TestContexts.typedSlot("java.awt.Color", "null");

        ColorEditors.commit(slot, Color.rgb(255, 128, 0));

        assertEquals("new java.awt.Color(255, 128, 0)", slot.replacement());
        assertEquals(List.of("java.awt.Color"), slot.imports(),
                "fully qualified in the expression and named again as the import is the always-safe pair");
    }

    @Test
    void a_row_gets_the_hex_the_project_file_stores() {
        TestContexts.Recording row = TestContexts.row("java.awt.Color", "");

        ColorEditors.commit(row, Color.rgb(255, 128, 0));

        assertEquals(List.of("#FF8000"), row.written());
    }

    /**
     * The components, never {@code Color.decode("#FF8000")}. Decode parses at class-initialisation time and
     * can throw, and a bot must not fail to start over its own configuration — the same rule the SDK's own
     * colour codec follows.
     */
    @Test
    void the_slot_form_is_parsed_numbers_rather_than_text_to_be_decoded() {
        TestContexts.Recording slot = TestContexts.typedSlot("java.awt.Color", "null");

        ColorEditors.commit(slot, Color.BLACK);

        assertEquals("new java.awt.Color(0, 0, 0)", slot.replacement());
    }

    @Test
    void a_slot_that_holds_a_constructor_seeds_the_swatch_from_it() {
        assertEquals(Color.rgb(12, 34, 56),
                ColorEditors.current(TestContexts.typedSlot("java.awt.Color", "new Color(12, 34, 56)")));
        assertEquals(Color.rgb(12, 34, 56),
                ColorEditors.current(TestContexts.typedSlot("java.awt.Color",
                        "new java.awt.Color(12, 34, 56)")));
    }

    /**
     * A slot holding something this editor did not write answers {@code null}, which leaves the swatch at its
     * default rather than claiming a colour. Seeding it from a constant or a variable is not possible, and
     * showing black for {@code Color.RED} would be a lie about what the bot does.
     */
    @Test
    void a_slot_holding_anything_else_claims_no_colour() {
        assertNull(ColorEditors.current(TestContexts.typedSlot("java.awt.Color", "Color.RED")));
        assertNull(ColorEditors.current(TestContexts.typedSlot("java.awt.Color", "healthBarColour")));
        assertNull(ColorEditors.current(TestContexts.typedSlot("java.awt.Color", "")));
    }

    /**
     * A row always answers, because a row always holds text and {@code WireText.color} is total. Unreadable
     * text reads as white in the editor because that is what it reads as in the running bot; the two must not
     * disagree.
     */
    @Test
    void a_row_reads_the_hex_back_and_never_declines() {
        assertEquals(Color.rgb(255, 128, 0),
                ColorEditors.current(TestContexts.row("java.awt.Color", "#FF8000")));
        assertEquals(Color.rgb(255, 128, 0),
                ColorEditors.current(TestContexts.row("java.awt.Color", "FF8000")),
                "a person typing a colour routinely leaves the hash off");
        assertEquals(Color.WHITE, ColorEditors.current(TestContexts.row("java.awt.Color", "not a colour")));
    }

    /** What a pick writes, read straight back, is the colour that was picked — in both places. */
    @Test
    void the_round_trip_holds_in_both_places() {
        TestContexts.Recording slot = TestContexts.typedSlot("java.awt.Color", "null");
        ColorEditors.commit(slot, Color.rgb(9, 200, 77));
        assertEquals(Color.rgb(9, 200, 77),
                ColorEditors.current(TestContexts.typedSlot("java.awt.Color", slot.replacement())));

        TestContexts.Recording row = TestContexts.row("java.awt.Color", "");
        ColorEditors.commit(row, Color.rgb(9, 200, 77));
        assertEquals(Color.rgb(9, 200, 77),
                ColorEditors.current(TestContexts.row("java.awt.Color", row.written().getFirst())));
    }
}
