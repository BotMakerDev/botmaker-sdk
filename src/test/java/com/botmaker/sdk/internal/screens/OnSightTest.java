package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Custom;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.plan.DoneRecords;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.marks;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a run's walk does on sight, over a game of Home, an ad on it, and a Shop. */
class OnSightTest {

    private static final Button AD_CLOSE = click("ad_close");
    private static final Button SHOP = click("shop");

    /** A clock moved by hand. */
    private static final class Hands extends Clock {
        Instant now = Instant.parse("2026-11-06T12:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    /** Home shows the ad while {@link #ad}; a press of a picture is said, the ad's closing it, the shop's opening it. */
    private static final class Game implements Reach {
        final List<String> pictures = List.of("home", "ad_close", "shop", "shop_title", "also");
        final List<String> pressed = new ArrayList<>();
        final Set<String> leadsToShop = new HashSet<>(Set.of("shop"));
        boolean ad = true;
        boolean also;
        String screen = "HOME";
        long now;

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            String id = picture.id();
            boolean shows = screen.equals("HOME") ? id.equals("home") || id.equals("shop") || ad && id.equals("ad_close")
                    || also && id.equals("also") : id.equals("shop_title") || also && id.equals("also");
            return shows ? Optional.of(new Rect(pictures.indexOf(id) * 100, 0, 10, 10)) : Optional.empty();
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
            String id = pictures.get(at.x() / 100);
            pressed.add(id);
            if (id.equals("ad_close")) ad = false;
            if (leadsToShop.contains(id)) screen = "SHOP";
        }

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
            return now;
        }

        @Override
        public void pause(long ms) {
            now += ms;
        }
    }

    private final Hands clock = new Hands();

    private static ScreenGraph map(Map<String, Do> onHome, Input... more) {
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("adClose", AD_CLOSE);
        inputs.put("shop", SHOP);
        for (Input input : more) inputs.put("more" + inputs.size(), input);
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", new MapScreen(Setup.named("Home").markers(marks("home")), inputs, Map.of(), Map.of(), onHome));
        screens.put("SHOP", new MapScreen(Setup.named("Shop").markers(marks("shop_title")).from(ref("HOME", "shop")),
                Map.of()));
        return ScreenGraph.of(screens);
    }

    private static ScreenWalk.Result go(ScreenGraph map, Game game, DoneRecords records, String target) {
        return new ScreenWalk(map, game, 2_000, null).doing(records).go(target, null);
    }

    private DoneRecords records(Path dir) {
        return new DoneRecords(dir.resolve("done.json"), clock);
    }

    @Test
    void closesAdOnArrival(@TempDir Path dir) {
        DoneRecords records = records(dir);
        Game game = new Game();

        ScreenWalk.Result walked = go(map(Map.of("closeAd", Do.press(AD_CLOSE).onSight())), game, records, "HOME");

        assertTrue(walked.arrived(), walked.why());
        assertEquals(List.of("ad_close"), game.pressed);
        assertTrue(records.last("HOME.closeAd").orElseThrow().held());
    }

    @Test
    void aScreenCrossedCountsAndTheWalkGoesOn(@TempDir Path dir) {
        Game game = new Game();

        ScreenWalk.Result walked = go(map(Map.of("closeAd", Do.press(AD_CLOSE).onSight())), game, records(dir), "SHOP");

        assertTrue(walked.arrived(), walked.why());
        assertEquals(List.of("ad_close", "shop"), game.pressed);
    }

    @Test
    void whatDoesNotShowIsNotWaitedForNorRecorded(@TempDir Path dir) {
        DoneRecords records = records(dir);
        Game game = new Game();
        game.ad = false;

        assertTrue(go(map(Map.of("closeAd", Do.press(AD_CLOSE).onSight())), game, records, "HOME").arrived());

        assertEquals(List.of(), game.pressed);
        assertEquals(0, game.now, "the walk spent no time on it");
        assertTrue(records.last("HOME.closeAd").isEmpty());
    }

    @Test
    void cadenceHoldsItBack(@TempDir Path dir) {
        DoneRecords records = records(dir);
        ScreenGraph map = map(Map.of("closeAd", Do.press(AD_CLOSE).onSight().every(Duration.ofHours(1))));
        Game game = new Game();
        go(map, game, records, "HOME");

        game.ad = true;
        clock.now = clock.now.plus(Duration.ofMinutes(59));
        go(map, game, records, "HOME");
        assertEquals(List.of("ad_close"), game.pressed, "not due yet, though it shows");

        clock.now = clock.now.plus(Duration.ofMinutes(1));
        go(map, game, records, "HOME");
        assertEquals(List.of("ad_close", "ad_close"), game.pressed);
    }

    @Test
    void aFailedCheckWaitsItsRetry(@TempDir Path dir) {
        DoneRecords records = records(dir);
        ScreenGraph map = map(Map.of("closeAd", Do.press(AD_CLOSE).onSight().checks(() -> Answer.NO)));
        Game game = new Game();
        go(map, game, records, "HOME");
        assertFalse(records.last("HOME.closeAd").orElseThrow().held());

        game.ad = true;
        go(map, game, records, "HOME");
        assertEquals(1, game.pressed.size(), "later, by default");
    }

