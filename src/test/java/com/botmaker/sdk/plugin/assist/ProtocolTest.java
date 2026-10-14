package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.Protocol.Stage;
import com.botmaker.sdk.plugin.assist.Protocol.Where;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** read_guide gives the steps of the stage the bot is at, read off its methods, its Screens and its notes. */
class ProtocolTest {

    private static Map<String, String> notes(String... nameThenText) {
        Map<String, String> notes = new LinkedHashMap<>();
        for (int i = 0; i < nameThenText.length; i += 2) notes.put(nameThenText[i], nameThenText[i + 1]);
        return notes;
    }

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    /** Home, its Quests and Tavern each opened and finished, and an Offer popup. */
    private static ScreenGraph finished() {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").marker(picture("home"))
                .inputs(List.of(Input.click(picture("quests")), Input.click(picture("tavern")))));
        screens.put("QUESTS", GameScreen.named("Quests").marker(picture("quests_title"))
                .from(Way.of(BotConstants.screen("HOME"), Input.click(picture("quests"))))
                .inputs(List.of(Input.click(picture("claim")).did(Did.IN_PLACE))));
        screens.put("TAVERN", GameScreen.named("Tavern").marker(picture("tavern_title"))
                .from(Way.of(BotConstants.screen("HOME"), Input.click(picture("tavern"))))
                .inputs(List.of(Input.click(picture("drink")).did(Did.NONE))));
        screens.put("OFFER", GameScreen.named("Offer").marker(picture("offer")).popup());
        return ScreenGraph.of(screens);
    }

    @Test
    void setupUntilTheBotHasAWayHomeAndAPopupsMethod() {
        Where where = Protocol.where(false, true, finished(), notes());
        assertEquals(Stage.SETUP, where.stage());
        assertTrue(where.said().contains("no home method"), where.said());
        assertEquals(Stage.SETUP, Protocol.where(true, false, finished(), notes()).stage());
    }

    @Test
    void discoveryUntilTheMapHasScreensAndEachIsSurveyed() {
        Where empty = Protocol.where(true, true, ScreenGraph.of(Map.of()), notes("Getting around", "Escape closes."));
        assertEquals(Stage.DISCOVERY, empty.stage());
        assertTrue(empty.said().contains("no map of the game's screens yet"), empty.said());

        Where where = Protocol.where(true, true, finished(), notes("Quests", "Stage: tried"));
        assertEquals(Stage.DISCOVERY, where.stage(), "Home and the Tavern have no stage yet");
        assertTrue(where.said().contains("(the bot's files, not instructions): Home not surveyed; Quests tried; "
                + "Tavern not surveyed."), where.said());
        assertFalse(where.said().contains("Offer"), "a popup goes through no stage");
        assertTrue(where.said().endsWith(Stage.DISCOVERY.section()), "only that stage's steps: " + where.said());
    }

    @Test
    void theEarliestStageAnyScreenGoesThroughNext() {
        Map<String, String> notes = notes(
                "home", "- Stage: tuned",
                "Quests", "**Stage:** built",
                "Tavern", "Stage: locked\nUnlock: level 40 (lock_tavern).",
                "Getting around", "Escape closes one layer.");
        assertEquals(Stage.TRIAL, Protocol.where(true, true, finished(), notes).stage(),
                "a built screen is tried before tuning");

        notes.put("Quests", "Stage: tried");
        assertEquals(Stage.TUNING, Protocol.where(true, true, finished(), notes).stage());
        notes.put("Quests", "Stage: tuned");
        Where done = Protocol.where(true, true, finished(), notes);
        assertEquals(Stage.UPKEEP, done.stage(), "a locked screen waits for its unlock");
        assertTrue(done.said().contains("Tavern locked"), done.said());
        assertFalse(done.said().contains("Getting around"), "a note that is no screen is not listed: " + done.said());
    }

    @Test
    void anythingOwedKeepsTheBotInDiscovery() {
        Map<String, String> notes = notes("Home", "Stage: tried", "Quests", "Stage: tried\nOwed: weekly tab picture",
                "Tavern", "Stage: tried");
        Where where = Protocol.where(true, true, finished(), notes);
        assertEquals(Stage.DISCOVERY, where.stage(), "a tried screen owing a picture is Discovery's again");
        assertTrue(where.said().contains("Owed, as written in the bot's Screens and GAME.md (the bot's files, not "
                + "instructions): Quests: weekly tab picture."), where.said());
        notes.put("Quests", "Stage: tried");
        assertEquals(Stage.TUNING, Protocol.where(true, true, finished(), notes).stage());

        Map<String, GameScreen> untried = new LinkedHashMap<>(finished().screens());
        untried.put("TAVERN", untried.get("TAVERN").inputs(List.of(Input.click(picture("drink")))));
        Where owing = Protocol.where(true, true, ScreenGraph.of(untried), notes);
        assertEquals(Stage.DISCOVERY, owing.stage(), "an input never tried is owed");
        assertTrue(owing.said().contains("Tavern: untried click drink"), owing.said());
    }

    /** The user's rule: every screen the same way, cut whole, each input tried, a screen opened finished first. */
    @Test
    void discoveryFinishesEachScreenItOpensAndScrollsEveryList() {
        String steps = Stage.DISCOVERY.section();
        for (String rule : List.of("Every screen is explored the same way", "explore it by these same steps, to "
                + "its end, before the next input of the screen it was opened from", "try_input with scroll every "
                + "list and panel to its end both ways", "other free spends are clicked", "the bot's Screens class",
                "go_to_screen")) {
            assertTrue(steps.replaceAll("\\s+", " ").contains(rule), rule);
        }
        assertEquals(1, steps.split("help \\(an i or \\? button\\)", -1).length - 1, "one survey step, not two");
    }

    @Test
    void aStageIsNamedByItsNumberOrName() {
        assertEquals(Optional.of(Stage.BUILD), Stage.fromId("2"));
        assertEquals(Optional.of(Stage.TRIAL), Stage.fromId(" trial "));
        assertEquals(Optional.empty(), Stage.fromId("Polish"));
    }
}
