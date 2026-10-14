package com.botmaker.sdk.plugin.assist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void aScreenWaitsForEveryScreenAboveItToBeTried() {
        Map<String, String> notes = Map.of(
                "Home", "Marker: h\nUntried: bag",
                "Bag", "Entrance: home › click bag\nMarker: b\nUntried: chest",
                "Chest", "Entrance: Bag › click chest\nMarker: c");
        assertEquals(Optional.of("Bag"), MapTools.unfinishedAbove(notes, "Chest"), "the nearest first");
        assertEquals(Optional.of("Home"), MapTools.unfinishedAbove(notes, "Bag"));
        assertEquals(Optional.empty(), MapTools.unfinishedAbove(notes, "Home"));
        assertEquals(OptionalInt.of(2), MapTools.depth(notes, "Chest"));

        Map<String, String> level = Map.of(
                "Home", "Marker: h\nShape: menu",
                "Bag", "Entrance: home › click bag\nUntried: chest",
                "Upgrades", "Entrance: home › click upgrades\nMarker: u\nShape: activity",
                "Detail", "Entrance: Upgrades › click detail\nUntried: row");
        assertEquals(Optional.empty(), MapTools.unfinishedAbove(level, "Detail"),
                "a finished screen's branch goes down while a sibling still waits: depth across branches");
        assertEquals(Optional.empty(), MapTools.unfinishedAbove(level, "Upgrades"), "one depth in any order");
        assertEquals(Optional.of("Upgrades"), MapTools.unfinishedAbove(Map.of("Home", "Shape: menu",
                        "Upgrades", "Entrance: home › click u\nMarker: u", "Detail", "Entrance: Upgrades › click d"),
                "Detail"), "a screen with nothing tried yet is not finished");
        assertEquals(List.of("Upgrades: no input tried yet"), MapTools.owed(Map.of("Home", "Shape: menu",
                "Upgrades", "Entrance: home › click u\nMarker: u")));
        assertEquals(Optional.empty(), MapTools.unfinishedAbove(Map.of("A", "Entrance: B › key A", "B",
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
}