    @Test
    void onlyOnSightOnes(@TempDir Path dir) {
        Game game = new Game();
        go(map(Map.of("closeAd", Do.press(AD_CLOSE))), game, records(dir), "HOME");
        assertEquals(List.of(), game.pressed, "the planner's or a flow's to press");
    }

    @Test
    void aWalkNotDoingAnyLeavesThemAlone() {
        Game game = new Game();
        ScreenGraph map = map(Map.of("closeAd", Do.press(AD_CLOSE).onSight()));

        assertTrue(new ScreenWalk(map, game, 2_000, null).go("HOME", null).arrived());

        assertEquals(List.of(), game.pressed, "the assistant's walks and ExploreMap");
    }

    @Test
    void oneThatLeavesEndsTheRest(@TempDir Path dir) {
        DoneRecords records = records(dir);
        Game game = new Game();
        game.also = true;
        game.leadsToShop.add("ad_close");
        Button also = click("also");
        Map<String, Do> onHome = new LinkedHashMap<>();
        onHome.put("closeAd", Do.press(AD_CLOSE).onSight());
        onHome.put("alsoDo", Do.press(also).onSight());

        ScreenWalk.Result walked = go(map(onHome, also), game, records, "SHOP");

        assertTrue(walked.arrived(), walked.why());
        assertEquals(List.of("ad_close"), game.pressed, "its picture shows on Shop too, and it is Home's");
    }

    /** What a screen with no marker does on sight leaves the walk knowing where it is. */
    @Test
    void aScreenWithNoMarkerStaysWhereTheWalkIs(@TempDir Path dir) {
        Game game = new Game();
        game.also = true;
        Button also = click("also");
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", new MapScreen(Setup.named("Home").markers(marks("home")), Map.of("shop", SHOP)));
        Do action = Do.press(also).onSight();
        screens.put("SHOP", new MapScreen(Setup.named("Shop").from(ref("HOME", "shop")), Map.of("also", also), Map.of(),
                Map.of(), Map.of("alsoDo", action)));
        game.screen = "HOME";
        game.ad = false;

        ScreenWalk.Result walked = go(ScreenGraph.of(screens), game, records(dir), "SHOP");

        assertTrue(walked.arrived(), walked.why());
        assertEquals(List.of("shop", "also"), game.pressed);
    }

    @Test
    void whatIsBeingPressedIsNotAlsoDoneOnSight(@TempDir Path dir) {
        DoneRecords records = records(dir);
        Game game = new Game();
        Do closeAd = Do.press(AD_CLOSE).onSight();
        ScreenGraph map = map(Map.of("closeAd", closeAd));

        assertTrue(records.start(closeAd));
        assertFalse(records.start(closeAd), "it is being done already");
        go(map, game, records, "HOME");
        assertEquals(List.of(), game.pressed, "the flow's press walked here");

        records.finish(closeAd);
        go(map, game, records, "HOME");
        assertEquals(List.of("ad_close"), game.pressed);
    }

    @Test
    void anInputHeldByAStripTakesWhatTheStripSays(@TempDir Path dir) {
        Button member = AD_CLOSE;
        Strip never = Strip.of(Scroll.wheel(-1), marks("ad_close")).never("spends gems");
        DoneRecords records = records(dir);
        Game game = new Game();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("adClose", member);
        inputs.put("list", never);
        ScreenGraph map = ScreenGraph.of(Map.of("HOME", new MapScreen(Setup.named("Home").markers(marks("home")), inputs,
                Map.of(), Map.of(), Map.of("closeAd", Do.press(member).onSight()))));

        assertTrue(go(map, game, records, "HOME").arrived());

        assertEquals(List.of(), game.pressed);
    }

    @Test
    void anInputOfAnotherScreenIsNotDoneHere(@TempDir Path dir) {
        Game game = new Game();
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", new MapScreen(Setup.named("Home").markers(marks("home")), Map.of(), Map.of(), Map.of(),
                Map.of("closeAd", Do.press(AD_CLOSE).onSight())));
        screens.put("SHOP", new MapScreen(Setup.named("Shop").markers(marks("shop_title")), Map.of("adClose", AD_CLOSE)));

        assertTrue(go(ScreenGraph.of(screens), game, records(dir), "HOME").arrived());

        assertEquals(List.of(), game.pressed);
    }

    @Test
    void theBotsOwnCodeThatWalksDoesNotRunItselfAgain(@TempDir Path dir) {
        DoneRecords records = records(dir);
        Game game = new Game();
        List<ScreenWalk.Result> inner = new ArrayList<>();
        List<ScreenGraph> holder = new ArrayList<>();
        int[] runs = {0};
        Custom custom = Custom.of(() -> {
            runs[0]++;
            inner.add(go(holder.getFirst(), game, records, "HOME"));
        });
        holder.add(map(Map.of("own", Do.press(custom).onSight()), custom));

        assertTrue(go(holder.getFirst(), game, records, "HOME").arrived());

        assertEquals(1, runs[0]);
        assertTrue(inner.getFirst().arrived());
        assertTrue(records.last("HOME.own").isPresent());
    }
}
