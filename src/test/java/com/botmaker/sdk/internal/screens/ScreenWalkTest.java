package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.marks;
import static com.botmaker.sdk.internal.screens.MapFixtures.picture;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static com.botmaker.sdk.internal.screens.MapFixtures.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A walk over a fake game: a state machine of screens, each showing its marker and the pictures of its inputs, an
 * input moving it to another screen. Time is counted, not waited.
 */
class ScreenWalkTest {

    private static final long STEP_MS = 2_000;

    private static Setup named(String name, String marker, String fromScreen, String fromField) {
        Setup setup = Setup.named(name).markers(marker == null ? List.of() : marks(marker));
        return fromScreen == null ? setup : setup.from(ref(fromScreen, fromField));
    }

    private static Map<String, MapScreen> screens() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(named("Home", "home", null, null), click("campaign"), click("engineer"),
                click("mail"), click("event").sometimes(), click("arena").locked(picture("padlock")), click("lobby")));
        screens.put("CAMPAIGN", screen(named("Campaign", "campaign_title", "HOME", "campaign")
                        .alsoFrom(List.of(Way.of(ref("MAIL", "mail_campaign")))),
                click("map"), click("bonus").sometimes()));
        screens.put("BONUS", screen(named("Bonus", "bonus_title", "CAMPAIGN", "bonus")));
        screens.put("LOBBY", screen(named("Lobby", null, "HOME", "lobby")));
        screens.put("MAP", screen(named("Map", "map_title", "CAMPAIGN", "map")));
        screens.put("ENGINEER", screen(named("Engineer", "engineer_title", "HOME", "engineer")));
        screens.put("MAIL", screen(named("Mail", "mail_title", "HOME", "mail"), click("mail_campaign")));
        screens.put("EVENT", screen(named("Event", "event_title", "HOME", "event")));
        screens.put("ARENA", screen(named("Arena", "arena_title", "HOME", "arena")));
        screens.put("OFFER", screen(Setup.named("Offer").markers(marks("offer")).back(click("close_x")).popup()));
        screens.put("NEWS", screen(Setup.named("News").markers(marks("news")).popup()));
        return screens;
    }

    private static ScreenGraph map() {
        return ScreenGraph.of(screens());
    }

    /** What the game shows while an overlay the map lacks covers it. */
    private static final String OVERLAY = "OVERLAY";

    /** The game: which screen shows, a popup over it, and what each input on each screen leads to. */
    private static class Game implements Reach {
        final ScreenGraph map;
        /** What the game shows on its own from a time on: Home once loading is done. */
        final TreeMap<Long, String> timeline = new TreeMap<>();
        final Map<String, Set<String>> shows = new HashMap<>();
        final Map<String, String> leads = new HashMap<>();
        /** What an input brings into view on the screen it is done on: a list scrolled. */
        final Map<String, String> reveals = new HashMap<>();
        final List<String> done = new ArrayList<>();
        final List<String> places = new ArrayList<>();
        String screen;
        String popup;
        /** What closes the popup: its way out or Escape ("both"), only Escape, or nothing. */
        String closes = "both";
        /** An overlay the map lacks, over the screen: it shows {@link #OVERLAY}'s pictures and closes by them. */
        boolean overlay;
        long now;
        /** A picture that runs out: after {@link #left} clicks it is gone, {@link #greyed} shown in its place. */
        String runsOut;
        int left;
        String greyed;
        /** How many clicks in a row the game misses: lag. */
        int deaf;

        Game(String screen) {
            this(screen, map());
        }

        Game(String screen, ScreenGraph map) {
            this.map = map;
            this.screen = screen;
            map.screens().forEach((c, s) -> {
                Set<String> on = new HashSet<>();
                s.markers().forEach(m -> on.add(m.id()));
                s.inputs().values().stream().filter(i -> i instanceof Button && !i.comesAndGoes())
                        .forEach(i -> on.add(((Button) i).picture().id()));
                if (s.back() instanceof Button back && BotConstants.inputName(back) == null) {
                    on.add(back.picture().id());
                }
                shows.put(c, on);
            });
            shows.put(OVERLAY, new HashSet<>(Set.of("close_x")));
            leads.put("HOME click campaign", "CAMPAIGN");
            leads.put("HOME click engineer", "ENGINEER");
            leads.put("HOME click mail", "MAIL");
            leads.put("HOME click event", "EVENT");
            leads.put("HOME click lobby", "LOBBY");
            leads.put("CAMPAIGN click map", "MAP");
            leads.put("MAIL click mail_campaign", "CAMPAIGN");
            leads.put("MAP key ESCAPE", "CAMPAIGN");
            leads.put("CAMPAIGN key ESCAPE", "HOME");
            leads.put("ENGINEER key ESCAPE", "HOME");
            leads.put("MAIL key ESCAPE", "HOME");
        }

        private String showing() {
            while (!timeline.isEmpty() && timeline.firstKey() <= now) screen = timeline.pollFirstEntry().getValue();
            return overlay ? OVERLAY : popup != null ? popup : screen;
        }

        private void did(String what) {
            done.add(what);
            if (what.startsWith("click ") && deaf > 0) {
                deaf--;
                return;
            }
            String key = showing() + " " + what;
            if (overlay) {
                if (shows.get(OVERLAY).isEmpty() ? what.equals("key ESCAPE") : what.equals("click close_x")) {
                    overlay = false;
                }
                return;
            }
            if (popup != null) {
                boolean way = what.equals("click close_x") && closes.equals("both");
                boolean escape = what.equals("key ESCAPE") && !closes.equals("nothing");
                if (way || escape) popup = null;
                return;
            }
            String shown = reveals.get(key);
            if (shown != null) shows.get(screen).add(shown);
            if (what.equals("click " + runsOut) && --left == 0) {
                shows.get(screen).remove(runsOut);
                if (greyed != null) shows.get(screen).add(greyed);
            }
            String next = leads.get(key);
            if (next != null && next.startsWith("popup:")) popup = next.substring("popup:".length());
            else if (next != null) screen = next;
        }

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            if (!shows.getOrDefault(showing(), Set.of()).contains(picture.id())) return Optional.empty();
            if (!places.contains(picture.id())) places.add(picture.id());
            return Optional.of(new Rect(places.indexOf(picture.id()) * 20, 0, 10, 10));
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
            did("click " + places.get(at.x() / 20));
        }

        @Override
        public void keys(Combo combo) {
            did("key " + combo.keys().getLast().name());
        }

        @Override
        public void type(String text, boolean clears, boolean submits) {
            did("type " + text);
        }

        @Override
        public void scroll(Point at, int notches) {
            did("scroll " + places.get(at.x() / 20) + " " + notches);
        }

        @Override
        public void drag(Point from, Point to, Duration time) {
            did("drag " + places.get(from.x() / 20));
        }

        @Override
        public long nowMs() {
            return now;
        }

        @Override
        public void pause(long ms) {
            now += ms;
        }
    }

    private static ScreenWalk.Result go(Game game, String target, List<String> reached) {
        return new ScreenWalk(game.map, game, STEP_MS, reached::add).go(target, null);
    }

    /** Home with a Claim the game shows, greyed as claim_off once it runs out. */
    private static ScreenGraph withClaim() {
        Map<String, MapScreen> screens = screens();
        screens.put("HOME", with(screens.get("HOME"),
                click("claim").looks(List.of(Look.of(picture("claim_off"), State.DISABLED)))));
        return ScreenGraph.of(screens);
    }

    private static Game claiming(int left) {
        Game game = new Game("HOME");
        game.shows.get("HOME").add("claim");
        game.runsOut = "claim";
        game.left = left;
        game.greyed = "claim_off";
        return game;
    }

    @Test
    void aPressClicksItsButtonAndWaitsForWhereTheMapSaysItLeads() {
        Game game = new Game("HOME");

        ScreenWalk.Press press = new ScreenWalk(game.map, game, STEP_MS, null).press("HOME", "campaign", null);

        assertEquals(Pressed.Result.DONE, press.result(), press.toString());
        assertEquals("CAMPAIGN", press.at());
        assertEquals(List.of("click campaign"), game.done);
    }

    @Test
    void aChoreThatStaysOnItsScreenIsDone() {
        Game game = claiming(5);

        ScreenWalk.Press press = new ScreenWalk(withClaim(), game, STEP_MS, null).press("HOME", "claim", null);

        assertEquals(new ScreenWalk.Press(Pressed.Result.DONE, "HOME", 1, press.why()), press);
        assertEquals(List.of("click claim"), game.done);
    }

    /** Buy until the money runs out: pressed while it shows lit, stopped by its disabled look. */
    @Test
    void untilNonePressesUntilTheButtonLooksDisabled() {
        Game game = claiming(3);

        ScreenWalk.Press press = new ScreenWalk(withClaim(), game, STEP_MS, null)
                .press("HOME", "claim", Pick.untilNone());

        assertEquals(Pressed.Result.DONE, press.result(), press.toString());
        assertEquals(3, press.times());
        assertEquals(List.of("click claim", "click claim", "click claim"), game.done);

        ScreenWalk.Press again = new ScreenWalk(withClaim(), game, STEP_MS, null)
                .press("HOME", "claim", Pick.untilNone());
        assertEquals(new ScreenWalk.Press(Pressed.Result.DISABLED, "HOME", 0, again.why()), again);
        assertEquals(3, game.done.size(), "a disabled button is never clicked");
    }

    /** A node showing locked, maxed or upgrading is never clicked, and says which; pressing until none stops on it. */
    @Test
    void aButtonThatLooksLockedDoneOrBusyIsNotPressed() {
        Map<State, Pressed.Result> results = Map.of(State.LOCKED, Pressed.Result.LOCKED,
                State.DONE, Pressed.Result.FINISHED, State.BUSY, Pressed.Result.BUSY);
        for (Map.Entry<State, Pressed.Result> look : results.entrySet()) {
            Map<String, MapScreen> screens = screens();
            screens.put("HOME", with(screens.get("HOME"),
                    click("claim").looks(List.of(Look.of(picture("claim_" + look.getKey().id()), look.getKey())))));
            ScreenGraph map = ScreenGraph.of(screens);
            Game game = new Game("HOME");
            game.shows.get("HOME").add("claim_" + look.getKey().id());

            ScreenWalk.Press press = new ScreenWalk(map, game, STEP_MS, null).press("HOME", "claim", null);

            assertEquals(new ScreenWalk.Press(look.getValue(), "HOME", 0, press.why()), press);
            assertTrue(press.why().contains(look.getKey().id()), press.why());
            assertTrue(game.done.isEmpty(), look.getKey() + " is never clicked");
        }

        Map<String, MapScreen> screens = screens();
        screens.put("HOME", with(screens.get("HOME"),
                click("claim").looks(List.of(Look.of(picture("claim_busy"), State.BUSY)))));
        Game game = claiming(1);
        game.greyed = "claim_busy";
        ScreenWalk.Press press = new ScreenWalk(ScreenGraph.of(screens), game, STEP_MS, null)
                .press("HOME", "claim", Pick.untilNone());
        assertEquals(new ScreenWalk.Press(Pressed.Result.DONE, "HOME", 1, press.why()), press);
        assertEquals(List.of("click claim"), game.done, "stopped by its busy look");
    }

    @Test
    void aNodeOfATreeIsPressedInViewAndWalkedToOutOfIt() {
        ImageTemplate root = picture("root").at(new Rect(0, 0, 10, 10));
        ImageTemplate fist = picture("fist").at(new Rect(0, 0, 10, 10));
        Tree tree = Tree.of(List.of(Node.of(root), Node.of(fist).beside(List.of(Beside.of(root, 40, 0)))));
        Map<String, MapScreen> screens = screens();
        screens.put("HOME", with(screens.get("HOME"), Button.of(root), Button.of(fist), tree));

        Game shown = new Game("HOME");
        shown.shows.get("HOME").addAll(List.of("root", "fist"));
        ScreenWalk.Press pressed = new ScreenWalk(ScreenGraph.of(screens), shown, STEP_MS, null)
                .press("HOME", "fist", null);
        assertEquals(Pressed.Result.DONE, pressed.result(), pressed.why());
        assertEquals(List.of("click fist"), shown.done);

        // Out of view, 40 of the root's widths east, and the tree does not scroll: missed, saying why.
        Game hidden = new Game("HOME");
        hidden.shows.get("HOME").add("root");
        ScreenWalk.Press missed = new ScreenWalk(ScreenGraph.of(screens), hidden, STEP_MS, null)
                .press("HOME", "fist", null);
        assertEquals(Pressed.Result.ABSENT, missed.result());
        assertTrue(missed.why().contains("does not scroll"), missed.why());
        assertTrue(hidden.done.isEmpty());
    }

    @Test
    void aButtonNotShownIsAbsentAndOneThatLeadsElsewhereLeft() {
        Game gone = new Game("HOME");
        ScreenWalk.Press absent = new ScreenWalk(withClaim(), gone, STEP_MS, null).press("HOME", "claim", null);
        assertEquals(Pressed.Result.ABSENT, absent.result(), absent.toString());
        assertTrue(gone.done.isEmpty());

        Game game = claiming(5);
        game.leads.put("HOME click claim", "ENGINEER");
        ScreenWalk.Press left = new ScreenWalk(withClaim(), game, STEP_MS, null).press("HOME", "claim", null);
        assertEquals(new ScreenWalk.Press(Pressed.Result.LEFT, "ENGINEER", 1, left.why()), left);
    }

    /** A Claim that opens a popup the map leads to: seen before the run's popup closing would shut it. */
    @Test
    void aLeadThatIsAPopupIsSeenBeforeItIsClosed() {
        Map<String, MapScreen> screens = new LinkedHashMap<>(withClaim().screens());
        screens.put("OFFER", screen(Setup.named("Offer").markers(marks("offer")).back(click("close_x")).popup()
                .from(ref("HOME", "claim"))));
        Game game = claiming(5);
        game.leads.put("HOME click claim", "popup:OFFER");

        ScreenWalk.Press press = new ScreenWalk(ScreenGraph.of(screens), game, STEP_MS, null).press("HOME", "claim", null);

        assertEquals(new ScreenWalk.Press(Pressed.Result.DONE, "OFFER", 1, press.why()), press);
        assertEquals("OFFER", game.popup, "left open for the body");
    }

    /** A click lost to lag, the game still on its screen though the map says it leads away: pressed once more. */
    @Test
    void aPressTheGameMissedIsPressedOnceMore() {
        Game game = new Game("HOME");
        game.deaf = 1;

        ScreenWalk.Press press = new ScreenWalk(game.map, game, STEP_MS, null).press("HOME", "campaign", null);

        assertEquals(Pressed.Result.DONE, press.result(), press.toString());
        assertEquals("CAMPAIGN", press.at());
        assertEquals(List.of("click campaign", "click campaign"), game.done);
    }

    /** Still on its screen after the second press, though the map says it leads away: not what the map says. */
    @Test
    void aPressThatStaysTwiceLeftTheMap() {
        Game game = new Game("HOME");
        game.deaf = 2;

        ScreenWalk.Press press = new ScreenWalk(game.map, game, STEP_MS, null).press("HOME", "campaign", null);

        assertEquals(Pressed.Result.LEFT, press.result(), press.toString());
        assertEquals("HOME", press.at());
        assertEquals(List.of("click campaign", "click campaign"), game.done, "pressed twice, never a third time");
    }

    /** A screen's way back leads to the screen it is opened from: landing there is what the map says. */
    @Test
    void aWayBackPressedLandsWhereTheScreenIsOpenedFrom() {
        Map<String, MapScreen> screens = screens();
        screens.put("ENGINEER", screen(screens.get("ENGINEER").setup().back(ref("ENGINEER", "close")),
                click("close")));
        Game game = new Game("ENGINEER");
        game.shows.get("ENGINEER").add("close");
        game.leads.put("ENGINEER click close", "HOME");

        ScreenWalk.Press press = new ScreenWalk(ScreenGraph.of(screens), game, STEP_MS, null)
                .press("ENGINEER", "close", null);

        assertEquals(Pressed.Result.DONE, press.result(), press.toString());
        assertEquals("HOME", press.at());
    }

    /** A way through a copy is the copy pressed by its place along its order, whatever the pick's mode. */
    @Test
    void aWaysCopyIsTheOnePressedByPlaceAndOrder() {
        assertTrue(ScreenWalk.sameCopy(null, Pick.untilNone()));
        assertTrue(ScreenWalk.sameCopy(Pick.first(), Pick.nth(1, Order.NORTH)));
        assertFalse(ScreenWalk.sameCopy(Pick.first(), Pick.first(Order.SOUTH)));
        assertFalse(ScreenWalk.sameCopy(Pick.nth(2, Order.NORTH), Pick.first()));
        assertFalse(ScreenWalk.sameCopy(null, Pick.each(Order.NORTH)));
    }

    @Test
    void aLockedInputIsNotPressed() {
        Game game = new Game("HOME");
        game.shows.get("HOME").add("arena");

        ScreenWalk.Press press = new ScreenWalk(game.map, game, STEP_MS, null).press("HOME", "arena", null);

        assertEquals(Pressed.Result.NOT_NOW, press.result());
        assertTrue(game.done.isEmpty());
    }

    @Test
    void aWalkDoesEachInputOnTheWayAndWaitsForEachScreen() {
        Game game = new Game("HOME");
        List<String> reached = new ArrayList<>();

        ScreenWalk.Result walked = go(game, "MAP", reached);

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome());
        assertEquals(List.of("click campaign", "click map"), game.done);
        assertEquals(List.of("CAMPAIGN", "MAP"), walked.walked());
        assertEquals(List.of("HOME", "CAMPAIGN", "MAP"), reached, "the screen it starts on is told, then each reached");
    }

    /** A way in that is a list member out of view: the list is scrolled over a member that shows, then it is clicked. */
    @Test
    void aWayInThatIsAListMemberOutOfViewIsScrolledToFirst() {
        Game game = new Game("HOME");
        game.shows.get("HOME").remove("engineer");
        game.reveals.put("HOME scroll campaign -1", "engineer");
        Map<String, MapScreen> screens = screens();
        screens.put("HOME", with(screens.get("HOME"), Strip.of(Scroll.wheel(-1), marks("campaign", "engineer"))));

        ScreenWalk.Result walked = new ScreenWalk(ScreenGraph.of(screens), game, STEP_MS, s -> {}).go("ENGINEER", null);

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome(), walked.toString());
        assertEquals(List.of("scroll campaign -1", "click engineer"), game.done);

        // A member with another look is taken too: out of view, it is open, and the click scrolls first.
        Game badged = new Game("HOME");
        badged.shows.get("HOME").remove("engineer");
        badged.reveals.put("HOME scroll campaign -1", "engineer");
        screens.put("HOME", with(screens.get("HOME"),
                click("engineer").looks(List.of(Look.of(picture("engineer_badge"), State.BADGE)))));
        ScreenWalk.Result badgedWalk = new ScreenWalk(ScreenGraph.of(screens), badged, STEP_MS, s -> {})
                .go("ENGINEER", null);
        assertEquals(ScreenWalk.Outcome.ARRIVED, badgedWalk.outcome(), badgedWalk.toString());
        assertEquals(List.of("scroll campaign -1", "click engineer"), badged.done);
    }

    /** A list member that comes and goes, out of view, is searched for only when no other way is open. */
    @Test
    void anotherWayIsTakenBeforeSearchingAListForWhatComesAndGoes() {
        Game game = new Game("HOME");
        game.shows.get("HOME").remove("campaign");
        Map<String, MapScreen> screens = screens();
        screens.put("HOME", with(screens.get("HOME"), click("campaign").sometimes(),
                Strip.of(Scroll.wheel(-1), marks("engineer", "campaign"))));

        ScreenWalk.Result walked = new ScreenWalk(ScreenGraph.of(screens), game, STEP_MS, s -> {}).go("CAMPAIGN", null);

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome(), walked.toString());
        assertEquals(List.of("click mail", "click mail_campaign"), game.done, "Mail's way, the column never scrolled");
    }

    /** A screen opened through an alsoFrom hides the screen it was opened from too, not only its tree parent. */
    @Test
    void aScreenOverTheOneAnotherWayOpenedItFromIsTheScreenShown() {
        Game game = new Game("CAMPAIGN");
        game.shows.get("CAMPAIGN").add("mail_title");

        assertEquals(Optional.of("CAMPAIGN"), new ScreenWalk(game.map, game, STEP_MS, null).where("MAIL"),
                "Mail's marker under Campaign, Mail looked for first: still Campaign");
    }

    /**
     * A screen opened from another than its tree parent closes back to that one: the way back arrives once the
     * screen left is gone, and the walk goes on from where the game is, without waiting out the step (2026-10-15).
     */
    @Test
    void aWayBackThatLandsOnAnotherScreenGoesOnFromThere() {
        Game game = new Game("CAMPAIGN");
        game.leads.put("CAMPAIGN key ESCAPE", "MAIL");
        List<String> reached = new ArrayList<>();

        ScreenWalk.Result walked = go(game, "ENGINEER", reached);

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome(), walked.why());
        assertEquals(List.of("key ESCAPE", "key ESCAPE", "click engineer"), game.done);
        assertEquals(List.of("CAMPAIGN", "MAIL", "HOME", "ENGINEER"), reached);
        assertTrue(game.now < STEP_MS, "no step waited out its time: " + game.now);
    }

    @Test
    void aWayBackWithNoInputIsEscape() {
        Game game = new Game("MAP");

        ScreenWalk.Result walked = go(game, "ENGINEER", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome());
        assertEquals(List.of("key ESCAPE", "key ESCAPE", "click engineer"), game.done);
    }

    /** The game's usual close button, given, is clicked where it shows; Escape only where it does not (2026-10-14). */
    @Test
    void aWayBackWithNoInputClicksTheUsualCloseButtonWhileItShows() {
        Game game = new Game("MAP");
        game.shows.get("CAMPAIGN").add("close_x");
        game.leads.put("CAMPAIGN click close_x", "HOME");

        ScreenWalk.Result walked = new ScreenWalk(game.map, game, STEP_MS, null, click("close_x")).go("ENGINEER", null);

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome());
        assertEquals(List.of("key ESCAPE", "click close_x", "click engineer"), game.done,
                "Map shows no close_x: Escape; Campaign shows it: clicked");
    }

    @Test
    void aStepThatLandsElsewhereIsReadAgainAndPlannedFromThere() {
        Game game = new Game("HOME");
        game.leads.put("HOME click campaign", "MAIL");

        ScreenWalk.Result walked = go(game, "CAMPAIGN", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome());
        assertEquals(List.of("click campaign", "click mail_campaign"), game.done);
        assertTrue(game.now >= STEP_MS, "the first step waited its time for Campaign");
    }

    @Test
    void aPopupIsClosedBeforeTheWalk() {
        Game game = new Game("HOME");
        game.popup = "OFFER";

        ScreenWalk.Result walked = go(game, "CAMPAIGN", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome());
        assertEquals(List.of("click close_x", "click campaign"), game.done);
    }

    @Test
    void aPopupWithNoWayOutIsClosedByEscape() {
        Game game = new Game("HOME");
        game.popup = "NEWS";

        assertEquals(ScreenWalk.Outcome.ARRIVED, go(game, "CAMPAIGN", new ArrayList<>()).outcome());
        assertEquals(List.of("key ESCAPE", "click campaign"), game.done);
    }

    @Test
    void aPopupStillShownAfterItsWayOutGetsEscapeOnce() {
        Game game = new Game("HOME");
        game.popup = "OFFER";
        game.closes = "escape";

        assertEquals(ScreenWalk.Outcome.ARRIVED, go(game, "CAMPAIGN", new ArrayList<>()).outcome());
        assertEquals(List.of("click close_x", "key ESCAPE", "click campaign"), game.done);
    }

    @Test
    void aPopupThatStaysAfterItsWayOutAndEscapeIsStuck() {
        Game game = new Game("HOME");
        game.popup = "OFFER";
        game.closes = "nothing";

        ScreenWalk.Result walked = go(game, "CAMPAIGN", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.STUCK, walked.outcome());
        assertTrue(walked.why().startsWith("Offer stayed after"), walked.why());
        assertEquals(List.of("click close_x", "key ESCAPE"), game.done, "Escape once, then the run's recovery");
        ScreenWalk walk = new ScreenWalk(game.map, game, STEP_MS, null);
        assertThrows(ScreenWalk.Stayed.class, () -> walk.closePopups(game.look()), "the guard's own call throws");
    }

    @Test
    void anOverlayTheMapLacksIsClearedByAMapsCloseButtonAndSaid() {
        Game game = new Game("HOME");
        game.overlay = true;

        ScreenWalk.Result walked = new ScreenWalk(game.map, game, STEP_MS, null).uncovering().go("CAMPAIGN", null);

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome(), walked.why());
        assertEquals(List.of("click close_x", "click campaign"), game.done, "Offer's way out, the map's close button");
        assertTrue(walked.why().contains("An unmapped popup covered the game (close_x closed it): map it in Upkeep."),
                walked.why());
    }

    /** The assistant's walks touch nothing the map lacks: the screen being explored may be one. */
    @Test
    void aWalkNotUncoveringEndsStuckOnAnOverlayAndPressesNothing() {
        Game game = new Game("HOME");
        game.overlay = true;

        ScreenWalk.Result walked = go(game, "CAMPAIGN", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.STUCK, walked.outcome());
        assertEquals(List.of(), game.done);
    }

    @Test
    void anOverlayIsNotEscapedUnlessAsked() {
        Game game = new Game("HOME");
        game.overlay = true;
        game.shows.get(OVERLAY).clear();
        ScreenWalk walk = new ScreenWalk(game.map, game, STEP_MS, null);

        assertEquals(Optional.empty(), walk.uncover(game.look().orElseThrow(), "HOME", false));
        assertEquals(List.of(), game.done, "a timed-out wait never presses Escape");
    }

    @Test
    void anOverlayWithNoCloseButtonOfTheMapGetsEscape() {
        Game game = new Game("HOME");
        game.overlay = true;
        game.shows.get(OVERLAY).clear();
        ScreenWalk walk = new ScreenWalk(game.map, game, STEP_MS, null);

        Optional<ScreenWalk.Uncovered> cleared = walk.uncover(game.look().orElseThrow(), "HOME", true);

        assertEquals(Optional.of(new ScreenWalk.Uncovered("HOME", "Escape")), cleared);
        assertEquals(List.of("key ESCAPE"), game.done);
        assertEquals(Optional.empty(), walk.uncover(game.look().orElseThrow(), "HOME", true), "nothing covers Home now");
    }

    /** A panel over its parent leaves the parent's marker in view: the panel is where the game is (2026-10-15). */
    @Test
    void aScreenOverTheOneItWasOpenedFromIsTheScreenShown() {
        Game game = new Game("ENGINEER");
        game.shows.get("ENGINEER").add("home");
        ScreenWalk walk = new ScreenWalk(game.map, game, STEP_MS, null);

        assertEquals(Optional.of("ENGINEER"), walk.where("HOME"));

        ScreenWalk.Result walked = walk.go("MAIL", "HOME");
        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome());
        assertEquals(List.of("key ESCAPE", "click mail"), game.done, "the panel closed before Home's input");
    }

    /** Where the game was, its neighbours are looked for first: a far screen's marker only when none of them shows. */
    @Test
    void theScreensNearWhereTheGameWasAreLookedForFirst() {
        Game game = new Game("MAP");
        List<String> looked = new ArrayList<>();
        Reach counting = new Reach() {
            @Override public Optional<BufferedImage> look() { return game.look(); }
            @Override public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
                looked.add(picture.id());
                return game.find(picture, frame);
            }
            @Override public void press(Point at, Gesture gesture, Duration hold) { game.press(at, gesture, hold); }
            @Override public void keys(Combo combo) { game.keys(combo); }
            @Override public void type(String text, boolean clears, boolean submits) {
                game.type(text, clears, submits);
            }
            @Override public void scroll(Point at, int notches) { game.scroll(at, notches); }
            @Override public void drag(Point from, Point to, Duration time) { game.drag(from, to, time); }
            @Override public long nowMs() { return game.nowMs(); }
            @Override public void pause(long ms) { game.pause(ms); }
        };
        ScreenWalk walk = new ScreenWalk(game.map, counting, STEP_MS, null);
        BufferedImage frame = game.look().orElseThrow();

        assertEquals(Optional.of("MAP"), walk.whereOn(frame, "CAMPAIGN"));
        assertFalse(looked.contains("engineer_title"), "Engineer is two steps from Campaign: " + looked);

        looked.clear();
        game.screen = "ENGINEER";
        assertEquals(Optional.of("ENGINEER"), walk.whereOn(frame, "MAP"), "none near shows: every screen next");
        assertTrue(looked.contains("engineer_title"), looked.toString());
    }

    /** A way back that left the panel open has not arrived, though the parent's marker shows under it. */
    @Test
    void aWayBackArrivesOnlyOnceTheScreenLeftIsGone() {
        Game game = new Game("ENGINEER");
        game.shows.get("ENGINEER").add("home");
        game.leads.remove("ENGINEER key ESCAPE");

        ScreenWalk.Result walked = go(game, "MAIL", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.STUCK, walked.outcome(), walked.why());
        assertFalse(game.done.contains("click mail"), "Home's input never clicked under the panel");
    }

    @Test
    void aPopupShowingIsSaidApartFromTheScreen() {
        Game game = new Game("HOME");
        BufferedImage frame = game.look().orElseThrow();
        ScreenWalk walk = new ScreenWalk(game.map, game, STEP_MS, null);
        assertEquals(Optional.empty(), walk.popupOn(frame));

        game.popup = "OFFER";

        assertEquals(Optional.of("OFFER"), walk.popupOn(frame));
        assertEquals(Optional.empty(), walk.whereOn(frame, null), "a popup is no place on the map");
    }

    @Test
    void aWayThroughWhatComesAndGoesIsTakenOnlyWhileItShows() {
        Game game = new Game("HOME");

        ScreenWalk.Result notNow = go(game, "EVENT", new ArrayList<>());
        assertEquals(ScreenWalk.Outcome.NOT_NOW, notNow.outcome());
        assertEquals(List.of(), game.done);

        game.shows.get("HOME").add("event");
        assertEquals(ScreenWalk.Outcome.ARRIVED, go(game, "EVENT", new ArrayList<>()).outcome());
    }

    @Test
    void aWayOrAnInputWhoseMethodSaysNoIsNotTakenAndANameIsUnknown() {
        Game game = new Game("HOME");
        boolean[] open = {false};
        Map<String, MapScreen> screens = screens();
        screens.put("ENGINEER", screens.get("ENGINEER").setup(s -> s.fromWhen(() -> open[0])));
        // An input's condition is on the screen's field, which the way names (2026-10-15).
        screens.put("HOME", with(screens.get("HOME"), click("mail").when(() -> open[0])));
        ScreenGraph conditioned = ScreenGraph.of(screens);

        ScreenWalk walk = new ScreenWalk(conditioned, game, STEP_MS, reached -> {});
        assertEquals(ScreenWalk.Outcome.NOT_NOW, walk.go("ENGINEER", null).outcome());
        assertEquals(ScreenWalk.Outcome.NOT_NOW, walk.go("MAIL", null).outcome());
        assertEquals(List.of(), game.done);

        open[0] = true;
        assertEquals(ScreenWalk.Outcome.ARRIVED, walk.go("ENGINEER", null).outcome());
        assertTrue(Conditions.holds(() -> { throw new UnsupportedOperationException("a name"); }),
                "a condition that cannot answer never closes a way");
    }

    @Test
    void aLockedWayIsNeverTaken() {
        Game game = new Game("HOME");
        game.shows.get("HOME").add("arena");

        ScreenWalk.Result walked = go(game, "ARENA", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.LOCKED, walked.outcome());
        assertEquals(List.of(), game.done);
    }

    /**
     * A screen whose only way in is a field whose text the body gives is never walked to: the walk ends NOT_NOW,
     * naming the field, and types nothing. The body's text gets there, and the field pressed without one is not.
     */
    @Test
    void aScreenOnlyBehindAFieldEndsNotNow() {
        Map<String, MapScreen> screens = screens();
        screens.put("HOME", with(screens.get("HOME"), click("search").typed().submits()));
        screens.put("RESULTS", screen(named("Results", "results_title", "HOME", "search")));
        Game game = new Game("HOME", ScreenGraph.of(screens));
        game.leads.put("HOME type Dragons", "RESULTS");

        ScreenWalk.Result walked = go(game, "RESULTS", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.NOT_NOW, walked.outcome(), walked.why());
        assertTrue(walked.why().contains("opens only by typing in Home's search"), walked.why());
        assertEquals(List.of(), game.done);

        ScreenWalk walk = new ScreenWalk(game.map, game, STEP_MS, null);
        assertEquals(Pressed.Result.NOT_NOW, walk.press("HOME", "search", null).result(), "no text, no press");
        assertEquals(Pressed.Result.NOT_NOW, walk.press("HOME", "campaign", null, Words.of("x")).result(),
                "a button that is no field types nothing");
        assertEquals(List.of(), game.done);
        ScreenWalk.Press typed = walk.press("HOME", "search", null, Words.of("Dragons"));
        assertEquals(Pressed.Result.DONE, typed.result(), typed.toString());
        assertEquals("RESULTS", typed.at());
        assertEquals(List.of("click search", "type Dragons"), game.done);
    }

    /** A field with a text of its own is walked through like a button, typing its text on the way. */
    @Test
    void aFieldWithItsOwnTextIsWalkedThrough() {
        Map<String, MapScreen> screens = screens();
        screens.put("HOME", with(screens.get("HOME"), click("search").types(Words.of("Dragons")).submits()));
        screens.put("RESULTS", screen(named("Results", "results_title", "HOME", "search")));
        Game game = new Game("HOME", ScreenGraph.of(screens));
        game.leads.put("HOME type Dragons", "RESULTS");

        ScreenWalk.Result walked = go(game, "RESULTS", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome(), walked.why());
        assertEquals(List.of("click search", "type Dragons"), game.done);

        // Left where it was, a field that keeps what it holds is not typed again as a lost click is pressed again.
        Game stays = new Game("HOME", ScreenGraph.of(screens));
        ScreenWalk.Press press = new ScreenWalk(stays.map, stays, STEP_MS, null).press("HOME", "search", null);
        assertEquals(Pressed.Result.LEFT, press.result(), press.toString());
        assertEquals(List.of("click search", "type Dragons"), stays.done);
    }

    /** A drag is pressed only once its drop shows too: absent, saying so, while the slot it goes onto is not there. */
    @Test
    void aDragWaitsForItsDrop() {
        Map<String, MapScreen> screens = screens();
        screens.put("HOME", with(screens.get("HOME"), click("card").dragsOnto(picture("slot"))));
        Game game = new Game("HOME", ScreenGraph.of(screens));
        game.shows.get("HOME").add("card");

        ScreenWalk.Press press = new ScreenWalk(game.map, game, STEP_MS, null).press("HOME", "card", null);
        assertEquals(Pressed.Result.ABSENT, press.result(), press.toString());
        assertTrue(press.why().contains("drags onto slot"), press.why());
        assertEquals(List.of(), game.done);

        game.shows.get("HOME").add("slot");
        press = new ScreenWalk(game.map, game, STEP_MS, null).press("HOME", "card", null);
        assertEquals(Pressed.Result.DONE, press.result(), press.toString());
        assertEquals(List.of("drag card"), game.done);

        // Left where it was though the map says it leads away, a drag is not done again as a lost click is.
        screens.put("DECK", screen(named("Deck", "deck_title", "HOME", "card")));
        Game stays = new Game("HOME", ScreenGraph.of(screens));
        stays.shows.get("HOME").addAll(List.of("card", "slot"));
        press = new ScreenWalk(stays.map, stays, STEP_MS, null).press("HOME", "card", null);
        assertEquals(Pressed.Result.LEFT, press.result(), press.toString());
        assertEquals(List.of("drag card"), stays.done);
    }

    @Test
    void aWayTheGameNoLongerShowsIsMissing() {
        Game game = new Game("HOME");
        game.shows.get("HOME").remove("engineer");

        ScreenWalk.Result walked = go(game, "ENGINEER", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.MISSING, walked.outcome());
        assertTrue(walked.why().contains("engineer not found"), walked.why());
    }

    @Test
    void aWayThatComesAndGoesFurtherOnIsLookedForWhenTheWalkGetsThere() {
        Game game = new Game("HOME");

        ScreenWalk.Result walked = go(game, "BONUS", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.NOT_NOW, walked.outcome());
        assertEquals(List.of("click campaign"), game.done, "Campaign was reached to look for the bonus");
    }

    @Test
    void aScreenWithNoMarkerIsTakenAsReached() {
        Game game = new Game("HOME");

        ScreenWalk.Result walked = go(game, "LOBBY", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome());
        assertEquals(List.of("click lobby"), game.done);
    }

    /** A panel with no marker over the screen it was opened from is where the game is, not that screen again. */
    @Test
    void aScreenWithNoMarkerOverItsParentIsReachedNotClickedAgain() {
        Game game = new Game("HOME");
        game.shows.get("LOBBY").add("home");

        ScreenWalk.Result walked = go(game, "LOBBY", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.ARRIVED, walked.outcome(), walked.why());
        assertEquals(List.of("click lobby"), game.done);
    }

    @Test
    void noWalkLeadsToAPopup() {
        assertEquals(ScreenWalk.Outcome.NOT_NOW, go(new Game("HOME"), "OFFER", new ArrayList<>()).outcome());
    }

    @Test
    void stepsThatKeepLandingElsewhereAreStuckNotMissing() {
        Game game = new Game("HOME");
        game.leads.put("HOME click engineer", "MAIL");

        ScreenWalk.Result walked = go(game, "ENGINEER", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.STUCK, walked.outcome());
        assertTrue(walked.why().contains("landed elsewhere"), walked.why());
    }

    @Test
    void aGameOnNoScreenOfTheMapIsStuck() {
        Game game = new Game("LOADING");

        assertEquals(ScreenWalk.Outcome.STUCK, go(game, "MAP", new ArrayList<>()).outcome());
        assertEquals(ScreenWalk.Outcome.MISSING, go(game, "GONE", new ArrayList<>()).outcome());
    }

    @Test
    void whereLooksAtTheLastScreensNeighboursFirst() {
        Game game = new Game("MAP");

        assertEquals(Optional.of("MAP"), new ScreenWalk(game.map, game, STEP_MS, null).where("CAMPAIGN"));
        assertEquals(Optional.empty(), new ScreenWalk(game.map, new Game("LOADING"), STEP_MS, null).where(null));
    }

    /** A frame that shows {@code pictures} and nothing else; it does nothing. */
    private static class Shows implements Reach {
        private final Set<String> pictures;

        Shows(String... pictures) {
            this.pictures = Set.of(pictures);
        }

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            return pictures.contains(picture.id()) ? Optional.of(new Rect(0, 0, 10, 10)) : Optional.empty();
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {}

        @Override
        public void keys(Combo combo) {}

        @Override
        public void type(String text, boolean clears, boolean submits) {}

        @Override
        public void scroll(Point at, int notches) {}

        @Override
        public void drag(Point from, Point to, Duration time) {}

        @Override
        public long nowMs() {
            return 0;
        }

        @Override
        public void pause(long ms) {}
    }

    /**
     * A window and its tab, each opened from Home, so neither hides the other by the tree: the tab is told by the
     * window's title and its own tab, lit. Two dialogs share a title; one shows no close button.
     */
    private static ScreenGraph tabs() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(named("Home", "home", null, null), click("window"), click("tab_b"),
                click("dialog"), click("shop")));
        screens.put("WINDOW", screen(named("Window", "window_title", "HOME", "window")));
        screens.put("TAB_B", screen(Setup.named("Tab B").markers(marks("window_title", "tab_b_lit"))
                .from(ref("HOME", "tab_b"))));
        screens.put("DIALOG", screen(named("Dialog", "dialog_title", "HOME", "dialog").absent(marks("close_x"))));
        screens.put("SHOP", screen(named("Shop", "dialog_title", "HOME", "shop")));
        return ScreenGraph.of(screens);
    }

    @Test
    void aScreenToldBySeveralMarkersShowsOnlyWhenAllShow() {
        ScreenGraph map = tabs();
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        assertEquals(Optional.of("WINDOW"),
                new ScreenWalk(map, new Shows("window_title", "tab_b"), STEP_MS, null).whereOn(frame, null),
                "the window's title alone is the window, not its tab");
        assertEquals(Optional.of("TAB_B"),
                new ScreenWalk(map, new Shows("window_title", "tab_b_lit"), STEP_MS, null).whereOn(frame, null),
                "the title and the lit tab are the tab: the larger set wins, though the window, first in the map, "
                        + "shows too");
    }

    /** A panel hides the screen it is in, its markers still in view, though its way in is on another screen. */
    @Test
    void aPanelHidesTheScreenItIsIn() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(named("Home", "home", null, null), click("upgrade")));
        screens.put("MARKET", screen(named("Market", "market", null, null), click("deal")));
        screens.put("UPGRADE", screen(named("Upgrade", "upgrade_title", "MARKET", "deal")
                .panelOf(BotConstants.screen("HOME"))));
        ScreenGraph map = ScreenGraph.of(screens);
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        assertEquals(Optional.of("UPGRADE"),
                new ScreenWalk(map, new Shows("home", "upgrade_title"), STEP_MS, null).whereOn(frame, null));
        assertEquals(Optional.of("UPGRADE"),
                new ScreenWalk(map, new Shows("home", "upgrade_title"), STEP_MS, null).whereOn(frame, "HOME"),
                "near the screen it is in");
    }

    @Test
    void anAbsentPictureTellsTwoScreensWithOneTitleApart() {
        ScreenGraph map = tabs();
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        assertEquals(Optional.of("SHOP"),
                new ScreenWalk(map, new Shows("dialog_title", "close_x"), STEP_MS, null).whereOn(frame, null),
                "a close button shown rules the dialog out");
        assertEquals(Optional.of("DIALOG"),
                new ScreenWalk(map, new Shows("dialog_title"), STEP_MS, null).whereOn(frame, "DIALOG"),
                "no close button: both match, and the screen it was last on is looked at first");
    }

    /**
     * A window of another shape shows its titles away from where they were cut: none shows near, so the screens one
     * step from where the game was are looked for over the whole frame; with nothing known, no screen is.
     */
    @Test
    void noScreenShownNearLooksForTheNearbyOnesOverTheWholeFrame() {
        List<String> wide = new ArrayList<>();
        Reach moved = new Shows("window_title") {
            @Override
            public List<Optional<Rect>> findEachNear(List<ImageTemplate> pictures, BufferedImage frame) {
                return pictures.stream().map(p -> Optional.<Rect>empty()).toList();
            }

            @Override
            public List<Optional<Rect>> findEach(List<ImageTemplate> pictures, BufferedImage frame) {
                pictures.forEach(p -> wide.add(p.id()));
                return super.findEach(pictures, frame);
            }
        };
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ScreenWalk walk = new ScreenWalk(tabs(), moved, STEP_MS, null);

        assertEquals(Optional.of("WINDOW"), walk.whereOn(frame, "HOME"));
        assertTrue(wide.contains("window_title"), "looked for over the whole frame: " + wide);
        wide.clear();
        assertEquals(Optional.empty(), walk.whereOn(frame, null));
        assertEquals(List.of("close_x"), wide, "with no screen known, only the absent picture is looked for over the "
                + "whole frame, as always; no marker is");
    }

    @Test
    void markersLookForEachPictureOnceAndNeedOneToTellAnything() {
        List<String> looked = new ArrayList<>();
        Reach counting = new Shows("a", "b") {
            @Override
            public List<Optional<Rect>> findEach(List<ImageTemplate> pictures, BufferedImage frame) {
                pictures.forEach(p -> looked.add(p.id()));
                return super.findEach(pictures, frame);
            }
        };
        BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        List<Boolean> on = Markers.onEach(counting, List.of(new Markers(marks("a", "b"), List.of()),
                new Markers(marks("a"), marks("b")), new Markers(List.of(), List.of())), frame);

        assertEquals(List.of(true, false, false), on);
        // Each marker once near its place, then each absent picture once anywhere.
        assertEquals(List.of("a", "b", "b"), looked);
    }

    /** The game over {@link ScreenGraphTest#loading}: Loading, Home on its own, Home's fight to a transition. */
    private static Game loading(String on) {
        Game game = new Game(on, ScreenGraph.of(ScreenGraphTest.loading()));
        game.leads.put("HOME click fight", "ENTERING");
        return game;
    }

    @Test
    void aWalkWaitsOnTheStartScreenForTheScreenAfterItPressingNothing() {
        Game game = loading("LOADING");
        game.timeline.put(20_000L, "HOME");
        List<String> reached = new ArrayList<>();
        ScreenWalk.Result result = go(game, "HOME", reached);
        assertTrue(result.arrived(), result.why());
        assertEquals(List.of(), game.done, "nothing pressed");
        assertEquals(List.of("LOADING", "HOME"), reached);
        assertTrue(game.now >= 20_000 && game.now < 60_000, "waited past a step's time: " + game.now);
    }

    @Test
    void aStartScreenStillShownOnceItsTimeIsUpIsStuckLoading() {
        Game game = loading("LOADING");
        ScreenWalk.Result result = go(game, "HOME", new ArrayList<>());
        assertEquals(ScreenWalk.Outcome.STUCK, result.outcome());
        assertEquals("Stuck loading on Loading: Home did not show in 60 s.", result.why());
        assertEquals(List.of(), game.done);
    }

    @Test
    void aTransitionTheGameShowedTooBrieflyToSeeIsPassed() {
        Game game = loading("HOME");
        game.leads.put("HOME click fight", "BATTLE");    // Entering battle came and went between two looks
        ScreenWalk.Result result = go(game, "BATTLE", new ArrayList<>());
        assertTrue(result.arrived(), result.why());
        assertEquals(List.of("click fight"), game.done);
    }

    @Test
    void aTransitionIsWaitedForAsLongAsItSays() {
        Game game = loading("HOME");
        game.timeline.put(25_000L, "BATTLE");
        ScreenWalk.Result result = go(game, "BATTLE", new ArrayList<>());
        assertTrue(result.arrived(), result.why());
        Game slow = loading("ENTERING");
        ScreenWalk.Result stuck = go(slow, "BATTLE", new ArrayList<>());
        assertEquals("Stuck loading on Entering battle: Battle did not show in 30 s.", stuck.why());
    }

    @Test
    void aWayOnItsOwnWhoseMethodSaysNoIsNotTaken() {
        Map<String, MapScreen> screens = ScreenGraphTest.loading();
        screens.put("HOME", screens.get("HOME").setup(s -> s.fromWay(Way.onItsOwn(BotConstants.screen("LOADING"))
                .when(() -> false))));
        Game game = new Game("LOADING", ScreenGraph.of(screens));
        game.timeline.put(1_000L, "HOME");
        ScreenWalk.Result result = go(game, "HOME", new ArrayList<>());
        assertFalse(result.arrived(), result.why());
        assertTrue(game.now < 1_000, "nothing waited for: " + game.now);
    }

    /** The game over {@link ScreenGraphTest#fight}: a fight that ends on Victory or Defeat, each after it. */
    private static Game fight(String on) {
        return new Game(on, ScreenGraph.of(ScreenGraphTest.fight()));
    }

    @Test
    void nextReturnsTheScreenThatFollowsOnItsOwn() {
        Game game = fight("BATTLE");
        game.popup = "OFFER";
        game.timeline.put(12_000L, "DEFEAT");
        ScreenWalk walk = new ScreenWalk(game.map, game, STEP_MS, null);
        assertEquals(Optional.of("DEFEAT"), walk.next("BATTLE"));
        assertTrue(game.now >= 12_000 && game.now < 30_000, "waited past a step's time: " + game.now);
        assertEquals(List.of("click close_x"), game.done, "a popup over it is closed");
        assertEquals(Optional.empty(), walk.next("DEFEAT"), "nothing follows a result");
    }

    @Test
    void nextIsEmptyAfterItsLoads() {
        Game game = fight("BATTLE");
        assertEquals(Optional.empty(), new ScreenWalk(game.map, game, STEP_MS, null).next("BATTLE"));
        assertTrue(game.now >= 30_000 && game.now < 31_000, "looked for as long as it loads: " + game.now);
    }

    @Test
    void aReadoutIsReadOnlyWhileItsScreenShows() {
        Game game = new Game("HOME", ScreenGraph.of(ScreenGraphTest.fight())) {
            @Override
            public Optional<String> readout(Readout readout, BufferedImage frame) {
                return Optional.of("1,250");
            }
        };
        ScreenWalk walk = new ScreenWalk(game.map, game, STEP_MS, null);
        assertEquals(Optional.of("1,250"), walk.read("HOME", "gold"));
        assertEquals(Optional.empty(), walk.read("HOME", "silver"), "no such readout");
        game.screen = "BATTLE";
        assertEquals(Optional.empty(), walk.read("HOME", "gold"), "its screen is not shown");
        assertEquals(List.of(), game.done, "nothing walked to");
    }

    /**
     * A tab is walked to by pressing its button where the game is, a sibling tab's included, never by a way back; the
     * screen it is in is reached once one of its tabs shows.
     */
    @Test
    void aTabIsWalkedByPressingIt() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("Home").markers(marks("home_title")), click("hero")));
        screens.put("HERO", screen(Setup.named("Hero").markers(marks("hero_title")).from(ref("HOME", "hero")),
                click("gear_tab").looks(List.of(Look.of(picture("gear_tab_on"), State.SELECTED))),
                click("skills_tab").looks(List.of(Look.of(picture("skills_tab_on"), State.SELECTED)))));
        screens.put("GEAR", screen(Setup.named("Gear").from(ref("HERO", "gear_tab")).tab()));
        screens.put("SKILLS", screen(Setup.named("Skills").from(ref("HERO", "skills_tab")).tab()));
        Game game = new Game("HOME", ScreenGraph.of(screens));
        game.shows.put("GEAR", new HashSet<>(Set.of("hero_title", "gear_tab_on", "skills_tab")));
        game.shows.put("SKILLS", new HashSet<>(Set.of("hero_title", "gear_tab", "skills_tab_on")));
        game.leads.put("HOME click hero", "GEAR");
        game.leads.put("GEAR click skills_tab", "SKILLS");
        game.leads.put("SKILLS click gear_tab", "GEAR");
        game.leads.put("GEAR key ESCAPE", "HOME");
        game.leads.put("SKILLS key ESCAPE", "HOME");

        ScreenWalk.Result result = go(game, "SKILLS", new ArrayList<>());
        assertTrue(result.arrived(), result.why());
        assertEquals(List.of("click hero", "click skills_tab"), game.done, "the hero opens on Gear, then its sibling");

        game.done.clear();
        result = go(game, "GEAR", new ArrayList<>());
        assertTrue(result.arrived(), result.why());
        assertEquals(List.of("click gear_tab"), game.done, "pressed from the tab shown, no way back");

        game.done.clear();
        result = go(game, "HERO", new ArrayList<>());
        assertTrue(result.arrived(), result.why());
        assertEquals("GEAR", result.at(), "a tab of it shows");
        assertEquals(List.of(), game.done);

        result = go(game, "HOME", new ArrayList<>());
        assertTrue(result.arrived(), result.why());
        assertEquals(List.of("key ESCAPE"), game.done, "left as the screen it is in is");
    }

    @Test
    void aGameJustOpenedIsWaitedForUntilItsStartScreenShows() {
        Game game = loading("NOTHING");    // drawing nothing of the map yet
        game.timeline.put(5_000L, "LOADING");
        game.timeline.put(15_000L, "HOME");
        ScreenWalk.Result result = go(game, "HOME", new ArrayList<>());
        assertTrue(result.arrived(), result.why());
        assertEquals(List.of(), game.done);
    }
}
