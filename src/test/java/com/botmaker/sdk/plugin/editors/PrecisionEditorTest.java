package com.botmaker.sdk.plugin.editors;

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
 * <p>The editor reads the {@link Precision} the host hands it and hands one back. A value the host could not
 * read is shown as written and opens on the SDK's defaults.
 *
 * <p>No JavaFX toolkit is needed for any of it.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PrecisionEditorTest {

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
     * form, spelled by this editor. The host spells it now, through this plugin's own {@code ComponentType}.
     * One writer means the editor and the reader cannot disagree, which is the whole property the old
     * assertion was protecting.
     */
    @Test
    void a_slot_gets_the_three_numbers_themselves() {
        TestContexts.Recording slot = TestContexts.typedSlot(Precision.class,
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
        TestContexts.Recording row = TestContexts.row(Precision.class, "");

        PrecisionEditors.commit(row, new PrecisionEditors.Settings(18.0, 400, 2000));

        assertEquals(new Precision(18.0, 400, 2000), row.value());
    }

    @Test
    void the_round_trip_holds_wherever_the_value_is() {
        PrecisionEditors.Settings picked = new PrecisionEditors.Settings(5.0, 400, 2000);

        TestContexts.Recording slot = TestContexts.typedSlot(Precision.class, "");
        PrecisionEditors.commit(slot, picked);
        assertEquals(picked, PrecisionEditors.current(slot));

        TestContexts.Recording row = TestContexts.row(Precision.class, "");
        PrecisionEditors.commit(row, picked);
        assertEquals(picked, PrecisionEditors.current(row));
    }

    /**
     * A wither chain the user wrote is not a call the host reads, so the pill shows it as written and the
     * dialog opens on the defaults. This plugin parsed the chain until 2026-09-23.
     */
    @Test
    void a_wither_chain_the_host_cannot_read_is_shown_as_written() {
        TestContexts.Recording chain = TestContexts.typedSlot(Precision.class,
                "Precision.TIGHT.minArea(400)");

        assertEquals("Precision.TIGHT.minArea(400)", PrecisionEditors.pillText(chain));
        assertEquals(new PrecisionEditors.Settings(12.0, 4, 0), PrecisionEditors.current(chain));
    }

    @Test
    void a_value_the_host_read_is_labelled_by_what_it_means() {
        TestContexts.Recording read = TestContexts.typedSlot(Precision.class,
                "new Precision(5.0, 4, 0)").withValue(new Precision(5.0, 4, 0));

        assertTrue(PrecisionEditors.pillText(read).startsWith("TIGHT"), PrecisionEditors.pillText(read));
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
                PrecisionEditors.current(TestContexts.row(Precision.class, "")));
        assertEquals(new PrecisionEditors.Settings(12.0, 4, 0), PrecisionEditors.current(
                TestContexts.row(Precision.class, "somebodysPrecision")));
    }
}
