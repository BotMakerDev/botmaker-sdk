package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Area;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.screens.MapFixtures;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.vision.FrameChange.Motion;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

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

    /** A button leading up is the way back only from a screen one screen opens; from one several open, it leaves. */
    @Test
    void aScreenOpenedFromSeveralHasNoWayBackUp() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", MapFixtures.screen("Home", MapFixtures.click("m_key")));
        screens.put("CAMPAIGN", MapFixtures.screen("Campaign", MapFixtures.click("map")));
        screens.put("MAP", MapScreen.of(Setup.named("Map").from(BotConstants.input("CAMPAIGN", "map"))
                .alsoFrom(List.of(Way.of(BotConstants.input("HOME", "m_key"))))));
        screens.put("STAGE", MapScreen.of(Setup.named("Stage").from(BotConstants.input("CAMPAIGN", "map"))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertTrue(MapTools.goesBack(graph, "STAGE", "CAMPAIGN"), "one screen opens Stage: up is back");
        assertFalse(MapTools.goesBack(graph, "MAP", "CAMPAIGN"), "Map opens from Campaign and Home: up is a way in");
        assertFalse(MapTools.goesBack(graph, "STAGE", "HOME"), "not the screen above");
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

    /** Only the way in by that input goes through the copy picked; its condition stays. */
    @Test
    void aWayGoesThroughTheCopyPickedAndKeepsWhenItIsOpen() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", MapFixtures.screen("Home", MapFixtures.click("m_key").repeated()));
        screens.put("CAMPAIGN", MapFixtures.screen("Campaign", MapFixtures.click("map").repeated()));
        BooleanSupplier eventOn = () -> true;
        screens.put("MAP", MapScreen.of(Setup.named("Map").from(BotConstants.input("CAMPAIGN", "map"))
                .alsoFrom(List.of(Way.of(BotConstants.input("HOME", "m_key")).when(eventOn)))));
        ScreenGraph graph = ScreenGraph.of(screens);
        Pick second = Pick.nth(2, Order.WEST);

        MapScreen other = ScreenEdits.withWayPick(graph, screens.get("MAP"), "HOME", "m_key", second);
        assertEquals(second, other.alsoFrom().getFirst().pick());
        assertSame(eventOn, other.alsoFrom().getFirst().when());
        assertEquals(null, other.from().pick(), "the entrance stays on its first copy");
        assertEquals(Pick.each(Order.NORTH), ScreenEdits.withWayPick(graph, other, "CAMPAIGN", "map",
                Pick.each(Order.NORTH)).from().pick());
    }

    /** A button made to cycle, and a hotkey given another key, keep all else they were. */
    @Test
    void aButtonCyclesAndAHotkeyTakesAnotherKeyKeepingTheRest() {
        Button buy = MapFixtures.click("buy").tried().repeated();
        Button cycling = ScreenEdits.cycles(buy, true);
        assertTrue(cycling.cycles());
        assertEquals(buy, ScreenEdits.cycles(cycling, false));
        Hotkey q = Hotkey.of(Key.Q).info().never("spends gems");
        assertEquals(new Hotkey(Combo.of(Key.W), false, false, true, "spends gems", false, null, null),
                q.combo(Combo.of(Key.W)));
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

    /**
     * Photo's zoom moved the whole island under its shutter, and try_input recorded a new screen (2026-11-02): the
     * screen stayed, as its marker's own pixels say. A popup's dimming changes them; a marker it never shows found, or
     * its marker gone, is another screen.
     */
    @Test
    void aScreenWhoseMarkerStaysPutStayedThoughMostOfItChanged() {
        Rect shutter = new Rect(0, 0, 40, 40);
        Function<String, Optional<Rect>> there = id -> Optional.of(shutter);
        ImageTemplate marker = new ImageTemplate("images/shutter.png");
        Markers markers = new Markers(List.of(marker), List.of());
        BufferedImage before = filled(Color.GREEN, 40);
        BufferedImage zoomed = filled(Color.BLUE, 40);
        Rect all = new Rect(0, 0, 320, 180);
        assertTrue(MapTools.stayed(markers, there, before, zoomed, all));
        // The shutter is a round button: its box's corners and see-through ring show the island the zoom moves.
        assertTrue(MapTools.stayed(markers, there, inset(Color.GREEN), inset(Color.BLUE), all),
                "a round marker's corners show the ground under it");

        BufferedImage dimmed = filled(Color.BLUE, 0);
        Graphics2D g = dimmed.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, 40, 40);
        g.dispose();
        assertFalse(MapTools.stayed(markers, there, before, dimmed, all), "a popup dims the marker");
        assertFalse(MapTools.stayed(new Markers(List.of(marker), List.of(new ImageTemplate("images/other.png"))),
                there, before, zoomed, all), "one it never shows is found");
        assertFalse(MapTools.stayed(markers, id -> Optional.empty(), before, zoomed, all), "its marker went");
        assertFalse(MapTools.stayed(new Markers(List.of(), List.of()), there, before, zoomed, all),
                "nothing tells it");
        assertFalse(MapTools.stayed(markers, there, before, zoomed, new Rect(160, 0, 160, 180)),
                "a panel opened beside the marker spans only a part");
    }

    /** A {@code color} frame with a white 20×20 square in the middle of its top-left 40×40 box. */
    private static BufferedImage inset(Color color) {
        BufferedImage image = filled(color, 0);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(10, 10, 20, 20);
        g.dispose();
        return image;
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

    /** A spot beside an anchor is tried by its field, never as the anchor's own button. */
    @Test
    void aSpotIsTriedByItsField() {
        ImageTemplate skill = new ImageTemplate("char_skill_icon");
        MapScreen character = MapScreen.of(Setup.named("Character")).withInput("charSkillIcon", Button.of(skill))
                .withInput("slotWeapon", Button.beside(skill, -5.6, -9.5));
        assertEquals(Optional.of("slotWeapon"), MapTools.spotField(character, "slotWeapon"));
        assertEquals(Optional.of("slotWeapon"), MapTools.spotField(character, "slotweapon"), "as a field is routed");
        assertEquals(Optional.empty(), MapTools.spotField(character, "charSkillIcon"), "the anchor's button is not one");
        assertEquals(Optional.empty(), MapTools.spotField(character, "char_skill_icon"), "a picture is not a field");
    }

    /**
     * which_screen says what each readout of the screen shown reads, as a run would (2026-11-09), and reads through
     * its area: one that reads nothing because its anchor is not shown says so, since that is a missing picture, not
     * an empty number.
     */
    @Test
    void theScreenShownSaysWhatItsReadoutsRead() {
        Readout gems = Readout.text(MapFixtures.area("gems_icon", 1.2, 0, 2, 1));
        Readout energy = Readout.text(MapFixtures.area("energy_icon", 1.2, 0, 3, 1));
        Readout rolla = Readout.text(MapFixtures.area("rolla_icon", 1.2, 0, 3, 1));
        Readout coins = Readout.text(MapFixtures.area("coin_icon", 1.2, 0, 3, 1));
        Map<String, Readout> readouts = new LinkedHashMap<>();
        readouts.put("gems", gems);
        readouts.put("energy", energy);
        readouts.put("rolla", rolla);
        readouts.put("coins", coins);
        ScreenGraph graph = ScreenGraph.of(Map.of("HOME", new MapScreen(Setup.named("Home"), Map.of(), readouts)));
        Map<Readout, Optional<String>> shown = Map.of(gems, Optional.of("130"), energy, Optional.empty(),
                coins, Optional.empty());
        assertEquals(" Its readouts read: gems \"130\", energy nothing, rolla unreadable, coins nothing (coin_icon "
                        + "is not shown).",
                MapTools.readings(graph, "HOME", r -> {
                    if (r == rolla) throw new IllegalStateException("words not read");
                    return shown.get(r);
                }, area -> area.anchor().id().equals("coin_icon") ? " (coin_icon is not shown)" : ""));
        assertEquals("", MapTools.readings(ScreenGraph.of(Map.of("HOME", new MapScreen(Setup.named("Home"),
                Map.of(), Map.of()))), "HOME", r -> Optional.empty(), area -> ""),
                "a screen with no readout says nothing");
    }

    /**
     * Why an area read nothing: its anchor not shown, or shown with the box off the screen, which are told apart since
     * one is a missing picture and the other a wrong offset; a box on the screen that shows nothing says no more.
     */
    @Test
    void anAreaThatReadsNothingSaysWhetherItsAnchorOrItsBoxIsMissing() {
        BufferedImage frame = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Rect icon = new Rect(300, 50, 40, 20);
        Area beside = MapFixtures.area("coin_icon", 1.2, 0, 1, 1);
        Area far = MapFixtures.area("coin_icon", 12, 0, 3, 1);
        assertEquals(" (coin_icon is not shown)", MapTools.whyNothing(beside, frame, (p, f) -> Optional.empty()));
        assertEquals(" (its box, placed from coin_icon, is off the screen)",
                MapTools.whyNothing(far, frame, (p, f) -> Optional.of(icon)));
        assertEquals("", MapTools.whyNothing(beside, frame, (p, f) -> Optional.of(icon)), "on the screen, blank");
        assertEquals("", MapTools.whyNothing(beside, frame, (p, f) -> {
            throw new IllegalStateException("no matcher");
        }), "an anchor that could not be looked for says nothing more");
    }

    @Test
    void eachReadoutsAreaIsPlacedFromItsAnchorWhereTheFrameShowsIt() {
        BufferedImage frame = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Rect icon = new Rect(100, 50, 40, 20);
        Map<String, Readout> readouts = new LinkedHashMap<>();
        readouts.put("gems", Readout.text(MapFixtures.area("gems_icon", 1, 0, 2, 1)));
        readouts.put("gems_below", Readout.text(MapFixtures.area("gems_icon", 0, 1, 1, 1)));
        readouts.put("coins", Readout.text(MapFixtures.area("coin_icon", 1, 0, 2, 1)));
        readouts.put("wide", Readout.text(MapFixtures.area("gems_icon", 1, 0, 20, 1)));
        int[] looks = {0};
        Map<String, Rect> areas = MapTools.areasOn(readouts, frame, (anchor, shown) -> {
            looks[0]++;
            return anchor.id().equals("gems_icon") ? Optional.of(icon) : Optional.empty();
        });
        assertEquals(Map.of("gems", new Rect(140, 50, 80, 20), "gems_below", new Rect(100, 70, 40, 20),
                "wide", new Rect(140, 50, 260, 20)), areas, "coins' anchor is not shown; wide is clipped");
        assertEquals(2, looks[0], "each anchor looked for once");
        assertEquals(Map.of(), MapTools.areasOn(readouts, frame, (anchor, shown) -> {
            throw new IllegalStateException("no matcher");
        }));
    }
}
