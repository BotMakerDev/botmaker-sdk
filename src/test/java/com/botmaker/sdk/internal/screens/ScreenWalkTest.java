package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A walk over a fake game: a state machine of screens, each showing its marker and the pictures of its inputs, an
 * input moving it to another screen. Time is counted, not waited.
 */
class ScreenWalkTest {

    private static final long STEP_MS = 2_000;

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    private static Input click(String id) {
        return Input.click(picture(id));
    }

    private static Way from(String constant, Input via) {
        return Way.of(BotConstants.screen(constant), via);
    }

    private static ScreenGraph map() {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").marker(picture("home"))
                .inputs(List.of(click("campaign"), click("engineer"), click("mail"), click("event").sometimes(),
                        click("arena").locked(picture("padlock")), click("lobby"))));
        screens.put("CAMPAIGN", GameScreen.named("Campaign").marker(picture("campaign_title"))
                .from(from("HOME", click("campaign"))).alsoFrom(List.of(from("MAIL", click("mail_campaign"))))
                .inputs(List.of(click("map"), click("bonus").sometimes())));
        screens.put("BONUS", GameScreen.named("Bonus").marker(picture("bonus_title"))
                .from(from("CAMPAIGN", click("bonus").sometimes())));
        screens.put("LOBBY", GameScreen.named("Lobby").from(from("HOME", click("lobby"))));
        screens.put("MAP", GameScreen.named("Map").marker(picture("map_title")).from(from("CAMPAIGN", click("map"))));
        screens.put("ENGINEER", GameScreen.named("Engineer").marker(picture("engineer_title"))
                .from(from("HOME", click("engineer"))));
        screens.put("MAIL", GameScreen.named("Mail").marker(picture("mail_title")).from(from("HOME", click("mail")))
                .inputs(List.of(click("mail_campaign"))));
        screens.put("EVENT", GameScreen.named("Event").marker(picture("event_title"))
                .from(from("HOME", click("event").sometimes())));
        screens.put("ARENA", GameScreen.named("Arena").marker(picture("arena_title"))
                .from(from("HOME", click("arena").locked(picture("padlock")))));
        screens.put("OFFER", GameScreen.named("Offer").marker(picture("offer")).back(click("close_x")).popup());
        return ScreenGraph.of(screens);
    }

    /** The game: which screen shows, a popup over it, and what each input on each screen leads to. */
    private static final class Game implements Reach {
        final ScreenGraph map = map();
        final Map<String, Set<String>> shows = new HashMap<>();
        final Map<String, String> leads = new HashMap<>();
        final List<String> done = new ArrayList<>();
        final List<String> places = new ArrayList<>();
        String screen;
        String popup;
        long now;

        Game(String screen) {
            this.screen = screen;
            map.screens().forEach((c, s) -> {
                Set<String> on = new HashSet<>();
                if (s.marker() != null) on.add(s.marker().id());
                s.inputs().stream().filter(i -> i.picture() != null && !i.comesAndGoes()).forEach(i -> on.add(i.picture().id()));
                if (s.back() != null) on.add(s.back().picture().id());
                shows.put(c, on);
            });
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
            return popup != null ? popup : screen;
        }

        private void did(String what) {
            done.add(what);
            String key = showing() + " " + what;
            if (popup != null) {
                if (what.equals("click close_x") || what.equals("key ESCAPE")) popup = null;
                return;
            }
            String next = leads.get(key);
            if (next != null) screen = next;
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
        public void click(Point at) {
            did("click " + places.get(at.x() / 20));
        }

        @Override
        public void key(Key key) {
            did("key " + key.name());
        }

        @Override
        public void scroll(Point at, int notches) {
            did("scroll " + places.get(at.x() / 20) + " " + notches);
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
        Map<String, GameScreen> screens = new LinkedHashMap<>(game.map.screens());
        screens.put("ENGINEER", screens.get("ENGINEER").from(from("HOME", click("engineer")).when(() -> open[0])));
        // An input's condition is on the screen's input, which the way does (2026-10-15).
        screens.put("HOME", screens.get("HOME").inputs(screens.get("HOME").inputs().stream()
                .map(i -> i.sameAs(click("mail")) ? i.when(() -> open[0]) : i).toList()));
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

    @Test
    void aWayTheGameNoLongerShowsIsMissing() {
        Game game = new Game("HOME");
        game.shows.get("HOME").remove("engineer");

        ScreenWalk.Result walked = go(game, "ENGINEER", new ArrayList<>());

        assertEquals(ScreenWalk.Outcome.MISSING, walked.outcome());
        assertTrue(walked.why().contains("click engineer"), walked.why());
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
}
