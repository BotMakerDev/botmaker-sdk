package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.vision.Precision;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the strictness editor writes into each of the two places a value lives, and what it reads back out of
 * them.
 *
 * <p>The reading half is the one that has to be right on code nobody generated: a {@code Precision} is spelled
 * as a named anchor, as a factory call, or as either with a chain of withers on the end, and an editor that
 * silently flattened a chain back to its anchor would quietly reset a setting somebody typed by hand. Studio
 * read that off a JDT syntax tree; this reads it off source text, which is what the contract hands a plugin,
 * so the parse is the part of the 2026-08-30 move most worth pinning.
 *
 * <p>No JavaFX toolkit is needed for any of it.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PrecisionEditorTest {

    @Test
    void the_shortest_exact_form_is_committed() {
        // An anchor alone when the quantity gates are the ones the anchor already carries…
        assertEquals("Precision.EXACT", PrecisionEditors.literalFor(0, 4, 0));
        assertEquals("Precision.TIGHT", PrecisionEditors.literalFor(5, 4, 0));
        assertEquals("Precision.DEFAULT", PrecisionEditors.literalFor(12, 4, 0));
        assertEquals("Precision.LOOSE", PrecisionEditors.literalFor(25, 4, 0));
        // …the anchor plus a wither for whatever differs from it…
        assertEquals("Precision.TIGHT.minArea(400)", PrecisionEditors.literalFor(5, 400, 0));
        assertEquals("Precision.DEFAULT.minCount(2000)", PrecisionEditors.literalFor(12, 4, 2000));
        assertEquals("Precision.LOOSE.minArea(1).minCount(2000)", PrecisionEditors.literalFor(25, 1, 2000));
        // …and the factory when the tolerance is off-anchor, three-argument when both gates are non-standard.
        assertEquals("Precision.of(18)", PrecisionEditors.literalFor(18, 4, 0));
        assertEquals("Precision.of(18, 400, 2000)", PrecisionEditors.literalFor(18, 400, 2000));
    }

    @Test
    void every_committed_form_reads_back_as_the_values_it_was_given() {
        assertEquals(new PrecisionEditors.Settings(5.0, 4, 0), PrecisionEditors.settingsOf("Precision.TIGHT"));
        assertEquals(new PrecisionEditors.Settings(5.0, 400, 0),
                PrecisionEditors.settingsOf("Precision.TIGHT.minArea(400)"));
        assertEquals(new PrecisionEditors.Settings(18.0, 400, 2000),
                PrecisionEditors.settingsOf("Precision.of(18, 400, 2000)"));
        assertEquals(new PrecisionEditors.Settings(18.0, 400, 2000),
                PrecisionEditors.settingsOf("Precision.of(18).minArea(400).minCount(2000)"));
        assertEquals(new PrecisionEditors.Settings(3.0, 1, 50),
                PrecisionEditors.settingsOf("Precision.DEFAULT.tolerance(3).minArea(1).minCount(50)"));
    }

    /**
     * A leading package name costs nothing, because the reader applies each dotted segment in turn and simply
     * recognises none of these. A hand-written file may well spell it this way, and so may a file whose
     * {@code Precision} import was shadowed.
     */
    @Test
    void a_fully_qualified_spelling_reads_the_same() {
        assertEquals(new PrecisionEditors.Settings(25.0, 40, 0),
                PrecisionEditors.settingsOf("com.botmaker.sdk.api.vision.Precision.LOOSE.minArea(40)"));
    }

    /**
     * A dot inside an argument belongs to that argument. The chain walk splits at top-level dots only, so an
     * off-anchor tolerance survives — and it is the one value a naive split would turn into two segments and
     * then fail to read, silently resetting the setting to {@code DEFAULT}.
     */
    @Test
    void a_decimal_tolerance_is_not_mistaken_for_a_link_in_the_chain() {
        assertEquals(new PrecisionEditors.Settings(12.5, 4, 0), PrecisionEditors.settingsOf("Precision.of(12.5)"));
        assertEquals(new PrecisionEditors.Settings(7.5, 400, 0),
                PrecisionEditors.settingsOf("Precision.of(7.5).minArea(400)"));
    }

    /**
     * Something the editor cannot read falls back to the SDK's own {@code DEFAULT} rather than to zeroes,
     * which would be a tolerance of "exact" and an area of "any" — the two most damaging values to invent.
     */
    @Test
    void anything_unreadable_reads_as_the_sdk_default() {
        assertEquals(new PrecisionEditors.Settings(12.0, 4, 0), PrecisionEditors.settingsOf("someVariable"));
        assertEquals(new PrecisionEditors.Settings(12.0, 4, 0), PrecisionEditors.settingsOf(""));
        assertEquals(new PrecisionEditors.Settings(12.0, 4, 0), PrecisionEditors.settingsOf("config.precision()"));
    }

    @Test
    void only_the_knobs_the_call_can_act_on_are_offered() {
        // The SDK collapsed colour and quantity into one type, which means some calls are handed fields with
        // no effect. Their javadoc says so; this is where it becomes something the user cannot get wrong.
        assertTrue(PrecisionEditors.knobsFor("matchesAt").tolerance());
        assertFalse(PrecisionEditors.knobsFor("matchesAt").quantity());
        assertFalse(PrecisionEditors.knobsFor("coverage").quantity());

        assertFalse(PrecisionEditors.knobsFor("findInRange").tolerance());
        assertTrue(PrecisionEditors.knobsFor("findInRange").quantity());

        // find/findAll/waitFor use all three — and so does an unrecognised name and a Parameters row, which
        // has no enclosing call at all: hiding a knob we are unsure about would silently strand a setting the
        // user cannot then reach.
        for (String m : new String[]{"find", "findAll", "waitFor", "waitForGone", "somethingNew", null}) {
            assertTrue(PrecisionEditors.knobsFor(m).tolerance(), m + " should offer the tolerance");
            assertTrue(PrecisionEditors.knobsFor(m).quantity(), m + " should offer the quantity gates");
        }
    }

    @Test
    void the_area_readout_describes_an_area_not_a_width() {
        // The misreading the preview exists to correct: 400 is a 20x20 patch, not a 400-wide one.
        String readout = PrecisionEditors.readoutFor(400);
        assertTrue(readout.contains("px²"), readout);
        assertTrue(readout.contains("20×20"), readout);
    }

    // --- what is written, wherever the value lives --------------------------------------------------------

    /**
     * A pick writes the three numbers as a {@link Precision}, not as an expression for one.
     *
     * <p>It asserted {@code "Precision.TIGHT.minArea(400)"} until 2026-09-22 — the shortest exact Java
     * form, spelled by this editor. The host spells it now, through this plugin's own {@code ComponentType},
     * and {@code literalFor} survives only for the dialog's own preview line. One writer means the editor
     * and the reader cannot disagree, which is the whole property the old assertion was protecting.
     */
    @Test
    void a_slot_gets_the_three_numbers_themselves() {
        TestContexts.Recording slot = TestContexts.typedSlot("com.botmaker.sdk.api.vision.Precision",
                "Precision.DEFAULT");

        PrecisionEditors.commit(slot, new PrecisionEditors.Settings(5.0, 400, 0));

        assertEquals(new Precision(5.0, 400, 0), slot.value());
    }

    /**
     * A value with no call site gets the same thing.
     *
     * <p>It got the SDK's three stored numbers — {@code 18.0,400,2000} — until 2026-09-20, because a
     * Parameters row held text its codec had written, and a Java form until 2026-09-22. The editor and the
     * codec were two writers of one file, and a disagreement between them was a value that changed meaning
     * on the way back.
     */
    @Test
    void a_value_with_no_call_site_gets_the_same_three_numbers() {
        TestContexts.Recording row = TestContexts.row("com.botmaker.sdk.api.vision.Precision", "");

        PrecisionEditors.commit(row, new PrecisionEditors.Settings(18.0, 400, 2000));

        assertEquals(new Precision(18.0, 400, 2000), row.value());
    }

    @Test
    void the_round_trip_holds_wherever_the_value_is() {
        PrecisionEditors.Settings picked = new PrecisionEditors.Settings(5.0, 400, 2000);

        TestContexts.Recording slot = TestContexts.typedSlot("com.botmaker.sdk.api.vision.Precision", "");
        PrecisionEditors.commit(slot, picked);
        assertEquals(picked, PrecisionEditors.current(slot));

        TestContexts.Recording row = TestContexts.row("com.botmaker.sdk.api.vision.Precision", "");
        PrecisionEditors.commit(row, picked);
        assertEquals(picked, PrecisionEditors.current(row));
    }

    /**
     * A wither chain the user wrote is still read, which is the one parser this editor kept.
     *
     * <p>The host cannot decode it — it is not a factory call, so no {@code ComponentType} describes it —
     * and falling through to {@code Precision.DEFAULT} would open the dialog on three values the user never
     * chose. See {@code PrecisionEditors.current}.
     */
    @Test
    void a_wither_chain_the_host_cannot_decode_is_still_read() {
        assertEquals(new PrecisionEditors.Settings(5.0, 400, 0), PrecisionEditors.current(
                TestContexts.typedSlot("com.botmaker.sdk.api.vision.Precision",
                        "Precision.TIGHT.minArea(400)")));
    }

    /**
     * A value that says nothing reads as the SDK default rather than as zeroes.
     *
     * <p>Zeroes would be a {@code Precision} that matches nothing, silently, on a slot the user has not
     * touched. It asserted this over {@code wireOf} — three comma-separated numbers, the stored row form —
     * which is deleted with the codec that wrote it; the same property over what a value actually holds now
     * is what is left.
     */
    @Test
    void a_value_that_says_nothing_reads_as_the_sdk_default() {
        assertEquals(new PrecisionEditors.Settings(12.0, 4, 0),
                PrecisionEditors.current(TestContexts.row("com.botmaker.sdk.api.vision.Precision", "")));
        assertEquals(new PrecisionEditors.Settings(12.0, 4, 0), PrecisionEditors.current(
                TestContexts.row("com.botmaker.sdk.api.vision.Precision", "somebodysPrecision")));
    }
}
