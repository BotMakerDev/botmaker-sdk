package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** read_map's text: the tree as the window draws it, then what the map cannot do. */
class MapReportTest {

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    private static Input click(String id) {
        return Input.click(picture(id));
    }

    /** Home opens Tavern, which opens Market; Lost names Home but by an input no screen has; Reward is a popup. */
    private static ScreenGraph map() {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").marker(picture("home"))
                .inputs(List.of(click("tavern"), click("event").sometimes())));
        screens.put("TAVERN", GameScreen.named("Tavern").marker(picture("tavern_title"))
                .from(Way.of(BotConstants.screen("HOME"), click("tavern"))).back(click("close"))
                .inputs(List.of(click("market"), click("play").did(Did.IN_PLACE))));
        screens.put("MARKET", GameScreen.named("Market").marker(picture("market_title"))
                .from(Way.of(BotConstants.screen("TAVERN"), click("market")))
                .inputs(List.of(click("gems").never("spends gems"))));
        screens.put("REWARD", GameScreen.named("Reward").marker(picture("reward")).back(click("claim_inline")).popup());
        return ScreenGraph.of(screens);
    }

    private static final Set<String> NAMED = Set.of("home", "tavern", "event", "tavern_title", "close", "market",
            "play", "market_title", "gems", "reward");

    @Test
    void theTreeIsIndentedUnderTheScreenThatOpensEach() {
        String text = MapReport.text(map(), Map.of(), NAMED::contains, id -> true, "TAVERN"::equals, null);
        assertTrue(text.startsWith("The map: 4 screens, 1 popup.\nHome [HOME] "), text);
        assertTrue(text.contains("\n    Tavern [TAVERN] finished, activity\n"
                + "      marker tavern_title · back click close · shot yes\n"
                + "      in from Home by click tavern\n"
                + "      - click market: → Market\n"
                + "      - click play: in place\n"), text);
        assertTrue(text.contains("\n        Market [MARKET] "), text);
        assertTrue(text.contains("\nPopups (opened by the game itself):\nReward [REWARD] "), text);
    }

    @Test
    void whatTheMapCannotDoIsSaid() {
        List<String> problems = MapReport.problems(map(), NAMED::contains, id -> !id.equals("market_title"), null);
        assertEquals(List.of(
                "Home: click event comes and goes and was never seen, and no method says when it is there "
                        + "(a crop_pictures box's when).",
                "Market: the picture market_title has no file.",
                "Reward: the picture claim_inline is written inline in Screens.java, not as one of the bot's Pictures."),
                problems);
    }

    @Test
    void looksAndTheMethodsSayingWhenAreRead() {
        Map<String, GameScreen> screens = new LinkedHashMap<>(map().screens());
        Input event = click("event").when(ScreenTypes.condition("Firestone::eventOn"));
        Input tavern = click("tavern").variants(List.of(Variant.of(picture("tavern_badge"), State.BADGE)));
        screens.put("HOME", screens.get("HOME").inputs(List.of(tavern, event)));
        screens.put("TAVERN", screens.get("TAVERN").from(Way.of(BotConstants.screen("HOME"), tavern)
                        .when(ScreenTypes.condition("Firestone::tavernOpen")))
                .variants(List.of(ScreenVariant.named("Halloween").marker(picture("pumpkin"))
                        .inputs(List.of(click("candy"))))));
        ScreenGraph graph = ScreenGraph.of(screens);

        String text = MapReport.text(graph, Map.of(), id -> true, id -> true, id -> true, null);
        assertTrue(text.contains("- click event: not seen yet (comes and goes) while Firestone::eventOn\n"), text);
        assertTrue(text.contains("- click tavern: → Tavern (while Firestone::tavernOpen); looks normal tavern → "
                + "Tavern (while Firestone::tavernOpen), badge tavern_badge → Tavern (while Firestone::tavernOpen)\n"),
                text);
        assertTrue(text.contains("in from Home by click tavern (while Firestone::tavernOpen)\n"), text);
        assertTrue(text.contains("look Halloween: marker pumpkin\n"), text);
        assertTrue(text.contains("- click candy: untried [look Halloween]\n"), text);
        assertTrue(MapReport.problems(graph, id -> true, id -> true, null).isEmpty(),
                "a method saying when it is there answers for what comes and goes");
    }

    @Test
    void theFirstScreensOtherWaysInAreSaidToo() {
        Map<String, GameScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("HOME", screens.get("HOME").alsoFrom(List.of(Way.of(BotConstants.screen("TAVERN"),
                click("home_button")))));
        String text = MapReport.text(ScreenGraph.of(screens), Map.of(), id -> true, id -> true, id -> true, "HOME");
        assertTrue(text.contains("\n  also from Tavern by click home_button\n"), text);
    }

    @Test
    void aScreenNoWayReachesIsSaid() {
        Map<String, GameScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("LOST", GameScreen.named("Lost").marker(picture("home"))
                .from(Way.of(BotConstants.screen("NOWHERE"), click("tavern"))));
        List<String> problems = MapReport.problems(ScreenGraph.of(screens), NAMED::contains, id -> true, "LOST");
        assertEquals(List.of("Lost: no way from Home reaches it, so a run cannot go there."), problems);
    }
}
