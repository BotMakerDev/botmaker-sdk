package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.MapFixtures;
import com.botmaker.sdk.internal.screens.MapScreen;
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
import java.util.function.BooleanSupplier;

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
        // The name is a Java string in the bot's Screens: only a line of its own holds it.
        assertTrue(MapTools.isName("Bag: chests"));
        assertTrue(MapTools.isName("Town › Tavern"));
        for (String bad : List.of("", "Bag\nStage: tried", "Bag\tchests")) {
            assertFalse(MapTools.isName(bad), bad);
        }
        assertEquals("Engineer", MapTools.nameFrom("engineer_home", "Home"));
        assertEquals("Tavern choice", MapTools.nameFrom("tavern_choice_town", "Town"));
        assertEquals("Upgrades", MapTools.nameFrom("upgrades", "Home"));
        assertEquals("Guild hall", MapTools.nameFrom("guild_hall", "Home"));
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

    /** Only the way in from that screen by that input is made conditional, whether it is the entrance or not. */
    @Test
    void aWayIsOpenOnlyWhileAMethodSaysSoAndTheOthersStayAlwaysOpen() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", MapFixtures.screen("Home", MapFixtures.click("m_key")));
        screens.put("CAMPAIGN", MapFixtures.screen("Campaign", MapFixtures.click("map")));
        screens.put("MAP", MapScreen.of(Setup.named("Map").from(BotConstants.input("CAMPAIGN", "map"))
                .alsoFrom(List.of(Way.of(BotConstants.input("HOME", "m_key"))))));
        ScreenGraph graph = ScreenGraph.of(screens);
        BooleanSupplier eventOn = () -> true;

        MapScreen entrance = ScreenEdits.withWayWhen(graph, screens.get("MAP"), "CAMPAIGN", "map", eventOn);
        assertSame(eventOn, entrance.from().when());
        assertEquals(null, entrance.alsoFrom().getFirst().when(), "the hotkey stays always open");

        MapScreen other = ScreenEdits.withWayWhen(graph, entrance, "HOME", "m_key", eventOn);
        assertSame(eventOn, other.alsoFrom().getFirst().when());
        assertEquals(null, ScreenEdits.withWayWhen(graph, other, "CAMPAIGN", "map", null).from().when(),
                "always again");
        assertEquals(screens.get("MAP").from(), ScreenEdits.withWayWhen(graph, screens.get("MAP"), "HOME", "map",
                eventOn).from(), "no way from Home by the map: nothing changes");
    }

    /**
     * An input that leads to a screen mapped already is one more way into it, by hand as try_input records it; not
     * into itself, a popup, the screen above it (its way back) or one it is reached through, nor twice.
     */
    @Test
    void aWayIntoAScreenMappedAlreadyIsAddedOnceAndOnlyWhereItCanBe() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", MapFixtures.screen("Home", MapFixtures.click("campaign"), MapFixtures.click("m_key")));
        screens.put("CAMPAIGN", MapFixtures.screen(Setup.named("Campaign").from(BotConstants.input("HOME",
                "campaign")), MapFixtures.click("map"), MapFixtures.click("home")));
        screens.put("MAP", MapFixtures.screen(Setup.named("Map").from(BotConstants.input("CAMPAIGN", "map")),
                MapFixtures.click("up")));
        screens.put("REWARD", MapFixtures.screen(Setup.named("Reward").popup()));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(Optional.empty(), ScreenEdits.wayRefused(graph, "MAP", "HOME", "m_key"));
        MapScreen map = ScreenEdits.withWay(screens.get("MAP"), "HOME", "m_key");
        assertEquals(screens.get("MAP").from(), map.from(), "the way it was found stays first");
        assertEquals(List.of(Way.of(BotConstants.input("HOME", "m_key"))), map.alsoFrom());

        screens.put("MAP", map);
        ScreenGraph added = ScreenGraph.of(screens);
        assertTrue(ScreenEdits.wayRefused(added, "MAP", "HOME", "m_key").isPresent(), "once");
        assertTrue(ScreenEdits.wayRefused(added, "MAP", "HOME", "nothing").isPresent(), "no such input");
        assertTrue(ScreenEdits.wayRefused(added, "HOME", "HOME", "m_key").isPresent(), "into itself");
        assertTrue(ScreenEdits.wayRefused(added, "REWARD", "HOME", "m_key").isPresent(), "a popup");
        assertTrue(ScreenEdits.wayRefused(added, "HOME", "CAMPAIGN", "home").isPresent(), "its way back");
        assertTrue(ScreenEdits.wayRefused(added, "HOME", "MAP", "up").isPresent(), "a screen it is reached through");
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
