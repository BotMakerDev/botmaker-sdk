package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Anchor;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Spot;
import com.botmaker.sdk.api.geometry.Zone;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a place written in the bot's pixels is on the game as it shows, and back. */
class PlacesTest {

    private static final Size BOT = new Size(1280, 720);

    @Test
    void atTheBotsSizeOrWithNoneAPlaceIsItsOwnPixels() {
        assertEquals(new Point(412, 230), Places.inSource(new Spot(412, 230), BOT, BOT));
        assertEquals(new Point(412, 230), Places.inSource(new Spot(412, 230), null, new Size(1920, 1080)),
                "a bot naming no screen size has nothing to scale against");
    }

    @Test
    void theSameShapeScalesWhateverTheAnchor() {
        Size live = new Size(1920, 1080);
        assertEquals(new Point(618, 345), Places.inSource(new Spot(412, 230), BOT, live));
        assertEquals(new Point(618, 345), Places.inSource(new Spot(412, 230).anchored(Anchor.BOTTOM_RIGHT), BOT, live));
        assertEquals(new Rect(15, 30, 300, 180), Places.inSource(new Zone(10, 20, 200, 120), BOT, live));
    }

    @Test
    void anotherShapeKeepsAPlaceWithItsAnchor() {
        // 16:9 bot on a 16:10 game: the art scales 1.5× and the 120 spare rows go to the top and bottom.
        Size live = new Size(1920, 1200);
        assertEquals(new Point(960, 600), Places.inSource(new Spot(640, 360), BOT, live), "the centre stays centred");
        assertEquals(new Point(15, 30), Places.inSource(new Spot(10, 20).anchored(Anchor.TOP_LEFT), BOT, live));
        assertEquals(new Point(1905, 1170),
                Places.inSource(new Spot(1270, 700).anchored(Anchor.BOTTOM_RIGHT), BOT, live), "a corner HUD");
        assertEquals(new Rect(1620, 1050, 300, 150),
                Places.inSource(new Zone(1080, 620, 200, 100).anchored(Anchor.BOTTOM_RIGHT), BOT, live));
        assertTrue(Places.otherShape(BOT, live));
        assertFalse(Places.otherShape(BOT, new Size(1920, 1080)));
    }

    @Test
    void aZoneRegionIsPlacedOnTheSurfaceAsItShowsAtEachCapture() {
        int[] width = {400};
        CaptureSource game = new CaptureSource() {
            @Override
            public BufferedImage capture() {
                return new BufferedImage(width[0], width[0] * 3 / 4, BufferedImage.TYPE_INT_RGB);
            }

            @Override
            public Point origin() {
                return new Point(1000, 500);
            }
        };
        BotScreen.use(new Size(200, 150));
        try {
            CaptureSource bag = game.region(new Zone(10, 10, 50, 50));
            assertEquals(100, bag.capture().getWidth(), "the 400×300 game is twice the bot's screen");
            assertEquals(new Point(1020, 520), bag.origin());
            width[0] = 200;
            assertEquals(50, bag.capture().getWidth(), "the same source follows the game back to its size");
        } finally {
            BotScreen.use(null);
        }
    }

    @Test
    void aScreenshotPixelGoesBackToTheSpotThatShowsThere() {
        Size live = new Size(1920, 1200);
        Spot spot = new Spot(1270, 700).anchored(Anchor.BOTTOM_RIGHT);
        Point shown = Places.inSource(spot, BOT, live);
        assertEquals(spot, Places.inBot(shown.x(), shown.y(), Anchor.BOTTOM_RIGHT, BOT, live));
        Zone zone = new Zone(1080, 620, 200, 100).anchored(Anchor.BOTTOM_RIGHT);
        assertEquals(zone, Places.inBot(Places.inSource(zone, BOT, live), Anchor.BOTTOM_RIGHT, BOT, live));
        assertEquals(new Spot(10, 20), Places.inBot(10, 20, null, BOT, BOT), "no anchor given is the centre");
    }
}
