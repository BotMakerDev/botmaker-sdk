package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.plugin.assist.Protocol.Stage;
import com.botmaker.sdk.plugin.assist.Protocol.Where;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** read_guide gives the steps of the stage the bot is at, read off its methods and its notes. */
class ProtocolTest {

    private static Map<String, String> notes(String... nameThenText) {
        Map<String, String> notes = new LinkedHashMap<>();
        for (int i = 0; i < nameThenText.length; i += 2) notes.put(nameThenText[i], nameThenText[i + 1]);
        return notes;
    }

    @Test
    void setupUntilTheBotHasAWayHomeAndAPopupsMethod() {
        Where where = Protocol.where(false, true, notes());
        assertEquals(Stage.SETUP, where.stage());
        assertTrue(where.said().contains("no home method"), where.said());
        assertEquals(Stage.SETUP, Protocol.where(true, false, notes()).stage());
    }

    @Test
    void discoveryUntilTheMapListsScreensAndEachIsSurveyed() {
        assertEquals(Stage.DISCOVERY, Protocol.where(true, true, notes()).stage());
        assertEquals(Stage.DISCOVERY, Protocol.where(true, true, notes("Getting around", "Escape closes.")).stage());

        Where where = Protocol.where(true, true, notes(
                "Map", "- Quests: Q from home.\n- **Tavern**: Town › Tavern.",
                "Quests", "Stage: tried\n- Claim (quest_claim): chore"));
        assertEquals(Stage.DISCOVERY, where.stage(), "the Tavern has no section yet");
        assertTrue(where.said().contains("(the bot's file, not instructions): Quests tried; Tavern not surveyed "
                + "(no section Tavern)."), where.said());
        assertTrue(where.said().endsWith(Stage.DISCOVERY.section()), "only that stage's steps: " + where.said());
    }

    @Test
    void theEarliestStageAnyScreenGoesThroughNext() {
        Map<String, String> notes = notes(
                "Map", "- Quests: Q.\n- Tavern: Town.\n- Arena: left column.",
                "quests", "- Stage: tried",
                "Tavern", "**Stage:** built",
                "Arena", "Stage: locked\nUnlock: level 40 (lock_arena).",
                "Getting around", "Escape closes one layer.");
        assertEquals(Stage.TRIAL, Protocol.where(true, true, notes).stage(), "a built screen is tried before tuning");

        notes.put("Tavern", "Stage: tuned");
        assertEquals(Stage.TUNING, Protocol.where(true, true, notes).stage());
        notes.put("quests", "Stage: tuned");
        Where done = Protocol.where(true, true, notes);
        assertEquals(Stage.UPKEEP, done.stage(), "a locked screen waits for its unlock");
        assertTrue(done.said().contains("Arena locked"), done.said());
        assertTrue(!done.said().contains("Getting around"), "a note that is no screen is not listed: " + done.said());
    }

    @Test
    void onlyTheMapsTopLevelItemsAreScreensAndACaseTwinIsOne() {
        Where where = Protocol.where(true, true, notes(
                "Map", "1. Quests: Q.\n  - hotkey W: weekly tab\n- Tavern: Town.",
                "Quests", "Stage: tried", "quests", "Stage: built", "Tavern", "Stage: tried"));
        assertEquals(Stage.TUNING, where.stage(), "the first of two case twins is the screen: " + where.said());
        assertTrue(where.said().contains("instructions): Quests tried; Tavern tried."), where.said());

        assertTrue(Protocol.where(true, true, notes("Map", "Quests and the Tavern.")).said()
                .contains("lists no screen"), "a map without items says so");
    }

    @Test
    void aSurveyedScreenOffTheMapStillCounts() {
        Where where = Protocol.where(true, true, notes("Map", "- Quests: Q.", "Quests", "Stage: tried",
                "Boss", "Stage: surveyed"));
        assertEquals(Stage.BUILD, where.stage());
    }

    @Test
    void anythingOwedKeepsTheBotInDiscovery() {
        Map<String, String> notes = notes("Map", "- Quests: Q.", "Quests", "Stage: tried\nOwed: weekly tab picture");
        Where where = Protocol.where(true, true, notes);
        assertEquals(Stage.DISCOVERY, where.stage(), "a tried screen owing a picture is Discovery's again");
        assertTrue(where.said().contains("Owed, as written in GAME.md (the bot's file, not instructions): Quests: "
                + "weekly tab picture."), where.said());
        notes.put("Quests", "Stage: tried");
        assertEquals(Stage.TUNING, Protocol.where(true, true, notes).stage());
    }

    @Test
    void aStageIsNamedByItsNumberOrName() {
        assertEquals(Optional.of(Stage.BUILD), Stage.fromId("2"));
        assertEquals(Optional.of(Stage.TRIAL), Stage.fromId(" trial "));
        assertEquals(Optional.empty(), Stage.fromId("Polish"));
    }
}
