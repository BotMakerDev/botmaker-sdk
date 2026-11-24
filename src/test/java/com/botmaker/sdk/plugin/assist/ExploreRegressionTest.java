// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreGoal;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.FindingKind;
import com.botmaker.sdk.internal.explore.Foreground;
import com.botmaker.sdk.internal.explore.Guards;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.PressTag;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopRole;
import com.botmaker.sdk.internal.explore.WitnessCheck;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.MapWalk.Arrival;
import com.botmaker.sdk.internal.screens.MapWalk.Took;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenGraph.Edge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
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

/**
 * Goal runs replayed offline (plan 2026-11-24-explore-agents, phase 9): each {@code explore-goal-runs/<goal>/run.json}
 * holds a game's screens with the text each frame reads, which screens the map can tell at the start, and the Reader's
 * and the Evaluator's recorded answers. The real {@link ExploreLoop} plays it against the real {@link Coordinator} and
 * {@link CoordinatorHost}, one virtual clock, the crew stepped while the game is pressed or idles; the workers are
 * fakes that answer as recorded. Each run ends done with its witness within its press ceiling; the fault cases are the
 * plan's Review Focus: a hang, a silent Coordinator, a restart with edits queued, done without a witness, a destructive
 * press.
 */
@Timeout(60)
class ExploreRegressionTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Coordinator.Crew CREW = new Coordinator.Crew(
            Set.of(StopRole.SETTLER, StopRole.CARTOGRAPHER, StopRole.EVALUATOR), Set.of());

    @TempDir
    Path folder;

    @ParameterizedTest
    @ValueSource(strings = {"google-sign-in", "hotspot", "dark-theme", "stuck-home"})
    void eachGoalRunEndsDoneWithItsWitnessWithinItsCeiling(String goal) throws IOException {
        Replay replay = new Replay(load(goal), folder);

        assertEquals(ExploreLoop.End.DONE, replay.run(s -> s), replay.journal.events().toString());

        List<ExploreEvent.Witnessed> witnessed = replay.events(ExploreEvent.Witnessed.class);
        assertEquals(1, witnessed.size(), witnessed.toString());
        assertEquals(WitnessCheck.fromId(replay.run.witness()), witnessed.getFirst().check());
        assertTrue(replay.game.taken.size() <= replay.run.pressCeiling(),
                replay.game.taken + " is over " + replay.run.pressCeiling() + " presses");
        assertFalse(replay.applied.isEmpty(), "the Reader drew the screen the map could not tell");
        assertEquals(1, replay.events(ExploreEvent.Summary.class).size(), "the run's summary is written at its end");
    }

    /**
     * Device drive 2026-11-24: the run starts on a screen whose every press it knows was tried, the way on a row the
     * map lacks. Its STUCK goes to the Reader, which adds the row; the run presses it and reaches the goal.
     */
    @Test
    void aGoalRunStuckAtItsStartIsTheReadersWhichAddsThePressOn() throws IOException {
        Replay replay = new Replay(load("stuck-home"), folder);

        assertEquals(ExploreLoop.End.DONE, replay.run(s -> s), replay.journal.events().toString());

        assertTrue(replay.reads.getFirst().startsWith("stuck-"), replay.reads.toString());
        assertEquals(List.of("Home.accounts"), replay.applied);
        assertEquals(List.of("Home.accounts→Accounts"), replay.game.taken);
    }

    /** A Reader that adds nothing ends its try without resuming: the stuck is the user's, and no Reader tries again. */
    @Test
    void aReaderThatAddsNothingHandsTheStuckToTheUser() throws IOException {
        Run run = load("stuck-home");
        Replay replay = new Replay(run.withReader(Map.of("Home", new ReaderAnswer(null, null, null))), folder);

        replay.run(s -> s);

        List<ExploreEvent.Leased> leases = replay.events(ExploreEvent.Leased.class).stream()
                .filter(l -> l.role() == StopRole.CARTOGRAPHER).toList();
        assertEquals(1, leases.size(), leases.toString());
        JournalView view = JournalView.of(replay.journal);
        Stop stuck = view.stopOf(leases.getFirst().stopId()).orElseThrow();
        assertEquals(StopRole.USER, view.roleOf(stuck));
        assertTrue(replay.game.taken.isEmpty(), replay.game.taken.toString());
    }

    @Test
    void aStarvedBudgetEndsTheRunOnItsBudget() throws IOException {
        Replay replay = new Replay(load("google-sign-in"), folder);

        assertEquals(ExploreLoop.End.BUDGET, replay.run(s -> s.spending(2, 0)));
        assertEquals(2, replay.game.taken.size(), replay.game.taken.toString());
        assertTrue(replay.events(ExploreEvent.Witnessed.class).isEmpty());
    }

    /** A Reader that never answers is taken back when its lease runs out, and its second try finishes the run. */
    @Test
    void aHungReaderIsTakenBackAndTriedAgain() throws IOException {
        Replay replay = new Replay(load("google-sign-in"), folder);
        replay.hangs = 1;

        assertEquals(ExploreLoop.End.DONE, replay.run(s -> s));

        String stop = replay.reads.getFirst();
        assertTrue(replay.events(ExploreEvent.Released.class).stream()
                .anyMatch(r -> r.stopId().equals(stop) && r.attempt() == 1 && !r.ok() && r.why().equals("timeout")));
        assertTrue(replay.events(ExploreEvent.Leased.class).stream()
                .anyMatch(l -> l.stopId().equals(stop) && l.attempt() == 2 && l.role() == StopRole.CARTOGRAPHER));
    }

    /** A Coordinator that stops beating is caught by the watchdog: the run ends stuck, and says why. */
    @Test
    void aSilentCoordinatorEndsTheRunStuck() throws IOException {
        Replay replay = new Replay(load("google-sign-in"), folder);
        replay.stepsLeft = 1;

        // The watchdog stops the loop by its command, so the loop ends STOPPED; the journal says why: stuck.
        assertEquals(ExploreLoop.End.STOPPED, replay.run(s -> s));

        assertEquals(ExploreLoop.End.STUCK, replay.events(ExploreEvent.Ended.class).getFirst().end());
        assertTrue(replay.events(ExploreEvent.Finding.class).stream()
                .anyMatch(f -> f.kind() == FindingKind.STUCK && f.why().equals("coordinator silent")));
    }

    /**
     * The host dies while it writes the Reader's edits: the one written stays written, the lease runs out, and the
     * Reader's next try queues both again; each edit is carried out once.
     */
    @Test
    void aRestartWithEditsQueuedWritesEachEditOnce() throws IOException {
        Run run = load("google-sign-in");
        Map<String, ReaderAnswer> reader = new LinkedHashMap<>(run.reader());
        ReaderAnswer signIn = reader.get("SignIn");
        reader.put("SignIn", new ReaderAnswer(List.of("SignIn", "Account"), List.of(), signIn.ranks()));
        Replay replay = new Replay(run.withReader(reader), folder);
        replay.crashOnApply = 2;

        assertEquals(ExploreLoop.End.DONE, replay.run(s -> s));

        assertEquals(1, replay.restarts);
        assertEquals(List.of("SignIn", "Account"), replay.applied);
        Map<String, Long> written = new LinkedHashMap<>();
        replay.events(ExploreEvent.EditApplied.class).forEach(a -> written.merge(a.editId(), 1L, Long::sum));
        assertEquals(List.of(1L, 1L), List.copyOf(written.values()), written.toString());
    }

    /** A done the frame does not show is a finding, not the end: the run goes on to its witness. */
    @Test
    void aDoneWithoutAWitnessIsAFindingAndTheRunGoesOn() throws IOException {
        Replay replay = new Replay(load("google-sign-in"), folder);
        replay.wrongFirst = "Signed out";

        assertEquals(ExploreLoop.End.DONE, replay.run(s -> s));

        List<ExploreEvent> events = replay.journal.events();
        int finding = events.stream().filter(e -> e instanceof ExploreEvent.Finding f
                && f.kind() == FindingKind.NO_WITNESS).findFirst().map(events::indexOf).orElse(-1);
        int witnessed = events.stream().filter(ExploreEvent.Witnessed.class::isInstance).findFirst()
                .map(events::indexOf).orElse(-1);
        assertTrue(finding >= 0 && finding < witnessed, events.toString());
        assertEquals(1, replay.events(ExploreEvent.Witnessed.class).size());
    }

    /** The Reader's destructive press is never made, though the map lists it first. */
    @Test
    void aDestructivePressIsNeverMade() throws IOException {
        Replay replay = new Replay(load("dark-theme"), folder);

        assertEquals(ExploreLoop.End.DONE, replay.run(s -> s));

        assertTrue(replay.events(ExploreEvent.Ranked.class).stream()
                .anyMatch(r -> r.press().equals("clearStorage") && r.tag() == PressTag.DESTRUCTIVE));
        assertTrue(replay.game.taken.stream().noneMatch(t -> t.startsWith("Display.clearStorage")),
                replay.game.taken.toString());
    }

    private static Run load(String goal) throws IOException {
        try (InputStream in = ExploreRegressionTest.class.getResourceAsStream("/explore-goal-runs/" + goal + "/run.json")) {
            if (in == null) throw new IOException("no recorded run " + goal);
            return JSON.readValue(in, Run.class);
        }
    }

    /** A recorded goal run. */
    record Run(String goal, String goalScreen, int pressCeiling, String witness, Evaluations evaluations,
               List<Screen> screens, Map<String, ReaderAnswer> reader, Map<String, String> evaluator) {

        Run withReader(Map<String, ReaderAnswer> answers) {
            return new Run(goal, goalScreen, pressCeiling, witness, evaluations, screens, answers, evaluator);
        }

        Screen named(String name) {
            return screens.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
        }

        String start() {
            return screens.stream().filter(Screen::start).map(Screen::name).findFirst().orElseThrow();
        }

        /**
         * The map: every screen with the ways into it, an undrawn one too, so a press can reach it; a press the map
         * lacks only once the Reader added it ({@code added}, {@code Screen.press}).
         */
        ScreenGraph map(Set<String> added) {
            Map<String, MapScreen> map = new LinkedHashMap<>();
            for (Screen shown : screens) {
                Setup setup = Setup.named(shown.name());
                if (shown.start()) setup = setup.start();
                // The first way in is its from, any other one alsoFrom: a second from would replace the first.
                List<Way> into = new ArrayList<>();
                for (Screen from : screens) {
                    for (Map.Entry<String, String> way : from.mapped(added).entrySet()) {
                        if (way.getValue().equals(shown.name())) into.add(Way.of(ref(from.name(), way.getKey())));
                    }
                }
                if (!into.isEmpty()) setup = setup.fromWay(into.getFirst()).alsoFrom(into.subList(1, into.size()));
                Input[] ways = shown.mapped(added).keySet().stream().map(press -> (Input) click(press).free())
                        .toArray(Input[]::new);
                map.put(shown.name(), screen(setup, ways));
            }
            return ScreenGraph.of(map);
        }
    }

    /** When the Coordinator starts an evaluation: its stall count and its safety net. */
    record Evaluations(int k, int every) {}

    /**
     * One screen of the game: whether the map tells it at the start, what its frame reads, where each press leads;
     * {@code unmapped} the presses it shows that the map lacks until the Reader adds them.
     */
    record Screen(String name, boolean start, boolean drawn, List<String> texts, Map<String, String> ways,
                  Map<String, String> unmapped) {
        Screen {
            unmapped = unmapped == null ? Map.of() : unmapped;
        }

        /** The presses the map holds of it once {@code added} were added. */
        Map<String, String> mapped(Set<String> added) {
            Map<String, String> mapped = new LinkedHashMap<>(ways);
            unmapped.forEach((press, to) -> {
                if (added.contains(name + "." + press)) mapped.put(press, to);
            });
            return mapped;
        }

        /** Where {@code press} leads, mapped or not. */
        String leads(String press, String otherwise) {
            return ways.getOrDefault(press, unmapped.getOrDefault(press, otherwise));
        }
    }

    /**
     * What the Reader answers on a screen: the screens it draws, the presses the map lacks it adds there, the presses
     * it ranks. One that draws and adds nothing ends its session without resuming.
     */
    record ReaderAnswer(List<String> draws, List<String> adds, List<Rank> ranks) {
        ReaderAnswer {
            draws = draws == null ? List.of() : draws;
            adds = adds == null ? List.of() : adds;
            ranks = ranks == null ? List.of() : ranks;
        }
    }

    record Rank(String press, int rank, String tag) {}

    /** The game, the crew and the run of one recorded goal. */
    static final class Replay {

        final Run run;
        final Journal journal;
        final Game game;
        /** The stops the Reader was given, in order. */
        final List<String> reads = new ArrayList<>();
        /** Each screen an edit drew, in order. */
        final List<String> applied = new ArrayList<>();
        /** How many of the Reader's first tries hang until their lease runs out. */
        int hangs;
        /** How many more rounds the Coordinator steps; below 0, every round. */
        int stepsLeft = -1;
        /** The call of the edit writer that kills the host; 0: none. */
        int crashOnApply;
        /** A mark the Evaluator proposes the first time, whatever is shown; null: none. */
        String wrongFirst;
        int restarts;
        private int applies;
        private int ticks;
        private Coordinator coordinator;

        Replay(Run run, Path folder) {
            this.run = run;
            this.journal = new Journal(folder);
            this.game = new Game(run, this::tick, folder);
            this.coordinator = coordinator();
        }

        private Coordinator coordinator() {
            return new Coordinator(Coordinator.Settings.DEFAULT, CREW,
                    new EvaluatorTrigger.Settings(run.evaluations().k(), run.evaluations().every()));
        }

        ExploreLoop.End run(Function<ExploreLoop.Settings, ExploreLoop.Settings> settings) {
            ScreenGraph map = game.map;
            // A resume reloads the bot: the map holds what the Reader added by then.
            ExploreLoop.Reload reload = () -> new ExploreLoop.Reload.Reloaded(1, game.map = run.map(game.added), null,
                    List.of());
            ExploreLoop.End end = new ExploreLoop(map, game, reload, Guards.with(List.of()), journal,
                    settings.apply(ExploreLoop.Settings.DEFAULT.within(Duration.ofHours(1))), () -> game.nowMs)
                    .toward(new ExploreGoal(run.goal(), run.goalScreen())).run();
            CoordinatorHost.close(journal, this::evaluate, Duration.ofMinutes(2), () -> game.nowMs);
            return end;
        }

        <T extends ExploreEvent> List<T> events(Class<T> type) {
            return journal.events().stream().filter(type::isInstance).map(type::cast).toList();
        }

        /** The crew's turn: the watchdog's look, then the Coordinator's step, its workers run at once. */
        private void tick() {
            if (++ticks > 50_000) throw new AssertionError("the run never ended: " + journal.events());
            if (CoordinatorHost.watch(journal, Coordinator.Settings.DEFAULT, game.nowMs)) return;
            if (stepsLeft == 0) return;
            if (stepsLeft > 0) stepsLeft--;
            try {
                CoordinatorHost.round(journal, JournalView.of(journal), coordinator, () -> game.nowMs,
                        (j, id, answers) -> {
                            throw new AssertionError("nothing is settled in a goal run");
                        }, Runnable::run, this::read, this::evaluate);
            } catch (Crash crash) {
                // The host died mid-write: a new one starts on the same journal, and nothing it held survives.
                restarts++;
                coordinator = coordinator();
            }
        }

        /** The Reader: draws what the screen shown needs, by queued edits the host writes, ranks, then resumes. */
        private WorkerRunner.Result read(Journal journal, StopRole role, Stop stop, Duration limit) {
            reads.add(stop.id());
            // A hung session writes nothing; its process is killed when the lease runs out, on the run's own clock.
            if (hangs > 0) {
                hangs--;
                return new WorkerRunner.Result(false, "timeout");
            }
            // The stop's place names the screen the last press led to; a stop with no press reads the screen shown.
            int arrow = stop.place().lastIndexOf('→');
            String screen = arrow < 0 ? game.at : stop.place().substring(arrow + 1);
            ReaderAnswer answer = run.reader().get(screen);
            if (answer == null) return new WorkerRunner.Result(false, "nothing recorded for " + screen);
            // Nothing to draw or add: the session ends without resuming, as the prompt says.
            if (answer.draws().isEmpty() && answer.adds().isEmpty()) return new WorkerRunner.Result(true, "");
            int n = 0;
            for (String draw : answer.draws()) {
                journal.append(new ExploreEvent.EditQueued(stop.id() + "-" + ++n, stop.id(), "edit_map",
                        "{\"draw\":\"" + draw + "\"}", game.nowMs));
            }
            for (String add : answer.adds()) {
                journal.append(new ExploreEvent.EditQueued(stop.id() + "-" + ++n, stop.id(), "crop_pictures",
                        "{\"screen\":\"" + screen + "\",\"add\":\"" + add + "\"}", game.nowMs));
            }
            // apply_edit waits for its edits to be written before the Reader goes on.
            EditWriter.drain(journal, JournalView.of(journal), this::apply, () -> game.nowMs);
            for (Rank rank : answer.ranks()) {
                journal.append(new ExploreEvent.Ranked(screen, rank.press(), rank.rank(), PressTag.fromId(rank.tag()),
                        game.nowMs));
            }
            journal.command("resume " + stop.id());
            return new WorkerRunner.Result(true, "");
        }

        private AgentReply apply(String tool, Map<String, Object> arguments) {
            if (++applies == crashOnApply) throw new Crash();
            if (arguments.get("add") != null) {
                String press = arguments.get("screen") + "." + arguments.get("add");
                game.added.add(press);
                applied.add(press);
                return AgentReply.text("cut " + press);
            }
            String draw = String.valueOf(arguments.get("draw"));
            game.drawn.add(draw);
            applied.add(draw);
            return AgentReply.text("drew " + draw);
        }

        /** The Evaluator: proposes done with the mark recorded for the screen shown. */
        private WorkerRunner.Result evaluate(Journal journal, String id, EvaluatorTrigger.Why why, Duration limit) {
            if (why == EvaluatorTrigger.Why.END) return new WorkerRunner.Result(true, "");
            String mark = wrongFirst != null ? wrongFirst : run.evaluator().get(game.at);
            wrongFirst = null;
            if (mark != null) journal.command("done " + mark);
            return new WorkerRunner.Result(true, "");
        }
    }

    /** The host's death: an error, so nothing between the edit and the test catches it. */
    private static final class Crash extends Error {}

    /** The recorded game: a press leads where its screen says; a screen the map cannot tell yet is nowhere. */
    static final class Game implements ExploreLoop.Play {

        final Run run;
        final Path frames;
        /** The map as the bot holds it now: a reload brings what the Reader added. */
        ScreenGraph map;
        final Runnable crew;
        final Set<String> drawn = new HashSet<>();
        /** The presses the Reader added, {@code Screen.press}. */
        final Set<String> added = new HashSet<>();
        final List<String> taken = new ArrayList<>();
        String at;
        long nowMs = 1_000;

        Game(Run run, Runnable crew, Path frames) {
            this.run = run;
            this.frames = frames;
            this.map = run.map(Set.of());
            this.crew = crew;
            run.screens().stream().filter(Screen::drawn).forEach(s -> drawn.add(s.name()));
            at = run.start();
        }

        /** The frame shown now, whatever the map calls it. */
        @Override
        public ExploreLoop.Look look(String screen) {
            return new ExploreLoop.Look(run.named(at).texts().stream()
                    .map(text -> new Stop.Text(text, new Rect(0, 0, 9, 9))).toList(), List.of());
        }

        @Override
        public Guards.Around around(Input input) {
            return new Guards.Around(List.of(), Foreground.UNKNOWN, List.of());
        }

        @Override
        public Foreground front() {
            return Foreground.UNKNOWN;
        }

        @Override
        public String keep(String name) {
            // A real file: the edit writer cuts a Reader's pictures from it.
            Path frame = frames.resolve(name + ".png");
            try {
                ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", frame.toFile());
            } catch (IOException failed) {
                throw new UncheckedIOException(failed);
            }
            return frame.toString();
        }

        @Override
        public void idle(Duration time) {
            nowMs += time.toMillis();
            crew.run();
        }

        @Override
        public Optional<String> where() {
            return drawn.contains(at) ? Optional.of(at) : Optional.empty();
        }

        @Override
        public boolean goTo(String screen) {
            return goTo(screen, edge -> false);
        }

        /** A walk is presses like any other, counted and led where the recording says, never by a refused way. */
        @Override
        public boolean goTo(String screen, Predicate<Edge> refused) {
            if (screen.equals(at)) return true;
            Optional<List<Edge>> path = map.pathTo(at, screen, edge -> !refused.test(edge));
            if (path.isEmpty()) return false;
            for (Edge edge : path.get()) {
                if (take(edge, 0).arrival() == Arrival.UNCHANGED) return false;
            }
            return screen.equals(at);
        }

        /** The crew works once each press is written, while its frame is still shown, as it does beside a live run. */
        @Override
        public void say(ExploreEvent event) {
            if (event instanceof ExploreEvent.Tried) crew.run();
        }

        @Override
        public Took take(Edge edge, long withinMs) {
            // A press of a screen not shown presses nothing that leads anywhere.
            if (!edge.from().equals(at)) return new Took(Arrival.UNCHANGED, at);
            taken.add(edge.from() + "." + edge.label() + "→" + edge.to());
            nowMs += 500;
            // A way the recording does not name is one the map adds, a way back: it leads where the map says.
            at = run.named(edge.from()).leads(edge.label(), edge.to());
            return drawn.contains(at) ? new Took(Arrival.AT_TARGET, at) : new Took(Arrival.UNKNOWN, null);
        }

        @Override
        public boolean recover() {
            at = run.start();
            return true;
        }

        @Override
        public void restart() {
            at = run.start();
        }
    }
}
