package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Did;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Kind;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The screen map read as a graph: Discovery's order (finish a screen an input opens before the next input of the
 * screen it was opened from), the ways a run may take, and what is not always there.
 */
class ScreenGraphTest {

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    private static Input click(String id) {
        return Input.click(picture(id));
    }

    /**
     * Home opens Campaign and Engineer; Campaign opens Map; Home's gold is left to try. Each entrance names its
     * screen by reference, as an editor holds it, so a test can change one screen and keep the others' ways in.
     */
    private static Map<String, GameScreen> firestone() {
        GameScreen home = GameScreen.named("Home").marker(picture("home"))
                .inputs(List.of(click("campaign"), click("engineer"), click("gold")));
        GameScreen campaign = GameScreen.named("Campaign").marker(picture("campaign_title"))
                .from(Way.of(BotConstants.screen("HOME"), click("campaign")))
                .inputs(List.of(click("claim").did(Did.IN_PLACE), click("map"), click("gems").never("spends gems")));
        GameScreen map = GameScreen.named("Map").marker(picture("map_title"))
                .from(Way.of(BotConstants.screen("CAMPAIGN"), click("map")))
                .inputs(List.of(Input.key(Key.ESCAPE).did(Did.NONE)));
        GameScreen engineer = GameScreen.named("Engineer").from(Way.of(BotConstants.screen("HOME"), click("engineer")));
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", home);
        screens.put("CAMPAIGN", campaign);
        screens.put("MAP", map);
        screens.put("ENGINEER", engineer);
        return screens;
    }

