package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.vision.FrameChange.Clock;
import com.botmaker.sdk.internal.vision.FrameChange.Motion;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * try_input's own rules: how a screen is named, a marker owed with a reason, a screen slow to draw. The order of
 * Discovery is the graph's ({@code ScreenGraphTest}); try_input on a bot is {@code SdkAssistTest}'s.
 */
class MapToolsTest {

    @Test
    void aScreenIsNamedInPlainWordsOrFromThePictureThatOpensIt() {
        assertTrue(MapTools.isName("Guild hall"));
        for (String bad : List.of("", "Guild *Hall*", "# Guild", "Guild #", "Bag: chests", "Bag\nStage: tried",
                "Bag\tchests", "[Bag]", "Town › Tavern")) {
            assertFalse(MapTools.isName(bad), bad);
        }
        assertEquals("Engineer", MapTools.nameFrom("engineer_home", "Home"));
        assertEquals("Tavern choice", MapTools.nameFrom("tavern_choice_town", "Town"));
        assertEquals("Upgrades", MapTools.nameFrom("upgrades", "Home"));
        assertEquals("Guild hall", MapTools.nameFrom("guild_hall", "Home"));
    }

    @Test
    void onlyAMarkerOwedWithAReasonLetsTheNextScreenGoOn() {
        assertTrue(MapTools.markerWithReason("marker — every icon here animates"));
        assertTrue(MapTools.markerWithReason("Marker - it scrolls"));
        assertFalse(MapTools.markerWithReason("marker"));
        assertFalse(MapTools.markerWithReason("marker-less icons"));
        assertFalse(MapTools.markerWithReason("weekly tab picture"));
    }

    /** A screen that draws a second after a small first change is still read as the screen it opens. */
    @Test
    void aScreenSlowToDrawIsWaitedFor() throws InterruptedException {
        BufferedImage before = filled(Color.GRAY, 0);
        BufferedImage pressed = filled(Color.GRAY, 20);     // a button's press: a corner, under SCREEN_SHARE
        BufferedImage garage = filled(Color.BLUE, 0);
        ActTools.Entered first = new ActTools.Entered(AgentReply.text("Clicked garage; changed a corner."), before,
                pressed, Motion.NONE, "click garage", "Clicked garage");
        assertTrue(first.share() < MapTools.SCREEN_SHARE);

        long[] waited = {0};
        Deque<BufferedImage> reads = new ArrayDeque<>(List.of(pressed, pressed, pressed, garage, garage));
        ActTools.Entered later = ActTools.later(first, () -> Optional.of(reads.size() > 1 ? reads.poll()
                : reads.peek()), MapTools.SLOW_SCREEN_MS, MapTools.SCREEN_SHARE, counting(waited),
                after -> AgentReply.text("answered"));
        assertSame(garage, later.after());
        assertTrue(later.share() >= MapTools.SCREEN_SHARE);
        assertEquals("answered", later.reply().parts().getFirst().text());
        assertEquals("click garage", later.how());

        waited[0] = 0;
        ActTools.Entered same = ActTools.later(first, () -> Optional.of(pressed), MapTools.SLOW_SCREEN_MS,
                MapTools.SCREEN_SHARE, counting(waited), after -> AgentReply.text("answered"));
        assertSame(first, same, "a change in place stays one");
        assertEquals(MapTools.SLOW_SCREEN_MS, waited[0], "read on to the cap, never past it");
    }

    /**
     * A screen finished sends the game up to the nearest screen above with work left, past finished ones; a screen
     * with work left, or below which some is left, goes nowhere (2026-10-14).
     */
    @Test
    void aFinishedScreenGoesUpToTheNearestScreenWithWorkLeft() {
        ScreenGraph open = map(click("mail"));
        assertEquals(Optional.of("HOME"), MapTools.upTo(open, "MAP"), "past Campaign, finished, to Home's mail");
        assertEquals(Optional.of("HOME"), MapTools.upTo(open, "CAMPAIGN"));
        assertEquals(Optional.empty(), MapTools.upTo(open, "HOME"), "Home has work left");

        ScreenGraph done = map(click("mail").did(Did.NONE));
        assertEquals(Optional.of("HOME"), MapTools.upTo(done, "MAP"), "all finished: the top screen");
        assertEquals(Optional.empty(), MapTools.upTo(done, "HOME"), "the top screen goes nowhere");

        Map<String, GameScreen> screens = new LinkedHashMap<>(open.screens());
        screens.put("MAP", screens.get("MAP").inputs(List.of(click("zoom"))));
        assertEquals(Optional.empty(), MapTools.upTo(ScreenGraph.of(screens), "MAP"), "Map's zoom is untried");
        assertEquals(Optional.empty(), MapTools.upTo(ScreenGraph.of(screens), "CAMPAIGN"), "Map, below it, is not done");
    }

    /** Home ▸ Campaign ▸ Map, each tried out but Home's {@code mail}. */
    private static ScreenGraph map(Input mail) {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").marker(picture("home")).inputs(List.of(click("campaign"), mail)));
        screens.put("CAMPAIGN", GameScreen.named("Campaign").marker(picture("campaign_title"))
                .from(Way.of(BotConstants.screen("HOME"), click("campaign")))
                .inputs(List.of(click("map"), click("claim").did(Did.IN_PLACE))));
        screens.put("MAP", GameScreen.named("Map").marker(picture("map_title"))
                .from(Way.of(BotConstants.screen("CAMPAIGN"), click("map"))).inputs(List.of(click("x").did(Did.NONE))));
        return ScreenGraph.of(screens);
    }

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    private static Input click(String id) {
        return Input.click(picture(id));
    }

    /** What moved on a screen stands for its next inputs there, for a while, on a frame of its size. */
    @Test
    void aScreensMotionIsReusedOnItForAWhile() {
        ActTools.Ambient none = new ActTools.Ambient("HOME", Motion.NONE, 1_000);
        assertTrue(none.fits("HOME", 1_000 + ActTools.MOTION_REUSE_MS - 1, 1920, 1080));
        assertFalse(none.fits("HOME", 1_000 + ActTools.MOTION_REUSE_MS, 1920, 1080), "read again once stale");
        assertFalse(none.fits("CAMPAIGN", 1_000, 1920, 1080), "another screen moves otherwise");

        BufferedImage a = filled(Color.GRAY, 0);
        Motion moving = Motion.of(List.of(a, filled(Color.GRAY, 20), a));
        ActTools.Ambient seen = new ActTools.Ambient("HOME", moving, 0);
        assertTrue(seen.fits("HOME", 0, 320, 180));
        assertFalse(seen.fits("HOME", 0, 640, 360), "a frame of another size is read again");
    }

    /** A {@code color} frame with its top-left {@code corner}×{@code corner} square white. */
    private static BufferedImage filled(Color color, int corner) {
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 320, 180);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, corner, corner);
        g.dispose();
        return image;
    }

    private static Clock counting(long[] waited) {
        return new Clock() {
            @Override
            public long nowMs() {
                return waited[0];
            }

            @Override
            public void sleep(long ms) {
                waited[0] += ms;
            }
        };
    }
}
