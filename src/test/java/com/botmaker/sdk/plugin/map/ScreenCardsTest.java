package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.map.ScreenCards.RowState;
import com.botmaker.sdk.plugin.map.ScreenCards.State;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What 🗺 Screens draws of a map: each card's state, progress and badges, each edge, each input's row. */
class ScreenCardsTest {

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    private static Input click(String id) {
        return Input.click(picture(id));
    }

    /**
     * Home opens Campaign (finished, a chore and a never) and Engineer (owing its marker); Campaign's map opens Map,
     * which Home also reaches by a hotkey; Arena is behind a locked input; Reward is a popup.
     */
    private static ScreenGraph map() {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").marker(picture("home"))
                .inputs(List.of(click("campaign"), click("engineer"), click("gold"), Input.key(Key.M),
                        click("arena").locked(picture("padlock")), click("event").sometimes())));
        screens.put("CAMPAIGN", GameScreen.named("Campaign").marker(picture("campaign_title"))
                .from(Way.of(BotConstants.screen("HOME"), click("campaign")))
                .inputs(List.of(click("claim").did(Did.IN_PLACE).kind(Kind.CHORE), click("map"),
                        click("gems").never("spends gems"))));
        screens.put("MAP", GameScreen.named("Map").marker(picture("map_title"))
                .from(Way.of(BotConstants.screen("CAMPAIGN"), click("map")))
                .alsoFrom(List.of(Way.of(BotConstants.screen("HOME"), Input.key(Key.M))))
                .inputs(List.of(Input.key(Key.ESCAPE).did(Did.NONE))));
        screens.put("ENGINEER", GameScreen.named("Engineer").from(Way.of(BotConstants.screen("HOME"), click("engineer"))));
        screens.put("ARENA", GameScreen.named("Arena").marker(picture("arena_title"))
                .from(Way.of(BotConstants.screen("HOME"), click("arena").locked(picture("padlock")))));
        screens.put("REWARD", GameScreen.named("Reward").marker(picture("reward")).back(click("close_x")).popup());
        return ScreenGraph.of(screens);
    }

    private static Card card(String constant) {
        return ScreenCards.cards(map(), Map.of("CAMPAIGN", "surveyed")).stream()
                .filter(c -> c.constant().equals(constant)).findFirst().orElseThrow();
    }

    @Test
    void aCardSaysHowFarDiscoveryGotByColourAndByWord() {
        Card home = card("HOME");
        assertEquals(State.UNTRIED, home.state());
        assertEquals("1 untried", home.said(), "gold only: a key that opened a screen is tried, a lock settled, an "
                + "input that comes and goes waits");
        assertEquals(4, home.tried());
        assertEquals(6, home.inputs());
        assertEquals("", home.shape(), "no shape before it is finished");

        Card campaign = card("CAMPAIGN");
        assertEquals(State.FINISHED, campaign.state());
        assertEquals("activity", campaign.shape(), "its claim opened nothing");
        assertEquals(1, campaign.chores());
        assertEquals("surveyed", campaign.stage());
        assertEquals("campaign_title", campaign.marker());

        assertEquals(State.OWES, card("ENGINEER").state());
        assertEquals("owes its marker", card("ENGINEER").said());
        assertEquals(State.FINISHED, card("REWARD").state(), "a popup needs no input");
        assertTrue(card("REWARD").popup());
    }

    @Test
    void aScreenBehindALockOrAnInputThatComesAndGoesIsDrawnDashed() {
        assertTrue(card("ARENA").conditional());
        assertEquals(ScreenGraph.Presence.LOCKED, card("ARENA").presence());
        assertFalse(card("MAP").conditional(), "one way in that is always there is enough");
        assertFalse(card("REWARD").conditional());
    }

    @Test
    void eachEntranceIsSolidAndOneOnlyAnotherWayInAndAWayBackAreDashed() {
        List<Link> links = ScreenCards.links(map());
        List<Link> entrances = links.stream().filter(Link::entrance).toList();
        assertEquals(List.of("HOME>CAMPAIGN", "CAMPAIGN>MAP", "HOME>ENGINEER", "HOME>ARENA"),
                entrances.stream().map(l -> l.from() + ">" + l.to()).toList());
        assertTrue(links.stream().anyMatch(l -> !l.entrance() && !l.back() && l.from().equals("HOME")
                && l.to().equals("MAP")), "the hotkey is another way in");
        assertTrue(links.stream().anyMatch(l -> l.back() && l.from().equals("MAP") && l.to().equals("CAMPAIGN")));
        assertTrue(links.stream().noneMatch(l -> l.from().equals("REWARD") || l.to().equals("REWARD")),
                "a popup is in no tree");
    }

    @Test
    void theTreeStartsAtTheFirstScreenAndAPathReadsFromIt() {
        assertEquals(Optional.of("HOME"), ScreenCards.root(map()));
        assertEquals(List.of("Home", "Campaign", "Map"), ScreenCards.path(map(), "MAP"));
        assertEquals(List.of("Reward"), ScreenCards.path(map(), "REWARD"));
        assertEquals(Optional.empty(), ScreenCards.root(ScreenGraph.of(Map.of())));
    }

    @Test
    void eachInputsRowSaysWhatItDid() {
        List<ScreenCards.Row> home = ScreenCards.rows(map(), "HOME");
        assertEquals(List.of(RowState.TRIED, RowState.TRIED, RowState.UNTRIED, RowState.TRIED, RowState.LOCKED,
                RowState.UNSEEN), home.stream().map(ScreenCards.Row::state).toList());
        assertEquals(List.of("→ Campaign", "→ Engineer", "untried", "→ Map", "locked", "not seen yet"),
                home.stream().map(ScreenCards.Row::result).toList());
        assertEquals("comes and goes", home.getLast().kind());
        assertEquals("event", home.getLast().picture());

        List<ScreenCards.Row> campaign = ScreenCards.rows(map(), "CAMPAIGN");
        assertEquals("in place", campaign.getFirst().result());
        assertEquals("chore", campaign.getFirst().kind());
        assertEquals(RowState.NEVER, campaign.getLast().state());
        assertEquals("never: spends gems", campaign.getLast().result());
        assertEquals(List.of(), ScreenCards.rows(map(), "NOWHERE"));
    }

    @Test
    void anUnchangedMapReadsAsEqualCardsSoNothingIsRedrawn() {
        assertEquals(ScreenCards.cards(map(), Map.of()), ScreenCards.cards(map(), Map.of()));
        assertEquals(ScreenCards.links(map()), ScreenCards.links(map()));
    }

    @Test
    void aWayOpenOnlyWhileAMethodSaysSoCarriesItOnItsLink() {
        Map<String, GameScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("CAMPAIGN", screens.get("CAMPAIGN").from(Way.of(BotConstants.screen("HOME"), click("campaign"))
                .when(ScreenTypes.condition("Firestone::eventOn"))));
        Link campaign = ScreenCards.links(ScreenGraph.of(screens)).stream()
                .filter(l -> l.to().equals("CAMPAIGN") && !l.back()).findFirst().orElseThrow();
        assertEquals("Firestone::eventOn", campaign.when());
        assertTrue(ScreenCards.links(map()).stream().allMatch(l -> l.when().isEmpty()), "no method, nothing said");
    }

    @Test
    void aWayBackOfNoInputReadsAsTheDefaultOne() {
        Link back = ScreenCards.links(map()).stream().filter(l -> l.back() && l.from().equals("CAMPAIGN"))
                .findFirst().orElseThrow();
        assertEquals(null, back.via(), "Campaign has no back of its own");
        assertEquals("the default way back (a close button, then Escape)", back.label());
    }

    @Test
    void aPopupWithNothingCutIsFinishedAsTheGraphCountsIt() {
        Map<String, GameScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("OFFER", GameScreen.named("Offer").marker(picture("offer")).popup());
        ScreenGraph graph = ScreenGraph.of(screens);
        assertFalse(graph.noneCut("OFFER"), "its default way back is its way out");
        assertFalse(graph.unfinished("OFFER"));
        assertEquals(State.FINISHED, ScreenCards.card(graph, "OFFER", "").state());
        assertTrue(graph.owed().stream().noneMatch(o -> o.startsWith("Offer")));
        assertTrue(graph.noneCut("ENGINEER"));
    }

    @Test
    void theCardsPillsAreOneListForEveryView() {
        assertEquals(List.of("finished", "activity", "surveyed"),
                ScreenCards.pills(card("CAMPAIGN")).stream().map(ScreenCards.Pill::text).toList());
        assertEquals(List.of("screen-pill-finished", "screen-pill-plain", "screen-pill-stage"),
                ScreenCards.pills(card("CAMPAIGN")).stream().map(ScreenCards.Pill::style).toList());
        assertEquals(List.of("nothing cut", "locked"),
                ScreenCards.pills(card("ARENA")).stream().map(ScreenCards.Pill::text).toList());
        assertEquals(List.of("finished", "popup"),
                ScreenCards.pills(card("REWARD")).stream().map(ScreenCards.Pill::text).toList());
    }

    @Test
    void aLinkSaysTheLookItsButtonMustHave() {
        Map<String, GameScreen> screens = new LinkedHashMap<>(map().screens());
        screens.put("CAMPAIGN", screens.get("CAMPAIGN").from(Way.of(BotConstants.screen("HOME"), click("campaign"))
                .whenLooks(com.botmaker.sdk.api.screens.State.BADGE)));
        Link campaign = ScreenCards.links(ScreenGraph.of(screens)).stream()
                .filter(l -> l.to().equals("CAMPAIGN") && !l.back()).findFirst().orElseThrow();
        assertEquals("badge", campaign.look());
        assertTrue(ScreenCards.links(map()).stream().allMatch(l -> l.look().isEmpty()));
    }

    @Test
    void anInputsBoxIsPlacedOnAShotOfAnyShape() {
        Rect centre = new Rect(960, 540, 192, 108);
        assertEquals(new ScreenCanvas.Box(200, 112.5, 40, 22.5),
                ScreenCanvas.boxOn(centre, 1920, 1080, 400, 225), "16:9");
        assertEquals(new ScreenCanvas.Box(200, 112.5, 40, 22.5),
                ScreenCanvas.boxOn(centre, 1920, 1200, 400, 250), "16:10, no 9/16 assumed");
        assertEquals(new ScreenCanvas.Box(100, 200, 20, 20),
                ScreenCanvas.boxOn(new Rect(540, 1080, 108, 108), 1080, 2160, 200, 400), "portrait");
        assertEquals(new ScreenCanvas.Box(0, 12.5, 4, 4),
                ScreenCanvas.boxOn(new Rect(0, 0, 1, 1), 1920, 1080, 400, 250),
                "a 16:9 cut on a 16:10 shot: letterboxed, and never smaller than 4 pixels");
    }

    @Test
    void theConstantViewFindsItsScreenOnlyOnAMapThatRead() {
        GameScreen campaign = map().screens().get("CAMPAIGN");
        assertEquals(Optional.of("CAMPAIGN"), ScreenConstantView.constantOf(map(), Optional.of(campaign)));
        assertEquals(Optional.empty(), ScreenConstantView.constantOf(ScreenGraph.of(Map.of()),
                Optional.of(campaign)), "a map that did not read: the slot draws as written");
        assertEquals(Optional.empty(), ScreenConstantView.constantOf(map(), Optional.empty()));
    }

    @Test
    void theTreeIsLaidOutTheWayAWindowShowsBigger() {
        Map<Direction, ScreenCanvas.Shape> column = Map.of(Direction.RIGHT, new ScreenCanvas.Shape(600, 3000),
                Direction.DOWN, new ScreenCanvas.Shape(3000, 600));
        assertEquals(Direction.DOWN, ScreenCanvas.better(column, 1600, 800), "a wide window: Home's children in a row");
        assertEquals(Direction.RIGHT, ScreenCanvas.better(column, 500, 1400), "a tall window: in a column");
        assertEquals(Direction.DOWN, ScreenCanvas.better(column, 0, 0), "not measured yet: as the window opens");
        Map<Direction, ScreenCanvas.Shape> square = Map.of(Direction.RIGHT, new ScreenCanvas.Shape(800, 800),
                Direction.DOWN, new ScreenCanvas.Shape(800, 800));
        assertEquals(Direction.RIGHT, ScreenCanvas.better(square, 1600, 800), "a tie stays rightward");
        assertTrue(ScreenCanvas.turns(column, Direction.RIGHT, 1600, 800));
        Map<Direction, ScreenCanvas.Shape> near = Map.of(Direction.RIGHT, new ScreenCanvas.Shape(1000, 1000),
                Direction.DOWN, new ScreenCanvas.Shape(950, 950));
        assertEquals(Direction.DOWN, ScreenCanvas.better(near, 900, 900));
        assertFalse(ScreenCanvas.turns(near, Direction.RIGHT, 900, 900), "a little bigger: the tree stays as it is");
    }
}
