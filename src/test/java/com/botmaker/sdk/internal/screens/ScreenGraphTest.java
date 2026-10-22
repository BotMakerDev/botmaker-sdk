package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.key;
import static com.botmaker.sdk.internal.screens.MapFixtures.marks;
import static com.botmaker.sdk.internal.screens.MapFixtures.picture;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static com.botmaker.sdk.internal.screens.MapFixtures.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The screen map read as a graph: Discovery's order (breadth first), the ways a run may take, and what is not always
 * there. Each input is a field of its screen, and a way names the field it goes through.
 */
class ScreenGraphTest {

    /**
     * Home opens Campaign and Engineer; Campaign opens Map; Home's gold is left to try. Each way in names its input
     * by reference, as an editor holds it, so a test can change one screen and keep the others' ways in.
     */
    private static Map<String, MapScreen> firestone() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("Home").markers(marks("home")), click("campaign"), click("engineer"),
                click("gold")));
        screens.put("CAMPAIGN", screen(Setup.named("Campaign").markers(marks("campaign_title"))
                        .from(ref("HOME", "campaign")),
                click("claim").tried(), click("map"), click("gems").never("spends gems")));
        screens.put("MAP", screen(Setup.named("Map").markers(marks("map_title")).from(ref("CAMPAIGN", "map")),
                key(Key.ESCAPE).tried()));
        screens.put("ENGINEER", screen(Setup.named("Engineer").from(ref("HOME", "engineer"))));
        return screens;
    }

    @Test
    void aScreensPicturesAreItsMarkerItsInputsTheirLocksAndItsWayBackEachOnce() {
        Map<String, MapScreen> screens = firestone();
        screens.put("CAMPAIGN", screen(screens.get("CAMPAIGN").setup().back(click("close_x")), click("claim"),
                click("arena").locked(picture("padlock")), key(Key.ESCAPE)));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(List.of("campaign_title", "claim", "arena", "padlock", "close_x"), graph.pictures("CAMPAIGN"));
        assertEquals(List.of(), graph.pictures("ENGINEER"), "no marker yet, nothing cut");
        assertEquals(List.of(), graph.pictures("NOWHERE"));
    }

    @Test
    void aScreensLooksAreItsToo_andAPictureGoingTakesItOffTheScreenButNotOffAWayIn() {
        Map<String, MapScreen> screens = firestone();
        Button claim = click("claim").looks(List.of(Look.of(picture("claim_badge"), State.BADGE)));
        screens.put("CAMPAIGN", screen(screens.get("CAMPAIGN").setup().variants(List.of(ScreenVariant.named("Halloween")
                .markers(marks("pumpkin")).inputs(List.of(ref("CAMPAIGN", "candy"))))), claim, click("map"),
                click("candy").sometimes()));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(List.of("campaign_title", "pumpkin", "claim", "claim_badge", "map", "candy"),
                graph.pictures("CAMPAIGN"));
        assertEquals(List.of("candy"), graph.unseen("CAMPAIGN"), "a look's input comes and goes with it");

        MapScreen campaign = screens.get("CAMPAIGN");
        assertEquals(click("claim"), ScreenGraph.without(campaign, "claim_badge").inputs().get("claim"),
                "a button's look goes, the button stays");
        MapScreen candyless = ScreenGraph.without(campaign, "candy");
        assertFalse(candyless.inputs().containsKey("candy"));
        assertEquals(List.of(), candyless.variants().getFirst().inputs(), "the look names it no more");
        assertEquals(List.of(), ScreenGraph.without(campaign, "pumpkin").variants().getFirst().markers());
        assertEquals(campaign.from(), ScreenGraph.without(campaign, "campaign").from(), "a way in is kept");
        assertTrue(graph.enteredBy("CAMPAIGN", "campaign"), "so the picture it opens by stays used");
        assertFalse(graph.enteredBy("CAMPAIGN", "candy"));
        Setup withLooks = campaign.setup().withVariant(ScreenVariant.named("Winter"))
                .withVariant(ScreenVariant.named("Halloween").markers(marks("bat")));
        assertEquals(List.of("Halloween", "Winter"), withLooks.variants().stream().map(ScreenVariant::name).toList(),
                "a look edited keeps its place");

        MapScreen stripped = screen("Home", click("tavern"), click("arena"), click("campaign"),
                Strip.of(Scroll.wheel(-5), marks("tavern", "arena", "campaign")));
        MapScreen arenaless = ScreenGraph.without(stripped, "arena");
        assertEquals(List.of("tavern", "campaign"), arenaless.strips().get("tavern_strip").members().stream()
                .map(ImageTemplate::id).toList(), "a picture gone is off its strip");
        assertEquals(Map.of(), ScreenGraph.without(ScreenGraph.without(arenaless, "tavern"), "campaign").strips(),
                "a strip of no member is none");
    }

    @Test
    void aButtonLeadsWhereItsLookItsCopyAndItsMethodSay() {
        Map<String, MapScreen> screens = firestone();
        screens.put("ENGINEER", screens.get("ENGINEER").setup(s -> s.alsoFrom(List.of(
                Way.of(ref("HOME", "campaign")).whenLooks(State.DISABLED),
                Way.of(ref("HOME", "campaign")).pick(Pick.nth(2, Order.NORTH))))));
        ScreenGraph graph = ScreenGraph.of(screens);

        List<ScreenGraph.Lead> leads = graph.leads("HOME", "campaign");
        assertEquals(List.of("CAMPAIGN", "ENGINEER", "ENGINEER"), leads.stream().map(ScreenGraph.Lead::to).toList());
        assertEquals(State.DISABLED, leads.get(1).state());
        assertEquals(Pick.nth(2, Order.NORTH), leads.get(2).pick());
        ScreenGraph.Edge copy = graph.edges().stream().filter(e -> e.pick() != null).findFirst().orElseThrow();
        assertEquals("campaign #2 from the north", copy.label());
    }

    @Test
    void anInputThatOpenedAScreenIsTriedAndTheTreeReadsOffEachScreensWayIn() {
        ScreenGraph graph = ScreenGraph.of(firestone());

        assertEquals(Optional.of("HOME"), graph.parent("CAMPAIGN"));
        assertEquals(List.of("CAMPAIGN", "ENGINEER"), graph.children("HOME"));
        assertEquals(Optional.of("CAMPAIGN"), graph.opens("HOME", "campaign"));
        assertEquals(List.of("gold"), graph.untried("HOME"));
        assertTrue(graph.untried("CAMPAIGN").isEmpty(), "in place, opened and never are all tried");
        assertEquals(OptionalInt.of(2), graph.depth("MAP"));
        assertEquals(Set.of("CAMPAIGN", "HOME"), graph.above("MAP"));
        assertEquals(Set.of("CAMPAIGN", "HOME"), graph.dependsOn("MAP"));
    }

    /** Breadth first (the user's call, 2026-10-16): a screen's own inputs, then the screens they opened, shallowest first. */
    @Test
    void discoveryExploresTheShallowestScreenWithInputsLeftFirst() {
        Map<String, MapScreen> screens = firestone();
        assertEquals(Optional.of("HOME"), ScreenGraph.of(screens).nextToExplore(),
                "Home's gold before Engineer, which it opened");

        screens.put("HOME", with(screens.get("HOME"), click("gold").tried()));
        screens.put("MAP", with(screens.get("MAP"), click("node")));
        assertEquals(Optional.of("ENGINEER"), ScreenGraph.of(screens).nextToExplore(), "Engineer, a step down, "
                + "before Map, two");
        Map<String, MapScreen> fickle = new LinkedHashMap<>(screens);
        fickle.put("HOME", with(fickle.get("HOME"), click("engineer").sometimes()));
        assertEquals(Optional.of("MAP"), ScreenGraph.of(fickle).nextToExplore(),
                "a screen whose way in comes and goes waits for every other");
        fickle.put("HOME", with(fickle.get("HOME"), click("engineer").locked(picture("padlock"))));
        fickle.put("MAP", with(fickle.get("MAP"), click("node").tried()));
        assertEquals(Optional.empty(), ScreenGraph.of(fickle).nextToExplore(), "a locked screen is none to go to");

        screens.put("ENGINEER", with(screens.get("ENGINEER"), click("upgrade").tried()));
        assertEquals(Optional.of("MAP"), ScreenGraph.of(screens).nextToExplore());
        screens.put("MAP", with(screens.get("MAP"), click("node").tried()));
        assertEquals(Optional.empty(), ScreenGraph.of(screens).nextToExplore(), "nothing left");
    }

    /** Help, rules and leaderboards only tell: tried last on their screen, and explored after every other screen. */
    @Test
    void informationIsTriedAndExploredLast() {
        Map<String, MapScreen> screens = firestone();
        Input ranks = click("ranks").info();
        screens.put("HOME", screen(screens.get("HOME").setup(), click("help_button"), ranks, click("campaign"),
                click("engineer"), click("gold")));
        assertEquals(List.of("gold", "help_button", "ranks"), ScreenGraph.of(screens).untried("HOME"));
        assertTrue(ScreenGraph.isInfo(null, click("i")) && ScreenGraph.isInfo(null, click("how_to_play")));
        assertTrue(ScreenGraph.isInfo("infoLuck", click("x")), "a field's words count as a picture's");
        assertFalse(ScreenGraph.isInfo(null, click("rank_up")) || ScreenGraph.isInfo(null, click("tier_i_reward"))
                || ScreenGraph.isInfo(null, click("guide_arrow")), "a rank-up or a tier's button does work");

        screens.put("HOME", with(screens.get("HOME"), ranks.tried(), click("gold").tried()));
        Map<String, MapScreen> withHelp = new LinkedHashMap<>();
        withHelp.put("HOME", screens.get("HOME"));
        withHelp.put("RULES", screen(Setup.named("Rules").from(ref("HOME", "help_button"))));
        screens.forEach(withHelp::putIfAbsent);
        withHelp.put("MAP", with(withHelp.get("MAP"), click("node")));
        ScreenGraph graph = ScreenGraph.of(withHelp);
        assertEquals(Optional.of("ENGINEER"), graph.nextToExplore(), "Rules, declared first, opened by help, waits");
        withHelp.put("ENGINEER", with(withHelp.get("ENGINEER"), click("upgrade").tried()));
        assertEquals(Optional.of("MAP"), ScreenGraph.of(withHelp).nextToExplore(), "a deeper screen before help");
        withHelp.put("MAP", with(withHelp.get("MAP"), click("node").tried()));
        assertEquals(Optional.of("RULES"), ScreenGraph.of(withHelp).nextToExplore());
    }

    @Test
    void aWayInOtherThanTheTreesAndAWayBackMakeShorterPaths() {
        Map<String, MapScreen> screens = firestone();
        ScreenGraph tree = ScreenGraph.of(screens);
        List<ScreenGraph.Edge> viaHome = tree.pathTo("MAP", "ENGINEER", edge -> true).orElseThrow();
        assertEquals(List.of("CAMPAIGN", "HOME", "ENGINEER"), viaHome.stream().map(ScreenGraph.Edge::to).toList());
        assertTrue(viaHome.getFirst().back() && viaHome.getFirst().via() == null, "the default way out");

        screens.put("MAP", with(screens.get("MAP"), click("engineer_shortcut")));
        screens.put("ENGINEER", screens.get("ENGINEER").setup(s -> s.alsoFrom(List.of(
                Way.of(ref("MAP", "engineer_shortcut"))))));
        List<ScreenGraph.Edge> shortcut = ScreenGraph.of(screens).pathTo("MAP", "ENGINEER", edge -> true)
                .orElseThrow();
        assertEquals(1, shortcut.size());
        assertEquals(click("engineer_shortcut"), shortcut.getFirst().via());
        assertEquals("engineer_shortcut", shortcut.getFirst().input());

        assertEquals(Optional.empty(), tree.pathTo("MAP", "ENGINEER", edge -> !edge.back()), "no way back allowed");
        assertEquals(Optional.of(List.of()), tree.pathTo("MAP", "MAP", edge -> true));
    }

    /** Root to Target through Slow, taking {@code slow}, or through Fast1 and Fast2, each taking {@code fast}. */
    private static Map<String, MapScreen> slowOrFast(Duration slow, Duration fast) {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("ROOT", screen("Root", click("to_slow"), click("to_fast")));
        screens.put("SLOW", screen(Setup.named("Slow").from(ref("ROOT", "to_slow")).takes(slow), click("slow_on")));
        screens.put("FAST1", screen(Setup.named("Fast1").from(ref("ROOT", "to_fast")).takes(fast),
                click("fast_on")));
        screens.put("FAST2", screen(Setup.named("Fast2").from(ref("FAST1", "fast_on")).takes(fast),
                click("fast_end")));
        screens.put("TARGET", screen(Setup.named("Target").from(ref("SLOW", "slow_on"))
                .alsoFrom(List.of(Way.of(ref("FAST2", "fast_end"))))));
        return screens;
    }

    /** A walk takes the quicker path by each screen's measured time, not the one of fewest steps (2026-10-22). */
    @Test
    void aSlowEdgeLosesToTwoFastOnes() {
        ScreenGraph graph = ScreenGraph.of(slowOrFast(Duration.ofSeconds(5), Duration.ofMillis(200)));
        List<ScreenGraph.Edge> path = graph.pathTo("ROOT", "TARGET", edge -> true).orElseThrow();
        assertEquals(List.of("FAST1", "FAST2", "TARGET"), path.stream().map(ScreenGraph.Edge::to).toList(),
                "0.5 + 0.5 + 1.3 s beats 5.3 + 1.3 s");
    }

    /** A map with no time measured walks the path of fewest steps, the first of them in map order. */
    @Test
    void noCostsGiveTheBfsPath() {
        ScreenGraph graph = ScreenGraph.of(slowOrFast(null, null));
        assertEquals(List.of("SLOW", "TARGET"), graph.pathTo("ROOT", "TARGET", edge -> true).orElseThrow().stream()
                .map(ScreenGraph.Edge::to).toList());
        assertEquals(List.of("CAMPAIGN", "HOME", "ENGINEER"), ScreenGraph.of(firestone())
                .pathTo("MAP", "ENGINEER", edge -> true).orElseThrow().stream().map(ScreenGraph.Edge::to).toList());
    }

    @Test
    void aLoopOfWaysInEndsEveryWalk() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").from(ref("B", "to_a")), click("to_b")));
        screens.put("B", screen(Setup.named("B").from(ref("A", "to_b")), click("to_a")));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(OptionalInt.empty(), graph.depth("A"));
        assertEquals(Optional.empty(), graph.nextToExplore(), "a loop starts nowhere: no screen to go to");
        assertEquals(ScreenGraph.Presence.LOCKED, graph.presence("A"), "no way in starts anywhere");
        assertTrue(graph.pathTo("A", "B", edge -> true).isPresent());

        screens.put("ROOT", screen("Root", click("to_a")));
        screens.put("A", screens.get("A").setup(s -> s.alsoFrom(List.of(Way.of(ref("ROOT", "to_a"))))));
        ScreenGraph reached = ScreenGraph.of(screens);
        assertEquals(ScreenGraph.Presence.ALWAYS, reached.presence("A"));
        assertEquals(ScreenGraph.Presence.ALWAYS, reached.presence("B"), "a loop met first does not lock the rest");
    }

    @Test
    void whatComesAndGoesOrIsLockedIsReadOffTheWaysIn() {
        Map<String, MapScreen> screens = firestone();
        screens.put("HOME", with(screens.get("HOME"), click("event").sometimes(),
                click("guild").locked(picture("padlock"))));
        screens.put("CAMPAIGN", with(screens.get("CAMPAIGN"), click("guild")));
        screens.put("EVENT", screen(Setup.named("Event").from(ref("HOME", "event")), click("shop")));
        screens.put("SHOP", screen(Setup.named("Event shop").from(ref("EVENT", "shop"))));
        screens.put("GUILD", screen(Setup.named("Guild").from(ref("HOME", "guild"))));
        screens.put("OFFER", screen(Setup.named("Offer").back(click("close_x")).popup()));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(ScreenGraph.Presence.ALWAYS, graph.presence("MAP"));
        assertEquals(ScreenGraph.Presence.SOMETIMES, graph.presence("EVENT"));
        assertEquals(ScreenGraph.Presence.SOMETIMES, graph.presence("SHOP"), "behind a screen that comes and goes");
        assertEquals(ScreenGraph.Presence.LOCKED, graph.presence("GUILD"));
        assertEquals(ScreenGraph.Presence.ALWAYS, graph.presence("OFFER"));
        assertEquals(OptionalInt.empty(), graph.depth("OFFER"), "a popup is outside the tree");

        screens.put("GUILD", screens.get("GUILD").setup(s -> s.alsoFrom(List.of(Way.of(ref("CAMPAIGN", "guild"))))));
        assertEquals(ScreenGraph.Presence.ALWAYS, ScreenGraph.of(screens).presence("GUILD"), "the best way in");
    }

    /** A strip's traits are its members' defaults (the user's call, 2026-10-16). */
    @Test
    void aStripThatComesAndGoesMakesEachMemberComeAndGo() {
        Map<String, MapScreen> screens = firestone();
        screens.put("HOME", with(screens.get("HOME"),
                Strip.of(Scroll.wheel(-5), marks("campaign", "engineer")).sometimes().info()));
        ScreenGraph graph = ScreenGraph.of(screens);

        Input campaign = graph.input("HOME", "campaign").orElseThrow();
        assertTrue(campaign.comesAndGoes());
        assertTrue(campaign.isInfo());
        assertEquals(ScreenGraph.Presence.SOMETIMES, graph.presence("CAMPAIGN"));
        assertFalse(graph.input("HOME", "gold").orElseThrow().comesAndGoes(), "a button off the strip is its own");
        assertEquals(Optional.of(graph.screens().get("HOME").strips().get("campaign_strip")),
                graph.screens().get("HOME").stripOf(campaign));
    }

    @Test
    void anInputThatComesAndGoesNeverSeenIsOwedWithoutHoldingItsScreen() {
        Map<String, MapScreen> screens = firestone();
        screens.put("MAP", with(screens.get("MAP"), click("loot").sometimes()));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertFalse(graph.unfinished("MAP"));
        assertEquals(List.of(
                "Home: untried gold",
                "Map: never seen to try loot (comes and goes)",
                "Engineer: marker",
                "Engineer: no input cut yet"), graph.owed());
    }

    @Test
    void aFinishedScreenIsAMenuWhenEveryInputItTriedOpenedOne() {
        Map<String, MapScreen> screens = firestone();
        screens.put("HOME", screen(screens.get("HOME").setup(), click("campaign"), click("engineer"),
                Strip.of(Scroll.wheel(-5), marks("left_column")).tried(),
                click("guild").locked(picture("padlock"))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(Optional.of(ScreenGraph.Shape.MENU), graph.shape("HOME"), "a strip or a locked tile is no activity");
        assertEquals(Optional.of(ScreenGraph.Shape.ACTIVITY), graph.shape("CAMPAIGN"));
        assertEquals(Optional.empty(), graph.shape("ENGINEER"));
    }

    /** A way back that is a field of the screen is tried by being its way back, and is no activity of it. */
    @Test
    void aScreensWayBackIsAFieldOfItsOwn() {
        Map<String, MapScreen> screens = firestone();
        screens.put("MAP", screen(screens.get("MAP").setup().back(ref("MAP", "close")), click("close")));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertTrue(graph.isBack("MAP", "close"));
        assertTrue(graph.untried("MAP").isEmpty());
        assertEquals(Optional.empty(), graph.shape("MAP"), "nothing on it but its way back");
        ScreenGraph.Edge back = graph.edges().stream().filter(e -> e.back() && e.from().equals("MAP")).findFirst()
                .orElseThrow();
        assertEquals("close", back.input());
        assertEquals(click("close"), back.via());
        assertEquals(Optional.of(click("close")), graph.backOf("MAP").map(ScreenGraph.Edge::via));
    }

    @Test
    void aReferenceNamesItsConstantOrItsFieldAndTheEdgesRunBothWays() {
        ScreenGraph graph = ScreenGraph.of(firestone());

        assertEquals(Optional.of("CAMPAIGN"), graph.constantOf(BotConstants.screen("CAMPAIGN")));
        assertEquals(Optional.empty(), graph.constantOf(BotConstants.screen("GONE")));
        assertEquals(Optional.of(new BotConstants.InputName("HOME", "gold")), graph.nameOf(ref("HOME", "gold")));
        assertEquals(Optional.empty(), graph.nameOf(ref("HOME", "nowhere")), "a field the screen lost names none");
        assertEquals("Map", graph.shown("MAP"));
        assertTrue(graph.edges().contains(new ScreenGraph.Edge("MAP", "CAMPAIGN", null, null, true)));
        assertTrue(graph.edges().contains(new ScreenGraph.Edge("HOME", "CAMPAIGN", "campaign", click("campaign"),
                false)));
    }

    /** What a bot's {@code Screens} holds, read by {@link ScreenMaps#read}. */
    static final class Screens {
        static final class Home extends Screen {
            public final Button campaign = click("campaign");

            @Override
            public Setup setup() {
                return Setup.named("Home");
            }
        }

        static final Home HOME = new Home();

        static final class Campaign extends Screen {
            public final Button claim = click("claim");

            @Override
            public Setup setup() {
                return Setup.named("Campaign").from(HOME.campaign).back(CAMPAIGN.claim);
            }
        }

        static final Campaign CAMPAIGN = new Campaign();
        static Screen notFinal = new Home();
    }

    @Test
    void theMapIsTheHoldersScreenConstantsInDeclaredOrderEachInputByItsField() {
        ScreenGraph graph = ScreenMaps.read(Screens.class);

        assertEquals(List.of("HOME", "CAMPAIGN"), List.copyOf(graph.screens().keySet()));
        assertEquals(Optional.of("HOME"), graph.parent("CAMPAIGN"), "the very field named is the screen's");
        assertTrue(graph.isBack("CAMPAIGN", "claim"), "a setup names its own fields through its constant");
        assertEquals(Optional.of("CAMPAIGN"), graph.constantOf(Screens.CAMPAIGN));
        assertEquals(Optional.empty(), graph.constantOf(Screens.notFinal), "an equal screen is not the constant");
        assertSame(Screens.HOME, graph.instance("HOME").orElseThrow());
        assertEquals(Optional.of(new BotConstants.InputName("HOME", "campaign")), graph.nameOf(Screens.HOME.campaign));
        assertEquals(Optional.empty(), ScreenMaps.beside(String.class));
    }

    /** A field name for an input: its words in lowerCamelCase, numbered when taken. */
    @Test
    void aNewFieldIsNamedFromWhatItActsOn() {
        MapScreen home = screen("Home");
        assertEquals("beerBuyTavern", home.fieldFor("beer_buy_tavern"));
        assertEquals("keyQ", home.fieldFor("key Q"));
        assertEquals("n10x", home.fieldFor("10x"));
        assertEquals("newInput", home.fieldFor("new"), "no keyword");
        assertEquals("input", home.fieldFor("—"));
        MapScreen taken = new MapScreen(Setup.named("Home"), Map.of("beerBuy", click("beer_buy")));
        assertEquals("beerBuy2", taken.fieldFor("beer buy"));
    }

    @Test
    void theScreensAWayIntoAScreenIsOnAreItsFromAndAlsoFrom() {
        Map<String, MapScreen> screens = firestone();
        screens.put("HOME", with(screens.get("HOME"), click("map")));
        screens.put("MAP", screens.get("MAP").setup(s -> s.alsoFrom(List.of(Way.of(ref("HOME", "map"))))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(List.of("CAMPAIGN", "HOME"), List.copyOf(graph.opensFrom("MAP")));
        assertEquals(List.of(), List.copyOf(graph.opensFrom("HOME")));
    }

    /** A popup shows over any screen: its inputs are tried while it shows, and Discovery never walks to it. */
    @Test
    void aPopupIsNoScreenToExploreNext() {
        Map<String, MapScreen> screens = new LinkedHashMap<>(upMap(click("mail").tried()).screens());
        screens.put("OFFER", screen(Setup.named("Offer").markers(marks("offer")).popup(), click("buy")));
        assertEquals(Optional.empty(), ScreenGraph.of(screens).nextToExplore());
    }

    /** A screen that says why it has no marker owes it no more, and holds no other screen back. */
    @Test
    void aMarkerTheScreenSaysWhyItLacksIsOwedNoMore() {
        assertTrue(ScreenGraph.markerWithReason("marker — every icon here animates"));
        assertTrue(ScreenGraph.markerWithReason("Marker - it scrolls"));
        assertFalse(ScreenGraph.markerWithReason("marker"));
        assertFalse(ScreenGraph.markerWithReason("marker-less icons"));
        assertFalse(ScreenGraph.markerWithReason("weekly tab picture"));

        Map<String, MapScreen> screens = firestone();
        assertEquals(Optional.of("ENGINEER"), ScreenGraph.of(screens).owingMarker());
        screens.put("ENGINEER", screens.get("ENGINEER").setup(s -> s.owes(List.of("marker — every icon animates",
                "its timer"))));
        ScreenGraph graph = ScreenGraph.of(screens);

        assertEquals(Optional.empty(), graph.owingMarker());
        assertTrue(graph.owed().contains("Engineer: its timer"), graph.owed().toString());
        assertTrue(graph.owed().stream().noneMatch(line -> line.startsWith("Engineer: marker")), graph.owed().toString());
    }

    /** A screen finished sends the game to the next screen to explore, wherever it is; none when all is done. */
    @Test
    void aFinishedScreenGoesToTheNextScreenToExplore() {
        assertEquals(Optional.of("HOME"), upMap(click("mail")).nextToExplore(), "Home's mail");
        assertEquals(Optional.empty(), upMap(click("mail").tried()).nextToExplore(), "all finished");

        Map<String, MapScreen> screens = new LinkedHashMap<>(upMap(click("mail").tried()).screens());
        screens.put("MAP", with(screens.get("MAP"), click("zoom")));
        assertEquals(Optional.of("MAP"), ScreenGraph.of(screens).nextToExplore(), "Map's zoom, two steps down");
    }

    /** Home ▸ Campaign ▸ Map, each tried out but Home's {@code mail}. */
    private static ScreenGraph upMap(Input mail) {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("Home").markers(marks("home")), click("campaign"), mail));
        screens.put("CAMPAIGN", screen(Setup.named("Campaign").markers(marks("campaign_title"))
                .from(ref("HOME", "campaign")), click("map"), click("claim").tried()));
        screens.put("MAP", screen(Setup.named("Map").markers(marks("map_title")).from(ref("CAMPAIGN", "map")),
                click("x").tried()));
        return ScreenGraph.of(screens);
    }

    /**
     * The game opens on Loading, which Home follows on its own; Home's fight opens Entering battle, a transition that
     * Battle follows on its own in 30 s at most.
     */
    static Map<String, MapScreen> loading() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("LOADING", screen(Setup.named("Loading").markers(marks("loading")).start()));
        screens.put("HOME", screen(Setup.named("Home").markers(marks("home")).after(BotConstants.screen("LOADING")),
                click("fight")));
        screens.put("ENTERING", screen(Setup.named("Entering battle").markers(marks("entering"))
                .from(ref("HOME", "fight")).loads(Duration.ofSeconds(30))));
        screens.put("BATTLE", screen(Setup.named("Battle").markers(marks("battle"))
                .after(BotConstants.screen("ENTERING"))));
        return screens;
    }

    /**
     * Home, which reads its gold, and its fight: Battle, loading up to 30 s, that ends on its own on Victory or
     * Defeat; an Offer popup may show over any of them.
     */
    static Map<String, MapScreen> fight() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("Home").markers(marks("home")), click("fight"))
                .withReadout("gold", Readout.digits(new Zone(10, 10, 80, 20), new DigitSet("digits/gold"))));
        screens.put("BATTLE", screen(Setup.named("Battle").markers(marks("battle")).from(ref("HOME", "fight"))
                .loads(Duration.ofSeconds(30))));
        screens.put("VICTORY", screen(Setup.named("Victory").markers(marks("victory"))
                .after(BotConstants.screen("BATTLE"))));
        screens.put("DEFEAT", screen(Setup.named("Defeat").markers(marks("defeat"))
                .after(BotConstants.screen("BATTLE"))));
        screens.put("OFFER", screen(Setup.named("Offer").markers(marks("offer")).back(click("close_x")).popup()));
        return screens;
    }

    @Test
    void twoScreensAfterOneAreBothItsResults() {
        ScreenGraph graph = ScreenGraph.of(fight());
        assertEquals(List.of("VICTORY", "DEFEAT"), graph.results("BATTLE"));
        assertEquals(List.of(), graph.results("HOME"), "Battle is pressed into, not followed on its own");
        assertEquals(List.of(), graph.results("VICTORY"));
        assertEquals(30_000, graph.loadMs("BATTLE"));
        assertEquals(Optional.of(new BotConstants.InputName("HOME", "gold")),
                graph.nameOf(graph.screens().get("HOME").readouts().get("gold")));
        assertEquals(Optional.empty(), graph.nameOf(Readout.text(new Zone(0, 0, 1, 1))), "a readout of no screen");
        assertEquals(Optional.empty(), graph.screens().get("HOME").subjectField());
        MapScreen hero = graph.screens().get("HOME").withReadout("name", Readout.text(new Zone(0, 0, 9, 9)).subject());
        assertEquals(Optional.of("name"), hero.subjectField());
        assertEquals("gold2", hero.fieldFor("gold"), "a readout's name is taken");
    }

    @Test
    void theStartScreenIsTheRootAndTheScreenAfterItFollowsOnItsOwn() {
        ScreenGraph graph = ScreenGraph.of(loading());
        assertEquals(Optional.of("LOADING"), graph.root(), "the start screen, though Home is declared without a way");
        assertEquals(Optional.of("LOADING"), graph.parent("HOME"));
        assertEquals(OptionalInt.of(1), graph.depth("HOME"));
        assertTrue(graph.followsOnItsOwn("HOME"));
        assertEquals(List.of("HOME"), graph.followers("LOADING"));
        assertEquals(List.of("BATTLE"), graph.followers("ENTERING"));
        assertEquals(ScreenGraph.Presence.ALWAYS, graph.presence("BATTLE"));
        assertFalse(graph.noneCut("LOADING"), "a start screen has nothing to cut but its marker");
        assertFalse(graph.unfinished("LOADING"));
    }

    @Test
    void aWayOnItsOwnIsAnEdgeWithNoInputAndNoWayBack() {
        ScreenGraph graph = ScreenGraph.of(loading());
        ScreenGraph.Edge own = new ScreenGraph.Edge("LOADING", "HOME", null, null, false);
        assertTrue(own.onItsOwn());
        assertEquals("on its own", own.label());
        assertTrue(graph.edges().contains(own));
        assertTrue(graph.edges().stream().noneMatch(e -> e.back() && (e.to().equals("LOADING")
                || e.to().equals("ENTERING"))), "the game never shows a loading screen again by going back");
        assertEquals(List.of("HOME", "ENTERING", "BATTLE"), graph.pathTo("LOADING", "BATTLE", e -> true)
                .orElseThrow().stream().map(ScreenGraph.Edge::to).toList());
        assertEquals(30_000, graph.loadMs("ENTERING"));
        assertEquals(60_000, graph.loadMs("LOADING"), "the default");
    }

    /** Past a transition the way back skips it, and home is where loading ends, never the start screen. */
    @Test
    void aScreenAfterATransitionGoesBackAboveItAndHomeIsWhereLoadingEnds() {
        ScreenGraph graph = ScreenGraph.of(loading());
        assertTrue(graph.edges().contains(new ScreenGraph.Edge("BATTLE", "HOME", null, null, true)),
                "Battle goes back to Home, which opened Entering battle");
        assertEquals(Optional.of("HOME"), graph.home());
        Map<String, MapScreen> splash = loading();
        splash.put("SPLASH", screen(Setup.named("Splash").markers(marks("splash"))
                .after(BotConstants.screen("LOADING"))));
        splash.put("HOME", screen(Setup.named("Home").markers(marks("home")).after(BotConstants.screen("SPLASH")),
                click("fight")));
        assertEquals(Optional.of("HOME"), ScreenGraph.of(splash).home(), "through Splash");
        assertEquals(Optional.of("HOME"), ScreenGraph.of(firestone()).home(), "no start screen: the root");
    }
}
