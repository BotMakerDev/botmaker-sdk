package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.internal.vision.FrameChange.Clock;
import com.botmaker.sdk.internal.vision.FrameChange.Motion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** try_input's notes: the Map's line, the section owing its marker, each input untried until tried, and its shape. */
class MapToolsTest {

    @Test
    void anOpenedScreenGetsItsLineAndASectionOwingItsMarker(@TempDir Path bot) {
        GameNotes.write(bot, "Map", "Home is the fight screen.\n- Engineer: left column, helmet. Tools Claim.");
        GameNotes.write(bot, "Quests", "Marker: quests_tab");
        assertEquals("Opened Engineer, mapped as home › click engineer_home, its section owing a marker.",
                MapTools.recorded(bot, "engineer", "home › click engineer_home"));
        Map<String, String> notes = GameNotes.sections(bot);
        assertEquals("Home is the fight screen.\n- Engineer: home › click engineer_home. left column, helmet. Tools "
                + "Claim.", notes.get("Map"), "the line rewritten in place, its spelling and what it said kept");
        assertEquals("Entrance: home › click engineer_home\nOwed: marker", notes.get("Engineer"));
        assertEquals(Optional.of("Engineer"), MapTools.owingMarker(notes));

        assertEquals("Opened Quests, mapped as home › key Q; its section was there already.",
                MapTools.recorded(bot, "Quests", "home › key Q"));
        assertEquals("Marker: quests_tab", GameNotes.sections(bot).get("Quests"), "a section written before is kept");
        assertEquals("Home is the fight screen.\n- Engineer: home › click engineer_home. left column, helmet. Tools "
                + "Claim.\n- Quests: home › key Q", GameNotes.sections(bot).get("Map"), "a new line goes last");
    }

    @Test
    void aScreenOpenedIsFinishedBeforeTheNextInputOfTheOneItWasOpenedFrom() {
        Map<String, String> notes = Map.of(
                "Home", "Marker: h\n- tried bag: opens Bag\nUntried: shop",
                "Bag", "Entrance: home › click bag\nMarker: b\nUntried: chest");
        assertEquals(Optional.of("Bag"), MapTools.unfinishedBelow(notes, "Home"), "Bag before Home's shop");
        assertEquals(Optional.empty(), MapTools.unfinishedBelow(notes, "Bag"), "Bag goes on, Home unfinished above it");

        Map<String, String> deeper = Map.of(
                "Home", "Marker: h\nUntried: shop",
                "Bag", "Entrance: home › click bag\nMarker: b\n- tried chest: opens Chest\nShape: menu",
                "Chest", "Entrance: Bag › click chest\nMarker: c");
        assertEquals(Optional.of("Chest"), MapTools.unfinishedBelow(deeper, "Home"),
                "a finished child's own child, nothing tried on it yet, holds Home too");
        assertEquals(OptionalInt.of(2), MapTools.depth(deeper, "Chest"));
        assertEquals(Set.of("Home: untried shop", "Chest: no input tried yet"), new HashSet<>(MapTools.owed(deeper)));

        assertEquals(Optional.of("Shop"), MapTools.unfinishedBelow(Map.of("Home", "Untried: x", "Bag",
                "Entrance: home › click bag\nShape: activity", "Shop", "Entrance: home › click shop\nUntried: y"),
                "Home"), "a finished sibling hides no unfinished one");
        assertEquals(Optional.empty(), MapTools.unfinishedBelow(Map.of("Home", "Untried: shop", "Bag",
                "Entrance: home › click bag\n- tried chest: in place\nShape: activity"), "Home"), "Bag finished");
        assertEquals(Optional.empty(), MapTools.unfinishedBelow(Map.of("A", "Entrance: B › key A\nShape: menu", "B",
                "Entrance: A › key B\nShape: menu"), "A"), "a loop ends");
    }

    @Test
    void oneBranchIsExploredToItsEndBeforeTheNextIsBegun() {
        Map<String, String> notes = new HashMap<>(Map.of(
                "Home", "Marker: h\n- tried a: opens A\n- tried b: opens B\nShape: menu",
                "A", "Entrance: home › click a\nMarker: a\n- tried x: opens Deep\nShape: menu",
                "Deep", "Entrance: A › click x\nMarker: d",
                "B", "Entrance: home › click b\nMarker: b"));
        assertEquals(Optional.of("A"), MapTools.branchBegun(notes, "B"), "A's branch is begun and not finished");
        assertEquals(Optional.empty(), MapTools.branchBegun(notes, "Deep"), "its own branch goes on");
        notes.put("Deep", "Entrance: A › click x\nMarker: d\n- tried y: no visible effect\nShape: activity");
        assertEquals(Optional.empty(), MapTools.branchBegun(notes, "B"), "A's branch done, B is next");
    }

    /** A list scrolled says nothing of the shape, and the last input tried asks once about what hides inputs. */
    @Test
    void aScrolledListIsTriedAndTheLastInputAsksAboutHiddenOnes(@TempDir Path bot) {
        GameNotes.write(bot, "Home", "Marker: h\nUntried: column, bag");
        String first = MapTools.tried(bot, "Home", "column", GameNotes.SCROLLED + "-10, " + GameNotes.IN_PLACE);
        assertEquals("Noted in Home: tried column, scrolled -10, changes this screen in place. Untried: bag.", first);
        String last = MapTools.tried(bot, "Home", "bag", GameNotes.OPENS + "Bag");
        assertTrue(last.contains("a menu.") && last.contains("scrolled to both ends"), last);
        assertTrue(GameNotes.sections(bot).get("Home").endsWith("Shape: menu"), "the column scrolled is no activity");
        assertFalse(MapTools.tried(bot, "Home", "bag", GameNotes.OPENS + "Bag").contains("scrolled to both ends"),
                "asked once, as the last input goes");
    }

    @Test
    void onlyAMarkerOwedWithoutAReasonStopsTheNextScreen() {
        assertEquals(Optional.empty(), MapTools.owingMarker(Map.of("Map", "Owed: marker", "Guild",
                "Owed: marker — every icon here animates", "Bag", "Owed: chest button, no chest to open")));
        assertEquals(Optional.of("Bag"), MapTools.owingMarker(Map.of("Bag", "Entrance: home › click bag\n- owed: "
                + "MARKER  ")));
        assertEquals(List.of("Guild: marker — every icon here animates", "Guild: expeditions lock",
                        "Guild: untried hall_guild"),
                MapTools.owed(Map.of("Map", "Owed: nothing", "Guild", "Owed: marker — every icon here animates\n"
                        + "Owed: expeditions lock\nUntried: hall_guild")));
    }

    @Test
    void anOwedLineSaysItsOwnLineOnlyAndNothingOwedIsNoDebt() {
        assertEquals(List.of(), MapTools.owed(Map.of("Bag", "Owed:\nStage: tried", "Guild", "Owed: none\nOwed: -")));
        assertEquals(Optional.of("Bag"), MapTools.owingMarker(Map.of("Bag", "Owed: marker.")));
        assertEquals(Optional.empty(), MapTools.owingMarker(Map.of("Bag", "Owed:\nmarker")));
    }

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
