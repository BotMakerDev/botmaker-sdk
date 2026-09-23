package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

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
 *
 * <h2>It asserted an expression until 2026-09-22, and now asserts the value</h2>
 *
 * <p>Every case here compared {@code slot.written()} against {@code "new java.awt.Color(255, 128, 0)"} — the
 * Java this editor spelled for itself. It spells nothing now: it hands the host a {@code java.awt.Color} and
 * the host writes it through the {@code ComponentType} plugin-basics declares for that type. So the subject
 * is the colour, which is what this editor is actually responsible for; the three components and the fact
 * that they are written rather than {@code Color.decode("#FF8000")} are asserted in plugin-basics, once,
 * beside the declaration that decides it.
 *
 * <p>That is not a weaker test. It is the same property with the second author removed — the drift it was
 * guarding against was between this editor's speller and the codec's, and there is one speller now.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ColorEditorTest {

    /** A slot holding {@code awt}, which is what the host hands an editor once it has decoded one. */
    private static TestContexts.Recording slotHolding(java.awt.Color awt) {
        return TestContexts.typedSlot("java.awt.Color", "new java.awt.Color("
                + awt.getRed() + ", " + awt.getGreen() + ", " + awt.getBlue() + ")").withValue(awt);
    }

    @Test
    void a_slot_gets_the_colour_itself_and_not_an_expression_for_it() {
        TestContexts.Recording slot = TestContexts.typedSlot("java.awt.Color", "null");

        ColorEditors.commit(slot, Color.rgb(255, 128, 0));

        assertEquals(new java.awt.Color(255, 128, 0), slot.value());
    }

    /**
     * A value with no call site gets the same thing a slot does.
     *
     * <p>It got {@code #FF8000} until 2026-09-20, because a Parameters row held stored text rather than
     * Java, and {@code new java.awt.Color(…)} until 2026-09-22, because this editor spelled its own. One
     * answer everywhere means one thing to write and one thing to read back, and it is why
     * {@code WireText.color} — which decoded that text, total, and answered white for anything it could not
     * parse — is no longer in this path.
     */
    @Test
    void a_value_with_no_call_site_gets_the_same_colour() {
        TestContexts.Recording row = TestContexts.row("java.awt.Color", "");

        ColorEditors.commit(row, Color.rgb(255, 128, 0));

        assertEquals(new java.awt.Color(255, 128, 0), row.value());
    }

    /** Each channel is clamped and rounded once, where the colour is made, rather than at each reader. */
    @Test
    void the_channels_are_rounded_to_whole_bytes() {
        TestContexts.Recording slot = TestContexts.typedSlot("java.awt.Color", "null");

        ColorEditors.commit(slot, Color.BLACK);
        assertEquals(new java.awt.Color(0, 0, 0), slot.value());

        ColorEditors.commit(slot, Color.WHITE);
        assertEquals(new java.awt.Color(255, 255, 255), slot.value());
    }

    @Test
    void a_slot_that_holds_a_colour_seeds_the_swatch_from_it() {
        assertEquals(Color.rgb(12, 34, 56),
                ColorEditors.current(slotHolding(new java.awt.Color(12, 34, 56))));
    }

    /**
     * A slot holding something the host could not decode answers {@code null}, which leaves the swatch at
     * its default rather than claiming a colour. Showing black for {@code Color.RED} would be a lie about
     * what the bot does — and a swatch that claimed it would write the claim back on the next redraw.
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
     * was total. A row holds a value now, so the honest answer for {@code Color.RED} is the same one a slot
     * gives: leave the swatch alone rather than claim a colour this editor cannot write back.
     */
    @Test
    void a_value_with_no_call_site_declines_what_it_cannot_write_back() {
        assertEquals(Color.rgb(255, 128, 0),
                ColorEditors.current(TestContexts.row("java.awt.Color", "")
                        .withValue(new java.awt.Color(255, 128, 0))));
        assertNull(ColorEditors.current(TestContexts.row("java.awt.Color", "Color.RED")));
        assertNull(ColorEditors.current(TestContexts.row("java.awt.Color", "")));
    }

    /** What a pick writes, read straight back, is the colour that was picked — wherever the value is. */
    @Test
    void the_round_trip_holds_wherever_the_value_is() {
        TestContexts.Recording slot = TestContexts.typedSlot("java.awt.Color", "null");
        ColorEditors.commit(slot, Color.rgb(9, 200, 77));
        assertEquals(Color.rgb(9, 200, 77), ColorEditors.current(slot));

        TestContexts.Recording row = TestContexts.row("java.awt.Color", "");
        ColorEditors.commit(row, Color.rgb(9, 200, 77));
        assertEquals(Color.rgb(9, 200, 77), ColorEditors.current(row));
    }
}
