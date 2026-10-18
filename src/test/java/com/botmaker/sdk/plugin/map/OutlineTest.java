package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.map.Outline.Entry;
import com.botmaker.sdk.plugin.map.Outline.Filter;
import com.botmaker.sdk.plugin.map.Outline.Kind;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.marks;
import static com.botmaker.sdk.internal.screens.MapFixtures.picture;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 🗺 Screens' outline: the map as a text tree, and what a filter keeps of it. */
class OutlineTest {

    /**
     * Home opens Campaign and Engineer (owing its marker); Campaign's map opens Map, which Home also reaches by a
     * hotkey; Arena is behind a locked input; Reward is a popup; Lost is reached from nowhere.
     */
    private static ScreenGraph map() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("Home").markers(marks("home")), click("campaign"), click("engineer"),
                click("gold"), Hotkey.of(Key.M), click("arena").locked(picture("padlock")), click("event").sometimes()));
        screens.put("CAMPAIGN", screen(Setup.named("Campaign").markers(marks("campaign_title"))
                .from(ref("HOME", "campaign")), click("claim").tried(), click("map")));
        screens.put("MAP", screen(Setup.named("Map").markers(marks("map_title")).from(ref("CAMPAIGN", "map"))
                .alsoFrom(List.of(Way.of(ref("HOME", "key_m")))), Hotkey.of(Key.ESCAPE).tried()));
        screens.put("ENGINEER", screen(Setup.named("Engineer").from(ref("HOME", "engineer"))));
        screens.put("ARENA", screen(Setup.named("Arena").markers(marks("arena_title")).from(ref("HOME", "arena"))));
        screens.put("REWARD", screen(Setup.named("Reward").markers(marks("reward")).back(click("close_x")).popup()));
        screens.put("LOST", screen(Setup.named("Lost").markers(marks("lost")), click("ok")));
        return ScreenGraph.of(screens);
    }

    private static List<Entry> outline(ScreenGraph graph) {
        return outline(graph, Map.of());
    }

    /** The outline with each screen's shot written at {@code shots}' time, none for a screen it leaves out. */
    private static List<Entry> outline(ScreenGraph graph, Map<String, Long> shots) {
        return Outline.entries(graph, ScreenCards.cards(graph, Map.of()), ScreenCards.links(graph),
                c -> shots.getOrDefault(c, 0L));
    }

    /** Each line's key, indented two spaces a level. */
    private static String lines(List<Entry> entries) {
        StringBuilder out = new StringBuilder();
        lines(entries, 0, out);
        return out.toString();
    }

    private static void lines(List<Entry> entries, int depth, StringBuilder out) {
        for (Entry entry : entries) {
            out.append("  ".repeat(depth)).append(entry.key()).append('\n');
            lines(entry.children(), depth + 1, out);
        }
    }

    /** A screen that follows another on its own sits beside it: Home after Loading, Battle after Entering battle. */
    @Test
    void aScreenThatFollowsOnItsOwnComesNextToTheOneItFollows() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("Home").markers(marks("home")).after(BotConstants.screen("LOADING")),
                click("fight")));
        screens.put("LOADING", screen(Setup.named("Loading").markers(marks("loading")).start()));
        screens.put("ENTERING", screen(Setup.named("Entering battle").from(ref("HOME", "fight"))));
        screens.put("BATTLE", screen(Setup.named("Battle").after(BotConstants.screen("ENTERING"))));
        assertEquals("""
                LOADING
                HOME
                  HOME.fight
                    ENTERING
                    BATTLE
                """, lines(outline(ScreenGraph.of(screens))));
    }

    @Test
    void eachScreenSitsUnderTheInputItsEntranceIsAndTheRestInGroups() {
        assertEquals("""
                HOME
                  HOME.campaign
                    CAMPAIGN
                      CAMPAIGN.claim
                      CAMPAIGN.map
                        MAP
                          MAP.key_escape
                  HOME.engineer
                    ENGINEER
                  HOME.gold
                  HOME.key_m
                  HOME.arena
                    ARENA
                  HOME.event
                group Popups
                  REWARD
                group Not reached from the first screen
                  LOST
                    LOST.ok
                """, lines(outline(map())), "a way in that is no entrance (Home's M) leads nowhere in the tree");
    }

    @Test
    void aFilterKeepsWhatMatchesAndTheWayDownToIt() {
        ScreenGraph graph = map();
        assertEquals("""
                HOME
                  HOME.campaign
                    CAMPAIGN
                      CAMPAIGN.claim
                      CAMPAIGN.map
                        MAP
                          MAP.key_escape
                  HOME.key_m
                """, lines(Outline.filter(graph, outline(graph), new Filter("MAP", false, false))),
                "Campaign has an input map, so all its inputs stay; Home's M leads to Map; the case is ignored");
    }

    @Test
    void owesKeepsOnlyTheScreensOwingSomething() {
        ScreenGraph graph = map();
        assertEquals("""
                HOME
                  HOME.engineer
                    ENGINEER
                  HOME.arena
                    ARENA
                """, lines(Outline.filter(graph, outline(graph), new Filter("", true, false))),
                "Engineer owes its marker, Arena has nothing cut");
    }

    @Test
    void untriedKeepsTheInputsLeftToTryOnAScreenDone() {
        ScreenGraph graph = map();
        List<Entry> kept = Outline.filter(graph, outline(graph), new Filter("ok", false, true));
        assertEquals("""
                group Not reached from the first screen
                  LOST
                    LOST.ok
                """, lines(kept));
        assertEquals(Kind.GROUP, kept.getFirst().kind());
    }

    /** Recent tops the outline, newest shot first; a screen with no shot is not in it, and it holds ten at most. */
    @Test
    void recentListsTheScreensWhoseShotWasTakenLast() {
        List<Entry> entries = outline(map(), Map.of("MAP", 300L, "HOME", 100L, "LOST", 200L));
        Entry recent = entries.getFirst();
        assertEquals("group Recent", recent.key());
        assertEquals(List.of("recent MAP", "recent LOST", "recent HOME"),
                recent.children().stream().map(Entry::key).toList());
        assertTrue(recent.children().stream().allMatch(e -> e.children().isEmpty() && !e.inTree()),
                "a screen's inputs stay under its own line in the tree");
        assertEquals("HOME", entries.get(1).key(), "the tree follows, as without shots");

        Map<String, MapScreen> many = new LinkedHashMap<>();
        Map<String, Long> shots = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            many.put("S" + i, screen(Setup.named("Screen " + i).markers(marks("s" + i))));
            shots.put("S" + i, 1L + i);
        }
        List<Entry> twelve = outline(ScreenGraph.of(many), shots).getFirst().children();
        assertEquals(Outline.RECENT_COUNT, twelve.size());
        assertEquals("recent S11", twelve.getFirst().key());
        assertEquals("recent S2", twelve.getLast().key());
    }

    /** Inputs that open a screen: one tried that leads nowhere is folded away, a way in by another keeps one. */
    @Test
    void openingFoldsAwayTheInputsTriedThatOpenNothing() {
        assertEquals("""
                HOME
                  HOME.campaign
                    CAMPAIGN
                      CAMPAIGN.map
                        MAP
                  HOME.engineer
                    ENGINEER
                  HOME.gold
                  HOME.key_m
                  HOME.arena
                    ARENA
                  HOME.event
                group Popups
                  REWARD
                group Not reached from the first screen
                  LOST
                    LOST.ok
                """, lines(Outline.opening(outline(map()))),
                "Campaign's claim and Map's Escape were tried and opened nothing; Home's M leads to Map; the rest "
                        + "are not tried yet");
    }

    @Test
    void noFilterKeepsTheOutlineAsItIs() {
        ScreenGraph graph = map();
        List<Entry> whole = outline(graph);
        assertEquals(whole, Outline.filter(graph, whole, Filter.NONE));
        assertEquals(Filter.NONE, new Filter("  ", false, false));
    }
}
