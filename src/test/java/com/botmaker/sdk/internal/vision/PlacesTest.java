package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Anchor;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
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
        assertEquals(new Rect(412, 230, 10, 10), Places.inSource(new Zone(412, 230, 10, 10), BOT, BOT));
        assertEquals(new Rect(412, 230, 10, 10), Places.inSource(new Zone(412, 230, 10, 10), null,
                new Size(1920, 1080)), "a bot naming no screen size has nothing to scale against");
    }

    @Test
    void theSameShapeScalesWhateverTheAnchor() {
        Size live = new Size(1920, 1080);
        assertEquals(new Rect(15, 30, 300, 180), Places.inSource(new Zone(10, 20, 200, 120), BOT, live));
        assertEquals(new Rect(15, 30, 300, 180), Places.inSource(new Zone(10, 20, 200, 120)
                .anchored(Anchor.BOTTOM_RIGHT), BOT, live));
    }

    @Test
    void anotherShapeKeepsAPlaceWithItsAnchor() {
        // 16:9 bot on a 16:10 game: the art scales 1.5× and the 120 spare rows go to the top and bottom.
        Size live = new Size(1920, 1200);
        assertEquals(new Rect(960, 600, 15, 15), Places.inSource(new Zone(640, 360, 10, 10), BOT, live),
                "the centre stays centred");
        assertEquals(new Rect(15, 30, 15, 15), Places.inSource(new Zone(10, 20, 10, 10).anchored(Anchor.TOP_LEFT),
                BOT, live));
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
    void aScreenshotBoxGoesBackToTheZoneThatShowsThere() {
        Size live = new Size(1920, 1200);
        Zone zone = new Zone(1080, 620, 200, 100).anchored(Anchor.BOTTOM_RIGHT);
        assertEquals(zone, Places.inBot(Places.inSource(zone, BOT, live), Anchor.BOTTOM_RIGHT, BOT, live));
        assertEquals(new Zone(10, 20, 30, 40), Places.inBot(new Rect(10, 20, 30, 40), null, BOT, BOT),
                "no anchor given is the centre");
    }
}
