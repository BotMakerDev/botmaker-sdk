package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.toolkit.graph.GraphLayout;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.map.ScreenCards.RowState;
import com.botmaker.sdk.plugin.map.ScreenCards.State;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.marks;
import static com.botmaker.sdk.internal.screens.MapFixtures.picture;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What 🗺 Screens draws of a map: each card's state, progress and badges, each edge, each input's row. */
class ScreenCardsTest {

    /**
     * Home opens Campaign (finished, a task and a never) and Engineer (owing its marker); Campaign's map opens Map,
     * which Home also reaches by a hotkey; Arena is behind a locked input; Reward is a popup.
     */
    private static Map<String, MapScreen> screens() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("Home").markers(marks("home")), click("campaign"), click("engineer"),
                click("gold"), Hotkey.of(Key.M), click("arena").locked(picture("padlock")), click("event").sometimes()));
        screens.put("CAMPAIGN", screen(Setup.named("Campaign").markers(marks("campaign_title"))
                        .from(ref("HOME", "campaign")),
                click("claim").tried(), click("map"),
                click("gems").never("spends gems")));
        screens.put("MAP", screen(Setup.named("Map").markers(marks("map_title")).from(ref("CAMPAIGN", "map"))
                .alsoFrom(List.of(Way.of(ref("HOME", "key_m")))), Hotkey.of(Key.ESCAPE).tried()));
        screens.put("ENGINEER", screen(Setup.named("Engineer").from(ref("HOME", "engineer"))));
        screens.put("ARENA", screen(Setup.named("Arena").markers(marks("arena_title")).from(ref("HOME", "arena"))));
        screens.put("REWARD", screen(Setup.named("Reward").markers(marks("reward")).back(click("close_x")).popup()));
        return screens;
    }

    private static ScreenGraph map() {
        return ScreenGraph.of(screens());
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
        assertEquals("surveyed", campaign.stage());
        assertEquals("campaign_title", campaign.marker());

        assertEquals(State.OWES, card("ENGINEER").state());
        assertEquals("owes its marker", card("ENGINEER").said());
        assertEquals(State.FINISHED, card("REWARD").state(), "a popup needs no input");
        assertTrue(card("REWARD").popup());
    }

    @Test
    void aScreenIsFoundByItsNameConstantInputsPicturesAndNotes() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("MARKET", screen(Setup.named("Market").notes("  Beer restocks at noon. "), click("beer_buy"),
                Strip.of(Scroll.wheel(-5), List.of(picture("stall_a"), picture("stall_b"))), Hotkey.of(Key.M)));
        assertEquals(List.of("Market", "MARKET", "input beer_buy", "input stall_a_strip", "input key_m",
                        "picture beer_buy", "picture stall_a", "picture stall_b", "notes Beer restocks at noon."),
                ScreenCards.terms(ScreenGraph.of(screens), "MARKET"));
        assertEquals(List.of("Home", "HOME", "input campaign", "input engineer", "input gold", "input key_m",
                        "input arena", "input event", "picture campaign", "picture engineer", "picture gold",
                        "picture arena", "picture event"),
                ScreenCards.terms(map(), "HOME"), "a key has no picture, a screen with no notes no notes term");
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
        assertEquals("tried", campaign.getFirst().result());
        assertEquals("", campaign.getFirst().kind());
        assertEquals("claim", campaign.getFirst().field());
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
        Map<String, MapScreen> screens = screens();
        screens.put("CAMPAIGN", screens.get("CAMPAIGN").setup(s -> s.fromWhen(
                ScreenTypes.condition("Firestone::eventOn"))));
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
        Map<String, MapScreen> screens = screens();
        screens.put("OFFER", MapScreen.of(Setup.named("Offer").markers(marks("offer")).popup()));
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
        Map<String, MapScreen> screens = screens();
        screens.put("CAMPAIGN", screens.get("CAMPAIGN").setup(s -> s.fromLooks(
                com.botmaker.sdk.api.screens.State.BADGE)));
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
        Setup campaign = map().screens().get("CAMPAIGN").setup();
        assertEquals(Optional.of("CAMPAIGN"), ScreenConstantView.constantOf(map(), Optional.of(campaign)));
        assertEquals(Optional.empty(), ScreenConstantView.constantOf(ScreenGraph.of(Map.of()),
                Optional.of(campaign)), "a map that did not read: the slot draws as written");
        assertEquals(Optional.empty(), ScreenConstantView.constantOf(map(), Optional.empty()));
    }

    /** A card opened stays open for that project's screen only, until it is shut. */
    @Test
    void aCardOpenedStaysOpenForItsProjectAndScreenOnly() {
        String here = ScreenConstantView.key(Path.of("/bots/a/../a"), "HOME");
        assertEquals(here, ScreenConstantView.key(Path.of("/bots/a"), "HOME"), "the same project however spelled");
        assertFalse(ScreenConstantView.isOpen(here), "shut until opened");
        try {
            ScreenConstantView.setOpen(here, true);
            assertTrue(ScreenConstantView.isOpen(here));
            assertFalse(ScreenConstantView.isOpen(ScreenConstantView.key(Path.of("/bots/b"), "HOME")));
            assertFalse(ScreenConstantView.isOpen(ScreenConstantView.key(Path.of("/bots/a"), "MARKET")));
            ScreenConstantView.setOpen(here, false);
            assertFalse(ScreenConstantView.isOpen(here));
        } finally {
            ScreenConstantView.setOpen(here, false);
        }
    }

    /** Home's left column: drawn whole over where its members were cut, in the list's order, one with no place too. */
    @Test
    void aListIsDrawnAsAStripOfAllItsMembersOverWhereTheySit() {
        ImageTemplate tavern = picture("tavern").at(new Rect(10, 240, 100, 100));
        ImageTemplate arena = picture("arena").at(new Rect(12, 340, 100, 100));
        ImageTemplate library = picture("library");
        Setup setup = Setup.named("Home").markers(marks("home"));
        MapScreen home = screen(setup, Button.of(tavern), Button.of(arena), Button.of(library).sometimes(),
                click("mail"), Strip.of(Scroll.wheel(-5), List.of(arena, tavern, library)));
        ScreenGraph graph = ScreenGraph.of(Map.of("HOME", home));
        List<ScreenCards.Row> rows = ScreenCards.rows(graph, "HOME");

        List<ScreenCards.Strip> strips = ScreenCards.strips(graph, "HOME", rows);
        assertEquals(1, strips.size());
        ScreenCards.Strip column = strips.getFirst();
        assertEquals(new Rect(10, 240, 102, 200), column.place());
        assertTrue(column.down(), "taller than wide: a column");
        assertEquals(List.of("arena", "tavern", "library"),
                column.members().stream().map(ScreenCards.Row::picture).toList(), "the list's order, not the inputs'");
        assertTrue(column.has(rows.get(0)));
        assertFalse(column.has(rows.get(3)), "mail is boxed on the shot as before");

        MapScreen nowhere = screen(setup, Button.of(tavern), Button.of(arena), Button.of(library).sometimes(),
                click("mail"), Strip.of(Scroll.wheel(-5), List.of(library, picture("mail"))));
        assertEquals(List.of(), ScreenCards.strips(ScreenGraph.of(Map.of("HOME", nowhere)), "HOME", rows),
                "no member has a place: no strip");
    }

    /** What the class form says beyond what an input did: a button's copies, a strip's scroll as measured. */
    @Test
    void aRowSaysAButtonsCopiesAndAStripsScroll() {
        Setup setup = Setup.named("Home").markers(marks("home"));
        MapScreen home = screen(setup, Button.of(picture("claim")).repeated(), click("mail"),
                Strip.of(Scroll.wheel(-5), List.of(picture("tavern"))),
                Strip.of(Scroll.wheel(3).step(120).settle(Duration.ofMillis(400)), List.of(picture("arena"))));
        List<String> traits = ScreenCards.rows(ScreenGraph.of(Map.of("HOME", home)), "HOME").stream()
                .map(ScreenCards.Row::traits).toList();
        assertEquals(List.of("repeated", "", "wheel -5, not measured", "wheel 3, step 120px, settles in 400 ms"),
                traits);
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

    @Test
    void aDragReadBackOnlyMovesTheCards() {
        Card home = new Card("HOME", "Home", "home", State.FINISHED, "", "", false, true, null,
                ScreenGraph.Presence.ALWAYS, 0, 0, "", null);
        Card shop = new Card("SHOP", "Shop", "shop", State.FINISHED, "", "", false, false, null,
                ScreenGraph.Presence.ALWAYS, 0, 0, "", null);
        List<Card> laid = List.of(home, shop);
        assertTrue(ScreenCanvas.onlyMoved(laid, List.of(home, shop.withAt(new Point(300, 40)))), "a card dropped");
        assertFalse(ScreenCanvas.onlyMoved(laid, List.of(home, new Card("SHOP", "Shop", "shop", State.OWES, "", "",
                false, false, null, ScreenGraph.Presence.ALWAYS, 0, 0, "", null))), "a card that changed is drawn again");
        assertFalse(ScreenCanvas.onlyMoved(laid, List.of(home)), "a card gone");
    }

    private static Link way(String from, String to, boolean entrance, boolean back) {
        return new Link(from, to, null, null, entrance, back, "", "");
    }

    @Test
    void theMapIsRankedByEveryWayEntrancesFirstPopupsApart() {
        List<Link> links = List.of(way("SHOP", "HOME", false, true), way("HOME", "BAG", false, false),
                way("HOME", "SHOP", true, false), way("SHOP", "BAG", true, false), way("SHOP", "REWARD", true, false));

        assertEquals(List.of(GraphLayout.Link.of("HOME", "SHOP"), GraphLayout.Link.of("SHOP", "BAG"),
                        GraphLayout.Link.of("HOME", "BAG"), GraphLayout.Link.of("SHOP", "HOME")),
                ScreenCanvas.ways(links, Set.of("REWARD")));
    }

    @Test
    void aFoldHidesWhatItsEntrancesOpenAndOnDownNeverTheRoot() {
        List<Link> links = List.of(way("HOME", "SHOP", true, false), way("SHOP", "BAG", true, false),
                way("BAG", "HOME", true, false), way("HOME", "BAG", false, false), way("HOME", "MAIL", true, false));

        assertEquals(Set.of("BAG"), ScreenCanvas.foldedAway(links, Set.of("SHOP"), "HOME"));
        assertEquals(Set.of("SHOP", "BAG", "MAIL"), ScreenCanvas.foldedAway(links, Set.of("HOME"), "HOME"));
        List<Link> ring = List.of(way("SHOP", "BAG", true, false), way("BAG", "SHOP", true, false));
        assertEquals(Set.of("BAG"), ScreenCanvas.foldedAway(ring, Set.of("SHOP"), "HOME"), "a fold never hides itself");
    }
}
