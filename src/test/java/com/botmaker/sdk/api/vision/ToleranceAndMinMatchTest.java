package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two value types exist to stop a number whose unit is invisible from being wrong in silence, so what is
 * worth pinning is the part that would otherwise stay silent: a nonsense value has to be rejected at the call
 * rather than degrade into "matches everything" or "matches nothing".
 */
class ToleranceAndMinMatchTest {

    @Test
    void theNamedTolerancesRunFromExactToTheWholeColourFamily() {
        assertEquals(0.0, Tolerance.EXACT.deltaE());
        assertTrue(Tolerance.EXACT.deltaE() < Tolerance.TIGHT.deltaE());
        assertTrue(Tolerance.TIGHT.deltaE() < Tolerance.DEFAULT.deltaE());
        assertTrue(Tolerance.DEFAULT.deltaE() < Tolerance.LOOSE.deltaE());
    }

    @Test
    void aNegativeToleranceIsRejectedRatherThanMatchingNothing() {
        // ΔE is a distance. A negative threshold can never be met, so a bot built on one would simply never
        // see its colour — the silent failure this type exists to prevent.
        assertThrows(IllegalArgumentException.class, () -> Tolerance.of(-1));
        assertThrows(IllegalArgumentException.class, () -> Tolerance.of(Double.NaN));
    }

    @Test
    void anEmptyClusterThresholdIsRejectedRatherThanMatchingEverything() {
        assertThrows(IllegalArgumentException.class, () -> MinMatch.area(0));
        assertThrows(IllegalArgumentException.class, () -> MinMatch.area(-5));
        assertEquals(1, MinMatch.ANY.area());
    }

    @Test
    void aZeroCountIsLegalBecauseItIsTheHonestWayToSayNoRequirement() {
        // The asymmetry is the point: an area of 0 asks for a cluster of nothing, which the search cannot
        // honour, but a count of 0 is a real answer — "however much of it there is". Rejecting it would leave
        // no way to express the common case, and both defaults use it.
        assertEquals(0, MinMatch.area(400).count());
        assertEquals(0, MinMatch.DEFAULT.count());
        assertThrows(IllegalArgumentException.class, () -> MinMatch.of(4, -1));
    }

    @Test
    void aCountAloneAcceptsAnyBlobSize() {
        // count(n) is "enough of this colour, however it clumps" — so it must not quietly bring an area floor
        // with it, or twenty scattered segments adding up to n would still be rejected.
        assertEquals(1, MinMatch.count(2000).area());
        assertEquals(2000, MinMatch.count(2000).count());
    }

    @Test
    void theEquivalentSideIsTheAreaBackAsALength() {
        // The readout the Studio's picker draws: 400 px of area is a 20x20 patch, not a 400px-wide one. The
        // conversion lives on the type so the type and its editor cannot disagree about what the unit is.
        assertEquals(20.0, MinMatch.area(400).equivalentSide(), 1e-9);
        assertEquals(2.0, MinMatch.DEFAULT.equivalentSide(), 1e-9);
    }

    @Test
    void theReadoutNamesTheUnitOfWhicheverThresholdsAreSet() {
        // "400" alone is the ambiguity this whole type exists to remove; a log line that says only the number
        // reintroduces it.
        assertEquals("400 px² blob", MinMatch.area(400).toString());
        assertEquals("400 px² blob, 2000 px total", MinMatch.of(400, 2000).toString());
    }
}
