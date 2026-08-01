package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Precision} exists to stop numbers whose unit is invisible from being wrong in silence, so what is
 * worth pinning is the part that would otherwise stay silent: a nonsense value has to be rejected at the call
 * rather than degrade into "matches everything" or "matches nothing", and adjusting one knob must not quietly
 * move another.
 */
class PrecisionTest {

    @Test
    void theNamedAnchorsRunFromExactToTheWholeColourFamily() {
        assertEquals(0.0, Precision.EXACT.deltaE());
        assertTrue(Precision.EXACT.deltaE() < Precision.TIGHT.deltaE());
        assertTrue(Precision.TIGHT.deltaE() < Precision.DEFAULT.deltaE());
        assertTrue(Precision.DEFAULT.deltaE() < Precision.LOOSE.deltaE());
    }

    @Test
    void everyAnchorStartsFromTheSameQuantityGates() {
        // The anchors name a colour tolerance and nothing else. If picking LOOSE also loosened the blob floor,
        // a bot switching anchors to chase a shading problem would silently change what counts as a patch.
        for (Precision p : new Precision[]{Precision.EXACT, Precision.TIGHT, Precision.DEFAULT, Precision.LOOSE}) {
            assertEquals(4, p.minArea(), p + " should carry the default area floor");
            assertEquals(0, p.minCount(), p + " should require no particular total");
        }
    }

    @Test
    void aNegativeToleranceIsRejectedRatherThanMatchingNothing() {
        // ΔE is a distance. A negative threshold can never be met, so a bot built on one would simply never
        // see its colour — the silent failure this type exists to prevent.
        assertThrows(IllegalArgumentException.class, () -> Precision.of(-1));
        assertThrows(IllegalArgumentException.class, () -> Precision.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Precision.DEFAULT.tolerance(-1));
    }

    @Test
    void anEmptyClusterThresholdIsRejectedRatherThanMatchingEverything() {
        assertThrows(IllegalArgumentException.class, () -> Precision.DEFAULT.minArea(0));
        assertThrows(IllegalArgumentException.class, () -> Precision.DEFAULT.minArea(-5));
    }

    @Test
    void aZeroCountIsLegalBecauseItIsTheHonestWayToSayNoRequirement() {
        // The asymmetry is the point: an area of 0 asks for a cluster of nothing, which the search cannot
        // honour, but a count of 0 is a real answer — "however much of it there is". Rejecting it would leave
        // no way to express the common case, and every anchor uses it.
        assertEquals(0, Precision.DEFAULT.minCount(0).minCount());
        assertThrows(IllegalArgumentException.class, () -> Precision.DEFAULT.minCount(-1));
    }

    @Test
    void eachWitherMovesOneKnobAndLeavesTheRest() {
        // The whole ergonomic argument for one type is that you start from a sane whole and adjust what you
        // care about. That only holds if adjusting is genuinely local.
        Precision p = Precision.of(18, 400, 2000);

        assertEquals(new Precision(5.0, 400, 2000), p.tolerance(5.0));
        assertEquals(new Precision(18, 40, 2000), p.minArea(40));
        assertEquals(new Precision(18, 400, 10), p.minCount(10));
    }

    @Test
    void aCountWithNoAreaFloorIsExpressible() {
        // "Is there enough of this colour, however it clumps" — twenty scattered segments must be able to
        // satisfy it, which they cannot if an area floor rides along.
        Precision p = Precision.DEFAULT.minArea(1).minCount(2000);
        assertEquals(1, p.minArea());
        assertEquals(2000, p.minCount());
    }

    @Test
    void theEquivalentSideIsTheAreaBackAsALength() {
        // The readout the Studio's picker draws: 400 px of area is a 20x20 patch, not a 400px-wide one. The
        // conversion lives on the type so the type and its editor cannot disagree about what the unit is.
        assertEquals(20.0, Precision.DEFAULT.minArea(400).equivalentSide(), 1e-9);
        assertEquals(2.0, Precision.DEFAULT.equivalentSide(), 1e-9);
    }

    @Test
    void theReadoutNamesTheUnitOfEveryKnobThatIsDoingSomething() {
        // "400" alone is the ambiguity this whole type exists to remove; a log line carrying only the numbers
        // reintroduces it. A knob is left out only when it is genuinely doing nothing — an area floor of 1
        // and a count of 0 — so DEFAULT still reports its floor of 4, which does filter.
        assertEquals("ΔE 12.0, 4 px² blob", Precision.DEFAULT.toString());
        assertEquals("ΔE 0.0", Precision.EXACT.minArea(1).toString());
        assertEquals("ΔE 12.0, 400 px² blob", Precision.DEFAULT.minArea(400).toString());
        assertEquals("ΔE 5.0, 400 px² blob, 2000 px total",
                Precision.TIGHT.minArea(400).minCount(2000).toString());
    }
}
