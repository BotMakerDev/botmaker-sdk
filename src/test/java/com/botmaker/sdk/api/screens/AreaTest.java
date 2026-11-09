package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** An area is measured from its anchor's corner in the anchor's own widths and heights, so it follows the anchor. */
class AreaTest {

    private static final ImageTemplate GOLD_ICON = new ImageTemplate("images/gold_icon.png");
    private static final Rect FOUND = new Rect(100, 50, 40, 20);

    @Test
    void aBoxCutBesideItsAnchorIsPlacedBackWhereItWasCut() {
        Rect cut = new Rect(148, 50, 120, 20);
        Area gold = Area.between(GOLD_ICON, FOUND, cut);
        assertEquals(Area.of(GOLD_ICON, 1.2, 0, 3, 1), gold);
        assertEquals(cut, gold.place(FOUND));
        assertEquals("1.2, 0 from gold_icon, 3 x 1", gold.said());
    }

    @Test
    void theGapAndTheSizeScaleWithTheAnchor() {
        Area gold = Area.of(GOLD_ICON, 1.2, 0, 3, 1);
        assertEquals(new Rect(222, 75, 180, 30), gold.place(new Rect(150, 75, 60, 30)), "the game at 1.5x");
    }

    @Test
    void anAreaLeftOfOrAboveItsAnchorHasANegativeOffset() {
        Rect cut = new Rect(20, 10, 40, 20);
        Area above = Area.between(GOLD_ICON, FOUND, cut);
        assertEquals(Area.of(GOLD_ICON, -2, -2, 1, 1), above);
        assertEquals(cut, above.place(FOUND));
    }

    @Test
    void aThirdIsKeptToAThousandthAndStillLandsOnItsPixel() {
        Rect found = new Rect(0, 0, 30, 30);
        Rect cut = new Rect(10, 10, 10, 10);
        Area third = Area.between(GOLD_ICON, found, cut);
        assertEquals(0.333, third.offsetX());
        assertEquals(cut, third.place(found));
    }

    @Test
    void anAreaHasAnAnchorAPositiveSizeAndANumberOffset() {
        assertThrows(IllegalArgumentException.class, () -> Area.of(GOLD_ICON, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Area.of(GOLD_ICON, 0, 0, 1, -2));
        assertThrows(IllegalArgumentException.class, () -> Area.of(GOLD_ICON, Double.NaN, 0, 1, 1));
        assertThrows(NullPointerException.class, () -> Area.of(null, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Area.between(GOLD_ICON, new Rect(0, 0, 0, 10), FOUND),
                "an anchor found with no size measures nothing");
    }
}
