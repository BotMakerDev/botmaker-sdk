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

        assertEquals("new java.awt.Color(255, 128, 0)", slot.written());
        assertEquals(List.of("java.awt.Color"), slot.imports(),
                "fully qualified in the expression and named again as the import is the always-safe pair");
    }

    /**
     * A value with no call site gets the same expression a slot does.
     *
     * <p>It got {@code #FF8000} until 2026-09-20, because a Parameters row held stored text rather than
     * Java. One spelling everywhere means one thing to write and one thing to read back, and it is why
     * {@code WireText.color} — which decoded that text, total, and answered white for anything it could not
     * parse — is no longer in this path.
     */
    @Test
    void a_value_with_no_call_site_gets_the_same_expression() {
        TestContexts.Recording row = TestContexts.row("java.awt.Color", "");

        ColorEditors.commit(row, Color.rgb(255, 128, 0));

        assertEquals("new java.awt.Color(255, 128, 0)", row.written());
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

        assertEquals("new java.awt.Color(0, 0, 0)", slot.written());
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
     * A value with no call site declines for the same reasons a slot does.
     *
     * <p>It used to answer white for anything unreadable, because a row held text and {@code WireText.color}
     * was total. A row holds Java now, so the honest answer for {@code Color.RED} is the same one a slot
     * gives: leave the swatch alone rather than claim a colour this editor cannot write back.
     */
    @Test
    void a_value_with_no_call_site_declines_what_it_cannot_write_back() {
        assertEquals(Color.rgb(255, 128, 0),
                ColorEditors.current(TestContexts.row("java.awt.Color", "new java.awt.Color(255, 128, 0)")));
        assertNull(ColorEditors.current(TestContexts.row("java.awt.Color", "Color.RED")));
        assertNull(ColorEditors.current(TestContexts.row("java.awt.Color", "")));
    }

    /** What a pick writes, read straight back, is the colour that was picked — wherever the value is. */
    @Test
    void the_round_trip_holds_wherever_the_value_is() {
        TestContexts.Recording slot = TestContexts.typedSlot("java.awt.Color", "null");
        ColorEditors.commit(slot, Color.rgb(9, 200, 77));
        assertEquals(Color.rgb(9, 200, 77),
                ColorEditors.current(TestContexts.typedSlot("java.awt.Color", slot.written())));

        TestContexts.Recording row = TestContexts.row("java.awt.Color", "");
        ColorEditors.commit(row, Color.rgb(9, 200, 77));
        assertEquals(Color.rgb(9, 200, 77),
                ColorEditors.current(TestContexts.row("java.awt.Color", row.written())));
    }
}