    @Test
    void aScreensPicturesAreItsMarkerItsInputsTheirLocksAndItsWayBackEachOnce() {
        Map<String, GameScreen> screens = firestone();
        screens.put("CAMPAIGN", screens.get("CAMPAIGN").back(click("close_x"))
                .inputs(List.of(click("claim"), click("claim").did(Did.NONE), click("arena").locked(picture("padlock")),
                        Input.key(Key.ESCAPE))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(List.of("campaign_title", "claim", "arena", "padlock", "close_x"), graph.pictures("CAMPAIGN"));
        assertEquals(List.of(), graph.pictures("ENGINEER"), "no marker yet, nothing cut");
        assertEquals(List.of(), graph.pictures("NOWHERE"));
    }

    @Test
    void aScreensLooksAreItsToo_andAPictureGoingTakesItOffTheScreenButNotOffAWayIn() {
        Map<String, GameScreen> screens = firestone();
        Input claim = click("claim").variants(List.of(Variant.of(picture("claim_badge"), State.BADGE)));
        screens.put("CAMPAIGN", screens.get("CAMPAIGN").inputs(List.of(claim, click("map")))
                .variants(List.of(ScreenVariant.named("Halloween").marker(picture("pumpkin"))
                        .inputs(List.of(click("candy"))))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(List.of("campaign_title", "pumpkin", "claim", "claim_badge", "map", "candy"),
                graph.pictures("CAMPAIGN"));
        assertTrue(graph.untried("CAMPAIGN").contains(click("candy")), "a look's input is tried like the screen's");

        GameScreen campaign = screens.get("CAMPAIGN");
        assertEquals(List.of(click("claim"), click("map")),
                ScreenGraph.without(campaign, "claim_badge").inputs(), "a button's look goes, the button stays");
        assertEquals(List.of(), ScreenGraph.without(campaign, "candy").variants().getFirst().inputs());
        assertEquals(null, ScreenGraph.without(campaign, "pumpkin").variants().getFirst().marker());
        assertEquals(campaign.from(), ScreenGraph.without(campaign, "campaign").from(), "a way in is kept");
        assertTrue(ScreenGraph.enteredBy(campaign, "campaign"), "so the picture it opens by stays used");
        assertFalse(ScreenGraph.enteredBy(campaign, "candy"));
        GameScreen withLooks = campaign.withVariant(ScreenVariant.named("Winter"))
                .withVariant(ScreenVariant.named("Halloween").marker(picture("bat")));
        assertEquals(List.of("Halloween", "Winter"), withLooks.variants().stream().map(ScreenVariant::name).toList(),
                "a look edited keeps its place");
    }

    @Test
    void aButtonLeadsWhereItsLookAndItsMethodSay() {
        Map<String, GameScreen> screens = firestone();
        screens.put("ENGINEER", screens.get("ENGINEER").alsoFrom(List.of(Way.of(BotConstants.screen("HOME"),
                click("campaign")).whenLooks(State.DISABLED))));
        ScreenGraph graph = ScreenGraph.of(screens);

        List<ScreenGraph.Lead> leads = graph.leads("HOME", click("campaign"));
        assertEquals(List.of("CAMPAIGN", "ENGINEER"), leads.stream().map(ScreenGraph.Lead::to).toList());
        assertEquals(State.DISABLED, leads.get(1).state());
    }

    @Test
    void anInputThatOpenedAScreenIsTriedAndTheTreeReadsOffEachScreensEntrance() {
        ScreenGraph graph = ScreenGraph.of(firestone());

        assertEquals(Optional.of("HOME"), graph.parent("CAMPAIGN"));
        assertEquals(List.of("CAMPAIGN", "ENGINEER"), graph.children("HOME"));
        assertEquals(Optional.of("CAMPAIGN"), graph.opens("HOME", click("campaign")));
        assertEquals(List.of(click("gold")), graph.untried("HOME"));
        assertTrue(graph.untried("CAMPAIGN").isEmpty(), "in place, opened and never are all tried");
        assertEquals(OptionalInt.of(2), graph.depth("MAP"));
    }

    @Test
    void aScreenOpenedIsFinishedBeforeTheNextInputOfTheOneItWasOpenedFrom() {
        Map<String, GameScreen> screens = firestone();
        ScreenGraph graph = ScreenGraph.of(screens);
        assertEquals(Optional.of("ENGINEER"), graph.unfinishedBelow("HOME"), "Engineer has no input cut yet");

        screens.put("ENGINEER", screens.get("ENGINEER").inputs(List.of(click("upgrade"))));
        assertEquals(Optional.of("ENGINEER"), ScreenGraph.of(screens).unfinishedBelow("HOME"));

        screens.put("ENGINEER", screens.get("ENGINEER").inputs(List.of(click("upgrade").did(Did.IN_PLACE))));
        screens.put("MAP", screens.get("MAP").inputs(List.of(click("node"))));
        assertEquals(Optional.of("MAP"), ScreenGraph.of(screens).unfinishedBelow("HOME"),
                "a grandchild left unfinished holds its grandparent too");
    }

    @Test
    void aBranchBegunIsFinishedBeforeAnotherIsBegun() {
        Map<String, GameScreen> screens = firestone();
        screens.put("MAP", screens.get("MAP").inputs(List.of(click("node"), click("zoom").did(Did.IN_PLACE))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(Optional.of("CAMPAIGN"), graph.branchBegun("ENGINEER"), "Campaign's branch still owes Map");
        assertEquals(Optional.empty(), graph.branchBegun("MAP"), "Map is on its own path");
    }

    @Test
    void aWayInOtherThanTheEntranceAndAWayBackMakeShorterPaths() {
        Map<String, GameScreen> screens = firestone();
        ScreenGraph tree = ScreenGraph.of(screens);
        List<ScreenGraph.Edge> viaHome = tree.pathTo("MAP", "ENGINEER", edge -> true).orElseThrow();
        assertEquals(List.of("CAMPAIGN", "HOME", "ENGINEER"), viaHome.stream().map(ScreenGraph.Edge::to).toList());
        assertTrue(viaHome.getFirst().back() && viaHome.getFirst().via() == null, "the default way out");

        screens.put("ENGINEER", screens.get("ENGINEER")
                .alsoFrom(List.of(Way.of(BotConstants.screen("MAP"), click("engineer_shortcut")))));
        List<ScreenGraph.Edge> shortcut = ScreenGraph.of(screens).pathTo("MAP", "ENGINEER", edge -> true)
                .orElseThrow();
        assertEquals(1, shortcut.size());
        assertEquals(click("engineer_shortcut"), shortcut.getFirst().via());

        assertEquals(Optional.empty(), tree.pathTo("MAP", "ENGINEER", edge -> !edge.back()), "no way back allowed");
        assertEquals(Optional.of(List.of()), tree.pathTo("MAP", "MAP", edge -> true));
    }

    @Test
    void aLoopOfEntrancesEndsEveryWalk() {
        GameScreen a = GameScreen.named("A").inputs(List.of(click("to_b")));
        GameScreen b = GameScreen.named("B").from(Way.of(BotConstants.screen("A"), click("to_b")))
                .inputs(List.of(click("to_a")));
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("A", a.from(Way.of(BotConstants.screen("B"), click("to_a"))));
        screens.put("B", b);
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(OptionalInt.empty(), graph.depth("A"));
        assertEquals(Optional.empty(), graph.unfinishedBelow("A"));
        assertEquals(Optional.empty(), graph.branchBegun("A"));
        assertEquals(ScreenGraph.Presence.LOCKED, graph.presence("A"), "no way in starts anywhere");
        assertTrue(graph.pathTo("A", "B", edge -> true).isPresent());

        screens.put("ROOT", GameScreen.named("Root"));
        screens.put("A", screens.get("A").alsoFrom(List.of(Way.of(BotConstants.screen("ROOT"), click("to_a")))));
        ScreenGraph reached = ScreenGraph.of(screens);
        assertEquals(ScreenGraph.Presence.ALWAYS, reached.presence("A"));
        assertEquals(ScreenGraph.Presence.ALWAYS, reached.presence("B"), "a loop met first does not lock the rest");
    }

    @Test
    void whatComesAndGoesOrIsLockedIsReadOffTheWaysIn() {
        Map<String, GameScreen> screens = firestone();
        GameScreen home = screens.get("HOME");
        screens.put("EVENT", GameScreen.named("Event").from(Way.of(home, click("event").sometimes())));
        screens.put("SHOP", GameScreen.named("Event shop").from(Way.of(BotConstants.screen("EVENT"), click("shop"))));
        screens.put("GUILD", GameScreen.named("Guild").from(Way.of(home, click("guild").locked(picture("padlock")))));
        screens.put("OFFER", GameScreen.named("Offer").back(click("close_x")).popup());
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(ScreenGraph.Presence.ALWAYS, graph.presence("MAP"));
        assertEquals(ScreenGraph.Presence.SOMETIMES, graph.presence("EVENT"));
        assertEquals(ScreenGraph.Presence.SOMETIMES, graph.presence("SHOP"), "behind a screen that comes and goes");
        assertEquals(ScreenGraph.Presence.LOCKED, graph.presence("GUILD"));
        assertEquals(ScreenGraph.Presence.ALWAYS, graph.presence("OFFER"));
        assertEquals(OptionalInt.empty(), graph.depth("OFFER"), "a popup is outside the tree");

        screens.put("GUILD", screens.get("GUILD").alsoFrom(List.of(Way.of(screens.get("CAMPAIGN"), click("guild")))));
        assertEquals(ScreenGraph.Presence.ALWAYS, ScreenGraph.of(screens).presence("GUILD"), "the best way in");
    }

    @Test
    void anInputThatComesAndGoesNeverSeenIsOwedWithoutHoldingItsScreen() {
        Map<String, GameScreen> screens = firestone();
        screens.put("MAP", screens.get("MAP").inputs(List.of(click("loot").sometimes())));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertFalse(graph.unfinished("MAP"));
        assertEquals(List.of(
                "Home: untried click gold",
                "Map: never seen to try click loot (comes and goes)",
                "Engineer: marker",
                "Engineer: no input cut yet"), graph.owed());
    }

    @Test
    void aFinishedScreenIsAMenuWhenEveryInputItTriedOpenedOne() {
        Map<String, GameScreen> screens = firestone();
        screens.put("HOME", screens.get("HOME").inputs(List.of(click("campaign"), click("engineer"),
                Input.scroll(picture("left_column"), -5).did(Did.IN_PLACE),
                click("guild").locked(picture("padlock")))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(Optional.of(ScreenGraph.Shape.MENU), graph.shape("HOME"), "a scroll or a locked tile is no activity");
        assertEquals(Optional.of(ScreenGraph.Shape.ACTIVITY), graph.shape("CAMPAIGN"));
        assertEquals(Optional.empty(), graph.shape("ENGINEER"));
    }

    @Test
    void aReferenceNamesItsConstantAndTheEdgesRunBothWays() {
        ScreenGraph graph = ScreenGraph.of(firestone());

        assertEquals(Optional.of("CAMPAIGN"), graph.constantOf(BotConstants.screen("CAMPAIGN")));
        assertEquals(Optional.empty(), graph.constantOf(BotConstants.screen("GONE")));
        assertEquals("Map", graph.shown("MAP"));
        assertTrue(graph.edges().contains(new ScreenGraph.Edge("MAP", "CAMPAIGN", null, true)));
        assertTrue(graph.edges().contains(new ScreenGraph.Edge("HOME", "CAMPAIGN", click("campaign"), false)));
    }

    /** What a bot's {@code Screens} holds, read by {@link ScreenMaps#read}. */
    static final class Screens {
        static final GameScreen HOME = GameScreen.named("Home").inputs(List.of(click("campaign")));
        static final GameScreen CAMPAIGN = GameScreen.named("Campaign").from(Way.of(HOME, click("campaign")));
        static GameScreen notFinal = GameScreen.named("Not a constant");
    }

    @Test
    void theMapIsTheHoldersScreenConstantsInDeclaredOrder() {
        ScreenGraph graph = ScreenMaps.read(Screens.class);

        assertEquals(List.of("HOME", "CAMPAIGN"), List.copyOf(graph.screens().keySet()));
        assertEquals(Optional.of("HOME"), graph.parent("CAMPAIGN"), "a constant named inside another is itself");
        assertEquals(Optional.empty(), ScreenMaps.beside(String.class));
    }

    /**
     * A way keeps a copy of the input it was found by; a lock, a look or a condition put on the screen's input later
     * is read off the screen's input, not off that copy (2026-10-15).
     */
    @Test
    void aWayDoesTheInputAsItsScreenHoldsItNow() {
        Map<String, GameScreen> screens = firestone();
        Input badged = click("campaign").variants(List.of(Variant.of(picture("campaign_badge"), State.BADGE)));
        screens.put("HOME", screens.get("HOME").inputs(List.of(badged, click("engineer").locked(picture("padlock")))));
        ScreenGraph graph = ScreenGraph.of(screens);

        ScreenGraph.Edge campaign = graph.edges().stream().filter(e -> e.to().equals("CAMPAIGN")).findFirst().orElseThrow();
        assertEquals(badged, campaign.via(), "the screen's input, with its look");
        assertEquals(ScreenGraph.Presence.LOCKED, graph.presence("ENGINEER"), "locked on Home, not on the way's copy");
        Way gone = Way.of(BotConstants.screen("HOME"), click("nowhere"));
        assertEquals(click("nowhere"), graph.via(gone), "a copy of an input the screen lost stands for itself");
        assertEquals(click("campaign"), ScreenGraph.bare(badged.did(Did.IN_PLACE).kind(Kind.CHORE)));
        assertEquals(Input.scroll(picture("list"), -3), ScreenGraph.bare(Input.scroll(picture("list"), -3).sometimes()));
    }

    @Test
    void theScreensAWayIntoAScreenIsOnAreItsFromAndAlsoFrom() {
        Map<String, GameScreen> screens = firestone();
        screens.put("MAP", screens.get("MAP").alsoFrom(List.of(Way.of(BotConstants.screen("HOME"), click("map")))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(List.of("CAMPAIGN", "HOME"), List.copyOf(graph.opensFrom("MAP")));
        assertEquals(List.of(), List.copyOf(graph.opensFrom("HOME")));
    }

    /** A popup shows over any screen: its inputs wait for no branch of the tree (2026-10-15). */
    @Test
    void aPopupIsOnNoBranch() {
        Map<String, GameScreen> screens = firestone();
        screens.put("CAMPAIGN", screens.get("CAMPAIGN").inputs(List.of(click("claim").did(Did.IN_PLACE), click("map"),
                click("loot"))));
        screens.put("OFFER", GameScreen.named("Offer").marker(picture("offer")).inputs(List.of(click("buy"))).popup());
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(Optional.of("CAMPAIGN"), graph.branchBegun("ENGINEER"), "a tree screen waits for Campaign");
        assertEquals(Optional.empty(), graph.branchBegun("OFFER"), "the popup does not");
    }

    /** A screen that says why it has no marker owes it no more, and holds no other screen back. */
    @Test
    void aMarkerTheScreenSaysWhyItLacksIsOwedNoMore() {
        assertTrue(ScreenGraph.markerWithReason("marker — every icon here animates"));
        assertTrue(ScreenGraph.markerWithReason("Marker - it scrolls"));
        assertFalse(ScreenGraph.markerWithReason("marker"));
        assertFalse(ScreenGraph.markerWithReason("marker-less icons"));
        assertFalse(ScreenGraph.markerWithReason("weekly tab picture"));

        Map<String, GameScreen> screens = firestone();
        assertEquals(Optional.of("ENGINEER"), ScreenGraph.of(screens).owingMarker());
        screens.put("ENGINEER", screens.get("ENGINEER").owes(List.of("marker — every icon animates", "its timer")));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(Optional.empty(), graph.owingMarker());
        assertTrue(graph.owed().contains("Engineer: its timer"), graph.owed().toString());
        assertTrue(graph.owed().stream().noneMatch(line -> line.startsWith("Engineer: marker")), graph.owed().toString());
    }

    /**
     * A screen finished sends the game up to the nearest screen above with work left, past finished ones; a screen
     * with work left, or below which some is left, goes nowhere (2026-10-14).
     */
    @Test
    void aFinishedScreenGoesUpToTheNearestScreenWithWorkLeft() {
        ScreenGraph open = upMap(click("mail"));
        assertEquals(Optional.of("HOME"), open.upTo("MAP"), "past Campaign, finished, to Home's mail");
        assertEquals(Optional.of("HOME"), open.upTo("CAMPAIGN"));
        assertEquals(Optional.empty(), open.upTo("HOME"), "Home has work left");

        ScreenGraph done = upMap(click("mail").did(Did.NONE));
        assertEquals(Optional.of("HOME"), done.upTo("MAP"), "all finished: the top screen");
        assertEquals(Optional.empty(), done.upTo("HOME"), "the top screen goes nowhere");

        Map<String, GameScreen> screens = new LinkedHashMap<>(open.screens());
        screens.put("MAP", screens.get("MAP").inputs(List.of(click("zoom"))));
        assertEquals(Optional.empty(), ScreenGraph.of(screens).upTo("MAP"), "Map's zoom is untried");
        assertEquals(Optional.empty(), ScreenGraph.of(screens).upTo("CAMPAIGN"), "Map, below it, is not done");
    }

    /** Home ▸ Campaign ▸ Map, each tried out but Home's {@code mail}. */
    private static ScreenGraph upMap(Input mail) {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").marker(picture("home")).inputs(List.of(click("campaign"), mail)));
        screens.put("CAMPAIGN", GameScreen.named("Campaign").marker(picture("campaign_title"))
                .from(Way.of(BotConstants.screen("HOME"), click("campaign")))
                .inputs(List.of(click("map"), click("claim").did(Did.IN_PLACE))));
        screens.put("MAP", GameScreen.named("Map").marker(picture("map_title"))
                .from(Way.of(BotConstants.screen("CAMPAIGN"), click("map"))).inputs(List.of(click("x").did(Did.NONE))));
        return ScreenGraph.of(screens);
    }
}
