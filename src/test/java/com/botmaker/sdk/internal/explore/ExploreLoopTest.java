// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.plan.Goal;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Do;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Role;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.internal.explore.tree.Effect;
import com.botmaker.sdk.internal.explore.tree.Locator;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.MapWalk.Arrival;
import com.botmaker.sdk.internal.screens.MapWalk.Took;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.botmaker.shared.web.Guard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.botmaker.sdk.internal.screens.MapFixtures.click;
import static com.botmaker.sdk.internal.screens.MapFixtures.ref;
import static com.botmaker.sdk.internal.screens.MapFixtures.screen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keep exploring's loop over a scripted game: what stops it, what parks, what a resume checks. */
class ExploreLoopTest {

    @TempDir
    Path folder;

    /** A, the start, opens B and D, each free; B and D have their way back. */
    private static ScreenGraph map() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b").free(), click("d").free()));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        screens.put("D", screen(Setup.named("D").from(ref("A", "d"))));
        return ScreenGraph.of(screens);
    }

    /** A game that does what {@code arrival} says, at its target by default; its clock moves only while idle. */
    private static final class Game implements ExploreLoop.Play {
        String at = "A";
        /** Where a recovery or a restart brings the game; null: nowhere the map knows. */
        String recoversTo = "A";
        long nowMs;
        Foreground front = Foreground.UNKNOWN;
        Function<Edge, Took> arrival = edge -> new Took(Arrival.AT_TARGET, edge.to());
        Runnable onIdle = () -> {};
        final List<String> taken = new ArrayList<>();
        /** The screen's tree, null for a game with none. */
        Effect.State state;
        /** How long taking a way takes. */
        long takeMs;
        final List<String> pressed = new ArrayList<>();
        /** Whether the run plays on a -SNAPSHOT SDK, so faults are raised and held. */
        boolean dev;

        @Override
        public boolean dev() {
            return dev;
        }

        @Override
        public String sdkVersion() {
            return dev ? "9.9.9-SNAPSHOT" : "9.9.9";
        }

        @Override
        public Optional<Effect.State> state() {
            return Optional.ofNullable(state);
        }

        @Override
        public Optional<String> refusal(Locator.Located input) {
            return "Buy now".equals(input.locator().name()) ? Optional.of("a buy") : Optional.empty();
        }

        @Override
        public Optional<String> press(String screen, String field, long withinMs) {
            pressed.add(screen + "." + field);
            Effect.State left = afterPress.get(field);
            if (left != null) state = left;
            guardStopped = stopsOnPress.getOrDefault(field, List.of());
            return Optional.ofNullable(at);
        }

        /** What the guard stops on a press of a field: a field not here, nothing. */
        final Map<String, List<Guard.Stopped>> stopsOnPress = new HashMap<>();
        private List<Guard.Stopped> guardStopped = List.of();

        @Override
        public List<Guard.Stopped> stopped() {
            List<Guard.Stopped> now = guardStopped;
            guardStopped = List.of();
            return now;
        }

        /** The tree a press of a field leaves; a field not here leaves the tree as it was. */
        final Map<String, Effect.State> afterPress = new HashMap<>();
        /** The tree the page loads with again; null: reopening is not offered, as for a game. */
        Effect.State loads;

        @Override
        public boolean reopen() {
            if (loads == null) return false;
            pressed.add("reopen");
            state = loads;
            return true;
        }

        /** What {@link #look} throws; null: it looks. */
        RuntimeException lookThrows;
        /** What {@link #look} reads, by screen; a screen not here reads nothing. */
        final Map<String, ExploreLoop.Look> seen = new HashMap<>();
        /** The plan the run plays. */
        Plan plan = Plan.NONE;

        @Override
        public ExploreLoop.Look look(String screen) {
            if (lookThrows != null) throw lookThrows;
            return seen.getOrDefault(screen, ExploreLoop.Look.NOTHING);
        }

        @Override
        public Plan plan() {
            return plan;
        }

        /** The text read around any press. */
        List<TextResult> near = List.of();

        @Override
        public Guards.Around around(Input input) {
            return new Guards.Around(near, front, List.of());
        }

        @Override
        public Foreground front() {
            return front;
        }

        /** Whether {@link #bringBack} brings the app back in front, as an Android emulator's does. */
        boolean bringsBack;

        @Override
        public boolean bringBack() {
            if (!bringsBack) return false;
            front = new Foreground(front.target(), front.target());
            return true;
        }

        /** Where {@link #keep} saves a frame, blank for nowhere. */
        String keptIn = "";

        @Override
        public String keep(String name) {
            return keptIn.isEmpty() ? "" : keptIn + "/" + name + ".png";
        }

        @Override
        public Optional<String> picture(String name, Rectangle box) {
            return state == null ? Optional.empty() : Optional.of("/run/inputs/" + name + ".png");
        }

        @Override
        public void idle(Duration time) {
            nowMs += time.toMillis();
            onIdle.run();
        }

        @Override
        public Optional<String> where() {
            return Optional.ofNullable(at);
        }

        @Override
        public boolean goTo(String screen) {
            at = screen;
            return true;
        }

        /** Each screen the run walked to, in order. */
        final List<String> walkedTo = new ArrayList<>();
        /** The ways the run's last walk kept off. */
        Predicate<Edge> refused = edge -> false;

        @Override
        public boolean goTo(String screen, Predicate<Edge> refused) {
            walkedTo.add(screen);
            this.refused = refused;
            return goTo(screen);
        }

        /** Whether a way's button is on the screen when the run looks for it; every one by default. */
        Predicate<Edge> shown = edge -> true;
        /** Each way the run looked for, by key, in order. */
        final List<String> lookedFor = new ArrayList<>();

        @Override
        public boolean open(Edge edge) {
            lookedFor.add(ExploreLoop.key(edge));
            return shown.test(edge);
        }

        @Override
        public Took take(Edge edge, long withinMs) {
            taken.add(ExploreLoop.key(edge));
            nowMs += takeMs;
            Took took = arrival.apply(edge);
            at = took.at();
            return took;
        }

        @Override
        public boolean recover() {
            at = recoversTo;
            return true;
        }

        @Override
        public void restart() {
            at = recoversTo;
        }
    }

    private Journal journal() {
        return new Journal(folder);
    }

    private ExploreLoop loop(ScreenGraph map, Game game, ExploreLoop.Reload reload, Duration within) {
        return new ExploreLoop(map, game, reload, Guards.with(List.of()), journal(),
                ExploreLoop.Settings.DEFAULT.within(within), () -> game.nowMs);
    }

    /** A 30-minute run whose screens each get 2 minutes, whatever they hold. */
    private ExploreLoop twoMinutesEach(ScreenGraph map, Game game) {
        return new ExploreLoop(map, game, same(map), Guards.with(List.of()), journal(),
                ExploreLoop.Settings.DEFAULT.within(Duration.ofMinutes(30))
                        .screens(ExploreLoop.ScreenBudget.fixed(Duration.ofMinutes(2))), () -> game.nowMs);
    }

    private static ExploreLoop.Reload same(ScreenGraph map) {
        return () -> new ExploreLoop.Reload.Reloaded(1, map, null, List.of());
    }

    private List<ExploreEvent.Stopped> stops() {
        return journal().events().stream().filter(ExploreEvent.Stopped.class::isInstance)
                .map(ExploreEvent.Stopped.class::cast).toList();
    }

    private <T extends ExploreEvent> List<T> events(Class<T> type) {
        return journal().events().stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** The open stop's id, the last one stopped on. */
    private String openStop() {
        return stops().getLast().stop().id();
    }

    /** A fact a condition reads: null until the run reads it, when asking it throws and the condition is unknown. */
    private static final class Gold {
        Integer read;

        int gold() {
            if (read == null) throw new IllegalStateException("gold not read yet");
            return read;
        }
    }

    /** {@link #map()} with A's way to B behind {@code gold > 3}. */
    private static ScreenGraph mapBehind(Gold gold) {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b").free(), click("d").free()));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b")).fromWhen(Condition.of(() -> gold.gold() > 3))));
        screens.put("D", screen(Setup.named("D").from(ref("A", "d"))));
        return ScreenGraph.of(screens);
    }

    /** An unknown never closes a way: one behind a fact never read is pressed, and its screen built. */
    @Test
    void aWayBehindAnUnreadFactIsPressed() {
        Game game = new Game();
        ScreenGraph map = mapBehind(new Gold());

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertTrue(game.taken.contains("A.b→B"), game.taken.toString());
        assertTrue(events(ExploreEvent.Built.class).stream().anyMatch(b -> b.screen().equals("B")),
                journal().events().toString());
    }

    /**
     * A way whose button is not on its screen now is skipped there, and looked for again once the run left the screen
     * and reached it again: pressed when it shows then. Absent a second time, it is left for the next run, which looks
     * for it again.
     */
    @Test
    void aWayNotSeenIsRetriedAfterItsScreenIsReachedAgain() {
        Game game = new Game();
        // Shown from the second look on.
        game.shown = edge -> !ExploreLoop.key(edge).equals("A.b→B")
                || game.lookedFor.stream().filter("A.b→B"::equals).count() > 1;
        ScreenGraph map = map();

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertTrue(game.taken.contains("A.b→B"), game.taken.toString());
        assertTrue(game.taken.indexOf("A.d→D") < game.taken.indexOf("A.b→B"), game.taken.toString());

        Game never = new Game();
        never.shown = edge -> !ExploreLoop.key(edge).equals("A.b→B");
        loop(map, never, same(map), Duration.ofMinutes(12)).run();

        assertFalse(never.taken.contains("A.b→B"), never.taken.toString());
        assertEquals(2, never.lookedFor.stream().filter("A.b→B"::equals).count(), never.lookedFor.toString());

        Game next = new Game();
        loop(map, next, same(map), Duration.ofMinutes(12)).run();

        assertTrue(next.taken.contains("A.b→B"), next.taken.toString());
    }

    /** A way not seen on a screen the run has nowhere to leave for is tried there: the screen is built, not waited on. */
    @Test
    void aWayNotSeenWithNowhereToGoIsTried() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b").free()));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.shown = edge -> false;

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        // Looked for once, then tried; the fake game's walk reaching B anyway is not a look on A.
        assertEquals(1, game.lookedFor.stream().filter("A.b→B"::equals).count(), game.lookedFor.toString());
        assertTrue(events(ExploreEvent.Built.class).stream().anyMatch(b -> b.screen().equals("A")),
                journal().events().toString());
    }

    /** A way whose condition says no is not pressed: its screen counts it as waiting. */
    @Test
    void aWayWhoseConditionIsNoWaits() {
        Game game = new Game();
        Gold gold = new Gold();
        gold.read = 1;
        ScreenGraph map = mapBehind(gold);
        ExploreLoop loop = loop(map, game, same(map), Duration.ofMinutes(12));

        loop.run();

        assertFalse(game.taken.contains("A.b→B"), game.taken.toString());
        assertTrue(loop.open("A").contains("1 waiting on a condition"), loop.open("A").toString());
    }

    /** A stop Claude handed to the user is parked when the run reads the park, not after its 10 minutes. */
    @Test
    void aParkParksTheOpenStopAtOnce() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.UNKNOWN, null) : new Took(Arrival.AT_TARGET, edge.to());
        boolean[] sent = {false};
        game.onIdle = () -> {
            if (!sent[0] && !stops().isEmpty()) {
                sent[0] = true;
                journal().command("park " + openStop());
            }
        };

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        ExploreEvent.Stopped stopped = stops().getFirst();
        ExploreEvent.Parked parked = events(ExploreEvent.Parked.class).getFirst();
        assertEquals(stopped.stop().id(), parked.stopId());
        assertTrue(parked.at() - stopped.at() < Duration.ofMinutes(1).toMillis(), (parked.at() - stopped.at()) + " ms");
    }

    @Test
    void unknownScreenStops() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.UNKNOWN, null) : new Took(Arrival.AT_TARGET, edge.to());

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        ExploreEvent.Stopped stopped = stops().getFirst();
        assertEquals(StopKind.UNKNOWN_SCREEN, stopped.stop().kind());
        assertEquals("A.b→B", stopped.stop().place(), "where it came from");
        assertTrue(stopped.stop().lastPresses().contains("A: b"), stopped.stop().lastPresses().toString());
        assertEquals(1, events(ExploreEvent.Parked.class).size(), "nobody answered in 10 min");
    }

    private static final Rectangle BOX = new Rectangle(0, 0, 100, 20);

    private static UiTree page(UiNode... parts) {
        return new UiTree(List.of(new UiNode("", "", "", "web:RootWebArea", "", BOX, Set.of(), List.of(parts))));
    }

    private static UiNode web(String role, String name, Set<Trait> traits) {
        return new UiNode("", name, name, "web:" + role, "", BOX, traits, List.of());
    }

    /** A page no screen matches is settled off its tree: a new screen with its inputs, the guard's verdict on each. */
    @Test
    void anUnknownPageIsSettledFromItsTree() {
        Game game = new Game();
        game.at = null;
        game.state = Effect.State.of(page(web("heading", "Pricing plans", Set.of()),
                web("button", "Buy now", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE)),
                web("link", "Docs", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/pricing");

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        ExploreEvent.Settled settled = events(ExploreEvent.Settled.class).getFirst();
        ExploreEvent.Stopped stopped = stops().getFirst();
        assertEquals(settled.stopId(), stopped.stop().id(), "the stop it settled");
        assertEquals(StopKind.UNKNOWN_SCREEN, stopped.stop().kind());
        TreeAnswer.NewScreen screen = (TreeAnswer.NewScreen) settled.answers().getFirst();
        assertEquals("Pricing", screen.name());
        assertEquals("/pricing", screen.route());
        List<TreeAnswer.NewInput> inputs = settled.answers().stream().filter(TreeAnswer.NewInput.class::isInstance)
                .map(TreeAnswer.NewInput.class::cast).toList();
        assertEquals(2, inputs.size(), inputs.toString());
        assertEquals("a buy", inputs.getFirst().never());
        assertEquals(null, inputs.get(1).never(), "a link is free");
        assertEquals("/run/inputs/Pricing.docs.png", inputs.get(1).picture(), "each input rendered");
        assertTrue(JournalView.of(journal()).settled(stopped.stop()), "the plugin side's, not Claude's");
        assertEquals(1, events(ExploreEvent.Parked.class).size(), "nobody wrote it in 10 min");
    }

    /** A page the server answered with an error (a 431) is no screen: the run goes back to one it knows. */
    @Test
    void anErrorPageIsNeverSettledAsAScreen() {
        Game game = new Game();
        game.at = null;
        game.state = new Effect.State(page(web("heading", "HTTP Error 431", Set.of())), "/jobs", List.of(),
                List.of(), 431);

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        assertTrue(stops().stream().noneMatch(s -> s.stop().kind() == StopKind.UNKNOWN_SCREEN), stops().toString());
        assertTrue(events(ExploreEvent.Settled.class).stream().flatMap(s -> s.answers().stream())
                .noneMatch(TreeAnswer.NewScreen.class::isInstance), "no screen written");
        assertTrue(stops().stream().noneMatch(s -> s.stop().kind() == StopKind.ERROR_PAGE), "once is recovered");
    }

    /** An error page met twice after one place is said, not recovered in silence again. */
    @Test
    void anErrorPageTwiceOnOnePlaceStops() {
        Game game = new Game();
        game.at = null;
        game.recoversTo = null;
        game.state = new Effect.State(page(web("heading", "HTTP Error 494", Set.of())), "/jobs", List.of(),
                List.of(), 494);
        // The site lets the run back in once it waited on the stop.
        game.onIdle = () -> game.recoversTo = "A";

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        List<Stop> errors = stops().stream().map(ExploreEvent.Stopped::stop)
                .filter(s -> s.kind() == StopKind.ERROR_PAGE).toList();
        assertEquals(1, errors.size(), stops().toString());
        assertTrue(errors.getFirst().missing().contains("HTTP 494 at /jobs twice"), errors.getFirst().missing());
        assertTrue(stops().stream().noneMatch(s -> s.stop().kind() == StopKind.UNKNOWN_SCREEN), stops().toString());
    }

    /** What the run's start settled (Discover's launch target) goes with the first settled stop. */
    @Test
    void whatTheStartSettledGoesWithTheFirstSettledStop() {
        Game game = new Game();
        game.at = null;
        game.state = Effect.State.of(page(web("heading", "Pricing plans", Set.of())), "/pricing");
        TreeAnswer target = new TreeAnswer.NewTarget("web:https://site.test/");

        loop(map(), game, same(map()), Duration.ofMinutes(12)).handOn(List.of(target)).run();

        List<TreeAnswer> answers = events(ExploreEvent.Settled.class).getFirst().answers();
        assertTrue(answers.getFirst() instanceof TreeAnswer.NewScreen, answers.toString());
        assertEquals(target, answers.getLast());
    }

    /** A page the map holds with no pictures gets them as the run reaches it: its frame, each input rendered. */
    @Test
    void aKnownPageWithNoPicturesIsPicturedWhenReached() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("toggle", Button.of(new Locator(Role.BUTTON, "Toggle", null, null, null).element()).free());
        inputs.put("docs", Button.of(new Locator(Role.LINK, "Docs", null, null, null).element()
                .shot("elements/docs.png")).free());
        screens.put("A", new MapScreen(Setup.named("A").start().route("/"), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.keptIn = "/run";
        game.state = Effect.State.of(page(web("button", "Toggle", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE)),
                web("link", "Docs", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertEquals(List.of(new ExploreEvent.Pictured("A", "/run/shot-A.png",
                Map.of("toggle", "/run/inputs/A.toggle.png"), events(ExploreEvent.Pictured.class).getFirst().at())),
                events(ExploreEvent.Pictured.class), "the docs link has its picture already");
    }

    /** nunu.ai, 2026-11-22: a picture cut before its section faded in is its background, so the run takes it again. */
    @Test
    void anInputWhosePictureIsFlatIsPicturedAgain(@TempDir Path dir) throws Exception {
        Path flat = dir.resolve("docs.png");
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", flat.toFile());
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("docs", Button.of(new Locator(Role.LINK, "Docs", null, null, null).element()
                .shot(flat.toString())).free());
        screens.put("A", new MapScreen(Setup.named("A").start().route("/"), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.keptIn = "/run";
        game.state = Effect.State.of(page(web("link", "Docs", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertEquals(Map.of("docs", "/run/inputs/A.docs.png"),
                events(ExploreEvent.Pictured.class).getFirst().inputs());
    }

    /**
     * An input whose last press failed is pressed once again; failing again is the Cartographer's stop, and its screen
     * stays not done. One tried that stays is not pressed, and an old {@code Fails:} note is not read.
     */
    @Test
    void aFailedInputIsPressedOnceAgainThenStops() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("toggle", Button.of(new Locator(Role.BUTTON, "Toggle", null, null, null).element()).free()
                .failed("blocked write: POST /monitoring"));
        inputs.put("docs", Button.of(new Locator(Role.LINK, "Docs", null, null, null).element()).free().tried());
        screens.put("A", new MapScreen(Setup.named("A").start().notes("docs: Fails: an older run's line"), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.state = Effect.State.of(page(web("button", "Toggle", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE)),
                web("link", "Docs", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");
        game.stopsOnPress.put("toggle", List.of(new Guard.Stopped(Guard.Kind.WRITE, "/monitoring", "POST")));

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertEquals(List.of("A.toggle"), game.pressed, "pressed once again, the docs link not at all");
        assertEquals(StopKind.FAILED_INPUT, stops().getFirst().stop().kind());
        assertEquals("A.toggle", stops().getFirst().stop().place());
        assertFalse(map.done("A"));
    }

    /** A screen is finished before it is left: both inputs that stay are pressed before the way to the next screen. */
    @Test
    void theRunFinishesItsScreenBeforeLeaving() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("b", click("b").free());
        inputs.put("toggle", Button.of(new Locator(Role.BUTTON, "Toggle", null, null, null).element()).free());
        inputs.put("theme", Button.of(new Locator(Role.BUTTON, "Theme", null, null, null).element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.state = Effect.State.of(page(web("button", "Toggle", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE)),
                web("button", "Theme", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");
        List<List<String>> pressedBeforeWay = new ArrayList<>();
        game.arrival = edge -> {
            pressedBeforeWay.add(List.copyOf(game.pressed));
            return new Took(Arrival.AT_TARGET, edge.to());
        };

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertEquals(List.of("A.b→B"), game.taken.subList(0, 1));
        assertEquals(List.of("A.toggle", "A.theme"), pressedBeforeWay.getFirst());
    }

    /**
     * A run restarted in the middle of a screen (a reload put it back where the game is) goes on with that screen,
     * not with a shallower one: its way first, before any of the start screen's.
     */
    @Test
    void aReloadedRunGoesBackToItsScreen() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b").free(), click("d").free()));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b")), click("c").free()));
        screens.put("C", screen(Setup.named("C").from(ref("B", "c"))));
        screens.put("D", screen(Setup.named("D").from(ref("A", "d"))));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.at = "B";

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertTrue(game.taken.getFirst().startsWith("B."), game.taken.toString());
        assertTrue(game.taken.indexOf("B.c→C") < game.taken.indexOf("A.d→D"), "B's way before the start's other");
    }

    /** A first failure is no stop: it goes with the next settled stop as a note that says the input failed. */
    @Test
    void aFirstFailureIsANoteNotAStop() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("toggle", Button.of(new Locator(Role.BUTTON, "Toggle", null, null, null).element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.state = Effect.State.of(page(web("button", "Toggle", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");
        game.stopsOnPress.put("toggle", List.of(new Guard.Stopped(Guard.Kind.WRITE, "/monitoring", "POST")));

        loop(map, game, same(map), Duration.ofMinutes(1)).run();

        assertTrue(stops().stream().noneMatch(s -> s.stop().kind() == StopKind.FAILED_INPUT));
        assertTrue(events(ExploreEvent.Settled.class).stream().flatMap(s -> s.answers().stream())
                .anyMatch(a -> a.equals(new TreeAnswer.InputNote("A", "toggle",
                        "press failed, blocked write: POST /monitoring", "blocked write: POST /monitoring"))),
                events(ExploreEvent.Settled.class).toString());
    }

    /** An input that is no way is pressed once; what it did goes with the next settled stop as a note. */
    @Test
    void aFreeInputIsPressedAndNoted() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("toggle", Button.of(new Locator(Role.BUTTON, "Toggle", null, null, null).element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.state = Effect.State.of(page(web("button", "Toggle", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))),
                "/");

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertEquals(List.of("A.toggle"), game.pressed, "pressed once");
        ExploreEvent.Settled settled = events(ExploreEvent.Settled.class).getFirst();
        assertEquals(List.of(new TreeAnswer.InputNote("A", "toggle", "Does nothing a visitor sees")),
                settled.answers());
        assertEquals(StopKind.SETTLED, stops().getFirst().stop().kind());
    }

    /** A tree input is guarded like a way: with another window in front, the run stops and presses nothing. */
    @Test
    void aTreeInputUnderAForeignWindowIsRefused() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("toggle", Button.of(new Locator(Role.BUTTON, "Toggle", null, null, null).element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.state = Effect.State.of(page(web("button", "Toggle", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");
        game.front = new Foreground("com.android.chrome", "com.game");

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertEquals(StopKind.GUARD, stops().getFirst().stop().kind());
        assertEquals(List.of(), game.pressed, "not pressed under another window");
    }

    /** A window in front of an input stops each round it is there, but its refusal is said once a run. */
    @Test
    void aRefusalIsRaisedOncePerRun() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("toggle", Button.of(new Locator(Role.BUTTON, "Toggle", null, null, null).element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.state = Effect.State.of(page(web("button", "Toggle", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");
        game.front = new Foreground("com.android.chrome", "com.game");
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(12).toMillis()) game.front = new Foreground("com.game", "com.game");
        };

        loop(map, game, same(map), Duration.ofMinutes(35)).run();

        assertEquals(1, events(ExploreEvent.Refused.class).size(), events(ExploreEvent.Refused.class).toString());
        assertEquals(List.of("A.toggle"), game.pressed, "pressed once the game was back");
    }

    /** An input a price beside it refuses is set aside, said once, and never pressed. */
    @Test
    void aPriceBesideATreeInputRefusesIt() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("buy", Button.of(new Locator(Role.BUTTON, "Buy", null, null, null).element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.state = Effect.State.of(page(web("button", "Buy", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");
        game.near = List.of(new TextResult("$5", new Rect(0, 0, 10, 10), 90, TextResult.Level.LINE));

        loop(map, game, same(map), Duration.ofMinutes(35)).run();

        List<ExploreEvent.Refused> refused = events(ExploreEvent.Refused.class);
        assertEquals(1, refused.size(), refused.toString());
        assertEquals(Guards.Kind.TEXT, refused.getFirst().guard());
        assertEquals(List.of(), game.pressed);
    }

    /** A press that leaves a list open on the page has the page loaded again, so the next press is not caught by it. */
    @Test
    void aListLeftOpenIsClosedBeforeTheNextPress() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("plan", Button.of(new Locator(Role.BUTTON, "Plan", null, null, null).element()).free());
        inputs.put("docs", Button.of(new Locator(Role.LINK, "Docs", null, null, null).element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        UiNode plan = web("button", "Plan", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE));
        UiNode docs = web("link", "Docs", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE));
        game.loads = Effect.State.of(page(plan, docs), "/");
        game.state = game.loads;
        game.afterPress.put("plan", Effect.State.of(page(plan, docs,
                web("option", "20,000 credits", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/"));

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertEquals(List.of("A.plan", "reopen", "A.docs"), game.pressed);
    }

    /**
     * A screen is built once its ways are all tried, on its own clock: the time the run was on it, not the time it
     * spent elsewhere between. Left with a way untried, it is building, with what is open.
     */
    @Test
    void aScreenIsBuiltOnItsOwnClock() {
        Game game = new Game();
        game.takeMs = 20_000;

        loop(map(), game, same(map()), Duration.ofMinutes(5)).run();

        List<ExploreEvent.Building> building = events(ExploreEvent.Building.class);
        assertEquals("A", building.getFirst().screen());
        assertEquals(20_000, building.getFirst().ms());
        assertEquals(List.of("1 untried"), building.getFirst().open());
        Map<String, Long> built = new LinkedHashMap<>();
        events(ExploreEvent.Built.class).forEach(b -> built.put(b.screen(), b.ms()));
        assertEquals(40_000L, built.get("A"), built.toString());
        assertEquals(20_000L, built.get("B"), "its way back is its one press");
        assertEquals(List.of("A", "B", "D"), built.keySet().stream().sorted().toList());
    }

    /** A start screen A with {@code ways} ways x1…xn, each to a screen of its own with nothing on it. */
    private static ScreenGraph wide(int ways) {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        List<Input> inputs = new ArrayList<>();
        for (int i = 1; i <= ways; i++) inputs.add(click("x" + i).free());
        screens.put("A", screen(Setup.named("A").start(), inputs.toArray(Input[]::new)));
        for (int i = 1; i <= ways; i++) screens.put("X" + i, screen(Setup.named("X" + i).from(ref("A", "x" + i))));
        return ScreenGraph.of(screens);
    }

    private List<Stop> lowYields() {
        return stops().stream().map(ExploreEvent.Stopped::stop).filter(s -> s.kind() == StopKind.LOW_YIELD).toList();
    }

    private Optional<ExploreEvent.Built> builtA() {
        return events(ExploreEvent.Built.class).stream().filter(b -> b.screen().equals("A")).findFirst();
    }

    /**
     * A screen set aside at 2m5s with no other open screen to go to is back on its own work; a second budget later,
     * still open, it stops on its yield and says what is open and where its clock went. Unanswered, it parks and the
     * screen is built anyway.
     */
    @Test
    void aScreenStillUnbuiltAfterASecondBudgetStopsOnItsYield() {
        ScreenGraph map = wide(12);
        Game game = new Game();
        game.takeMs = 25_000;

        twoMinutesEach(map, game).run();

        List<Stop> low = lowYields();
        assertEquals(1, low.size(), stops().toString());
        Stop stop = low.getFirst();
        assertEquals("A", stop.screen());
        assertTrue(stop.missing().contains("after 4m10s on its clock, 2m5s since it was set aside: 2 untried"),
                stop.missing());
        assertTrue(stop.missing().contains("its clock went to pressing 4m10s."), stop.missing());
        assertEquals(1, events(ExploreEvent.Parked.class).stream().filter(p -> p.stopId().equals(stop.id())).count());
        assertTrue(builtA().isPresent(), "a parked low-yield stop holds nothing open: " + game.taken);
        assertTrue(events(ExploreEvent.Fixed.class).stream().anyMatch(f -> f.stopId().equals(stop.id())
                && f.why().equals("its screen was built")), events(ExploreEvent.Fixed.class).toString());
        assertTrue(journal().queue().stream().noneMatch(s -> s.kind() == StopKind.LOW_YIELD), "answered once built");
    }

    /** A resume gives the screen a fresh clock: built two presses later, which says the fix took. */
    @Test
    void aLowYieldResumedStartsTheScreensClockAgain() {
        ScreenGraph map = wide(12);
        Game game = new Game();
        game.takeMs = 25_000;
        boolean[] sent = {false};
        game.onIdle = () -> {
            if (!sent[0] && !lowYields().isEmpty()) {
                sent[0] = true;
                journal().command("resume " + lowYields().getFirst().id());
            }
        };

        twoMinutesEach(map, game).run();

        assertEquals(1, lowYields().size(), stops().toString());
        assertEquals(50_000, builtA().orElseThrow().ms(), "x11 and x12 on a fresh clock");
        String id = lowYields().getFirst().id();
        assertTrue(events(ExploreEvent.Fixed.class).stream().anyMatch(f -> f.stopId().equals(id)
                && f.why().equals("built on its fresh clock")), events(ExploreEvent.Fixed.class).toString());
    }

    /** A resume with ignore leaves the screen as it is: no fresh clock, and no low-yield stop on it again. */
    @Test
    void aLowYieldIgnoredLeavesTheScreenAsItIs() {
        ScreenGraph map = wide(20);
        Game game = new Game();
        game.takeMs = 25_000;
        boolean[] sent = {false};
        game.onIdle = () -> {
            if (!sent[0] && !lowYields().isEmpty()) {
                sent[0] = true;
                journal().command("resume " + lowYields().getFirst().id() + " ignore");
            }
        };

        twoMinutesEach(map, game).run();

        assertEquals(1, lowYields().size(), "ten more presses past another budget, not stopped on again");
        assertTrue(builtA().orElseThrow().ms() > 500_000, "its clock goes on: " + builtA());
        String id = lowYields().getFirst().id();
        assertTrue(events(ExploreEvent.Fixed.class).stream().anyMatch(f -> f.stopId().equals(id)
                && f.why().equals("ignored")), events(ExploreEvent.Fixed.class).toString());
    }

    /**
     * A screen with more ways than its budget allows is set aside once its clock runs over: the run walks to the next
     * open screen, builds it, and finishes the first one after.
     */
    @Test
    void aScreenOverItsBudgetIsLeftForTheNextOne() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        List<String> xs = List.of("x1", "x2", "x3", "x4", "x5", "x6");
        List<Input> ways = new ArrayList<>();
        xs.forEach(x -> ways.add(click(x).free()));
        ways.add(click("b").free());
        screens.put("A", screen(Setup.named("A").start(), ways.toArray(Input[]::new)));
        for (String x : xs) screens.put(x.toUpperCase(), screen(Setup.named(x.toUpperCase()).from(ref("A", x))));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b")), click("y").free()));
        screens.put("Y", screen(Setup.named("Y").from(ref("B", "y"))));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.takeMs = 25_000;

        twoMinutesEach(map, game).run();

        List<ExploreEvent.SetAside> aside = events(ExploreEvent.SetAside.class);
        assertEquals(List.of("A"), aside.stream().map(ExploreEvent.SetAside::screen).toList(), aside.toString());
        assertEquals(125_000, aside.getFirst().ms(), "five presses of 25 s: " + game.taken);
        int y = game.taken.indexOf("B.y→Y");
        int x6 = game.taken.indexOf("A.x6→X6");
        assertTrue(y >= 0 && x6 > y, "B is built before A's last ways: " + game.taken);
        assertTrue(events(ExploreEvent.Built.class).stream().anyMatch(b -> b.screen().equals("A")),
                "A is finished after: " + game.taken);
        assertTrue(JournalView.of(journal()).build().aside().isEmpty(), "built, it is no longer aside");
    }

    /**
     * A screen's budget is what it holds at the run's measured press time: 18 ways at 12.6 s give 3m47s, inside floor
     * and cap, so the screen is built without being set aside. Two ways get the floor; a huge screen the cap.
     */
    @Test
    void aScreensBudgetIsItsLeftInputsTimesThePressTime() {
        ScreenGraph map = wide(18);
        Game game = new Game();
        game.takeMs = 12_600;
        ExploreLoop loop = loop(map, game, same(map), Duration.ofMinutes(30));

        loop.run();

        assertEquals(Duration.ofMillis(226_800), loop.budget("A"));
        assertTrue(events(ExploreEvent.SetAside.class).isEmpty(), events(ExploreEvent.SetAside.class).toString());
        assertTrue(builtA().isPresent(), game.taken.toString());
        assertEquals(Duration.ofMinutes(2), loop.budget("X1"), "nothing on it: the floor");
        assertEquals(Duration.ofMinutes(15), ExploreLoop.Settings.DEFAULT.screenBudget().fit(Duration.ofHours(1)));
    }

    /** A low-yield stop names what is left on its screen, so whoever answers sees which inputs never got pressed. */
    @Test
    void lowYieldNamesTheInputsLeft() {
        ScreenGraph map = wide(12);
        Game game = new Game();
        game.takeMs = 25_000;

        twoMinutesEach(map, game).run();

        Stop stop = lowYields().getFirst();
        assertTrue(stop.missing().contains("2 untried (left: x11, x12)"), stop.missing());
    }

    /** An element with no shot is open until the run takes its picture off the page. */
    @Test
    void anElementWithNoPictureIsUncropped() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("docs", Button.of(new Locator(Role.LINK, "Docs", null, null, null).element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.state = Effect.State.of(page(web("link", "Docs", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");
        ExploreLoop loop = loop(map, game, same(map), Duration.ofMinutes(1));

        assertEquals(List.of("1 uncropped", "1 untried"), loop.open("A"));
    }

    /** Exploring builds the map only: a value no readout covers raises no stop, and the ways are taken all the same. */
    @Test
    void exploringPlaysNoPlanAndStopsForNoFact() {
        Game game = new Game();
        game.seen.put("A", new ExploreLoop.Look(List.of(new Stop.Text("Gold 1200", new Rect(40, 40, 80, 20))),
                List.of()));
        game.plan = Plan.of(Plan.tier(Goal.done(() -> Do.press(click("b")))));

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        assertTrue(stops().stream().noneMatch(s -> s.stop().kind() == StopKind.MISSING_FACT), stops().toString());
        assertTrue(game.taken.containsAll(List.of("A.b→B", "A.d→D")), game.taken.toString());
    }

    /** An input that spends with no goal's action pressing it is open; one a goal of the plan does is not. */
    @Test
    void aSpendingInputWithNoGoalKeepsItsScreenOpen() {
        Button buy = click("buy").spends(Progress::gems);
        Button sell = click("sell").spends(Progress::gems);
        Do selling = Do.press(sell);
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), buy, sell).withAction("selling", selling)
                .withAction("buying", Do.press(buy)));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.plan = Plan.of(Plan.tier(Goal.done(() -> selling)));

        ExploreLoop loop = loop(map, game, same(map), Duration.ofMinutes(1));
        assertEquals(List.of("1 spending for no goal"), loop.open("A"));

        game.plan = Plan.NONE;
        assertEquals(List.of(), loop.open("A"), "a run with no plan asks no goal of a spend");
    }

    /** A loop of an hour toward {@code goal}, with {@code settings} changed as given. */
    private ExploreLoop toward(ScreenGraph map, Game game, ExploreGoal goal,
                               Function<ExploreLoop.Settings, ExploreLoop.Settings> settings) {
        return new ExploreLoop(map, game, same(map), Guards.with(List.of()), journal(),
                settings.apply(ExploreLoop.Settings.DEFAULT.within(Duration.ofHours(1))), () -> game.nowMs)
                .toward(goal);
    }

    /** The frontier's best press is made first, before the one the map lists first. */
    @Test
    void theBestRankedPressComesFirst() {
        journal().append(new ExploreEvent.Ranked("A", "b", 1, PressTag.SAFE, 0));
        journal().append(new ExploreEvent.Ranked("A", "d", 9, PressTag.SAFE, 0));
        Game game = new Game();

        toward(map(), game, new ExploreGoal("reach D", null), s -> s).run();

        assertEquals("A.d→D", game.taken.getFirst(), game.taken.toString());
    }

    /**
     * The Reader ranks a tree input by the words it shows, the map names it by its field: the best is pressed by its
     * words, and one tagged risky is never pressed, whatever it is called.
     */
    @Test
    void aRankBySaidWordsReachesTheInputAndARiskyOneIsNeverPressed() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("pairNewDevice", Button.of(new Locator(Role.BUTTON, "Pair new device", null, null, null).element())
                .free());
        inputs.put("passwordsAccounts", Button.of(new Locator(Role.BUTTON, "Passwords & accounts", null, null, null)
                .element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        journal().append(new ExploreEvent.Ranked("A", "Pair new device", 1, PressTag.RISKY, 0));
        journal().append(new ExploreEvent.Ranked("A", "Passwords & accounts", 10, PressTag.SAFE, 0));
        Game game = new Game();
        game.state = Effect.State.of(page(
                web("button", "Pair new device", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE)),
                web("button", "Passwords & accounts", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");

        toward(map, game, new ExploreGoal("add a Google account", null), s -> s).run();

        assertEquals("A.passwordsAccounts", game.pressed.getFirst(), game.pressed.toString());
        assertFalse(game.pressed.contains("A.pairNewDevice"), "tagged risky: " + game.pressed);
    }

    /**
     * A way ranked by its words is that way: a risky one is never pressed, and the best, once pressed, leaves the
     * frontier, so no walk comes back for it.
     */
    @Test
    void aWayRankedByItsWordsIsHeldOrPressedOnce() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("pair", Button.of(new Locator(Role.BUTTON, "Pair new device", null, null, null).element()).free());
        inputs.put("accounts", Button.of(new Locator(Role.BUTTON, "Passwords & accounts", null, null, null).element())
                .free());
        inputs.put("d", click("d").free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        screens.put("P", screen(Setup.named("P").from(ref("A", "pair"))));
        screens.put("C", screen(Setup.named("C").from(ref("A", "accounts"))));
        screens.put("D", screen(Setup.named("D").from(ref("A", "d"))));
        ScreenGraph map = ScreenGraph.of(screens);
        journal().append(new ExploreEvent.Ranked("A", "Pair new device", 9, PressTag.RISKY, 0));
        journal().append(new ExploreEvent.Ranked("A", "Passwords & accounts", 10, PressTag.SAFE, 0));
        Game game = new Game();

        toward(map, game, new ExploreGoal("add a Google account", null), s -> s).run();

        assertEquals("A.accounts→C", game.taken.getFirst(), game.taken.toString());
        assertTrue(game.taken.stream().noneMatch(t -> t.startsWith("A.pair")), "tagged risky: " + game.taken);
        assertEquals(1, game.taken.stream().filter(t -> t.startsWith("A.accounts")).count(), game.taken.toString());
    }

    /**
     * A rank by a picture's name ({@code add_account_button}) reaches the row the map holds by its words: the goal's
     * best is pressed first, never dropped for an untried one.
     */
    @Test
    void aRankByAPicturesNameReachesTheRowItShows() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("search", Button.of(new Locator(Role.BUTTON, "Search settings", null, null, null).element()).free());
        inputs.put("addAccount", Button.of(new Locator(Role.BUTTON, "Add account", null, null, null).element()).free());
        screens.put("A", new MapScreen(Setup.named("A").start(), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        journal().append(new ExploreEvent.Ranked("A", "add_account_button", 10, PressTag.SAFE, 0));
        Game game = new Game();
        game.state = Effect.State.of(page(
                web("button", "Search settings", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE)),
                web("button", "Add account", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");

        toward(map, game, new ExploreGoal("add a Google account", null), s -> s).run();

        assertEquals("A.addAccount", game.pressed.getFirst(), game.pressed.toString());
    }

    /** Reaching the goal's screen is a witness by code: said, and the run ends done. */
    @Test
    void theGoalScreenReachedEndsTheRunDone() {
        journal().append(new ExploreEvent.Ranked("A", "d", 9, PressTag.SAFE, 0));
        Game game = new Game();

        assertEquals(ExploreLoop.End.DONE, toward(map(), game, new ExploreGoal("", "D"), s -> s).run());

        List<ExploreEvent.Witnessed> witnessed = events(ExploreEvent.Witnessed.class);
        assertEquals(1, witnessed.size(), witnessed.toString());
        assertEquals(WitnessCheck.SCREEN_ON_MAP, witnessed.getFirst().check());
        assertEquals(List.of("A.d→D"), game.taken, "nothing pressed after the goal");
    }

    /** A done no check holds on is a finding, and the run goes on. */
    @Test
    void doneWithoutAWitnessIsAFindingAndTheRunGoesOn() {
        Game game = new Game();
        // Written while the run goes, after its first press: what was written before it starts is dropped.
        game.arrival = edge -> {
            if (game.taken.size() == 1) journal().command("done Welcome back");
            return new Took(Arrival.AT_TARGET, edge.to());
        };

        assertEquals(ExploreLoop.End.DONE, toward(map(), game, ExploreGoal.NONE, s -> s).run());

        List<ExploreEvent.Finding> findings = events(ExploreEvent.Finding.class);
        assertEquals(FindingKind.NO_WITNESS, findings.getFirst().kind(), findings.toString());
        assertTrue(events(ExploreEvent.Witnessed.class).isEmpty());
        assertTrue(game.taken.containsAll(List.of("A.b→B", "A.d→D")), game.taken.toString());
    }

    /** A done whose mark the frame shows is witnessed by its text, and the run ends done at once. */
    @Test
    void doneWithAWitnessEndsTheRunDone() {
        Game game = new Game();
        game.arrival = edge -> {
            journal().command("done welcome back");
            return new Took(Arrival.AT_TARGET, edge.to());
        };
        game.seen.put("B", new ExploreLoop.Look(List.of(new Stop.Text("Welcome  Back, Ann", new Rect(0, 0, 9, 9))),
                List.of()));

        assertEquals(ExploreLoop.End.DONE, toward(map(), game, new ExploreGoal("sign in", null), s -> s).run());

        assertEquals(WitnessCheck.TEXT, events(ExploreEvent.Witnessed.class).getFirst().check());
        assertEquals(List.of("A.b→B"), game.taken, "nothing pressed once the goal is witnessed");
    }

    /** A goal not reached once nothing is left, with nothing waiting on the user, is unreachable. */
    @Test
    void aGoalNotReachedWithNothingWaitingIsUnreachable() {
        assertEquals(ExploreLoop.End.UNREACHABLE,
                toward(map(), new Game(), new ExploreGoal("the shop", null), s -> s).run());
    }

    /** A map with a way {@code delete} beside {@code b}. */
    private static ScreenGraph withDelete() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b").free(), click("delete").free()));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        screens.put("X", screen(Setup.named("X").from(ref("A", "delete"))));
        return ScreenGraph.of(screens);
    }

    /** A denied label is never pressed and, met on the way, keeps no goal waiting: unreachable. */
    @Test
    void aDeniedPressIsNeverMade() {
        Game game = new Game();

        assertEquals(ExploreLoop.End.UNREACHABLE,
                toward(withDelete(), game, new ExploreGoal("the shop", null), s -> s).run());

        assertFalse(game.taken.contains("A.delete→X"), game.taken.toString());
        assertTrue(events(ExploreEvent.Ranked.class).stream()
                .anyMatch(r -> r.press().equals("delete") && r.tag() == PressTag.DESTRUCTIVE));
    }

    /** A screen only a denied way reaches is never walked to: its ranked press is parked, the way never pressed. */
    @Test
    void aWalkNeverGoesThroughADeniedWay() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b").free(), click("delete").free()));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        screens.put("X", screen(Setup.named("X").from(ref("A", "delete")), click("y").free()));
        screens.put("Y", screen(Setup.named("Y").from(ref("X", "y"))));
        journal().append(new ExploreEvent.Ranked("X", "y", 9, PressTag.SAFE, 0));
        Game game = new Game();
        ScreenGraph map = ScreenGraph.of(screens);

        toward(map, game, new ExploreGoal("the shop", null), s -> s).run();

        assertFalse(game.walkedTo.contains("X"), game.walkedTo.toString());
        assertFalse(game.taken.contains("A.delete→X"), game.taken.toString());
        Edge delete = map.edges().stream().filter(e -> e.label().equals("delete")).findFirst().orElseThrow();
        assertTrue(game.walkedTo.isEmpty() || game.refused.test(delete), "a walk keeps off the denied way");
    }

    /** A press the Reader ranked toward the goal that nobody watching may make leaves the run stuck on it. */
    @Test
    void aGoalPressWaitingOnTheUserEndsTheRunStuck() {
        journal().append(new ExploreEvent.Ranked("A", "delete", 5, PressTag.SAFE, 0));
        Game game = new Game();

        assertEquals(ExploreLoop.End.STUCK,
                toward(withDelete(), game, new ExploreGoal("the shop", null), s -> s).run());

        assertFalse(game.taken.contains("A.delete→X"), game.taken.toString());
        assertEquals(FindingKind.STUCK, events(ExploreEvent.Finding.class).getLast().kind());
    }

    /** Ranks carried from a run toward a goal are not the order of a run with none. */
    @Test
    void aRunWithNoGoalKeepsTheMapsOrder() {
        journal().append(new ExploreEvent.Ranked("A", "d", 9, PressTag.SAFE, 0));
        Game game = new Game();

        toward(map(), game, ExploreGoal.NONE, s -> s).run();

        assertEquals("A.b→B", game.taken.getFirst(), game.taken.toString());
    }

    /** A spent press budget ends the run, and what it ranked and did not press is carried to the next run. */
    @Test
    void aSpentPressBudgetEndsTheRunAndTheFrontierCarriesOver(@TempDir Path project) {
        Journal first = Journal.begin(project, 1_000);
        first.append(new ExploreEvent.Ranked("A", "d", 9, PressTag.SAFE, 0));
        first.append(new ExploreEvent.Ranked("A", "b", 1, PressTag.SAFE, 0));
        Game game = new Game();

        ExploreLoop.End end = new ExploreLoop(map(), game, same(map()), Guards.with(List.of()), first,
                ExploreLoop.Settings.DEFAULT.within(Duration.ofHours(1)).spending(1, 0), () -> game.nowMs)
                .toward(new ExploreGoal("reach B", null)).run();

        assertEquals(ExploreLoop.End.BUDGET, end);
        assertEquals(List.of("A.d→D"), game.taken);
        List<ExploreEvent.Ranked> carried = Journal.begin(project, 2_000).events().stream()
                .filter(ExploreEvent.Ranked.class::isInstance).map(ExploreEvent.Ranked.class::cast).toList();
        assertEquals(List.of("A.b"), carried.stream().map(r -> r.screen() + "." + r.press()).toList());
    }

    /** A fact group of the test's, for an input to spend. */
    private interface Progress {
        int gems();
    }

    @Test
    void newScreenIsAnEventNotAStop() {
        Game game = new Game();

        assertEquals(ExploreLoop.End.DONE, loop(map(), game, same(map()), Duration.ofMinutes(5)).run());

        List<ExploreEvent.NewScreen> found = events(ExploreEvent.NewScreen.class);
        assertEquals(List.of("A", "B", "D"), found.stream().map(ExploreEvent.NewScreen::screen).toList());
        assertEquals("A", found.get(1).from());
        assertEquals("b", found.get(1).by());
        assertTrue(stops().isEmpty(), stops().toString());
        assertTrue(game.taken.containsAll(List.of("A.b→B", "A.d→D")), game.taken.toString());
    }

    /** {@code maxScreens} ends the run once it has been on that many screens, long before its time is spent. */
    @Test
    void aScreenBudgetEndsTheRun() {
        Game game = new Game();

        ExploreLoop.End end = new ExploreLoop(map(), game, same(map()), Guards.with(List.of()), journal(),
                ExploreLoop.Settings.DEFAULT.within(Duration.ofHours(5)).upTo(2), () -> game.nowMs).run();

        assertEquals(ExploreLoop.End.BUDGET, end);
        assertEquals(List.of("A", "B"), events(ExploreEvent.NewScreen.class).stream()
                .map(ExploreEvent.NewScreen::screen).toList());
        assertTrue(game.nowMs < Duration.ofMinutes(5).toMillis(), "ended by screens, not time: " + game.nowMs);
    }

    @Test
    void untaggedMapGivesOneBatchStop() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b"), click("d")));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        screens.put("D", screen(Setup.named("D").from(ref("A", "d"))));
        Game game = new Game();

        loop(ScreenGraph.of(screens), game, same(map()), Duration.ofMinutes(30)).run();

        Stop batch = stops().getFirst().stop();
        assertEquals(StopKind.NO_VERDICT, batch.kind());
        assertTrue(batch.missing().contains("A.b") && batch.missing().contains("A.d"), batch.missing());
        // A way back has no input to judge: only what is in front guards it.
        assertTrue(game.taken.stream().noneMatch(t -> t.startsWith("A.")),
                "no input pressed without a verdict: " + game.taken);
        assertEquals(1, stops().stream().filter(s -> s.stop().place().equals(batch.place())).count(),
                "the batch is raised once");
    }

    /** A dialog opened from B and from A: its close goes back where the game came from, not to its first parent. */
    private static ScreenGraph dialogMap() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b").free(), click("p").free(), click("d").free()));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b")), click("x").free()));
        screens.put("D", screen(Setup.named("D").from(ref("A", "d"))));
        screens.put("P", screen(Setup.named("P").from(ref("B", "x")).alsoFrom(List.of(Way.of(ref("A", "p"))))
                .back(ref("P", "close")), click("close").free()));
        return ScreenGraph.of(screens);
    }

    @Test
    void aWayBackArrivesWhereTheGameCameFrom() {
        Game game = new Game();
        String[] came = {null};
        game.arrival = edge -> {
            if (!edge.back()) {
                came[0] = edge.from();
                return new Took(Arrival.AT_TARGET, edge.to());
            }
            return new Took(came[0].equals(edge.to()) ? Arrival.AT_TARGET : Arrival.ELSEWHERE, came[0]);
        };

        loop(dialogMap(), game, same(dialogMap()), Duration.ofMinutes(14)).run();

        assertTrue(stops().stream().noneMatch(s -> s.stop().kind() == StopKind.WRONG_ARRIVAL), stops().toString());
        assertTrue(game.taken.stream().anyMatch(k -> k.startsWith("P.")), game.taken.toString());
    }

    @Test
    void aWayBackToAScreenThatNeverLedThereStops() {
        Game game = new Game();
        game.arrival = edge -> edge.back() && edge.from().equals("P") ? new Took(Arrival.ELSEWHERE, "D")
                : new Took(Arrival.AT_TARGET, edge.to());

        loop(dialogMap(), game, same(dialogMap()), Duration.ofMinutes(14)).run();

        assertTrue(stops().stream().anyMatch(s -> s.stop().kind() == StopKind.WRONG_ARRIVAL), stops().toString());
    }

    @Test
    void parkedStopIsAvoidedAndNotRepeated() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());

        loop(map(), game, same(map()), Duration.ofMinutes(14)).run();

        List<ExploreEvent.Stopped> wrong = stops().stream()
                .filter(s -> s.stop().kind() == StopKind.WRONG_ARRIVAL).toList();
        assertEquals(1, wrong.size(), stops().toString());
        assertEquals(1, game.taken.stream().filter("A.b→B"::equals).count(), "its place avoided once parked");
        assertEquals(List.of(wrong.getFirst().stop().id()), journal().queue().stream().map(Stop::id).toList());
        assertTrue(game.taken.contains("A.d→D"), "the run went on elsewhere");
    }

    @Test
    void foreignWindowNeverParks() {
        Game game = new Game();
        game.front = new Foreground("com.android.chrome", "com.game");
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(12).toMillis()) game.front = new Foreground("com.game", "com.game");
        };

        loop(map(), game, same(map()), Duration.ofMinutes(35)).run();

        Stop guard = stops().getFirst().stop();
        assertEquals(StopKind.GUARD, guard.kind());
        assertTrue(guard.missing().contains("com.android.chrome"), guard.missing());
        assertTrue(events(ExploreEvent.Parked.class).isEmpty(), "never parked, 12 min in front");
        assertEquals(guard.id(), events(ExploreEvent.Fixed.class).getFirst().stopId());
        assertTrue(game.taken.contains("A.b→B"), "pressed once the game was back");
    }

    /**
     * A press that left the app for the launcher is no screen to map: the app is brought back at once, said once, and
     * the run goes on with no stop (the Reader made the launcher a screen, 2026-11-25).
     */
    @Test
    void anAppLeftForTheLauncherIsBroughtBackWithNoStop() {
        Game game = new Game();
        game.front = new Foreground("com.android.launcher3", "com.game");
        game.bringsBack = true;

        loop(map(), game, same(map()), Duration.ofMinutes(35)).run();

        assertTrue(stops().isEmpty(), stops().toString());
        List<ExploreEvent.Refused> left = events(ExploreEvent.Refused.class);
        assertEquals(1, left.size(), left.toString());
        assertTrue(left.getFirst().why().contains("com.android.launcher3 came in front of com.game"), left.toString());
        assertTrue(game.taken.contains("A.b→B"), "pressed once the app was back");
    }

    /** A window that stays in front past the stuck time ends the run: never parked, nothing pressed into it. */
    @Test
    void stuckIsCheckedWhilePaused() {
        Game game = new Game();
        game.front = new Foreground("com.android.chrome", "com.game");

        ExploreLoop.End end = loop(map(), game, same(map()), Duration.ofMinutes(60)).run();

        assertEquals(ExploreLoop.End.STUCK, end);
        assertTrue(game.nowMs < Duration.ofMinutes(20).toMillis(), "ended at the stuck time: " + game.nowMs);
        assertTrue(game.taken.isEmpty(), game.taken.toString());
        assertTrue(events(ExploreEvent.Parked.class).isEmpty());
    }

    /** A run whose map is built and has nothing left to press ends by itself, not after the stuck time (2026-11-24). */
    @Test
    void aMapWithNothingLeftEndsDoneAtOnce() {
        Game game = new Game();

        ExploreLoop.End end = loop(map(), game, same(map()), Duration.ofMinutes(60)).run();

        assertEquals(ExploreLoop.End.DONE, end);
        assertTrue(game.nowMs < Duration.ofMinutes(2).toMillis(), "ended at once, not at the stuck time: " + game.nowMs);
        assertEquals(ExploreLoop.End.DONE, events(ExploreEvent.Ended.class).getLast().end());
        assertTrue(stops().stream().noneMatch(s -> s.stop().kind() == StopKind.STUCK), stops().toString());
    }

    /** What is still open while nothing can be pressed is said in a stop at once, not after the stuck time. */
    @Test
    void aRunWithAScreenStillOpenStopsAtOnceNamingIt() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());

        loop(map(), game, same(map()), Duration.ofMinutes(90)).run();

        Stop stuck = stops().stream().map(ExploreEvent.Stopped::stop).filter(s -> s.kind() == StopKind.STUCK)
                .findFirst().orElseThrow();
        assertTrue(stuck.missing().contains("still open"), stuck.missing());
        assertTrue(stuck.at() < Duration.ofMinutes(12).toMillis(), "raised at once: " + stuck.at());
    }

    /** The start-of-run batch of inputs with no verdict parks like any stop nobody answers. */
    @Test
    void everyBlockingStopEndsOnItsBudget() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("A", screen(Setup.named("A").start(), click("b"), click("d")));
        screens.put("B", screen(Setup.named("B").from(ref("A", "b"))));
        Game game = new Game();

        loop(ScreenGraph.of(screens), game, same(map()), Duration.ofMinutes(15)).run();

        Stop batch = stops().getFirst().stop();
        assertEquals(StopKind.NO_VERDICT, batch.kind());
        assertTrue(events(ExploreEvent.Parked.class).stream().anyMatch(p -> p.stopId().equals(batch.id())),
                "parked after 10 min");
        assertTrue(game.taken.stream().noneMatch(t -> t.startsWith("A.")),
                "still no input pressed without a verdict: " + game.taken);
    }

    @Test
    void resumeCommandReloadsAndChecksTheStopIsGone() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());
        int[] reloads = {0};
        ExploreLoop.Reload reload = () -> {
            reloads[0]++;
            if (reloads[0] == 1) return new ExploreLoop.Reload.Reloaded(0, map(), null, List.of("Shop.java:3: ';' expected"));
            // The second compiles but fixes nothing; the third fixes the way.
            if (reloads[0] == 3) game.arrival = edge -> new Took(Arrival.AT_TARGET, edge.to());
            return new ExploreLoop.Reload.Reloaded(reloads[0] - 1, map(), null, List.of());
        };
        game.onIdle = () -> {
            if (!stops().isEmpty() && game.nowMs % 60_000 == 0) journal().command("resume " + openStop());
        };

        loop(map(), game, reload, Duration.ofMinutes(8)).run();

        List<ExploreEvent.Reloaded> reloaded = events(ExploreEvent.Reloaded.class);
        assertTrue(reloaded.getFirst().refused(), "a compile error refuses the resume");
        List<ExploreEvent.Stopped> stopped = stops();
        assertEquals(2, stopped.size(), stopped.toString());
        assertFalse(stopped.getFirst().again());
        assertTrue(stopped.get(1).again(), "the same stop at once after a reload");
        assertTrue(stopped.get(1).stop().missing().startsWith("the fix did not take"), stopped.get(1).stop().missing());
        List<ExploreEvent.Fixed> fixed = events(ExploreEvent.Fixed.class);
        assertEquals(List.of(stopped.get(1).stop().id()), fixed.stream().map(ExploreEvent.Fixed::stopId).toList(),
                "only the resume that took is fixed");
        assertEquals(3, game.taken.stream().filter("A.b→B"::equals).count());
    }

    @Test
    void staleResumeDoesNotHoldParking() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());
        game.onIdle = () -> {
            if (game.nowMs % 60_000 == 0) journal().command("resume crash-99");
        };

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        assertEquals(1, events(ExploreEvent.Parked.class).size(), "parked on time despite the stale resumes");
        assertTrue(events(ExploreEvent.Reloaded.class).stream().allMatch(ExploreEvent.Reloaded::refused),
                "a stale resume reloads nothing, and says so");
    }

    @Test
    void aFaultIsHeldPastParkAfter() {
        Game game = new Game();
        game.dev = true;
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(35).toMillis()) journal().command("stop");
        };
        ExploreLoop loop = loop(map(), game, same(map()), Duration.ofHours(2));

        ExploreLoop.Paused paused = loop.fault(StopKind.WRONG_ARRIVAL, "A", "A.b→B", "did-not-take",
                "what the tree settled did not take", "NewWay A.b → B");

        assertEquals(ExploreLoop.Paused.END, paused, "held until the run was stopped, 35 min in");
        Stop held = stops().getFirst().stop();
        assertEquals(StopKind.FAULT, held.kind());
        assertEquals(List.of(new ExploreEvent.Faulted(held.id(), "did-not-take", "what the tree settled did not take",
                "NewWay A.b → B", "9.9.9-SNAPSHOT", 0)), events(ExploreEvent.Faulted.class));
        assertTrue(events(ExploreEvent.Parked.class).isEmpty(), "never parked, 3 times its parking time");
        assertTrue(journal().queue().isEmpty());
    }

    @Test
    void aFaultIsAPlainStopOutOfDevMode() {
        Game game = new Game();
        ExploreLoop loop = loop(map(), game, same(map()), Duration.ofHours(2));

        ExploreLoop.Paused paused = loop.fault(StopKind.WRONG_ARRIVAL, "A", "A.b→B", "did-not-take",
                "what the tree settled did not take", "NewWay A.b → B");

        assertEquals(ExploreLoop.Paused.PARKED, paused);
        assertEquals(StopKind.WRONG_ARRIVAL, stops().getFirst().stop().kind());
        assertTrue(events(ExploreEvent.Faulted.class).isEmpty(), "a bot's author is never told to edit the SDK");
    }

    @Test
    void aCommandFaultTurnsTheOpenStopIntoAFault() {
        Game game = new Game();
        game.dev = true;
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());
        int[] reloads = {0};
        ExploreLoop.Reload reload = () -> {
            reloads[0]++;
            return new ExploreLoop.Reload.Reloaded(2, map(), null, List.of());
        };
        game.onIdle = () -> {
            if (game.nowMs == Duration.ofMinutes(1).toMillis()) {
                journal().command("fault " + openStop() + " judged a toggle as navigation");
            }
            if (game.nowMs == Duration.ofMinutes(20).toMillis()) journal().command("resume " + openStop());
        };

        loop(map(), game, reload, Duration.ofMinutes(40)).run();

        String id = stops().getFirst().stop().id();
        assertEquals(1, stops().size(), stops().toString());
        assertEquals(List.of(new ExploreEvent.Faulted(id, "claude", "judged a toggle as navigation", "",
                "9.9.9-SNAPSHOT", Duration.ofMinutes(1).toMillis())), events(ExploreEvent.Faulted.class));
        assertTrue(events(ExploreEvent.Parked.class).isEmpty(), "held, not parked");
        assertEquals(List.of(ExploreLoop.FAULT_RESUMES), events(ExploreEvent.Reloaded.class).getFirst().errors());
        assertEquals(0, reloads[0], "a fault resumes through reload_sdk, not by reloading the bot");
        assertFalse(game.taken.contains("A.d→D"), "the run held, it went nowhere else");
    }

    @Test
    void aFaultHoldsWhileAPlainStopStaysParked() {
        Game game = new Game();
        game.dev = true;
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());
        boolean[] sent = {false};
        game.onIdle = () -> {
            if (!sent[0] && !stops().isEmpty() && stops().getLast().stop().kind() == StopKind.STUCK) {
                sent[0] = true;
                journal().command("fault " + openStop() + " stuck while inputs were left");
            }
        };

        loop(map(), game, same(map()), Duration.ofMinutes(90)).run();

        String wrong = stops().getFirst().stop().id();
        String stuck = stops().getLast().stop().id();
        assertTrue(sent[0], stops().toString());
        assertEquals(List.of(wrong), events(ExploreEvent.Parked.class).stream().map(ExploreEvent.Parked::stopId).toList());
        assertEquals(List.of(stuck), events(ExploreEvent.Faulted.class).stream().map(ExploreEvent.Faulted::stopId).toList());
        assertEquals(List.of(wrong), journal().queue().stream().map(Stop::id).toList(), "the plain stop stays queued");
    }

    /** A game on a page no screen matches, which the run settles; resumed once with {@code skipped}, stopped at 40 min. */
    private Game settledPage(boolean dev, List<String> skipped) {
        Game game = new Game();
        game.dev = dev;
        game.at = null;
        game.state = Effect.State.of(page(web("heading", "Pricing plans", Set.of())), "/pricing");
        boolean[] sent = {false};
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(40).toMillis()) journal().command("stop");
            else if (!sent[0] && !events(ExploreEvent.Settled.class).isEmpty()) {
                sent[0] = true;
                journal().command(Journal.Command.resume(openStop(), skipped));
            }
        };
        return game;
    }

    @Test
    void aSkippedAnswerIsATypedStopNotAFault() {
        Game game = settledPage(true, List.of("x: no screen GONE in the map."));

        loop(map(), game, same(map()), Duration.ofHours(2)).run();

        String settled = stops().getFirst().stop().id();
        Stop unwritten = stops().get(1).stop();
        assertEquals(StopKind.UNWRITTEN, unwritten.kind(), "the bot's to fix, even in dev mode");
        assertTrue(unwritten.missing().contains(settled) && unwritten.missing().contains("x: no screen GONE in the map."),
                unwritten.missing());
        assertTrue(unwritten.missing().contains("settled: [NewScreen"), unwritten.missing());
        assertTrue(events(ExploreEvent.Faulted.class).isEmpty(), events(ExploreEvent.Faulted.class).toString());
    }

    @Test
    void whatWasLeftOutOutlivesARefusedReload() {
        Game game = settledPage(true, List.of("x: no screen GONE in the map."));
        Runnable first = game.onIdle;
        game.onIdle = () -> {
            first.run();
            // The bot did not compile: Claude fixes it and resumes with a plain resume, which says nothing left out.
            List<ExploreEvent.Reloaded> reloads = events(ExploreEvent.Reloaded.class);
            if (reloads.size() == 1 && reloads.getFirst().refused() && !journal().commandWaiting()) {
                journal().command("resume " + reloads.getFirst().stopId());
            }
        };
        int[] reloads = {0};
        ExploreLoop.Reload reload = () -> ++reloads[0] == 1
                ? new ExploreLoop.Reload.Reloaded(1, map(), null, List.of("Shop.java:3: ';' expected"))
                : new ExploreLoop.Reload.Reloaded(2, map(), null, List.of());

        loop(map(), game, reload, Duration.ofHours(2)).run();

        assertTrue(stops().stream().map(ExploreEvent.Stopped::stop).anyMatch(stop -> stop.kind() == StopKind.UNWRITTEN
                && stop.missing().contains("x: no screen GONE in the map.")), stops().toString());
    }

    @Test
    void aSkippedAnswerIsSaidOutOfDevMode() {
        Game game = settledPage(false, List.of("x: no screen GONE in the map."));

        loop(map(), game, same(map()), Duration.ofHours(2)).run();

        Stop said = stops().get(1).stop();
        assertEquals(StopKind.UNWRITTEN, said.kind());
        assertTrue(said.missing().contains("x: no screen GONE in the map."), said.missing());
        assertFalse(JournalView.of(journal()).settled(said), "Claude's to write, not the plugin side's");
        assertTrue(events(ExploreEvent.Faulted.class).isEmpty());
    }

    @Test
    void aSettleThatDidNotTakeIsAFault() {
        // Resumed with nothing skipped, and the page is still none of the map's: what was written did not take.
        Game game = settledPage(true, List.of());

        loop(map(), game, same(map()), Duration.ofHours(2)).run();

        ExploreEvent.Faulted fault = events(ExploreEvent.Faulted.class).getFirst();
        assertEquals("did-not-take", fault.source());
        assertTrue(fault.evidence().startsWith("settled again: [NewScreen"), fault.evidence());
        assertEquals(StopKind.FAULT, stops().get(1).stop().kind());
        assertEquals(stops().getFirst().stop().place(), stops().get(1).stop().place());
    }

    @Test
    void anExceptionInTheLoopIsAFault() {
        Game game = new Game();
        game.dev = true;
        game.lookThrows = new IllegalStateException("no look");
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(30).toMillis()) journal().command("stop");
        };

        loop(map(), game, same(map()), Duration.ofHours(2)).run();

        Stop stop = stops().getFirst().stop();
        assertEquals(StopKind.FAULT, stop.kind());
        assertEquals("A:IllegalStateException", stop.place());
        ExploreEvent.Faulted fault = events(ExploreEvent.Faulted.class).getFirst();
        assertEquals("exception", fault.source());
        assertTrue(fault.evidence().startsWith("java.lang.IllegalStateException: no look\n  at "), fault.evidence());
    }

    @Test
    void theBotsExceptionStaysACrash() {
        Game game = new Game();
        game.dev = true;
        IllegalStateException thrown = new IllegalStateException("the bot's own");
        thrown.setStackTrace(new StackTraceElement[]{new StackTraceElement("com.example.bot.Farm", "run", "Farm.java", 3),
                new StackTraceElement("com.botmaker.sdk.internal.explore.LivePlay", "look", "LivePlay.java", 9)});
        game.lookThrows = thrown;
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(5).toMillis()) journal().command("stop");
        };

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        assertEquals(StopKind.CRASH, stops().getFirst().stop().kind());
        assertTrue(events(ExploreEvent.Faulted.class).isEmpty());
    }

    @Test
    void aHeldFaultWritesItsMemory() {
        Game game = new Game();
        game.dev = true;
        game.lookThrows = new IllegalStateException("no look");
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(30).toMillis()) journal().command("stop");
        };

        loop(map(), game, same(map()), Duration.ofHours(2)).run();

        ResumeFile file = ResumeFile.read(journal(), why -> {}).orElseThrow();
        assertEquals(stops().getFirst().stop(), file.fault());
        assertEquals(StopKind.CRASH, file.replays(), "what its place raised before it was a fault");
        assertEquals("exception", file.source());
        assertEquals(Set.of("A"), file.seen());
        assertTrue(JournalView.of(journal()).heldWhenLeft(file.fault().id()), "the run ended holding it");
    }

    /**
     * A run that held {@code fault-3} on {@code place} of {@code screen}, its memory written with {@code tried}, then
     * ended as the SDK's reload ends it.
     */
    private void heldAndLeft(StopKind replays, String screen, String place, Set<String> tried) {
        heldAndLeft(replays, screen, place, tried, ExploreBudget.NONE);
    }

    /** {@link #heldAndLeft(StopKind, String, String, Set)} by a run started with {@code budget}. */
    private void heldAndLeft(StopKind replays, String screen, String place, Set<String> tried, ExploreBudget budget) {
        Journal journal = journal();
        Stop fault = new Stop("fault-3", StopKind.FAULT, screen, place, "", List.of(), List.of(), Map.of(), List.of(),
                "judged wrong", 1);
        journal.append(new ExploreEvent.Began(true, 0));
        journal.append(new ExploreEvent.Stopped(fault, false, 1));
        journal.append(new ExploreEvent.Faulted("fault-3", "claude", "judged wrong", "", "9.9.9-SNAPSHOT", 1));
        journal.append(new ExploreEvent.Ended(ExploreLoop.End.STOPPED, 2));
        new ResumeFile(fault, replays, "claude", Set.of("A"), tried, Set.of(), Set.of(), budget, null).write(journal);
    }

    /**
     * A restart (a dev reload, a crash) hands the entry no arguments: it takes the budget the run kept, as
     * {@code KeepExploring.main} does, and keeps it again in the memory it writes.
     */
    @Test
    void aRestartedRunReadsItsArgumentsFromTheResumeFile() {
        heldAndLeft(StopKind.WRONG_ARRIVAL, "A", "A.d→D", Set.of("A.d→D"), new ExploreBudget(10, null));
        Game game = new Game();
        game.dev = true;
        Optional<ResumeFile> pending = ResumeFile.pending(journal(), why -> {});
        ExploreBudget budget = ResumeFile.budgetOf(ExploreBudget.NONE, pending);
        // A way behind a fact that says no keeps the run waiting (a built map would end it done), so its budget ends it.
        Gold gold = new Gold();
        gold.read = 0;
        ScreenGraph waiting = mapBehind(gold);

        ExploreLoop.End end = new ExploreLoop(waiting, game, same(waiting), Guards.with(List.of()), journal(),
                ExploreLoop.Settings.DEFAULT.within(budget.time()).upTo(budget.screens()), () -> game.nowMs)
                .startedWith(budget, null).resuming(pending).run();

        assertEquals(ExploreLoop.End.BUDGET, end);
        assertTrue(game.nowMs >= Duration.ofMinutes(10).toMillis() && game.nowMs < Duration.ofMinutes(12).toMillis(),
                "ran its 10 minutes: " + game.nowMs);
    }

    @Test
    void aHeldFaultKeepsTheRunsArguments() {
        Game game = new Game();
        game.dev = true;
        game.lookThrows = new IllegalStateException("no look");
        ExploreBudget budget = new ExploreBudget(30, null);

        loop(map(), game, same(map()), Duration.ofMinutes(30)).startedWith(budget, null).run();

        assertEquals(budget, ResumeFile.read(journal(), why -> {}).orElseThrow().budget());
    }

    private ExploreLoop resumed(Game game, Duration within) {
        return loop(map(), game, same(map()), within).resuming(ResumeFile.pending(journal(), why -> {}));
    }

    private boolean resumeFileLies() {
        return Files.exists(folder.resolve(ResumeFile.FILE));
    }

    @Test
    void aRunWithAResumeFileRestoresWhatItTried() {
        heldAndLeft(StopKind.WRONG_ARRIVAL, "A", "A.d→D", Set.of("A.b→B", "A.d→D"));
        Game game = new Game();
        game.dev = true;

        resumed(game, Duration.ofMinutes(12)).run();

        assertFalse(game.taken.contains("A.b→B"), "tried before the restart: " + game.taken);
        assertEquals("A.d→D", game.taken.getFirst(), "the fault's place, pressed again");
        assertEquals(1, events(ExploreEvent.Began.class).size(), "the same run");
        assertEquals("fault-3", events(ExploreEvent.Restarted.class).getFirst().faultId());
    }

    @Test
    void aReplayThatPassesSaysFixed() {
        heldAndLeft(StopKind.WRONG_ARRIVAL, "A", "A.d→D", Set.of("A.d→D"));
        Game game = new Game();
        game.dev = true;

        resumed(game, Duration.ofMinutes(12)).run();

        ExploreEvent.Fixed fixed = events(ExploreEvent.Fixed.class).getFirst();
        assertEquals("fault-3", fixed.stopId());
        assertEquals("replayed", fixed.why());
        assertFalse(resumeFileLies());
        assertEquals(1, stops().size(), "nothing stopped again");
    }

    @Test
    void aResumeReplaysTheFaultsPlace() {
        heldAndLeft(StopKind.WRONG_ARRIVAL, "A", "A.d→D", Set.of("A.d→D"));
        Game game = new Game();
        game.dev = true;
        // The fix did not take: d still arrives elsewhere.
        game.arrival = edge -> edge.to().equals("D") ? new Took(Arrival.ELSEWHERE, "B") : new Took(Arrival.AT_TARGET, edge.to());
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(30).toMillis()) journal().command("stop");
        };

        resumed(game, Duration.ofHours(2)).run();

        ExploreEvent.Stopped again = stops().getLast();
        assertEquals(StopKind.FAULT, again.stop().kind(), "held again, never parked");
        assertEquals("A.d→D", again.stop().place());
        assertTrue(again.again());
        assertTrue(again.stop().missing().startsWith("the fix did not take: "), again.stop().missing());
        assertTrue(events(ExploreEvent.Parked.class).isEmpty());
        assertEquals("claude", events(ExploreEvent.Faulted.class).getLast().source());
        assertEquals(again.stop().id(), ResumeFile.read(journal(), why -> {}).orElseThrow().fault().id(),
                "its memory written again");
    }

    @Test
    void aReplayOutlivesAnotherStopOnTheWay() {
        heldAndLeft(StopKind.WRONG_ARRIVAL, "A", "A.d→D", Set.of("A.d→D"));
        Game game = new Game();
        game.dev = true;
        // b, pressed first, stops elsewhere: a stop of its own, parked, and the replay still waits for d.
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "A") : new Took(Arrival.AT_TARGET, edge.to());

        resumed(game, Duration.ofMinutes(14)).run();

        assertEquals("A.b→B", stops().get(1).stop().place());
        assertEquals("replayed", events(ExploreEvent.Fixed.class).stream().filter(f -> f.stopId().equals("fault-3"))
                .findFirst().orElseThrow().why());
        assertFalse(resumeFileLies());
    }

    @Test
    void aCrashPassesOnlyWhenItsInputIsPressedAgain() {
        // d threw before the restart, and was tried: the replay presses it again, and only that press passes it.
        heldAndLeft(StopKind.CRASH, "A", "A.d:IllegalStateException", Set.of("A.b→B", "A.d→D"));
        Game game = new Game();
        game.dev = true;

        resumed(game, Duration.ofMinutes(12)).run();

        assertTrue(game.taken.contains("A.d→D"), "pressed again: " + game.taken);
        assertFalse(game.taken.contains("A.b→B"), "tried before the restart: " + game.taken);
        List<ExploreEvent> events = journal().events();
        int pressedAt = -1;
        int fixedAt = -1;
        for (int i = 0; i < events.size(); i++) {
            if (pressedAt < 0 && events.get(i) instanceof ExploreEvent.Tried(String s, String by, ExploreLoop.Mode m,
                    Arrival a, long at) && by.equals("d")) pressedAt = i;
            if (events.get(i) instanceof ExploreEvent.Fixed fixed && fixed.stopId().equals("fault-3")) fixedAt = i;
        }
        assertTrue(pressedAt >= 0 && fixedAt > pressedAt, "fixed after d's press: " + events);
        assertEquals("replayed", ((ExploreEvent.Fixed) events.get(fixedAt)).why());
        assertFalse(resumeFileLies());
    }

    @Test
    void aCrashReplayWhosePressIsGoneSaysSo() {
        // The fix removed A's x: it can never be pressed again, so the replay is closed for that, not passed.
        heldAndLeft(StopKind.CRASH, "A", "A.x:IllegalStateException", Set.of());
        Game game = new Game();
        game.dev = true;

        resumed(game, Duration.ofMinutes(12)).run();

        ExploreEvent.Fixed fixed = events(ExploreEvent.Fixed.class).getFirst();
        assertEquals("fault-3", fixed.stopId());
        assertTrue(fixed.why().contains("A.x is no longer in the map"), fixed.why());
        assertFalse(resumeFileLies());
    }

    @Test
    void aCrashPlaceNamesItsEdge() {
        Game game = new Game();
        game.arrival = edge -> {
            if (edge.to().equals("D")) throw new IllegalStateException("the bot's own");
            return new Took(Arrival.AT_TARGET, edge.to());
        };
        game.onIdle = () -> {
            if (game.nowMs >= Duration.ofMinutes(5).toMillis()) journal().command("stop");
        };

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        Stop crash = stops().stream().map(ExploreEvent.Stopped::stop).filter(s -> s.kind() == StopKind.CRASH)
                .findFirst().orElseThrow();
        assertEquals("A.d:IllegalStateException", crash.place());
    }

    @Test
    void aReplayDroppedByAnotherStopIsRecorded() {
        // A page b led to was unknown; on the rebuilt SDK b arrives elsewhere first, which sets the place aside.
        heldAndLeft(StopKind.UNKNOWN_SCREEN, "A", "A.b→B", Set.of("A.b→B"));
        Game game = new Game();
        game.dev = true;
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());

        resumed(game, Duration.ofMinutes(14)).run();

        assertEquals(StopKind.WRONG_ARRIVAL, stops().get(1).stop().kind());
        List<ExploreEvent.ReplayLost> lost = events(ExploreEvent.ReplayLost.class);
        assertEquals(1, lost.size(), "said once: " + lost);
        assertEquals("fault-3", lost.getFirst().faultId());
        assertTrue(events(ExploreEvent.Fixed.class).stream().noneMatch(f -> f.stopId().equals("fault-3")));
        assertFalse(resumeFileLies(), "its memory deleted");
        assertFalse(JournalView.of(journal()).heldWhenLeft("fault-3"));
    }

    @Test
    void aReplayedFaultIsLetGoByIgnoreOnly() {
        // On no screen and a place never met again: the replay waits as long as the run.
        heldAndLeft(StopKind.WRONG_ARRIVAL, null, "Z.x→A", Set.of());
        Game game = new Game();
        game.dev = true;
        boolean[] sent = {false, false};
        game.onIdle = () -> {
            if (!sent[0]) {
                sent[0] = true;
                journal().command("resume fault-3");
            } else if (!sent[1] && !journal().commandWaiting()) {
                sent[1] = true;
                journal().command("resume fault-3 ignore");
            }
        };

        resumed(game, Duration.ofMinutes(12)).run();

        assertEquals(List.of(ExploreLoop.FAULT_RESUMES), events(ExploreEvent.Reloaded.class).getFirst().errors());
        assertEquals("ignored", events(ExploreEvent.Fixed.class).getFirst().why());
        assertFalse(resumeFileLies());
    }

    @Test
    void aResumeToAScreenGoneExploresOn() {
        heldAndLeft(StopKind.WRONG_ARRIVAL, "GONE", "GONE.x→A", Set.of());
        Game game = new Game();
        game.dev = true;

        resumed(game, Duration.ofMinutes(12)).run();

        ExploreEvent.Fixed fixed = events(ExploreEvent.Fixed.class).getFirst();
        assertEquals("fault-3", fixed.stopId());
        assertTrue(fixed.why().contains("GONE is no longer in the map"), fixed.why());
        assertFalse(resumeFileLies());
        assertFalse(game.taken.isEmpty(), "it explores on");
    }

    @Test
    void anEndedRunsResumeFileIsIgnored() {
        heldAndLeft(StopKind.WRONG_ARRIVAL, "A", "A.d→D", Set.of("A.b→B", "A.d→D"));
        // Let go after all: the file lying there is stale.
        journal().append(new ExploreEvent.Fixed("fault-3", "ignored", 3));
        Game game = new Game();
        game.dev = true;

        resumed(game, Duration.ofMinutes(12)).run();

        assertFalse(resumeFileLies());
        assertTrue(events(ExploreEvent.Restarted.class).isEmpty());
        assertEquals(2, events(ExploreEvent.Began.class).size(), "a fresh run");
        assertTrue(game.taken.contains("A.b→B"), "nothing restored: " + game.taken);
    }

    @Test
    void aResumeIgnoreDropsTheMemory() {
        Game game = new Game();
        game.dev = true;
        game.lookThrows = new IllegalStateException("no look");
        boolean[] sent = {false};
        game.onIdle = () -> {
            if (!sent[0] && resumeFileLies()) {
                sent[0] = true;
                game.lookThrows = null;
                journal().command("resume " + openStop() + " ignore");
            }
        };

        loop(map(), game, same(map()), Duration.ofMinutes(12)).run();

        assertTrue(sent[0]);
        assertFalse(resumeFileLies(), "no restart goes on from a fault let go");
    }

    @Test
    void aRunThatThrowsSaysItEnded() {
        Game game = new Game();
        // What the loop does not take for a stop of its own goes on up: an error of the JVM here.
        game.arrival = edge -> {
            throw new StackOverflowError("deep");
        };

        try {
            loop(map(), game, same(map()), Duration.ofMinutes(5)).run();
        } catch (StackOverflowError expected) {
            // gone up, as it should
        }

        assertTrue(journal().events().getLast() instanceof ExploreEvent.Ended(ExploreLoop.End end, long at)
                && end == ExploreLoop.End.BROKE, journal().events().toString());
    }

    @Test
    void parkedStopResumedWhileRunning() {
        Game game = new Game();
        game.arrival = edge -> edge.to().equals("B") ? new Took(Arrival.ELSEWHERE, "D") : new Took(Arrival.AT_TARGET, edge.to());
        ExploreLoop.Reload reload = () -> {
            game.arrival = edge -> new Took(Arrival.AT_TARGET, edge.to());
            return new ExploreLoop.Reload.Reloaded(2, map(), null, List.of());
        };
        boolean[] sent = {false};
        game.onIdle = () -> {
            if (!sent[0] && !journal().queue().isEmpty()) {
                sent[0] = true;
                journal().command("resume " + journal().queue().getFirst().id());
            }
        };

        loop(map(), game, reload, Duration.ofMinutes(14)).run();

        String parked = events(ExploreEvent.Parked.class).getFirst().stopId();
        List<ExploreEvent.Reloaded> reloaded = events(ExploreEvent.Reloaded.class);
        assertEquals(1, reloaded.size(), reloaded.toString());
        assertFalse(reloaded.getFirst().refused());
        assertTrue(events(ExploreEvent.Fixed.class).stream().anyMatch(f -> f.stopId().equals(parked)));
        assertTrue(journal().queue().isEmpty(), "it left the queue");
        assertEquals(2, game.taken.stream().filter("A.b→B"::equals).count(), "its place tried again");
        assertTrue(journal().events().getFirst() instanceof ExploreEvent.Began);
        // Its place tried again and the map built: nothing is left, so it ends done, not at its budget.
        assertTrue(journal().events().getLast() instanceof ExploreEvent.Ended(ExploreLoop.End end, long at)
                && end == ExploreLoop.End.DONE);
    }

    @Test
    void newerJournalReadsAsUnknown() throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(Journal.QUEUE), "[{\"id\":\"later-1\",\"kind\":\"LATER_KIND\",\"place\":\"A\","
                + "\"texts\":[],\"elements\":[],\"facts\":{},\"lastPresses\":[],\"added\":1}]");
        Files.writeString(folder.resolve(Journal.EVENTS), "{\"type\":\"tried\",\"screen\":\"A\",\"by\":\"b\","
                + "\"mode\":\"LATER_MODE\",\"arrival\":\"LATER_ARRIVAL\",\"at\":1}\n");

        assertEquals(StopKind.UNKNOWN, journal().queue().getFirst().kind());
        ExploreEvent.Tried tried = (ExploreEvent.Tried) journal().events().getFirst();
        assertEquals(ExploreLoop.Mode.UNKNOWN, tried.mode());
        assertEquals(null, tried.arrival());
    }

    @Test
    void journalReadsBackAfterACutLine() throws IOException {
        Journal journal = journal();
        journal.append(new ExploreEvent.NewScreen("A", null, null, 1));
        Stop stop = new Stop("guard-1", StopKind.GUARD, "A", "A", "", List.of(), List.of(), Map.of(), List.of(),
                "x", 2);
        journal.append(new ExploreEvent.Stopped(stop, false, 2));
        journal.queue(List.of(stop));
        // A JVM killed mid-write: half a line, cut inside a character.
        byte[] cut = "{\"type\":\"tried\",\"screen\":\"é".getBytes(StandardCharsets.UTF_8);
        Files.write(folder.resolve(Journal.EVENTS), Arrays.copyOf(cut, cut.length - 1), StandardOpenOption.APPEND);

        List<ExploreEvent> events = new Journal(folder).events();

        assertEquals(2, events.size(), events.toString());
        assertEquals(stop, ((ExploreEvent.Stopped) events.get(1)).stop());
        assertEquals(List.of(stop), new Journal(folder).queue());
        journal.command("resume guard-1 ignore");
        assertEquals(Optional.of(new Journal.Command.Resume("guard-1", true)), journal.takeCommand());
        assertEquals(Optional.empty(), journal.takeCommand(), "read once");
    }

    /** What a run settled reads back whole, each kind of answer, for the plugin side in another process. */
    @Test
    void settledAnswersReadBack() {
        Locator buy = new Locator(Role.BUTTON, "Buy now", null, null, null);
        ExploreEvent.Settled settled = new ExploreEvent.Settled("unknown_screen-1", List.of(
                new TreeAnswer.NewScreen("Pricing", "/pricing", List.of("/pricing"), List.of(buy), null),
                new TreeAnswer.NewRoute("CASE", "/case/{slug}", List.of("/case/a", "/case/b")),
                new TreeAnswer.NewInput("Pricing", "buyNow", buy, "a buy"),
                new TreeAnswer.NewWay("A", "b", "Pricing"),
                new TreeAnswer.InputNote("A", "toggle", "Does nothing a visitor sees"),
                new TreeAnswer.NewTarget("web:https://site.test/")), 7);
        journal().append(settled);

        assertEquals(List.of(settled), journal().events());
    }
}
