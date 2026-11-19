// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Role;
import com.botmaker.sdk.api.screens.Setup;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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
        long nowMs;
        Foreground front = Foreground.UNKNOWN;
        Function<Edge, Took> arrival = edge -> new Took(Arrival.AT_TARGET, edge.to());
        Runnable onIdle = () -> {};
        final List<String> taken = new ArrayList<>();
        /** The screen's tree, null for a game with none. */
        Effect.State state;
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
            return Optional.ofNullable(at);
        }

        /** What {@link #look} throws; null: it looks. */
        RuntimeException lookThrows;

        @Override
        public ExploreLoop.Look look(String screen) {
            if (lookThrows != null) throw lookThrows;
            return ExploreLoop.Look.NOTHING;
        }

        @Override
        public Guards.Around around(Edge edge) {
            return new Guards.Around(List.of(), front, List.of());
        }

        @Override
        public Foreground front() {
            return front;
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
        public boolean planRound() {
            return false;
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

        @Override
        public Took take(Edge edge, long withinMs) {
            taken.add(ExploreLoop.key(edge));
            Took took = arrival.apply(edge);
            at = took.at();
            return took;
        }

        @Override
        public boolean recover() {
            at = "A";
            return true;
        }

        @Override
        public void restart() {
            at = "A";
        }
    }

    private Journal journal() {
        return new Journal(folder);
    }

    private ExploreLoop loop(ScreenGraph map, Game game, ExploreLoop.Reload reload, Duration within) {
        return new ExploreLoop(map, game, reload, Guards.with(List.of()), journal(),
                ExploreLoop.Settings.DEFAULT.within(within), () -> game.nowMs);
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

    /** A tried input a note says fails is pressed again, once; one a note says does nothing is not. */
    @Test
    void anInputThatFailedIsPressedAgainOnce() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        Map<String, Input> inputs = new LinkedHashMap<>();
        inputs.put("toggle", Button.of(new Locator(Role.BUTTON, "Toggle", null, null, null).element()).free().tried());
        inputs.put("docs", Button.of(new Locator(Role.LINK, "Docs", null, null, null).element()).free().tried());
        screens.put("A", new MapScreen(Setup.named("A").start().notes("toggle: Fails: blocked write: POST /monitoring\n"
                + "docs: Does nothing a visitor sees"), inputs));
        ScreenGraph map = ScreenGraph.of(screens);
        Game game = new Game();
        game.state = Effect.State.of(page(web("button", "Toggle", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE)),
                web("link", "Docs", EnumSet.of(Trait.ENABLED, Trait.CLICKABLE))), "/");

        loop(map, game, same(map), Duration.ofMinutes(12)).run();

        assertEquals(List.of("A.toggle"), game.pressed);
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

    @Test
    void newScreenIsAnEventNotAStop() {
        Game game = new Game();

        assertEquals(ExploreLoop.End.BUDGET, loop(map(), game, same(map()), Duration.ofMinutes(5)).run());

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

        assertEquals(1, stops().size());
        Stop batch = stops().getFirst().stop();
        assertEquals(StopKind.NO_VERDICT, batch.kind());
        assertTrue(batch.missing().contains("A.b") && batch.missing().contains("A.d"), batch.missing());
        assertTrue(game.taken.isEmpty(), "nothing pressed before every input has a verdict");
        assertTrue(events(ExploreEvent.Parked.class).isEmpty(), "never parked");
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
            if (game.nowMs >= Duration.ofMinutes(30).toMillis()) game.front = new Foreground("com.game", "com.game");
        };

        loop(map(), game, same(map()), Duration.ofMinutes(35)).run();

        Stop guard = stops().getFirst().stop();
        assertEquals(StopKind.GUARD, guard.kind());
        assertTrue(guard.missing().contains("com.android.chrome"), guard.missing());
        assertTrue(events(ExploreEvent.Parked.class).isEmpty(), "never parked, 30 min in front");
        assertEquals(guard.id(), events(ExploreEvent.Fixed.class).getFirst().stopId());
        assertTrue(game.taken.contains("A.b→B"), "pressed once the game was back");
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
    void aSkippedAnswerIsAFaultInDevMode() {
        Game game = settledPage(true, List.of("x: no screen GONE in the map."));

        loop(map(), game, same(map()), Duration.ofHours(2)).run();

        String settled = stops().getFirst().stop().id();
        ExploreEvent.Faulted fault = events(ExploreEvent.Faulted.class).getFirst();
        assertEquals("skipped", fault.source());
        assertTrue(fault.why().contains(settled) && fault.why().contains("x: no screen GONE in the map."), fault.why());
        assertTrue(fault.evidence().contains("settled: [NewScreen"), fault.evidence());
        assertEquals(StopKind.FAULT, stops().get(1).stop().kind());
        assertTrue(events(ExploreEvent.Parked.class).isEmpty(), "held");
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

        assertEquals("skipped", events(ExploreEvent.Faulted.class).getFirst().source());
    }

    @Test
    void aSkippedAnswerIsSaidOutOfDevMode() {
        Game game = settledPage(false, List.of("x: no screen GONE in the map."));

        loop(map(), game, same(map()), Duration.ofHours(2)).run();

        Stop said = stops().get(1).stop();
        assertEquals(StopKind.UNKNOWN_SCREEN, said.kind());
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
        assertTrue(journal().events().getLast() instanceof ExploreEvent.Ended(ExploreLoop.End end, long at)
                && end == ExploreLoop.End.BUDGET);
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
