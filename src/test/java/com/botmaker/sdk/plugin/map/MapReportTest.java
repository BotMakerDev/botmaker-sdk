package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.MapAudit.Audit;
import com.botmaker.sdk.plugin.assist.MapAudit.Spot;
import com.botmaker.sdk.plugin.assist.MapAudit.Where;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.marks;
import static com.botmaker.sdk.internal.screens.MapFixtures.picture;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static com.botmaker.sdk.internal.screens.MapFixtures.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** read_map's text: the tree as the window draws it, then what the map cannot do. */
class MapReportTest {

    /** Home opens Tavern, which opens Market; Reward is a popup closed by a button written inline. */
    private static Map<String, MapScreen> screens() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("Home").markers(marks("home")), click("tavern"),
                click("event").sometimes()));
        screens.put("TAVERN", screen(Setup.named("Tavern").markers(marks("tavern_title")).from(ref("HOME", "tavern"))
                        .back(ref("TAVERN", "close")),
                click("market"), click("play").tried(), click("close")));
        screens.put("MARKET", screen(Setup.named("Market").markers(marks("market_title")).from(ref("TAVERN", "market")),
                click("gems").never("spends gems")));
        screens.put("REWARD", screen(Setup.named("Reward").markers(marks("reward")).back(click("claim_inline"))
                .popup()));
        return screens;
    }

    private static ScreenGraph map() {
        return ScreenGraph.of(screens());
    }

    private static final Set<String> NAMED = Set.of("home", "tavern", "event", "tavern_title", "close", "market",
            "play", "market_title", "gems", "reward");

    @Test
    void theTreeIsIndentedUnderTheScreenThatOpensEach() {
        String text = MapReport.text(map(), Map.of(), NAMED::contains, id -> true, "TAVERN"::equals, null);
        assertTrue(text.startsWith("The map: 4 screens, 1 popup.\nHome [HOME] "), text);
        assertTrue(text.contains("\n    Tavern [TAVERN] finished, activity\n"
                + "      marker tavern_title · back close · shot yes\n"
                + "      in from Home by tavern\n"
                + "      - market: → Market\n"
                + "      - play: tried\n"
                + "      - close: way back\n"), text);
        assertTrue(text.contains("\n        Market [MARKET] "), text);
        assertTrue(text.contains("\nPopups (opened by the game itself):\nReward [REWARD] "), text);
    }

    @Test
    void whatTheMapCannotDoIsSaid() {
        List<String> problems = MapReport.problems(map(), NAMED::contains, id -> !id.equals("market_title"), null);
        assertEquals(List.of(
                "Home: event comes and goes and was never seen, and no method says when it is there "
                        + "(a crop_pictures box's when).",
                "Market: the picture market_title has no file.",
                "Reward: the picture claim_inline is written inline in Screens.java, not as one of the bot's Pictures."),
                problems);
    }

    @Test
    void looksAndTheMethodsSayingWhenAreRead() {
        Map<String, MapScreen> screens = screens();
        screens.put("HOME", with(screens.get("HOME"),
                click("tavern").looks(List.of(Look.of(picture("tavern_badge"), State.BADGE))),
                click("event").when(ScreenTypes.condition("Firestone::eventOn"))));
        screens.put("TAVERN", with(screens.get("TAVERN").setup(s -> s.fromWhen(
                        ScreenTypes.condition("Firestone::tavernOpen")).variants(List.of(ScreenVariant
                        .named("Halloween").markers(marks("pumpkin")).inputs(List.of(ref("TAVERN", "candy")))))),
                click("candy")));
        ScreenGraph graph = ScreenGraph.of(screens);

        String text = MapReport.text(graph, Map.of(), id -> true, id -> true, id -> true, null);
        assertTrue(text.contains("- event: not seen yet (comes and goes) while Firestone::eventOn\n"), text);
        assertTrue(text.contains("- tavern: → Tavern (while Firestone::tavernOpen); looks normal tavern → "
                + "Tavern (while Firestone::tavernOpen), badge tavern_badge → Tavern (while Firestone::tavernOpen)\n"),
                text);
        assertTrue(text.contains("in from Home by tavern (while Firestone::tavernOpen)\n"), text);
        assertTrue(text.contains("look Halloween: marker pumpkin\n"), text);
        assertTrue(text.contains("- candy: untried [look Halloween]\n"), text);
        assertTrue(MapReport.problems(graph, id -> true, id -> true, null).isEmpty(),
                "a method saying when it is there answers for what comes and goes");
    }

    @Test
    void theFirstScreensOtherWaysInAreSaidToo() {
        Map<String, MapScreen> screens = screens();
        screens.put("TAVERN", with(screens.get("TAVERN"), click("home_button")));
        screens.put("HOME", screens.get("HOME").setup(s -> s.alsoFrom(List.of(Way.of(ref("TAVERN",
                "home_button"))))));
        String text = MapReport.text(ScreenGraph.of(screens), Map.of(), id -> true, id -> true, id -> true, "HOME");
        assertTrue(text.contains("\n  also from Tavern by home_button\n"), text);
    }

    @Test
    void whatAShotDoesNotShowIsSaid() {
        Rect close = new Rect(1800, 40, 40, 40);
        Audit tavern = new Audit(List.of(
                new Spot("tavern_title", true, false, true, Where.AT_PLACE, new Rect(900, 30, 80, 30), null),
                new Spot("market", false, false, true, Where.MISSING, null, new Rect(400, 500, 60, 60)),
                new Spot("play", false, false, false, Where.MISSING, null, null),
                new Spot("close", false, false, true, Where.ELSEWHERE, new Rect(1848, 40, 40, 40), close)));
        Audit market = new Audit(List.of(
                new Spot("market_title", true, false, true, Where.MISSING, null, null),
                new Spot("gems", false, false, true, Where.MISSING, null, null)));
        // Home's shot shows the button Home is told apart from Tavern by never showing.
        Audit home = new Audit(List.of(
                new Spot("home", true, false, true, Where.AT_PLACE, new Rect(10, 10, 40, 40), null),
                new Spot("upgrade", false, true, false, Where.AT_PLACE, new Rect(300, 300, 40, 40), null)));
        String text = MapReport.text(map(), Map.of(), NAMED::contains, id -> true, id -> true,
                Map.of("TAVERN", tavern, "MARKET", market, "HOME", home), null);
        assertTrue(text.contains("- Home: its shot shows upgrade, which Home never shows: take the shot again while "
                + "the game shows Home, or cut its markers again."), text);
        assertTrue(text.contains("- Tavern: market is not on its shot.\n"), text);
        assertTrue(text.contains("- Tavern: close sits (+48,0) from its place on its shot: placed again while the "
                + "game shows Tavern, it gets a copy of its own there.\n"), text);
        assertTrue(text.contains("- Tavern: its shot does not show play, which comes and goes or is another look "
                + "(a note).\n"), text);
        assertTrue(text.contains("- Market: its shot does not show its marker market_title: take the shot again "
                + "while the game shows Market, or cut its markers again."), text);
        assertFalse(text.contains("gems is not on"), "a shot of another moment says only that: " + text);
    }

    @Test
    void aScreenNoWayReachesIsSaid() {
        Map<String, MapScreen> screens = screens();
        screens.put("LOST", screen(Setup.named("Lost").markers(marks("home")).from(ref("NOWHERE", "tavern"))));
        List<String> problems = MapReport.problems(ScreenGraph.of(screens), NAMED::contains, id -> true, "LOST");
        assertEquals(List.of("Lost: no way from Home reaches it, so a run cannot go there."), problems);
    }
}
