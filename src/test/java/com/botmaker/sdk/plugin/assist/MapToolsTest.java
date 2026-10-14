package com.botmaker.sdk.plugin.assist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** map_screen's notes: the Map's line, the section owing its marker, and each cut noted where it belongs. */
class MapToolsTest {

    @Test
    void aMappedScreenGetsItsLineAndASectionOwingItsMarker(@TempDir Path bot) {
        GameNotes.write(bot, "Map", "Home is the fight screen.\n- Engineer: left column, helmet. Tools Claim.");
        GameNotes.write(bot, "Quests", "Marker: quests_tab");
        assertEquals("Mapped Engineer as home › click engineer_home, its section owing a marker.",
                MapTools.recorded(bot, "engineer", "home › click engineer_home"));
        Map<String, String> notes = GameNotes.sections(bot);
        assertEquals("Home is the fight screen.\n- Engineer: home › click engineer_home. left column, helmet. Tools "
                + "Claim.", notes.get("Map"), "the line rewritten in place, its spelling and what it said kept");
        assertEquals("Entrance: home › click engineer_home\nOwed: marker", notes.get("Engineer"));
        assertEquals(Optional.of("Engineer"), MapTools.owingMarker(notes));

        assertEquals("Mapped Quests as home › key Q; its section was there already.",
                MapTools.recorded(bot, "Quests", "home › key Q"));
        assertEquals("Marker: quests_tab", GameNotes.sections(bot).get("Quests"), "a section written before is kept");
        assertEquals("Home is the fight screen.\n- Engineer: home › click engineer_home. left column, helmet. Tools "
                + "Claim.\n- Quests: home › key Q", GameNotes.sections(bot).get("Map"), "a new line goes last");
    }

    @Test
    void aMarkerPaysTheDebtAndOtherCutsGatherOnOneLine() {
        String opened = "Entrance: home › click engineer_home\nOwed: marker";
        String marked = MapTools.withMarker(opened, "engineer_title");
        assertEquals("Entrance: home › click engineer_home\nMarker: engineer_title", marked);
        assertEquals("Entrance: home › click engineer_home\nMarker: gear_engineer",
                MapTools.withMarker(marked, "gear_engineer"), "one marker, the latest");
        assertEquals("Marker: a\nNotes.", MapTools.withMarker("- Owed: marker — it animates\nNotes.", "a"),
                "a debt with a reason is paid too");

        String cut = MapTools.withCut(marked, "claim_engineer");
        assertEquals(marked + "\nCut: claim_engineer", cut);
        assertEquals(marked + "\nCut: claim_engineer, garage_engineer", MapTools.withCut(cut, "garage_engineer"));
        assertEquals(cut, MapTools.withCut(cut, "claim_engineer"), "a picture is listed once");
    }

    @Test
    void onlyAMarkerOwedWithoutAReasonStopsTheNextScreen() {
        assertEquals(Optional.empty(), MapTools.owingMarker(Map.of("Map", "Owed: marker", "Guild",
                "Owed: marker — every icon here animates", "Bag", "Owed: chest button, no chest to open")));
        assertEquals(Optional.of("Bag"), MapTools.owingMarker(Map.of("Bag", "Entrance: home › click bag\n- owed: "
                + "MARKER  ")));
        assertEquals(List.of("Guild: marker — every icon here animates", "Guild: expeditions lock"),
                MapTools.owed(Map.of("Map", "Owed: nothing", "Guild", "Owed: marker — every icon here animates\n"
                        + "Owed: expeditions lock")));
    }

    @Test
    void anOwedLineSaysItsOwnLineOnlyAndNothingOwedIsNoDebt() {
        assertEquals(List.of(), MapTools.owed(Map.of("Bag", "Owed:\nStage: tried", "Guild", "Owed: none\nOwed: -")));
        assertEquals(Optional.of("Bag"), MapTools.owingMarker(Map.of("Bag", "Owed: marker.")));
        assertEquals(Optional.empty(), MapTools.owingMarker(Map.of("Bag", "Owed:\nmarker")));
        assertEquals("Marker: a\nOwed: marker-less icons", MapTools.withMarker("Owed: marker-less icons", "a"),
                "a debt that only starts with the word is not the marker's");
    }

    @Test
    void aScreenIsNamedInPlainWords() {
        assertTrue(MapTools.isName("Guild hall"));
        for (String bad : List.of("", "Guild *Hall*", "# Guild", "Guild #", "Bag: chests", "Bag\nStage: tried",
                "Bag\tchests", "[Bag]")) {
            assertFalse(MapTools.isName(bad), bad);
        }
    }

    @Test
    void aScreenMappedAgainLosesItsOldEntranceAndKeepsItsBulletAndWords() {
        assertEquals("- Bag: home › click bag_home. chests, daily",
                MapTools.withMapLine("- Bag: home › key B. chests, daily", "Bag", "home › click bag_home"));
        assertEquals("1. Bag: home › key B. chests",
                MapTools.withMapLine("1. **Bag:** chests", "Bag", "home › key B"));
    }
}
