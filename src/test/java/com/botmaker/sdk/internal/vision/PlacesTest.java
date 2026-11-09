package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a box of a frame at one size shows on a frame at another, kept from the centre. */
class PlacesTest {

    private static final Size BOT = new Size(1280, 720);

    @Test
    void atTheSameSizeOrWithNoneABoxIsItsOwnPixels() {
        assertEquals(new Rect(412, 230, 10, 10), Places.scaled(new Rect(412, 230, 10, 10), BOT, BOT));
        assertEquals(new Rect(412, 230, 10, 10), Places.scaled(new Rect(412, 230, 10, 10), null,
                new Size(1920, 1080)), "no size to scale against");
    }

    @Test
    void theSameShapeScalesEachAxis() {
        assertEquals(new Rect(15, 30, 300, 180), Places.scaled(new Rect(10, 20, 200, 120), BOT,
                new Size(1920, 1080)));
        assertFalse(Places.otherShape(BOT, new Size(1920, 1080)));
    }

    @Test
    void anotherShapeScalesByTheSmallerRatioFromTheCentre() {
        // 16:9 on 16:10: the art scales 1.5× and the 120 spare rows go to the top and bottom.
        Size live = new Size(1920, 1200);
        assertEquals(new Rect(960, 600, 15, 15), Places.scaled(new Rect(640, 360, 10, 10), BOT, live),
                "the centre stays centred");
        assertEquals(new Rect(15, 90, 15, 15), Places.scaled(new Rect(10, 20, 10, 10), BOT, live));
        assertTrue(Places.otherShape(BOT, live));
    }
}
