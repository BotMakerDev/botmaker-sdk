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
class ToleranceAndMinPixelsTest {

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
        assertThrows(IllegalArgumentException.class, () -> MinPixels.of(0));
        assertThrows(IllegalArgumentException.class, () -> MinPixels.of(-5));
        assertEquals(1, MinPixels.ANY.pixels());
    }

    @Test
    void theEquivalentSideIsTheAreaBackAsALength() {
        // The readout the Studio's picker draws: 400 px of area is a 20x20 patch, not a 400px-wide one. The
        // conversion lives on the type so the type and its editor cannot disagree about what the unit is.
        assertEquals(20.0, MinPixels.of(400).equivalentSide(), 1e-9);
        assertEquals(2.0, MinPixels.DEFAULT.equivalentSide(), 1e-9);
    }
}
