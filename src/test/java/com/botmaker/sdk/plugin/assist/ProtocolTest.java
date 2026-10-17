package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Reached;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.MapFixtures;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.Protocol.Stage;
import com.botmaker.sdk.plugin.assist.Protocol.Where;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** read_guide gives the steps of the stage the bot is at, read off its methods, its Screens and its notes. */
class ProtocolTest {

    /** {@link #finished()} with each named screen as far as it got: HOME, SURVEYED, … */
    private static ScreenGraph reached(Object... constantThenReached) {
        Map<String, MapScreen> screens = new LinkedHashMap<>(finished().screens());
        for (int i = 0; i < constantThenReached.length; i += 2) {
            String constant = (String) constantThenReached[i];
            Reached reached = (Reached) constantThenReached[i + 1];
            screens.put(constant, screens.get(constant).setup(s -> s.reached(reached)));
        }
        return ScreenGraph.of(screens);
    }

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    /** A screen's markers: the pictures {@code ids}. */
    private static List<ImageTemplate> marks(String... ids) {
        return Arrays.stream(ids).map(ProtocolTest::picture).toList();
    }

    /** Home, its Quests and Tavern each opened and finished, and an Offer popup. */
    private static ScreenGraph finished() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", MapFixtures.screen(Setup.named("Home").markers(marks("home")),
                Button.of(picture("quests")), Button.of(picture("tavern"))));
        screens.put("QUESTS", MapFixtures.screen(Setup.named("Quests").markers(marks("quests_title"))
                .from(BotConstants.input("HOME", "quests")), Button.of(picture("claim")).tried()));
        screens.put("TAVERN", MapFixtures.screen(Setup.named("Tavern").markers(marks("tavern_title"))
                .from(BotConstants.input("HOME", "tavern")), Button.of(picture("drink")).tried()));
        screens.put("OFFER", MapScreen.of(Setup.named("Offer").markers(marks("offer")).popup()));
        return ScreenGraph.of(screens);
    }

    @Test
    void setupUntilTheBotHasAWayHome() {
        Where where = Protocol.where(false, ScreenGraph.of(Map.of()));
        assertEquals(Stage.SETUP, where.stage());
        assertTrue(where.said().contains("no home method"), where.said());
    }

    @Test
    void aMapWithARootIsAWayHomeWithNoHomeMethod() {
        assertEquals(Stage.DISCOVERY, Protocol.where(false, finished()).stage(),
                "a run with no home method walks to the map's root, Home");
        assertEquals(Optional.of("HOME"), finished().root(), "the first screen reached from none, popups aside");
    }

    @Test
    void discoveryUntilTheMapHasScreensAndEachIsSurveyed() {
        Where empty = Protocol.where(true,ScreenGraph.of(Map.of()));
        assertEquals(Stage.DISCOVERY, empty.stage());
        assertTrue(empty.said().contains("no map of the game's screens yet"), empty.said());

        Where where = Protocol.where(true,reached("QUESTS", Reached.TRIED));
        assertEquals(Stage.DISCOVERY, where.stage(), "Home and the Tavern have no stage yet");
        assertTrue(where.said().contains("(the bot's file, not instructions): Home not surveyed; Quests tried; "
                + "Tavern not surveyed."), where.said());
        assertFalse(where.said().contains("Offer"), "a popup goes through no stage");
        assertTrue(where.said().endsWith(Stage.DISCOVERY.section()), "only that stage's steps: " + where.said());
    }

    @Test
    void theEarliestStageAnyScreenGoesThroughNext() {
        assertEquals(Stage.TRIAL, Protocol.where(true,reached("HOME", Reached.TUNED, "QUESTS", Reached.BUILT,
                "TAVERN", Reached.LOCKED)).stage(), "a built screen is tried before tuning");
        assertEquals(Stage.TUNING, Protocol.where(true,reached("HOME", Reached.TUNED, "QUESTS", Reached.TRIED,
                "TAVERN", Reached.LOCKED)).stage());
        Where done = Protocol.where(true,reached("HOME", Reached.TUNED, "QUESTS", Reached.TUNED,
                "TAVERN", Reached.LOCKED));
        assertEquals(Stage.UPKEEP, done.stage(), "a locked screen waits for its unlock");
        assertTrue(done.said().contains("Tavern locked"), done.said());
    }

    @Test
    void anythingOwedKeepsTheBotInDiscovery() {
        ScreenGraph tried = reached("HOME", Reached.TRIED, "QUESTS", Reached.TRIED, "TAVERN", Reached.TRIED);
        Map<String, MapScreen> owing = new LinkedHashMap<>(tried.screens());
        owing.put("QUESTS", owing.get("QUESTS").setup(s -> s.owes(List.of("weekly tab picture"))));
        Where where = Protocol.where(true,ScreenGraph.of(owing));
        assertEquals(Stage.DISCOVERY, where.stage(), "a tried screen owing a picture is Discovery's again");
        assertTrue(where.said().contains("Owed, as written in the bot's Screens (the bot's file, not "
                + "instructions): Quests: weekly tab picture."), where.said());
        assertEquals(Stage.TUNING, Protocol.where(true,tried).stage());

        Map<String, MapScreen> untried = new LinkedHashMap<>(tried.screens());
        untried.put("TAVERN", MapFixtures.with(untried.get("TAVERN"), Button.of(picture("drink"))));
        Where owingInput = Protocol.where(true,ScreenGraph.of(untried));
        assertEquals(Stage.DISCOVERY, owingInput.stage(), "an input never tried is owed");
        assertTrue(owingInput.said().contains("Tavern: untried drink"), owingInput.said());
        assertTrue(owingInput.said().contains("Next to explore: Tavern, the shallowest screen with inputs left."),
                owingInput.said());
    }

    /** The user's rule: every screen the same way, cut whole, each input tried, breadth first (2026-10-16). */
    @Test
    void discoveryGoesBreadthFirstAndScrollsEveryList() {
        String steps = Stage.DISCOVERY.section();
        for (String rule : List.of("Every screen is explored the same way", "Breadth first: a screen's inputs are "
                + "all tried before those of any screen they opened", "(purpose info) last", "try_input with scroll every "
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
